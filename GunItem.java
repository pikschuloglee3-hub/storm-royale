package com.example.stormroyale;

import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Hitscan gun. Right-click to shoot (hold for full-auto guns). Uses ammo from your inventory. */
public class GunItem extends Item {
    private final Supplier<Item> ammo;
    private final int pellets;
    private final float damage;
    private final double spread;
    private final double range;
    private final int interval;
    private final int cooldown;
    private final boolean rocket;
    private final SoundEvent sound;
    private final float pitch;

    public GunItem(Properties props, Supplier<Item> ammo, int pellets, float damage, double spread, double range,
                   int interval, int cooldown, boolean rocket, SoundEvent sound, float pitch) {
        super(props);
        this.ammo = ammo;
        this.pellets = pellets;
        this.damage = damage;
        this.spread = spread;
        this.range = range;
        this.interval = interval;
        this.cooldown = cooldown;
        this.rocket = rocket;
        this.sound = sound;
        this.pitch = pitch;
    }

    private boolean hasAmmo(Player p) {
        if (p.getAbilities().instabuild) return true;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(ammo.get())) return true;
        }
        return false;
    }

    private boolean takeAmmo(Player p) {
        for (ItemStack s : p.getInventory().items) {
            if (s.is(ammo.get())) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!hasAmmo(player)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("Out of ammo!").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (interval > 0) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (!level.isClientSide) {
            fire((ServerLevel) level, player);
            player.getCooldowns().addCooldown(this, cooldown);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide || interval <= 0 || !(entity instanceof Player p)) return;
        int used = getUseDuration(stack) - remaining;
        if (used % interval == 0) {
            if (!hasAmmo(p)) {
                p.stopUsingItem();
                p.displayClientMessage(Component.literal("Out of ammo!").withStyle(ChatFormatting.RED), true);
                return;
            }
            fire((ServerLevel) level, p);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    private void fire(ServerLevel level, Player p) {
        if (!p.getAbilities().instabuild && !takeAmmo(p)) return;
        Vec3 start = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        if (rocket) {
            rocketShot(level, p, start, look);
        } else {
            for (int i = 0; i < pellets; i++) {
                Vec3 dir = look.add(
                        (level.random.nextDouble() - 0.5) * 2 * spread,
                        (level.random.nextDouble() - 0.5) * 2 * spread,
                        (level.random.nextDouble() - 0.5) * 2 * spread).normalize();
                hitscan(level, p, start, dir);
            }
        }
        Vec3 muzzle = start.add(look.scale(1.2)).add(0, -0.2, 0);
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 3, 0.05, 0.05, 0.05, 0.01);
        level.sendParticles(ParticleTypes.CRIT, muzzle.x, muzzle.y, muzzle.z, 2, 0.05, 0.05, 0.05, 0.1);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, 1.0F, pitch);
    }

    private void hitscan(ServerLevel level, Player p, Vec3 start, Vec3 dir) {
        Vec3 end = start.add(dir.scale(range));
        BlockHitResult bh = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 hit = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        AABB box = p.getBoundingBox().expandTowards(dir.scale(range)).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, p, start, hit, box,
                x -> !x.isSpectator() && x.isPickable() && x != p);
        if (eh != null) {
            hit = eh.getLocation();
            if (eh.getEntity() instanceof LivingEntity t) {
                boolean head = hit.y >= t.getEyeY() - 0.2;
                t.invulnerableTime = 0;
                t.hurt(level.damageSources().playerAttack(p), damage * (head ? 2.0F : 1.0F));
                level.sendParticles(head ? ParticleTypes.CRIT : ParticleTypes.DAMAGE_INDICATOR, hit.x, hit.y, hit.z, 4, 0.1, 0.1, 0.1, 0.1);
                if (p instanceof ServerPlayer sp) {
                    sp.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, head ? 2.0F : 1.4F);
                }
            }
        } else if (bh.getType() == HitResult.Type.BLOCK) {
            BlockState state = level.getBlockState(bh.getBlockPos());
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), hit.x, hit.y, hit.z, 6, 0.1, 0.1, 0.1, 0.1);
        }
        double dist = start.distanceTo(hit);
        for (double d = 1.5; d < dist; d += 1.5) {
            Vec3 pt = start.add(dir.scale(d));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
        }
    }

    private void rocketShot(ServerLevel level, Player p, Vec3 start, Vec3 dir) {
        Vec3 end = start.add(dir.scale(range));
        BlockHitResult bh = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 hit = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        AABB box = p.getBoundingBox().expandTowards(dir.scale(range)).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, p, start, hit, box,
                x -> !x.isSpectator() && x.isPickable() && x != p);
        if (eh != null) {
            hit = eh.getLocation();
            if (eh.getEntity() instanceof LivingEntity t) {
                t.invulnerableTime = 0;
                t.hurt(level.damageSources().playerAttack(p), damage);
            }
        }
        double dist = start.distanceTo(hit);
        for (double d = 1.0; d < dist; d += 0.8) {
            Vec3 pt = start.add(dir.scale(d));
            level.sendParticles(ParticleTypes.SMOKE, pt.x, pt.y, pt.z, 1, 0.02, 0.02, 0.02, 0.0);
            if (d % 2.4 < 0.8) level.sendParticles(ParticleTypes.FLAME, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
        }
        level.explode(p, hit.x, hit.y, hit.z, 3.0F,
                StormConfig.ROCKET_BLOCK_DAMAGE.get() ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
    }
}
