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
    private static volatile boolean worldGeometryAuthoritative;
    private static boolean worldGeometryShown;
    private static volatile long requiredBuildRevision;
    private static volatile GameplayRegion region=GameplayRegion.DEFAULT;
    public static GameplayRegion region(){return region;}
    public static boolean nativeGeometry(){return scene!=null;}
    public static boolean worldGeometryAuthoritative(){return worldGeometryAuthoritative;}
    public static boolean worldGeometryActive(GDBridge.Frame frame){return worldGeometryAuthoritative&&frame!=null&&frame.packet().has("authority")&&frame.packet().get("authority").getAsString().equals("minecraft-world")&&frame.packet().has("buildRevision")&&frame.packet().get("buildRevision").getAsLong()>=requiredBuildRevision;}
    public static boolean ownsGameplayPoint(double x,double y){return region.containsGD(x,y);}
    private record SceneryBox(BlockPos from,BlockPos to,BlockState state){}
    public static void applyScenery(MinecraftClient mc,String filename) throws java.io.IOException {
        if(!Set.of("GDBridge-XO","GDBridge-GeometryTests","GDBridge-Reference").contains(worldName)||mc.getServer()==null)throw new IllegalStateException("Scenery only in isolated bridge worlds");
        JsonObject plan=SceneBlueprint.readSource(filename);if(plan.has("targetWorld")&&!worldName.equals(plan.get("targetWorld").getAsString()))throw new IllegalArgumentException("Scenery targets a different world");List<SceneryBox> boxes=new ArrayList<>();
        for(JsonElement entry:plan.getAsJsonArray("boxes")){JsonObject box=entry.getAsJsonObject();BlockPos from=sceneryPos(box.getAsJsonArray("from")),to=sceneryPos(box.getAsJsonArray("to"));if(Math.min(from.getZ(),to.getZ())<=0&&Math.max(from.getZ(),to.getZ())>=0)throw new IllegalArgumentException("Scenery cannot touch gameplay z=0");boxes.add(new SceneryBox(from,to,sceneryState(box.get("block").getAsString())));}
        if(plan.has("terrainFloor"))for(JsonElement entry:plan.getAsJsonArray("terrainFloor")){JsonObject box=entry.getAsJsonObject();BlockPos from=sceneryPos(box.getAsJsonArray("from"),true),to=sceneryPos(box.getAsJsonArray("to"),true);if(Math.max(from.getY(),to.getY())>66)throw new IllegalArgumentException("Terrain floor must remain below authored gameplay markers");Block material=sceneryMaterial(box.get("block").getAsString());if(material!=Blocks.STONE&&material!=Blocks.DEEPSLATE&&material!=Blocks.COBBLESTONE&&material!=Blocks.BEDROCK&&material!=Blocks.DIRT&&material!=Blocks.GRASS_BLOCK)throw new IllegalArgumentException("Terrain floor requires a full solid material");boxes.add(new SceneryBox(from,to,material.getDefaultState()));}
        if(plan.has("plants"))for(JsonElement entry:plan.getAsJsonArray("plants")){JsonObject plant=entry.getAsJsonObject();BlockPos pos=sceneryPos(plant.getAsJsonArray("at"));boxes.add(new SceneryBox(pos,pos,sceneryState(plant.get("block").getAsString())));}
        long total=0;for(SceneryBox box:boxes)total+=(Math.abs(box.from.getX()-box.to.getX())+1L)*(Math.abs(box.from.getY()-box.to.getY())+1)*(Math.abs(box.from.getZ()-box.to.getZ())+1);if(total>16384)throw new IllegalArgumentException("Scenery batch exceeds 16384 cells");
        if(WorldWorkQueue.busy())throw new IllegalStateException("World operation in progress");
        mc.getServer().execute(()->{ServerWorld world=mc.getServer().getOverworld();List<Runnable> writes=new ArrayList<>();
            if(plan.has("cleanupOwnedBackWalls"))for(int x=8;x<=464;x+=24)for(int dx=0;dx<5;dx++)for(int y=66;y<=68;y++){BlockPos pos=new BlockPos(x+dx,y,-6);writes.add(()->{BlockState old=world.getBlockState(pos);if(old.isOf(Blocks.STONE_BRICKS)||old.isOf(Blocks.MOSSY_STONE_BRICKS))world.setBlockState(pos,Blocks.AIR.getDefaultState(),3);});}
            int[] changed={0};for(SceneryBox box:boxes)for(BlockPos iterated:BlockPos.iterate(box.from,box.to)){BlockPos pos=iterated.toImmutable();writes.add(()->{if(!world.getBlockState(pos).equals(box.state)){world.setBlockState(pos,box.state,3);changed[0]++;}});}
            WorldWorkQueue.start("Building Minecraft scenery",writes.size(),128,index->writes.get(index).run(),()->{if(plan.has("timeOfDay"))world.setTimeOfDay(plan.get("timeOfDay").getAsLong());message="Scenery plan applied: "+changed[0]+" cells; gameplay unchanged";});
        });
    }
    private static BlockPos sceneryPos(JsonArray a){return sceneryPos(a,false);}
    private static BlockPos sceneryPos(JsonArray a,boolean terrainFloor){BlockPos pos=new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());int minX=Math.max(-16,region.minX()-16),maxX=Math.min(4095,region.maxX()+16),maxY=Math.min(255,Math.max(160,region.maxY()+8));if(!terrainFloor&&worldName.equals("GDBridge-Reference"))maxX=Math.min(4095,Math.max(110,region.maxX()+48));if(pos.getX()<minX||pos.getX()>maxX||pos.getY()<50||pos.getY()>maxY||pos.getZ()<-16||pos.getZ()>24||(!terrainFloor&&pos.getZ()==0))throw new IllegalArgumentException("Scenery outside padded "+region.label()+" or gameplay z=0");return pos;}
    private static BlockState sceneryState(String name){BlockState state=sceneryMaterial(name).getDefaultState();return state.getBlock() instanceof LeavesBlock?state.with(LeavesBlock.PERSISTENT,true):state;}
    private static Block sceneryMaterial(String name){return switch(name){case "minecraft:dirt"->Blocks.DIRT;case "minecraft:grass_block"->Blocks.GRASS_BLOCK;case "minecraft:stone"->Blocks.STONE;case "minecraft:stone_bricks"->Blocks.STONE_BRICKS;case "minecraft:mossy_stone_bricks"->Blocks.MOSSY_STONE_BRICKS;case "minecraft:grass"->Blocks.GRASS;case "minecraft:fern"->Blocks.FERN;case "minecraft:dandelion"->Blocks.DANDELION;case "minecraft:poppy"->Blocks.POPPY;case "minecraft:mossy_cobblestone"->Blocks.MOSSY_COBBLESTONE;case "minecraft:moss_carpet"->Blocks.MOSS_CARPET;case "minecraft:air"->Blocks.AIR;case "minecraft:oak_log"->Blocks.OAK_LOG;case "minecraft:oak_planks"->Blocks.OAK_PLANKS;case "minecraft:oak_leaves"->Blocks.OAK_LEAVES;case "minecraft:bookshelf"->Blocks.BOOKSHELF;case "minecraft:lantern"->Blocks.LANTERN;case "minecraft:torch"->Blocks.TORCH;case "minecraft:cobblestone"->Blocks.COBBLESTONE;case "minecraft:deepslate"->Blocks.DEEPSLATE;case "minecraft:bedrock"->Blocks.BEDROCK;case "minecraft:diamond_ore"->Blocks.DIAMOND_ORE;case "minecraft:iron_ore"->Blocks.IRON_ORE;case "minecraft:coal_ore"->Blocks.COAL_ORE;case "minecraft:redstone_ore"->Blocks.REDSTONE_ORE;case "minecraft:granite"->Blocks.GRANITE;case "minecraft:diorite"->Blocks.DIORITE;case "minecraft:obsidian"->Blocks.OBSIDIAN;case "minecraft:nether_bricks"->Blocks.NETHER_BRICKS;case "minecraft:netherrack"->Blocks.NETHERRACK;case "minecraft:lava"->Blocks.LAVA;case "minecraft:birch_log"->Blocks.BIRCH_LOG;case "minecraft:birch_leaves"->Blocks.BIRCH_LEAVES;case "minecraft:water"->Blocks.WATER;case "minecraft:glass"->Blocks.GLASS;default->throw new IllegalArgumentException("Unsupported scenery block "+name);};}
    public static void resetWorld(String name){WorldWorkQueue.cancel();region=GameplayRegion.DEFAULT;worldName=name;prepared=false;columns.clear();authored=List.of();scene=null;templates=new HashMap<>();templateMaterials=new HashMap<>();worldGeometryAuthoritative=false;worldGeometryShown=false;requiredBuildRevision=0;}
    public static void importBlueprint(MinecraftClient mc,String filename) throws java.io.IOException {
        if(!Set.of("GDBridge-XO","GDBridge-GeometryTests","GDBridge-Reference").contains(worldName))throw new IllegalStateException("Import only into an isolated geometry bridge world");
        if(mc.getServer()==null)throw new IllegalStateException("No local server");
        if(WorldWorkQueue.busy())throw new IllegalStateException("World operation in progress");
        var blueprint=SceneBlueprint.read(filename);if(!editing)toggle();
        mc.getServer().execute(()->{
            ServerWorld world=mc.getServer().getOverworld();List<Runnable> writes=new ArrayList<>();
            GameplayRegion importedRegion=GameplayRegion.from(blueprint.source());
            for(var old:templateMaterials.entrySet())if(!blueprint.blocks().containsKey(old.getKey())){BlockPos pos=old.getKey();Block material=old.getValue();boolean previousWorld=worldGeometryAuthoritative;writes.add(()->{BlockState actual=world.getBlockState(pos);boolean ownedSpike=previousWorld&&material==Blocks.MAGMA_BLOCK&&actual.isOf(GDBridgeCommon.STONE_SPIKE);if(actual.isOf(material)||ownedSpike)world.setBlockState(pos,Blocks.AIR.getDefaultState(),3);});}
            // A new native level import explicitly seeds its flat starting track
            // once. F6, restart and ordinary streaming never repair edited holes.
            for(int x=importedRegion.minX();x<=importedRegion.maxX();x++)for(int y=64;y<=66;y++){BlockPos pos=new BlockPos(x,y,0);Block block=y==66?Blocks.GRASS_BLOCK:y==65?Blocks.DIRT:Blocks.STONE;writes.add(()->putIfAir(world,pos,block));}
            for(var entry:blueprint.blocks().entrySet()){BlockPos pos=entry.getKey();BlockState state=entry.getValue().getDefaultState();List<JsonObject> pieces=blueprint.groups().get(pos);writes.add(()->{world.setBlockState(pos,state,3);if(world.getBlockEntity(pos) instanceof CompoundObstacleBlockEntity compound)compound.setPieces(pieces);else if(state.isOf(GDBridgeCommon.COMPOUND_OBSTACLE))throw new IllegalStateException("Missing compound world entity at "+pos);});}
            WorldWorkQueue.start("Importing Minecraft cells",writes.size(),128,index->writes.get(index).run(),()->{
                List<Cell> importedCells=new ArrayList<>();for(var entry:blueprint.blocks().entrySet())importedCells.add(new Cell(entry.getKey(),entry.getValue().getDefaultState(),false));
                scene=blueprint.source();region=GameplayRegion.from(scene);templates=new HashMap<>(blueprint.groups());templateMaterials=new HashMap<>(blueprint.blocks());worldGeometryAuthoritative=false;worldGeometryShown=false;
                mc.execute(()->{authored=List.copyOf(importedCells);restoreMarkers(mc,editing);});
                try{saveScene(mc);}catch(Exception e){message="Scene metadata save failed: "+e.getMessage();return;}
                long pieceCount=blueprint.groups().values().stream().mapToLong(List::size).sum(),unknown=blueprint.groups().values().stream().flatMap(List::stream).filter(piece->!CompoundGeometry.supported(piece)).count();message="Imported "+pieceCount+" exact pieces in "+templates.size()+" world cells; "+unknown+" unsupported visuals; "+blueprint.clipped()+" outside safe bounds";
            });
        });
    }
    private static void saveScene(MinecraftClient mc) throws java.io.IOException {
        if(scene==null)return;Path output=mc.runDirectory.toPath().resolve("config/gdbridge/scene-"+worldName+".json");Files.createDirectories(output.getParent());Files.writeString(output,scene.toString());
    }
    public static void shiftGeometryCoordinates(JsonObject piece,double dx,double dy){for(String key:List.of("hitboxX","bodyX"))if(piece.has(key))piece.addProperty(key,piece.get(key).getAsDouble()+dx);for(String key:List.of("hitboxY","bodyY"))if(piece.has(key))piece.addProperty(key,piece.get(key).getAsDouble()+dy);for(String key:List.of("visualQuad","triangleVertices"))if(piece.has(key))for(JsonElement entry:piece.getAsJsonArray(key)){JsonArray vertex=entry.getAsJsonArray();vertex.set(0,new JsonPrimitive(vertex.get(0).getAsDouble()+dx));vertex.set(1,new JsonPrimitive(vertex.get(1).getAsDouble()+dy));}}
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
        if(WorldWorkQueue.busy())throw new IllegalStateException("World operation in progress");
        if(mc.getServer()==null||!region.contains(from)||!region.contains(to))throw new IllegalArgumentException("Move within "+region.label());
        mc.getServer().execute(()->{var world=mc.getServer().getOverworld();if(!world.getBlockState(to).isAir()){message="Move destination occupied";return;}
            BlockState state=world.getBlockState(from);List<JsonObject> worldPieces=world.getBlockEntity(from) instanceof CompoundObstacleBlockEntity compound?compound.exportPieces():null;
            world.setBlockState(from,Blocks.AIR.getDefaultState(),3);world.setBlockState(to,state,3);
            if(worldPieces!=null&&world.getBlockEntity(to) instanceof CompoundObstacleBlockEntity target){for(JsonObject piece:worldPieces){piece.addProperty("x",piece.get("x").getAsDouble()+(to.getX()-from.getX())*30);piece.addProperty("y",piece.get("y").getAsDouble()+(to.getY()-from.getY())*30);shiftGeometryCoordinates(piece,(to.getX()-from.getX())*30,(to.getY()-from.getY())*30);}target.setPieces(worldPieces);}
            var group=templates.remove(from);if(group!=null){List<JsonObject> shifted=new ArrayList<>();for(JsonObject object:group){JsonObject copy=object.deepCopy();copy.addProperty("x",copy.get("x").getAsDouble()+(to.getX()-from.getX())*30);copy.addProperty("y",copy.get("y").getAsDouble()+(to.getY()-from.getY())*30);shifted.add(copy);}templates.put(to,shifted);templateMaterials.put(to,templateMaterials.remove(from));
                if(scene!=null&&scene.has("objects")){JsonArray saved=scene.getAsJsonArray("objects");for(int i=0;i<saved.size();i++)for(int j=0;j<group.size();j++)if(saved.get(i).equals(group.get(j))){saved.set(i,shifted.get(j).deepCopy());break;}try{Files.writeString(mc.runDirectory.toPath().resolve("config/gdbridge/scene-"+worldName+".json"),scene.toString());}catch(Exception e){message="Marker moved; metadata save failed: "+e.getMessage();return;}}
            }
            message="Minecraft marker moved; native offsets preserved";
        });
    }
    public static void toggle() {
        if(WorldWorkQueue.busy()){message="Wait for "+WorldWorkQueue.operation;return;}
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
        if(WorldWorkQueue.busy()){message="Wait for "+WorldWorkQueue.operation;return;}
        if(!editing)toggle();
        GameplayRegion capturedRegion=region;message="Reading Minecraft blocks...";
        server.execute(()->{
            ServerWorld world=server.getOverworld();JsonArray objects=new JsonArray(),actualCells=new JsonArray();JsonObject counts=new JsonObject();List<Cell> cells=new ArrayList<>();int[] unsupported={0};
            int height=capturedRegion.maxY()-capturedRegion.minY()+1;
            WorldWorkQueue.start("Reading world collision",Math.toIntExact(capturedRegion.cells()),2048,index->{
                BlockPos pos=new BlockPos(capturedRegion.minX()+index/height,capturedRegion.minY()+index%height,0);BlockState state=world.getBlockState(pos);if(state.isAir())return;
                if(state.isOf(Blocks.MAGMA_BLOCK)||state.isOf(Blocks.IRON_BARS)){
                    var originals=templates.get(pos);JsonObject hazard=originals==null?null:originals.stream().filter(o->o.has("type")&&o.get("type").getAsString().equals("hazard")).findFirst().orElse(null);
                    double rotation=hazard!=null&&hazard.has("rotation")?hazard.get("rotation").getAsDouble():0;
                    state=GDBridgeCommon.STONE_SPIKE.getDefaultState().with(StoneSpikeBlock.FACING,StoneSpikeBlock.facing(rotation));world.setBlockState(pos,state,3);if(templates.containsKey(pos))templateMaterials.put(pos,GDBridgeCommon.STONE_SPIKE);
                }
                int id=SceneBlueprint.id(state);boolean marker=SceneBlueprint.isSpecialMarker(state);
                if(!state.isOf(GDBridgeCommon.COMPOUND_OBSTACLE)&&id==1&&!state.isFullCube(world,pos)){unsupported[0]++;return;}
                var original=templates.get(pos);List<JsonObject> compiledPieces;
                if(state.isOf(GDBridgeCommon.COMPOUND_OBSTACLE)){if(!(world.getBlockEntity(pos) instanceof CompoundObstacleBlockEntity compound))throw new IllegalStateException("Missing compound world entity at "+pos);compiledPieces=compound.exportPieces();}
                else compiledPieces=List.of(SceneBlueprint.worldObject(pos,state,original!=null&&state.isOf(templateMaterials.get(pos))?original:null));
                JsonArray perCell=new JsonArray();for(JsonObject compiled:compiledPieces){objects.add(compiled);perCell.add(compiled.deepCopy());String type=CompoundGeometry.special(compiled)?"special":CompoundGeometry.text(compiled,"type",state.isOf(GDBridgeCommon.STONE_SPIKE)?"hazard":"solid");counts.addProperty(type,counts.has(type)?counts.get(type).getAsInt()+1:1);if(state.isOf(GDBridgeCommon.COMPOUND_OBSTACLE)&&!CompoundGeometry.supported(compiled))unsupported[0]++;}
                cells.add(new Cell(pos,state,marker));JsonObject actual=new JsonObject();JsonArray position=new JsonArray();position.add(pos.getX());position.add(pos.getY());position.add(0);actual.add("pos",position);actual.addProperty("block",net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString());actual.addProperty("state",state.toString());actual.addProperty("lightEmission",state.getLuminance());actual.addProperty("blockLight",world.getLightLevel(net.minecraft.world.LightType.BLOCK,pos));actual.add("compiledPieces",perCell);if(perCell.size()==1)actual.add("compiled",perCell.get(0).deepCopy());actual.addProperty("hiddenMarker",marker);actualCells.add(actual);
                if(state.getLuminance()>0){JsonArray samples=new JsonArray();for(int dz=-1;dz<=1;dz+=2){BlockPos nearby=pos.add(0,0,dz);JsonObject sample=new JsonObject();sample.addProperty("zOffset",dz);sample.addProperty("blockLight",world.getLightLevel(net.minecraft.world.LightType.BLOCK,nearby));sample.addProperty("block",net.minecraft.registry.Registries.BLOCK.getId(world.getBlockState(nearby).getBlock()).toString());samples.add(sample);}actual.add("adjacentLightSamples",samples);}
            },()->{
                JsonObject manifest=new JsonObject();manifest.addProperty("v",3);manifest.addProperty("world",worldName);manifest.addProperty("geometryAuthority","minecraft-world");manifest.addProperty("scope",capturedRegion.label());manifest.add("authoringRegion",capturedRegion.json());manifest.addProperty("exportedAtEpochMs",System.currentTimeMillis());manifest.addProperty("cellCount",cells.size());manifest.addProperty("pieceCount",objects.size());manifest.addProperty("unsupportedCells",unsupported[0]);manifest.addProperty("unsupportedCellsOrPieces",unsupported[0]);manifest.add("objects",objects.deepCopy());manifest.add("cells",actualCells);manifest.add("counts",counts);
                try{Path output=Path.of("C:/Users/shelk/Documents/Codex/2026-10-02/minecraft-java-geometry-dash-nasgubb-xo/outputs/bridge/runtime/world-authority-export.local.json");Files.createDirectories(output.getParent());Files.writeString(output,manifest.toString());}catch(Exception e){message="Export audit save failed: "+e.getMessage();return;}
                mc.execute(()->finishExport(mc,capturedRegion,objects,cells,unsupported[0]));
            });
        });
    }
    private static void finishExport(MinecraftClient mc,GameplayRegion capturedRegion,JsonArray objects,List<Cell> cells,int skipped){
        JsonObject command=new JsonObject();command.addProperty("cmd","load-minecraft-level");command.addProperty("name","Minecraft Build");command.add("objects",objects);command.addProperty("worldGeometryAuthoritative",true);command.addProperty("authority","minecraft-world");
        command.addProperty("rangeMinX",capturedRegion.minX()*30.0);command.addProperty("rangeMaxX",(capturedRegion.maxX()+1)*30.0);command.addProperty("rangeMinY",(capturedRegion.minY()-64)*30.0);command.addProperty("rangeMaxY",(capturedRegion.maxY()+1-64)*30.0);
        if(scene!=null){if(scene.has("rawLevelString"))command.add("baseRawLevelString",scene.get("rawLevelString"));if(scene.has("levelString"))command.add("baseLevelString",scene.get("levelString"));if(scene.has("songId"))command.add("songId",scene.get("songId"));if(scene.has("audioTrack"))command.add("audioTrack",scene.get("audioTrack"));command.addProperty("name",scene.has("name")?scene.get("name").getAsString()+" / Minecraft":"Minecraft Blueprint");}
        var oldFrame=GDBridge.FRAME.get();long nextRevision=oldFrame!=null&&oldFrame.packet().has("buildRevision")?oldFrame.packet().get("buildRevision").getAsLong()+1:1;
        if(!GDBridge.send(command)){message="GD disconnected. Keep building; press F6 after reconnect.";return;}
        requiredBuildRevision=nextRevision;worldGeometryAuthoritative=true;worldGeometryShown=false;
        if(scene!=null){scene.addProperty("worldGeometryAuthoritative",true);scene.add("authoringRegion",capturedRegion.json());try{saveScene(mc);}catch(Exception e){message="Metadata save failed: "+e.getMessage();}}
        authored=List.copyOf(cells);GDBridge.setProgrammaticJump(false);GDBridge.sendJump(false);mc.options.attackKey.setPressed(false);mc.options.useKey.setPressed(false);editing=false;
        JsonObject play=new JsonObject();play.addProperty("cmd","build-mode");play.addProperty("active",false);GDBridge.send(play);
        mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);mc.player.noClip=true;mc.player.setNoGravity(true);mc.player.setInvisible(true);
        restoreMarkers(mc,false);message="Minecraft world collision: "+objects.size()+" pieces in "+cells.size()+" cells; "+capturedRegion.label()+(skipped>0?"; "+skipped+" unsupported cells or visual shapes":"");
    }
    // Client-only authoring visibility never changes integrated-server cells.
    public static void restoreMarkers(MinecraftClient mc,boolean show) {
        if(mc.world==null)return;
        boolean waitingForCompiledPhysics=worldGeometryAuthoritative&&!worldGeometryActive(GDBridge.FRAME.get());
        for(Cell cell:authored)if(!cell.state.isOf(GDBridgeCommon.COMPOUND_OBSTACLE)){BlockState wanted=show||(!cell.hazard&&!waitingForCompiledPhysics)?cell.state:Blocks.AIR.getDefaultState();if(!mc.world.getBlockState(cell.pos).equals(wanted))mc.world.setBlockState(cell.pos,wanted,18);}
    }
    public static int objectCount(){return authored.size();}
    public static void tick(MinecraftClient mc) {
        var server=mc.getServer();if(server==null||mc.player==null||mc.world==null)return;
        if(!prepared){prepared=true;server.execute(()->{
            // Save first, then copy while holding the server thread: no region writes race the backup.
            try {server.save(false,true,true);Path source=mc.runDirectory.toPath().resolve("saves/"+worldName),backup=mc.runDirectory.toPath().resolve("saves/"+worldName+"-before-authoring");
                if(!Files.exists(backup)&&Files.exists(source))try(var paths=Files.walk(source)){for(Path path:paths.toList()){if(path.getFileName().toString().equals("session.lock"))continue;Path target=backup.resolve(source.relativize(path));if(Files.isDirectory(path))Files.createDirectories(target);else Files.copy(path,target);}}
            }catch(Exception e){message="World backup failed: "+e.getMessage();return;}
            try {
                Path saved=mc.runDirectory.toPath().resolve("config/gdbridge/scene-"+worldName+".json");
                if(Files.exists(saved)){
                    var restored=SceneBlueprint.from(JsonParser.parseString(Files.readString(saved)).getAsJsonObject());scene=restored.source();region=GameplayRegion.from(scene);templates=new HashMap<>(restored.groups());templateMaterials=new HashMap<>(restored.blocks());worldGeometryAuthoritative=scene.has("worldGeometryAuthoritative")&&scene.get("worldGeometryAuthoritative").getAsBoolean();
                    List<Cell> restoredCells=new ArrayList<>();List<BlockPos> positions=new ArrayList<>(templates.keySet());ServerWorld restoredWorld=server.getOverworld();
                    WorldWorkQueue.start("Restoring world metadata",positions.size(),128,index->{BlockPos pos=positions.get(index);BlockState state=restoredWorld.getBlockState(pos);if(worldGeometryAuthoritative&&templateMaterials.get(pos)==Blocks.MAGMA_BLOCK&&state.isOf(GDBridgeCommon.STONE_SPIKE))templateMaterials.put(pos,GDBridgeCommon.STONE_SPIKE);if(!state.isAir())restoredCells.add(new Cell(pos,state,worldGeometryAuthoritative?SceneBlueprint.isSpecialMarker(state):true));},()->authored=List.copyOf(restoredCells));
                }
            }catch(Exception e){message="Scene metadata restore failed: "+e.getMessage();}
            ServerWorld world=server.getOverworld();world.setTimeOfDay(worldName.equals("GDBridge-Reference")?3000:6000);world.setWeather(0,0,false,false);
            var player=server.getPlayerManager().getPlayer(mc.player.getUuid());if(player!=null){Block[] palette={Blocks.STONE,GDBridgeCommon.STONE_SPIKE,Blocks.STONE_BRICKS,Blocks.WHITE_CONCRETE,Blocks.GLASS,Blocks.OAK_PLANKS,Blocks.GRASS_BLOCK,Blocks.OAK_LOG,Blocks.OAK_LEAVES};for(int i=0;i<palette.length;i++)if(player.getInventory().getStack(i).isEmpty())player.getInventory().setStack(i,new ItemStack(palette[i],64));player.currentScreenHandler.sendContentUpdates();}
        });}
        int center=(int)Math.floor(mc.player.getX());List<Integer> next=new ArrayList<>();
        for(int x=Math.max(-48,center-40);x<Math.max(80,center+65)&&next.size()<4;x++)if(columns.add(x))next.add(x);
        if(!next.isEmpty())server.execute(()->{ServerWorld world=server.getOverworld();for(int x:next){
            for(int z=-16;z<=24;z++)putTerrain(world,x,z,false);
            if(!worldName.equals("GDBridge-Reference")&&Math.floorMod(x,12)==0){int height=4+Math.floorMod(Math.floorDiv(x,12),3),base=terrainTop(-9)+1;for(int y=base;y<base+height;y++)putIfAir(world,new BlockPos(x,y,-9),Blocks.OAK_LOG);
                for(int dy=-2;dy<=1;dy++)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(Math.abs(dx)+Math.abs(dz)<5&&(dy<1||Math.abs(dx)<=1&&Math.abs(dz)<=1))putIfAir(world,new BlockPos(x+dx,base+height+dy,-9+dz),Blocks.OAK_LEAVES);}
        }});
        if(!editing){boolean activeWorld=worldGeometryActive(GDBridge.FRAME.get());if(activeWorld&&!worldGeometryShown){for(Cell cell:authored)if(!cell.hazard)mc.world.setBlockState(cell.pos,cell.state,18);}worldGeometryShown=activeWorld;restoreMarkers(mc,false);}
    }
    private static void putIfAir(ServerWorld world,BlockPos pos,Block block){if(world.getBlockState(pos).isAir())world.setBlockState(pos,block.getDefaultState(),3);}
    private static int terrainTop(int z){return !worldName.equals("GDBridge-XO")?66:z>=2?64:z<=-2?65:66;}
    private static void putTerrain(ServerWorld world,int x,int z,boolean reshape){
        if(worldName.equals("GDBridge-Reference"))return; // Authored terraces, including AIR, survive streaming and restart.
        if(!reshape&&z==0&&x>=region.minX()&&x<=region.maxX()&&(scene!=null||worldGeometryAuthoritative||editing))return;
        int top=terrainTop(z);
        if(reshape&&z!=0)for(int y=top+1;y<=66;y++){BlockPos pos=new BlockPos(x,y,z);BlockState state=world.getBlockState(pos);if(state.isOf(Blocks.GRASS_BLOCK)||state.isOf(Blocks.DIRT)||state.isOf(Blocks.STONE))world.setBlockState(pos,Blocks.AIR.getDefaultState(),3);}
        for(int y=top-2;y<=top;y++){BlockPos pos=new BlockPos(x,y,z);BlockState state=world.getBlockState(pos);Block wanted=y==top?Blocks.GRASS_BLOCK:y==top-1?Blocks.DIRT:Blocks.STONE;if(state.isAir()||reshape&&(state.isOf(Blocks.GRASS_BLOCK)||state.isOf(Blocks.DIRT)||state.isOf(Blocks.STONE)))world.setBlockState(pos,wanted.getDefaultState(),3);}
    }
}
