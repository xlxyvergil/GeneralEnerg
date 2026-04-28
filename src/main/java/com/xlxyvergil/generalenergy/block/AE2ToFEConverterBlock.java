package com.xlxyvergil.generalenergy.block;

import com.xlxyvergil.generalenergy.config.GeneralEnergyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import javax.annotation.Nullable;
import java.util.List;

public class AE2ToFEConverterBlock extends Block implements EntityBlock {

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

    public AE2ToFEConverterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(ENERGY_STATE, EnergyState.OFFLINE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ENERGY_STATE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AE2ToFEConverterBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        int maxFEOutput = GeneralEnergyConfig.COMMON.aeToFeMaxFEOutputPerConverter.get();
        double baseConsumption = GeneralEnergyConfig.COMMON.aeToFeBaseConsumption.get();
        
        tooltip.add(Component.translatable("tooltip.generalenergy.ae2_to_fe_converter.fe_cache", maxFEOutput));
        tooltip.add(Component.translatable("tooltip.generalenergy.ae2_to_fe_converter.ae_consumption", String.format("%.0f", baseConsumption)));
        tooltip.add(Component.translatable("tooltip.generalenergy.ae2_to_fe_converter.output"));
    }
}
