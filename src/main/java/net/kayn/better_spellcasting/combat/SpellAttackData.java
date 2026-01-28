package net.kayn.better_spellcasting.combat;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;

// Data class that holds spell information for a weapon attack

public class SpellAttackData {
    private final AbstractSpell spell;
    private final int level;
    private final String targetType;

    public SpellAttackData(AbstractSpell spell, int level, String targetType) {
        this.spell = spell;
        this.level = level;
        this.targetType = targetType;
    }

    public AbstractSpell getSpell() {
        return spell;
    }

    public int getLevel() {
        return level;
    }

    public String getTargetType() {
        return targetType;
    }

    public boolean isSelfCast() {
        return "SELF".equalsIgnoreCase(targetType);
    }
}