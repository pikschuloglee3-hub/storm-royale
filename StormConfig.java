package com.example.stormroyale;

import net.minecraftforge.common.ForgeConfigSpec;

public class StormConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue BUILD_COST;
    public static final ForgeConfigSpec.BooleanValue ROCKET_BLOCK_DAMAGE;
    public static final ForgeConfigSpec.IntValue WAIT_SECONDS;
    public static final ForgeConfigSpec.IntValue SHRINK_SECONDS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        BUILD_COST = b.comment("Building pieces consumes planks / cobblestone / bricks from your inventory (not in creative).")
                .define("buildCostsMaterials", true);
        ROCKET_BLOCK_DAMAGE = b.comment("Rocket Launcher explosions destroy terrain and builds.")
                .define("rocketBlockDamage", false);
        WAIT_SECONDS = b.comment("Seconds the storm waits before each shrink.")
                .defineInRange("stageWaitSeconds", 60, 5, 600);
        SHRINK_SECONDS = b.comment("Seconds each storm shrink takes.")
                .defineInRange("stageShrinkSeconds", 45, 5, 600);
        SPEC = b.build();
    }
}
