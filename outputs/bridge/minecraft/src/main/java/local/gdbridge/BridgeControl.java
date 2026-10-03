package local.gdbridge;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;

/** Local file requests apply through real Minecraft/GD APIs, without desktop input. */
public final class BridgeControl {
    private static long lastCheck,lastMenuPauseSync;private static String nonce="";private static boolean initialized,lastMenuPaused;public static volatile String result="Ready";
    public static void tick(MinecraftClient mc){
        boolean menuPaused=mc.world!=null&&mc.isPaused();
        if(GDBridge.FRAME.get()!=null&&(menuPaused!=lastMenuPaused||System.nanoTime()-lastMenuPauseSync>1_000_000_000L)){
            JsonObject command=new JsonObject();command.addProperty("cmd","minecraft-pause");command.addProperty("active",menuPaused);
            if(GDBridge.send(command)){lastMenuPaused=menuPaused;lastMenuPauseSync=System.nanoTime();}
        }
        if(System.nanoTime()-lastCheck<100_000_000L)return;lastCheck=System.nanoTime();
        Path dir=mc.runDirectory.toPath().resolve("config/gdbridge"),path=dir.resolve("control.json");
        try {if(!initialized){initialized=true;Path previous=dir.resolve("control-status.json");if(Files.exists(previous)){JsonObject ack=JsonParser.parseString(Files.readString(previous)).getAsJsonObject();if(ack.has("nonce"))nonce=ack.get("nonce").getAsString();}}if(!Files.exists(path))return;JsonObject j=JsonParser.parseString(Files.readString(path)).getAsJsonObject();String token=j.get("nonce").getAsString();if(token.equals(nonce))return;
            nonce=token;String action=j.get("action").getAsString();
            switch(action){
                case "build-mode" -> {boolean value=j.get("active").getAsBoolean();if(value!=WorldEditor.editing)WorldEditor.toggle();result="Build mode "+WorldEditor.editing;}
                case "export" -> {WorldEditor.export();result="Minecraft export queued";}
                case "restart" -> {GDBridge.sendRestart();result="Native GD restart requested";}
                case "jump" -> {GDBridge.setProgrammaticJump(j.get("down").getAsBoolean());result="Native GD input requested";}
                case "gd-pause" -> {JsonObject command=new JsonObject();command.addProperty("cmd","build-mode");command.addProperty("active",j.get("active").getAsBoolean());result=GDBridge.send(command)?"Native GD pause requested":"GD disconnected";}
                case "shutdown-gd" -> {JsonObject command=new JsonObject();command.addProperty("cmd","shutdown-gd");result=GDBridge.send(command)?"Native GD save/exit requested":"GD disconnected";}
                case "quit" -> {mc.scheduleStop();result="Minecraft save/exit requested";}
                case "resume-world" -> {if(mc.world==null)throw new IllegalStateException("No local world");if(mc.currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen){mc.setScreen(null);result="Minecraft pause menu closed through native API";}else if(mc.currentScreen==null){result="Minecraft world already active";}else throw new IllegalStateException("Resume supports only the Minecraft pause menu");}
                case "load-level" -> {JsonObject command=new JsonObject();command.addProperty("input","load-level");command.addProperty("id",j.get("id").getAsInt());result=GDBridge.send(command)?"GD level request sent":"GD disconnected";}
                case "load-level-data" -> {JsonObject source=SceneBlueprint.readSource(j.get("path").getAsString());JsonObject command=new JsonObject();command.addProperty("input","load-level-data");for(String field:java.util.List.of("id","name","songId","audioTrack","levelString","rawLevelString","downloadMusic"))if(source.has(field))command.add(field,source.get(field));if(!command.has("id")&&source.has("levelId"))command.add("id",source.get("levelId"));result=GDBridge.send(command)?"Original GD data load requested":"GD disconnected";}
                case "set-time" -> {if(mc.getServer()==null)throw new IllegalStateException("No local world");long time=j.get("time").getAsLong();mc.getServer().execute(()->mc.getServer().getOverworld().setTimeOfDay(time));result="World time requested: "+time;}
                case "camera" -> {JsonObject camera=j.getAsJsonObject("camera");Files.createDirectories(dir);Files.writeString(dir.resolve("camera.json"),camera.toString());result="Camera config saved";}
                case "screenshot" -> {if(mc.world==null)throw new IllegalStateException("No world");String filename="bridge-"+token.replaceAll("[^A-Za-z0-9_-]","_")+".png";ScreenshotRecorder.saveScreenshot(mc.runDirectory,filename,mc.getFramebuffer(),text->{});result="Native framebuffer saved: screenshots/"+filename;}
                case "shaders" -> {applyShaders(mc,j);result="Iris shaders applied";}
                case "open-world" -> {openWorld(mc,j.get("world").getAsString());result="World switch requested";}
                case "set-demo" -> {WorldEditor.buildDemo(mc,!j.has("gameplay")||j.get("gameplay").getAsBoolean());result="Persistent demo construction queued";}
                case "import-blueprint" -> {WorldEditor.importBlueprint(mc,j.get("path").getAsString());result="Blueprint import queued";}
                case "scenery" -> {WorldEditor.applyScenery(mc,j.get("path").getAsString());result="Scenery plan queued";}
                case "music-config" -> {JsonObject command=musicConfig(j);result=GDBridge.send(command)?"Native GD music configuration requested":"GD disconnected";}
                case "diagnostic-noclip" -> {JsonObject command=new JsonObject();command.addProperty("cmd","diagnostic-noclip");command.addProperty("active",j.get("active").getAsBoolean());if(j.has("durationSeconds")){double duration=j.get("durationSeconds").getAsDouble();if(!Double.isFinite(duration)||duration<1||duration>60)throw new IllegalArgumentException("Noclip audit duration must be 1..60 seconds");command.addProperty("durationSeconds",duration);}result=GDBridge.send(command)?"Native GD diagnostic noclip requested":"GD disconnected";}
                case "record" -> {result=BridgeRecorder.start(mc,j);}
                case "visual-style" -> {BridgeVisualStyle.set(mc,j.get("mode").getAsString());result="GD visual style: "+BridgeVisualStyle.mode;}
                case "move-marker" -> {JsonArray a=j.getAsJsonArray("from"),b=j.getAsJsonArray("to");WorldEditor.moveMarker(mc,new net.minecraft.util.math.BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),0),new net.minecraft.util.math.BlockPos(b.get(0).getAsInt(),b.get(1).getAsInt(),0));result="Minecraft marker move queued";}
                default -> throw new IllegalArgumentException("Unknown action: "+action);
            }
            JsonObject ack=new JsonObject();ack.addProperty("nonce",token);ack.addProperty("action",action);ack.addProperty("result",result);ack.addProperty("editing",WorldEditor.editing);Files.createDirectories(dir);Files.writeString(dir.resolve("control-status.json"),ack.toString());
        }catch(Exception e){result="Control error: "+e.getMessage();try{Files.createDirectories(dir);JsonObject ack=new JsonObject();ack.addProperty("nonce",nonce);ack.addProperty("error",result);Files.writeString(dir.resolve("control-status.json"),ack.toString());}catch(Exception ignored){}}
    }
    private static void applyShaders(MinecraftClient mc,JsonObject request) throws Exception {
        Class<?> iris=Class.forName("net.irisshaders.iris.Iris");
        Object irisConfig=iris.getMethod("getIrisConfig").invoke(null);
        if(request.has("pack")){
            String name=request.get("pack").getAsString();
            if(name.isBlank()||name.contains("/")||name.contains("\\")||!name.toLowerCase(Locale.ROOT).endsWith(".zip"))throw new IllegalArgumentException("Shader pack must be a ZIP filename");
            Path root=mc.runDirectory.toPath().resolve("shaderpacks").toRealPath(),pack=root.resolve(name).toRealPath();
            if(!pack.startsWith(root)||!Files.isRegularFile(pack))throw new IllegalArgumentException("Shader pack must be an installed ZIP under shaderpacks");
            if(!(Boolean)iris.getMethod("isValidShaderpack",Path.class).invoke(null,pack))throw new IllegalArgumentException("Invalid Iris shader pack");
            irisConfig.getClass().getMethod("setShaderPackName",String.class).invoke(irisConfig,name);
        }
        if(request.has("enabled"))irisConfig.getClass().getMethod("setShadersEnabled",boolean.class).invoke(irisConfig,request.get("enabled").getAsBoolean());
        irisConfig.getClass().getMethod("save").invoke(irisConfig);
        iris.getMethod("reload").invoke(null);
    }
    private static JsonObject musicConfig(JsonObject request) throws java.io.IOException {
        Path musicRoot=Path.of("C:/Users/shelk/Documents/Codex/2026-10-02/minecraft-java-geometry-dash-nasgubb-xo/outputs/music").toRealPath();
        JsonObject source=request;
        if(request.has("path")&&request.get("path").getAsString().toLowerCase(Locale.ROOT).endsWith(".json")){
            Path supplied=Path.of(request.get("path").getAsString());if(!supplied.isAbsolute())throw new IllegalArgumentException("Music config path must be absolute");
            Path real=supplied.toRealPath();if(!real.startsWith(musicRoot)||!Files.isRegularFile(real)||Files.size(real)>65536)throw new IllegalArgumentException("Music config must be a JSON file under outputs/music, at most 64KiB");
            source=JsonParser.parseString(Files.readString(real)).getAsJsonObject();
        }
        JsonObject command=new JsonObject();command.addProperty("cmd","music-config");
        for(String field:List.of("enabled","path","offset","volume"))if(source.has(field))command.add(field,source.get(field));
        boolean enabled=command.has("enabled")&&command.get("enabled").getAsBoolean();
        if(command.has("offset")){double offset=command.get("offset").getAsDouble();if(!Double.isFinite(offset)||offset<0||offset>86400)throw new IllegalArgumentException("Music offset must be 0..86400 seconds");}
        if(command.has("volume")){double volume=command.get("volume").getAsDouble();if(!Double.isFinite(volume)||volume<0||volume>1)throw new IllegalArgumentException("Music volume must be 0..1");}
        String filename=command.has("path")?command.get("path").getAsString():"";
        if(enabled&&filename.isEmpty())throw new IllegalArgumentException("Enabled custom music requires an audio path");
        if(!filename.isEmpty()){
            Path audio=Path.of(filename);if(!audio.isAbsolute())throw new IllegalArgumentException("Music audio path must be absolute");audio=audio.toRealPath();
            String name=audio.getFileName().toString().toLowerCase(Locale.ROOT);
            if(!audio.startsWith(musicRoot)||!Files.isRegularFile(audio)||Files.size(audio)<1||Files.size(audio)>256L*1024*1024||!(name.endsWith(".mp3")||name.endsWith(".ogg")||name.endsWith(".wav")))throw new IllegalArgumentException("Music audio must be MP3/OGG/WAV under outputs/music, at most 256MiB");
            command.addProperty("path",audio.toString());
        }
        return command;
    }
    private static void openWorld(MinecraftClient mc,String name){
        if(!Set.of("GDBridge","GDBridge-XO","GDBridge-GeometryTests").contains(name))throw new IllegalArgumentException("Use an isolated bridge world");
        if(mc.world!=null){mc.world.disconnect();mc.disconnect(new net.minecraft.client.gui.screen.ProgressScreen(true));}WorldEditor.resetWorld(name);WorldEditor.editing=true;
        try{Path selected=mc.runDirectory.toPath().resolve("config/gdbridge/selected-world.json");Files.createDirectories(selected.getParent());Files.writeString(selected,"{\"world\":\""+name+"\"}");}catch(Exception ignored){}
        JsonObject pause=new JsonObject();pause.addProperty("cmd","build-mode");pause.addProperty("active",true);GDBridge.send(pause);
        if(Files.exists(mc.runDirectory.toPath().resolve("saves/"+name+"/level.dat")))mc.createIntegratedServerLoader().start(mc.currentScreen,name);
        else {var rules=new net.minecraft.world.GameRules();rules.get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,null);rules.get(net.minecraft.world.GameRules.DO_WEATHER_CYCLE).set(false,null);rules.get(net.minecraft.world.GameRules.DO_MOB_SPAWNING).set(false,null);
            var info=new net.minecraft.world.level.LevelInfo(name,net.minecraft.world.GameMode.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,rules,net.minecraft.resource.DataConfiguration.SAFE_MODE);
            mc.createIntegratedServerLoader().createAndStart(name,info,new net.minecraft.world.gen.GeneratorOptions(0,false,false),r->r.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).get(net.minecraft.world.gen.WorldPresets.FLAT).createDimensionsRegistryHolder());}
    }
}
