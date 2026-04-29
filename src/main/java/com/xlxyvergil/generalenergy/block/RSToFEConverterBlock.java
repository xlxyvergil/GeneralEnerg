package com.xlxyvergil.generalenergy.block;

import com.xlxyvergil.generalenergy.ModRegistration;
import com.xlxyvergil.generalenergy.config.GeneralEnergyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import javax.annotation.Nullable;
import java.util.List;

public class RSToFEConverterBlock extends Block implements EntityBlock {

    public enum EnergyState implements StringRepresentable {
        OFFLINE("offline"),
        ONLINE("online");

        private final String name;

        EnergyState(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<EnergyState> ENERGY_STATE = EnumProperty.create("energy_state", EnergyState.class);

    public RSToFEConverterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(ENERGY_STATE, EnergyState.OFFLINE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ENERGY_STATE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RSToFEConverterBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        // 功能说明
        tooltip.add(Component.translatable("tooltip.generalenergy.rs_to_fe_converter.description"));
        
        int capacityPerConverter = GeneralEnergyConfig.COMMON.rsToFeCapacityPerConverter.get();
        
        tooltip.add(Component.translatable("tooltip.generalenergy.rs_to_fe_converter.network_capacity", capacityPerConverter));
        tooltip.add(Component.translatable("tooltip.generalenergy.rs_to_fe_converter.input"));
        tooltip.add(Component.translatable("tooltip.generalenergy.rs_to_fe_converter.output"));
        tooltip.add(Component.translatable("tooltip.generalenergy.rs_to_fe_converter.boost", RSToFENetworkNode.EXTRA_CAPACITY_PER_CONVERTER));
    }
    
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {
        // 掉落当前方块（保留 NBT 数据）
        if (!level.isClientSide && !player.isCreative()) {
            popResource(level, pos, new ItemStack(this));
        }
        super.playerWillDestroy(level, pos, state, player);
    }
    
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        
        // 移除断开连接时变回基础方块的逻辑
        // 转换器应该保持自身状态，即使网络断开
    }
}
