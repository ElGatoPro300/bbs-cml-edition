# Architecture docs (agent / contributor reference)

Deep-dives extracted from the Cursor project map. The always-on agent index lives in `.cursor/rules/PROJECT-STRUCTURE.mdc`.

**Usage for agents:** treat the project-structure rule as orientation; **read the matching file here before changing that area.**

## Core

| Doc | Topic |
|-----|--------|
| [ui-localization.md](ui-localization.md) | `IKey` / `UIKeys` / `en_us.json` |
| [ui-framework.md](ui-framework.md) | `UIElement`, layouts, dashboard/panels |
| [values-and-registration.md](values-and-registration.md) | `Value` system, registration, model formats |
| [rendering-iris.md](rendering-iris.md) | Iris/shadow/render-state rules |

## Film / forms

| Doc | Topic |
|-----|--------|
| [forms.md](forms.md) | Form system overview |
| [model-form.md](model-form.md) | ModelForm, animator, matrix cache |
| [particle-form.md](particle-form.md) | Bedrock-subset particles, MoLang vars |
| [replays.md](replays.md) | Replay data model, onion, actor flag |
| [actor-mode.md](actor-mode.md) | Stub vs `ActorEntity` (critical) |
| [keyframes.md](keyframes.md) | Channels, factories, anchors |
| [clips.md](clips.md) | Camera/effects/action clips |
| [keybinds.md](keybinds.md) | `KeyCombo` / `Keybind` stack |

## How-tos

| Doc | Topic |
|-----|--------|
| [how-to-ui-element.md](how-to-ui-element.md) | New UI widget |
| [how-to-form.md](how-to-form.md) | New Form + renderer + panel |
| [how-to-clip.md](how-to-clip.md) | New Clip + registration |

## Other project docs (not under this folder)

* `docs/ADDONS.md`, `docs/FILM_FRAME_TIMELINE.md`, opacity/limb docs, etc.
* `AGENTS.md`, `CONTRIBUTING.md`
* User wiki: `bbs-mod-wiki.wiki/`
