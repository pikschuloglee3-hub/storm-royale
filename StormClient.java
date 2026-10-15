package com.example.stormroyale;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public class StormClient {
    public static final String CATEGORY = "key.categories.storm_royale";
    public static final KeyMapping WALL = key("wall", GLFW.GLFW_KEY_U);
    public static final KeyMapping FLOOR = key("floor", GLFW.GLFW_KEY_I);
    public static final KeyMapping RAMP = key("ramp", GLFW.GLFW_KEY_O);
    public static final KeyMapping ROOF = key("roof", GLFW.GLFW_KEY_P);
    public static final KeyMapping MATERIAL = key("material", GLFW.GLFW_KEY_K);

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.storm_royale." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    @Mod.EventBusSubscriber(modid = StormRoyaleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBus {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(WALL);
            event.register(FLOOR);
            event.register(RAMP);
            event.register(ROOF);
            event.register(MATERIAL);
        }
    }

    @Mod.EventBusSubscriber(modid = StormRoyaleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ForgeBus {
        private static boolean gliding = false;
        private static boolean wasJump = false;

        private static void sendBuild(int piece) {
            StormRoyaleMod.CHANNEL.sendToServer(new BuildPacket(piece));
        }

        private static void setGlide(LocalPlayer p, boolean g) {
            gliding = g;
            StormRoyaleMod.CHANNEL.sendToServer(new GlidePacket(g));
            p.displayClientMessage(Component.literal(g ? "Glider deployed" : "Glider closed")
                    .withStyle(ChatFormatting.AQUA), true);
            p.playSound(g ? SoundEvents.ELYTRA_FLYING : SoundEvents.WOOL_PLACE, 0.8F, 1.2F);
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            if (p == null) {
                gliding = false;
                wasJump = false;
                return;
            }

            while (WALL.consumeClick()) sendBuild(BuildSystem.WALL);
            while (FLOOR.consumeClick()) sendBuild(BuildSystem.FLOOR);
            while (RAMP.consumeClick()) sendBuild(BuildSystem.RAMP);
            while (ROOF.consumeClick()) sendBuild(BuildSystem.ROOF);
            while (MATERIAL.consumeClick()) sendBuild(BuildSystem.CYCLE);

            boolean jump = mc.options.keyJump.isDown() && mc.screen == null;
            boolean blocked = p.onGround() || p.isInWater() || p.isInLava() || p.onClimbable()
                    || p.getAbilities().flying || p.isFallFlying() || p.isPassenger();

            if (blocked) {
                if (gliding) setGlide(p, false);
            } else if (jump && !wasJump) {
                if (gliding) setGlide(p, false);
                else if (p.fallDistance > 4.0F) setGlide(p, true);
            }

            if (gliding) {
                Vec3 look = p.getLookAngle();
                Vec3 hv = new Vec3(look.x, 0, look.z);
                hv = hv.lengthSqr() < 1.0E-4 ? Vec3.ZERO : hv.normalize();
                double pitchDown = Math.max(0.0, -look.y);
                double speed = 0.55 + pitchDown * 0.6;
                if (p.isShiftKeyDown()) speed *= 1.4;
                double vy = Math.max(-1.0, Math.min(-0.05, -0.12 + look.y * 0.35));
                p.setDeltaMovement(hv.x * speed, vy, hv.z * speed);
                p.fallDistance = 0;
                p.level().addParticle(ParticleTypes.CLOUD, p.getX(), p.getY() + 2.0, p.getZ(), 0, -0.02, 0);
            }
            wasJump = jump;
        }
    }
}
