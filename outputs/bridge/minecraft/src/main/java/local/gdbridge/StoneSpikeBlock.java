package local.gdbridge;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.BlockView;

/** A persistent, placeable Minecraft obstacle. GD does not supply its mesh. */
public final class StoneSpikeBlock extends BlockWithEntity {
    public static final DirectionProperty FACING=DirectionProperty.of("facing",Direction.UP,Direction.DOWN,Direction.EAST,Direction.WEST);
    private static final java.util.Map<Direction,VoxelShape> SHAPES=shapes();
    public StoneSpikeBlock(Settings settings){super(settings);setDefaultState(getStateManager().getDefaultState().with(FACING,Direction.UP));}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState getPlacementState(ItemPlacementContext context){Direction side=context.getSide();return getDefaultState().with(FACING,side.getAxis()==Direction.Axis.Z?Direction.UP:side);}
    @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new StoneSpikeBlockEntity(pos,state);}
    @Override public BlockRenderType getRenderType(BlockState state){return BlockRenderType.INVISIBLE;}
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return SHAPES.get(state.get(FACING));}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return SHAPES.get(state.get(FACING));}
    private static java.util.Map<Direction,VoxelShape> shapes(){
        var result=new java.util.EnumMap<Direction,VoxelShape>(Direction.class);
        for(Direction direction:new Direction[]{Direction.UP,Direction.DOWN,Direction.EAST,Direction.WEST}){VoxelShape shape=VoxelShapes.empty();for(int i=0;i<8;i++){
            VoxelShape band=switch(direction){case DOWN->Block.createCuboidShape(i,14-2*i,0,16-i,16-2*i,16);case EAST->Block.createCuboidShape(2*i,i,0,2*i+2,16-i,16);case WEST->Block.createCuboidShape(14-2*i,i,0,16-2*i,16-i,16);default->Block.createCuboidShape(i,2*i,0,16-i,2*i+2,16);};
            shape=VoxelShapes.union(shape,band);
        }result.put(direction,shape.simplify());}return java.util.Collections.unmodifiableMap(result);
    }
    public static double gdRotation(BlockState state){return switch(state.get(FACING)){case EAST->90;case DOWN->180;case WEST->270;default->0;};}
    public static Direction facing(double rotation){int quarter=Math.floorMod((int)Math.round(rotation/90),4);return switch(quarter){case 1->Direction.EAST;case 2->Direction.DOWN;case 3->Direction.WEST;default->Direction.UP;};}
}
