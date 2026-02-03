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
import io.redspace.ironsspellbooks.setup.Messages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Utility class for casting weapon-attached spells.
 * Supports optional free casts that do not consume mana.
 */
public class SpellCastHelper {

    /**
     * Casts a spell from an attack.
     *
     * @param caster     the entity performing the cast
     * @param spell      the spell to cast
     * @param spellLevel spell level
     * @param target     the target entity (can be caster for self)
     * @param freeCast   if true, bypasses MagicData.initiateCast to avoid charging mana
     */
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
                Messages.sendToPlayer(new SyncTargetingDataPacket(target, spell), serverPlayer);
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

        if (serverPlayer.isUsingItem()) serverPlayer.stopUsingItem();

        int effectiveCastTime = 0;
        if (spell.getCastType() == CastType.CONTINUOUS) {
            effectiveCastTime = spell.getEffectiveCastTime(spellLevel, serverPlayer);
        }

        magicData.initiateCast(spell, spellLevel, effectiveCastTime, CastSource.SPELLBOOK, "weapon_attack");
        magicData.setPlayerCastingItem(ItemStack.EMPTY);

        spell.onServerPreCast(serverPlayer.level(), spellLevel, serverPlayer, magicData);

        Messages.sendToPlayer(new UpdateCastingStatePacket(spell.getSpellId(), spellLevel, effectiveCastTime, CastSource.SPELLBOOK, "weapon_attack"), serverPlayer);
        Messages.sendToPlayer(new OnCastStartedPacket(serverPlayer.getUUID(), spell.getSpellId(), spellLevel), serverPlayer);

        if (effectiveCastTime == 0) {
            spell.onCast(serverPlayer.level(), spellLevel, serverPlayer, CastSource.SPELLBOOK, magicData);
            Messages.sendToPlayer(new OnClientCastPacket(spell.getSpellId(), spellLevel, CastSource.SPELLBOOK, magicData.getAdditionalCastData()), serverPlayer);
        }
    }

    private static void freeCastSpellForPlayer(AbstractSpell spell, int spellLevel, ServerPlayer serverPlayer, MagicData magicData) {
        try {
            if (serverPlayer.isUsingItem()) serverPlayer.stopUsingItem();

            spell.onServerPreCast(serverPlayer.level(), spellLevel, serverPlayer, magicData);

            spell.onCast(serverPlayer.level(), spellLevel, serverPlayer, CastSource.SPELLBOOK, magicData);

            RecastInstance recastInstance = magicData.getPlayerRecasts().getRecastInstance(spell.getSpellId());
            if (recastInstance != null) {

                magicData.getPlayerRecasts().removeRecast(recastInstance, RecastResult.TIMEOUT);
            }

            spell.onServerCastComplete(serverPlayer.level(), spellLevel, serverPlayer, magicData, false);

            Messages.sendToPlayer(new OnClientCastPacket(
                    spell.getSpellId(), spellLevel, CastSource.SPELLBOOK, magicData.getAdditionalCastData()
            ), serverPlayer);

        } catch (Throwable ignored) {
        }
    }

    public static void castSpellFromAttack(LivingEntity caster, AbstractSpell spell, int spellLevel, LivingEntity target) {
        castSpellFromAttack(caster, spell, spellLevel, target, true);
    }
}