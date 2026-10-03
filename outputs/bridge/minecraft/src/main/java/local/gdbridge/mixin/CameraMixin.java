package local.gdbridge.mixin;
import local.gdbridge.GDBridge;
import local.gdbridge.BridgeCamera;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPos(double x,double y,double z);
    @Shadow protected abstract void setRotation(float yaw,float pitch);
    @Inject(method="update",at=@At("TAIL"))
    private void gdCamera(BlockView world,Entity entity,boolean thirdPerson,boolean inverseView,float delta,CallbackInfo ci){
        var f=GDBridge.captureRenderFrame();if(f==null||!GDBridge.active())return;
        double vertical=BridgeCamera.followY(f);
        setPos(f.x()/30+BridgeCamera.offsetX,64+vertical,BridgeCamera.distance);
        setRotation((float)BridgeCamera.yaw,(float)BridgeCamera.pitch);
    }
}
