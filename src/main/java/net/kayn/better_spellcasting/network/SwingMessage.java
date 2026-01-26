package net.kayn.better_spellcasting.network;

import net.kayn.better_spellcasting.event.AttackSpellHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 Client tells server "I swung".
 Server will read player from context and update combo state.
 */
public class SwingMessage {

    public static void encode(SwingMessage msg, FriendlyByteBuf buf) {
    }

    public static SwingMessage decode(FriendlyByteBuf buf) {
        return new SwingMessage();
    }

    public static void handle(SwingMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            var sender = ctx.getSender();
            if (sender != null) {
                AttackSpellHandler.onClientSwingFromPacket(sender);
            }
        });
        ctx.setPacketHandled(true);
    }
}
