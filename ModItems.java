package com.example.stormroyale;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, StormRoyaleMod.MODID);

    public static final RegistryObject<Item> BULLET = ITEMS.register("bullet",
            () -> new Item(new Item.Properties()));

    // heal (hp), shield (absorption hp, max 20), fullHeal
    public static final RegistryObject<Item> BANDAGE = ITEMS.register("bandage",
            () -> new ConsumableItem(new Item.Properties().stacksTo(15), 60, 6f, 0f, false));
    public static final RegistryObject<Item> MEDKIT = ITEMS.register("medkit",
            () -> new ConsumableItem(new Item.Properties().stacksTo(3), 200, 0f, 0f, true));
    public static final RegistryObject<Item> MINI_SHIELD = ITEMS.register("mini_shield",
            () -> new ConsumableItem(new Item.Properties().stacksTo(6), 40, 0f, 10f, false));
    public static final RegistryObject<Item> SHIELD_POTION = ITEMS.register("shield_potion",
            () -> new ConsumableItem(new Item.Properties().stacksTo(3), 100, 0f, 20f, false));

    // ammo, pellets, damage, spread, range, interval(>0 = full auto), cooldown, rocket, sound, pitch
    public static final RegistryObject<Item> ASSAULT_RIFLE = ITEMS.register("assault_rifle",
            () -> new GunItem(new Item.Properties().stacksTo(1), () -> ModItems.BULLET.get(),
                    1, 4.0f, 0.025, 80, 3, 0, false, SoundEvents.FIREWORK_ROCKET_BLAST, 2.0f));
    public static final RegistryObject<Item> PUMP_SHOTGUN = ITEMS.register("pump_shotgun",
            () -> new GunItem(new Item.Properties().stacksTo(1), () -> ModItems.BULLET.get(),
                    8, 3.0f, 0.10, 28, 0, 25, false, SoundEvents.GENERIC_EXPLODE, 1.8f));
    public static final RegistryObject<Item> SNIPER_RIFLE = ITEMS.register("sniper_rifle",
            () -> new GunItem(new Item.Properties().stacksTo(1), () -> ModItems.BULLET.get(),
                    1, 18.0f, 0.0, 150, 0, 45, false, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, 1.2f));
    public static final RegistryObject<Item> ROCKET_LAUNCHER = ITEMS.register("rocket_launcher",
            () -> new GunItem(new Item.Properties().stacksTo(1), () -> Items.FIREWORK_ROCKET,
                    1, 12.0f, 0.0, 120, 0, 60, true, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.8f));

    private ModItems() {}
}
