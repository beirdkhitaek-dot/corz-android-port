package dev.dov.hack.echo.client.mixin;

import dev.dov.hack.echo.client.EchoClient;
import dev.dov.hack.echo.client.event.input.KeyboardEvent;
import dev.dov.hack.echo.client.keyboard.InputKey;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"))
    public void postKeyEvent(long window, int action, KeyInput input, CallbackInfo callback) {
        if (EchoClient.eventsDisabled) {
            return;
        }
        if (!InputKey.ESCAPE.matches(input.key())) {
            new KeyboardEvent(input.key(), action).post();
        }
    }
}
