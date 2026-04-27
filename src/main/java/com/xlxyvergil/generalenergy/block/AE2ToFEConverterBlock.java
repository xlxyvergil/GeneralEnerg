package com.xlxyvergil.generalenergy.block;

import com.xlxyvergil.generalenergy.GeneralEnergy;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class AE2ToFEConverterBlock extends Block implements EntityBlock {

    public enum EnergyState implements StringRepresentable {
        OFFLINE("offline"),
        ONLINE("online"),
        CONFLICTED("conflicted");

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
        super(properties.lightLevel(state -> state.getValue(ENERGY_STATE) == EnergyState.ONLINE ? 15 : 0));
        this.registerDefaultState(this.defaultBlockState().setValue(ENERGY_STATE, EnergyState.OFFLINE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ENERGY_STATE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AE2ToFEConverterBlockEntity(GeneralEnergy.AE2_TO_FE_CONVERTER_ENTITY.get(), pos, state);
    }
}
