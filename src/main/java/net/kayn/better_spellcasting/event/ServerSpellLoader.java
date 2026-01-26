package net.kayn.better_spellcasting.event;

import com.google.gson.*;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.kayn.better_spellcasting.BetterSpellcasting;
import net.kayn.better_spellcasting.combat.SpellAttackData;
import net.kayn.better_spellcasting.compat.SpellDataHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.Map;

// Loads spell data after weapon attributes are loaded
@Mod.EventBusSubscriber(modid = BetterSpellcasting.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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

        } catch (Exception ignored) {}
    }

    private static void loadSpellDataForWeapon(ResourceManager resourceManager, ResourceLocation weaponId, WeaponAttributes attributes) {
        ResourceLocation jsonLoc = new ResourceLocation(
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
                if (attacks.size() == 0) {
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

                    AbstractSpell spell = SpellRegistry.getSpell(new ResourceLocation(spellId));
                    if (spell == SpellRegistry.none()) {
                        continue;
                    }

                    SpellDataHolder.register(weaponId, i, new SpellAttackData(spell, level, target));
                }

            }

        } catch (Exception ignored) {}
    }
}
