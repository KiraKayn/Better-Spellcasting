package net.kayn.better_spellcasting.network;

import net.kayn.better_spellcasting.BetterSpellcasting;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class NetworkHandler {

    private static final String PROTOCOL = "1";
    public static SimpleChannel CHANNEL;

    public static void register() {
        CHANNEL = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(BetterSpellcasting.MOD_ID, "main"))
                .clientAcceptedVersions(PROTOCOL::equals)
                .serverAcceptedVersions(PROTOCOL::equals)
                .networkProtocolVersion(() -> PROTOCOL)
                .simpleChannel();

        int id = 0;

        CHANNEL.registerMessage(
                id++,
                SwingMessage.class,
                SwingMessage::encode,
                SwingMessage::decode,
                SwingMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
    }
}
