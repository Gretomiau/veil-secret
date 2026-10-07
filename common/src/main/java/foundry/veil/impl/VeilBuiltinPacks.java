package foundry.veil.impl;

import java.util.List;

/** Optional examples shared by both loaders. Never activate these automatically. */
public final class VeilBuiltinPacks {
    public static final List<String> IDS = List.of("test_particles", "test_shaders", "fog", "black_hole");
    private VeilBuiltinPacks() { }
    public static String title(String id) { return "Mystrix: " + id.replace('_', ' '); }
}
