package com.example.stormroyale;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/** The match: closing storm (world border), supply drops, eliminations, victory. */
public final class StormManager {
    private static boolean active;
    private static boolean shrinking;
    private static boolean finalStage;
    private static int stage;
    private static int ticksLeft;
    private static double startRadius;
    private static int initialPlayers;
    private static int tickCounter;
    private static final Set<UUID> ALIVE = new HashSet<>();
    private static final Set<UUID> ELIMINATED = new HashSet<>();
    private static final List<BlockPos> DROPS = new ArrayList<>();

    private StormManager() {}

    private static void broadcast(MinecraftServer server, Component msg) {
        server.getPlayerList().broadcastSystemMessage(msg, false);
    }

    // ------------------------------------------------------------ commands
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("storm")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("start")
                        .executes(c -> start(c.getSource(), 300))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(20, 3000))
                                .executes(c -> start(c.getSource(), IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("stop").executes(c -> {
                    stop(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal("Storm stopped."), true);
                    return 1;
                }))
                .then(Commands.literal("scatter").executes(c -> scatter(c.getSource())))
                .then(Commands.literal("status").executes(c -> status(c.getSource()))));
        event.getDispatcher().register(Commands.literal("supplydrop")
                .requires(s -> s.hasPermission(2))
                .executes(c -> dropNearPlayer(c.getSource())));
    }

    private static int start(CommandSourceStack src, int radius) {
        MinecraftServer server = src.getServer();
        ServerLevel ow = server.overworld();
        WorldBorder wb = ow.getWorldBorder();
        Vec3 pos = src.getPosition();
        wb.setCenter(pos.x, pos.z);
        wb.setSize(radius * 2.0);
        wb.setDamageSafeZone(0.0);
        wb.setDamagePerBlock(0.3);
        wb.setWarningBlocks(8);

        ALIVE.clear();
        ELIMINATED.clear();
        DROPS.clear();
        for (ServerPlayer p : ow.players()) {
            if (!p.isSpectator() && !p.isCreative()) ALIVE.add(p.getUUID());
        }
        initialPlayers = ALIVE.size();
        active = true;
        shrinking = false;
        finalStage = false;
        stage = 0;
        startRadius = radius;
        ticksLeft = StormConfig.WAIT_SECONDS.get() * 20;
        tickCounter = 0;

        broadcast(server, Component.literal("THE STORM HAS FORMED! Safe zone radius: " + radius + " blocks. "
                + ALIVE.size() + " players in the match.").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        spawnRandomDrop(ow, wb);
        return 1;
    }

    private static int scatter(CommandSourceStack src) {
        if (!active) {
            src.sendFailure(Component.literal("No match running. Use /storm start first."));
            return 0;
        }
        ServerLevel ow = src.getServer().overworld();
        WorldBorder wb = ow.getWorldBorder();
        double radius = wb.getSize() / 2.0 * 0.85;
        int n = 0;
        for (ServerPlayer p : ow.players()) {
            if (!ALIVE.contains(p.getUUID())) continue;
            double ang = ow.random.nextDouble() * Math.PI * 2;
            double r = radius * Math.sqrt(ow.random.nextDouble());
            double x = wb.getCenterX() + Math.cos(ang) * r;
            double z = wb.getCenterZ() + Math.sin(ang) * r;
            BlockPos top = ow.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, 0, z));
            p.teleportTo(ow, x, Math.min(ow.getMaxBuildHeight() - 5, top.getY() + 120), z, p.getYRot(), 20.0F);
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, false));
            p.sendSystemMessage(Component.literal("Jump to deploy your glider!").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            n++;
        }
        final int count = n;
        src.sendSuccess(() -> Component.literal("Scattered " + count + " players."), true);
        return n;
    }

    private static int status(CommandSourceStack src) {
        if (!active) {
            src.sendSuccess(() -> Component.literal("No match running."), false);
            return 0;
        }
        WorldBorder wb = src.getServer().overworld().getWorldBorder();
        src.sendSuccess(() -> Component.literal("Stage " + stage + " | radius " + (int) (wb.getSize() / 2)
                + " | " + (shrinking ? "closing" : "waiting") + " | " + ALIVE.size() + " alive"), false);
        return 1;
    }

    private static int dropNearPlayer(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ServerLevel level = p.serverLevel();
        double ang = level.random.nextDouble() * Math.PI * 2;
        double r = 20 + level.random.nextDouble() * 20;
        spawnDrop(level, (int) (p.getX() + Math.cos(ang) * r), (int) (p.getZ() + Math.sin(ang) * r));
        return 1;
    }

    // ------------------------------------------------------------ match loop
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        tickCounter++;
        ServerLevel ow = server.overworld();
        if (tickCounter % 10 == 0) tickDrops(ow);
        if (!active) return;

        WorldBorder wb = ow.getWorldBorder();
        if (ticksLeft > 0) ticksLeft--;
        if (tickCounter % 20 == 0) announce(ow, wb);
        if (ticksLeft > 0) return;
        if (!shrinking) beginShrink(server, ow, wb);
        else endShrink(server, ow, wb);
    }

    private static void announce(ServerLevel ow, WorldBorder wb) {
        int radius = (int) (wb.getSize() / 2);
        String text;
        if (finalStage) text = "FINAL CIRCLE  |  radius " + radius;
        else if (shrinking) text = "STORM CLOSING  |  radius " + radius + "  |  " + ticksLeft / 20 + "s";
        else text = "Storm closes in " + ticksLeft / 20 + "s  |  radius " + radius;
        for (ServerPlayer p : ow.players()) {
            if (!ALIVE.contains(p.getUUID())) continue;
            boolean out = !wb.isWithinBounds(p.blockPosition());
            p.displayClientMessage(Component.literal(out ? "YOU ARE IN THE STORM!  " + text : text + "  |  " + ALIVE.size() + " alive")
                    .withStyle(out ? ChatFormatting.RED : ChatFormatting.AQUA), true);
        }
    }

    private static void beginShrink(MinecraftServer server, ServerLevel ow, WorldBorder wb) {
        stage++;
        double target = Math.max(3.0, startRadius * Math.pow(0.6, stage));
        int seconds = StormConfig.SHRINK_SECONDS.get();
        wb.lerpSizeBetween(wb.getSize(), target * 2.0, seconds * 1000L);
        wb.setDamagePerBlock(0.3 + stage * 0.5);
        shrinking = true;
        ticksLeft = seconds * 20;
        broadcast(server, Component.literal("THE STORM IS CLOSING! Safe zone shrinking to " + (int) target + " blocks.")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        spawnRandomDrop(ow, wb);
    }

    private static void endShrink(MinecraftServer server, ServerLevel ow, WorldBorder wb) {
        shrinking = false;
        if (startRadius * Math.pow(0.6, stage + 1) < 3.0 || startRadius * Math.pow(0.6, stage) <= 3.5) {
            finalStage = true;
            ticksLeft = Integer.MAX_VALUE;
            broadcast(server, Component.literal("FINAL CIRCLE! Last one standing wins.").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        } else {
            ticksLeft = StormConfig.WAIT_SECONDS.get() * 20;
            broadcast(server, Component.literal("The storm has paused. Radius " + (int) (wb.getSize() / 2) + ".")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    public static void stop(MinecraftServer server) {
        ServerLevel ow = server.overworld();
        WorldBorder wb = ow.getWorldBorder();
        wb.setCenter(0, 0);
        wb.setSize(5.9999968E7);
        wb.setDamagePerBlock(0.2);
        wb.setDamageSafeZone(5.0);
        wb.setWarningBlocks(5);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (ELIMINATED.contains(p.getUUID())) p.setGameMode(GameType.SURVIVAL);
        }
        active = false;
        shrinking = false;
        finalStage = false;
        ALIVE.clear();
        ELIMINATED.clear();
    }

    // ------------------------------------------------------------ eliminations
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!active || !(e.getEntity() instanceof ServerPlayer p)) return;
        if (!ALIVE.remove(p.getUUID())) return;
        ELIMINATED.add(p.getUUID());
        MinecraftServer server = p.getServer();
        if (server == null) return;
        broadcast(server, Component.literal(p.getName().getString() + " was eliminated! " + ALIVE.size() + " left.")
                .withStyle(ChatFormatting.RED));
        checkWin(server);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (active && e.getEntity() instanceof ServerPlayer sp && ELIMINATED.contains(sp.getUUID())) {
            sp.setGameMode(GameType.SPECTATOR);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent e) {
        active = false;
        shrinking = false;
        finalStage = false;
        ALIVE.clear();
        ELIMINATED.clear();
        DROPS.clear();
    }

    private static void checkWin(MinecraftServer server) {
        if (!active) return;
        if (ALIVE.isEmpty()) {
            broadcast(server, Component.literal("Nobody survived the storm.").withStyle(ChatFormatting.GRAY));
            stop(server);
            return;
        }
        if (initialPlayers >= 2 && ALIVE.size() == 1) {
            UUID id = ALIVE.iterator().next();
            ServerPlayer w = server.getPlayerList().getPlayer(id);
            if (w != null) {
                w.connection.send(new ClientboundSetTitleTextPacket(
                        Component.literal("VICTORY ROYALE!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
                broadcast(server, Component.literal(w.getName().getString() + " got the VICTORY ROYALE!")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                w.serverLevel().sendParticles(ParticleTypes.FIREWORK, w.getX(), w.getY() + 1, w.getZ(), 80, 1.0, 1.5, 1.0, 0.2);
            }
            stop(server);
        }
    }

    // ------------------------------------------------------------ supply drops
    private static void spawnRandomDrop(ServerLevel ow, WorldBorder wb) {
        double ang = ow.random.nextDouble() * Math.PI * 2;
        double r = wb.getSize() / 2.0 * 0.7 * Math.sqrt(ow.random.nextDouble());
        spawnDrop(ow, (int) (wb.getCenterX() + Math.cos(ang) * r), (int) (wb.getCenterZ() + Math.sin(ang) * r));
    }

    public static void spawnDrop(ServerLevel level, int x, int z) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        level.setBlock(top, Blocks.CHEST.defaultBlockState(), 3);
        if (level.getBlockEntity(top) instanceof ChestBlockEntity chest) {
            chest.setCustomName(Component.literal("Supply Drop").withStyle(ChatFormatting.GOLD));
            fillChest(chest, level.random);
        }
        DROPS.add(top);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(Vec3.atBottomCenterOf(top));
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        MinecraftServer server = level.getServer();
        if (server != null) {
            broadcast(server, Component.literal("A supply drop landed at X: " + top.getX() + "  Z: " + top.getZ() + "!")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    private static void tickDrops(ServerLevel ow) {
        if (DROPS.isEmpty()) return;
        Iterator<BlockPos> it = DROPS.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (!ow.isLoaded(pos)) continue;
            if (!ow.getBlockState(pos).is(Blocks.CHEST) || !(ow.getBlockEntity(pos) instanceof ChestBlockEntity ch) || ch.isEmpty()) {
                it.remove();
                continue;
            }
            for (int y = 0; y < 40; y += 2) {
                ow.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + y, pos.getZ() + 0.5, 1, 0.05, 0, 0.05, 0.0);
            }
        }
    }

    private static void fillChest(ChestBlockEntity chest, RandomSource r) {
        List<ItemStack> loot = new ArrayList<>();
        int guns = 2 + r.nextInt(2);
        for (int i = 0; i < guns; i++) {
            int roll = r.nextInt(100);
            Item gun = roll < 32 ? ModItems.ASSAULT_RIFLE.get()
                    : roll < 62 ? ModItems.PUMP_SHOTGUN.get()
                    : roll < 82 ? ModItems.SNIPER_RIFLE.get()
                    : ModItems.ROCKET_LAUNCHER.get();
            loot.add(new ItemStack(gun));
        }
        loot.add(new ItemStack(ModItems.BULLET.get(), 32 + r.nextInt(33)));
        loot.add(new ItemStack(Items.FIREWORK_ROCKET, 2 + r.nextInt(3)));
        loot.add(new ItemStack(ModItems.SHIELD_POTION.get(), 1 + r.nextInt(2)));
        loot.add(new ItemStack(ModItems.MINI_SHIELD.get(), 2 + r.nextInt(3)));
        loot.add(new ItemStack(ModItems.MEDKIT.get(), 1));
        loot.add(new ItemStack(ModItems.BANDAGE.get(), 4 + r.nextInt(5)));
        loot.add(new ItemStack(Items.OAK_PLANKS, 64));
        loot.add(new ItemStack(Items.COBBLESTONE, 64));
        loot.add(new ItemStack(Items.BRICKS, 48));
        loot.add(new ItemStack(Items.GOLDEN_APPLE, 2));

        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 27; i++) slots.add(i);
        Collections.shuffle(slots, new java.util.Random(r.nextLong()));
        for (int i = 0; i < loot.size() && i < slots.size(); i++) {
            chest.setItem(slots.get(i), loot.get(i));
        }
    }
}
