// Vercel serverless function — POST /api/trial
// Generates a 1-day RSA-signed MinecraftAI license token.
//
// Required environment variable (set in Vercel dashboard, never commit):
//   PRIVATE_KEY_B64  —  contents of private_key.b64 (the raw base64 string)
//
// Optional environment variable for persistent rate-limiting:
//   UPSTASH_REDIS_REST_URL + UPSTASH_REDIS_REST_TOKEN
//   (free tier at upstash.com — add via Vercel dashboard → Storage → Redis)

const crypto = require('crypto');

// ── Token generation (mirrors LicenseSigner.java exactly) ─────────────────────
function generateToken(privKeyB64, username, days) {
  // PKCS8 DER → PEM (Node crypto requires PEM or KeyObject)
  const pem =
    '-----BEGIN PRIVATE KEY-----\n' +
    privKeyB64.match(/.{1,64}/g).join('\n') +
    '\n-----END PRIVATE KEY-----';

  // payload = "username|YYYY-MM-DD"  (UTC date, same as Java LocalDate on a UTC server)
  const expiry = new Date();
  expiry.setUTCDate(expiry.getUTCDate() + days);
  const dateStr = expiry.toISOString().split('T')[0];
  const payload = `${username}|${dateStr}`;

  // SHA256withRSA signature
  const sign = crypto.createSign('SHA256');
  sign.update(Buffer.from(payload, 'utf8'));
  sign.end();
  const sig = sign.sign(pem);

  // base64(payload) + "." + base64(signature)
  return Buffer.from(payload, 'utf8').toString('base64') + '.' + sig.toString('base64');
}

// ── Username validation ────────────────────────────────────────────────────────
function validUsername(name) {
  return typeof name === 'string' && /^[a-zA-Z0-9_]{3,16}$/.test(name);
}

// ── Optional Upstash rate-limiting ────────────────────────────────────────────
// If UPSTASH_REDIS_REST_URL is set, uses Redis to enforce:
//   - one trial per username (forever)
//   - one trial per IP per 7 days
// If not set, skips rate-limiting (fine for low-traffic / trusted audiences).
const TTL_7D  = 7 * 24 * 60 * 60; // 7 days in seconds
const TTL_INF = 365 * 24 * 60 * 60 * 10; // ~10 years for "forever"

function redisConfigured() {
  return process.env.UPSTASH_REDIS_REST_URL && process.env.UPSTASH_REDIS_REST_TOKEN;
}

async function redisCmd(...args) {
  const res = await fetch(process.env.UPSTASH_REDIS_REST_URL, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${process.env.UPSTASH_REDIS_REST_TOKEN}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(args),
  });
  const json = await res.json();
  return json.result;
}

// Check only — does NOT record. Recording happens after token generation
// succeeds, so a 500 during signing doesn't permanently burn the username/IP.
async function checkRateLimit(username, ip) {
  if (!redisConfigured()) return null; // rate-limiting not configured — allow

  const [userExists, ipExists] = await Promise.all([
    redisCmd('EXISTS', `trial:user:${username.toLowerCase()}`),
    redisCmd('EXISTS', `trial:ip:${ip}`),
  ]);

  if (userExists) {
    return `Username "${username}" has already claimed a free trial. Contact the server owner for a full license.`;
  }
  if (ipExists) {
    return 'Your IP address has already generated a trial in the past 7 days.';
  }
  return null; // allowed
}

// Record a successful trial issue (fire-and-forget — don't block the response).
function recordRateLimit(username, ip) {
  if (!redisConfigured()) return;
  Promise.all([
    redisCmd('SET', `trial:user:${username.toLowerCase()}`, '1', 'EX', TTL_INF),
    redisCmd('SET', `trial:ip:${ip}`, '1', 'EX', TTL_7D),
  ]).catch(() => {}); // non-fatal
}

// ── Handler ────────────────────────────────────────────────────────────────────
module.exports = async function handler(req, res) {
  // CORS (allow same-origin + any explicit origin for dev)
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }
  if (req.method !== 'POST') {
    return res.status(405).json({ ok: false, error: 'Method not allowed.' });
  }

  // Parse body
  let body;
  try {
    body = typeof req.body === 'string' ? JSON.parse(req.body) : req.body;
  } catch {
    return res.status(400).json({ ok: false, error: 'Invalid JSON body.' });
  }

  const username = (body?.username || '').trim();
  if (!validUsername(username)) {
    return res.status(400).json({
      ok: false,
      error: 'Username must be 3–16 characters: letters, numbers, underscore only.',
    });
  }

  // Private key from environment
  const privKeyB64 = (process.env.PRIVATE_KEY_B64 || '').trim();
  if (!privKeyB64) {
    console.error('[trial] PRIVATE_KEY_B64 environment variable is not set.');
    return res.status(500).json({
      ok: false,
      error: 'Token server is not configured. Contact the server owner.',
    });
  }

  // Rate-limiting (optional — needs Upstash env vars)
  const clientIp = (
    req.headers['x-forwarded-for'] ||
    req.headers['x-real-ip'] ||
    req.socket?.remoteAddress ||
    'unknown'
  ).split(',')[0].trim();

  const rateLimitError = await checkRateLimit(username, clientIp).catch(() => null);
  if (rateLimitError) {
    return res.status(429).json({ ok: false, error: rateLimitError });
  }

  // Generate token
  let token;
  try {
    token = generateToken(privKeyB64, username, 1);
  } catch (err) {
    console.error('[trial] Token generation failed:', err);
    return res.status(500).json({
      ok: false,
      error: 'Failed to generate token. Please try again.',
    });
  }

  // Only burn the rate-limit keys once the token actually exists.
  recordRateLimit(username, clientIp);

  const expiry = new Date();
  expiry.setUTCDate(expiry.getUTCDate() + 1);
  const expiryStr = expiry.toISOString().replace('T', ' ').slice(0, 16) + ' UTC';

  console.log(`[trial] Token issued for "${username}" (IP: ${clientIp}) — expires ${expiryStr}`);

  return res.status(200).json({
    ok:       true,
    token,
    username,
    expiry:   expiryStr,
  });
};
