package local.gdbridge.mixin;
import local.gdbridge.GDBridge;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method="onMouseButton",at=@At("HEAD"),cancellable=true)
    private void gdMouse(long window,int button,int action,int modifiers,CallbackInfo ci){
        MinecraftClient mc=MinecraftClient.getInstance();
        if(GDBridge.active()&&mc.currentScreen==null&&mc.isWindowFocused()){
            if(button==GLFW.GLFW_MOUSE_BUTTON_LEFT&&(action==GLFW.GLFW_PRESS||action==GLFW.GLFW_RELEASE))GDBridge.sendJump(action==GLFW.GLFW_PRESS);
            // Gameplay clicks belong to GD; vanilla clicks must not edit the persistent level.
            ci.cancel();
        }
    }
}
