package com.xlxyvergil.generalenergy.mixin;

import com.refinedmods.refinedstorage.RS;
import com.refinedmods.refinedstorage.api.network.INetworkNodeGraphListener;
import com.refinedmods.refinedstorage.apiimpl.network.Network;
import com.refinedmods.refinedstorage.energy.BaseEnergyStorage;
import com.xlxyvergil.generalenergy.block.RSToFEConverterBlockEntity;
import com.xlxyvergil.generalenergy.block.RSToFENetworkNode;
import com.xlxyvergil.generalenergy.energy.CustomBaseEnergyStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Network.class, remap = false)
public abstract class NetworkMixin {

    @Accessor
    abstract BaseEnergyStorage getEnergy();

    /**
     * 拦截 Network 构造函数中的 new BaseEnergyStorage() 调用
     * 替换为 CustomBaseEnergyStorage（支持动态扩容）
     */
    @Redirect(
        method = "<init>",
        at = @At(value = "NEW", target = "com/refinedmods/refinedstorage/energy/BaseEnergyStorage")
    )
    private BaseEnergyStorage redirectEnergyStorageCreation(int capacity, int maxReceive, int maxExtract) {
        // 从 RS 配置文件读取初始容量
        int baseCapacity = RS.SERVER_CONFIG.getController().getCapacity();
        
        // 创建自定义的 EnergyStorage
        return new CustomBaseEnergyStorage(
            baseCapacity,
            Integer.MAX_VALUE,  // maxReceive（无限制接收）
            0                    // maxExtract（不允许外部提取，由 RS 内部管理）
        );
    }
    
    /**
     * 在 Network 构造函数末尾注入，添加节点图变更监听器
     */
    @Inject(
        method = "<init>",
        at = @At("RETURN")
    )
    private void onConstructed(CallbackInfo ci) {
        Network network = (Network)(Object)this;
        
        // 添加节点图变更监听器
        network.getNodeGraph().addListener(new INetworkNodeGraphListener() {
            @Override
            public void onChanged() {
                updateCapacity(network);
            }
        });
        
        // 立即进行一次初始化容量计算，确保现有转换器被统计
        updateCapacity(network);
    }
    
    /**
     * 根据网络中的转换器方块数量计算并更新容量
     */
    private void updateCapacity(Network network) {
        NetworkMixin mixin = (NetworkMixin)(Object)network;
        if (mixin.getEnergy() instanceof CustomBaseEnergyStorage customEnergy) {
            // 计算当前网络中转换器方块的数量
            int converterCount = 0;
            for (var entry : network.getNodeGraph().all()) {
                var level = entry.getNode().getLevel();
                if (level == null) continue;
                
                var be = level.getBlockEntity(entry.getNode().getPos());
                if (be instanceof RSToFEConverterBlockEntity) {
                    converterCount++;
                }
            }
            
            // 计算新容量
            int baseCapacity = RS.SERVER_CONFIG.getController().getCapacity();
            int newCapacity = baseCapacity + (converterCount * RSToFENetworkNode.EXTRA_CAPACITY_PER_CONVERTER);
            
            // 更新容量
            customEnergy.setCapacity(newCapacity);
            
            // 如果当前能量超过新容量，裁剪到容量上限
            if (customEnergy.getEnergyStored() > newCapacity) {
                // 直接设置能量值（绕过 canExtract 限制）
                customEnergy.setEnergy(newCapacity);
            }
        }
    }
}
