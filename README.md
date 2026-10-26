# ChunkAnchor

A Paper plugin that adds craftable chunk loaders — **Chunk Anchors** — that keep the chunks around them loaded as long as they are fuelled with **Anchor Charge**, a brand-new fuel item.

Built for **Paper 26.3** (Java 25).

## Features

- Three anchor tiers, crafted in survival and upgraded by crafting the previous tier with more expensive materials
- Anchor Charge — a custom fuel item with its own recipe
- Every anchor has a maximum charge capacity and slowly drains while running; when the charge runs out it stops loading chunks
- Furnace-style fuel: unused fuel drops when the anchor is broken, the piece that is currently burning is lost
- Right-click an anchor to open its menu: charge bar, time left, insert/withdraw fuel, switch on/off
- Uses Paper's plugin chunk tickets, so chunks stay loaded through Paper's own chunk system
- Per-player anchor limit, disabled worlds, owner-only access/breaking, explosion protection
- Everything (recipes, tiers, ranges, capacities, drain rates, items, menu and messages) is configurable

## Default tiers

| Tier | Block | Area | Capacity | Drain / minute | Runtime when full |
|---|---|---|---|---|---|
| Chunk Anchor | Lodestone | 1 chunk | 1440 | 1 | 24 h |
| Reinforced Chunk Anchor | Crying Obsidian | 3×3 | 2880 | 3 | 16 h |
| Eternal Chunk Anchor | Netherite Block | 5×5 | 5760 | 6 | 16 h |

One **Anchor Charge** is worth 60 charge.

## Default recipes

**Anchor Charge** (makes 2)
```
R G R      R = Redstone Dust
G P G      G = Glowstone Dust
R G R      P = Ender Pearl
```

**Chunk Anchor**
```
O D O      O = Obsidian
E L E      D = Diamond
O D O      E = Eye of Ender, L = Lodestone
```

**Reinforced Chunk Anchor**
```
G E G      G = Block of Gold, E = Eye of Ender
C A C      C = Crying Obsidian, A = Chunk Anchor
G D G      D = Block of Diamond
```

**Eternal Chunk Anchor**
```
N E N      N = Netherite Ingot, E = End Crystal
E A E      A = Reinforced Chunk Anchor
N S N      S = Nether Star
```

## Commands

| Command | Description | Permission |
|---|---|---|
| `/chunkanchor list [player]` | List your (or someone's) anchors | `chunkanchor.use` / `chunkanchor.admin` for others |
| `/chunkanchor give <player> <tier> [amount]` | Give anchors | `chunkanchor.admin` |
| `/chunkanchor fuel <player> [amount]` | Give Anchor Charge | `chunkanchor.admin` |
| `/chunkanchor debug` | Show loaded chunk tickets and load levels | `chunkanchor.admin` |
| `/chunkanchor reload` | Reload the config | `chunkanchor.admin` |

Alias: `/anchor`

## Permissions

| Permission | Default | Description |
|---|---|---|
| `chunkanchor.use` | everyone | Place and use anchors |
| `chunkanchor.admin` | op | Admin commands, access and break any anchor |
| `chunkanchor.bypass.limit` | op | Ignore the per-player anchor limit |

## Configuration

See [`config.yml`](src/main/resources/config.yml). Text uses [MiniMessage](https://docs.advntr.dev/minimessage/format.html). Recipe ingredients can be any material, `anchor:<tier>` for an anchor of another tier, or `fuel` for Anchor Charge.

Changing a tier's `block` affects anchors that are already placed: anchors whose block no longer matches are removed.

## Building

```
./gradlew build
```

The jar ends up in `build/libs/`.

## Releases

Development happens on `dev`. Every push to `master` builds the plugin and, if the version in `gradle.properties` has no release yet, creates a GitHub release and publishes the jar to Modrinth. Bump `version` in `gradle.properties` before merging into `master`.

## License

[MIT](LICENSE)
