package local.gdbridge;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
public final class GDPlayerRenderer extends EntityRenderer<GDRenderEntity> {
    public GDPlayerRenderer(EntityRendererFactory.Context context){super(context);shadowRadius=.5f;}
    @Override public Identifier getTexture(GDRenderEntity entity){return new Identifier("minecraft","textures/block/white_concrete.png");}
    @Override public boolean shouldRender(GDRenderEntity entity,Frustum frustum,double x,double y,double z){
        if(!GDBridge.active())return false;var frame=GDBridge.getRenderFrame();return entity.playerIndex==0||frame.packet().has("player2")&&frame.packet().getAsJsonObject("player2").get("active").getAsBoolean();
    }
    @Override public void render(GDRenderEntity entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider buffers,int light){
        var frame=GDBridge.getRenderFrame();if(frame==null||!GDBridge.active())return;
        var data=entity.playerIndex==0?frame.packet():frame.packet().getAsJsonObject("player2");
        // WorldRenderer translates by this interpolated origin, not raw tick position.
        double ox=net.minecraft.util.math.MathHelper.lerp(delta,entity.lastRenderX,entity.getX()),oy=net.minecraft.util.math.MathHelper.lerp(delta,entity.lastRenderY,entity.getY()),oz=net.minecraft.util.math.MathHelper.lerp(delta,entity.lastRenderZ,entity.getZ());
        NativeGDVisuals.renderWorldLit(matrices,buffers,data,ox,oy,oz,light);
        if(entity.playerIndex==0)GDBridge.renderObjectsLit(matrices,buffers,frame,ox,oy,oz);
    }
}
