package local.gdbridge;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;

/** Original native GD records remain attached to their actual Minecraft authoring cells. */
public final class SceneBlueprint {
    private static final Path LEVELS=Path.of("C:/Users/shelk/Documents/Codex/2026-10-02/minecraft-java-geometry-dash-nasgubb-xo/outputs/bridge/levels");
    public record Blueprint(JsonObject source,Map<BlockPos,List<JsonObject>> groups,Map<BlockPos,Block> blocks,int clipped) {}
    public static Blueprint read(String absolutePath) throws IOException {
        return from(readSource(absolutePath));
    }
    public static JsonObject readSource(String absolutePath) throws IOException {
        Path file=Path.of(absolutePath);
        if(!file.isAbsolute())throw new IOException("Blueprint path must be absolute");
        Path root=LEVELS.toRealPath();file=file.toRealPath();
        if(!file.startsWith(root)||!Files.isRegularFile(file))throw new IOException("Blueprint must be inside outputs/bridge/levels");
        if(Files.size(file)>64L*1024*1024)throw new IOException("Blueprint exceeds 64MiB");
        try{return JsonParser.parseString(Files.readString(file)).getAsJsonObject();}
        catch(RuntimeException error){throw new IOException("Invalid native GD blueprint",error);}
    }
    public static Blueprint from(JsonObject original) {
        JsonObject source=original.deepCopy();double maxX=15375;
        if(source.has("rangeMaxX")){double supplied=source.get("rangeMaxX").getAsDouble();if(Double.isFinite(supplied)&&supplied>0)maxX=Math.min(supplied,maxX);}
        source.addProperty("rangeMaxX",maxX);
        Map<BlockPos,List<JsonObject>> groups=new LinkedHashMap<>();Map<BlockPos,Block> blocks=new LinkedHashMap<>();
        Map<BlockPos,Integer> priorities=new HashMap<>();int clipped=0;
        if(!source.has("objects")||!source.get("objects").isJsonArray())throw new IllegalArgumentException("Native objects array missing");
        for(JsonElement entry:source.getAsJsonArray("objects")) {
            if(!entry.isJsonObject()){clipped++;continue;}JsonObject object=entry.getAsJsonObject();
            try {
                double x=object.get("x").getAsDouble(),y=object.get("y").getAsDouble();
                if(!Double.isFinite(x)||!Double.isFinite(y)||x<0||x>maxX||y<90||y>1080){clipped++;continue;}
                BlockPos cell=new BlockPos((int)Math.round(x/30-.5),(int)Math.round(64+y/30-.5),0);
                JsonObject preserved=object.deepCopy();groups.computeIfAbsent(cell,key->new ArrayList<>()).add(preserved);
                String type=object.has("type")?object.get("type").getAsString():"solid";
                int priority=switch(type){case "portal"->4;case "orb"->3;case "hazard"->2;default->1;};
                if(priority>=priorities.getOrDefault(cell,0)){blocks.put(cell,marker(object));priorities.put(cell,priority);}
            }catch(RuntimeException error){clipped++;}
        }
        Map<BlockPos,List<JsonObject>> frozen=new LinkedHashMap<>();groups.forEach((cell,records)->frozen.put(cell,List.copyOf(records)));
        return new Blueprint(source,Collections.unmodifiableMap(frozen),Collections.unmodifiableMap(blocks),clipped);
    }
    public static Block marker(JsonObject object) {
        int id=object.has("id")?object.get("id").getAsInt():object.has("objectId")?object.get("objectId").getAsInt():1;
        return switch(id) {
            case 1->Blocks.STONE;case 8->Blocks.MAGMA_BLOCK;case 36->Blocks.GOLD_BLOCK;case 84->Blocks.DIAMOND_BLOCK;case 141->Blocks.REDSTONE_BLOCK;
            case 12->Blocks.GLASS;case 13->Blocks.AMETHYST_BLOCK;case 47->Blocks.COPPER_BLOCK;case 111->Blocks.EMERALD_BLOCK;case 660->Blocks.LAPIS_BLOCK;
            case 200->Blocks.YELLOW_CONCRETE;case 201->Blocks.BLUE_CONCRETE;case 202->Blocks.GREEN_CONCRETE;case 203->Blocks.PINK_CONCRETE;case 1334->Blocks.RED_CONCRETE;
            default->{String type=object.has("type")?object.get("type").getAsString():"solid";yield switch(type){case "hazard"->Blocks.MAGMA_BLOCK;case "orb"->Blocks.GOLD_BLOCK;case "portal"->Blocks.AMETHYST_BLOCK;default->Blocks.STONE;};}
        };
    }
    public static int id(BlockState state) {
        if(state.isAir())return 0;Block b=state.getBlock();
        if(b==Blocks.MAGMA_BLOCK||b==Blocks.IRON_BARS)return 8;
        if(b==Blocks.GOLD_BLOCK)return 36;if(b==Blocks.DIAMOND_BLOCK)return 84;if(b==Blocks.REDSTONE_BLOCK)return 141;
        if(b==Blocks.GLASS)return 12;if(b==Blocks.AMETHYST_BLOCK)return 13;if(b==Blocks.COPPER_BLOCK)return 47;if(b==Blocks.EMERALD_BLOCK)return 111;if(b==Blocks.LAPIS_BLOCK)return 660;
        if(b==Blocks.YELLOW_CONCRETE)return 200;if(b==Blocks.BLUE_CONCRETE)return 201;if(b==Blocks.GREEN_CONCRETE)return 202;if(b==Blocks.PINK_CONCRETE)return 203;if(b==Blocks.RED_CONCRETE)return 1334;
        return 1;
    }
}
