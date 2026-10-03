package local.gdbridge;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import net.minecraft.world.BlockView;

/** One persistent world cell can contain several precisely positioned pieces. */
public final class CompoundObstacleBlock extends BlockWithEntity {
    public CompoundObstacleBlock(Settings settings){super(settings);}
    @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new CompoundObstacleBlockEntity(pos,state);}
    @Override public BlockRenderType getRenderType(BlockState state){return BlockRenderType.INVISIBLE;}
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){var entity=world.getBlockEntity(pos);return entity instanceof CompoundObstacleBlockEntity compound?compound.outline():VoxelShapes.fullCube();}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){var entity=world.getBlockEntity(pos);return entity instanceof CompoundObstacleBlockEntity compound?compound.collision():VoxelShapes.empty();}
}
