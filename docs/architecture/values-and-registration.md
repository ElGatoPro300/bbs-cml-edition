# Values, registration, and model formats

## Registration

Features (Forms, Clips, etc.) are registered on both sides:

* **Common / server**: typically via `BBSMod` and shared factories (`FormArchitect`, clip factories, …).
* **Client**: renderers, editor panels, and client-only utilities.

Always register the data type on common **and** the client UI/renderer when the feature is visible in-game.

## Values

The mod uses a `Value` system (e.g. `ValueFloat`, `ValueBoolean`, `ValueGroup`) to handle:

* NBT / JSON serialization
* UI syncing with editors and timelines

New persistent fields on forms, replays, body parts, or clips should be `Value` fields, not ad-hoc plain fields, unless there is an existing exception in that class.

## Model formats

| Format | Extension | Notes |
|--------|-----------|--------|
| BBS | `.bbs.json` | Native custom format; complex rigs and animations |
| BOBJ | `.bobj` | Binary OBJ (Blockbuster); performance-oriented |
| GEO | `.geo.json` | Bedrock / GeckoLib geometry |
| OBJ | `.obj` | Wavefront OBJ (static or shape keys) |
| VOX | `.vox` | MagicaVoxel |

Forms that load models typically support at least `.bbs.json`, `.obj`, and `.bobj`.

## Related

* Forms overview: `docs/architecture/forms.md`
* Addon registration API: `docs/ADDONS.md`
