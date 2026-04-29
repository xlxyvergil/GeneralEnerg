package com.xlxyvergil.generalenergy.block;

import com.refinedmods.refinedstorage.blockentity.NetworkNodeBlockEntity;
import com.refinedmods.refinedstorage.blockentity.data.BlockEntitySynchronizationSpec;
import com.refinedmods.refinedstorage.energy.BaseEnergyStorage;
import com.xlxyvergil.generalenergy.ModRegistration;
import com.xlxyvergil.generalenergy.config.GeneralEnergyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nullable;


public class RSToFEConverterBlockEntity extends NetworkNodeBlockEntity<RSToFENetworkNode> implements IEnergyStorage {

    // 内部能量缓存容量 - 使用 RS 转换器独立配置
    private static final int INTERNAL_CAPACITY = GeneralEnergyConfig.COMMON.rsToFeInternalCapacity.get();
    // 无外部需求时的填充速率 / 基础消耗 - 从配置读取（默认100 FE/t）
    private static final int ENERGY_USAGE = GeneralEnergyConfig.COMMON.rsToFeEnergyUsage.get();
    // 最大输出速率 - 从配置读取
    private static final int MAX_OUTPUT_RATE = GeneralEnergyConfig.COMMON.rsToFeMaxFETransfer.get();
    
    // 内部能量缓存（可序列化的包装类）
    private final SerializableEnergyStorage internalStorage = new SerializableEnergyStorage(INTERNAL_CAPACITY, Integer.MAX_VALUE, MAX_OUTPUT_RATE);
    
    // 可序列化的能量存储包装类（参考Mekanism的BasicEnergyContainer）
    private static class SerializableEnergyStorage extends BaseEnergyStorage {
        public SerializableEnergyStorage(int capacity, int maxReceive, int maxExtract) {
            super(capacity, maxReceive, maxExtract);
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            if (getEnergyStored() > 0) {
                tag.putInt("Energy", getEnergyStored());
            }
            return tag;
        }

        @Override
        public void deserializeNBT(Tag tag) {
            if (tag instanceof CompoundTag compoundTag && compoundTag.contains("Energy")) {
                setStored(compoundTag.getInt("Energy"));
            }
        }
    }
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
    
    /**
     * 每个 tick 调用，从 RS 网络提取能量到内部缓存
     * 逻辑：
     * 1. 无外部需求时：以 ENERGY_USAGE 填充内部缓存
     * 2. 有外部需求时：消耗 = ENERGY_USAGE + 外部需求（上限 MAX_OUTPUT_RATE）
     * 3. 内部缓存满且无外部需求时：停止消耗
     */
    public void tick() {
        if (level == null || level.isClientSide()) return;
        
        var networkNode = getNode();
        var network = (networkNode != null) ? networkNode.getNetwork() : null;
        boolean hasNetwork = (network != null && network.canRun());
        
        if (!hasNetwork) return;
        
        // 计算外部总需求（限制在 MAX_OUTPUT_RATE 内）
        int externalDemand = Math.min(calculateExternalDemand(), MAX_OUTPUT_RATE);
        
        // 计算本 tick 需要从 RS 网络提取的能量
        int energyNeeded;
        if (externalDemand > 0) {
            // 有外部需求：基础消耗（填充内部缓存） + 外部需求
            int spaceInInternal = internalStorage.getMaxEnergyStored() - internalStorage.getEnergyStored();
            int fillInternal = Math.min(ENERGY_USAGE, spaceInInternal);
            energyNeeded = fillInternal + externalDemand;
        } else {
            // 无外部需求：仅填充内部缓存
            int spaceInInternal = internalStorage.getMaxEnergyStored() - internalStorage.getEnergyStored();
            if (spaceInInternal <= 0) return;
            energyNeeded = Math.min(ENERGY_USAGE, spaceInInternal);
        }
        
        // 第1步：从 RS 网络提取能量到内部缓存
        if (energyNeeded > 0) {
            var rsEnergyStorage = network.getEnergyStorage();
            if (rsEnergyStorage instanceof com.refinedmods.refinedstorage.energy.BaseEnergyStorage baseStorage 
                && baseStorage.getEnergyStored() > 0) {
                // 使用 extractEnergyBypassCanExtract 绕过RS网络的canExtract限制
                int extracted = Math.min(energyNeeded, baseStorage.getEnergyStored());
                baseStorage.extractEnergyBypassCanExtract(extracted, false);
                
                if (extracted > 0) {
                    // 存入内部缓存
                    internalStorage.receiveEnergy(extracted, false);
                    setChanged();
                }
            }
        }
        
        // 第2步：从内部缓存输出到外部
        if (externalDemand > 0) {
            emitFEToNeighbors(externalDemand);
        }
    }
    
    /**
     * 计算6方向邻居的总 FE 需求
     */
    private int calculateExternalDemand() {
        if (level == null) return 0;
        
        int totalDemand = 0;
        for (Direction direction : Direction.values()) {
            var neighborPos = getBlockPos().relative(direction);
            var neighborBE = level.getBlockEntity(neighborPos);
            
            if (neighborBE == null) continue;
            
            var cap = neighborBE.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite());
            if (!cap.isPresent()) continue;
            
            var handler = cap.orElse(null);
            if (handler == null) continue;
            
            // 模拟检测邻居能接收多少
            totalDemand += handler.receiveEnergy(Integer.MAX_VALUE, true);
        }
        
        return totalDemand;
    }
    
    /**
     * 从内部缓存输出 FE 到邻居
     */
    private void emitFEToNeighbors(int maxOutput) {
        if (level == null || maxOutput <= 0) return;
        
        int remaining = maxOutput;
        for (Direction direction : Direction.values()) {
            if (remaining <= 0) break;
            
            var neighborPos = getBlockPos().relative(direction);
            var neighborBE = level.getBlockEntity(neighborPos);
            
            if (neighborBE == null) continue;
            
            var cap = neighborBE.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite());
            if (!cap.isPresent()) continue;
            
            var handler = cap.orElse(null);
            if (handler == null) continue;
            
            // 实际输出FE（从内部缓存提取）
            int sent = internalStorage.extractEnergy(remaining, false);
            if (sent > 0) {
                // 发送给邻居
                int actuallyReceived = handler.receiveEnergy(sent, false);
                // 如果没完全接收，退回内部缓存
                if (actuallyReceived < sent) {
                    internalStorage.receiveEnergy(sent - actuallyReceived, false);
                }
                remaining -= actuallyReceived;
                setChanged();
            }
        }
    }

    // ==================== IEnergyStorage 实现 ====================

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        // 本方块不接收外部能量
        return 0;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        // 从内部缓存输出
        int extracted = internalStorage.extractEnergy(maxExtract, simulate);
        if (extracted > 0 && !simulate) {
            // 触发数据同步
            setChanged();
        }
        return extracted;
    }

    @Override
    public int getEnergyStored() {
        return internalStorage.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored() {
        return internalStorage.getMaxEnergyStored();
    }

    @Override
    public boolean canExtract() {
        return true;
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
    public CompoundTag writeUpdate(CompoundTag tag) {
        tag.putInt("InternalEnergy", internalStorage.getEnergyStored());
        return super.writeUpdate(tag);
    }

    @Override
    public void readUpdate(CompoundTag tag) {
        super.readUpdate(tag);
        if (tag.contains("InternalEnergy")) {
            internalStorage.setStored(tag.getInt("InternalEnergy"));
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("InternalEnergy", internalStorage.getEnergyStored());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("InternalEnergy")) {
            internalStorage.setStored(tag.getInt("InternalEnergy"));
        }
    }
}
