package foundry.veil.mixin.screenshake.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import foundry.veil.api.client.render.VeilRenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mystrix compatibility patch for Minecraft 1.21.1.
 *
 * <p>Upstream Veil 4.5.0 captured a local float by its LVT name ({@code f}). In some production
 * Fabric runtimes that local name is unavailable after transformation, which causes MixinExtras
 * to fail with "Unable to find matching local" and prevents GameRenderer from loading.
 *
 * <p>The render method already receives {@link DeltaTracker} as a stable method argument, so this
 * patch captures that argument and derives the partial tick from it instead. This preserves the
 * Veil 4.5 screen-shake feature while avoiding dependence on local-variable debug names.</p>
 */
@Mixin(GameRenderer.class)
public class ScreenShakeGameRendererMixin {

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 veil$moveCameraPosition(Camera instance, Operation<Vec3> original,
                                         @Local(argsOnly = true) DeltaTracker deltaTracker) {
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        Vector3f pos = VeilRenderSystem.renderer().getScreenShakeManager().getPosition(partialTick);
        return original.call(instance).add(pos.x, pos.y, pos.z);
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;rotation()Lorg/joml/Quaternionf;"))
    private Quaternionf veil$offsetCameraRotation(Camera instance, Operation<Quaternionf> original,
                                                   @Local(argsOnly = true) DeltaTracker deltaTracker) {
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        Vector3f offset = VeilRenderSystem.renderer().getScreenShakeManager().getPosition(partialTick).mul(0.1f);
        return original.call(instance).rotateXYZ(offset.x, offset.y, offset.z, new Quaternionf());
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void veil$tickScreenShake(CallbackInfo ci) {
        if (VeilRenderSystem.renderer() != null) {
            VeilRenderSystem.renderer().getScreenShakeManager().tick();
        }
    }
}
