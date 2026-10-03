package local.gdbridge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.*;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

/** Original procedural pixel surfaces; Minecraft block textures stay pack-aware. */
public final class PixelSpecialMaterials {
    private static final Identifier INTERIOR=new Identifier("gdbridge","pixel_portal_interior");
    private static final Identifier LIGHT=new Identifier("gdbridge","pixel_special_light");
    private static boolean initialized;
    private PixelSpecialMaterials(){}
    private static void initialize(){
        if(initialized)return;
        NativeImage interior=new NativeImage(32,64,false),light=new NativeImage(16,16,false);
        for(int y=0;y<64;y++)for(int x=0;x<32;x++){
            int noise=Math.floorMod((x*73)^(y*37),5),value=9+noise;
            if((x+y*3)%29==0&&x>5&&x<27)value=24;
            if((x*11+y*7)%113==0)value=55;
            interior.setColor(x,y,0xFF000000|(value<<16)|(value<<8)|value);
        }
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){
            int value=(x==0||y==0||x==15||y==15)?218:246+Math.floorMod(x*3+y*5,10);
            light.setColor(x,y,0xFF000000|(value<<16)|(value<<8)|value);
        }
        var textures=MinecraftClient.getInstance().getTextureManager();
        textures.registerTexture(INTERIOR,new NativeImageBackedTexture(interior));textures.registerTexture(LIGHT,new NativeImageBackedTexture(light));initialized=true;
    }
    public static VertexConsumer block(VertexConsumerProvider buffers,String name){var sprite=MinecraftClient.getInstance().getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getSprite(new Identifier("minecraft","block/"+name));return sprite.getTextureSpecificVertexConsumer(buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)));}
    public static VertexConsumer interior(VertexConsumerProvider buffers){initialize();return buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(INTERIOR));}
    public static VertexConsumer glow(VertexConsumerProvider buffers){initialize();return buffers.getBuffer(NativeGDRenderLayers.get(LIGHT,GL11.GL_ONE,GL11.GL_ONE));}
    public static int tint(int color,double gain){int r=(int)Math.min(255,Math.max(0,((color>>16)&255)*gain)),g=(int)Math.min(255,Math.max(0,((color>>8)&255)*gain)),b=(int)Math.min(255,Math.max(0,(color&255)*gain));return (r<<16)|(g<<8)|b;}
    public static int whiteMix(int color,double amount){int r=(int)(((color>>16)&255)*(1-amount)+255*amount),g=(int)(((color>>8)&255)*(1-amount)+255*amount),b=(int)((color&255)*(1-amount)+255*amount);return (r<<16)|(g<<8)|b;}
}
