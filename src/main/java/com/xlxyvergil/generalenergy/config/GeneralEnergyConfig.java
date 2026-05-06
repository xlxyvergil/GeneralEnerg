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
        public final ForgeConfigSpec.DoubleValue aeToFeBonusPerSide;
        public final ForgeConfigSpec.DoubleValue aeToFeCapacityPerConverter;
        
        // RS 转 FE 转换器配置
        public final ForgeConfigSpec.IntValue rsToFeInternalCapacity;
        public final ForgeConfigSpec.IntValue rsToFeEnergyUsage;
        public final ForgeConfigSpec.IntValue rsToFeBonusPerSide;
        public final ForgeConfigSpec.IntValue rsToFeCapacityPerConverter;
        
        public CommonConfig(ForgeConfigSpec.Builder builder) {
            builder.push("ae2_to_fe_converter");
            
            aeToFeMaxAEPower = builder
                .comment("AE缓存上限（单位：AE）", "Maximum AE cache capacity (unit: AE)")
                .translation("config.generalenergy.aeToFeMaxAEPower")
                .defineInRange("maxAEPower", 500000, 1000, Integer.MAX_VALUE);
            
            aeToFeBaseConsumption = builder
                .comment("基础AE消耗（单位：AE/t）", "Base AE consumption per tick (unit: AE/t)")
                .translation("config.generalenergy.aeToFeBaseConsumption")
                .defineInRange("baseConsumption", 100.0, 1.0, Double.MAX_VALUE);
            
            aeToFeBonusPerSide = builder
                .comment("每个连接面增加的AE抽取速率（单位：AE/t）", "Additional AE extraction rate per connected side (unit: AE/t)")
                .translation("config.generalenergy.aeToFeBonusPerSide")
                .defineInRange("bonusPerSide", 32000.0, 0.0, Double.MAX_VALUE);
            
            aeToFeCapacityPerConverter = builder
                .comment("每个转换器增加的Controller AE容量（单位：AE）", "Additional Controller AE capacity per converter (unit: AE)")
                .translation("config.generalenergy.aeToFeCapacityPerConverter")
                .defineInRange("capacityPerConverter", 500000.0, 1000.0, Double.MAX_VALUE);
            
            builder.pop();
            
            builder.push("rs_to_fe_converter");
            
            rsToFeInternalCapacity = builder
                .comment("RS转换器内部缓存容量（单位：FE）", "Internal cache capacity of RS converter (unit: FE)")
                .translation("config.generalenergy.rsToFeInternalCapacity")
                .defineInRange("internalCapacity", 1000000, 1000, Integer.MAX_VALUE);
            
            rsToFeEnergyUsage = builder
                .comment("RS转换器无外部需求时的填充速率/基础消耗（单位：FE/t）", "Fill rate when idle / base consumption (unit: FE/t)")
                .translation("config.generalenergy.rsToFeEnergyUsage")
                .defineInRange("energyUsage", 200, 0, Integer.MAX_VALUE);
            
            rsToFeBonusPerSide = builder
                .comment("每个连接面增加的FE抽取速率（单位：FE/t）", "Additional FE extraction rate per connected side (unit: FE/t)")
                .translation("config.generalenergy.rsToFeBonusPerSide")
                .defineInRange("bonusPerSide", 32000, 0, Integer.MAX_VALUE);
            
            rsToFeCapacityPerConverter = builder
                .comment("每个RS转换器增加的FE容量", "Additional FE capacity per RS converter")
                .translation("config.generalenergy.rsToFeCapacityPerConverter")
                .defineInRange("capacityPerConverter", 5000000, 1000, Integer.MAX_VALUE);
            
            builder.pop();
        }
    }
}
