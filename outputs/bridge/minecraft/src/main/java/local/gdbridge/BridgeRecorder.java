package local.gdbridge;

import com.google.gson.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.world.ClientWorld;

/** Bounded game-frame recording using Minecraft's native framebuffer screenshot API. */
public final class BridgeRecorder {
    private static final Path ROOT=Path.of("C:/Users/shelk/Documents/Codex/2026-10-02/minecraft-java-geometry-dash-nasgubb-xo/outputs/bridge/recordings");
    private static Path directory;
    private static JsonObject metadata;
    private static JsonArray frames;
    private static long started,next,period,end;
    private static int fps,limit,index;
    private static boolean recording;
    private static Screen previousScreen;
    private static ClientWorld recordedWorld;
    private static boolean restoreEligible;
    private BridgeRecorder() {}
    public static boolean active(){return recording;}
    public static String start(MinecraftClient mc,JsonObject request) throws java.io.IOException {
        if(recording)throw new IllegalStateException("Recording already active");
        if(mc.world==null)throw new IllegalStateException("No Minecraft world");
        double duration=request.has("durationSeconds")?request.get("durationSeconds").getAsDouble():10;
        fps=request.has("fps")?request.get("fps").getAsInt():8;
        if(!Double.isFinite(duration)||duration<1||duration>60||fps<1||fps>20)throw new IllegalArgumentException("Recording requires duration1..60 seconds and fps1..20");
        limit=Math.min(1200,(int)Math.ceil(duration*fps));index=0;
        directory=ROOT.resolve("recording-"+Instant.now().toEpochMilli()+"-"+UUID.randomUUID().toString().substring(0,8));Files.createDirectories(directory);
        frames=new JsonArray();metadata=new JsonObject();metadata.addProperty("state","capturing");metadata.addProperty("startedUtc",Instant.now().toString());metadata.addProperty("fps",fps);metadata.addProperty("durationSeconds",duration);metadata.addProperty("maximumFrames",limit);metadata.addProperty("screenshots",directory.resolve("screenshots").toString());metadata.add("frames",frames);
        recordedWorld=mc.world;previousScreen=null;restoreEligible=false;
        boolean resume=request.has("resumeForRecording")&&request.get("resumeForRecording").getAsBoolean();
        if(resume&&mc.currentScreen instanceof GameMenuScreen){previousScreen=mc.currentScreen;restoreEligible=true;mc.setScreen(null);}
        metadata.addProperty("resumeForRecording",resume);metadata.addProperty("resumed",previousScreen!=null);metadata.addProperty("previousScreen",previousScreen==null?"":previousScreen.getClass().getName());metadata.addProperty("restored",false);
        started=System.nanoTime();period=1_000_000_000L/fps;next=started+50_000_000L;end=started+(long)(duration*1_000_000_000L);recording=true;save();
        return "Native framebuffer recording started: "+directory;
    }
    public static void tick(MinecraftClient mc){
        if(!recording)return;long now=System.nanoTime();
        try {
            if(mc.world!=recordedWorld||now>=end||index>=limit){finish(mc,mc.world!=recordedWorld?"world changed":"duration complete");return;}
            if(previousScreen!=null&&mc.currentScreen!=null){restoreEligible=false;metadata.addProperty("userChangedScreen",true);}
            if(now<next)return;next+=period;if(next<now-period)next=now+period;
            String filename=String.format(java.util.Locale.ROOT,"frame-%05d.png",index++);
            ScreenshotRecorder.saveScreenshot(directory.toFile(),filename,mc.getFramebuffer(),text->{});
            JsonObject item=new JsonObject();item.addProperty("index",index-1);item.addProperty("timeSeconds",(now-started)/1_000_000_000.0);item.addProperty("path",directory.resolve("screenshots").resolve(filename).toString());
            var frame=GDBridge.FRAME.get();if(frame!=null){item.addProperty("seq",frame.seq());item.addProperty("x",frame.x());item.addProperty("y",frame.y());item.addProperty("name",frame.name());item.addProperty("dead",frame.dead());item.addProperty("levelId",frame.packet().has("level")?frame.packet().get("level").getAsInt():0);item.addProperty("diagnosticNoclip",frame.packet().has("diagnosticNoclip")&&frame.packet().get("diagnosticNoclip").getAsBoolean());}
            item.addProperty("cubeDepthFaces",NativeGDVisuals.lastCubeDepthFaces);item.addProperty("nativeParticleQuadsDrawn",NativeGDVisuals.lastParticleQuads);
            if(frame!=null){item.addProperty("mode",frame.mode());if(frame.packet().has("demoPilotActive"))item.add("demoPilotActive",frame.packet().get("demoPilotActive"));if(frame.packet().has("demoPilotHolding"))item.add("demoPilotHolding",frame.packet().get("demoPilotHolding"));item.addProperty("levelCompleted",frame.packet().has("levelCompleted")&&frame.packet().get("levelCompleted").getAsBoolean());}
            frames.add(item);metadata.addProperty("capturedFrames",index);if(index%fps==0)save();
        }catch(Exception error){recording=false;restoreScreen(mc);metadata.addProperty("state","failed");metadata.addProperty("error",error.getMessage());try{save();}catch(Exception ignored){}}
    }
    private static void restoreScreen(MinecraftClient mc){if(previousScreen!=null&&restoreEligible&&mc.currentScreen==null&&mc.world==recordedWorld){mc.setScreen(previousScreen);metadata.addProperty("restored",true);}previousScreen=null;recordedWorld=null;restoreEligible=false;}
    private static void finish(MinecraftClient mc,String reason) throws java.io.IOException {recording=false;restoreScreen(mc);metadata.addProperty("state","capture-complete");metadata.addProperty("reason",reason);metadata.addProperty("capturedFrames",index);if(frames.size()>1){double elapsed=frames.get(frames.size()-1).getAsJsonObject().get("timeSeconds").getAsDouble()-frames.get(0).getAsJsonObject().get("timeSeconds").getAsDouble();metadata.addProperty("actualFps",elapsed>0?(frames.size()-1)/elapsed:0);}metadata.addProperty("finishedUtc",Instant.now().toString());metadata.addProperty("note","PNG encoding is asynchronous; verify all PNGs are complete before video encoding.");save();}
    private static void save() throws java.io.IOException {Files.writeString(directory.resolve("recording.json"),metadata.toString());}
}
