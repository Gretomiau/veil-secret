# Veil 4.5.0 + Mystrix patch 1

This patch targets the Minecraft 1.21.1 Fabric build of Veil 4.5.0.

## Fixed crash

The upstream screen-shake mixins captured `@Local(name = "f") float partialTick`. On the target runtime the local-variable name was not available, producing `Unable to find matching local` and aborting the `GameRenderer` transformation.

The patch now captures the stable `DeltaTracker` method argument with `@Local(argsOnly = true)` and obtains partial tick through `getGameTimeDeltaPartialTick(true)`.

The screen-shake mixin group also uses `defaultRequire = 0`, so a future camera-transform conflict disables only the unmatched injection rather than taking down all of Veil.

Version marker: `4.5.0+mystrix.1`.
