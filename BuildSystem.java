package com.example.stormroyale;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Server-side instant building (walls, floors, ramps, roofs) plus glider bookkeeping. */
public final class BuildSystem {
    public static final int WALL = 0, FLOOR = 1, RAMP = 2, ROOF = 3, CYCLE = 4;
    private static final String MAT_KEY = "sr_material";
    private static final String GLIDE_KEY = "sr_glide";
    private static final Map<UUID, Long> LAST = new HashMap<>();

    public enum Material {
        WOOD("Wood", s -> s.is(ItemTags.PLANKS), Blocks.OAK_PLANKS, Blocks.OAK_STAIRS),
        STONE("Stone", s -> s.is(Items.COBBLESTONE), Blocks.COBBLESTONE, Blocks.COBBLESTONE_STAIRS),
        BRICK("Brick", s -> s.is(Items.BRICKS), Blocks.BRICKS, Blocks.BRICK_STAIRS);

        final String label;
        final Predicate<ItemStack> item;
        final Block block;
        final Block stairs;

        Material(String label, Predicate<ItemStack> item, Block block, Block stairs) {
            this.label = label;
            this.item = item;
            this.block = block;
            this.stairs = stairs;
        }
    }

    private BuildSystem() {}

    private static int count(Player p, Predicate<ItemStack> pred) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (!s.isEmpty() && pred.test(s)) n += s.getCount();
        }
        return n;
    }

    private static void consume(Player p, Predicate<ItemStack> pred, int amount) {
        for (ItemStack s : p.getInventory().items) {
            if (amount <= 0) break;
            if (!s.isEmpty() && pred.test(s)) {
                int take = Math.min(amount, s.getCount());
                s.shrink(take);
                amount -= take;
            }
        }
        p.getInventory().setChanged();
    }

    public static void handle(ServerPlayer p, int piece) {
        long now = p.level().getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < 3) return;
        LAST.put(p.getUUID(), now);

        CompoundTag d = p.getPersistentData();
        Material[] mats = Material.values();
        int mi = Math.floorMod(d.getInt(MAT_KEY), mats.length);

        if (piece == CYCLE) {
            mi = (mi + 1) % mats.length;
            d.putInt(MAT_KEY, mi);
            p.displayClientMessage(Component.literal("Build material: " + mats[mi].label + "  (you have "
                    + count(p, mats[mi].item) + ")").withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        Material mat = mats[mi];
        ServerLevel level = p.serverLevel();
        Direction f = p.getDirection();
        Direction right = f.getClockWise();
        BlockPos o = p.blockPosition();
        BlockState solid = mat.block.defaultBlockState();
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        String name;

        switch (piece) {
            case WALL -> {
                name = "Wall";
                BlockPos base = o.relative(f, 2);
                for (int w = -1; w <= 1; w++) {
                    for (int h = 0; h < 3; h++) {
                        positions.add(base.relative(right, w).above(h));
                        states.add(solid);
                    }
                }
            }
            case FLOOR -> {
                name = "Floor";
                BlockPos c = o.relative(f, 1).below();
                for (int w = -1; w <= 1; w++) {
                    for (int l = -1; l <= 1; l++) {
                        positions.add(c.relative(right, w).relative(f, l));
                        states.add(solid);
                    }
                }
            }
            case RAMP -> {
                name = "Ramp";
                BlockState stair = mat.stairs.defaultBlockState().setValue(StairBlock.FACING, f);
                for (int i = 1; i <= 3; i++) {
                    for (int w = -1; w <= 1; w++) {
                        positions.add(o.relative(f, i).above(i - 1).relative(right, w));
                        states.add(stair);
                    }
                }
            }
            case ROOF -> {
                name = "Roof";
                BlockPos c = o.above(3);
                for (int w = -1; w <= 1; w++) {
                    for (int l = -1; l <= 1; l++) {
                        positions.add(c.relative(right, w).relative(f, l));
                        states.add(solid);
                    }
                }
            }
            default -> {
                return;
            }
        }

        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            if (level.isInWorldBounds(pos) && level.getBlockState(pos).canBeReplaced()) idx.add(i);
        }
        if (idx.isEmpty()) {
            p.displayClientMessage(Component.literal("No room to build here.").withStyle(ChatFormatting.GRAY), true);
            return;
        }

        int cost = idx.size();
        boolean free = p.isCreative() || !StormConfig.BUILD_COST.get();
        if (!free) {
            int have = count(p, mat.item);
            if (have < cost) {
                p.displayClientMessage(Component.literal("Need " + cost + " " + mat.label + " (you have " + have + ")")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            consume(p, mat.item, cost);
        }

        for (int i : idx) level.setBlock(positions.get(i), states.get(i), 3);
        BlockPos first = positions.get(idx.get(0));
        level.playSound(null, first, solid.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.2F);
        level.sendParticles(ParticleTypes.CLOUD, first.getX() + 0.5, first.getY() + 0.5, first.getZ() + 0.5, 6, 0.8, 0.5, 0.8, 0.02);
        p.displayClientMessage(Component.literal(name + " (" + mat.label + ")" + (free ? "" : "  -" + cost))
                .withStyle(ChatFormatting.AQUA), true);
    }

    // ---------------------------------------------------------------- glider
    public static void setGliding(ServerPlayer p, boolean gliding) {
        p.getPersistentData().putBoolean(GLIDE_KEY, gliding);
        if (gliding) p.fallDistance = 0;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp)) return;
        CompoundTag d = sp.getPersistentData();
        if (!d.getBoolean(GLIDE_KEY)) return;
        if (sp.onGround() || sp.isInWater() || sp.isInLava() || sp.isFallFlying() || sp.getAbilities().flying) {
            d.putBoolean(GLIDE_KEY, false);
            return;
        }
        sp.fallDistance = 0;
        if (sp.tickCount % 3 == 0) {
            ServerLevel level = sp.serverLevel();
            Vec3 look = sp.getLookAngle();
            Vec3 side = new Vec3(-look.z, 0, look.x);
            if (side.lengthSqr() > 1.0E-4) {
                side = side.normalize();
                for (int i = 1; i <= 3; i++) {
                    level.sendParticles(ParticleTypes.END_ROD, sp.getX() + side.x * i * 0.6, sp.getY() + 1.9, sp.getZ() + side.z * i * 0.6, 1, 0, 0, 0, 0);
                    level.sendParticles(ParticleTypes.END_ROD, sp.getX() - side.x * i * 0.6, sp.getY() + 1.9, sp.getZ() - side.z * i * 0.6, 1, 0, 0, 0, 0);
                }
            }
        }
    }
}
