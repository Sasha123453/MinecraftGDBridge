package local.gdbridge.mixin;
import local.gdbridge.GDBridge;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(InGameHud.class)
public abstract class HudMixin {
    @Inject(method="renderHotbar",at=@At("HEAD"),cancellable=true)
    private void gdHotbar(float tickDelta,DrawContext context,CallbackInfo ci){if(GDBridge.active())ci.cancel();}
    @Inject(method="renderCrosshair",at=@At("HEAD"),cancellable=true)
    private void gdCrosshair(DrawContext context,CallbackInfo ci){if(GDBridge.active())ci.cancel();}
}
