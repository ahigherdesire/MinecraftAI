/* ── Particle canvas ──────────────────────────────────────────────────────── */
(function () {
  const canvas = document.getElementById('bg-canvas');
  const ctx = canvas.getContext('2d');
  let W, H, particles = [];
  const N = 90, CONN = 130;

  function resize() {
    W = canvas.width  = window.innerWidth;
    H = canvas.height = window.innerHeight;
  }
  resize();
  window.addEventListener('resize', resize);

  function mkParticle() {
    return {
      x:  Math.random() * W,
      y:  Math.random() * H,
      vx: (Math.random() - .5) * .35,
      vy: (Math.random() - .5) * .35,
      r:  Math.random() * 1.5 + .5,
      op: Math.random() * .7 + .2,
    };
  }
  for (let i = 0; i < N; i++) particles.push(mkParticle());

  function draw() {
    ctx.clearRect(0, 0, W, H);
    for (let i = 0; i < N; i++) {
      const p = particles[i];
      p.x += p.vx; p.y += p.vy;
      if (p.x < -10)  p.x = W + 10;
      if (p.x > W+10) p.x = -10;
      if (p.y < -10)  p.y = H + 10;
      if (p.y > H+10) p.y = -10;

      ctx.beginPath();
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
      ctx.fillStyle = `rgba(0,229,255,${p.op * .6})`;
      ctx.fill();

      for (let j = i + 1; j < N; j++) {
        const q  = particles[j];
        const dx = p.x - q.x, dy = p.y - q.y;
        const d  = Math.sqrt(dx * dx + dy * dy);
        if (d < CONN) {
          ctx.beginPath();
          ctx.moveTo(p.x, p.y);
          ctx.lineTo(q.x, q.y);
          ctx.strokeStyle = `rgba(0,229,255,${(1 - d / CONN) * .12})`;
          ctx.lineWidth = .6;
          ctx.stroke();
        }
      }
    }
    requestAnimationFrame(draw);
  }
  draw();
})();

/* ── Nav scroll / hamburger ───────────────────────────────────────────────── */
const nav       = document.getElementById('nav');
const scrollTop = document.getElementById('scrollTop');
const navMobile = document.getElementById('navMobile');

window.addEventListener('scroll', () => {
  nav.classList.toggle('scrolled', window.scrollY > 30);
  scrollTop.classList.toggle('show', window.scrollY > 400);
});

document.getElementById('navToggle').addEventListener('click', () => {
  navMobile.classList.toggle('open');
});
navMobile.querySelectorAll('a').forEach(a => {
  a.addEventListener('click', () => navMobile.classList.remove('open'));
});

scrollTop.addEventListener('click', () => window.scrollTo({ top: 0, behavior: 'smooth' }));

/* ── Scroll reveal ────────────────────────────────────────────────────────── */
const revealObserver = new IntersectionObserver(entries => {
  entries.forEach(e => { if (e.isIntersecting) e.target.classList.add('visible'); });
}, { threshold: .12 });

document.querySelectorAll('.reveal, .reveal-l, .reveal-r').forEach(el => revealObserver.observe(el));

/* ── Stats count-up ───────────────────────────────────────────────────────── */
const statsObserver = new IntersectionObserver(entries => {
  entries.forEach(e => {
    if (!e.isIntersecting || e.target.dataset.animated) return;
    e.target.dataset.animated = '1';
    const el     = e.target.querySelector('[data-target]');
    if (!el) return;
    const target = +el.dataset.target;
    const suffix = el.dataset.suffix || '';
    const dur    = 1200;
    const start  = Date.now();
    (function tick() {
      const t    = Math.min(1, (Date.now() - start) / dur);
      const ease = 1 - Math.pow(1 - t, 3);
      el.textContent = Math.round(ease * target) + (t >= 1 ? suffix : '');
      if (t < 1) requestAnimationFrame(tick);
      else el.textContent = target + suffix;
    })();
  });
}, { threshold: .5 });

document.querySelectorAll('.stat').forEach(el => statsObserver.observe(el));

