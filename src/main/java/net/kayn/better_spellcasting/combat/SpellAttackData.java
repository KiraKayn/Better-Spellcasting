package net.kayn.better_spellcasting.combat;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import org.jetbrains.annotations.NotNull;

/**
 * Data class that holds spell information for a weapon attack.
 *
 * New field: trigger - controls WHEN the spell should fire:
 *   - ON_ATTACK => try to fire immediately on the attack/swing (if target known)
 *   - ON_HIT    => fire only when the attack actually hits (damage event)
 */
public class SpellAttackData {

    public enum Trigger {
        ON_ATTACK,
        ON_HIT;

        public static @NotNull Trigger fromString(String s) {
            if (s == null) return ON_HIT;
            String n = s.trim().toLowerCase();
            return switch (n) {
                case "on_attack", "onattack", "attack", "on-attack" -> ON_ATTACK;
                case "on_hit", "onhit", "hit", "on-hit" -> ON_HIT;
                default -> ON_HIT;
            };
        }
    }

    private final AbstractSpell spell;
    private final int level;
    private final String targetType; // "SELF" or "TARGET"
    private final Trigger trigger;

    public SpellAttackData(AbstractSpell spell, int level, String targetType, Trigger trigger) {
        this.spell = spell;
        this.level = level;
        this.targetType = targetType;
        this.trigger = trigger == null ? Trigger.ON_HIT : trigger;
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

    public Trigger getTrigger() {
        return trigger;
    }

    public boolean isSelfCast() {
        return "SELF".equalsIgnoreCase(targetType);
    }

    public boolean isOnAttack() {
        return trigger == Trigger.ON_ATTACK;
    }

    public boolean isOnHit() {
        return trigger == Trigger.ON_HIT;
    }

    @Override
    public String toString() {
        return "SpellAttackData{" +
                "spell=" + (spell != null ? spell.getSpellId() : "null") +
                ", level=" + level +
                ", targetType='" + targetType + '\'' +
                ", trigger=" + trigger +
                '}';
    }
}