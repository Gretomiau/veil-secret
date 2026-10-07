package foundry.veil.mixin.resource.client;

import foundry.veil.impl.resource.MystrixPackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(MultiPackResourceManager.class)
public abstract class ResourceMystrixAliasesMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static List<PackResources> veil$aliasResources(List<PackResources> packs, PackType type, List<PackResources> original) {
        return type == PackType.CLIENT_RESOURCES
                ? packs.stream().map(pack -> pack instanceof MystrixPackResources ? pack : new MystrixPackResources(pack)).toList()
                : packs;
    }
}
