package com.xlxyvergil.generalenergy.block;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkPowerBlockEntity;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.core.settings.TickRates;
import com.xlxyvergil.generalenergy.ModRegistration;
import com.xlxyvergil.generalenergy.api.IControllerBaseCapacity;
import com.xlxyvergil.generalenergy.config.GeneralEnergyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;


public class AE2ToFEConverterBlockEntity extends AENetworkPowerBlockEntity implements IGridTickable {

    private static final int MAX_AE_POWER = GeneralEnergyConfig.COMMON.aeToFeMaxAEPower.get(); // AE缓存上限（从配置读取）
    private static final double BASE_AE_CONSUMPTION = GeneralEnergyConfig.COMMON.aeToFeBaseConsumption.get(); // 基础AE消耗（从配置读取）
    private static final double AE_BONUS_PER_SIDE = GeneralEnergyConfig.COMMON.aeToFeBonusPerSide.get(); // 每个连接面增加的AE抽取速率
    private static final double CAPACITY_PER_CONVERTER = GeneralEnergyConfig.COMMON.aeToFeCapacityPerConverter.get(); // 每个转换器增加的AE容量（从配置读取）

    public AE2ToFEConverterBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistration.AE2_TO_FE_CONVERTER_ENTITY.get(), pos, state);
        this.setInternalMaxPower(MAX_AE_POWER);
        
        this.getMainNode()
            .setIdlePowerUsage(0) // 初始为0，在tick中动态设置
            .addService(IGridTickable.class, this);
    }

    /**
     * @deprecated 仅供内部使用，请使用 {@link #AE2ToFEConverterBlockEntity(BlockPos, BlockState)}
     */
    @Deprecated
    public AE2ToFEConverterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.setInternalMaxPower(MAX_AE_POWER);
        
        this.getMainNode()
            .setIdlePowerUsage(0)
            .addService(IGridTickable.class, this);
    }



    @Override
    public void onReady() {
        super.onReady();
        
        // 动态提升Controller缓存上限：每个转换器方块增加50000 AE
        var grid = getMainNode().getGrid();
        if (grid != null) {
            for (var node : grid.getNodes()) {
                var owner = node.getOwner();
                if (owner instanceof ControllerBlockEntity controller) {
                    // 统计网络中转换器方块数量
                    int converterCount = 0;
                    for (var n : grid.getNodes()) {
                        var o = n.getOwner();
                        if (o instanceof BlockEntity be) {
                            if (be.getType() == ModRegistration.AE2_TO_FE_CONVERTER_ENTITY.get()) {
                                converterCount++;
                            }
                        }
                    }
                    // 通过 Mixin 接口获取基础容量（支持其他 Mod 修改后仍能正确计算）
                    double baseCapacity = ((IControllerBaseCapacity) controller).generalenergy$getBaseCapacity();
                    // 设置新的缓存上限
                    double newMaxPower = baseCapacity + (converterCount * CAPACITY_PER_CONVERTER);
                    controller.setInternalMaxPower(newMaxPower);
                    break; // 只处理第一个Controller
                }
            }
        }
        
        this.getMainNode().setVisualRepresentation(ModRegistration.AE2_TO_FE_CONVERTER_ITEM.get());
        updateBlockState();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        updateBlockState();
    }


    // IGridTickable接口实现 - 告诉AE2我们需要被tick（用于动态更新idlePowerUsage）
    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.Charger, false, true);
    }

    // IGridTickable接口实现 - AE2网格定期调用（仅用于保持网络连接）
    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        return TickRateModulation.SLOWER;
    }
    
    // 原生 tick() - 每 Tick 执行，负责从网络提取能量并推送到邻居
    public void tick() {
        if (level == null || level.isClientSide()) return;
        
        var grid = getMainNode().getGrid();
        if (grid == null) return;
        
        // 1. 计算有效连接面数
        int connectedSides = 0;
        for (Direction direction : Direction.values()) {
            var neighborPos = this.worldPosition.relative(direction);
            var neighborBE = this.level.getBlockEntity(neighborPos);
            if (neighborBE == null) continue;
            
            String beClassName = neighborBE.getClass().getName();
            if (beClassName.contains("ControllerBlockEntity") || 
                beClassName.contains("appeng.blockentity.networking.ControllerBlockEntity")) {
                continue;
            }
            
            var cap = neighborBE.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite());
            if (cap.isPresent() && cap.orElse(null) != null) {
                connectedSides++;
            }
        }
        
        // 2. 动态计算本 Tick 的 AE 提取目标
        double targetAE = BASE_AE_CONSUMPTION + (connectedSides * AE_BONUS_PER_SIDE);
        
        // 3. 从 AE2 网络提取能量并存入缓存
        double extracted = grid.getEnergyService().extractAEPower(targetAE, Actionable.MODULATE, PowerMultiplier.ONE);
        if (extracted > 0) {
            this.injectAEPower(extracted, Actionable.MODULATE);
        }
        
        // 4. 从内部缓存提取 AE 转换为 FE，推送到邻居
        emitFEToNeighbors();
    }
    
    private void emitFEToNeighbors() {
        if (this.level == null || this.level.isClientSide()) return;
        
        double currentAE = getInternalCurrentPower();
        if (currentAE <= 0) return;
        
        for (Direction direction : Direction.values()) {
            var neighborPos = this.worldPosition.relative(direction);
            var neighborBE = this.level.getBlockEntity(neighborPos);
            if (neighborBE == null) continue;
            
            // 跳过AE2 Controller防止循环
            String beClassName = neighborBE.getClass().getName();
            if (beClassName.contains("ControllerBlockEntity") || 
                beClassName.contains("appeng.blockentity.networking.ControllerBlockEntity")) {
                continue;
            }
            
            var cap = neighborBE.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite());
            if (!cap.isPresent()) continue;
            
            var handler = cap.orElse(null);
            if (handler == null) continue;
            
            // 计算该面可推送的 FE 量（单面奖励对应的 FE）
            int feToSend = (int) Math.floor(AE_BONUS_PER_SIDE * PowerMultiplier.CONFIG.multiplier);
            if (feToSend <= 0) continue;
            
            // 检查缓存是否有足够的 AE 转换为 FE
            double aeNeeded = feToSend / PowerMultiplier.CONFIG.multiplier;
            if (currentAE < aeNeeded) {
                feToSend = (int) Math.floor(currentAE * PowerMultiplier.CONFIG.multiplier);
                if (feToSend <= 0) continue;
            }
            
            // 模拟检测邻居能接收多少
            int canReceive = handler.receiveEnergy(feToSend, true);
            if (canReceive > 0) {
                // 实际推送
                int actuallySent = handler.receiveEnergy(canReceive, false);
                if (actuallySent > 0) {
                    // 从 AE2 缓存扣除对应的 AE
                    double aeActuallyUsed = actuallySent / PowerMultiplier.CONFIG.multiplier;
                    this.extractAEPower(aeActuallyUsed, Actionable.MODULATE);
                    currentAE -= aeActuallyUsed;
                    setChanged();
                }
            }
        }
    }
    
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putDouble("internalCurrentPower", this.getInternalCurrentPower());
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        this.setInternalCurrentPower(tag.getDouble("internalCurrentPower"));
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        // AE2父类AEBasePoweredBlockEntity已保存internalCurrentPower到NBT
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        // AE2父类AEBasePoweredBlockEntity已加载internalCurrentPower从NBT
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
                        Block.UPDATE_CLIENTS);
            }
        }
    }
}
