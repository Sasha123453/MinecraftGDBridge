package local.gdbridge;
import com.google.gson.*;
import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

/** Persistent world meshes use Minecraft materials, light and shader depth. */
public final class CompoundObstacleRenderer implements BlockEntityRenderer<CompoundObstacleBlockEntity> {
    public CompoundObstacleRenderer(BlockEntityRendererFactory.Context context){}
    @Override public boolean rendersOutsideBoundingBox(CompoundObstacleBlockEntity entity){return true;}
    @Override public int getRenderDistance(){return 128;}
    @Override public void render(CompoundObstacleBlockEntity entity,float delta,MatrixStack matrices,VertexConsumerProvider buffers,int light,int overlay){
        if(!WorldEditor.editing&&!WorldEditor.worldGeometryActive(GDBridge.getRenderFrame()))return;
        Set<String> drawn=new HashSet<>();int index=0;
        for(JsonObject piece:entity.localPieces()){
            String shape=CompoundGeometry.shape(piece);if(shape.equals("special")||shape.equals("unsupported")||!CompoundGeometry.visible(piece))continue;
            double[][] quad=quad(piece);if(quad==null)continue;
            String key=shape+Arrays.deepToString(quad)+CompoundGeometry.flag(piece,"nativeFlipX")+CompoundGeometry.flag(piece,"nativeFlipY")+CompoundGeometry.raw(piece,"4",0)+CompoundGeometry.raw(piece,"5",0)+CompoundGeometry.number(piece,"spikePeaks",1)+(piece.has("triangleVertices")?piece.get("triangleVertices").toString():"");if(!drawn.add(key))continue;
            var sprite=MinecraftClient.getInstance().getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getSprite(new Identifier("minecraft","block/"+(shape.equals("solid-rect")?"stone_bricks":"stone")));
            VertexConsumer consumer=sprite.getTextureSpecificVertexConsumer(buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)));
            double front=1+Math.min(index++,40)*.0005;
            double depth=depth(piece,quad,shape.equals("solid-rect")),back=front-depth;
            if(shape.equals("solid-rect"))extrude(matrices,consumer,quad,front,back,light,overlay);
            else if(shape.equals("triangle")){JsonArray vertices=piece.getAsJsonArray("triangleVertices");double sx=CompoundGeometry.number(piece,"x",0),sy=CompoundGeometry.number(piece,"y",0),lx=CompoundGeometry.number(piece,"localX",.5),ly=CompoundGeometry.number(piece,"localY",.5);double[][] triangle=new double[3][2];for(int i=0;i<3;i++){JsonArray p=vertices.get(i).getAsJsonArray();triangle[i][0]=lx+(p.get(0).getAsDouble()-sx)/30;triangle[i][1]=ly+(p.get(1).getAsDouble()-sy)/30;}extrude(matrices,consumer,triangle,front,back,light,overlay);}
            else {
                int peaks=shape.equals("spike-strip")?(int)CompoundGeometry.number(piece,"spikePeaks",0):1;if(peaks<1||peaks>128)continue;
                boolean flipX=CompoundGeometry.flag(piece,"nativeFlipX")||CompoundGeometry.raw(piece,"4",0)==1,flipY=CompoundGeometry.flag(piece,"nativeFlipY")||CompoundGeometry.raw(piece,"5",0)==1;
                for(int peak=0;peak<peaks;peak++){double a=(double)peak/peaks,b=(double)(peak+1)/peaks;double[][] triangle={at(quad,flipX?1-a:a,flipY?1:0),at(quad,flipX?1-b:b,flipY?1:0),at(quad,flipX?1-(a+b)/2:(a+b)/2,flipY?0:1)};extrude(matrices,consumer,triangle,front,back,light,overlay);}
            }
        }
    }
    private static double depth(JsonObject piece,double[][] quad,boolean rectangle){
        double width=CompoundGeometry.number(piece,"bodyWidth",Math.hypot(quad[1][0]-quad[0][0],quad[1][1]-quad[0][1])*30)/30,height=CompoundGeometry.number(piece,"bodyHeight",Math.hypot(quad[3][0]-quad[0][0],quad[3][1]-quad[0][1])*30)/30;
        double value=rectangle?Math.min(width,height):height;return Double.isFinite(value)&&value>0?Math.min(1,value):1;
    }
    private static double[][] quad(JsonObject piece){
        double sx=CompoundGeometry.number(piece,"x",0),sy=CompoundGeometry.number(piece,"y",0),x=CompoundGeometry.number(piece,"localX",.5),y=CompoundGeometry.number(piece,"localY",.5);
        if(piece.has("visualQuad")){JsonArray data=piece.getAsJsonArray("visualQuad");if(data.size()==4){double[][] result=new double[4][2];for(int i=0;i<4;i++){JsonArray v=data.get(i).getAsJsonArray();result[i][0]=x+(v.get(0).getAsDouble()-sx)/30;result[i][1]=y+(v.get(1).getAsDouble()-sy)/30;}return result;}}
        double w=CompoundGeometry.number(piece,"bodyWidth",CompoundGeometry.number(piece,"vw",30*Math.abs(CompoundGeometry.number(piece,"scale",1))))/30,h=CompoundGeometry.number(piece,"bodyHeight",CompoundGeometry.number(piece,"vh",30*Math.abs(CompoundGeometry.number(piece,"scale",1))))/30;
        if(!Double.isFinite(w)||!Double.isFinite(h)||w<=0||h<=0||w>128||h>128)return null;
        double radians=Math.toRadians(-CompoundGeometry.number(piece,"nativeRotation",CompoundGeometry.number(piece,"rotation",0))),c=Math.cos(radians),s=Math.sin(radians);double[][] corners={{-w/2,-h/2},{w/2,-h/2},{w/2,h/2},{-w/2,h/2}};
        boolean signX=CompoundGeometry.number(piece,"nativeScaleX",1)<0,signY=CompoundGeometry.number(piece,"nativeScaleY",1)<0;
        for(double[] p:corners){double px=p[0]*(signX?-1:1),py=p[1]*(signY?-1:1);p[0]=x+px*c-py*s;p[1]=y+px*s+py*c;}return corners;
    }
    private static double[] at(double[][] quad,double u,double v){return new double[]{quad[0][0]+u*(quad[1][0]-quad[0][0])+v*(quad[3][0]-quad[0][0]),quad[0][1]+u*(quad[1][1]-quad[0][1])+v*(quad[3][1]-quad[0][1])};}
    private static void extrude(MatrixStack matrices,VertexConsumer consumer,double[][] polygon,double front,double back,int light,int overlay){
        double winding=0;for(int i=0;i<polygon.length;i++){double[] a=polygon[i],b=polygon[(i+1)%polygon.length];winding+=a[0]*b[1]-b[0]*a[1];}if(winding<0){double[][] reversed=new double[polygon.length][];for(int i=0;i<polygon.length;i++)reversed[i]=polygon[polygon.length-1-i];polygon=reversed;}
        double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;for(double[] p:polygon){minX=Math.min(minX,p[0]);maxX=Math.max(maxX,p[0]);minY=Math.min(minY,p[1]);maxY=Math.max(maxY,p[1]);}
        double[][] face=new double[4][3],rear=new double[4][3];float[][] uv=new float[4][2];for(int i=0;i<4;i++){double[] p=polygon[Math.min(i,polygon.length-1)];face[i]=new double[]{p[0],p[1],front};double[] r=polygon[Math.max(0,polygon.length-1-i)];rear[i]=new double[]{r[0],r[1],back};uv[i]=new float[]{(float)((p[0]-minX)/Math.max(.00001,maxX-minX)),(float)(1-(p[1]-minY)/Math.max(.00001,maxY-minY))};}
        emit(matrices,consumer,face,uv,light,overlay);emit(matrices,consumer,rear,uv,light,overlay);
        for(int i=0;i<polygon.length;i++){double[] a=polygon[i],b=polygon[(i+1)%polygon.length];emit(matrices,consumer,new double[][]{{a[0],a[1],front},{a[0],a[1],back},{b[0],b[1],back},{b[0],b[1],front}},new float[][]{{0,0},{0,1},{1,1},{1,0}},light,overlay);}
    }
    private static void emit(MatrixStack matrices,VertexConsumer consumer,double[][] points,float[][] uv,int light,int overlay){double[] a=points[0],b=points[1],c=points[2];var normal=new Vector3f((float)(b[0]-a[0]),(float)(b[1]-a[1]),(float)(b[2]-a[2])).cross(new Vector3f((float)(c[0]-a[0]),(float)(c[1]-a[1]),(float)(c[2]-a[2])));if(normal.lengthSquared()<1e-10f)normal.set(0,0,1);else normal.normalize();for(int i=0;i<4;i++){consumer.vertex(matrices.peek().getPositionMatrix(),(float)points[i][0],(float)points[i][1],(float)points[i][2]);consumer.color(255,255,255,255);consumer.texture(uv[i][0],uv[i][1]);consumer.overlay(overlay);consumer.light(light);consumer.normal(matrices.peek().getNormalMatrix(),normal.x,normal.y,normal.z);consumer.next();}}
}
