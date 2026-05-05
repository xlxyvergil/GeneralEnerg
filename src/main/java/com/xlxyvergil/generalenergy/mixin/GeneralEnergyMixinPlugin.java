package com.xlxyvergil.generalenergy.mixin;

import net.minecraftforge.fml.ModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class GeneralEnergyMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Forge 尚未初始化时，ModList.get() 可能为 null，此时默认应用所有 mixin
        var modList = ModList.get();
        if (modList == null) {
            return true;
        }
        
        // NetworkMixin 依赖 Refined Storage
        if ("com.xlxyvergil.generalenergy.mixin.NetworkMixin".equals(mixinClassName)) {
            return modList.isLoaded("refinedstorage");
        }
        
        // ControllerBlockEntityMixin 依赖 AE2
        if ("com.xlxyvergil.generalenergy.mixin.ControllerBlockEntityMixin".equals(mixinClassName)) {
            return modList.isLoaded("ae2");
        }
        
        // 其他 mixin 默认应用
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
