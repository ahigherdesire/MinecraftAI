# Installation

## Requirements

- **Minecraft 26.1.2** with **Fabric Loader 0.18.4**
- **Java 25 JDK** — required both to run MC 26.1.2 and to build from source

## Installing the mod

1. Install [Fabric Loader 0.18.4](https://fabricmc.net/use/) for Minecraft 26.1.2.
2. Copy `build/libs/minecraftai-dirty.jar` into your `mods/` folder.
3. Launch Minecraft. You should see `baritone/` created in your game directory.

## Activating your license

The mod is access-controlled. On first launch you'll see:

```
[MinecraftAI] Type #activate <token> to activate.
```

Enter the token you were given:

```
#activate <your-token>
```

The token is saved to `baritone/license.key` — you only need to do this **once**. After that the mod loads automatically every session until the token expires.

## First-time setup (multiplayer)

If you play on multiplayer and want to use `#structure` or `#where` to find structures, enter your world seed once:

```
#seedinput 12345678
```

The seed is saved to `baritone/seed.txt` and reloaded automatically on every future launch — you only need to do this once per world.

## First-run survival tips &nbsp;·&nbsp; 🧪 EXPERIMENTAL

If you want the bot to walk you to a bed at night, turn on auto-sleep:

```
#autosleep
```

For it to work, walk near your bed at least once so Baritone caches it.

> ⚠️ `#autosleep` is **experimental** — it may misfire or interact poorly with other Baritone processes. Use `#cancel` if anything looks wrong, and `#autosleep off` to disable.

For auto-eat / auto-flee / auto-torch, use **Meteor Client** — Baritone's plans for those were dropped to avoid duplicating Meteor's work.

## Building from source

**Requirements:**
- Java 25 JDK with `JAVA_HOME` pointing to it — if you get `error: invalid source release: 25`, your JDK is too old
- Git (for the version tag in the build command)

**Build command:**
```bash
./gradlew build -Pmod_version="$(git describe --always --tags --first-parent | cut -c2-)"
```

The output JAR is written to `build/libs/`.

> On Windows, use Git Bash or WSL to run the `$(git describe ...)` substitution, or set `mod_version` manually:
> ```
> gradlew build -Pmod_version=1.0.0
> ```

## After installation

Type `#help` in the Minecraft chat for a list of all commands.

See [USAGE.md](USAGE.md) for full command documentation and [FEATURES.md](FEATURES.md) for a complete feature overview.

---

## Owner guide — managing access

> This section is for the person who built and distributes the mod.

### Where is the private key?

`private_key.b64` in the project root. It is **gitignored** — never committed, never shared.

**Back it up somewhere safe** (password manager, encrypted USB, iCloud/Dropbox in an encrypted folder). If you lose it you cannot generate new licenses without rotating the keypair and redistributing the JAR.

### Giving someone access

Run from the project root:

```powershell
.\tools\generate_license.ps1 -Name "PlayerName" -Days 90
```

This prints a token. Copy it and send it to them. They type it in Minecraft once:

```
#activate <token>
```

Done — no recompile, no source edit, no UUID lookup required.

Common expiry lengths:
```powershell
.\tools\generate_license.ps1 -Name "Steve" -Days 30    # trial / short-term
.\tools\generate_license.ps1 -Name "Steve" -Days 90    # standard (default)
.\tools\generate_license.ps1 -Name "Steve" -Days 365   # 1 year
.\tools\generate_license.ps1 -Name "Steve" -Days 3650  # 10 years (trusted friend)
```

### Revoking access

Tokens expire automatically on the printed expiry date. **Simply don't generate a renewal.** Their `license.key` stops working on that date with no action from you.

For immediate revocation before expiry: there is no built-in mechanism (tokens are verified fully offline). Your options:
1. Use short-lived tokens (30–90 days) so the window is small.
2. Rotate the keypair — generate new keys, rebuild and redistribute the JAR. Everyone needs to re-activate.

### If you lose the private key

You cannot generate new licenses. To recover:
1. Generate a new RSA keypair (run `KeyGen.java` in the project).
2. Update `LicenseValidator.java` with the new public key.
3. Rebuild and redistribute the JAR.
4. Generate fresh tokens for all users — they must re-activate.

### How it works (security model)

- The JAR contains only the **public key** (for verification).
- The **private key** lives only on your machine (gitignored).
- Tokens are RSA-SHA256 signed — they cannot be forged without the private key.
- Anyone who decompiles the JAR gets the public key but cannot use it to sign new tokens.
- Tokens embed the player's name and an expiry date in plaintext inside the signature.
