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
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nullable;


public class RSToFEConverterBlockEntity extends NetworkNodeBlockEntity<RSToFENetworkNode> implements IEnergyStorage {

    // 内部能量缓存容量 - 使用 RS 转换器独立配置
    private static final int INTERNAL_CAPACITY = GeneralEnergyConfig.COMMON.rsToFeInternalCapacity.get();
    // 基础消耗速率 - 从配置读取
    private static final int BASE_ENERGY_USAGE = GeneralEnergyConfig.COMMON.rsToFeEnergyUsage.get();
    // 每个连接面增加的抽取速率 - 从配置读取
    private static final int BONUS_PER_SIDE = GeneralEnergyConfig.COMMON.rsToFeBonusPerSide.get();
    
    // 内部能量缓存（可序列化的包装类），最大提取速率设为无限制，由外部拉取决定
    private final SerializableEnergyStorage internalStorage = new SerializableEnergyStorage(INTERNAL_CAPACITY, Integer.MAX_VALUE, Integer.MAX_VALUE);
    
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
     * 每个 tick 调用，根据外部潜在需求动态决定从 RS 网络提取的能量
     */
    public void tick() {
        if (level == null || level.isClientSide()) return;
        
        var networkNode = getNode();
        var network = (networkNode != null) ? networkNode.getNetwork() : null;
        boolean hasNetwork = (network != null && network.canRun());
        
        if (!hasNetwork) return;
        
        var rsEnergyStorage = network.getEnergyStorage();
        if (!(rsEnergyStorage instanceof com.refinedmods.refinedstorage.energy.BaseEnergyStorage baseStorage)) return;
        
        // 1. 固定部分：无条件从 RS 网络抽取基础能量，存入缓存
        int baseExtracted = Math.min(BASE_ENERGY_USAGE, baseStorage.getEnergyStored());
        if (baseExtracted > 0) {
            baseStorage.extractEnergyBypassCanExtract(baseExtracted, false);
            internalStorage.receiveEnergy(baseExtracted, false);
            setChanged();
        }
        
        // 2. 动态部分：遍历每个面，如果该面有邻居，则额外抽取并存入缓存，然后立即推送
        for (Direction direction : Direction.values()) {
            var neighborPos = getBlockPos().relative(direction);
            var neighborBE = level.getBlockEntity(neighborPos);
            if (neighborBE == null) continue;
            
            var cap = neighborBE.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite());
            if (!cap.isPresent()) continue;
            
            var handler = cap.orElse(null);
            if (handler == null) continue;
            
            // 该面有邻居，额外抽取 BONUS_PER_SIDE 的能量并存入缓存
            int bonusExtract = Math.min(BONUS_PER_SIDE, baseStorage.getEnergyStored());
            if (bonusExtract <= 0) continue;
            
            baseStorage.extractEnergyBypassCanExtract(bonusExtract, false);
            internalStorage.receiveEnergy(bonusExtract, false);
            setChanged();
            
            // 从缓存推送到该面的邻居
            int canReceive = handler.receiveEnergy(bonusExtract, true);
            if (canReceive > 0) {
                int actuallySent = handler.receiveEnergy(canReceive, false);
                if (actuallySent > 0) {
                    // 从缓存扣除
                    internalStorage.extractEnergy(actuallySent, false);
                    setChanged();
                }
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
