package com.xlxyvergil.generalenergy.block;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkPowerBlockEntity;
import appeng.core.settings.TickRates;
import com.xlxyvergil.generalenergy.GeneralEnergy;
import com.xlxyvergil.generalenergy.ModRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.List;

public class AE2ToFEConverterBlockEntity extends AENetworkPowerBlockEntity implements IGridTickable {

    private static final int MAX_AE_POWER = 50000; // AE缓存上限：50k AE = 100k FE
    private static final double BASE_AE_CONSUMPTION = 100.0; // 基础AE消耗：100 AE/t → 200 FE/t
    private static final int MAX_FE_OUTPUT_PER_TICK = 80000; // 最大FE输出：80k FE/t（对应40k AE/t）
    private double currentIdlePowerUsage = BASE_AE_CONSUMPTION;

    public AE2ToFEConverterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.setInternalMaxPower(MAX_AE_POWER);
        
        this.getMainNode()
            .setIdlePowerUsage(BASE_AE_CONSUMPTION)
            .addService(IGridTickable.class, this);
        // 不设置为公共电源存储，防止网络直接注入AE（会导致瞬间填满）
        // 我们通过extractAEAndConvertToFE主动控制提取速度
    }



    @Override
    public void onReady() {
        super.onReady();
        
        // 动态提升Controller缓存上限：每个转换器方块增加50000 AE
        var grid = getMainNode().getGrid();
        if (grid != null) {
            for (var node : grid.getNodes()) {
                var owner = node.getOwner();
                if (owner instanceof appeng.blockentity.networking.ControllerBlockEntity controller) {
                    // 统计网络中转换器方块数量
                    int converterCount = 0;
                    for (var n : grid.getNodes()) {
                        var o = n.getOwner();
                        if (o instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
                            if (be.getType() == ModRegistration.AE2_TO_FE_CONVERTER_ENTITY.get()) {
                                converterCount++;
                            }
                        }
                    }
                    // 设置新的缓存上限
                    double newMaxPower = 8000.0 + (converterCount * 50000.0);
                    controller.setInternalMaxPower(newMaxPower);
                    break; // 只处理第一个Controller
                }
            }
        }
        
        this.getMainNode().setVisualRepresentation(ModRegistration.AE2_TO_FE_CONVERTER_ITEM.get());
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
     * 从AE2网络提取AE能量转换为FE
     * 每次tickingRequest调用时执行
     * @param aeSpace AE缓存剩余空间
     */
    private void extractAEAndConvertToFE(double aeSpace) {
        var grid = getMainNode().getGrid();
        if (grid == null) return;
        
        // 填充自身AE缓存时，限制速率为BASE_AE_CONSUMPTION（100 AE/t）
        double extractAE = Math.min(aeSpace, BASE_AE_CONSUMPTION);
        
        // 直接从网络提取并注入到内部存储
        double extracted = grid.getEnergyService().extractAEPower(extractAE, appeng.api.config.Actionable.MODULATE, appeng.api.config.PowerMultiplier.ONE);
        
        if (extracted > 0) {
            // 使用injectAEPower注入到内部存储
            double notInserted = this.injectAEPower(extracted, appeng.api.config.Actionable.MODULATE);
            // 如果没有完全注入（存储满了），提取的多余AE应该返回网络
            if (notInserted > 0) {
                grid.getEnergyService().injectPower(notInserted, appeng.api.config.Actionable.MODULATE);
            }
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
        
        // 第1步：检测6个方向的总FE需求
        int totalFEDemand = 0;
        int[] faceDemands = new int[6];  // 记录每个面的需求
        for (int i = 0; i < Direction.values().length; i++) {
            Direction direction = Direction.values()[i];
            var neighborPos = this.worldPosition.relative(direction);
            if (this.level == null) continue;
            var neighborBE = this.level.getBlockEntity(neighborPos);
            if (neighborBE == null) continue;
            
            // 跳过AE2 Controller防止循环
            String beClassName = neighborBE.getClass().getName();
            if (beClassName.contains("ControllerBlockEntity") || 
                beClassName.contains("appeng.blockentity.networking.ControllerBlockEntity")) {
                continue;
            }
            
            var cap = neighborBE.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite());
            if (cap.isPresent()) {
                var handler = cap.resolve().get();
                int demand = handler.receiveEnergy(Integer.MAX_VALUE, true);
                faceDemands[i] = demand;
                totalFEDemand += demand;
            }
        }
        
        // 第2步：先提取AE填充自身缓存（限制为100 AE/t）
        double currentAE = getInternalCurrentPower();
        double maxAE = getInternalMaxPower();
        double aeSpace = maxAE - currentAE;
        extractAEAndConvertToFE(aeSpace);  // 内部会限制为BASE_AE_CONSUMPTION
        
        // 第3步：计算总AE需求（外部FE需求 + 内部缓存填充）
        double externalAENeeded = Math.min(totalFEDemand / 2.0, 40000.0);  // 外部需求上限40000 AE/t
        double internalAENeeded = Math.min(aeSpace, BASE_AE_CONSUMPTION);  // 内部填充最多100 AE/t
        double totalAENeeded = externalAENeeded + internalAENeeded;
        
        // 第4步：设置idlePowerUsage为总需求
        if (currentIdlePowerUsage != totalAENeeded) {
            currentIdlePowerUsage = totalAENeeded;
            this.getMainNode().setIdlePowerUsage(totalAENeeded);
            updateBlockState();
        }
        
        // 第5步：对外输出FE
        emitFEToNeighbors();
        
        return TickRateModulation.FASTER;
    }
    
    /**
     * 对外输出FE（参考Mekanism的CableUtils.emit逻辑）
     * @return 实际输出的FE总量
     */
    private int emitFEToNeighbors() {
        int totalSent = 0;
        
        // 计算可用的FE总量（基于当前AE缓存）
        double currentAE = getInternalCurrentPower();
        int availableFE = (int) Math.floor(currentAE * 2);
        
        // 限制本tick的最大输出
        int maxOutputThisTick = Math.min(availableFE, MAX_FE_OUTPUT_PER_TICK);
        if (maxOutputThisTick <= 0) return 0;
        
        // 收集所有有需求的邻居
        List<FEDemandInfo> demands = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            var neighborPos = this.worldPosition.relative(direction);
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
            
            demands.add(new FEDemandInfo(handler, canReceive));
        }
        
        if (demands.isEmpty()) return 0;
        
        // 按需求比例分配可用FE
        int totalDemand = demands.stream().mapToInt(d -> d.demand).sum();
        int remainingOutput = maxOutputThisTick;
        
        for (FEDemandInfo demand : demands) {
            if (remainingOutput <= 0) break;
            
            // 按比例分配
            int allocated = totalDemand > 0 ? (int) Math.floor((double) demand.demand / totalDemand * maxOutputThisTick) : 0;
            allocated = Math.min(allocated, remainingOutput);
            allocated = Math.min(allocated, demand.demand);
            
            if (allocated <= 0) continue;
            
            // 实际发送FE（从AE转换）
            int actuallySent = demand.handler.receiveEnergy(allocated, false);
            if (actuallySent > 0) {
                // 从AE2内部存储扣除对应的AE
                double aeNeeded = actuallySent / 2.0;
                this.extractAEPower(aeNeeded, appeng.api.config.Actionable.MODULATE);
                totalSent += actuallySent;
                remainingOutput -= actuallySent;
            }
        }
        
        return totalSent;
    }
    
    private static class FEDemandInfo {
        IEnergyStorage handler;
        int demand;
        
        FEDemandInfo(IEnergyStorage handler, int demand) {
            this.handler = handler;
            this.demand = demand;
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        // AE2内部存储会自动保存
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        // AE2内部存储会自动加载
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        // 使用AE2的ForgeEnergyAdapter，不需要自定义
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
        if (this.level == null) {
            return;
        }

        var grid = getMainNode().getGrid();
        var newState = AE2ToFEConverterBlock.EnergyState.OFFLINE;
        
        if (grid != null && grid.getEnergyService().isNetworkPowered()) {
            newState = AE2ToFEConverterBlock.EnergyState.ONLINE;
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
}
