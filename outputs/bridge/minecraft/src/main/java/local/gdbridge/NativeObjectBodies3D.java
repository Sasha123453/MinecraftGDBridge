package local.gdbridge;

import net.minecraft.client.render.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;

/** Minecraft materials for native-sized playable objects; GD owns their behavior. */
public final class NativeObjectBodies3D {
    private static final int SEGMENTS=24;
    private NativeObjectBodies3D() {}
    public static boolean renderable(GDBridge.Obj object){return object.visualColor()>=0&&Double.isFinite(object.bodyWidth())&&Double.isFinite(object.bodyHeight())&&object.bodyWidth()>0&&object.bodyHeight()>0&&object.bodyWidth()<=360&&object.bodyHeight()<=360;}
    public static boolean bodyOnly(GDBridge.Obj object){return renderable(object)&&(object.type().equals("portal")||object.type().equals("orb"));}
    public static void render(MatrixStack matrices,VertexConsumerProvider buffers,GDBridge.Obj object,double ox,double oy,double oz,int light){
        // Never substitute canonical portal/orb colors for the live GD material.
        if(!renderable(object))return;
        double width=object.bodyWidth()/30,height=object.bodyHeight()/30;
        matrices.push();matrices.translate(object.bodyX()/30-ox,64+object.bodyY()/30-oy,.71-oz);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)-object.bodyRotation()));
        VertexConsumer material=blockMaterial(buffers,"white_concrete");
        if(object.type().equals("portal")) {
            VertexConsumer stone=blockMaterial(buffers,"obsidian");
            ring(matrices,material,stone,width*.47,height*.47,.77,.30,object.visualColor(),light);
        }else if(isPad(object.objectId())) {
            pad(matrices,material,blockMaterial(buffers,"stone_bricks"),blockMaterial(buffers,"gold_block"),width*.47,height*.44,.26,object.visualColor(),light);
        }else {
            ring(matrices,material,material,width*.45,height*.45,.80,.11,object.visualColor(),light);
            sphere(matrices,material,width*.34,height*.34,.29,object.visualColor(),light);
        }
        if(object.nativeAdditive()&&!isPad(object.objectId())){
            VertexConsumer glow=blockGlow(buffers);
            double rx=width*(object.type().equals("portal")?.375:.45),ry=height*(object.type().equals("portal")?.375:.45);
            glowRim(matrices,glow,rx,ry,object.visualColor());
        }
        matrices.pop();
    }
    private static boolean isPad(int id){return id==35||id==67||id==140||id==1332||id==3004;}
    private static VertexConsumer blockMaterial(VertexConsumerProvider buffers,String name){
        // Vanilla resource-pack sprites, not painted approximations. Entity cutout
        // binds the atlas with blur=false/mipmap=false, retaining nearest pixels.
        var sprite=MinecraftClient.getInstance().getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getSprite(new Identifier("minecraft","block/"+name));
        return sprite.getTextureSpecificVertexConsumer(buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)));
    }
    private static VertexConsumer blockGlow(VertexConsumerProvider buffers){var sprite=MinecraftClient.getInstance().getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getSprite(new Identifier("minecraft","block/white_concrete"));return sprite.getTextureSpecificVertexConsumer(buffers.getBuffer(NativeGDRenderLayers.get(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,org.lwjgl.opengl.GL11.GL_ONE,org.lwjgl.opengl.GL11.GL_ONE)));}
    private static void glowRim(MatrixStack matrices,VertexConsumer consumer,double rx,double ry,int color){for(int i=0;i<SEGMENTS;i++){double a=i*Math.PI*2/SEGMENTS,b=(i+1)*Math.PI*2/SEGMENTS;double[] oa=ellipse(rx,ry,a,.302),ob=ellipse(rx,ry,b,.302),ia=ellipse(rx*.965,ry*.965,a,.302),ib=ellipse(rx*.965,ry*.965,b,.302);quad(matrices,consumer,oa,ob,ib,ia,color,0xF000F0,faceUV(oa,ob,ib,ia,rx,ry));}}
    private static double[] ellipse(double rx,double ry,double angle,double z){return new double[]{rx*Math.cos(angle),ry*Math.sin(angle),z};}
    private static void ring(MatrixStack m,VertexConsumer front,VertexConsumer side,double rx,double ry,double inner,double depth,int color,int light){
        for(int i=0;i<SEGMENTS;i++){
            double a=i*Math.PI*2/SEGMENTS,b=(i+1)*Math.PI*2/SEGMENTS;
            double[] oa=ellipse(rx,ry,a,depth),ob=ellipse(rx,ry,b,depth),ia=ellipse(rx*inner,ry*inner,a,depth),ib=ellipse(rx*inner,ry*inner,b,depth);
            double[] oaa=ellipse(rx,ry,a,-depth),obb=ellipse(rx,ry,b,-depth),iaa=ellipse(rx*inner,ry*inner,a,-depth),ibb=ellipse(rx*inner,ry*inner,b,-depth);
            quad(m,front,oa,ob,ib,ia,color,light,faceUV(oa,ob,ib,ia,rx,ry));quad(m,front,oaa,iaa,ibb,obb,color,light,faceUV(oaa,iaa,ibb,obb,rx,ry));
            int sideColor=side==front?color:0xFFFFFF;
            double arc=Math.hypot(ob[0]-oa[0],ob[1]-oa[1]);float tileWidth=(float)Math.min(1,arc),tileDepth=(float)Math.min(1,depth*2);
            // One pixel per roughly 1/16 block, instead of stretching a complete
            // 16-pixel block texture across each narrow ring segment.
            float u0=(float)((i*arc)%1);if(u0+tileWidth>1)u0=0;
            float[][] wallUV={{u0,0},{u0,tileDepth},{u0+tileWidth,tileDepth},{u0+tileWidth,0}};
            quad(m,side,oa,oaa,obb,ob,sideColor,light,wallUV);quad(m,side,ia,ib,ibb,iaa,sideColor,light,wallUV);
        }
        // Deliberately no center cap: the world remains visible through a portal.
    }
    private static double[] spherePoint(double rx,double ry,double rz,double lat,double angle){return new double[]{rx*Math.cos(lat)*Math.cos(angle),ry*Math.cos(lat)*Math.sin(angle),rz*Math.sin(lat)};}
    private static void sphere(MatrixStack m,VertexConsumer consumer,double rx,double ry,double rz,int color,int light){
        for(int row=0;row<8;row++)for(int i=0;i<SEGMENTS;i++){
            double a=i*Math.PI*2/SEGMENTS,b=(i+1)*Math.PI*2/SEGMENTS,l0=-Math.PI/2+row*Math.PI/8,l1=-Math.PI/2+(row+1)*Math.PI/8;
            quad(m,consumer,spherePoint(rx,ry,rz,l0,a),spherePoint(rx,ry,rz,l0,b),spherePoint(rx,ry,rz,l1,b),spherePoint(rx,ry,rz,l1,a),color,light);
        }
    }
    private static void pad(MatrixStack m,VertexConsumer face,VertexConsumer stone,VertexConsumer gold,double rx,double ry,double depth,int color,int light){
        double bevel=Math.min(rx,ry)*.30;
        double[][] outline={{-rx+bevel,-ry},{rx-bevel,-ry},{rx,-ry+bevel},{rx,ry-bevel},{rx-bevel,ry},{-rx+bevel,ry},{-rx,ry-bevel},{-rx,-ry+bevel}};
        for(int i=0;i<outline.length;i++){
            double[] a=outline[i],b=outline[(i+1)%outline.length];
            double[] af={a[0]*.94,a[1]*.94,depth},bf={b[0]*.94,b[1]*.94,depth},ab={a[0],a[1],-depth},bb={b[0],b[1],-depth};
            double[] shoulderA={a[0],a[1],depth-.08},shoulderB={b[0],b[1],depth-.08};
            double[] insetA={a[0]*.76,a[1]*.76,depth+.001},insetB={b[0]*.76,b[1]*.76,depth+.001};
            float tileWidth=(float)Math.min(1,Math.hypot(b[0]-a[0],b[1]-a[1])),tileDepth=(float)Math.min(1,depth*2-.08);
            quad(m,stone,shoulderA,ab,bb,shoulderB,0xFFFFFF,light,new float[][]{{0,0},{0,tileDepth},{tileWidth,tileDepth},{tileWidth,0}});
            quad(m,gold,af,shoulderA,shoulderB,bf,0xFFFFFF,light,faceUV(af,shoulderA,shoulderB,bf,rx,ry));
            quad(m,gold,af,bf,insetB,insetA,0xFFFFFF,light,faceUV(af,bf,insetB,insetA,rx,ry));
            double[] center={0,0,depth+.001};quad(m,face,center,insetA,insetB,insetB,color,light,faceUV(center,insetA,insetB,insetB,rx,ry));
            double[] backCenter={0,0,-depth};quad(m,gold,backCenter,bb,ab,ab,0xFFFFFF,light,faceUV(backCenter,bb,ab,ab,rx,ry));
        }
    }
    private static float[][] faceUV(double[] a,double[] b,double[] c,double[] d,double rx,double ry){double[][] points={a,b,c,d};float[][] result=new float[4][2];for(int i=0;i<4;i++){result[i][0]=(float)(.5+points[i][0]/(2*rx));result[i][1]=(float)(.5+points[i][1]/(2*ry));}return result;}
    private static void quad(MatrixStack m,VertexConsumer consumer,double[] a,double[] b,double[] c,double[] d,int color,int light){
        quad(m,consumer,a,b,c,d,color,light,new float[][]{{0,0},{1,0},{1,1},{0,1}});
    }
    private static void quad(MatrixStack m,VertexConsumer consumer,double[] a,double[] b,double[] c,double[] d,int color,int light,float[][] uv){
        Vector3f normal=new Vector3f((float)(b[0]-a[0]),(float)(b[1]-a[1]),(float)(b[2]-a[2])).cross(new Vector3f((float)(c[0]-a[0]),(float)(c[1]-a[1]),(float)(c[2]-a[2])));
        if(normal.lengthSquared()<1e-10f)normal.set(0,0,a[2]>=0?1:-1);else normal.normalize();
        double[][] vertices={a,b,c,d};
        for(int i=0;i<4;i++){
            // SpriteTexturedVertexConsumer.vertex/color return the raw delegate.
            // Keep each call on the wrapper so texture() maps into THIS block sprite.
            consumer.vertex(m.peek().getPositionMatrix(),(float)vertices[i][0],(float)vertices[i][1],(float)vertices[i][2]);
            consumer.color((color>>16)&255,(color>>8)&255,color&255,255);
            consumer.texture(Math.max(0,Math.min(1,uv[i][0])),Math.max(0,Math.min(1,uv[i][1])));
            consumer.overlay(OverlayTexture.DEFAULT_UV);consumer.light(light);
            consumer.normal(m.peek().getNormalMatrix(),normal.x,normal.y,normal.z);consumer.next();
        }
    }
}
