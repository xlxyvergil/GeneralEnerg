package com.xlxyvergil.generalenergy.block;

import appeng.api.config.Actionable;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkPowerBlockEntity;
import appeng.core.settings.TickRates;
import com.xlxyvergil.generalenergy.GeneralEnergy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

public class AE2ToFEConverterBlockEntity extends AENetworkPowerBlockEntity implements IGridTickable {

    private final EnergyStorage feStorage;
    private static final int MAX_FE_POWER = 80000; // FE缓存上限：80k FE
    private static final double BASE_AE_CONSUMPTION = 100.0; // 基础AE消耗：100 AE/t → 200 FE/t
    private double currentIdlePowerUsage = BASE_AE_CONSUMPTION;
    private int tickCounter = 0; // tick计数器，控制提取频率

    public AE2ToFEConverterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        // FE存储的maxExtract设为Integer.MAX_VALUE，实现无上限输出
        this.feStorage = new EnergyStorage(MAX_FE_POWER, Integer.MAX_VALUE, Integer.MAX_VALUE);
        
        // 初始idlePowerUsage为基础消耗100 AE/t
        this.getMainNode()
            .setIdlePowerUsage(BASE_AE_CONSUMPTION)
            .addService(IGridTickable.class, this);
        // 设置为公共电源存储，允许网络注入能量（转换为FE）
        this.setInternalPublicPowerStorage(true);
        // 设置为WRITE，让网络可以向我们的方块注入AE能量
        this.setInternalPowerFlow(appeng.api.config.AccessRestriction.WRITE);
    }



    @Override
    public void onReady() {
        super.onReady();
        this.getMainNode().setVisualRepresentation(GeneralEnergy.AE2_TO_FE_CONVERTER_ITEM.get());
        updateBlockState();
    }

    @Override
    public void onMainNodeStateChanged(appeng.api.networking.IGridNodeListener.State reason) {
        updateBlockState();
    }

    /**
     * 覆盖funnelPowerIntoStorage，但我们的方块不接收AE存储，而是直接从网络提取AE转换为FE
     */
    @Override
    protected double funnelPowerIntoStorage(double power, appeng.api.config.Actionable mode) {
        // 我们的方块不存储AE，直接返回未接收的量
        return power;
    }

    /**
     * 从AE2网络提取AE能量转换为FE，每20 tick提取一次
     */
    private void extractAEAndConvertToFE() {
        tickCounter++;
        if (tickCounter < 20) return; // 每20 tick（1秒）提取一次
        tickCounter = 0;
        
        var grid = getMainNode().getGrid();
        if (grid == null) return;
        
        int currentFE = feStorage.getEnergyStored();
        int feSpace = MAX_FE_POWER - currentFE;
        if (feSpace <= 0) return;
        
        // 每次最多提取100 AE（200 FE）
        double maxExtractAE = 100.0;
        double needAE = Math.min(feSpace / 2.0, maxExtractAE);
        
        double extracted = grid.getEnergyService().extractAEPower(needAE, appeng.api.config.Actionable.MODULATE, appeng.api.config.PowerMultiplier.ONE);
        
        if (extracted > 0) {
            int feToAdd = (int) (extracted * 2);
            feStorage.receiveEnergy(feToAdd, false);
        }
    }

    // IGridTickable接口实现 - 告诉AE2我们需要被tick
    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        // 每tick都调用，类似于Charger的tick速率
        return new TickingRequest(TickRates.Charger, false, true);
    }

    // IGridTickable接口实现 - AE2网格定期调用此方法进行tick
    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        var grid = getMainNode().getGrid();
        if (grid == null) return TickRateModulation.SLOWER;
        
        // 第1步：对外输出FE（参考Mekanism的emit逻辑）
        int totalFEExtracted = emitFEToNeighbors();
        
        // 第2步：检查FE缓存状态
        int currentFE = feStorage.getEnergyStored();
        int feSpace = MAX_FE_POWER - currentFE;
        
        // 第3步：根据FE缓存状态动态调整AE消耗
        if (feSpace <= 0) {
            currentIdlePowerUsage = 0.0;
        } else if (totalFEExtracted > 0) {
            currentIdlePowerUsage = Math.min(totalFEExtracted / 2.0, BASE_AE_CONSUMPTION);
        } else {
            currentIdlePowerUsage = BASE_AE_CONSUMPTION;
        }
        
        this.getMainNode().setIdlePowerUsage(currentIdlePowerUsage);
        updateBlockState(); // 更新材质
        
        // 第4步：从AE2网络提取AE能量转换为FE（参考控制器的逻辑，但我们是提取而非注入）
        extractAEAndConvertToFE();
        
        return TickRateModulation.FASTER;
    }
    
    /**
     * 对外输出FE（参考Mekanism的CableUtils.emit逻辑）
     * @return 实际输出的FE总量
     */
    private int emitFEToNeighbors() {
        int totalSent = 0;
        
        // 对每个方向独立输出
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = this.getBlockPos().relative(direction);
            if (this.level == null) continue;
            
            var neighborBE = this.level.getBlockEntity(neighborPos);
            if (neighborBE == null) continue;
            
            // 跳过AE2 Controller防止循环
            String beClassName = neighborBE.getClass().getName();
            if (beClassName.contains("ControllerBlockEntity") || 
                beClassName.contains("appeng.blockentity.networking.ControllerBlockEntity")) {
                continue;
            }
            
            // 获取邻居的FE能力
            var optionalHandler = neighborBE.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite());
            if (!optionalHandler.isPresent()) continue;
            
            var handler = optionalHandler.resolve().get();
            
            // 模拟检测：邻居这个tick最多能接收多少FE
            int canReceive = handler.receiveEnergy(Integer.MAX_VALUE, true);
            if (canReceive <= 0) continue;
            
            // 模拟检测：feStorage能提取多少
            int canExtract = feStorage.extractEnergy(Integer.MAX_VALUE, true);
            if (canExtract <= 0) continue;
            
            // 计算实际能发送的量
            int toSend = Math.min(canReceive, canExtract);
            if (toSend <= 0) continue;
            
            // 实际发送FE
            int actuallySent = handler.receiveEnergy(toSend, false);
            if (actuallySent > 0) {
                feStorage.extractEnergy(actuallySent, false);
                totalSent += actuallySent;
            }
        }
        
        return totalSent;
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("feStorage", feStorage.getEnergyStored());
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        if (tag.contains("feStorage")) {
            feStorage.setEnergy(tag.getInt("feStorage"));
        }
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return LazyOptional.of(() -> feStorage).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public InternalInventory getInternalInventory() {
        return InternalInventory.empty();
    }

    @Override
    public void onChangeInventory(InternalInventory inv, int slot) {
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.COVERED;
    }

    private void updateBlockState() {
        if (this.level == null || !this.getMainNode().isReady()) {
            return;
        }

        var grid = getMainNode().getGrid();
        var newState = AE2ToFEConverterBlock.EnergyState.OFFLINE;
        
        if (grid != null) {
            if (grid.getEnergyService().isNetworkPowered()) {
                newState = AE2ToFEConverterBlock.EnergyState.ONLINE;
            }
        }

        // 如果状态不同，更新BlockState
        var currentState = this.level.getBlockState(this.worldPosition);
        if (currentState.getBlock() instanceof AE2ToFEConverterBlock) {
            var currentEnergyState = currentState.getValue(AE2ToFEConverterBlock.ENERGY_STATE);
            if (currentEnergyState != newState) {
                this.level.setBlock(this.worldPosition,
                        currentState.setValue(AE2ToFEConverterBlock.ENERGY_STATE, newState),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
    }

    private static class EnergyStorage implements IEnergyStorage {
        private final int capacity;
        private final int maxReceive;
        private final int maxExtract;
        private int energy;

        public EnergyStorage(int capacity, int maxReceive, int maxExtract) {
            this.capacity = capacity;
            this.maxReceive = maxReceive;
            this.maxExtract = maxExtract;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int energyReceived = Math.min(capacity - energy, Math.min(this.maxReceive, maxReceive));
            if (!simulate) {
                energy += energyReceived;
            }
            return energyReceived;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int energyExtracted = Math.min(energy, Math.min(this.maxExtract, maxExtract));
            if (!simulate) {
                energy -= energyExtracted;
            }
            return energyExtracted;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }

        public void setEnergy(int energy) {
            this.energy = Math.min(energy, capacity);
        }
    }
}
