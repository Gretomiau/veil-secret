# Finite volume fog and procedural noise

These resources adapt useful capabilities from the supplied Veil 2.1.0
distribution to current Veil 4.5. Minecraft 1.21.1, Java 21, Fabric and NeoForge
share the same resources. Nothing is enabled by default.

## Height fog

The post pipeline and shader both use `veil:effects/height_fog`.
`FogHeight` is an absolute world Y coordinate, `FogDensity` is nonnegative inverse
blocks, and `FogColor` is RGBA in the same color space as the input framebuffer.
Default values are Y=64, density=0.05 and color=(0.5, 0.6, 0.7, 1).

On the render thread, after resources have loaded:

```java
var manager = VeilRenderSystem.renderer().getPostProcessingManager();
var id = ResourceLocation.fromNamespaceAndPath("veil", "effects/height_fog");
var pipeline = manager.getPipeline(id);
if (pipeline != null) {
    pipeline.getUniformSafe("FogHeight").setFloat(70.0F);
    pipeline.getUniformSafe("FogDensity").setFloat(0.03F);
    pipeline.getUniformSafe("FogColor").setVector(0.25F, 0.35F, 0.45F, 0.8F);
    manager.add(id);
}
// Later: manager.remove(id);
```

Uniform changes should be reapplied after resource reload: do not retain a stale
pipeline object. This uses current `getUniformSafe`, not the removed
`getOrCreateUniform` API. Zero/negative density or zero fog alpha has no effect.

## Box fog and large coordinates

Use `veil:effects/box_fog`. Its `FogBoxMin` and `FogBoxMax` uniforms are
**camera-relative world-axis bounds**, measured in blocks. Subtract the exact
current Veil camera position from world bounds before every pipeline execution.
Defaults are (-8,-8,-8) to (8,8,8), which intentionally follow the camera.

```java
var camera = VeilRenderSystem.renderer().getCameraMatrices().getCameraPosition();
var pipeline = manager.getPipeline(ResourceLocation.fromNamespaceAndPath("veil", "effects/box_fog"));
if (pipeline != null) {
    pipeline.getUniformSafe("FogBoxMin").setVector(minX - camera.x(), minY - camera.y(), minZ - camera.z());
    pipeline.getUniformSafe("FogBoxMax").setVector(maxX - camera.x(), maxY - camera.y(), maxZ - camera.z());
}
```

Here `minX` etc. must be floats, or explicitly cast the final differences to
float after subtracting in double precision. Update at the render point that
matches the camera UBO, including perspective rendering. A fixed world volume
needs this per-render update; setting the bounds once causes it to move with the
camera. The shader reconstructs the eye from `IViewMat` and the visible endpoint
from depth, so view bob is accounted for through the current matrices.

Reversed corners are supported. A box with any zero extent is empty.
Opaque surfaces in front of the volume prevent fog behind them from contributing.
The sample depth at sky pixels limits the segment to the projection's far plane.
Geometry without depth writes cannot clip it. Each enabled effect costs one
fullscreen pass and two scene texture samples; box intersection uses three axes.

## Reusable include

```glsl
#include veil:fog_volume
float distance = veilFogBoxLength(start, end, cornerA, cornerB);
vec4 result = veilApplyVolumeFog(source, fogColor, distance, density);
```

All positions must be finite and share the same coordinate system; local space
is preferred. Segments need no normalized direction. Height fog treats a segment
lying on its plane as inside. A parallel segment on a nonempty box face is also
inside. Alpha is preserved, and density/alpha are clamped to sensible ranges.
Use the functions in rotated local coordinates for an oriented box or wall.

The built-in pipeline reads `minecraft:main` and draws to Veil's post target.
For chaining, follow the existing post-manager/framebuffer semantics or create
your own pipeline with separate intermediate targets; never read/write the same
texture in one stage. Linear and box fog effects are absorption approximations;
they do not add shadowed volumetric scattering.

## Procedural noise

```glsl
#include veil:procedural_noise
float smoke = veilFbm3(localPosition * 0.2, 3);
float energy = veilEnergyNoise3(localPosition, timeSeconds);
```

`veilHash13`, `veilValueNoise3`, `veilFbm3` and `veilEnergyNoise3` have no hidden
uniforms, texture dependencies or mutable state. Noise is in [0,1] within floating
point precision. fBm clamps octaves to 0–8, returns zero for zero/negative octaves
and normalizes accumulated weights. Prefer 2–4 octaves in fullscreen shaders.
Energy noise uses three two-octave warps and a three-octave final sample, so it is
more expensive than plain noise. Keep coordinates small for float precision.
The smoother/normalized output intentionally differs from old `cubidNoise`/`fbm3`.

## Verification

```sh
./gradlew :common:test :fabric:build :neoforge:build
VEIL_VERIFY_OPENGL=1 ./gradlew :common:test --rerun-tasks
```

On Windows cmd, set `VEIL_VERIFY_OPENGL=1` before running `gradlew.bat`.
Default tests compile GLSL without a display. GPU tests are opt-in and require a
working OpenGL 3.3 desktop driver; an explicitly requested run fails if unavailable.
Neither shaderc nor the test native libraries are included in release jars.
