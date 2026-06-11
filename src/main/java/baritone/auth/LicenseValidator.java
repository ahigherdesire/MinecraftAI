/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.auth;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.util.Base64;

/**
 * RSA-based license verification.
 *
 * <p>Token format: {@code Base64(name|YYYY-MM-DD) + "." + Base64(RSA-SHA256-signature)}
 *
 * <p>The RSA-2048 public key is embedded here. The corresponding private key is held
 * only by the mod owner and is used offline to sign license tokens via {@code generate_license.ps1}.
 * Tokens cannot be forged without the private key — even someone who decompiles this
 * class cannot generate new valid tokens, only verify existing ones.
 *
 * <p>To grant access: run {@code generate_license.ps1 -Name "PlayerName" -Days 90},
 * send the token to the player, they type {@code #activate <token>} once in-game.
 * The token is saved to {@code <gamedir>/baritone/license.key} and verified on every session.
 */
public final class LicenseValidator {

    /**
     * RSA-2048 public key in X.509/DER format, Base64-encoded.
     * The matching private key is in {@code private_key.b64} (gitignored, owner only).
     */
    private static final String PUBLIC_KEY_B64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3RTdn3XIUzXsESRsehkx" +
            "4afa59xFn3z5p9Layfcn1fntKFrHTwm46kkbsmHv+KgdeLavtFpfUw/Hik4v+P03" +
            "ppT7456NYpQjcH5f6SUdKWl/K+qXJ50v4j6i0e8vKsGauSL6NdmaJF3ZJ0OBfnuz" +
            "SA5Kt5gnv6Zs4MkaCOKotAg1PxMVQfb97gCHvZCFVezdao+H7S22JJU9tVZkYrJY" +
            "o13xDBgfUWu1W5xRyLSlhIPYGdx1kR9TrXrQyX74A2q/ztYJASsq868CgbSv//2v" +
            "I+0Au+vieRpH6LRjrAcNb1bk9JGXzFmxMcEgDMQRq23x8rRtPQsE5O8dPiUAEtZT" +
            "ZQIDAQAB";

    public enum Status { VALID, NO_LICENSE, INVALID, EXPIRED }

    public static final class Result {
        public final Status status;
        /** Display name embedded in the token. {@code null} unless {@link Status#VALID}. */
        public final String name;
        /** Expiry date embedded in the token. {@code null} unless {@link Status#VALID}. */
        public final LocalDate expiry;

        Result(Status status, String name, LocalDate expiry) {
            this.status = status;
            this.name = name;
            this.expiry = expiry;
        }
    }

    private LicenseValidator() {}

    /** Reads and validates the license from {@code <gamedir>/baritone/license.key}. */
    public static Result check() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return new Result(Status.NO_LICENSE, null, null);

            Path licenseFile = mc.gameDirectory.toPath().resolve("baritone/license.key");
            if (!Files.exists(licenseFile)) {
                return new Result(Status.NO_LICENSE, null, null);
            }

            String token = Files.readString(licenseFile, StandardCharsets.UTF_8).trim();
            return validate(token);
        } catch (Throwable t) {
            return new Result(Status.INVALID, null, null);
        }
    }

    /**
     * Validates a raw token string without reading from disk.
     * Called by {@code #activate} before saving.
     */
    public static Result validate(String token) {
        try {
            String[] parts = token.split("\\.", 2);
            if (parts.length != 2) return new Result(Status.INVALID, null, null);

            byte[] payloadBytes = Base64.getDecoder().decode(parts[0]);
            byte[] sigBytes     = Base64.getDecoder().decode(parts[1]);

            // Verify RSA-SHA256 signature against the embedded public key
            byte[] pubKeyBytes = Base64.getDecoder().decode(PUBLIC_KEY_B64);
            PublicKey pubKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(pubKeyBytes));
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(pubKey);
            sig.update(payloadBytes);
            if (!sig.verify(sigBytes)) return new Result(Status.INVALID, null, null);

            // Parse payload: "name|YYYY-MM-DD"
            String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            String[] fields = payload.split("\\|", 2);
            if (fields.length != 2) return new Result(Status.INVALID, null, null);

            String name      = fields[0];
            LocalDate expiry = LocalDate.parse(fields[1]);

            // Tokens are signed with UTC dates (generate_license.ps1 and the
            // website trial API both use UTC) — compare in UTC, otherwise users
            // east of UTC lose hours off the end of their license.
            if (LocalDate.now(java.time.ZoneOffset.UTC).isAfter(expiry)) {
                return new Result(Status.EXPIRED, name, expiry);
            }
            return new Result(Status.VALID, name, expiry);

        } catch (Throwable t) {
            return new Result(Status.INVALID, null, null);
        }
    }

    /** Saves a token to {@code <gamedir>/baritone/license.key}. */
    public static void save(String token) throws IOException {
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("baritone");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("license.key"), token, StandardCharsets.UTF_8);
    }
}
