package com.xlxyvergil.generalenergy.block;

import appeng.me.helpers.IGridConnectedBlockEntity;
import com.refinedmods.refinedstorage.api.network.node.INetworkNodeProxy;
import com.xlxyvergil.generalenergy.ModRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;

/**
 * 能量接口 - 基础方块
 * 放置时根据相邻方块自动转换为 AE2 或 RS 转换器
 */
public class EnergyInterfaceBlock extends Block {
    
    public EnergyInterfaceBlock(Properties properties) {
        super(properties);
    }
    
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        
        if (level.isClientSide()) return;
        
        // 检测相邻方块的网络类型
        NetworkType detectedType = detectNetworkType(level, pos);
        
        // 根据检测结果替换为对应方块
        switch (detectedType) {
            case AE2:
                replaceWithAE2Converter(level, pos, state);
                break;
            case RS:
                replaceWithRSConverter(level, pos, state);
                break;
            case NONE:
                // 保持为基础方块，不做任何操作
                break;
        }
    }
    
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {
        // 掉落基础方块
        if (!level.isClientSide && !player.isCreative()) {
            popResource(level, pos, new ItemStack(ModRegistration.ENERGY_INTERFACE_ITEM.get()));
        }
        super.playerWillDestroy(level, pos, state, player);
    }
    
    /**
     * 检测相邻方块的网络类型
     * 检测顺序：西、东、北、南、下、上
     * 通过检查是否实现网络接口来判断（支持主模组和附属模组）
     */
    private NetworkType detectNetworkType(Level level, BlockPos pos) {
        // 按照指定顺序检测：WEST, EAST, NORTH, SOUTH, DOWN, UP
        Direction[] checkOrder = {
            Direction.WEST,
            Direction.EAST,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.DOWN,
            Direction.UP
        };
        
        for (Direction dir : checkOrder) {
            BlockPos neighborPos = pos.relative(dir);
            BlockEntity neighborBE = level.getBlockEntity(neighborPos);
            
            if (neighborBE == null) continue;
            
            // 检测 AE2 网络节点 - 检查是否实现 IGridConnectedBlockEntity 接口
            if (ModList.get().isLoaded("ae2") && neighborBE instanceof IGridConnectedBlockEntity) {
                var node = ((IGridConnectedBlockEntity) neighborBE).getMainNode();
                if (node != null && node.isReady()) {
                    return NetworkType.AE2;
                }
            }
            
            // 检测 RS 网络节点 - 检查是否实现 INetworkNodeProxy 接口
            if (ModList.get().isLoaded("refinedstorage") && neighborBE instanceof INetworkNodeProxy<?>) {
                var proxy = (INetworkNodeProxy<?>) neighborBE;
                var networkNode = proxy.getNode();
                if (networkNode != null && networkNode.getNetwork() != null) {
                    return NetworkType.RS;
                }
            }
        }
        
        return NetworkType.NONE;
    }
    
    /**
     * 替换为 AE2 转换器
     */
    private void replaceWithAE2Converter(Level level, BlockPos pos, BlockState oldState) {
        BlockState newState = ModRegistration.AE2_TO_FE_CONVERTER.get().defaultBlockState();
        level.setBlock(pos, newState, 3);
    }
    
    /**
     * 替换为 RS 转换器
     */
    private void replaceWithRSConverter(Level level, BlockPos pos, BlockState oldState) {
        BlockState newState = ModRegistration.RS_TO_FE_CONVERTER.get().defaultBlockState();
        level.setBlock(pos, newState, 3);
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
