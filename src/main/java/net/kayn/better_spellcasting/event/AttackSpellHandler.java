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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles tracking of swings (client -> server) and casting weapon-attached spells.
 * All spells (self + target) are queued on swing.
 * Spells are executed only on LivingHurtEvent (when the attack connects).
 */
@Mod.EventBusSubscriber(modid = BetterSpellcasting.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AttackSpellHandler {
    private static final long COMBO_TIMEOUT_TICKS = 40L;
    private static final Map<UUID, ComboState> serverCombo = new HashMap<>();
    private static final Map<UUID, PendingSpell> pending = new HashMap<>();

    private static record ComboState(int index, long lastSwingTick, ResourceLocation weaponId) {}
    private static record PendingSpell(SpellAttackData data, int attackIndex) {}

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

    public static void onClientSwingFromPacket(ServerPlayer serverPlayer) {
        if (serverPlayer == null) return;
        trackSwingServerSide(serverPlayer);
    }

    @SubscribeEvent
    public static void onServerAttackEntity(AttackEntityEvent event) {
        Player p = event.getEntity();
        if (p.level().isClientSide()) return;
        if (!(p instanceof ServerPlayer serverPlayer)) return;
        trackSwingServerSide(serverPlayer);
    }

    private static void trackSwingServerSide(ServerPlayer player) {
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
            if (data != null) {
                pending.put(player.getUUID(), new PendingSpell(data, nextIndex));
            } else {
                pending.remove(player.getUUID());
            }

        } catch (Throwable ignored) {}
    }

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
            if (data != null) {
                if (data.isSelfCast()) {
                    SpellCastHelper.castSpellFromAttack(player, data.getSpell(), data.getLevel(), player);
                } else {
                    SpellCastHelper.castSpellFromAttack(player, data.getSpell(), data.getLevel(), victim);
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void resetComboFor(Player player) {
        if (player == null) return;
        serverCombo.remove(player.getUUID());
        pending.remove(player.getUUID());
    }
}
