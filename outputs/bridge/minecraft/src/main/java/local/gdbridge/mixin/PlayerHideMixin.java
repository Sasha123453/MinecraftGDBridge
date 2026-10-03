package local.gdbridge.mixin;
import local.gdbridge.GDBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** The real Minecraft player is a camera carrier; its equipment is never GD geometry. */
@Mixin(EntityRenderDispatcher.class)
public abstract class PlayerHideMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void hideCameraCarrier(Entity entity,double x,double y,double z,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider buffers,int light,CallbackInfo ci){
        if(entity==MinecraftClient.getInstance().player&&GDBridge.active())ci.cancel();
    }
}
