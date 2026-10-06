# Veil 2.1.0 distribution audit and selective adaptation

Audit date: 2026-10-06. Baseline: `MystrixStudio/veil`, `main`,
`4f922da49a9c07295887e1f799597a2304ce4af9`, version `4.5.0+mystrix.1`.

The supplied `veil-2.1.0.zip` is an **expanded Fabric mod distribution**, not a
source checkout. Its metadata already targets Minecraft **1.21.1 / Java 21**.
It includes compiled intermediary-mapped classes, resources and nested libraries.
Its SHA-256 is
`f39a0cf306faf35eb4c884621224e7d8b25d1a770c8341dfe25df4c2a6987376`.
The custom fog and black-hole experiments in this particular archive should not
be treated as proof of features in every upstream 2.1.0 release.

## Method and evidence

1. Built the unmodified baseline and ran its common tests for both loaders.
2. Inventoried every `foundry/veil/` class, including nested classes, in the ZIP
   and the baseline remapped Fabric jar. Compared public/protected JVM member
   signatures of retained `api/` classes using the same intermediary mappings.
3. Compared every asset and embedded resource-pack file by path and content.
4. Inspected source replacements, registry/loader wiring, shader resource codecs,
   camera transforms, editor registration and selected old bytecode with `javap`.
5. Rewrote selected shader capabilities against current resources and APIs.
   No old classfiles, nested jars, fonts, mixins or renderer implementations were copied.

The complete baseline inventory is [audit/veil-2.1.0-baseline.json](audit/veil-2.1.0-baseline.json).
It records exact removed/added member descriptors, removed classes, identical,
changed and absent assets, metadata and both artifact hashes. Reproduce with Node:

```sh
node tools/audit-distribution.mjs /path/to/veil-2.1.0.zip /path/to/baseline-remapped-fabric.jar /tmp/audit.json
```

Counts: **853** old Veil classes; **971** baseline classes; **53** old class names
absent (including nested classes); **101** retained API classes with member changes.
Of **210** old resources, **55** are identical, **20** changed and **135** are absent.
The baseline has **16** additional resources. These are inventory counts, not
counts of missing systems. The ZIP supplies no NeoForge binary for comparison.
A successful build or matching signature does not prove equivalent runtime behavior.

## Findings and decisions

Value describes developer usefulness; risk describes adapting the old behavior
into the current renderer. “Retained” means implementation/resources exist, not
that an exhaustive in-game equivalence test was performed.

