package com.xlxyvergil.generalenergy;

import com.mojang.logging.LogUtils;
import com.xlxyvergil.generalenergy.block.AE2ToFEConverterBlock;
import com.xlxyvergil.generalenergy.block.AE2ToFEConverterBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;


@Mod(GeneralEnergy.MODID)
public class GeneralEnergy {
    public static final String MODID = "generalenergy";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<Block> AE2_TO_FE_CONVERTER = BLOCKS.register("ae2_to_fe_converter",
            () -> new AE2ToFEConverterBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0F)
                .lightLevel(state -> state.getValue(AE2ToFEConverterBlock.ENERGY_STATE) == AE2ToFEConverterBlock.EnergyState.ONLINE ? 15 : 0)));

    // BlockEntityType - 使用Object避免类加载时解析AE2类型
    public static final Object AE2_TO_FE_CONVERTER_ENTITY;
    
    static {
        if (net.minecraftforge.fml.ModList.get().isLoaded("appliedenergistics2")) {
            AE2_TO_FE_CONVERTER_ENTITY = BLOCK_ENTITIES.register("ae2_to_fe_converter",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new AE2ToFEConverterBlockEntity(null, pos, state),
                            AE2_TO_FE_CONVERTER.get()
                    ).build(null));
        } else {
            AE2_TO_FE_CONVERTER_ENTITY = null;
        }
    }

    public static final RegistryObject<Item> AE2_TO_FE_CONVERTER_ITEM = ITEMS.register("ae2_to_fe_converter",
            () -> new BlockItem(AE2_TO_FE_CONVERTER.get(), new Item.Properties()));

    public GeneralEnergy() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("General Energy mod loaded!");
    }

}
