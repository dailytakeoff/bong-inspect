package io.github.dailytakeoff.bonginspect;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Hidden extras, sound only, and only on this client (vanilla sounds, so nothing shows up in the jar):
 * <ul>
 * <li>Knocking: 5 to 13.5 minutes after the emote, someone knocks on a door nearby.</li>
 * <li>Doors: every so often a wooden door opens somewhere close by, then shuts a moment later.</li>
 * </ul>
 * No keybinds, nothing in the controls, description or README.
 */
public final class AmbientSounds {
	private static final int KNOCK_MIN = 5 * 60 * 20;
	private static final int KNOCK_MAX = (int) (13.5 * 60 * 20);
	private static final int DOOR_MIN = 3 * 60 * 20;
	private static final int DOOR_MAX = 9 * 60 * 20;

	/** Knock pattern (ticks from the first knock): three knocks, a pause, three more. */
	private static final int[] KNOCKS = {0, 5, 10, 34, 39, 44};

	private static int knockIn = -1;
	private static int knockAge = -1;
	private static Vec3 knockFrom;

	private static int doorIn = -1;
	private static int doorAge = -1;
	private static int doorCloseAt;
	private static Vec3 doorAt;

	private AmbientSounds() {
	}

	/** The emote was used: knocking 5-13.5 minutes from now (unless some is already on the way). */
	public static void afterEmote() {
		if (knockIn < 0 && knockAge < 0) {
			knockIn = gap(KNOCK_MIN, KNOCK_MAX);
		}
	}

	/** Only used by the screenshot test. */
	public static void knockNow() {
		knockIn = 0;
	}

	/** Only used by the screenshot test. */
	public static void doorNow() {
		doorIn = 0;
	}

	/** Called every client tick. */
	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null) {
			// left the world: anything mid-way stops; a pending knock waits for the next world
			knockAge = -1;
			doorAge = -1;
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		tickKnock(player, level);
		tickDoor(player, level);
	}

	private static void tickKnock(LocalPlayer player, ClientLevel level) {
		if (knockAge < 0) {
			if (knockIn < 0) {
				return;
			}
			if (knockIn > 0) {
				knockIn--;
				return;
			}
			knockIn = -1;
			knockAge = 0;
			// a door somewhere off to the side or behind, a few blocks away
			knockFrom = nearby(player, 5, 7).add(0, player.getEyeHeight() - 0.3, 0);
		}
		ThreadLocalRandom r = ThreadLocalRandom.current();
		for (int k : KNOCKS) {
			if (knockAge == k) {
				// a fist on a wooden door, a little softer and sharper than a zombie bashing one
				play(level, knockFrom, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 0.5f + r.nextFloat() * 0.08f, 1.45f + r.nextFloat() * 0.15f);
			}
		}
		knockAge++;
		if (knockAge > KNOCKS[KNOCKS.length - 1]) {
			knockAge = -1;
		}
	}

	private static void tickDoor(LocalPlayer player, ClientLevel level) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		if (doorAge < 0) {
			if (doorIn < 0) {
				doorIn = gap(DOOR_MIN, DOOR_MAX);
			}
			if (doorIn > 0) {
				doorIn--;
				return;
			}
			doorIn = -1;
			doorAge = 0;
			doorCloseAt = 20 + r.nextInt(31);
			doorAt = nearby(player, 4, 8).add(0, 1, 0);
			play(level, doorAt, SoundEvents.WOODEN_DOOR_OPEN, 1f, 0.9f + r.nextFloat() * 0.1f);
		}
		doorAge++;
		if (doorAge >= doorCloseAt) {
			play(level, doorAt, SoundEvents.WOODEN_DOOR_CLOSE, 1f, 0.9f + r.nextFloat() * 0.1f);
			doorAge = -1;
		}
	}

	/** A spot min-max blocks away, off to the side or behind (never straight ahead, where you'd look for it). */
	private static Vec3 nearby(LocalPlayer player, double min, double max) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		double yaw = Math.toRadians(player.getYRot() + 180 + (r.nextDouble() - 0.5) * 200);
		double d = min + r.nextDouble() * (max - min);
		return player.position().add(-Math.sin(yaw) * d, 0, Math.cos(yaw) * d);
	}

	private static void play(ClientLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch, false);
	}

	private static int gap(int min, int max) {
		return ThreadLocalRandom.current().nextInt(min, max + 1);
	}

	/** For the screenshot test. */
	public static String debugState() {
		return "knockIn=" + knockIn + " knockAge=" + knockAge + " knockFrom=" + knockFrom
				+ " doorIn=" + doorIn + " doorAge=" + doorAge + " doorAt=" + doorAt;
	}
}
