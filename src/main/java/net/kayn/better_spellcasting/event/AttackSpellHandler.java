package net.kayn.better_spellcasting.event;

import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.kayn.better_spellcasting.BetterSpellcasting;
import net.kayn.better_spellcasting.combat.SpellAttackData;
import net.kayn.better_spellcasting.compat.SpellDataHolder;
import net.kayn.better_spellcasting.network.NetworkHandler;
import net.kayn.better_spellcasting.network.SwingMessage;
import net.kayn.better_spellcasting.util.SpellCastHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles swings & casting while honoring SpellAttackData.Trigger:
 *
 * - ON_ATTACK: cast immediately on the server when the swing is processed.
 *              if a target is known at that moment it will be used; otherwise `null` is passed
 *              (allowing projectile/forward spells to fire).
 *
 * - ON_HIT: always queue and cast only when LivingHurtEvent fires (when damage lands).
 *
 * Self-casts follow the trigger: ON_ATTACK -> cast immediately on player, ON_HIT -> cast on player when hit.
 */
@Mod.EventBusSubscriber(modid = BetterSpellcasting.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AttackSpellHandler {
    private static final long COMBO_TIMEOUT_TICKS = 40L;
    private static final ConcurrentHashMap<UUID, ComboState> serverCombo = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, PendingSpell> pending = new ConcurrentHashMap<>();

    private static record ComboState(int index, long lastSwingTick, ResourceLocation weaponId) {}
    private static record PendingSpell(SpellAttackData data, int attackIndex) {}

    // -------------------------
    // Client-side: capture swings and send to server
    // -------------------------
    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player p = event.getEntity();
        if (!p.level().isClientSide()) return;
        if (NetworkHandler.CHANNEL != null) {
            try {
                NetworkHandler.CHANNEL.sendToServer(new SwingMessage());
            } catch (Throwable ignored) {}
        }
    }

    @SubscribeEvent
    public static void onClientAttackEntity(AttackEntityEvent event) {
        Player p = event.getEntity();
        if (!p.level().isClientSide()) return;
        if (NetworkHandler.CHANNEL != null) {
            try {
                NetworkHandler.CHANNEL.sendToServer(new SwingMessage());
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Packet entrypoint called by the network handler (sender is the server player)
     */
    public static void onClientSwingFromPacket(ServerPlayer serverPlayer) {
        if (serverPlayer == null) return;
        // No known attacked entity on packet path
        trackSwingServerSide(serverPlayer, null);
    }

    /**
     * Server AttackEntityEvent: we know the targeted entity here -> pass it for possible immediate ON_ATTACK cast.
     */
    @SubscribeEvent
    public static void onServerAttackEntity(AttackEntityEvent event) {
        Player p = event.getEntity();
        if (p.level().isClientSide()) return;
        if (!(p instanceof ServerPlayer serverPlayer)) return;

        LivingEntity attacked = null;
        if (event.getTarget() instanceof LivingEntity le) attacked = le;

        trackSwingServerSide(serverPlayer, attacked);
    }

    /**
     * Central server-side combo advancement.
     * attackedEntity may be null (packet path) or non-null (server AttackEntityEvent).
     */
    private static void trackSwingServerSide(ServerPlayer player, LivingEntity attackedEntity) {
        try {
            var handStack = player.getMainHandItem();
            Item item = handStack.getItem();
            ResourceLocation weaponId = ForgeRegistries.ITEMS.getKey(item);
            if (weaponId == null) {
                serverCombo.remove(player.getUUID());
                pending.remove(player.getUUID());
                return;
            }

            WeaponAttributes attributes = WeaponRegistry.getAttributes(player.getMainHandItem());
            if (attributes == null || attributes.attacks() == null || attributes.attacks().length == 0) {
                serverCombo.remove(player.getUUID());
                pending.remove(player.getUUID());
                return;
            }

            int max = attributes.attacks().length;
            long now = player.level().getGameTime();
            ComboState state = serverCombo.get(player.getUUID());

            // ignore duplicate server calls in same tick for same weapon
            if (state != null && state.lastSwingTick == now && weaponId.equals(state.weaponId)) {
                return;
            }

            int nextIndex;
            if (state == null || !weaponId.equals(state.weaponId) || (now - state.lastSwingTick) > COMBO_TIMEOUT_TICKS) {
                nextIndex = 0;
            } else {
                nextIndex = (state.index + 1) % max;
            }

            serverCombo.put(player.getUUID(), new ComboState(nextIndex, now, weaponId));

            SpellAttackData data = SpellDataHolder.getSpellData(weaponId, nextIndex);
            if (data == null) {
                // no spell attached -> ensure no leftover pending
                pending.remove(player.getUUID());
                return;
            }

            // ===== DECIDE BASED ON TRIGGER =====
            if (data.getTrigger() == SpellAttackData.Trigger.ON_ATTACK) {
                // ON_ATTACK -> cast immediately (do NOT queue)
                if (data.isSelfCast()) {
                    // self-cast always on player
                    SpellCastHelper.castSpellFromAttack(player, data.getSpell(), data.getLevel(), player);
                } else {
                    // target-cast: if we have a target known, use it; otherwise pass null so spell uses caster/orientation
                    SpellCastHelper.castSpellFromAttack(player, data.getSpell(), data.getLevel(), attackedEntity);
                }
                // ensure no leftover pending
                pending.remove(player.getUUID());
                return;
            } else {
                // ON_HIT -> queue and cast during LivingHurtEvent
                pending.put(player.getUUID(), new PendingSpell(data, nextIndex));
            }

        } catch (Throwable t) {
            BetterSpellcasting.LOGGER.error("Error tracking swing on server", t);
        }
    }

    /**
     * When damage is applied: cast the queued ON_HIT spell (if any).
     * ON_ATTACK spells are already executed at swing time and are not present here.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;

        UUID puuid = player.getUUID();
        PendingSpell ps = pending.remove(puuid);
        if (ps == null) return;

        LivingEntity victim = event.getEntity();
        if (victim == null) return;

        try {
            SpellAttackData data = ps.data();
            if (data == null) return;

            if (data.isSelfCast()) {
                SpellCastHelper.castSpellFromAttack(player, data.getSpell(), data.getLevel(), player);
            } else {
                SpellCastHelper.castSpellFromAttack(player, data.getSpell(), data.getLevel(), victim);
            }
        } catch (Throwable t) {
            BetterSpellcasting.LOGGER.error("Error casting queued weapon spell", t);
        }
    }

    // optional helper to reset a player's combo (call on death / dimension change)
    public static void resetComboFor(Player player) {
        if (player == null) return;
        serverCombo.remove(player.getUUID());
        pending.remove(player.getUUID());
    }
}