package local.gdbridge.mixin;
import local.gdbridge.GDBridge;
import local.gdbridge.WorldEditor;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Inject(method="onKey",at=@At("HEAD"))
    private void gdKey(long window,int key,int scancode,int action,int modifiers,CallbackInfo ci){
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.currentScreen!=null||!mc.isWindowFocused()||mc.world==null)return;
        if(action==GLFW.GLFW_PRESS&&key==GLFW.GLFW_KEY_F7){WorldEditor.toggle();return;}
        if(action==GLFW.GLFW_PRESS&&key==GLFW.GLFW_KEY_F6){WorldEditor.export();return;}
        if(!GDBridge.active())return;
        if((key==GLFW.GLFW_KEY_SPACE||key==GLFW.GLFW_KEY_UP)&&(action==GLFW.GLFW_PRESS||action==GLFW.GLFW_RELEASE))GDBridge.sendJump(action==GLFW.GLFW_PRESS);
        if(key==GLFW.GLFW_KEY_R&&action==GLFW.GLFW_PRESS)GDBridge.sendRestart();
    }
}