/* ── Terminal typewriter ──────────────────────────────────────────────────── */
(function () {
  const body = document.getElementById('termBody');
  if (!body) return;

  const sequences = [
    [
      { type: 'cmd',  text: '#mine diamond_ore' },
      { type: 'ok',   text: '[MinecraftAI] Mining diamond_ore — 64 targets queued' },
      { type: 'out',  text: '  Nearest vein: X=1204 Y=-58 Z=441  (38 blocks away)' },
      { type: 'out',  text: '  Pathing... sprint-bridging over gap at Y=-52' },
    ],
    [
      { type: 'cmd',  text: '#goto 8000 64 -3000' },
      { type: 'ok',   text: '[MinecraftAI] Pathing to X=8000 Y=64 Z=-3000' },
      { type: 'out',  text: '  Distance: 6241 blocks — splicing 4 route segments' },
      { type: 'out',  text: '  ETA: ~4 min at sprint speed' },
    ],
    [
      { type: 'cmd',  text: '#structure stronghold' },
      { type: 'ok',   text: '[MinecraftAI] Stronghold found: X=2048 Z=-3584' },
      { type: 'out',  text: '[JourneyMap] Waypoint dropped: "Stronghold" (gold)' },
      { type: 'out',  text: '  Type #goto 2048 -3584 to navigate there' },
    ],
    [
      { type: 'cmd',  text: '#chest diamond' },
      { type: 'ok',   text: '[MinecraftAI] Found 3 chests with diamonds:' },
      { type: 'out',  text: '  1. X=1234 Y=32 Z=-567 [overworld]  48x diamond' },
      { type: 'out',  text: '  2. X=-800 Y=12 Z=300  [overworld]  17x diamond' },
    ],
    [
      { type: 'cmd',  text: '#threats on' },
      { type: 'ok',   text: '[Threats] Detection ON — radius 64 blocks' },
      { type: 'warn', text: '[Threats] WARNING: Alex  X=123 Y=64 Z=-44  (~31 blocks)' },
    ],
    [
      { type: 'cmd',  text: '#bases' },
      { type: 'ok',   text: '[MinecraftAI] DBSCAN scan complete — 3 clusters found' },
      { type: 'out',  text: '  #1 Score=847  X=8432 Z=-2341 [overworld]' },
      { type: 'out',  text: '  #2 Score=623  X=-1203 Z=5671 [overworld]' },
    ],
  ];

  let seq = 0, line = 0, char = 0;
  let currentEl = null;

  function typeClass(t) {
    if (t === 'cmd')  return 'term-cmd';
    if (t === 'ok')   return 'term-ok';
    if (t === 'warn') return 'term-warn';
    return 'term-out';
  }
  function esc(s) {
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  function typeChar() {
    const s = sequences[seq];
    const l = s[line];
    const isFirst = line === 0;
    const prefix  = isFirst ? '<span class="term-prompt">›&nbsp;</span>' : '';

    if (!currentEl) {
      currentEl = document.createElement('span');
      currentEl.className = 'term-line ' + typeClass(l.type);
      body.appendChild(currentEl);
      char = 0;
    }
    if (char < l.text.length) {
      currentEl.innerHTML = prefix + '<span>' + esc(l.text.slice(0, char + 1)) + '</span><span class="term-cursor"></span>';
      char++;
      setTimeout(typeChar, isFirst ? 45 : 0);
    } else {
      currentEl.innerHTML = prefix + esc(l.text);
      currentEl = null;
      line++;
      if (line < s.length) {
        setTimeout(typeChar, 200);
      } else {
        setTimeout(() => {
          body.innerHTML = '';
          line = 0; currentEl = null;
          seq = (seq + 1) % sequences.length;
          setTimeout(typeChar, 300);
        }, 2800);
      }
    }
  }
  setTimeout(typeChar, 600);
})();

/* ── Trial token generator ────────────────────────────────────────────────── */
let lastToken = '';

async function generateTrial() {
  const input    = document.getElementById('trialUsername');
  const btn      = document.getElementById('trialBtn');
  const result   = document.getElementById('tokenResult');
  const errDiv   = document.getElementById('trialError');
  const username = input.value.trim();

  result.classList.remove('show');
  errDiv.classList.remove('show');

  if (!username) {
    showTrialError('Please enter your Minecraft username.');
    input.focus(); return;
  }
  if (!/^[a-zA-Z0-9_]{3,16}$/.test(username)) {
    showTrialError('Username must be 3–16 characters (letters, numbers, underscore only).');
    input.focus(); return;
  }

  btn.classList.add('loading');
  btn.querySelector('.btn-label').textContent = 'Generating…';

  try {
    const res  = await fetch('/api/trial', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username }),
    });
    const data = await res.json();

    if (data.ok) {
      lastToken = data.token;
      document.getElementById('tokenBox').textContent = data.token;
      document.getElementById('tokenExpiry').textContent =
        `Valid for 24 hours · Expires ${data.expiry} · Paste into Minecraft chat to activate`;
      result.classList.add('show');
      result.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    } else {
      showTrialError(data.error || 'Could not generate token. Please try again.');
    }
  } catch {
    showTrialError(
      'Could not reach the token server. Make sure server.ps1 is running on localhost:8080, or contact the mod owner.'
    );
  }

  btn.classList.remove('loading');
  btn.querySelector('.btn-label').textContent = '⚡  Generate 1-Day Token';
}

function showTrialError(msg) {
  const el = document.getElementById('trialError');
  el.textContent = msg;
  el.classList.add('show');
}

function copyToken() {
  if (!lastToken) return;
  navigator.clipboard.writeText(lastToken).then(() => flashBtn('copyToken', '✓ Copied!'));
}
function copyActivate() {
  if (!lastToken) return;
  navigator.clipboard.writeText('#activate ' + lastToken).then(() => flashBtn('copyActivate', '✓ Copied!'));
}
function flashBtn(id, txt) {
  const btn  = document.getElementById(id);
  const orig = btn.textContent;
  btn.textContent = txt;
  btn.classList.add('copied');
  setTimeout(() => { btn.textContent = orig; btn.classList.remove('copied'); }, 2000);
}

document.getElementById('trialUsername').addEventListener('keydown', e => {
  if (e.key === 'Enter') generateTrial();
});
