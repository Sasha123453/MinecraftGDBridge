package local.gdbridge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

/** Small cosmetic light spill on actual solid block tops, supplementing vanilla blocklight.
 * This is a depth-tested surface glow, not colored global illumination or a collider. */
public final class PortalSurfaceGlow {
    private static final Identifier TEXTURE=new Identifier("gdbridge","special_surface_glow");
    private static boolean initialized;
    private PortalSurfaceGlow(){}
    private static void initialize(){
        if(initialized)return;
        NativeImage image=new NativeImage(32,32,false);
        for(int y=0;y<32;y++)for(int x=0;x<32;x++){
            double dx=(x+.5)/16-1,dy=(y+.5)/16-1;
            int value=(int)(255*Math.pow(Math.max(0,1-dx*dx-dy*dy),2));
            // Premultiplied RGB is required by ONE/ONE additive blending.
            image.setColor(x,y,(value<<24)|(value<<16)|(value<<8)|value);
        }
        MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE,new NativeImageBackedTexture(image));initialized=true;
    }
    public static void render(MatrixStack m,VertexConsumerProvider buffers,GDBridge.Frame frame,double ox,double oy,double oz){
        var world=MinecraftClient.getInstance().world;
        if(world==null||frame==null||!BridgeVisualStyle.volume())return;
        initialize();VertexConsumer v=buffers.getBuffer(NativeGDRenderLayers.get(TEXTURE,GL11.GL_ONE,GL11.GL_ONE));
        int count=0;
        for(var object:frame.objects()){
            if(count>=24)break;
            if(!object.visualEnabled()||!NativeObjectBodies3D.bodyOnly(object)||Math.abs(object.bodyX()-frame.x())>900)continue;
            boolean portal=object.type().equals("portal");
            int id=object.objectId();boolean pad=id==35||id==67||id==140||id==1332||id==3004;
            double cx=object.bodyX()/30,cy=64+object.bodyY()/30,cz=.5;
            double radius=portal?1.65:pad?.65:.85;
            int color=WorldEditor.worldName.equals("GDBridge-Reference")&&portal&&id==12?0x42E6F8:object.visualColor();
            if(color<0)continue;
            count++;double gain=portal?.30:pad?.12:.16;
            int rgb=PixelSpecialMaterials.tint(color,gain);
            for(int x=(int)Math.floor(cx-radius);x<=Math.floor(cx+radius);x++)for(int z=(int)Math.floor(cz-radius);z<=Math.floor(cz+radius);z++){
                // Follow actual block tops. Never draw a glow in empty space,
                // on vegetation, through a higher solid block, or on an invented floor.
                for(int y=(int)Math.floor(cy);y>=Math.floor(cy)-5;y--){
                    BlockPos pos=new BlockPos(x,y,z);var state=world.getBlockState(pos);
                    if(!state.isFullCube(world,pos))continue;
                    if(!world.getBlockState(pos.up()).isAir())break;
                    double top=y+1.006;if(top>cy+.30)break;
                    double attenuation=Math.max(.20,1-Math.max(0,cy-top-1)*.18);
                    int shaded=PixelSpecialMaterials.tint(rgb,attenuation);
                    double x0=Math.max(x,cx-radius),x1=Math.min(x+1,cx+radius),z0=Math.max(z,cz-radius),z1=Math.min(z+1,cz+radius);
                    if(x0>=x1||z0>=z1)break;
                    emit(v,m,x0-ox,top-oy,z0-oz,(x0-cx+radius)/(2*radius),(z0-cz+radius)/(2*radius),shaded);
                    emit(v,m,x0-ox,top-oy,z1-oz,(x0-cx+radius)/(2*radius),(z1-cz+radius)/(2*radius),shaded);
                    emit(v,m,x1-ox,top-oy,z1-oz,(x1-cx+radius)/(2*radius),(z1-cz+radius)/(2*radius),shaded);
                    emit(v,m,x1-ox,top-oy,z0-oz,(x1-cx+radius)/(2*radius),(z0-cz+radius)/(2*radius),shaded);
                    break;
                }
            }
        }
    }
    private static void emit(VertexConsumer v,MatrixStack m,double x,double y,double z,double u,double vv,int color){
        v.vertex(m.peek().getPositionMatrix(),(float)x,(float)y,(float)z).color((color>>16)&255,(color>>8)&255,color&255,255).texture((float)u,(float)vv).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(m.peek().getNormalMatrix(),0,1,0).next();
    }
}
