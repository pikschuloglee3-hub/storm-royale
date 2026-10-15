package com.example.stormroyale;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod(StormRoyaleMod.MODID)
public class StormRoyaleMod {
    public static final String MODID = "storm_royale";
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public StormRoyaleMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(bus);
        bus.addListener(StormRoyaleMod::addToCreativeTabs);

        CHANNEL.registerMessage(0, BuildPacket.class, BuildPacket::encode, BuildPacket::decode, BuildPacket::handle);
        CHANNEL.registerMessage(1, GlidePacket.class, GlidePacket::encode, GlidePacket::decode, GlidePacket::handle);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, StormConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(StormManager.class);
        MinecraftForge.EVENT_BUS.register(BuildSystem.class);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.COMBAT) {
            e.accept(ModItems.ASSAULT_RIFLE.get());
            e.accept(ModItems.PUMP_SHOTGUN.get());
            e.accept(ModItems.SNIPER_RIFLE.get());
            e.accept(ModItems.ROCKET_LAUNCHER.get());
            e.accept(ModItems.BULLET.get());
        } else if (e.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            e.accept(ModItems.BANDAGE.get());
            e.accept(ModItems.MEDKIT.get());
            e.accept(ModItems.MINI_SHIELD.get());
            e.accept(ModItems.SHIELD_POTION.get());
        }
    }
}
