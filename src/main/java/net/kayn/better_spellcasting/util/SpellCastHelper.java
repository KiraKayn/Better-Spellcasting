package net.kayn.better_spellcasting.util;

import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import io.redspace.ironsspellbooks.capabilities.magic.RecastResult;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import io.redspace.ironsspellbooks.network.casting.OnCastStartedPacket;
import io.redspace.ironsspellbooks.network.casting.OnClientCastPacket;
import io.redspace.ironsspellbooks.network.casting.SyncTargetingDataPacket;
import io.redspace.ironsspellbooks.network.casting.UpdateCastingStatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// Utility class for casting weapon-attached spells.
public class SpellCastHelper {

    public static void castSpellFromAttack(LivingEntity caster, AbstractSpell spell, int spellLevel, LivingEntity target, boolean freeCast) {
        if (caster.level().isClientSide()) return;

        MagicData magicData = MagicData.getPlayerMagicData(caster);

        if (magicData.isCasting()) {
            try {
                AbstractSpell oldSpell = magicData.getCastingSpell().getSpell();
                oldSpell.onCast(caster.level(), magicData.getCastingSpellLevel(), caster, magicData.getCastSource(), magicData);
                oldSpell.onServerCastComplete(caster.level(), magicData.getCastingSpellLevel(), caster, magicData, false);
            } catch (Throwable ignored) {
            }

            magicData.resetCastingState();
            magicData = MagicData.getPlayerMagicData(caster);
        }

        if (target != null) {
            magicData.setAdditionalCastData(new TargetEntityCastData(target));

            if (caster instanceof ServerPlayer serverPlayer && spell.getCastType() != CastType.INSTANT) {
                PacketDistributor.sendToPlayer(serverPlayer, new SyncTargetingDataPacket(target, spell));
            }
        }

        if (caster instanceof ServerPlayer serverPlayer) {
            if (freeCast) {
                freeCastSpellForPlayer(spell, spellLevel, serverPlayer, magicData);
            } else {
                castSpellForPlayer(spell, spellLevel, serverPlayer, magicData);
            }
        } else if (caster instanceof IMagicEntity magicEntity) {
            magicEntity.initiateCastSpell(spell, spellLevel);
        } else {
            if (spell.checkPreCastConditions(caster.level(), spellLevel, caster, magicData)) {
                try {
                    spell.onCast(caster.level(), spellLevel, caster, CastSource.SPELLBOOK, magicData);
                    spell.onServerCastComplete(caster.level(), spellLevel, caster, magicData, false);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void castSpellForPlayer(AbstractSpell spell, int spellLevel, ServerPlayer serverPlayer, MagicData magicData) {
        if (magicData.isCasting()) return;

        if (serverPlayer.isUsingItem()) {
            serverPlayer.stopUsingItem();
        }

        int effectiveCastTime = 0;
        if (spell.getCastType() == CastType.CONTINUOUS) {
            effectiveCastTime = spell.getEffectiveCastTime(spellLevel, serverPlayer);
        }

        magicData.initiateCast(spell, spellLevel, effectiveCastTime, CastSource.SPELLBOOK, "weapon_attack");
        magicData.setPlayerCastingItem(ItemStack.EMPTY);

        spell.onServerPreCast(serverPlayer.level(), spellLevel, serverPlayer, magicData);

        PacketDistributor.sendToPlayer(serverPlayer, new UpdateCastingStatePacket(spell.getSpellId(), spellLevel, effectiveCastTime, CastSource.SPELLBOOK, "weapon_attack"));

        PacketDistributor.sendToPlayer(serverPlayer, new OnCastStartedPacket(serverPlayer.getUUID(), spell.getSpellId(), spellLevel));

        if (effectiveCastTime == 0) {
            spell.onCast(serverPlayer.level(), spellLevel, serverPlayer, CastSource.SPELLBOOK, magicData);

            PacketDistributor.sendToPlayer(serverPlayer, new OnClientCastPacket(spell.getSpellId(), spellLevel, CastSource.SPELLBOOK, magicData.getAdditionalCastData()));
        }
    }

    private static void freeCastSpellForPlayer(AbstractSpell spell, int spellLevel, ServerPlayer serverPlayer, MagicData magicData) {
        try {
            if (serverPlayer.isUsingItem()) {
                serverPlayer.stopUsingItem();
            }

            spell.onServerPreCast(serverPlayer.level(), spellLevel, serverPlayer, magicData);

            spell.onCast(serverPlayer.level(), spellLevel, serverPlayer, CastSource.SPELLBOOK, magicData);

            spell.onServerCastComplete(serverPlayer.level(), spellLevel, serverPlayer, magicData, false);

            RecastInstance recastInstance = magicData.getPlayerRecasts().getRecastInstance(spell.getSpellId());
            if (recastInstance != null) {
                magicData.getPlayerRecasts().removeRecast(recastInstance, RecastResult.USED_ALL_RECASTS);
            }

            PacketDistributor.sendToPlayer(serverPlayer, new OnClientCastPacket(spell.getSpellId(), spellLevel, CastSource.SPELLBOOK, magicData.getAdditionalCastData()));
        } catch (Throwable ignored) {
        }
    }

    public static void castSpellFromAttack(LivingEntity caster, AbstractSpell spell, int spellLevel, LivingEntity target) {
        castSpellFromAttack(caster, spell, spellLevel, target, true);
    }
}