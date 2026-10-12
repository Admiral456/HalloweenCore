package cz.halloween.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class HalloweenCoreClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playS2C().register(VisualStatePayload.ID, VisualStatePayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(VisualStatePayload.ID,
                (payload, context) -> BloodMoonState.setActive(payload.state() != 0));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BloodMoonState.reset());
    }
}
