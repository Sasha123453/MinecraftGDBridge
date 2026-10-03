package local.gdbridge;
import net.minecraft.entity.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
/** Client-only render proxy. GD remains the sole authority for player physics. */
public final class GDRenderEntity extends Entity {
    public int playerIndex;
    public GDRenderEntity(EntityType<? extends GDRenderEntity> type,World world){super(type,world);noClip=true;setNoGravity(true);}
    @Override protected void initDataTracker(){}
    @Override protected void readCustomDataFromNbt(NbtCompound tag){}
    @Override protected void writeCustomDataToNbt(NbtCompound tag){}
    @Override public boolean shouldRender(double distance){return true;}
    public void sync(GDBridge.Frame frame){
        com.google.gson.JsonObject data=playerIndex==0?frame.packet():frame.packet().has("player2")?frame.packet().getAsJsonObject("player2"):new com.google.gson.JsonObject();
        if(!data.has("x"))return;double x=data.get("x").getAsDouble()/30,y=64+data.get("y").getAsDouble()/30;
        setPosition(x,y,.5);prevX=lastRenderX=x;prevY=lastRenderY=y;prevZ=lastRenderZ=.5;
    }
}
