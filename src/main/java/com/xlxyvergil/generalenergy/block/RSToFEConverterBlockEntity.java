package com.xlxyvergil.generalenergy.block;

import com.refinedmods.refinedstorage.blockentity.NetworkNodeBlockEntity;
import com.refinedmods.refinedstorage.blockentity.data.BlockEntitySynchronizationSpec;
import com.refinedmods.refinedstorage.energy.BaseEnergyStorage;
import com.xlxyvergil.generalenergy.ModRegistration;
import com.xlxyvergil.generalenergy.config.GeneralEnergyConfig;
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

    // 内部能量缓存容量 - 使用 RS 转换器独立配置
    private static final int INTERNAL_CAPACITY = GeneralEnergyConfig.COMMON.rsToFeInternalCapacity.get();
    // 默认输入速率 - 100 FE/t
    private static final int DEFAULT_INPUT_RATE = 100;
    
    // 使用 RS 的 BaseEnergyStorage（支持 setStored API）
    // 参数：容量, 最大接收速率, 最大输出速率
    private final BaseEnergyStorage internalStorage = new BaseEnergyStorage(INTERNAL_CAPACITY, Integer.MAX_VALUE, Integer.MAX_VALUE);
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
     * 输入速率根据外部需求动态调整
     */
    public void tick() {
        if (level == null || level.isClientSide()) return;
        
        var networkNode = getNode();
        if (networkNode == null || networkNode.getNetwork() == null) return;
        
        var network = networkNode.getNetwork();
        if (!network.canRun()) return;
        
        var rsEnergyStorage = network.getEnergyStorage();
        if (rsEnergyStorage.getEnergyStored() <= 0) return;
        
        // 计算外部总需求
        int externalDemand = calculateExternalDemand();
        
        // 动态调整输入速率：外部需求越大，输入越快
        int inputRate = Math.max(DEFAULT_INPUT_RATE, externalDemand);
        
        // 计算实际可提取量（不超过内部缓存剩余空间）
        int spaceInInternal = internalStorage.getMaxEnergyStored() - internalStorage.getEnergyStored();
        int toExtract = Math.min(inputRate, spaceInInternal);
        
        if (toExtract > 0) {
            // 从 RS 网络提取
            int extracted = rsEnergyStorage.extractEnergy(toExtract, false);
            if (extracted > 0) {
                // 存入内部缓存
                internalStorage.receiveEnergy(extracted, false);
            }
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

    // ==================== IEnergyStorage 实现 ====================

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        // 本方块不接收外部能量
        return 0;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        // 从内部缓存输出
        return internalStorage.extractEnergy(maxExtract, simulate);
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
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        // 保存内部缓存能量
        tag.putInt("InternalEnergy", internalStorage.getEnergyStored());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // 加载内部缓存能量 - 使用 RS API 的 setStored
        if (tag.contains("InternalEnergy")) {
            internalStorage.setStored(tag.getInt("InternalEnergy"));
        }
    }
}
