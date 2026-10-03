package local.gdbridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;

/** Rendered by the block world, even without GD telemetry or an actor entity. */
public final class StoneSpikeRenderer implements BlockEntityRenderer<StoneSpikeBlockEntity> {
    public StoneSpikeRenderer(BlockEntityRendererFactory.Context context){}
    @Override public void render(StoneSpikeBlockEntity entity,float delta,MatrixStack matrices,VertexConsumerProvider buffers,int light,int overlay){
        var sprite=MinecraftClient.getInstance().getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getSprite(new Identifier("minecraft","block/stone"));
        var consumer=sprite.getTextureSpecificVertexConsumer(buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)));
        matrices.push();matrices.translate(.5,.5,.5);matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)-StoneSpikeBlock.gdRotation(entity.getCachedState())));
        double[][] front={{-.5,-.5,.5},{.5,-.5,.5},{0,.5,.5}},back={{-.5,-.5,-.5},{.5,-.5,-.5},{0,.5,-.5}};
        face(matrices,consumer,new double[][]{front[0],front[1],front[2],front[2]},new float[][]{{0,1},{1,1},{.5f,0},{.5f,0}},light,overlay);
        face(matrices,consumer,new double[][]{back[2],back[1],back[0],back[0]},new float[][]{{.5f,0},{1,1},{0,1},{0,1}},light,overlay);
        for(int i=0;i<3;i++){int j=(i+1)%3;face(matrices,consumer,new double[][]{front[i],back[i],back[j],front[j]},new float[][]{{0,0},{0,1},{1,1},{1,0}},light,overlay);}
        matrices.pop();
    }
    private static void face(MatrixStack matrices,VertexConsumer consumer,double[][] points,float[][] uv,int light,int overlay){
        double[] a=points[0],b=points[1],c=points[2];var normal=new Vector3f((float)(b[0]-a[0]),(float)(b[1]-a[1]),(float)(b[2]-a[2])).cross(new Vector3f((float)(c[0]-a[0]),(float)(c[1]-a[1]),(float)(c[2]-a[2]))).normalize();
        for(int i=0;i<4;i++){consumer.vertex(matrices.peek().getPositionMatrix(),(float)points[i][0],(float)points[i][1],(float)points[i][2]);consumer.color(255,255,255,255);consumer.texture(uv[i][0],uv[i][1]);consumer.overlay(overlay);consumer.light(light);consumer.normal(matrices.peek().getNormalMatrix(),normal.x,normal.y,normal.z);consumer.next();}
    }
}
