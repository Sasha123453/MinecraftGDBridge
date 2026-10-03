package local.gdbridge;

import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import net.minecraft.util.math.*;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Draws original GD sprites and native trail meshes; it never invents a replacement asset. */
public final class NativeGDVisuals {
    private static final Path RUNTIME = Path.of("C:/Users/shelk/Documents/Codex/2026-10-02/minecraft-java-geometry-dash-nasgubb-xo/outputs/bridge/runtime").toAbsolutePath().normalize();
    private static final int MAX_LAYERS=96, MAX_TRAILS=3, MAX_STRIP_VERTICES=2048, MAX_WAVE_VERTICES=6144;
    private static final long MAX_CACHE_BYTES=128L*1024*1024;
    private record Texture(Identifier id,int width,int height,long bytes) {}
    private record Decoded(NativeImage image,int width,int height,long bytes) {}
    private record Vertex(float x,float y,float u,float v,int r,int g,int b,int a) {}
    private static final LinkedHashMap<String,Texture> TEXTURES=new LinkedHashMap<>(16,.75f,true);
    private static final Map<String,Long> RETRY_AFTER=new HashMap<>();
    private static final Map<String,CompletableFuture<Decoded>> LOADING=new HashMap<>();
    private static final ExecutorService DECODER=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"GD original PNG decoder");t.setDaemon(true);return t;});
    private static long textureBytes, serial;
    private NativeGDVisuals() {}

    /** Standard entity buffers provide Minecraft lightmap, normals and Iris entity/shadow passes. */
    public static void renderWorldLit(MatrixStack matrices,VertexConsumerProvider buffers,JsonObject packet,double ox,double oy,double oz,int light){
        if(packet==null)return;retireUnused();JsonArray layers=array(packet,"avatarLayers");
        float front=(float)(1.02-oz);
        for(int i=0;layers!=null&&i<Math.min(MAX_LAYERS,layers.size());i++){
            JsonObject layer=layers.get(i).getAsJsonObject();Vertex[] quad=vertices(array(layer,"vertices"),4);if(quad==null||quad.length!=4)continue;
            int source=blend(number(layer,"blendSource",GL11.GL_ONE)),destination=blend(number(layer,"blendDestination",GL11.GL_ONE_MINUS_SRC_ALPHA));
            Texture tex=texture(string(layer,"path"),source==GL11.GL_ONE);if(tex==null)continue;
            boolean glow=destination==GL11.GL_ONE;
            RenderLayer renderLayer=NativeGDRenderLayers.get(tex.id,source,destination);
            VertexConsumer consumer=buffers.getBuffer(renderLayer);int localLight=quadLight(quad,light);int materialLight=glow?0xF000F0:localLight;
            float us=(float)number(layer,"textureWidth",tex.width)/tex.width,vs=(float)number(layer,"textureHeight",tex.height)/tex.height;
            float z=front+i*.0002f;
            for(Vertex v:quad)litVertex(consumer,matrices,v,ox,oy,z,v.u*us,v.v*vs,materialLight,0,0,1);
        }
        JsonArray objects=array(packet,"objectLayers");
        Map<Integer,GDBridge.Obj> nativeObjects=new HashMap<>();var current=GDBridge.getRenderFrame();if(current!=null)for(var object:current.objects())nativeObjects.put(object.id(),object);
        for(int i=0;objects!=null&&i<Math.min(512,objects.size());i++){
            JsonObject layer=objects.get(i).getAsJsonObject();GDBridge.Obj object=nativeObjects.get(number(layer,"id",-1));
            if(object==null||!object.visualEnabled())continue;
            if(object.type().equals("hazard")&&NativeSpikes3D.supports(object))continue;
            if(BridgeVisualStyle.volume()&&NativeObjectBodies3D.bodyOnly(object))continue;
            Vertex[] quad=vertices(array(layer,"vertices"),4);if(quad==null||quad.length!=4)continue;
            int source=blend(number(layer,"blendSource",GL11.GL_ONE)),destination=blend(number(layer,"blendDestination",GL11.GL_ONE_MINUS_SRC_ALPHA));
            Texture tex=texture(string(layer,"path"),source==GL11.GL_ONE);if(tex==null)continue;boolean glow=destination==GL11.GL_ONE;
            VertexConsumer consumer=buffers.getBuffer(NativeGDRenderLayers.get(tex.id,source,destination));int materialLight=glow?0xF000F0:quadLight(quad,light);
            float us=(float)number(layer,"textureWidth",tex.width)/tex.width,vs=(float)number(layer,"textureHeight",tex.height)/tex.height;
            float z=(float)(1.025-oz)+i*.000002f;for(Vertex v:quad)litVertex(consumer,matrices,v,ox,oy,z,v.u*us,v.v*vs,materialLight,0,0,1);
        }
        // Real trail vertices remain native. Only native additive materials are emissive.
        JsonArray trails=array(packet,"trails");
        for(int i=0;trails!=null&&i<Math.min(MAX_TRAILS,trails.size());i++){
            JsonObject mesh=trails.get(i).getAsJsonObject();boolean strip=string(mesh,"topology").equals("strip");
            Vertex[] points=vertices(array(mesh,"vertices"),strip?MAX_STRIP_VERTICES:MAX_WAVE_VERTICES);if(points==null||points.length<3)continue;
            int source=blend(number(mesh,"blendSource",GL11.GL_ONE)),destination=blend(number(mesh,"blendDestination",GL11.GL_ONE_MINUS_SRC_ALPHA));
            Texture tex=string(mesh,"path").isEmpty()?whiteTexture():texture(string(mesh,"path"),source==GL11.GL_ONE);if(tex==null)continue;
            boolean glow=destination==GL11.GL_ONE;
            VertexConsumer consumer=buffers.getBuffer(NativeGDRenderLayers.get(tex.id,source,destination));
            int trailLight=glow?0xF000F0:light;float us=(float)number(mesh,"textureWidth",tex.width)/tex.width,vs=(float)number(mesh,"textureHeight",tex.height)/tex.height;
            if(strip)for(int e=2;e<points.length;e++){int a=e-2,b=e-1;if((e&1)!=0){int swap=a;a=b;b=swap;}litTriangle(consumer,matrices,points[a],points[b],points[e],ox,oy,(float)(1.018-oz),us,vs,trailLight);}
            else for(int e=0;e+2<points.length;e+=3)litTriangle(consumer,matrices,points[e],points[e+1],points[e+2],ox,oy,(float)(1.018-oz),us,vs,trailLight);
        }
    }
    private static int quadLight(Vertex[] quad,int fallback){var mc=MinecraftClient.getInstance();if(mc.world==null)return fallback;double x=0,y=0;for(Vertex v:quad){x+=v.x;y+=v.y;}return WorldRenderer.getLightmapCoordinates(mc.world,BlockPos.ofFloored(x/quad.length/30,64+y/quad.length/30,.5));}
    private static Vertex bodyTint(Vertex v,JsonArray color){return color!=null&&color.size()>=3?new Vertex(v.x,v.y,v.u,v.v,Math.max(0,Math.min(255,color.get(0).getAsInt())),Math.max(0,Math.min(255,color.get(1).getAsInt())),Math.max(0,Math.min(255,color.get(2).getAsInt())),v.a):v;}
    private static void collectRequested(JsonObject packet,Set<String> requested){
        for(String field:List.of("avatarLayers","trails","objectLayers")){JsonArray layers=array(packet,field);if(layers!=null)for(JsonElement e:layers)if(e.isJsonObject()){JsonObject layer=e.getAsJsonObject();requested.add(string(layer,"path")+":"+(number(layer,"blendSource",GL11.GL_ONE)==GL11.GL_ONE));}}
        if(packet.has("player2")&&packet.get("player2").isJsonObject())collectRequested(packet.getAsJsonObject("player2"),requested);
    }
    private static void retireUnused(){var frame=GDBridge.FRAME.get();if(frame==null)return;Set<String> requested=new HashSet<>();collectRequested(frame.packet(),requested);var jobs=LOADING.entrySet().iterator();while(jobs.hasNext()){var job=jobs.next();if(!requested.contains(job.getKey())&&job.getValue().isDone()){Decoded retired=job.getValue().getNow(null);if(retired!=null)retired.image.close();jobs.remove();}}}
    private static void litTriangle(VertexConsumer consumer,MatrixStack matrices,Vertex a,Vertex b,Vertex c,double ox,double oy,float z,float us,float vs,int light){
        for(Vertex v:new Vertex[]{a,b,c,c})litVertex(consumer,matrices,v,ox,oy,z,v.u*us,v.v*vs,light,0,0,1);
    }
    private static void litVertex(VertexConsumer consumer,MatrixStack matrices,Vertex v,double ox,double oy,float z,float u,float vv,int light,float nx,float ny,float nz){
        consumer.vertex(matrices.peek().getPositionMatrix(),(float)(v.x/30-ox),(float)(64+v.y/30-oy),z).color(v.r,v.g,v.b,v.a).texture(u,vv).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(matrices.peek().getNormalMatrix(),nx,ny,nz).next();
    }
    private static Texture whiteTexture(){
        Texture existing=TEXTURES.get("white");if(existing!=null)return existing;
        NativeImage image=new NativeImage(1,1,false);image.setColor(0,0,0xFFFFFFFF);Identifier id=new Identifier("gdbridge","trail_white");MinecraftClient.getInstance().getTextureManager().registerTexture(id,new NativeImageBackedTexture(image));
        Texture texture=new Texture(id,1,1,4);TEXTURES.put("white",texture);textureBytes+=4;return texture;
    }

    /** matrices is already translated by minus the Minecraft camera position. */
    public static boolean render(MatrixStack matrices, JsonObject packet) {
        if(packet==null)return false;
        // An icon/level change can retire a PNG before decode completes. Release
        // completed retired jobs so the bounded decoder cannot fill permanently.
        Set<String> requested=new HashSet<>();
        for(String field:List.of("avatarLayers","trails")){
            JsonArray meshes=array(packet,field);if(meshes==null)continue;
            for(JsonElement element:meshes)if(element.isJsonObject()){
                JsonObject mesh=element.getAsJsonObject();requested.add(string(mesh,"path")+":"+(number(mesh,"blendSource",GL11.GL_ONE)==GL11.GL_ONE));
            }
        }
        var loading=LOADING.entrySet().iterator();
        while(loading.hasNext()){
            var job=loading.next();if(!requested.contains(job.getKey())&&job.getValue().isDone()){
                Decoded retired=job.getValue().getNow(null);if(retired!=null)retired.image.close();loading.remove();
            }
        }
        boolean hasAvatar=false;
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int oldSrc=GL11.glGetInteger(GL11.GL_BLEND_SRC),oldDst=GL11.glGetInteger(GL11.GL_BLEND_DST);
        int oldSrcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),oldDstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int oldTexture=RenderSystem.getShaderTexture(0);
        var oldShader=RenderSystem.getShader();
        float[] oldColor=RenderSystem.getShaderColor().clone();
        try {
            RenderSystem.enableBlend();RenderSystem.disableCull();RenderSystem.enableDepthTest();RenderSystem.depthMask(false);RenderSystem.setShaderColor(1,1,1,1);
            Matrix4f matrix=matrices.peek().getPositionMatrix();
            JsonArray trails=array(packet,"trails");
            for(int i=0;trails!=null&&i<Math.min(MAX_TRAILS,trails.size());i++) {
                if(!trails.get(i).isJsonObject())continue;
                JsonObject mesh=trails.get(i).getAsJsonObject();
                String topology=string(mesh,"topology"),kind=string(mesh,"kind");
                boolean strip=topology.equals("strip"),wave=kind.equals("wave")&&topology.equals("triangles");
                if(!strip&&!wave)continue;
                Vertex[] vertices=vertices(array(mesh,"vertices"),strip?MAX_STRIP_VERTICES:MAX_WAVE_VERTICES);
                if(vertices==null||vertices.length<3)continue;
                int source=blend(number(mesh,"blendSource",GL11.GL_ONE)),destination=blend(number(mesh,"blendDestination",GL11.GL_ONE_MINUS_SRC_ALPHA));
                Texture texture=wave&&string(mesh,"path").isEmpty()?null:texture(string(mesh,"path"),source==GL11.GL_ONE);
                if(texture==null&&!wave)continue; // Missing regular/ship textures remain absent.
                RenderSystem.blendFunc(source,destination);
                draw(matrix,vertices,texture,mesh,strip,false,.53f);
            }
            JsonArray layers=array(packet,"avatarLayers");
            for(int i=0;layers!=null&&i<Math.min(MAX_LAYERS,layers.size());i++) {
                if(!layers.get(i).isJsonObject())continue;
                JsonObject layer=layers.get(i).getAsJsonObject();
                Vertex[] vertices=vertices(array(layer,"vertices"),4);
                if(vertices==null||vertices.length!=4)continue;
                int source=blend(number(layer,"blendSource",GL11.GL_ONE)),destination=blend(number(layer,"blendDestination",GL11.GL_ONE_MINUS_SRC_ALPHA));
                Texture texture=texture(string(layer,"path"),source==GL11.GL_ONE);
                if(texture==null)continue;
                RenderSystem.blendFunc(source,destination);
                draw(matrix,vertices,texture,layer,false,true,.6f+i*.0001f);
                hasAvatar=true;
            }
        } finally {
            RenderSystem.depthMask(depthWrite);RenderSystem.blendFuncSeparate(oldSrc,oldDst,oldSrcAlpha,oldDstAlpha);
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            RenderSystem.setShaderTexture(0,oldTexture);RenderSystem.setShaderColor(oldColor[0],oldColor[1],oldColor[2],oldColor[3]);
            if(oldShader!=null)RenderSystem.setShader(()->oldShader);
        }
        return hasAvatar;
    }
    private static void draw(Matrix4f matrix,Vertex[] vertices,Texture texture,JsonObject metadata,boolean strip,boolean quad,float z) {
        BufferBuilder builder=Tessellator.getInstance().getBuffer();
        float uScale=texture==null?1:(float)number(metadata,"textureWidth",texture.width)/texture.width;
        float vScale=texture==null?1:(float)number(metadata,"textureHeight",texture.height)/texture.height;
        if(texture==null){RenderSystem.setShader(GameRenderer::getPositionColorProgram);builder.begin(VertexFormat.DrawMode.TRIANGLES,VertexFormats.POSITION_COLOR);}
        else {RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);RenderSystem.setShaderTexture(0,texture.id);builder.begin(VertexFormat.DrawMode.TRIANGLES,VertexFormats.POSITION_TEXTURE_COLOR);}
        if(quad){emit(builder,matrix,vertices[0],texture,uScale,vScale,z);emit(builder,matrix,vertices[1],texture,uScale,vScale,z);emit(builder,matrix,vertices[2],texture,uScale,vScale,z);emit(builder,matrix,vertices[0],texture,uScale,vScale,z);emit(builder,matrix,vertices[2],texture,uScale,vScale,z);emit(builder,matrix,vertices[3],texture,uScale,vScale,z);}
        else if(strip){for(int i=2;i<vertices.length;i++){int a=i-2,b=i-1;if((i&1)!=0){int swap=a;a=b;b=swap;}emit(builder,matrix,vertices[a],texture,uScale,vScale,z);emit(builder,matrix,vertices[b],texture,uScale,vScale,z);emit(builder,matrix,vertices[i],texture,uScale,vScale,z);}}
        else {for(int i=0;i+2<vertices.length;i+=3){emit(builder,matrix,vertices[i],texture,uScale,vScale,z);emit(builder,matrix,vertices[i+1],texture,uScale,vScale,z);emit(builder,matrix,vertices[i+2],texture,uScale,vScale,z);}}
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }
    private static void emit(BufferBuilder builder,Matrix4f matrix,Vertex vertex,Texture texture,float uScale,float vScale,float z) {
        builder.vertex(matrix,vertex.x/30f,64+vertex.y/30f,z);
        if(texture!=null)builder.texture(vertex.u*uScale,vertex.v*vScale);
        builder.color(vertex.r,vertex.g,vertex.b,vertex.a).next();
    }
    private static Vertex[] vertices(JsonArray data,int cap) {
        if(data==null||data.size()>cap)return null;
        Vertex[] result=new Vertex[data.size()];
        try {for(int i=0;i<result.length;i++) {
            JsonArray a=data.get(i).getAsJsonArray();if(a.size()!=8)return null;
            float[] n=new float[8];for(int k=0;k<8;k++){n[k]=a.get(k).getAsFloat();if(!Float.isFinite(n[k]))return null;}
            if(Math.abs(n[0])>100000||Math.abs(n[1])>100000||Math.abs(n[2])>8||Math.abs(n[3])>8)return null;
            result[i]=new Vertex(n[0],n[1],n[2],n[3],color(n[4]),color(n[5]),color(n[6]),color(n[7]));
        }}catch(RuntimeException bad){return null;}
        return result;
    }
    private static int color(float value){return Math.max(0,Math.min(255,Math.round(value)));}
    private static int blend(int value){return switch(value){case 0,1,768,769,770,771,772,773,774,775,776->value;default->GL11.GL_ONE_MINUS_SRC_ALPHA;};}
    private static JsonArray array(JsonObject object,String key){JsonElement e=object.get(key);return e!=null&&e.isJsonArray()?e.getAsJsonArray():null;}
    private static String string(JsonObject object,String key){try{return object.has(key)?object.get(key).getAsString():"";}catch(RuntimeException bad){return "";}}
    private static int number(JsonObject object,String key,int fallback){try{return object.has(key)?object.get(key).getAsInt():fallback;}catch(RuntimeException bad){return fallback;}}

    private static Texture texture(String filename,boolean premultiply) {
        if(filename.isEmpty())return null;
        String key=filename+":"+premultiply;
        Texture cached=TEXTURES.get(key);if(cached!=null)return cached;
        if(System.nanoTime()<RETRY_AFTER.getOrDefault(key,0L))return null;
        CompletableFuture<Decoded> pending=LOADING.get(key);
        if(pending==null){
            if(LOADING.size()<2)LOADING.put(key,CompletableFuture.supplyAsync(()->decode(filename,premultiply),DECODER));
            return null;
        }
        if(!pending.isDone())return null;
        LOADING.remove(key);
        Decoded decoded=pending.getNow(null);
        if(decoded==null){RETRY_AFTER.put(key,System.nanoTime()+2_000_000_000L);return null;}
        NativeImage image=decoded.image;
        try {
            while(!TEXTURES.isEmpty()&&(TEXTURES.size()>=32||textureBytes+decoded.bytes>MAX_CACHE_BYTES)) {
                var first=TEXTURES.entrySet().iterator();Texture old=first.next().getValue();first.remove();textureBytes-=old.bytes;MinecraftClient.getInstance().getTextureManager().destroyTexture(old.id);
            }
            Identifier identifier=new Identifier("gdbridge","native_gd_"+(++serial));
            NativeImageBackedTexture upload=new NativeImageBackedTexture(image);image=null;
            MinecraftClient.getInstance().getTextureManager().registerTexture(identifier,upload);
            Texture ready=new Texture(identifier,decoded.width,decoded.height,decoded.bytes);TEXTURES.put(key,ready);textureBytes+=decoded.bytes;RETRY_AFTER.remove(key);return ready;
        }catch(RuntimeException unavailable){RETRY_AFTER.put(key,System.nanoTime()+2_000_000_000L);return null;}
        finally {if(image!=null)image.close();}
    }
    // Disk IO, PNG decompression and alpha conversion run off the render thread.
    // Only texture registration/upload remains on Minecraft's render thread.
    private static Decoded decode(String filename,boolean premultiply) {
        NativeImage image=null;
        try {
            Path path=Path.of(filename).toAbsolutePath().normalize();
            if(!path.startsWith(RUNTIME)||!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))return null;
            Path realRoot=RUNTIME.toRealPath(),real=path.toRealPath();if(!real.startsWith(realRoot)||!Files.isRegularFile(real)||Files.size(real)>32L*1024*1024)return null;
            // Validate PNG dimensions before STB allocates the native image.
            byte[] header;try(InputStream input=Files.newInputStream(real)){header=input.readNBytes(24);}
            if(header.length!=24||header[0]!=(byte)137||header[1]!=80||header[2]!=78||header[3]!=71||header[12]!=73||header[13]!=72||header[14]!=68||header[15]!=82)return null;
            ByteBuffer dimensions=ByteBuffer.wrap(header);int width=dimensions.getInt(16),height=dimensions.getInt(20);
            if(width<1||height<1||width>4096||height>4096)return null;
            try(InputStream input=Files.newInputStream(real)){image=NativeImage.read(input);}
            long bytes=(long)image.getWidth()*image.getHeight()*4;
            if(premultiply)for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
                int pixel=image.getColor(x,y),alpha=pixel>>>24;
                image.setColor(x,y,(alpha<<24)|((((pixel>>>16)&255)*alpha/255)<<16)|((((pixel>>>8)&255)*alpha/255)<<8)|((pixel&255)*alpha/255));
            }
            Decoded ready=new Decoded(image,width,height,bytes);image=null;return ready;
        }catch(IOException|RuntimeException unavailable){return null;}
        finally {if(image!=null)image.close();}
    }
}
