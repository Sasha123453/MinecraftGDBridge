package local.gdbridge;
import net.fabricmc.api.ModInitializer;
import net.minecraft.entity.*;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
public final class GDBridgeCommon implements ModInitializer {
    public static EntityType<GDRenderEntity> PLAYER;
    @Override public void onInitialize(){
        PLAYER=Registry.register(Registries.ENTITY_TYPE,new Identifier("gdbridge","player"),EntityType.Builder.<GDRenderEntity>create(GDRenderEntity::new,SpawnGroup.MISC).setDimensions(2,2).maxTrackingRange(128).trackingTickInterval(1).build("gdbridge:player"));
    }
}
