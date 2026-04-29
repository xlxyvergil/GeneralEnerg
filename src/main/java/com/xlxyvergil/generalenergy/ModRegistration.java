package com.xlxyvergil.generalenergy;

import com.mojang.logging.LogUtils;
import com.xlxyvergil.generalenergy.block.AE2ToFEConverterBlock;
import com.xlxyvergil.generalenergy.block.AE2ToFEConverterBlockEntity;
import com.xlxyvergil.generalenergy.block.EnergyInterfaceBlock;
import com.xlxyvergil.generalenergy.block.RSToFEConverterBlock;
import com.xlxyvergil.generalenergy.block.RSToFEConverterBlockEntity;
import com.xlxyvergil.generalenergy.block.RSToFENetworkNode;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

public class ModRegistration {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // DeferredRegister
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, GeneralEnergy.MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, GeneralEnergy.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, GeneralEnergy.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GeneralEnergy.MODID);
    
    // RegistryObject 引用 - 图标物品（始终存在）
    public static final RegistryObject<Item> CREATIVE_TAB_ICON = ITEMS.register("creative_tab_icon", () -> new Item(new Item.Properties()));
    
    // 基础方块 - 能量接口（始终存在）
    public static final RegistryObject<Block> ENERGY_INTERFACE = BLOCKS.register("energy_interface", () -> new EnergyInterfaceBlock(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0F)
    ));
    
    public static final RegistryObject<Item> ENERGY_INTERFACE_ITEM = ITEMS.register("energy_interface", () -> new BlockItem(ENERGY_INTERFACE.get(), new Item.Properties()));
    
    // 创造模式标签页
    public static final RegistryObject<CreativeModeTab> GENERAL_ENERGY_TAB = CREATIVE_MODE_TABS.register("general_energy_tab", () -> 
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.generalenergy.general_energy_tab"))
            .icon(() -> new ItemStack(CREATIVE_TAB_ICON.get()))
            .displayItems((parameters, output) -> {
                // 由 BuildCreativeModeTabContentsEvent 动态添加物品
            })
            .build()
    );
    
    // AE2 相关 - 条件注册
    public static RegistryObject<Block> AE2_TO_FE_CONVERTER;
    public static RegistryObject<Item> AE2_TO_FE_CONVERTER_ITEM;
    public static RegistryObject<BlockEntityType<?>> AE2_TO_FE_CONVERTER_ENTITY;
    
    // RS 相关 - 条件注册
    public static RegistryObject<Block> RS_TO_FE_CONVERTER;
    public static RegistryObject<Item> RS_TO_FE_CONVERTER_ITEM;
    public static RegistryObject<BlockEntityType<?>> RS_TO_FE_CONVERTER_ENTITY;
    
    public static void init(IEventBus modEventBus) {
        // 注册所有 DeferredRegister
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        
        // 完全参照 Mekanism: 注册 BuildCreativeModeTabContentsEvent 监听器
        modEventBus.addListener(ModRegistration::onBuildCreativeTab);
    }
    
    private static void onBuildCreativeTab(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) {
        // 动态添加物品到创造模式标签页
        if (event.getTabKey() == GENERAL_ENERGY_TAB.getKey()) {
            // 添加图标物品（标签页必须至少有一个物品才能显示）
            event.accept(CREATIVE_TAB_ICON.get());
            
            // 如果 AE2 已加载，添加 AE2 相关物品
            if (AE2_TO_FE_CONVERTER_ITEM != null) {
                event.accept(AE2_TO_FE_CONVERTER_ITEM.get());
            }
            
            // 如果 RS 已加载，添加 RS 相关物品
            if (RS_TO_FE_CONVERTER_ITEM != null) {
                event.accept(RS_TO_FE_CONVERTER_ITEM.get());
            }
            
            // 添加基础方块（始终显示）
            event.accept(ENERGY_INTERFACE_ITEM.get());
        }
    }
    
    public static void initAE2Content(IEventBus modEventBus) {
        // 检查 AE2 是否加载，条件注册 AE2 相关方块和物品
        if (!ModList.get().isLoaded("ae2")) {
            LOGGER.warn("AE2 not loaded, skipping AE2 content registration");
            return;
        }
        
        LOGGER.info("AE2 detected, registering AE2 content");
        
        AE2_TO_FE_CONVERTER = BLOCKS.register("ae2_to_fe_converter", () -> new AE2ToFEConverterBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0F)
                .lightLevel(state -> state.getValue(AE2ToFEConverterBlock.ENERGY_STATE) == AE2ToFEConverterBlock.EnergyState.ONLINE ? 15 : 0)
        ));
        
        AE2_TO_FE_CONVERTER_ITEM = ITEMS.register("ae2_to_fe_converter", () -> new BlockItem(AE2_TO_FE_CONVERTER.get(), new Item.Properties()));
        
        AE2_TO_FE_CONVERTER_ENTITY = BLOCK_ENTITIES.register("ae2_to_fe_converter", () -> BlockEntityType.Builder.of(
            AE2ToFEConverterBlockEntity::new,
            AE2_TO_FE_CONVERTER.get()
        ).build(null));
        
        LOGGER.info("AE2 content registered successfully");
    }
    
    public static void initRSContent(IEventBus modEventBus) {
        // 检查 RS 是否加载，条件注册 RS 相关方块和物品
        if (!ModList.get().isLoaded("refinedstorage")) {
            LOGGER.warn("RS not loaded, skipping RS content registration");
            return;
        }
        
        LOGGER.info("RS detected, registering RS content");
        
        // 注册 RS NetworkNode
        try {
            com.refinedmods.refinedstorage.apiimpl.API.instance().getNetworkNodeRegistry().add(
                RSToFENetworkNode.ID,
                (tag, world, pos) -> new RSToFENetworkNode(world, pos)
            );
            LOGGER.info("RS NetworkNode registered successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to register RS NetworkNode", e);
        }
        
        RS_TO_FE_CONVERTER = BLOCKS.register("rs_to_fe_converter", () -> new RSToFEConverterBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0F)
                .lightLevel(state -> state.getValue(RSToFEConverterBlock.ENERGY_STATE) == RSToFEConverterBlock.EnergyState.ONLINE ? 15 : 0)
        ));
        
        RS_TO_FE_CONVERTER_ITEM = ITEMS.register("rs_to_fe_converter", () -> new BlockItem(RS_TO_FE_CONVERTER.get(), new Item.Properties()));
        
        RS_TO_FE_CONVERTER_ENTITY = BLOCK_ENTITIES.register("rs_to_fe_converter", () -> BlockEntityType.Builder.of(
            RSToFEConverterBlockEntity::new,
            RS_TO_FE_CONVERTER.get()
        ).build(null));
        
        LOGGER.info("RS content registered successfully");
    }
}
