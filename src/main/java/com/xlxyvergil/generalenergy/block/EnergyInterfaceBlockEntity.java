package com.xlxyvergil.generalenergy.block;

import appeng.me.helpers.IGridConnectedBlockEntity;
import com.xlxyvergil.generalenergy.ModRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

/**
 * 能量接口 BlockEntity
 * 负责检测相邻网络并触发方块转换
 */
public class EnergyInterfaceBlockEntity extends BlockEntity {
    
    public EnergyInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistration.ENERGY_INTERFACE_BE.get(), pos, state);
    }
    
    /**
     * 检测并转换方块
     */
    public void checkAndConvert() {
        if (level == null || level.isClientSide()) return;
        
        NetworkType detectedType = detectNetworkType();
        
        switch (detectedType) {
            case AE2:
                if (!(getBlockState().getBlock() instanceof AE2ToFEConverterBlock)) {
                    replaceWithAE2Converter();
                }
                break;
            case RS:
                if (!(getBlockState().getBlock() instanceof RSToFEConverterBlock)) {
                    replaceWithRSConverter();
                }
                break;
            case NONE:
                // 保持为基础方块
                break;
        }
    }
    
    /**
     * 检测相邻方块的网络类型
     * 检测顺序：西、东、北、南、下、上
     */
    private NetworkType detectNetworkType() {
        Direction[] checkOrder = {
            Direction.WEST,
            Direction.EAST,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.DOWN,
            Direction.UP
        };
        
        for (Direction dir : checkOrder) {
            BlockPos neighborPos = getBlockPos().relative(dir);
            BlockEntity neighborBE = level.getBlockEntity(neighborPos);
            
            if (neighborBE == null) continue;
            
            // 检测 AE2 网络节点
            if (ModList.get().isLoaded("ae2") && neighborBE instanceof IGridConnectedBlockEntity) {
                var node = ((IGridConnectedBlockEntity) neighborBE).getMainNode();
                if (node != null && node.isReady()) {
                    return NetworkType.AE2;
                }
            }
            
            // 检测 RS 网络节点 - 检查是否存在 RS 网络节点
            if (ModList.get().isLoaded("refinedstorage")) {
                try {
                    var rsAPI = com.refinedmods.refinedstorage.apiimpl.API.instance();
                    if (rsAPI != null) {
                        var nodeManager = rsAPI.getNetworkNodeManager((net.minecraft.server.level.ServerLevel) level);
                        if (nodeManager != null) {
                            var rsNode = nodeManager.getNode(neighborPos);
                            // 只要存在 RS 网络节点就判定为 RS 网络
                            if (rsNode != null) {
                                return NetworkType.RS;
                            }
                        }
                    }
                } catch (Exception e) {
                    // 忽略异常，继续检测其他类型
                }
            }
        }
        
        return NetworkType.NONE;
    }
    
    /**
     * 替换为 AE2 转换器
     */
    private void replaceWithAE2Converter() {
        BlockState newState = ModRegistration.AE2_TO_FE_CONVERTER.get().defaultBlockState();
        level.setBlock(getBlockPos(), newState, 3);
    }
    
    /**
     * 替换为 RS 转换器
     */
    private void replaceWithRSConverter() {
        BlockState newState = ModRegistration.RS_TO_FE_CONVERTER.get().defaultBlockState();
        level.setBlock(getBlockPos(), newState, 3);
    }
    
    /**
     * 网络类型枚举
     */
    private enum NetworkType {
        NONE,
        AE2,
        RS
    }
}
