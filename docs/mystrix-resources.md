# Mystrix resources and Veil 2.1.0 example packs

The canonical client-resource root is now `assets/<namespace>/mystrix/`.
The mod ID and program/pipeline IDs remain `veil`; for example,
`veil:effects/height_fog` is unchanged. This is a directory migration, not a
Java package or resource-namespace rename.

## Legacy path compatibility

`pinwheel/` is a read alias for `mystrix/`, in either direction. Each pack is
adapted **before** Minecraft merges it with other packs. Therefore:

- A higher-priority legacy user pack overrides the library's canonical files.
- Canonical files win over legacy aliases in the **same** pack.
- Resource stacks retain pack order and contain one logical file per pack.
- Pack filters cover both spellings; legacy metadata cannot attach to a
  canonical file in the same pack when canonical metadata is absent.
- The editor displays canonical IDs while keeping physical file paths,
  writable roots, icons and file watchers for existing legacy directories.
- Server data is not aliased. Namespaces and resource IDs are not renamed.

Compatibility applies to resource paths and current shader-injection targets.
It does not reinstate the removed `.txt` shader-modifier engine or obsolete
Quasar schemas. The shipped 2.1.0 examples have been migrated to current formats.
New packs should write only the canonical directory; they need no duplicate files.

## Complete source inventory

[asset-migration.json](audit/asset-migration.json) accounts for all **210** asset
and resource-pack files in the supplied archive, with original paths, destination
paths and SHA-256 hashes. Of these, 75 overlapping files retain current fork
content, 66 are imported without content changes, 68 are adapted, and one deprecated rotation
sample is replaced by particle settings and preserved as a migration reference.
The archive has 91 ordinary assets and 119 files in four embedded packs.

Missing ordinary assets include all four JetBrains Mono styles and their
metadata, the OBJ test model and legacy shader references. Font metadata now
uses ImGuiMC's `imguimc:font_ranges` section. Font binaries remain unmodified;
their [upstream OFL license](https://github.com/JetBrains/JetBrainsMono/blob/master/OFL.txt)
is bundled at `licenses/jetbrains-mono-OFL.txt`. Legacy shaders in
`assets/veil/shaders_legacy/` are reference material, not registered shader programs.
Existing light, compositing, particle and skinned-mesh shaders retain their
newer implementation.

## Optional packs

Both Fabric and NeoForge expose all four packs in Minecraft's resource-pack
selection screen, labelled **Mystrix** and disabled by default. Packs supply
examples; selecting a fog/black-hole pack makes its pipelines available but does
not activate those fullscreen effects automatically.

| Pack | Shipped files | Behavior and adaptation |
| --- | ---: | --- |
| `test_particles` | 81 | Eight original emitters plus a missing `swiftshot` emitter; unified module registry and `modules` arrays, repaired shrapnel velocity, lights, trails, forces and collision examples. |
| `test_shaders` | 20 | Test framebuffer/program, empty first-person pipeline and five current JSON injection examples. Original `.txt` samples are outside `assets`, under `migration_reference`. |
| `fog` | 25 | Linear, height, wall and box fog; cubemap skybox and local-space debug view. Height/wall/box programs use tested finite-segment intersection and preserve alpha. |
| `black_hole` | 5 | Framebuffer, correctly located fragment, shader definition and newly supplied pipeline. Artistic lensing/disk prototype with bounded 128-step loop, explicit time, depth limit and finite denominators. |

Fabric pack IDs are `veil:<pack>`. NeoForge IDs are
`mod/veil:resourcepacks/<pack>`. Physical directories are
`resourcepacks/<pack>/assets/.../mystrix/...` in both release jars.

The shader test pack intentionally replaces `minecraft:rendertype_cutout` with
the magenta `veil:test` shader when selected. This is a demonstration, not a
production visual preset. Its Sodium example records clip position without
depending on a specific release's private vertex attributes. The old deferred
helper injection targeted a non-main function; its adapted example demonstrates
including color utilities in the fullscreen vertex program instead. Original
samples remain available for comparison, but are not loaded by the renderer.

Quasar smoke rotation moved from deprecated `init_random_rotation` to particle
settings, with a Z rotation centered at 127 degrees and a 66-degree variation.
The new `veil:bounce` collision module uses restitution in `[0,1]`, reflects only
blocked block-collision axes and preserves tangential velocity. The sample uses
0.6 instead of the old unbounded 2.0 setting. Entity overlaps without a resolved
block axis retain the existing stop behavior.

## Using the effects

Pipeline IDs supplied by `fog`: `veil:fog`, `veil:height_fog`, `veil:wall_fog`,
`veil:cube_fog`, `veil:skybox`, `veil:space_test`.
`veil:cube_fog_clone` is a shader-definition alias for the repaired cube program;
it no longer depends on an absent `bordercreator` mod.

Height fog accepts `FogHeight`, `FogDensity` and `FogColor`. Wall fog uses
`FogHeight` as the X-plane position. Cube fog accepts world-space corners `pos1`
and `pos2`, density and color. These shader uniforms are set through current
pipeline uniform access before execution. See [volume-effects.md](volume-effects.md)
for pipeline execution and camera-space contracts of the library's core effects.

The black-hole pipeline is `venuslibs:black_hole`. Set `BlackHolePosition` in
world space, `BlackHoleScale` as radius in blocks, and `TimeSeconds` explicitly
before execution. Non-positive scale disables it. The background warp is a
screen-space approximation; it cannot reveal geometry missing from the source
image. This prototype is not a physical relativistic simulation.

## Validation and remaining work

Automated tests cover all example shader stages through the current GLSL
parser/writer and shaderc, particle/subemitter reference closure, shader-definition
references, pack priority/stacks/filters, editor paths and bounded bounce response.
The opt-in OpenGL test executes the shipped fog/noise includes on a GTX 1050.
Release jars are checked for every manifest destination, all pack metadata,
canonical resource roots and Java 21 bytecode.

Fabric 0.18.4 and NeoForge 21.1.219 on Minecraft 1.21.1 successfully start with all four packs enabled:
32 shader programs, four redirects, one replacement, five framebuffers,
13 pipelines and nine Quasar emitters load without resource/codec errors.
This verifies resource loading and compilation, not visual quality in a world.

Before release, validate actual effect execution and particle collisions in
scenes, camera bob/shake, resize/hot reload, transparent geometry, Sodium/Iris,
optional ImGuiMC editing and AMD/Intel drivers. The broader API/tooling gaps
remain documented in [the distribution audit](veil-2.1.0-audit.md).
