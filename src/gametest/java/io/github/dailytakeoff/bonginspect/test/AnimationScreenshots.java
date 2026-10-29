package io.github.dailytakeoff.bonginspect.test;

import io.github.dailytakeoff.bonginspect.BongAnimation;
import io.github.dailytakeoff.bonginspect.BongInspectClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Not a real test: a photographer. Starts a world, plays the animation and saves screenshots
 * at fixed moments, so the animation can be checked without playing.
 */
public class AnimationScreenshots implements FabricClientGameTest {
	// moments to photograph (ticks since the key press) for the full HIT animation
	private static final int[] HIT_TICKS = {3, 6, 10, 14, 17, 24, 34, 44, 49, 53, 58, 62, 70, 78, 86, 94, 102, 110, 116};
	private static final int[] INSPECT_TICKS = {7, 16, 25, 34, 43, 49};

	@Override
	public void runTest(ClientGameTestContext ctx) {
		ctx.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("fill -12 99 -12 12 99 12 minecraft:grass_block");
			server.runCommand("fill -12 100 -12 12 110 12 minecraft:air");
			server.runCommand("gamemode creative @a");
			server.runCommand("tp @a 0.5 100 6.5 180 5");
			ctx.waitTicks(60);
			ctx.runOnClient(mc -> BongInspectClient.LOGGER.info("KEYS: hit={} inspect={}",
					BongInspectClient.hitKey.saveString(), BongInspectClient.inspectKey.saveString()));
			ctx.takeScreenshot("00_idle");

			ctx.runOnClient(mc -> BongAnimation.start(BongAnimation.Mode.HIT));
			int at = 0;
			for (int tick : HIT_TICKS) {
				ctx.waitTicks(tick - at);
				at = tick;
				ctx.takeScreenshot(String.format("hit_%03d", tick));
			}
			ctx.waitTicks(30);
			ctx.runOnClient(mc -> BongAnimation.stop());
			ctx.waitTicks(80);

			ctx.runOnClient(mc -> BongAnimation.start(BongAnimation.Mode.INSPECT));
			at = 0;
			for (int tick : INSPECT_TICKS) {
				ctx.waitTicks(tick - at);
				at = tick;
				ctx.takeScreenshot(String.format("inspect_%03d", tick));
			}
			ctx.waitTicks(20);

			// third-person check: smoke still comes out of the mouth
			ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
			ctx.runOnClient(mc -> BongAnimation.start(BongAnimation.Mode.HIT));
			ctx.waitTicks(66);
			ctx.takeScreenshot("third_person_smoke");
		}
	}
}
