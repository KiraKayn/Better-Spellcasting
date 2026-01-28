package net.kayn.better_spellcasting.network;

import io.netty.buffer.ByteBuf;
import net.kayn.better_spellcasting.BetterSpellcasting;
import net.kayn.better_spellcasting.event.AttackSpellHandler;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client tells server "I swung".
 * Server will read player from context and update combo state.
 */
public record SwingMessage() implements CustomPacketPayload {

    public static final Type<SwingMessage> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BetterSpellcasting.MOD_ID, "swing")
    );

    public static final StreamCodec<ByteBuf, SwingMessage> CODEC = StreamCodec.unit(new SwingMessage());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwingMessage msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var sender = ctx.player();
            if (sender != null) {
                AttackSpellHandler.onClientSwingFromPacket((ServerPlayer) sender);
            }
        });
    }
}