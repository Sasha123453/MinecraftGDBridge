package local.gdbridge;

import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class GDBridge implements ClientModInitializer {
    public record Obj(int id,int objectId,String type,double x,double y,double w,double h,double rotation,double scale,double vx,double vy,double vw,double vh,int visualColor,String visualShape,int spikePeaks,double bodyX,double bodyY,double bodyWidth,double bodyHeight,double bodyRotation,boolean collisionEnabled,boolean visualEnabled,boolean nativeAdditive,String nativeFrameName) {}
    public record Frame(long seq,long received,double x,double y,double rotation,double scale,String mode,boolean dead,boolean paused,double percent,String name,List<Obj> objects,JsonObject packet) {}
    public static final AtomicReference<Frame> FRAME = new AtomicReference<>();
    private static volatile Frame renderFrame;
    public static Frame captureRenderFrame(){return renderFrame=FRAME.get();}
    public static Frame getRenderFrame(){Frame snapshot=renderFrame;return snapshot!=null?snapshot:FRAME.get();}
    public static volatile String connection="Listening on 127.0.0.1:18471";
    private static long frames, lastStatus;
    private static volatile PrintWriter commands;
    private static boolean buttonDown, restartDown, autoLoaded;
    private static boolean programmaticButtonDown;
    public static void setProgrammaticJump(boolean down){programmaticButtonDown=down;}
    private static GDRenderEntity renderPlayer,renderPlayer2;
    public static boolean send(JsonObject message) {
        PrintWriter writer=commands;if(writer==null)return false;
        message.addProperty("v",1);writer.println(message);writer.flush();return !writer.checkError();
    }
    public static void sendJump(boolean down) {
        if(down==buttonDown)return;buttonDown=down;PrintWriter writer=commands;
        if(writer!=null){writer.println("{\"v\":1,\"cmd\":\"input\",\"button\":1,\"down\":"+down+"}");writer.flush();}
    }
    public static void sendRestart() {
        restartDown=true;
        PrintWriter writer=commands;if(writer!=null){writer.println("{\"v\":1,\"input\":\"restart\"}");writer.flush();}
    }
    public static boolean active() { return FRAME.get()!=null&&!WorldEditor.editing; }
    public static double ground=3; // GD normal ground is y=90, 30 GD units per block.
    @Override public void onInitializeClient() {
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context,hit)->!active()||WorldEditor.editing);
        WorldRenderEvents.BLOCK_OUTLINE.register((context,outline)->!active()||WorldEditor.editing);
        Thread thread=new Thread(GDBridge::listen,"GD-Bridge-IPC"); thread.setDaemon(true); thread.start();
        EntityRendererRegistry.register(GDBridgeCommon.PLAYER,GDPlayerRenderer::new);
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(GDBridgeCommon.STONE_SPIKE_ENTITY,StoneSpikeRenderer::new);
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(GDBridgeCommon.COMPOUND_OBSTACLE_ENTITY,CompoundObstacleRenderer::new);
        HudRenderCallback.EVENT.register((draw,delta)->{
            MinecraftClient mc=MinecraftClient.getInstance(); Frame f=FRAME.get();
            if(f!=null&&!WorldEditor.editing){String percent=String.format(Locale.ROOT,"%.1f%%",f.percent);int width=mc.textRenderer.getWidth(percent),cx=draw.getScaledWindowWidth()/2;draw.drawTextWithShadow(mc.textRenderer,percent,cx-width/2,8,0xFFFFFF);}
            if(f!=null&&bool(f.packet,"levelCompleted")){String caption=bool(f.packet,"levelCompletedDiagnosticNoclip")||bool(f.packet,"diagnosticNoclip")?"Проверочный проход завершён":"Уровень пройден";String finished=String.format(Locale.ROOT,"%s | %s | %.1f%%",caption,f.name,f.percent);int width=mc.textRenderer.getWidth(finished),cx=draw.getScaledWindowWidth()/2;draw.fill(cx-width/2-10,36,cx+width/2+10,63,0xD0000000);draw.drawTextWithShadow(mc.textRenderer,finished,cx-width/2,45,0x55FFAA);}
            if(f!=null&&bool(f.packet,"diagnosticNoclip")){draw.fill(5,5,174,25,0xC0000000);draw.drawTextWithShadow(mc.textRenderer,"VISUAL CHECK / NOCLIP",9,11,0xFFFF55);if(!WorldEditor.editing&&!BridgeCamera.debugHud)return;}
            if(f!=null&&!WorldEditor.editing&&!BridgeCamera.debugHud)return;
            String line=WorldEditor.editing?"BUILD | "+WorldEditor.region().label()+" | cubes=solid, stone spike=hazard | F6 play | "+WorldEditor.message:f==null?connection:String.format(Locale.ROOT,"GD LIVE | %s | %.1f%% | F7 build | F6 play | %s%s",f.name,f.percent,WorldEditor.message,f.dead?" | DEAD":"");
            draw.fill(5,5,Math.min(mc.getWindow().getScaledWidth()-5,mc.textRenderer.getWidth(line)+13),25,0xB0000000);
            draw.drawTextWithShadow(mc.textRenderer,line,9,11,f==null?0xFFFFFF:(System.nanoTime()-f.received>1000000000?0xFFAA55:f.dead?0xFF5555:0x55FFAA));
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            mc.options.pauseOnLostFocus=false;
            BridgeCamera.reload(mc);BridgeVisualStyle.reload(mc);BridgeControl.tick(mc);BridgeRecorder.tick(mc);
            if(!autoLoaded && mc.currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen){
                autoLoaded=true;
                String selected="GDBridge";try{Path choice=mc.runDirectory.toPath().resolve("config/gdbridge/selected-world.json");if(Files.exists(choice)){String candidate=JsonParser.parseString(Files.readString(choice)).getAsJsonObject().get("world").getAsString();if(Set.of("GDBridge","GDBridge-XO","GDBridge-GeometryTests","GDBridge-Reference").contains(candidate))selected=candidate;}}catch(Exception ignored){}WorldEditor.resetWorld(selected);
                if(Files.exists(mc.runDirectory.toPath().resolve("saves/"+selected+"/level.dat"))) mc.createIntegratedServerLoader().start(mc.currentScreen,selected);
                else {
                    net.minecraft.world.GameRules rules=new net.minecraft.world.GameRules();
                    rules.get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,null);
                    rules.get(net.minecraft.world.GameRules.DO_WEATHER_CYCLE).set(false,null);
                    rules.get(net.minecraft.world.GameRules.DO_MOB_SPAWNING).set(false,null);
                    var info=new net.minecraft.world.level.LevelInfo("Geometry Dash Live",net.minecraft.world.GameMode.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,rules,net.minecraft.resource.DataConfiguration.SAFE_MODE);
                    mc.createIntegratedServerLoader().createAndStart(selected,info,new net.minecraft.world.gen.GeneratorOptions(0L,false,false),r->r.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).get(net.minecraft.world.gen.WorldPresets.FLAT).createDimensionsRegistryHolder());
                }
            }
            if(mc.player==null)return;
            if(mc.world!=null&&(renderPlayer==null||renderPlayer.getWorld()!=mc.world)){
                renderPlayer=new GDRenderEntity(GDBridgeCommon.PLAYER,mc.world);mc.world.addEntity(-18471,renderPlayer);
                renderPlayer2=new GDRenderEntity(GDBridgeCommon.PLAYER,mc.world);renderPlayer2.playerIndex=1;mc.world.addEntity(-18472,renderPlayer2);
            }
            if(renderPlayer!=null&&FRAME.get()!=null)renderPlayer.sync(FRAME.get());
            if(renderPlayer2!=null&&FRAME.get()!=null)renderPlayer2.sync(FRAME.get());
            WorldEditor.tick(mc);
            mc.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            if(WorldEditor.editing){writeStatus(mc,FRAME.get());return;}
            if(!active()){writeStatus(mc,FRAME.get());return;}
            mc.options.getBobView().setValue(false);
            long window=mc.getWindow().getHandle();
            boolean pressed=programmaticButtonDown || mc.isWindowFocused() && mc.currentScreen==null && (GLFW.glfwGetKey(window,GLFW.GLFW_KEY_SPACE)==GLFW.GLFW_PRESS || GLFW.glfwGetKey(window,GLFW.GLFW_KEY_UP)==GLFW.GLFW_PRESS || GLFW.glfwGetMouseButton(window,GLFW.GLFW_MOUSE_BUTTON_LEFT)==GLFW.GLFW_PRESS);
            if(pressed!=buttonDown)sendJump(pressed);
            boolean restart=mc.isWindowFocused()&&mc.currentScreen==null&&GLFW.glfwGetKey(window,GLFW.GLFW_KEY_R)==GLFW.GLFW_PRESS;
            if(restart&&!restartDown)sendRestart();
            restartDown=restart;
            Frame f=FRAME.get();
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            mc.player.noClip=true;
            mc.player.setNoGravity(true);
            mc.player.setInvisible(true);
            mc.player.setPosition(f.x/30.0,64+f.y/30.0,0);
            mc.player.setVelocity(0,0,0);
            writeStatus(mc,f);
        });
    }
    private static void writeStatus(MinecraftClient mc,Frame f) {
            if(System.nanoTime()-lastStatus>100000000L) {
                lastStatus=System.nanoTime();
                try {
                    Path dir=mc.runDirectory.toPath().resolve("config/gdbridge"); Files.createDirectories(dir);
                    JsonObject j=new JsonObject();j.addProperty("worldLoaded",mc.world!=null);j.addProperty("editing",WorldEditor.editing);j.addProperty("authoredObjects",WorldEditor.objectCount());j.addProperty("message",WorldEditor.message);j.addProperty("receivedFrames",frames);
                    if(f!=null){j.addProperty("seq",f.seq);j.addProperty("x",f.x);j.addProperty("y",f.y);j.addProperty("dead",f.dead);j.addProperty("paused",f.paused);j.addProperty("objectCount",f.objects.size());j.addProperty("staleAgeMs",(System.nanoTime()-f.received)/1000000);j.addProperty("name",f.name);j.addProperty("mode",f.mode);j.addProperty("scale",f.scale);
                    j.addProperty("level",num(f.packet,"level",0));j.addProperty("levelCompleted",bool(f.packet,"levelCompleted"));j.addProperty("source",str(f.packet,"source","geometry-dash"));j.addProperty("buildRevision",num(f.packet,"buildRevision",0));j.addProperty("avatarLayers",f.packet.has("avatarLayers")?f.packet.getAsJsonArray("avatarLayers").size():-1);j.addProperty("trailLayers",f.packet.has("trails")?f.packet.getAsJsonArray("trails").size():-1);
                    j.addProperty("objectLayers",f.packet.has("objectLayers")?f.packet.getAsJsonArray("objectLayers").size():-1);j.addProperty("worldTime",mc.world.getTimeOfDay());j.addProperty("ambientDarkness",mc.world.getAmbientDarkness());j.addProperty("packedWorldLight",WorldRenderer.getLightmapCoordinates(mc.world,BlockPos.ofFloored(f.x/30,64+f.y/30,.5)));j.addProperty("avatarRenderPath","native-entity-world-lit");j.addProperty("fps",mc.getCurrentFps());
                    }
                    if(f!=null){j.addProperty("authority",str(f.packet,"authority","geometry-dash"));j.addProperty("worldGeometryActive",WorldEditor.worldGeometryActive(f));}
                    j.add("authoringRegion",WorldEditor.region().json());j.addProperty("worldOperationBusy",WorldWorkQueue.busy());j.addProperty("worldOperation",WorldWorkQueue.operation);j.addProperty("worldOperationProgress",WorldWorkQueue.progress);j.addProperty("worldOperationTotal",WorldWorkQueue.total);
                    j.addProperty("world",WorldEditor.worldName);j.addProperty("visualStyle",BridgeVisualStyle.mode);j.addProperty("cameraDistance",BridgeCamera.distance);j.addProperty("cameraYaw",BridgeCamera.yaw);j.addProperty("cameraPitch",BridgeCamera.pitch);j.addProperty("controlResult",BridgeControl.result);j.addProperty("recording",BridgeRecorder.active());if(f!=null)j.addProperty("diagnosticNoclip",bool(f.packet,"diagnosticNoclip"));j.addProperty("gpu",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));Files.writeString(dir.resolve("status.json"),j.toString());
                }catch(Exception ignored) {}
            }
    }
    private static double num(JsonObject j,String k,double def){return j.has(k)?j.get(k).getAsDouble():def;}
    private static String str(JsonObject j,String k,String def){return j.has(k)?j.get(k).getAsString():def;}
    private static boolean bool(JsonObject j,String k){return j.has(k)&&j.get(k).getAsBoolean();}
    private static int visualColor(JsonObject object){try{JsonArray color=object.getAsJsonArray("visualColor");if(color==null||color.size()<3)return -1;int packed=0;for(int i=0;i<3;i++){int component=color.get(i).getAsInt();if(component<0||component>255)return -1;packed=(packed<<8)|component;}return packed;}catch(RuntimeException invalid){return -1;}}
    private static void listen(){
        try(ServerSocket server=new ServerSocket(18471,1,InetAddress.getByName("127.0.0.1"))){
            for(;;) try(Socket socket=server.accept(); BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),java.nio.charset.StandardCharsets.UTF_8))){
                socket.setTcpNoDelay(true); connection="Geometry Dash connected";
                commands=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),java.nio.charset.StandardCharsets.UTF_8));
                for(String line;(line=reader.readLine())!=null;){
                    try {
                        JsonObject j=JsonParser.parseString(line).getAsJsonObject(); if(num(j,"v",1)!=1)continue;
                        Frame previous=FRAME.get(); List<Obj> objects=previous==null?List.of():previous.objects;
                        if(!j.has("objectLayers")&&previous!=null&&previous.packet.has("objectLayers"))j.add("objectLayers",previous.packet.get("objectLayers"));
                        if(j.has("objects")){
                            List<Obj> next=new ArrayList<>();
                            for(JsonElement e:j.getAsJsonArray("objects")) {JsonObject o=e.getAsJsonObject();next.add(new Obj((int)num(o,"id",0),(int)num(o,"objectId",0),str(o,"type","decor"),num(o,"x",0),num(o,"y",0),num(o,"w",30),num(o,"h",30),num(o,"rotation",0),num(o,"scale",1),num(o,"vx",num(o,"x",0)),num(o,"vy",num(o,"y",0)),num(o,"vw",30*num(o,"scale",1)),num(o,"vh",30*num(o,"scale",1)),visualColor(o),str(o,"visualShape",""),(int)num(o,"spikePeaks",0),num(o,"bodyX",num(o,"vx",num(o,"x",0))),num(o,"bodyY",num(o,"vy",num(o,"y",0))),num(o,"bodyWidth",num(o,"vw",30*num(o,"scale",1))),num(o,"bodyHeight",num(o,"vh",30*num(o,"scale",1))),num(o,"bodyRotation",num(o,"rotation",0)),!o.has("collisionEnabled")||bool(o,"collisionEnabled"),!o.has("visualEnabled")||bool(o,"visualEnabled"),bool(o,"nativeAdditive"),str(o,"nativeFrameName","")));}
                            objects=List.copyOf(next);
                        }
                        ground=num(j,"ground",90)/30;
                        Frame f=new Frame((long)num(j,"seq",frames),System.nanoTime(),num(j,"x",0),num(j,"y",105),num(j,"rotation",0),num(j,"scale",1),str(j,"mode","cube"),bool(j,"dead"),bool(j,"paused"),num(j,"percent",0),str(j,"name","Geometry Dash"),objects,j);
                        FRAME.set(f);frames++;
                    }catch(Exception ignored){}
                }
            }catch(Exception e){commands=null;connection="GD disconnected; waiting for reconnect";}
        }catch(Exception e){connection="GD listener error: "+e.getMessage();}
    }
    private static void render(WorldRenderContext ctx){
        Frame f=FRAME.get(); if(f==null||!active())return;
        MinecraftClient mc=MinecraftClient.getInstance(); MatrixStack m=ctx.matrixStack(); Vec3d camera=ctx.camera().getPos();
        m.push();m.translate(-camera.x,-camera.y,-camera.z);
        VertexConsumerProvider.Immediate immediate=mc.getBufferBuilders().getEntityVertexConsumers();
        for(Obj o:f.objects){
            if(Math.abs(o.x-f.x)>1800)continue;
            double w=Math.max(.08,o.w/30),h=Math.max(.08,o.h/30),x=o.x/30,y=64+o.y/30;
            if(o.type.equals("solid")&&!str(f.packet,"source","").equals("minecraft")) block(m,immediate,x-w/2,y-h/2,-.5,w,h,1,Blocks.STONE_BRICKS);
            else if(o.type.equals("orb"))block(m,immediate,x-.22,y-.22,-.25,.44,.44,.5,Blocks.GOLD_BLOCK);
            else if(o.type.equals("portal"))block(m,immediate,x-w/2,y-h/2,-.3,Math.max(.2,w),h,.6,Blocks.AMETHYST_BLOCK);
        }
        // Legacy sender compatibility only: modern avatarLayers are authoritative, including empty/dead.
        if(!f.packet.has("avatarLayers")){
        m.push();m.translate(f.x/30,64+f.y/30,0);m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)-f.rotation));
        double size=Math.max(.1,f.scale);
        block(m,immediate,-size/2,-size/2,-size/2,size,size,size,f.dead?Blocks.REDSTONE_BLOCK:Blocks.DIAMOND_BLOCK);m.pop();
        }
        immediate.draw();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);RenderSystem.disableCull();RenderSystem.enableDepthTest();
        BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(VertexFormat.DrawMode.TRIANGLES,VertexFormats.POSITION_COLOR);
        for(Obj o:f.objects)if(o.type.equals("hazard")&&Math.abs(o.x-f.x)<1800){
            m.push();m.translate(o.vx/30,64+o.vy/30,.5);m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)-o.rotation));
            spike(b,m.peek().getPositionMatrix(),Math.max(.15,o.vw/30),Math.max(.15,o.vh/30));m.pop();
        }
        BufferRenderer.drawWithGlobalProgram(b.end());RenderSystem.enableCull();
        m.pop(); // GD player/trails now render through the native entity and shadow passes.
    }
    public static void renderObjectsLit(MatrixStack m,VertexConsumerProvider buffers,Frame frame,double ox,double oy,double oz){
        MinecraftClient mc=MinecraftClient.getInstance();if(mc.world==null)return;
        // In a compiled Minecraft build, world blocks own obstacle geometry.
        boolean worldGeometry=WorldEditor.worldGeometryActive(frame);
        boolean nativeCards=frame.packet.has("objectLayers");
        for(Obj o:frame.objects){if(Math.abs(o.x-frame.x)>1200)continue;double x=o.x/30,y=64+o.y/30,w=Math.max(.04,o.w/30),h=Math.max(.04,o.h/30);int light=WorldRenderer.getLightmapCoordinates(mc.world,BlockPos.ofFloored(x,y,.5));
            if(worldGeometry&&WorldEditor.ownsGameplayPoint(o.x(),o.y())&&(o.type.equals("solid")||o.type.equals("hazard")))continue;
            // Hidden native collision helpers retain GD physics, but must not
            // become visible Minecraft walls or spikes. Missing visualEnabled
            // defaults to true when decoding older senders.
            if(o.type.equals("solid")&&o.collisionEnabled()&&o.visualEnabled()&&(!str(frame.packet,"source","").equals("minecraft")||WorldEditor.nativeGeometry()))blockLit(m,buffers,x-ox-w/2,y-oy-h/2,-oz,w,h,1,Blocks.STONE_BRICKS,light);
            else if(o.type.equals("hazard")&&o.collisionEnabled()&&o.visualEnabled())NativeSpikes3D.render(m,buffers,o,ox,oy,oz,light);
            else if(BridgeVisualStyle.volume()&&o.visualEnabled()&&(o.type.equals("orb")||o.type.equals("portal")))NativeObjectBodies3D.render(m,buffers,o,ox,oy,oz,light);
        }
    }
    private static void blockLit(MatrixStack m,VertexConsumerProvider buffers,double x,double y,double z,double w,double h,double d,net.minecraft.block.Block block,int light){m.push();m.translate(x,y,z);m.scale((float)w,(float)h,(float)d);MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(block.getDefaultState(),m,buffers,light,OverlayTexture.DEFAULT_UV);m.pop();}
    private static void triangleLit(VertexConsumer consumer,MatrixStack m,double[] a,double[] b,double[] c,int light){
        var u=new org.joml.Vector3f((float)(b[0]-a[0]),(float)(b[1]-a[1]),(float)(b[2]-a[2]));var v=new org.joml.Vector3f((float)(c[0]-a[0]),(float)(c[1]-a[1]),(float)(c[2]-a[2]));u.cross(v).normalize();
        double[][] vertices={a,b,c,c};float[][] uv={{0,1},{1,1},{.5f,0},{.5f,0}};
        for(int i=0;i<4;i++)consumer.vertex(m.peek().getPositionMatrix(),(float)vertices[i][0],(float)vertices[i][1],(float)vertices[i][2]).color(255,255,255,255).texture(uv[i][0],uv[i][1]).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(m.peek().getNormalMatrix(),u.x,u.y,u.z).next();
    }
    private static void spikeLit(VertexConsumer consumer,MatrixStack m,double w,double h,int light){double[] a={-w/2,-h/2,.5},b={w/2,-h/2,.5},c={0,h/2,.5},aa={-w/2,-h/2,-.5},bb={w/2,-h/2,-.5},cc={0,h/2,-.5};triangleLit(consumer,m,a,b,c,light);triangleLit(consumer,m,aa,cc,bb,light);triangleLit(consumer,m,a,c,cc,light);triangleLit(consumer,m,a,cc,aa,light);triangleLit(consumer,m,b,bb,cc,light);triangleLit(consumer,m,b,cc,c,light);triangleLit(consumer,m,a,aa,bb,light);triangleLit(consumer,m,a,bb,b,light);}
    private static void block(MatrixStack m,VertexConsumerProvider buffers,double x,double y,double z,double w,double h,double d,net.minecraft.block.Block block){
        m.push();m.translate(x,y,z);m.scale((float)w,(float)h,(float)d);
        MinecraftClient mc=MinecraftClient.getInstance();int light=mc.world==null?0xF000F0:WorldRenderer.getLightmapCoordinates(mc.world,BlockPos.ofFloored(x,y,z));
        mc.getBlockRenderManager().renderBlockAsEntity(block.getDefaultState(),m,buffers,light,OverlayTexture.DEFAULT_UV);m.pop();
    }
    private static void vertex(BufferBuilder b,Matrix4f m,double x,double y,double z,float shade){b.vertex(m,(float)x,(float)y,(float)z).color(shade,shade*.9f,shade*.9f,1).next();}
    private static void tri(BufferBuilder b,Matrix4f m,double[] a,double[] c,double[] d,float shade){vertex(b,m,a[0],a[1],a[2],shade);vertex(b,m,c[0],c[1],c[2],shade);vertex(b,m,d[0],d[1],d[2],shade);}
    private static void spike(BufferBuilder b,Matrix4f m,double w,double h){
        double[] a={-w/2,-h/2,.5},c={w/2,-h/2,.5},d={0,h/2,.5},aa={-w/2,-h/2,-.5},cc={w/2,-h/2,-.5},dd={0,h/2,-.5};
        tri(b,m,a,c,d,1);tri(b,m,aa,dd,cc,.5f);tri(b,m,a,d,dd,.7f);tri(b,m,a,dd,aa,.7f);tri(b,m,c,cc,dd,.85f);tri(b,m,c,dd,d,.85f);tri(b,m,a,aa,cc,.4f);tri(b,m,a,cc,c,.4f);
    }
}
