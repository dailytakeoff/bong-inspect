package io.github.dailytakeoff.bonginspect;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.sdl.SDLScancode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only entry point. Registers two keybinds (changeable in Options > Controls > Key Binds,
 * under "Bong Inspect") and drives the animation every client tick.
 */
public final class BongInspectClient implements ClientModInitializer {
	public static final String MOD_ID = "bonginspect";
	public static final Logger LOGGER = LoggerFactory.getLogger("Bong Inspect");

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	public static KeyMapping hitKey;
	public static KeyMapping inspectKey;

	@Override
	public void onInitializeClient() {
		hitKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.bonginspect.hit", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_G, CATEGORY));
		inspectKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.bonginspect.inspect", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_H, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (hitKey.consumeClick()) {
				if (mc.player != null && mc.gui.screen() == null) {
					if (!BongAnimation.active()) {
						Ambience.afterHit();
						AmbientSounds.afterEmote();
					}
					BongAnimation.start(BongAnimation.Mode.HIT);
				}
			}
			while (inspectKey.consumeClick()) {
				if (mc.player != null && mc.gui.screen() == null) {
					if (!BongAnimation.active()) {
						AmbientSounds.afterEmote();
					}
					BongAnimation.start(BongAnimation.Mode.INSPECT);
				}
			}
			BongAnimation.tick(mc);
			Ambience.tick(mc);
			AmbientSounds.tick(mc);
		});
	}
}
