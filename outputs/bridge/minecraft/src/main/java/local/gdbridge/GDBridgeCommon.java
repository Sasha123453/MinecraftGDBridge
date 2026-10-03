package local.gdbridge;
import net.fabricmc.api.ModInitializer;
import net.minecraft.entity.*;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.*;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
public final class GDBridgeCommon implements ModInitializer {
    public static EntityType<GDRenderEntity> PLAYER;
    public static StoneSpikeBlock STONE_SPIKE;
    public static BlockEntityType<StoneSpikeBlockEntity> STONE_SPIKE_ENTITY;
    public static CompoundObstacleBlock COMPOUND_OBSTACLE;
    public static BlockEntityType<CompoundObstacleBlockEntity> COMPOUND_OBSTACLE_ENTITY;
    @Override public void onInitialize(){
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(WorldWorkQueue::tick);
        PLAYER=Registry.register(Registries.ENTITY_TYPE,new Identifier("gdbridge","player"),EntityType.Builder.<GDRenderEntity>create(GDRenderEntity::new,SpawnGroup.MISC).setDimensions(2,2).maxTrackingRange(128).trackingTickInterval(1).build("gdbridge:player"));
        STONE_SPIKE=Registry.register(Registries.BLOCK,new Identifier("gdbridge","stone_spike"),new StoneSpikeBlock(AbstractBlock.Settings.copy(Blocks.STONE).nonOpaque()));
        Registry.register(Registries.ITEM,new Identifier("gdbridge","stone_spike"),new BlockItem(STONE_SPIKE,new Item.Settings()));
        STONE_SPIKE_ENTITY=Registry.register(Registries.BLOCK_ENTITY_TYPE,new Identifier("gdbridge","stone_spike"),FabricBlockEntityTypeBuilder.create(StoneSpikeBlockEntity::new,STONE_SPIKE).build());
        COMPOUND_OBSTACLE=Registry.register(Registries.BLOCK,new Identifier("gdbridge","compound_obstacle"),new CompoundObstacleBlock(AbstractBlock.Settings.copy(Blocks.STONE).nonOpaque().dynamicBounds()));
        Registry.register(Registries.ITEM,new Identifier("gdbridge","compound_obstacle"),new BlockItem(COMPOUND_OBSTACLE,new Item.Settings()));
        COMPOUND_OBSTACLE_ENTITY=Registry.register(Registries.BLOCK_ENTITY_TYPE,new Identifier("gdbridge","compound_obstacle"),FabricBlockEntityTypeBuilder.create(CompoundObstacleBlockEntity::new,COMPOUND_OBSTACLE).build());
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries->entries.add(STONE_SPIKE));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries->entries.add(COMPOUND_OBSTACLE));
    }
}
