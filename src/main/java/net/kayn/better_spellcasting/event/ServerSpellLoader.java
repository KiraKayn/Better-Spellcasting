package net.kayn.better_spellcasting.event;

import com.google.gson.*;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.kayn.better_spellcasting.BetterSpellcasting;
import net.kayn.better_spellcasting.combat.SpellAttackData;
import net.kayn.better_spellcasting.compat.SpellDataHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.Map;

// Loads spell data after weapon attributes are loaded
@EventBusSubscriber(modid = BetterSpellcasting.MOD_ID)
public class ServerSpellLoader {
    private static final Gson GSON = new Gson();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        loadAllWeaponSpells(event.getServer().getResourceManager());
    }

    private static void loadAllWeaponSpells(ResourceManager resourceManager) {
        try {
            Field registrationsField = WeaponRegistry.class.getDeclaredField("registrations");
            registrationsField.setAccessible(true);

            @SuppressWarnings("unchecked")
            Map<ResourceLocation, WeaponAttributes> weaponMap =
                    (Map<ResourceLocation, WeaponAttributes>) registrationsField.get(null);

            if (weaponMap == null || weaponMap.isEmpty()) {
                return;
            }

            for (Map.Entry<ResourceLocation, WeaponAttributes> entry : weaponMap.entrySet()) {
                ResourceLocation weaponId = entry.getKey();
                WeaponAttributes attributes = entry.getValue();

                if (attributes == null || attributes.attacks() == null) {
                    continue;
                }

                loadSpellDataForWeapon(resourceManager, weaponId, attributes);
            }

        } catch (Exception ignored) {
        }
    }

    private static void loadSpellDataForWeapon(ResourceManager resourceManager,
                                               ResourceLocation weaponId,
                                               WeaponAttributes attributes) {
        ResourceLocation jsonLoc = ResourceLocation.fromNamespaceAndPath(
                weaponId.getNamespace(),
                "weapon_attributes/" + weaponId.getPath() + ".json"
        );

        try {
            var resource = resourceManager.getResource(jsonLoc);
            if (resource.isEmpty()) {
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.get().open()))) {

                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root == null || !root.has("attributes")) {
                    return;
                }

                JsonObject attrs = root.getAsJsonObject("attributes");
                if (!attrs.has("attacks")) {
                    return;
                }

                JsonArray attacks = attrs.getAsJsonArray("attacks");
                if (attacks.isEmpty()) {
                    return;
                }

                int registeredAttacks = attributes.attacks().length;

                for (int i = 0; i < attacks.size(); i++) {
                    JsonElement attackElement = attacks.get(i);
                    if (!attackElement.isJsonObject()) {
                        continue;
                    }

                    JsonObject attack = attackElement.getAsJsonObject();
                    if (!attack.has("spell")) {
                        continue;
                    }
                    if (i >= registeredAttacks) {
                        continue;
                    }

                    String spellId = attack.get("spell").getAsString();
                    int level = attack.has("level") ? attack.get("level").getAsInt() : 1;
                    String target = attack.has("target") ? attack.get("target").getAsString() : "TARGET";

                    AbstractSpell spell = SpellRegistry.getSpell(
                            ResourceLocation.parse(spellId)
                    );
                    if (spell == SpellRegistry.none()) {
                        BetterSpellcasting.LOGGER.warn("Unknown spell '{}' in {}", spellId, weaponId);
                        continue;
                    }

                    // Blacklist continuous cast spells
                    if (spell.getCastType() == CastType.CONTINUOUS) {
                        BetterSpellcasting.LOGGER.error(
                                "CONTINUOUS cast spell '{}' is not supported for weapon attacks in {} attack {}. Skipping.",
                                spellId, weaponId, i
                        );
                        BetterSpellcasting.LOGGER.error(
                                "Continuous spells cannot be bound to weapons. Please use INSTANT or CHARGED spells only."
                        );
                        continue;
                    }

                    // Register the spell data
                    // Register the spell data
                    String triggerStr = attack.has("trigger") ? attack.get("trigger").getAsString() : "on_hit";
                    SpellAttackData.Trigger trigger = SpellAttackData.Trigger.fromString(triggerStr);

                    SpellAttackData pad = new SpellAttackData(spell, level, target, trigger);
                    SpellDataHolder.register(weaponId, i, pad);
                }

            }

        } catch (Exception ignored) {
        }
    }
}