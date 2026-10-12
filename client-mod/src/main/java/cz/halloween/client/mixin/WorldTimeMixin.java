package cz.halloween.client.mixin;

import cz.halloween.client.BloodMoonState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class WorldTimeMixin {
    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void halloweencore$exposeBloodMoonMarker(CallbackInfoReturnable<Long> callback) {
        if (!((Object) this instanceof ClientWorld)) return;
        Long syntheticTime = BloodMoonState.getSyntheticTimeOfDay();
        if (syntheticTime != null) {
            callback.setReturnValue(syntheticTime);
        }
    }
}
