package com.xlxyvergil.generalenergy.block;

import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.apiimpl.network.node.ConnectivityStateChangeCause;
import com.refinedmods.refinedstorage.apiimpl.network.node.NetworkNode;
import com.refinedmods.refinedstorage.blockentity.ControllerBlockEntity;
import com.refinedmods.refinedstorage.energy.BaseEnergyStorage;
import com.xlxyvergil.generalenergy.config.GeneralEnergyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

public class RSToFENetworkNode extends NetworkNode {

    public static final ResourceLocation ID = new ResourceLocation("generalenergy", "rs_to_fe_converter");
    
    // 每个方块贡献的额外 RS 网络容量（FE）- 从配置读取
    public static final int EXTRA_CAPACITY_PER_CONVERTER = GeneralEnergyConfig.COMMON.rsToFeCapacityPerConverter.get();
    
    // RS 网络节点的能量消耗（每 tick）- 从配置读取，默认10
    public static final int ENERGY_USAGE = GeneralEnergyConfig.COMMON.rsToFeEnergyUsage.get();
    
    // 最大提取/传输速率：80k FE/t - 从配置读取
    public static final int MAX_FE_TRANSFER = GeneralEnergyConfig.COMMON.rsToFeMaxFETransfer.get();

    public RSToFENetworkNode(Level level, BlockPos pos) {
        super(level, pos);
    }

    @Override
    protected void onConnectedStateChange(INetwork network, boolean state, ConnectivityStateChangeCause cause) {
        super.onConnectedStateChange(network, state, cause);
        updateBlockState();
    }

    private void updateBlockState() {
        if (level == null) return;
            
        var currentState = level.getBlockState(pos);
        if (!(currentState.getBlock() instanceof RSToFEConverterBlock)) return;
            
        // 检查网络是否存在且可以运行（控制器在线）
        var newState = (network != null && network.canRun()) 
            ? RSToFEConverterBlock.EnergyState.ONLINE
            : RSToFEConverterBlock.EnergyState.OFFLINE;
            
        var currentEnergyState = currentState.getValue(RSToFEConverterBlock.ENERGY_STATE);
        if (currentEnergyState != newState) {
            level.setBlock(pos,
                currentState.setValue(RSToFEConverterBlock.ENERGY_STATE, newState),
                Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public int getEnergyUsage() {
        // 计算总能量消耗 = 自身基础消耗 + 对外传输的FE量
        if (level == null) return ENERGY_USAGE;
        
        // 计算6方向邻居的总FE需求
        int totalDemand = 0;
        
        for (Direction direction : Direction.values()) {
            var neighborPos = pos.relative(direction);
            var neighborBE = level.getBlockEntity(neighborPos);
            
            if (neighborBE == null) continue;
            
            // 跳过 RS Controller
            if (neighborBE instanceof ControllerBlockEntity) continue;
            
            // 获取邻居的 FE 能力
            var cap = neighborBE.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite());
            if (!cap.isPresent()) continue;
            
            var handler = cap.orElse(null);
            if (handler == null) continue;
            
            // 模拟检测：邻居最多能接收多少 FE
            // 使用 MAX_FE_TRANSFER 探测，避免极端数值
            int canReceive = handler.receiveEnergy(MAX_FE_TRANSFER, true);
            totalDemand += canReceive;
        }
        
        // 限制最大提取速率
        int feTransferAmount = Math.min(totalDemand, MAX_FE_TRANSFER);
        
        // 总消耗 = 基础消耗 + 对外传输的FE量
        return ENERGY_USAGE + feTransferAmount;
    }

    @Override
    public void update() {
        super.update();

        // 调用 BlockEntity 的 tick，从 RS 网络提取能量到内部缓存
        if (level != null) {
            var blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof RSToFEConverterBlockEntity rsBE) {
                rsBE.tick();
            }
        }
    }

    @Override
    public void onConnected(INetwork network) {
        super.onConnected(network);
    }

    @Override
    public void onDisconnected(INetwork network) {
        super.onDisconnected(network);
    }

    @Override
    public CompoundTag write(CompoundTag tag) {
        super.write(tag);
        return tag;
    }

    @Override
    public void read(CompoundTag tag) {
        super.read(tag);
    }
}
