package local.gdbridge;
import com.google.gson.*;
import java.nio.file.*;
import java.util.Set;
import net.minecraft.client.MinecraftClient;

/** Minecraft collision geometry; original GD specials and actor by default. */
public final class BridgeVisualStyle {
    private static long checked,modified=-1;
    public static String mode="hybrid";
    private BridgeVisualStyle() {}
    public static boolean volume(){return mode.equals("minecraft")||mode.equals("volume");}
    private static Path path(MinecraftClient mc){return mc.runDirectory.toPath().resolve("config/gdbridge/visual-style.json");}
    public static void reload(MinecraftClient mc){if(System.nanoTime()-checked<500_000_000L)return;checked=System.nanoTime();try{Path file=path(mc);if(!Files.exists(file))return;long stamp=Files.getLastModifiedTime(file).toMillis();if(stamp==modified)return;String value=JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("mode").getAsString();if(Set.of("hybrid","native","minecraft","volume").contains(value)){mode=value;modified=stamp;}}catch(Exception ignored){}}
    public static void set(MinecraftClient mc,String value) throws java.io.IOException {if(!Set.of("hybrid","native","minecraft","volume").contains(value))throw new IllegalArgumentException("Visual style must be hybrid or minecraft");Path file=path(mc);Files.createDirectories(file.getParent());JsonObject config=new JsonObject();config.addProperty("mode",value);Files.writeString(file,config.toString());mode=value;modified=Files.getLastModifiedTime(file).toMillis();}
}
