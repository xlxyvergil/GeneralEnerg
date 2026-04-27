package com.xlxyvergil.generalenergy;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid = GeneralEnergy.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModCreativeTabs {
    
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) {
            event.register(Registries.CREATIVE_MODE_TAB, helper -> {
                CreativeModeTab tab = CreativeModeTab.builder()
                        .title(net.minecraft.network.chat.Component.translatable("itemGroup.generalenergy.general_energy_tab"))
                        .icon(() -> new net.minecraft.world.item.ItemStack(GeneralEnergy.AE2_TO_FE_CONVERTER_ITEM.get()))
                        .displayItems((parameters, output) -> {
                            output.accept(GeneralEnergy.AE2_TO_FE_CONVERTER_ITEM.get());
                        })
                        .build();
                
                helper.register(new ResourceLocation(GeneralEnergy.MODID, "general_energy_tab"), tab);
            });
        }
    }
}
