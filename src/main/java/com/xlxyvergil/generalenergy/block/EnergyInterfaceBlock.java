package com.xlxyvergil.generalenergy.block;

import com.xlxyvergil.generalenergy.ModRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 能量接口 - 基础方块
 * 放置时根据相邻方块自动转换为 AE2 或 RS 转换器
 */
public class EnergyInterfaceBlock extends Block implements EntityBlock {
    
    public EnergyInterfaceBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
    
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnergyInterfaceBlockEntity(pos, state);
    }
    
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        
        if (level.isClientSide()) return;
        
        // 通知 BlockEntity 进行网络检测
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof EnergyInterfaceBlockEntity energyBE) {
            energyBE.checkAndConvert();
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
    
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        
        if (level.isClientSide()) return;
        
        // 通知 BlockEntity 进行网络检测
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof EnergyInterfaceBlockEntity energyBE) {
            energyBE.checkAndConvert();
        }
    }
    
    @Override
    public void onNeighborChange(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos, BlockPos neighbor) {
        super.onNeighborChange(state, level, pos, neighbor);
        
        if (level instanceof Level serverLevel && !serverLevel.isClientSide()) {
            // 通知 BlockEntity 进行网络检测
            BlockEntity be = serverLevel.getBlockEntity(pos);
            if (be instanceof EnergyInterfaceBlockEntity energyBE) {
                energyBE.checkAndConvert();
            }
        }
    }
    
}
