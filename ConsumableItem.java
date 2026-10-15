package com.example.stormroyale;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Hold right-click to use. Heals and/or adds a shield (golden absorption hearts, max 20 hp). */
public class ConsumableItem extends Item {
    private static final float MAX_SHIELD = 20f;
    private final int useTicks;
    private final float heal;
    private final float shield;
    private final boolean fullHeal;

    public ConsumableItem(Properties props, int useTicks, float heal, float shield, boolean fullHeal) {
        super(props);
        this.useTicks = useTicks;
        this.heal = heal;
        this.shield = shield;
        this.fullHeal = fullHeal;
    }

    private boolean needed(Player p) {
        boolean healsNow = (fullHeal || heal > 0) && p.getHealth() < p.getMaxHealth();
        boolean shieldsNow = shield > 0 && p.getAbsorptionAmount() < MAX_SHIELD;
        return healsNow || shieldsNow;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!needed(player)) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player p) {
            if (fullHeal) p.setHealth(p.getMaxHealth());
            else if (heal > 0) p.heal(heal);
            if (shield > 0) p.setAbsorptionAmount(Math.min(MAX_SHIELD, p.getAbsorptionAmount() + shield));
            level.playSound(null, p.blockPosition(), shield > 0 ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.ENCHANTMENT_TABLE_USE,
                    SoundSource.PLAYERS, 1.0F, 1.4F);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(shield > 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.HEART,
                        p.getX(), p.getY() + 1.0, p.getZ(), 15, 0.4, 0.6, 0.4, 0.1);
            }
            if (!p.getAbilities().instabuild) stack.shrink(1);
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return useTicks;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return shield > 0 ? UseAnim.DRINK : UseAnim.EAT;
    }
}
