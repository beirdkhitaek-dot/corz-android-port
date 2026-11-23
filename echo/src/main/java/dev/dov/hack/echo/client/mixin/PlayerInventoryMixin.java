package dev.dov.hack.echo.client.mixin;

import dev.dov.hack.echo.client.EchoClient;
import dev.dov.hack.echo.client.event.input.SelectedSlotEvent;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerInventory.class)
public class PlayerInventoryMixin {
    @Inject(method = "setSelectedSlot", at = @At("HEAD"), cancellable = true)
    private void onSetSelectedSlotHead(int selectedSlot, CallbackInfo callback) {
        if (!EchoClient.eventsDisabled) {
            PlayerInventory inventory = (PlayerInventory) (Object) this;
            int previousSlot = inventory.getSelectedSlot();
            if (previousSlot != selectedSlot) {
                SelectedSlotEvent event = new SelectedSlotEvent(previousSlot, selectedSlot);
                event.post();
                if (event.cancelled) {
                    callback.cancel();
                }
            }
        }
    }
}
