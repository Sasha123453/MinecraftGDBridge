package local.gdbridge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;

/** Stone prism for verified native triangular spikes; no collision geometry changes. */
public final class NativeSpikes3D {
    private NativeSpikes3D() {}
    public static boolean supports(int objectId){return objectId==8;}
    public static boolean supports(GDBridge.Obj object){boolean strip=object.visualShape().equals("spike-strip");if(strip&&(object.spikePeaks()<2||object.spikePeaks()>128))return false;return (object.visualShape().equals("spike")||supports(object.objectId())||strip)&&Double.isFinite(object.bodyWidth())&&Double.isFinite(object.bodyHeight())&&object.bodyWidth()>0&&object.bodyHeight()>0&&object.bodyWidth()<=360&&object.bodyHeight()<=360;}
    public static void render(MatrixStack matrices,VertexConsumerProvider buffers,GDBridge.Obj object,double ox,double oy,double oz,int light){
        if(!supports(object))return;
        double width=object.bodyWidth()/30,height=object.bodyHeight()/30;
        if(width<=0||height<=0||width>12||height>12)return;
        var sprite=MinecraftClient.getInstance().getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getSprite(new Identifier("minecraft","block/stone"));
        VertexConsumer stone=sprite.getTextureSpecificVertexConsumer(buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)));
        matrices.push();matrices.translate(object.bodyX()/30-ox,64+object.bodyY()/30-oy,.71-oz);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)-object.bodyRotation()));
        int peaks=object.visualShape().equals("spike-strip")?object.spikePeaks():1;
        double peakWidth=width/peaks;
        for(int peak=0;peak<peaks;peak++){
            matrices.push();matrices.translate(-width/2+(peak+.5)*peakWidth,0,0);
            prism(matrices,stone,peakWidth,height,light);matrices.pop();
        }
        matrices.pop();
    }
    private static void prism(MatrixStack matrices,VertexConsumer stone,double width,double height,int light){
        double[][] front={{-width/2,-height/2,.30},{width/2,-height/2,.30},{0,height/2,.30}};
        double[][] back={{-width/2,-height/2,-.30},{width/2,-height/2,-.30},{0,height/2,-.30}};
        float u=(float)Math.min(1,width),v=(float)Math.min(1,height);
        face(matrices,stone,new double[][]{front[0],front[1],front[2],front[2]},new float[][]{{0,v},{u,v},{u/2,0},{u/2,0}},light);
        face(matrices,stone,new double[][]{back[2],back[1],back[0],back[0]},new float[][]{{u/2,0},{u,v},{0,v},{0,v}},light);
        for(int i=0;i<3;i++){
            int j=(i+1)%3;float edge=(float)Math.min(1,Math.hypot(front[j][0]-front[i][0],front[j][1]-front[i][1]));
            face(matrices,stone,new double[][]{front[i],back[i],back[j],front[j]},new float[][]{{0,0},{0,.6f},{edge,.6f},{edge,0}},light);
        }
    }
    private static void face(MatrixStack matrices,VertexConsumer consumer,double[][] vertices,float[][] uv,int light){
        double[] a=vertices[0],b=vertices[1],c=vertices[2];
        Vector3f normal=new Vector3f((float)(b[0]-a[0]),(float)(b[1]-a[1]),(float)(b[2]-a[2])).cross(new Vector3f((float)(c[0]-a[0]),(float)(c[1]-a[1]),(float)(c[2]-a[2]))).normalize();
        for(int i=0;i<4;i++){
            // Keep texture() on the sprite wrapper: chained vertex() returns its raw delegate.
            consumer.vertex(matrices.peek().getPositionMatrix(),(float)vertices[i][0],(float)vertices[i][1],(float)vertices[i][2]);
            consumer.color(255,255,255,255);consumer.texture(uv[i][0],uv[i][1]);
            consumer.overlay(OverlayTexture.DEFAULT_UV);consumer.light(light);
            consumer.normal(matrices.peek().getNormalMatrix(),normal.x,normal.y,normal.z);consumer.next();
        }
    }
}
