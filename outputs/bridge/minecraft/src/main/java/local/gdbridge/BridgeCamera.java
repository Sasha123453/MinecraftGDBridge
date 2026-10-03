package local.gdbridge;
import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.client.MinecraftClient;
public final class BridgeCamera {
    public static volatile double distance=9.5,offsetX=3.2,height=3.6,playerHeight=1.6,yaw=174,pitch=10,cubeDeadzone=3.3;
    public static volatile boolean debugHud;
    private static double smoothY,firstNativeY,firstActorY,actorAnchor,lastX;
    private static String cameraLevel="";
    private static boolean anchored,hasNativeCamera;
    private static long previousSeq=-1,followTime;
    public static double followY(GDBridge.Frame frame){
        long now=System.nanoTime();String level=frame.name()+":"+(frame.packet().has("level")?frame.packet().get("level").getAsString():"");
        double base=GDBridge.ground+height,actorY=frame.y()/30;
        double nativeY=frame.packet().has("nativeCameraY")?frame.packet().get("nativeCameraY").getAsDouble():Double.NaN;
        if(!anchored||!cameraLevel.equals(level)||frame.seq()<previousSeq||frame.x()<lastX-120){anchored=true;cameraLevel=level;smoothY=base;firstActorY=actorAnchor=actorY;hasNativeCamera=Double.isFinite(nativeY);firstNativeY=nativeY;followTime=now;}
        if(!hasNativeCamera&&Double.isFinite(nativeY)){hasNativeCamera=true;firstNativeY=nativeY;}
        double target=base;
        if(hasNativeCamera&&Double.isFinite(nativeY))target=base+(nativeY-firstNativeY)/30;
        else {double deadzone=Math.max(2,cubeDeadzone);if(actorY>actorAnchor+deadzone)actorAnchor=actorY-deadzone;else if(actorY<actorAnchor-deadzone)actorAnchor=actorY+deadzone;target=base+actorAnchor-firstActorY;}
        double dt=Math.max(0,Math.min(.1,(now-followTime)/1_000_000_000.0));smoothY+=(target-smoothY)*(1-Math.exp(-dt/.18));
        followTime=now;previousSeq=frame.seq();lastX=frame.x();return smoothY;
    }
    private static long lastCheck,modified=-1;
    public static void reload(MinecraftClient mc){
        if(System.nanoTime()-lastCheck<1_000_000_000L)return;lastCheck=System.nanoTime();
        try {Path path=mc.runDirectory.toPath().resolve("config/gdbridge/camera.json");
            if(!Files.exists(path)){Files.createDirectories(path.getParent());Files.writeString(path,"{\"distance\":9.5,\"offsetX\":3.2,\"height\":3.6,\"playerHeight\":1.6,\"yaw\":174,\"pitch\":10,\"cubeDeadzone\":3.3}");}
            long stamp=Files.getLastModifiedTime(path).toMillis();if(stamp==modified)return;
            JsonObject j=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            distance=value(j,"distance",9.5,4,60);offsetX=value(j,"offsetX",3.2,-20,30);height=value(j,"height",3.6,0,20);playerHeight=value(j,"playerHeight",1.6,-5,20);yaw=value(j,"yaw",174,0,360);pitch=value(j,"pitch",10,-60,60);cubeDeadzone=value(j,"cubeDeadzone",3.3,0,12);modified=stamp;
            debugHud=j.has("debugHud")&&j.get("debugHud").getAsBoolean();
        }catch(Exception ignored){} // Partial writes retain the last valid camera.
    }
    private static double value(JsonObject j,String key,double fallback,double min,double max){double n=j.has(key)?j.get(key).getAsDouble():fallback;return Double.isFinite(n)?Math.max(min,Math.min(max,n)):fallback;}
}
