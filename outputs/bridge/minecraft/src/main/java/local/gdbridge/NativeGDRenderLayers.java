package local.gdbridge;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.*;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

/** Native Cocos blend factors with Minecraft entity shaders, depth and lightmap. */
public final class NativeGDRenderLayers extends RenderLayer {
    private record Key(Identifier texture,int source,int destination) {}
    private static final Map<Key,RenderLayer> CACHE=new LinkedHashMap<>();
    private NativeGDRenderLayers(){super("gd_native",VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,VertexFormat.DrawMode.QUADS,256,false,true,()->{},()->{});}
    public static RenderLayer get(Identifier texture,int source,int destination){
        Key key=new Key(texture,source,destination);RenderLayer cached=CACHE.get(key);if(cached!=null)return cached;
        var transparency=new Transparency("gd_blend_"+source+"_"+destination,()->{RenderSystem.enableBlend();RenderSystem.blendFunc(source,destination);},()->{RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
        var phases=MultiPhaseParameters.builder().program(ENTITY_TRANSLUCENT_PROGRAM).texture(new Texture(texture,false,false)).transparency(transparency).cull(DISABLE_CULLING).lightmap(ENABLE_LIGHTMAP).overlay(ENABLE_OVERLAY_COLOR).depthTest(LEQUAL_DEPTH_TEST).writeMaskState(destination==GL11.GL_ONE?COLOR_MASK:ALL_MASK).build(false);
        RenderLayer layer=RenderLayer.of("gd_native_entity",VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,VertexFormat.DrawMode.QUADS,256,false,true,phases);
        if(CACHE.size()>=128)CACHE.remove(CACHE.keySet().iterator().next());CACHE.put(key,layer);return layer;
    }
}
