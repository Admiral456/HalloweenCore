package cz.halloween.client;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Mirrors the single-byte Bukkit plugin message sent by HalloweenCore.
 * 0 = inactive; 1 = Blood Moon event active.
 */
public record VisualStatePayload(byte state) implements CustomPayload {
    public static final Id<VisualStatePayload> ID =
            new Id<>(Identifier.of("halloweencore", "visual_state"));

    public static final PacketCodec<RegistryByteBuf, VisualStatePayload> CODEC =
            new PacketCodec<>() {
                @Override
                public VisualStatePayload decode(RegistryByteBuf buf) {
                    return new VisualStatePayload(buf.readByte());
                }

                @Override
                public void encode(RegistryByteBuf buf, VisualStatePayload payload) {
                    buf.writeByte(payload.state());
                }
            };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
