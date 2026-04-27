package com.xlxyvergil.generalenergy.mixin;

import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.blockentity.powersink.AEBasePoweredBlockEntity;
import com.xlxyvergil.generalenergy.api.IControllerBaseCapacity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ControllerBlockEntity.class, remap = false)
public abstract class ControllerBlockEntityMixin implements IControllerBaseCapacity {

    @Unique
    private double generalenergy$baseCapacity = 0;

    /**
     * 拦截 Controller 构造函数中的 setInternalMaxPower(8000) 调用
     * 保存基础容量
     */
    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/blockentity/powersink/AEBasePoweredBlockEntity;setInternalMaxPower(D)V"
        )
    )
    private void captureBaseCapacity(AEBasePoweredBlockEntity instance, double internalMaxPower) {
        // 保存基础容量
        this.generalenergy$baseCapacity = internalMaxPower;
        
        // 调用原始方法
        instance.setInternalMaxPower(internalMaxPower);
    }

    /**
     * 暴露基础容量（供外部调用）
     */
    public double generalenergy$getBaseCapacity() {
        return this.generalenergy$baseCapacity;
    }
}
