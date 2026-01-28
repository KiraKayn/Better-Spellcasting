package net.kayn.better_spellcasting.compat;

import net.kayn.better_spellcasting.combat.SpellAttackData;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores spell data for weapon attacks
 */
public class SpellDataHolder {

    private static final Map<ResourceLocation, Map<Integer, SpellAttackData>> WEAPON_SPELL_DATA = new HashMap<>();

    public static void registerSpellData(ResourceLocation weaponId, int attackIndex, SpellAttackData data) {
        WEAPON_SPELL_DATA.computeIfAbsent(weaponId, k -> new HashMap<>()).put(attackIndex, data);
    }

    public static void register(ResourceLocation weaponId, int attackIndex, SpellAttackData data) {
        registerSpellData(weaponId, attackIndex, data);
    }

    public static SpellAttackData getSpellData(ResourceLocation weaponId, int attackIndex) {
        if (weaponId == null) {
            return null;
        }

        Map<Integer, SpellAttackData> attackMap = WEAPON_SPELL_DATA.get(weaponId);
        if (attackMap == null) {
            return null;
        }

        return attackMap.get(attackIndex);
    }

    public static SpellAttackData get(ResourceLocation weaponId, int attackIndex) {
        return getSpellData(weaponId, attackIndex);
    }

    public static boolean contains(ResourceLocation weaponId) {
        return WEAPON_SPELL_DATA.containsKey(weaponId);
    }

    public static void clear() {
        WEAPON_SPELL_DATA.clear();
    }
}