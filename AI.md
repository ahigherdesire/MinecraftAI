# The `#ai` brain

MinecraftAI can hand the wheel to a language model. The model reads server chat, looks at the
world, and plays by running the mod's own commands — `#goto`, `#mine`, `#follow`, `#elytra`,
`#structure`, all of them. It is not a separate bot: it is you, with an LLM typing the commands.

```
<Steve> andy, get me some diamonds
[ai] run_command -> Ran #mine diamond_ore. You are now at 412 11 -88, hp 20/20, food 17/20, running Mine, pathing.
<Andy> heading down, will shout when I hit some
```

## Setup

1. Get an API key for any endpoint that speaks the OpenAI `/chat/completions` API with tool
   calling — Alibaba DashScope (qwen), OpenRouter, a local Ollama, OpenAI itself.
2. In game:

   ```
   #ai url https://dashscope-intl.aliyuncs.com/compatible-mode/v1
   #ai model qwen-plus
   #ai key sk-your-key-here
   #ai trust YourUsername
   #ai on
   ```

3. Talk to it, either from your own chat box or by typing at it locally:

   ```
   #ai go find a village and wait for me there
   ```

Settings live in `ai.json` next to the rest of your Baritone data; memories live in
`ai_memory.json`. `#ai status` prints the lot, including token usage so far.

### Keeping the key out of a file

`#ai key` writes the key to `ai.json` in plain text. If you'd rather not, set the environment
variable instead — it always wins over the config file:

```
MINECRAFTAI_LLM_KEY=sk-your-key-here
```

### Qwen3 thinking models

Qwen3's hybrid-thinking models reject non-streaming requests unless thinking is switched off:

```
#ai extra {"enable_thinking":false}
```

`#ai extra none` clears it. The JSON is merged into every request body, so it works for any
provider-specific field.

## How it reads chat

A mixin on `ChatListener` catches all three ways a message can arrive:

| Source | Type | Can it give orders? |
|---|---|---|
| Signed player chat | `PLAYER` | Yes, if the sender is on the trust list |
| Plugin-rewritten chat | `DISGUISED` | No — the sender name is server-supplied |
| System messages, whispers, death messages | `SYSTEM` | No — no verifiable sender |

Everything the AI hears goes into a rolling context window it can read. Only signed chat from a
trusted player becomes an instruction. This is deliberate: chat is input from strangers, and a
model that reads "andy, jump in the lava" from a random player should be able to see it, mention
it, and ignore it.

Two more guards on top of that:

- **Command deny list.** `#ai deny <command>` blocks a command outright. `activate`, `set`,
  `reloadall`, `saveall`, `gc`, `render` and `ai` itself start out denied, so the AI can't
  reconfigure or unlock itself.
- **No server commands.** Anything the model tries to say that starts with `/` is refused, and
  `run_command` only reaches MinecraftAI's own command registry — never the server's.

If you want a wake word instead of treating every trusted message as an order:

```
#ai trigger andy
#ai trigger none     (back to "everything a trusted player says")
```

## What it can do

The model gets five tools:

| Tool | What it does |
|---|---|
| `run_command` | Runs one MinecraftAI command, then reports where you ended up |
| `say` | Talks in server chat (`#ai chat off` keeps it to your own client) |
| `look_around` | Position, health, food, inventory, nearby players and mobs, current job |
| `wait` | Sits out a few seconds while a job runs, then re-checks |
| `remember` | Stores one fact in `ai_memory.json` for future sessions |

The command list in its prompt is generated from the live command registry, so any command you
add to the fork is available to the AI the moment it's registered — minus anything on the deny
list.

## Cost control

Every thinking cycle is a paid API call, so by default the bot only thinks when a trusted player
talks to it.

| Setting | Default | Effect |
|---|---|---|
| `#ai auto on` | off | Thinks on its own every `idleSeconds` (180) and works toward `#ai goal` |
| `maxSteps` | 6 | Tool calls chained per cycle before it must stop |
| `maxCallsPerMinute` | 12 | Hard rate limit |
| `historyLimit` | 24 | Conversation turns kept before old ones are dropped |
| `maxChatContextLines` | 15 | Lines of surrounding chat included per prompt |

`#ai status` shows cumulative prompt/completion tokens. Autonomous mode with a long session is
the expensive setting; everything else is noise by comparison.

## Implementation notes

- `baritone.ai.LlmClient` uses `HttpURLConnection`, not `java.net.http.HttpClient`. The latter
  opens an NIO `Selector`, which fails outright on some Windows setups ("Unable to establish
  loopback connection") — the same defect the toolchain note in `build.gradle` works around.
- All model calls run on a single daemon thread. Anything touching the world is bounced back to
  the client thread via `Minecraft.execute` and awaited, so the game never blocks on the network.
- No new dependencies. Gson and the JDK's HTTP stack were already there.