| System / evidence | Current fork and actual gap | Value / risk | Decision |
| --- | --- | --- | --- |
| Height and wall fog experiments in `resourcepacks/fog` | Core linear fog remains; finite volume effects are absent. The old height shader reads uninitialized `Intersection.hit` and divides by a parallel-plane dot product. | High / medium | Rewrite height fog and a reusable finite-segment helper. Wall effects can use the box effect or a rotated coordinate system in a custom shader. |
| Box/cube fog experiments | Six-plane trial-and-error code does not consistently clip both entry and exit. The include variant references undefined `PLANE` / `intersectPlane`; it colors faces rather than behaving as reusable fog. | High / medium | Rewrite with a three-axis slab test, scene-depth limit, parallel handling, reversed corners and empty-volume handling. |
| Legacy `noise.glsl` | No equivalent reusable procedural-noise include in current Pinwheel includes. Old `magicEnergyEffect` relies on undeclared global `time`; octave count is unbounded and interpolation is only linear. | High / low–medium | New namespaced include with smooth value noise, normalized fBm, explicit time and eight-octave limit. Opt-in; not part of fog's per-pixel cost. |
| Legacy common math and ray/sphere intersections | Current `space_helper` already handles depth reconstruction, FOV, aspect and transformations. Old `getWorldDepth` ignores UV/FOV/aspect; sphere helper has awkward inside/outside results. | Medium / medium | Use current space helpers. Do not restore redundant reconstruction or old sphere API; a tested general geometry include can be a later feature. |
| Legacy luma | Current `color_utilities` has gamma, HSV, luminance and ACES. Old luma uses Rec.601-style coefficients; current luminance is approximate. | Low / low | Do not add synonymous functions or silently change existing color output. Explicit color-space utilities are a separate design task. |
| Black-hole pack | Four files exist under `venuslibs`; fragment source is under `framebuffers`, not `shaders/program`. The pack is not registered by old `VeilBuiltinPacks.registerPacks`. | Potentially high / high | Exclude incomplete prototype. Requires a valid program/pipeline, texture inputs and visual validation. |
| Skybox / space demos and cubemap texture | Demo assets are absent; cubemap/array textures, debug shaders and perspective rendering remain. A demo is not a new renderer subsystem. | Medium / medium | Exclude hard-coded demo scene/texture assets; keep current texture and perspective APIs. |
| Particle demo pack | 79 particle-pack resource files are absent; Quasar itself remains. Data changed from separate init/update/render module registries to a unified `modules` representation. | High for examples / medium | Do not copy old JSON unmodified. A migrated, codec-tested showcase pack is pending; forces, trails, collisions, subemitters and lights are retained in current classes. |
| Shader modifier demo pack / lexer and parser | Old `.txt` modifier language and parser classes are absent. `ShaderModificationManager` now has deprecated no-op methods; current code uses `ShaderInjectionManager`, loader/adapters and JSON injections. | High migration need / high | Document migration; do not restore a second shader transformation engine or ship dead `.txt` samples. |
| Mutable framebuffer attachment | Public old class moved to `impl/client/render/framebuffer`. Builder and mutable texture operations remain. | Low / medium | Keep ownership boundaries; do not expose the internal class or duplicate texture lifetime logic. |
| `MutableUniformAccess`, `getOrCreateUniform`, sampler methods | Block setters are now on `UniformAccess`; current access uses `getUniform` / `getUniformSafe`, texture targets and sampler IDs via `setTexture`. | High migration need / medium | Use current APIs. Do not restore obsolete interfaces or sampler aliases. |
| Dynamic pipeline registration / cloning | Old `PostProcessingManager.registerPipeline` and `clonePipeline` are absent; resource-backed pipelines and explicit execution remain. | Medium / medium–high | Real API gap, deferred: requires clear ownership, reload replacement and GPU-resource disposal contracts, rather than restoring old signatures alone. |
| `LayeredRenderTypePreEvent` | Event and Fabric bridge are absent while layered drawing remains. This is a real missing per-layer callback, not a renamed event. | Medium–high / high | Deferred. Design before/after lifecycle and shader access for both loaders, then test per-layer state restoration in-game. |
| Lighting, ambient occlusion controls | Point/area/directional lighting and indirect rendering remain; spotlights, voxel occlusion and light in-scattering are newer. Old ambient-occlusion enable/disable/isEnabled controls are absent. | Medium / high | Do not revive renderer-wide AO toggles without current compositing and Sodium tests. Existing per-light occlusion controls are available. |
| Dynamic depth buffer and vertical normal wrapper | `DynamicBufferType.DEPTH` and old wrappers/mixins are absent. Current framebuffer depth sampling and dynamic-buffer compiler remain. | Medium / high | Use `DiffuseDepthSampler` for these effects. Do not reinstate old buffer ordinals or CPU vertex wrappers. Exact old AO/normal behavior requires scene tests. |
| ImGui runtime, state stack, font manager | Old embedded ImGui runtime/font APIs are absent. Current editor uses optional **ImGuiMC 2.x**, with its context/fonts/language APIs. | High migration need / high | Keep ImGuiMC integration. Reintroducing embedded contexts/input hooks can conflict with the new renderer and native libraries. |
| JetBrains Mono styles / language helpers | Four old fonts and metadata are absent; old `VeilLanguageDefinitions` source is commented out and emits no class. Resource language selection uses `TextEditorLanguage` instead. | Low / medium | No font/binary copy. Use the dependency's language/font facilities; do not resurrect the commented legacy bindings. |
| Shader, texture, framebuffer, resource, post, particle and light inspectors | Current `VeilImGuiCompat` registers these inspectors when ImGuiMC integration loads. | Retained / low | No duplicate tooling. Shader/resource editing remains available with the optional dependency installed. |
| Pipeline statistics viewer | Viewer/profiler classes remain, but inspector registration is explicitly commented out. Plot calls changed for the newer binding; profiler still synchronously reads `GL_QUERY_RESULT`. | High / high | Real tooling regression, deferred. Fix nesting, plot style balance and query collection first; enabling it blindly can stall the GPU. |
| Necromancer skeletal animation | Bone, skin, skeleton, renderers and keyframes remain; newer additive/looped animation and interpolation modes exist. Old mirrored-index convenience and one Skin builder constructor are absent. | Low–medium / medium | No old skinned shader or builder copy. Current rendering resources changed; verify exact convenience needs before adding aliases. |
| Performance / scheduling / GL utilities | Tick/thread schedulers, indirect light compute, multi-bind, instanced rendering, VAOs and debug extensions remain. Texture binding is more explicit; Quasar render styles now own instancing buffers/lifecycle. | Retained / high for backports | No demonstrated old optimization missing that is safe to restore. Counts and source inspection are not benchmarks; do not claim FPS gains. |
| Other utility removals | `Color.RGB_CODEC_FLOATS`, bridge dither/line-smoothing helpers and several record-style equality methods are absent or changed. Current codec/vector/matrix and effect APIs are broader. | Low / medium | Listed with exact descriptors in the appendix. No blanket binary-compatibility aliases or changes to existing equality/render state. |

