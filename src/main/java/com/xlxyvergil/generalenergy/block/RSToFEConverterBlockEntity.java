package com.xlxyvergil.generalenergy.block;

import com.refinedmods.refinedstorage.blockentity.NetworkNodeBlockEntity;
import com.refinedmods.refinedstorage.blockentity.data.BlockEntitySynchronizationSpec;
import com.xlxyvergil.generalenergy.ModRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nullable;


public class RSToFEConverterBlockEntity extends NetworkNodeBlockEntity<RSToFENetworkNode> implements IEnergyStorage {

    private final LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(() -> this);

    public RSToFEConverterBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistration.RS_TO_FE_CONVERTER_ENTITY.get(), pos, state, BlockEntitySynchronizationSpec.builder().build(), RSToFENetworkNode.class);
    }

    /**
     * @deprecated 仅供内部使用，请使用 {@link #RSToFEConverterBlockEntity(BlockPos, BlockState)}
     */
    @Deprecated
    public RSToFEConverterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, BlockEntitySynchronizationSpec.builder().build(), RSToFENetworkNode.class);
    }

    @Override
    public RSToFENetworkNode createNode(Level level, BlockPos pos) {
        return new RSToFENetworkNode(level, pos);
    }

    // ==================== IEnergyStorage 实现 ====================

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        // 本方块不接收能量，只是转换器
        return 0;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        // 本方块不直接提取能量，由 NetworkNode 处理
        return 0;
    }

    @Override
    public int getEnergyStored() {
        // 返回网络存储的能量
        var networkNode = getNode();
        if (networkNode != null && networkNode.getNetwork() != null) {
            return networkNode.getNetwork().getEnergyStorage().getEnergyStored();
        }
        return 0;
    }

    @Override
    public int getMaxEnergyStored() {
        // 返回网络存储的容量
        var networkNode = getNode();
        if (networkNode != null && networkNode.getNetwork() != null) {
            return networkNode.getNetwork().getEnergyStorage().getMaxEnergyStored();
        }
        return 0;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return false;
    }

    // ==================== Capability 暴露 ====================

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energyCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        energyCapability.invalidate();
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
    }
}
