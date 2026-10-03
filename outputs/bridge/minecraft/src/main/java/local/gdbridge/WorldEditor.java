package local.gdbridge;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Minecraft's integrated server owns the authored level. GD only supplies physics. */
public final class WorldEditor {
    public static volatile boolean editing;
    public static volatile String message="F7 build | F6 play Minecraft level";
    private record Cell(BlockPos pos,BlockState state,boolean hazard) {}
    private static volatile List<Cell> authored=List.of();
    private static final Set<Integer> columns=new HashSet<>();
    private static boolean prepared;
    public static String worldName="GDBridge";
    private static volatile JsonObject scene;
    private static Map<BlockPos,List<JsonObject>> templates=new HashMap<>();
    private static Map<BlockPos,Block> templateMaterials=new HashMap<>();
    public static boolean nativeGeometry(){return scene!=null;}
    private record SceneryBox(BlockPos from,BlockPos to,BlockState state){}
    public static void applyScenery(MinecraftClient mc,String filename) throws java.io.IOException {
        if(!worldName.equals("GDBridge-XO")||mc.getServer()==null)throw new IllegalStateException("Scenery only in isolated GDBridge-XO");
        JsonObject plan=SceneBlueprint.readSource(filename);List<SceneryBox> boxes=new ArrayList<>();
        for(JsonElement entry:plan.getAsJsonArray("boxes")){JsonObject box=entry.getAsJsonObject();BlockPos from=sceneryPos(box.getAsJsonArray("from")),to=sceneryPos(box.getAsJsonArray("to"));if(Math.min(from.getZ(),to.getZ())<=0&&Math.max(from.getZ(),to.getZ())>=0)throw new IllegalArgumentException("Scenery cannot touch gameplay z=0");boxes.add(new SceneryBox(from,to,sceneryMaterial(box.get("block").getAsString()).getDefaultState()));}
        if(plan.has("terrainFloor"))for(JsonElement entry:plan.getAsJsonArray("terrainFloor")){JsonObject box=entry.getAsJsonObject();BlockPos from=sceneryPos(box.getAsJsonArray("from"),true),to=sceneryPos(box.getAsJsonArray("to"),true);if(Math.max(from.getY(),to.getY())>66)throw new IllegalArgumentException("Terrain floor must remain below authored gameplay markers");Block material=sceneryMaterial(box.get("block").getAsString());if(material!=Blocks.STONE&&material!=Blocks.DEEPSLATE&&material!=Blocks.COBBLESTONE&&material!=Blocks.BEDROCK)throw new IllegalArgumentException("Terrain floor requires solid cave stone");boxes.add(new SceneryBox(from,to,material.getDefaultState()));}
        if(plan.has("plants"))for(JsonElement entry:plan.getAsJsonArray("plants")){JsonObject plant=entry.getAsJsonObject();BlockPos pos=sceneryPos(plant.getAsJsonArray("at"));boxes.add(new SceneryBox(pos,pos,sceneryMaterial(plant.get("block").getAsString()).getDefaultState()));}
        long total=0;for(SceneryBox box:boxes)total+=(Math.abs(box.from.getX()-box.to.getX())+1L)*(Math.abs(box.from.getY()-box.to.getY())+1)*(Math.abs(box.from.getZ()-box.to.getZ())+1);if(total>16384)throw new IllegalArgumentException("Scenery batch exceeds 16384 cells");
        mc.getServer().execute(()->{ServerWorld world=mc.getServer().getOverworld();
            // Remove only the exact earlier generated back-wall footprint and matching materials.
            if(plan.has("cleanupOwnedBackWalls"))for(int x=8;x<=464;x+=24)for(int dx=0;dx<5;dx++)for(int y=66;y<=68;y++){BlockPos pos=new BlockPos(x+dx,y,-6);BlockState old=world.getBlockState(pos);if(old.isOf(Blocks.STONE_BRICKS)||old.isOf(Blocks.MOSSY_STONE_BRICKS))world.setBlockState(pos,Blocks.AIR.getDefaultState(),3);}
            int changed=0;for(SceneryBox box:boxes)for(BlockPos pos:BlockPos.iterate(box.from,box.to))if(!world.getBlockState(pos).equals(box.state)){world.setBlockState(pos,box.state,3);changed++;}
            if(plan.has("timeOfDay"))world.setTimeOfDay(plan.get("timeOfDay").getAsLong());message="Scenery plan applied: "+changed+" cells; gameplay unchanged";
        });
    }
    private static BlockPos sceneryPos(JsonArray a){return sceneryPos(a,false);}
    private static BlockPos sceneryPos(JsonArray a,boolean terrainFloor){BlockPos pos=new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());if(pos.getX()<0||pos.getX()>512||pos.getY()<50||pos.getY()>90||pos.getZ()<-16||pos.getZ()>24||(!terrainFloor&&pos.getZ()==0))throw new IllegalArgumentException("Scenery outside safe region or z=0");return pos;}
    private static Block sceneryMaterial(String name){return switch(name){case "minecraft:dirt"->Blocks.DIRT;case "minecraft:grass_block"->Blocks.GRASS_BLOCK;case "minecraft:stone"->Blocks.STONE;case "minecraft:stone_bricks"->Blocks.STONE_BRICKS;case "minecraft:mossy_stone_bricks"->Blocks.MOSSY_STONE_BRICKS;case "minecraft:grass"->Blocks.GRASS;case "minecraft:air"->Blocks.AIR;case "minecraft:oak_log"->Blocks.OAK_LOG;case "minecraft:oak_planks"->Blocks.OAK_PLANKS;case "minecraft:oak_leaves"->Blocks.OAK_LEAVES;case "minecraft:bookshelf"->Blocks.BOOKSHELF;case "minecraft:lantern"->Blocks.LANTERN;case "minecraft:torch"->Blocks.TORCH;case "minecraft:cobblestone"->Blocks.COBBLESTONE;case "minecraft:deepslate"->Blocks.DEEPSLATE;case "minecraft:bedrock"->Blocks.BEDROCK;case "minecraft:diamond_ore"->Blocks.DIAMOND_ORE;case "minecraft:iron_ore"->Blocks.IRON_ORE;case "minecraft:coal_ore"->Blocks.COAL_ORE;case "minecraft:redstone_ore"->Blocks.REDSTONE_ORE;case "minecraft:granite"->Blocks.GRANITE;case "minecraft:diorite"->Blocks.DIORITE;case "minecraft:obsidian"->Blocks.OBSIDIAN;case "minecraft:nether_bricks"->Blocks.NETHER_BRICKS;case "minecraft:netherrack"->Blocks.NETHERRACK;case "minecraft:lava"->Blocks.LAVA;case "minecraft:birch_log"->Blocks.BIRCH_LOG;case "minecraft:birch_leaves"->Blocks.BIRCH_LEAVES;case "minecraft:water"->Blocks.WATER;case "minecraft:glass"->Blocks.GLASS;default->throw new IllegalArgumentException("Unsupported scenery block "+name);};}
    public static void resetWorld(String name){worldName=name;prepared=false;columns.clear();authored=List.of();scene=null;templates=new HashMap<>();templateMaterials=new HashMap<>();}
    public static void importBlueprint(MinecraftClient mc,String filename) throws java.io.IOException {
        if(!worldName.equals("GDBridge-XO"))throw new IllegalStateException("Import only into isolated GDBridge-XO");
        if(mc.getServer()==null)throw new IllegalStateException("No local server");
        var blueprint=SceneBlueprint.read(filename);if(!editing)toggle();
        mc.getServer().execute(()->{
            ServerWorld world=mc.getServer().getOverworld();
            for(var old:templateMaterials.entrySet())if(!blueprint.blocks().containsKey(old.getKey())&&world.getBlockState(old.getKey()).isOf(old.getValue()))world.setBlockState(old.getKey(),Blocks.AIR.getDefaultState(),3);
            for(var entry:blueprint.blocks().entrySet())world.setBlockState(entry.getKey(),entry.getValue().getDefaultState(),3);
            List<Cell> importedCells=new ArrayList<>();for(var entry:blueprint.blocks().entrySet())importedCells.add(new Cell(entry.getKey(),entry.getValue().getDefaultState(),true));
            scene=blueprint.source();templates=new HashMap<>(blueprint.groups());templateMaterials=new HashMap<>(blueprint.blocks());
            mc.execute(()->{authored=List.copyOf(importedCells);restoreMarkers(mc,editing);});
            try{Path output=mc.runDirectory.toPath().resolve("config/gdbridge/scene-"+worldName+".json");Files.createDirectories(output.getParent());Files.writeString(output,scene.toString());}catch(Exception e){message="Scene metadata save failed: "+e.getMessage();}
            message="Imported "+templates.size()+" Minecraft markers; "+blueprint.clipped()+" outside range";
        });
    }
    public static void buildDemo(MinecraftClient mc){buildDemo(mc,true);}
    public static void buildDemo(MinecraftClient mc,boolean gameplay){
        if(!worldName.equals("GDBridge-XO"))throw new IllegalStateException("Demo only into isolated GDBridge-XO");
        if(mc.getServer()==null)throw new IllegalStateException("No server");if(!editing)toggle();
        mc.getServer().execute(()->{var world=mc.getServer().getOverworld();
            for(int x=0;x<=512;x++)for(int z=-16;z<=24;z++)putTerrain(world,x,z,true);
            if(gameplay){for(int x=12;x<180;x+=18){putIfAir(world,new BlockPos(x,67,0),Blocks.MAGMA_BLOCK);if(x%36==12)putIfAir(world,new BlockPos(x+5,67,0),Blocks.STONE);}
            putIfAir(world,new BlockPos(48,70,0),Blocks.GOLD_BLOCK);putIfAir(world,new BlockPos(90,71,0),Blocks.DIAMOND_BLOCK);putIfAir(world,new BlockPos(130,71,0),Blocks.REDSTONE_BLOCK);
            putIfAir(world,new BlockPos(200,68,0),Blocks.AMETHYST_BLOCK);putIfAir(world,new BlockPos(235,68,0),Blocks.GLASS);
            putIfAir(world,new BlockPos(270,68,0),Blocks.EMERALD_BLOCK);putIfAir(world,new BlockPos(300,68,0),Blocks.GLASS);
            putIfAir(world,new BlockPos(350,68,0),Blocks.LAPIS_BLOCK);putIfAir(world,new BlockPos(400,68,0),Blocks.GLASS);
            putIfAir(world,new BlockPos(425,67,0),Blocks.YELLOW_CONCRETE);putIfAir(world,new BlockPos(450,67,0),Blocks.BLUE_CONCRETE);}
            // Genuine world depth: these decorations never enter the GD gameplay slice.
            for(int x=8;x<480;x+=24){
                int plinth=65+(Math.floorMod(x,48)==8?1:0);
                for(int dx=0;dx<3;dx++)for(int z=4;z<=6;z++){for(int y=65;y<plinth;y++)putIfAir(world,new BlockPos(x+dx,y,z),Blocks.STONE);putIfAir(world,new BlockPos(x+dx,plinth,z),Blocks.GRASS_BLOCK);}
                putIfAir(world,new BlockPos(x+1,plinth+1,5),Blocks.OAK_LEAVES);
                for(int dx=0;dx<5;dx++)for(int y=66;y<=68;y++)putIfAir(world,new BlockPos(x+dx,y,-6),y==68?Blocks.MOSSY_STONE_BRICKS:Blocks.STONE_BRICKS);
            }
            message="Programmatic editable demo built (not the original XO level)";
        });
    }
    public static void moveMarker(MinecraftClient mc,BlockPos from,BlockPos to){
        if(mc.getServer()==null||from.getZ()!=0||to.getZ()!=0||to.getX()<0||to.getX()>512||to.getY()<67||to.getY()>100)throw new IllegalArgumentException("Move within gameplay slice");
        mc.getServer().execute(()->{var world=mc.getServer().getOverworld();if(!world.getBlockState(to).isAir()){message="Move destination occupied";return;}
            BlockState state=world.getBlockState(from);world.setBlockState(from,Blocks.AIR.getDefaultState(),3);world.setBlockState(to,state,3);
            var group=templates.remove(from);if(group!=null){List<JsonObject> shifted=new ArrayList<>();for(JsonObject object:group){JsonObject copy=object.deepCopy();copy.addProperty("x",copy.get("x").getAsDouble()+(to.getX()-from.getX())*30);copy.addProperty("y",copy.get("y").getAsDouble()+(to.getY()-from.getY())*30);shifted.add(copy);}templates.put(to,shifted);templateMaterials.put(to,templateMaterials.remove(from));
                if(scene!=null&&scene.has("objects")){JsonArray saved=scene.getAsJsonArray("objects");for(int i=0;i<saved.size();i++)for(int j=0;j<group.size();j++)if(saved.get(i).equals(group.get(j))){saved.set(i,shifted.get(j).deepCopy());break;}try{Files.writeString(mc.runDirectory.toPath().resolve("config/gdbridge/scene-"+worldName+".json"),scene.toString());}catch(Exception e){message="Marker moved; metadata save failed: "+e.getMessage();return;}}
            }
            message="Minecraft marker moved; native offsets preserved";
        });
    }
    public static void toggle() {
        MinecraftClient mc=MinecraftClient.getInstance();if(mc.player==null||mc.world==null)return;
        GDBridge.setProgrammaticJump(false);GDBridge.sendJump(false);mc.options.attackKey.setPressed(false);mc.options.useKey.setPressed(false);editing=!editing;
        JsonObject j=new JsonObject();j.addProperty("cmd","build-mode");j.addProperty("active",editing);GDBridge.send(j);
        mc.options.setPerspective(editing?Perspective.FIRST_PERSON:Perspective.THIRD_PERSON_BACK);
        mc.player.noClip=!editing;mc.player.setNoGravity(!editing);mc.player.setInvisible(!editing);
        if(editing){mc.player.getAbilities().flying=true;mc.player.sendAbilitiesUpdate();mc.player.setPosition(mc.player.getX(),70,6);mc.player.setYaw(180);mc.player.setPitch(18);}
        restoreMarkers(mc,editing);
    }
    public static void export() {
        MinecraftClient mc=MinecraftClient.getInstance();var server=mc.getServer();
        if(server==null){message="Open the local GDBridge world first";return;}
        message="Reading Minecraft blocks...";
        server.execute(()->{
            ServerWorld world=server.getOverworld();JsonArray objects=new JsonArray();List<Cell> cells=new ArrayList<>();int unsupported=0;
            for(int x=0;x<=512;x++)for(int y=67;y<=100;y++){
                BlockPos pos=new BlockPos(x,y,0);BlockState state=world.getBlockState(pos);if(state.isAir())continue;
                int id=SceneBlueprint.id(state);boolean dynamic=id!=1||scene!=null;
                if(id==1&&!state.isFullCube(world,pos)){unsupported++;continue;}
                var original=templates.get(pos);
                if(original!=null&&state.isOf(templateMaterials.get(pos)))for(JsonObject object:original)objects.add(object.deepCopy());
                else {JsonObject object=new JsonObject();object.addProperty("id",id);object.addProperty("x",(x+.5)*30);object.addProperty("y",(y+.5-64)*30);objects.add(object);}
                cells.add(new Cell(pos,state,dynamic));
            }
            int skipped=unsupported;mc.execute(()->{
                JsonObject command=new JsonObject();command.addProperty("cmd","load-minecraft-level");command.addProperty("name","Minecraft Build");command.add("objects",objects);
                command.addProperty("rangeMaxX",scene!=null&&scene.has("rangeMaxX")?scene.get("rangeMaxX").getAsDouble():15390);
                command.addProperty("rangeMinY",90);command.addProperty("rangeMaxY",1080);
                if(scene!=null){if(scene.has("rawLevelString"))command.add("baseRawLevelString",scene.get("rawLevelString"));if(scene.has("levelString"))command.add("baseLevelString",scene.get("levelString"));if(scene.has("songId"))command.add("songId",scene.get("songId"));if(scene.has("audioTrack"))command.add("audioTrack",scene.get("audioTrack"));command.addProperty("name",scene.has("name")?scene.get("name").getAsString()+" / Minecraft":"Minecraft Blueprint");}
                if(!GDBridge.send(command)){message="GD disconnected. Keep building; press F6 after reconnect.";return;}
                authored=List.copyOf(cells);GDBridge.setProgrammaticJump(false);GDBridge.sendJump(false);mc.options.attackKey.setPressed(false);mc.options.useKey.setPressed(false);editing=false;
                JsonObject play=new JsonObject();play.addProperty("cmd","build-mode");play.addProperty("active",false);GDBridge.send(play);
                mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);mc.player.noClip=true;mc.player.setNoGravity(true);mc.player.setInvisible(true);
                restoreMarkers(mc,false);message="Minecraft level: "+cells.size()+" objects"+(skipped>0?"; "+skipped+" non-cube blocks skipped":"");
            });
        });
    }
    // Magma is an authoring marker. Hide only its client copy in play; retain server data.
    public static void restoreMarkers(MinecraftClient mc,boolean show) {
        if(mc.world==null)return;
        for(Cell cell:authored)if(cell.hazard){BlockState wanted=show?cell.state:Blocks.AIR.getDefaultState();if(!mc.world.getBlockState(cell.pos).equals(wanted))mc.world.setBlockState(cell.pos,wanted,18);}
    }
    public static int objectCount(){return authored.size();}
    public static void tick(MinecraftClient mc) {
        var server=mc.getServer();if(server==null||mc.player==null||mc.world==null)return;
        if(!prepared){prepared=true;server.execute(()->{
            // Save first, then copy while holding the server thread: no region writes race the backup.
            try {server.save(false,true,true);Path source=mc.runDirectory.toPath().resolve("saves/"+worldName),backup=mc.runDirectory.toPath().resolve("saves/"+worldName+"-before-authoring");
                if(!Files.exists(backup)&&Files.exists(source))try(var paths=Files.walk(source)){for(Path path:paths.toList()){if(path.getFileName().toString().equals("session.lock"))continue;Path target=backup.resolve(source.relativize(path));if(Files.isDirectory(path))Files.createDirectories(target);else Files.copy(path,target);}}
            }catch(Exception e){message="World backup failed: "+e.getMessage();return;}
            try{Path saved=mc.runDirectory.toPath().resolve("config/gdbridge/scene-"+worldName+".json");if(Files.exists(saved)){var restored=SceneBlueprint.from(com.google.gson.JsonParser.parseString(Files.readString(saved)).getAsJsonObject());scene=restored.source();templates=new HashMap<>(restored.groups());templateMaterials=new HashMap<>(restored.blocks());}}catch(Exception e){message="Scene metadata restore failed: "+e.getMessage();}
            ServerWorld world=server.getOverworld();world.setTimeOfDay(6000);world.setWeather(0,0,false,false);
            var player=server.getPlayerManager().getPlayer(mc.player.getUuid());if(player!=null){Block[] palette={Blocks.STONE,Blocks.MAGMA_BLOCK,Blocks.STONE_BRICKS,Blocks.WHITE_CONCRETE,Blocks.GLASS,Blocks.OAK_PLANKS,Blocks.GRASS_BLOCK,Blocks.OAK_LOG,Blocks.OAK_LEAVES};for(int i=0;i<palette.length;i++)if(player.getInventory().getStack(i).isEmpty())player.getInventory().setStack(i,new ItemStack(palette[i],64));player.currentScreenHandler.sendContentUpdates();}
        });}
        int center=(int)Math.floor(mc.player.getX());List<Integer> next=new ArrayList<>();
        for(int x=Math.max(-48,center-40);x<Math.max(80,center+65)&&next.size()<4;x++)if(columns.add(x))next.add(x);
        if(!next.isEmpty())server.execute(()->{ServerWorld world=server.getOverworld();for(int x:next){
            for(int z=-16;z<=24;z++)putTerrain(world,x,z,false);
            if(Math.floorMod(x,12)==0){int height=4+Math.floorMod(x,3),base=terrainTop(-9)+1;for(int y=base;y<base+height;y++)putIfAir(world,new BlockPos(x,y,-9),Blocks.OAK_LOG);
                for(int dy=-2;dy<=1;dy++)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(Math.abs(dx)+Math.abs(dz)<5&&(dy<1||Math.abs(dx)<=1&&Math.abs(dz)<=1))putIfAir(world,new BlockPos(x+dx,base+height+dy,-9+dz),Blocks.OAK_LEAVES);}
        }});
        if(!editing)restoreMarkers(mc,false);
    }
    private static void putIfAir(ServerWorld world,BlockPos pos,Block block){if(world.getBlockState(pos).isAir())world.setBlockState(pos,block.getDefaultState(),3);}
    private static int terrainTop(int z){return !worldName.equals("GDBridge-XO")?66:z>=2?64:z<=-2?65:66;}
    private static void putTerrain(ServerWorld world,int x,int z,boolean reshape){
        int top=terrainTop(z);
        if(reshape&&z!=0)for(int y=top+1;y<=66;y++){BlockPos pos=new BlockPos(x,y,z);BlockState state=world.getBlockState(pos);if(state.isOf(Blocks.GRASS_BLOCK)||state.isOf(Blocks.DIRT)||state.isOf(Blocks.STONE))world.setBlockState(pos,Blocks.AIR.getDefaultState(),3);}
        for(int y=top-2;y<=top;y++){BlockPos pos=new BlockPos(x,y,z);BlockState state=world.getBlockState(pos);Block wanted=y==top?Blocks.GRASS_BLOCK:y==top-1?Blocks.DIRT:Blocks.STONE;if(state.isAir()||reshape&&(state.isOf(Blocks.GRASS_BLOCK)||state.isOf(Blocks.DIRT)||state.isOf(Blocks.STONE)))world.setBlockState(pos,wanted.getDefaultState(),3);}
    }
}
