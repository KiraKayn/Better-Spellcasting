package net.kayn.better_spellcasting.network;

import net.kayn.better_spellcasting.BetterSpellcasting;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = BetterSpellcasting.MOD_ID)
public class NetworkHandler {

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        BetterSpellcasting.LOGGER.info("Registering payload handlers for {}", BetterSpellcasting.MOD_ID);

        PayloadRegistrar registrar = event.registrar(BetterSpellcasting.MOD_ID)
                .versioned("1.0.0")
                .optional();

        registrar.playToServer(
                SwingMessage.TYPE,
                SwingMessage.CODEC,
                SwingMessage::handle
        );

        BetterSpellcasting.LOGGER.info("Registered SwingMessage payload");
    }
}