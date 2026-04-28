package com.xlxyvergil.generalenergy.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class GeneralEnergyConfig {
    
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final CommonConfig COMMON;
    
    static {
        final var pair = new ForgeConfigSpec.Builder()
            .configure(CommonConfig::new);
        COMMON_SPEC = pair.getRight();
        COMMON = pair.getLeft();
    }
    
    public static class CommonConfig {
        
        // AE2 转 FE 转换器配置
        public final ForgeConfigSpec.IntValue aeToFeMaxAEPower;
        public final ForgeConfigSpec.DoubleValue aeToFeBaseConsumption;
        public final ForgeConfigSpec.IntValue aeToFeMaxFEOutputPerConverter;
        public final ForgeConfigSpec.DoubleValue aeToFeCapacityPerConverter;
        
        // RS 转 FE 转换器配置
        public final ForgeConfigSpec.IntValue rsToFeCapacityPerConverter;
        public final ForgeConfigSpec.IntValue rsToFeEnergyUsage;
        public final ForgeConfigSpec.IntValue rsToFeMaxFETransfer;
        
        public CommonConfig(ForgeConfigSpec.Builder builder) {
            builder.push("ae2_to_fe_converter");
            
            aeToFeMaxAEPower = builder
                .comment("AE缓存上限（单位：AE）", "Maximum AE cache capacity (unit: AE)")
                .translation("config.generalenergy.aeToFeMaxAEPower")
                .defineInRange("maxAEPower", 50000, 1000, Integer.MAX_VALUE);
            
            aeToFeBaseConsumption = builder
                .comment("基础AE消耗（单位：AE/t）", "Base AE consumption per tick (unit: AE/t)")
                .translation("config.generalenergy.aeToFeBaseConsumption")
                .defineInRange("baseConsumption", 100.0, 1.0, Double.MAX_VALUE);
            
            aeToFeMaxFEOutputPerConverter = builder
                .comment("每个转换器最大FE输出（单位：FE/t）", "Maximum FE output per converter (unit: FE/t)")
                .translation("config.generalenergy.aeToFeMaxFEOutputPerConverter")
                .defineInRange("maxFEOutputPerConverter", 80000, 1000, Integer.MAX_VALUE);
            
            aeToFeCapacityPerConverter = builder
                .comment("每个转换器增加的Controller AE容量（单位：AE）", "Additional Controller AE capacity per converter (unit: AE)")
                .translation("config.generalenergy.aeToFeCapacityPerConverter")
                .defineInRange("capacityPerConverter", 50000.0, 1000.0, Double.MAX_VALUE);
            
            builder.pop();
            
            builder.push("rs_to_fe_converter");
            
            rsToFeCapacityPerConverter = builder
                .comment("每个RS转换器增加的FE容量", "Additional FE capacity per RS converter")
                .translation("config.generalenergy.rsToFeCapacityPerConverter")
                .defineInRange("capacityPerConverter", 500000, 1000, Integer.MAX_VALUE);
            
            rsToFeEnergyUsage = builder
                .comment("RS转换器每tick消耗的RS能量", "RS energy consumed per tick by RS converter")
                .translation("config.generalenergy.rsToFeEnergyUsage")
                .defineInRange("energyUsage", 10, 0, Integer.MAX_VALUE);
            
            rsToFeMaxFETransfer = builder
                .comment("RS转换器最大FE传输速率（单位：FE/t）", "Maximum FE transfer rate of RS converter (unit: FE/t)")
                .translation("config.generalenergy.rsToFeMaxFETransfer")
                .defineInRange("maxFETransfer", 80000, 1000, Integer.MAX_VALUE);
            
            builder.pop();
        }
    }
}
