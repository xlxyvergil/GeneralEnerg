package com.xlxyvergil.generalenergy;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;


@Mod(GeneralEnergy.MODID)
public class GeneralEnergy {
    public static final String MODID = "generalenergy";
    private static final Logger LOGGER = LogUtils.getLogger();

    public GeneralEnergy() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        // 初始化注册（完全参照 Mekanism）
        ModRegistration.init(modEventBus);
        
        // 条件注册 AE2 相关内容
        ModRegistration.initAE2Content(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("General Energy mod loaded!");
    }

}
