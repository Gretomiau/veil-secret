## Unreleased (Mystrix)

- Enable all four bundled Mystrix example packs by default on Fabric; players can still disable them in the resource-pack screen.
- Move canonical rendering resource directories to `mystrix/`, with per-pack `pinwheel/` aliases preserving priorities, stacks, metadata, filters and editor file paths.
- Account for all 210 source asset/pack files; import missing assets, preserve newer overlapping shaders and bundle adapted optional particle/shader/fog/black-hole packs for both loaders.
- Migrate Quasar data and shader injections, repair missing sample references, and add bounded collision restitution through `veil:bounce`.
- Add asset migration hashes, resource/pack documentation, shader/reference/alias/editor/bounce tests and the JetBrains Mono font license.
- Unreleased Mystrix: audit the supplied Veil 2.1.0 Fabric distribution against the 4.5 baseline; document API migrations and deferred tooling gaps.
- Add opt-in height/box fog pipelines with finite scene-depth clipping and alpha-preserving compositing.
- Add reusable volume-fog and bounded, explicit-time procedural-noise shader includes.
- Add headless GLSL compilation and opt-in OpenGL numerical tests; include resource/test changes and main-branch PRs in build CI.

## 4.5.0

- Improve Quasar emitter rotation ([#180](https://github.com/FoundryMC/Veil/pull/180))
- Add screen shake module ([#183](https://github.com/FoundryMC/Veil/pull/183))
- Fix Necromancer Animation classes ([#196](https://github.com/FoundryMC/Veil/pull/196))
