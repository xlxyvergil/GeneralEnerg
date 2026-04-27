package com.xlxyvergil.generalenergy.mixin;

import appeng.blockentity.networking.ControllerBlockEntity;
import com.xlxyvergil.generalenergy.api.IControllerBaseCapacity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ControllerBlockEntity.class, remap = false)
public abstract class ControllerBlockEntityMixin implements IControllerBaseCapacity {

    @Unique
    private double generalenergy$baseCapacity = 8000.0; // 默认值，与 AE2 硬编码一致

    /**
     * 在 Controller 构造函数末尾保存基础容量
     */
    @Inject(
        method = "<init>",
        at = @At("TAIL")
    )
    private void onConstructed(CallbackInfo ci) {
        // 此时 setInternalMaxPower(8000) 已执行，读取当前值作为基础容量
        this.generalenergy$baseCapacity = ((ControllerBlockEntity)(Object)this).getInternalMaxPower();
    }

    /**
     * 暴露基础容量（供外部调用）
     */
    public double generalenergy$getBaseCapacity() {
        return this.generalenergy$baseCapacity;
    }
}