## Delivered behavior

- `#include veil:fog_volume`: finite height/box path lengths, transmittance and
  alpha-preserving fog compositing.
- `#include veil:procedural_noise`: hash, smooth value noise, bounded fBm and
  explicit-time domain-warped energy noise.
- Shader and post-pipeline IDs `veil:effects/height_fog` and `veil:effects/box_fog`.
  Pipelines are available for explicit use, **not activated automatically**.
- [Usage and coordinate contracts](volume-effects.md), shader compilation and
  opt-in GPU numerical tests, and resource/test-aware CI on PRs targeting `main`.

No Java production API, mixin, dependency version, loader metadata, screen shake,
voxel state handling or existing shader was modified. Test libraries are test-only.
The project version stays unreleased at `4.5.0+mystrix.1`; assign a new version when releasing.

## Validation and practical limits

The baseline build passed before edits. The adapted branch is checked with common
tests and both loader builds. Shader tests expand these effects' imports and use
the real camera layout plus Veil's GLSL parser/writer. Shaderc validates GLSL 330
for OpenGL-target SPIR-V, substituting `std140` for OpenGL's legal `shared` camera
layout only in the test. This is not a full resource-manager reload test.

The opt-in GPU test executes the **shipped includes**, rather than a Java rewrite,
via transform feedback in a hidden OpenGL 3.3 context. It checks parallel rays,
inside/outside/crossing/occluded segments, reversed/empty boxes, zero distance,
negative/zero density, alpha preservation and octave budgets/ranges.
The GPU test also compiles and links both fog programs with the real camera fields
and OpenGL `shared` layout. Local validation uses an NVIDIA GeForce GTX 1050.

Full Minecraft scene validation remains pending: camera bob/shake and perspective
views, resize/reload, sky depth, transparent geometry, shader-pack/Sodium paths,
NeoForge runtime, and AMD/Intel drivers. Transparent surfaces that do not write
depth cannot bound a depth-based post effect. Fog is homogeneous absorption, not
multiple scattering or a replacement for Veil's light in-scattering.
Enable each additional effect only when needed; each is a fullscreen pass.

Final local validation: **`gradlew.bat build` passed using JDK 21.0.7**, with
`VEIL_VERIFY_OPENGL=1`: eight common tests, zero failures/errors/skips. Both
Fabric and NeoForge release jars contain all eight new resources, use Java class
major version 65, and contain neither shaderc nor the test classes. Git diff
whitespace validation passed. Remote CI status is reported on the PR separately.

Existing baseline build warnings include Loom parsing unexpanded metadata,
unremappable lambda targets, deprecated shader-modification APIs and Gradle
deprecations. They were not introduced or suppressed by this change.

## Pending work in priority order

1. Runtime scene/driver matrix for the new effects before release.
2. Repair and re-enable GPU statistics with asynchronous query handling.
3. Migrate a small Quasar showcase through current codecs and editor workflows.
4. Define a cross-loader per-layer event with paired cleanup guarantees.
5. Define dynamic pipeline registration/cloning ownership and reload semantics.
6. Rebuild black-hole/sky/geometry prototypes as separate opt-in, tested features.

The old ZIP does not contain evidence of a production PBR/GI/SSR/TAA renderer to
recover. Such features would require new design and profiling beyond this port.
