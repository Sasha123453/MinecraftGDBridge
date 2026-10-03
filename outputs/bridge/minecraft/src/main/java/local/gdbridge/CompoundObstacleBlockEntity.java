package local.gdbridge;
import com.google.gson.*;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;

/** Piece parameters are Minecraft save data, independent of live GD telemetry. */
public final class CompoundObstacleBlockEntity extends BlockEntity {
    private List<JsonObject> pieces=List.of();
    private int originX,originY;
    private VoxelShape collision=VoxelShapes.empty(),outline=VoxelShapes.fullCube();
    public CompoundObstacleBlockEntity(BlockPos pos,BlockState state){super(GDBridgeCommon.COMPOUND_OBSTACLE_ENTITY,pos,state);originX=pos.getX();originY=pos.getY();}
    public List<JsonObject> localPieces(){return pieces;}
    public void setPieces(List<JsonObject> originals){
        if(originals.size()>1024)throw new IllegalArgumentException("Compound cell exceeds 1024 pieces");List<JsonObject> result=new ArrayList<>();
        for(JsonObject original:originals){JsonObject piece=original.deepCopy();double x=CompoundGeometry.number(piece,"x",Double.NaN),y=CompoundGeometry.number(piece,"y",Double.NaN);if(!Double.isFinite(x)||!Double.isFinite(y))throw new IllegalArgumentException("Non-finite compound coordinates");piece.addProperty("localX",x/30-pos.getX());piece.addProperty("localY",64+y/30-pos.getY());result.add(piece);}
        originX=pos.getX();originY=pos.getY();pieces=List.copyOf(result);rebuildShapes();markDirty();if(world!=null)world.updateListeners(pos,getCachedState(),getCachedState(),3);
    }
    public List<JsonObject> exportPieces(){List<JsonObject> result=new ArrayList<>();double dx=(pos.getX()-originX)*30.0,dy=(pos.getY()-originY)*30.0;for(JsonObject stored:pieces){JsonObject copy=stored.deepCopy();copy.addProperty("x",CompoundGeometry.number(copy,"x",0)+dx);copy.addProperty("y",CompoundGeometry.number(copy,"y",0)+dy);WorldEditor.shiftGeometryCoordinates(copy,dx,dy);copy.remove("localX");copy.remove("localY");result.add(copy);}return result;}
    public VoxelShape collision(){return collision;}
    public VoxelShape outline(){return outline;}
    private void rebuildShapes(){collision=VoxelShapes.empty();VoxelShape visual=VoxelShapes.empty();for(JsonObject piece:pieces){double x=CompoundGeometry.number(piece,"localX",.5),y=CompoundGeometry.number(piece,"localY",.5);double sourceX=CompoundGeometry.number(piece,"x",0),sourceY=CompoundGeometry.number(piece,"y",0);double cx=x+(CompoundGeometry.number(piece,"hitboxX",sourceX)-sourceX)/30,cy=y+(CompoundGeometry.number(piece,"hitboxY",sourceY)-sourceY)/30;double w=CompoundGeometry.number(piece,"hitboxW",CompoundGeometry.number(piece,"w",30))/30,h=CompoundGeometry.number(piece,"hitboxH",CompoundGeometry.number(piece,"h",30))/30;if(!Double.isFinite(w)||!Double.isFinite(h)||w<=0||h<=0||w>128||h>128)continue;VoxelShape box=VoxelShapes.cuboid(cx-w/2,cy-h/2,0,cx+w/2,cy+h/2,1);visual=VoxelShapes.union(visual,box);if(CompoundGeometry.physical(piece))collision=VoxelShapes.union(collision,box);}outline=visual.isEmpty()?VoxelShapes.fullCube():visual;}
    @Override protected void writeNbt(NbtCompound nbt){super.writeNbt(nbt);JsonArray data=new JsonArray();pieces.forEach(data::add);nbt.putString("gdPieces",data.toString());nbt.putInt("originX",originX);nbt.putInt("originY",originY);}
    @Override public void readNbt(NbtCompound nbt){super.readNbt(nbt);originX=nbt.contains("originX")?nbt.getInt("originX"):pos.getX();originY=nbt.contains("originY")?nbt.getInt("originY"):pos.getY();String json=nbt.getString("gdPieces");if(json.isEmpty()){pieces=List.of();rebuildShapes();return;}if(json.length()>2*1024*1024)throw new IllegalArgumentException("Compound NBT exceeds 2MiB");JsonArray data=JsonParser.parseString(json).getAsJsonArray();if(data.size()>1024)throw new IllegalArgumentException("Compound cell exceeds 1024 pieces");List<JsonObject> restored=new ArrayList<>();for(JsonElement entry:data)restored.add(entry.getAsJsonObject());pieces=List.copyOf(restored);rebuildShapes();}
    @Override public NbtCompound toInitialChunkDataNbt(){return createNbt();}
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
}
