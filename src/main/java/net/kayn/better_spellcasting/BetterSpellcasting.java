package net.kayn.better_spellcasting;

import com.mojang.logging.LogUtils;
import net.kayn.better_spellcasting.network.NetworkHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(BetterSpellcasting.MOD_ID)
public class BetterSpellcasting {

    public static final String MOD_ID = "better_spellcasting";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static boolean ironSpellbooksLoaded = false;
    public static boolean betterCombatLoaded = false;

    public BetterSpellcasting() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);

        ironSpellbooksLoaded = ModList.get().isLoaded("irons_spellbooks");
        betterCombatLoaded = ModList.get().isLoaded("bettercombat");

        LOGGER.info("Better Spellcasting initialized!");
        LOGGER.info("Iron's Spellbooks loaded: {}", ironSpellbooksLoaded);
        LOGGER.info("Better Combat loaded: {}", betterCombatLoaded);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("Registering BetterSpellcasting network channel");
            NetworkHandler.register();
        });
    }
}
