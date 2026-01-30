package net.kayn.better_spellcasting.event;

import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.kayn.better_spellcasting.BetterSpellcasting;
import net.kayn.better_spellcasting.combat.SpellAttackData;
import net.kayn.better_spellcasting.compat.SpellDataHolder;
import net.kayn.better_spellcasting.network.SwingMessage;
import net.kayn.better_spellcasting.util.SpellCastHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles tracking of swings (client -> server) and casting weapon-attached spells.
 * All spells (self + target) are queued on swing.
 * Spells are executed only on LivingDamageEvent (when the attack connects).
 */

@EventBusSubscriber(modid = BetterSpellcasting.MOD_ID)
public class AttackSpellHandler {
    private static final long COMBO_TIMEOUT_TICKS = 40L;
    private static final ConcurrentHashMap<UUID, ComboState> serverCombo = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, PendingSpell> pending = new ConcurrentHashMap<>();

    private record ComboState(int index, long lastSwingTick, ResourceLocation weaponId) {}
    private record PendingSpell(SpellAttackData data, int attackIndex) {}

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player p = event.getEntity();
        if (!p.level().isClientSide()) return;
        try { PacketDistributor.sendToServer(new SwingMessage()); } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public static void onClientAttackEntity(AttackEntityEvent event) {
        Player p = event.getEntity();
        if (!p.level().isClientSide()) return;
        try { PacketDistributor.sendToServer(new SwingMessage()); } catch (Throwable ignored) {}
    }

    public static void onClientSwingFromPacket(ServerPlayer serverPlayer) {
        if (serverPlayer == null) return;
        trackSwingServerSide(serverPlayer, null);
    }

    @SubscribeEvent
    public static void onServerAttackEntity(AttackEntityEvent event) {
        Player p = event.getEntity();
        if (p.level().isClientSide()) return;
        if (!(p instanceof ServerPlayer serverPlayer)) return;
        LivingEntity attacked = null;
        if (event.getTarget() instanceof LivingEntity le) attacked = le;
        trackSwingServerSide(serverPlayer, attacked);
    }

    private static void trackSwingServerSide(ServerPlayer player, LivingEntity attackedEntity) {
        try {
            var handStack = player.getMainHandItem();
            Item item = handStack.getItem();
            ResourceLocation weaponId = BuiltInRegistries.ITEM.getKey(item);
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

            if (state != null && state.lastSwingTick == now && weaponId.equals(state.weaponId)) return;

            int nextIndex = (state == null || !weaponId.equals(state.weaponId) || (now - state.lastSwingTick) > COMBO_TIMEOUT_TICKS)
                    ? 0 : (state.index + 1) % max;

            serverCombo.put(player.getUUID(), new ComboState(nextIndex, now, weaponId));

            SpellAttackData data = SpellDataHolder.getSpellData(weaponId, nextIndex);
            if (data == null) {
                pending.remove(player.getUUID());
                return;
            }

            if (data.getTrigger() == SpellAttackData.Trigger.ON_ATTACK) {
                if (data.isSelfCast()) {
                    SpellCastHelper.castSpellFromAttack(player, data.getSpell(), data.getLevel(), player);
                } else {
                    SpellCastHelper.castSpellFromAttack(player, data.getSpell(), data.getLevel(), attackedEntity);
                }
                pending.remove(player.getUUID());
            } else {
                pending.put(player.getUUID(), new PendingSpell(data, nextIndex));
            }

        } catch (Throwable t) {
            BetterSpellcasting.LOGGER.error("Error tracking swing on server", t);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
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
        } catch (Throwable ignored) {}
    }

    public static void resetComboFor(Player player) {
        if (player == null) return;
        serverCombo.remove(player.getUUID());
        pending.remove(player.getUUID());
    }
}