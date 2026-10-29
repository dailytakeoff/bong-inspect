package io.github.dailytakeoff.bonginspect;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The animation timeline. Everything here is client-side only: the model, the sounds and the
 * campfire smoke exist only on this player's screen.
 *
 * Hand-space coordinates (what the first-person renderer uses): camera at the origin,
 * +x right, +y up, -z forward. Units are roughly blocks.
 *
 * HIT:     raise -> tip to the mouth -> hit (chamber fills with smoke, bubbles) -> clear ->
 *          pull away -> blow out campfire smoke -> inspect (~1.8 s) -> lower
 * INSPECT: raise -> inspect -> lower
 */
public final class BongAnimation {
	public enum Mode { HIT, INSPECT }

	/** Height of the bong in hand space. */
	public static final float HEIGHT = 0.85f;
	/** Where the mouthpiece ends up: just under the eyes, a little in front of the face. */
	private static final Vector3f MOUTH_TIP = new Vector3f(0.0f, -0.13f, -0.12f);

	// poses: centre (x, y, z), pitch (top toward camera), roll (top leans left), spin (around its own axis)
	static final Pose HIDDEN = new Pose(0.62f, -1.25f, -0.95f, -18, 28, 35);
	static final Pose READY = new Pose(0.40f, -0.24f, -1.0f, 8, 10, 35);
	static final Pose MOUTH = mouthPose(79, 20, -35);
	// inspect: brought a bit closer and toward the middle of the screen
	private static final Vector3f INSPECT_SHIFT = new Vector3f(-0.15f, 0.07f, 0.13f);

	// HIT timeline (seconds)
	private static final float RAISE = 0.35f;
	private static final float AT_MOUTH = 0.85f;
	private static final float LIGHT = 0.90f;
	private static final float FILL_START = 0.95f;
	private static final float FILL_END = 2.25f;
	private static final float CLEAR_START = 2.35f;
	private static final float CLEAR_END = 2.55f;
	private static final float AWAY = 2.95f;
	private static final float SMOKE_START = 2.70f;
	private static final float SMOKE_END = 3.85f;
	private static final float INSPECT_LEN = 1.8f;
	private static final float LOWER_LEN = 0.4f;

	private static Mode mode;
	private static int age = -1;

	private BongAnimation() {
	}

	public static boolean active() {
		return age >= 0;
	}

	public static void start(Mode m) {
		if (active()) {
			return;
		}
		mode = m;
		age = 0;
	}

	public static void stop() {
		age = -1;
	}

	private static float inspectStart() {
		return mode == Mode.HIT ? SMOKE_END : RAISE;
	}

	private static float total() {
		return inspectStart() + INSPECT_LEN + LOWER_LEN;
	}

	/** Called every client tick: advances time and fires sounds and smoke. */
	public static void tick(Minecraft mc) {
		if (!active()) {
			return;
		}
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			stop();
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		age++;
		float t = age / 20f;
		if (t >= total()) {
			stop();
			return;
		}
		if (mode != Mode.HIT) {
			return;
		}
		ThreadLocalRandom r = ThreadLocalRandom.current();
		Vec3 eye = player.getEyePosition();
		float prev = (age - 1) / 20f;
		if (prev < LIGHT && t >= LIGHT) {
			mc.level.playLocalSound(eye.x, eye.y, eye.z, SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 0.5f, 1.1f, false);
		}
		if (t >= FILL_START && t < CLEAR_END && age % 2 == 0) {
			float pitch = 0.7f + r.nextFloat() * 0.6f + (t - FILL_START) * 0.15f;
			mc.level.playLocalSound(eye.x, eye.y - 0.4, eye.z, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.PLAYERS,
					0.25f + r.nextFloat() * 0.2f, pitch, false);
		}
		if (t >= SMOKE_START && t < SMOKE_END) {
			exhale(mc, player, (t - SMOKE_START) / (SMOKE_END - SMOKE_START), r);
		}
	}

	/** Blow campfire smoke out of the mouth, strong at first and tapering off. */
	private static void exhale(Minecraft mc, LocalPlayer player, float progress, ThreadLocalRandom r) {
		Vec3 look = player.getViewVector(1f);
		Vec3 eye = player.getEyePosition();
		Vec3 mouth = eye.add(0, -0.18, 0).add(look.scale(0.45));
		float strength = 1f - progress;
		int count = progress < 0.6f ? 3 : 1 + (r.nextFloat() < strength ? 1 : 0);
		for (int i = 0; i < count; i++) {
			double speed = 0.018 + 0.03 * strength + r.nextDouble() * 0.012;
			double jx = (r.nextDouble() - 0.5) * 0.012;
			double jy = (r.nextDouble() - 0.5) * 0.008;
			double jz = (r.nextDouble() - 0.5) * 0.012;
			double ox = (r.nextDouble() - 0.5) * 0.1;
			double oy = (r.nextDouble() - 0.5) * 0.06;
			double oz = (r.nextDouble() - 0.5) * 0.1;
			mc.level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
					mouth.x + ox, mouth.y + oy, mouth.z + oz,
					look.x * speed + jx, look.y * speed + 0.006 + jy, look.z * speed + jz);
		}
	}

	// ------------------------------------------------------------------ frame for rendering

	/** What the renderer needs for one frame. milkTop/milkBottom are heights (0..1) of the smoke in the chamber. */
	public record Frame(Pose pose, float milkBottom, float milkTop, float milkAlpha) {
	}

	public static Frame frame(float partial) {
		float t = Math.max(0, (age + partial) / 20f);
		Pose pose;
		if (t < RAISE) {
			pose = Pose.lerp(HIDDEN, READY, ease(t / RAISE));
		} else if (mode == Mode.HIT && t < AWAY) {
			if (t < AT_MOUTH) {
				pose = Pose.lerp(READY, MOUTH, ease((t - RAISE) / (AT_MOUTH - RAISE)));
			} else if (t < CLEAR_END) {
				// breathing in: tiny bob, and a little pull toward the face while clearing
				float breathe = Mth.sin((t - AT_MOUTH) * 5f) * 0.004f;
				float pull = t > CLEAR_START ? ease((t - CLEAR_START) / (CLEAR_END - CLEAR_START)) * 0.012f : 0;
				pose = MOUTH.offset(0, breathe, pull);
			} else {
				pose = Pose.lerp(MOUTH.offset(0, 0, 0.012f), READY, ease((t - CLEAR_END) / (AWAY - CLEAR_END)));
			}
		} else if (t < inspectStart()) {
			// holding it while blowing the smoke out
			float idle = t - AWAY;
			pose = READY.offset(0, Mth.sin(idle * 3f) * 0.006f, 0);
		} else if (t < inspectStart() + INSPECT_LEN) {
			pose = inspect((t - inspectStart()) / INSPECT_LEN);
		} else {
			pose = Pose.lerp(READY, HIDDEN, ease((t - inspectStart() - INSPECT_LEN) / LOWER_LEN));
		}

		float bottom = 0;
		float top = 0;
		float alpha = 0;
		if (mode == Mode.HIT && t >= FILL_START && t < CLEAR_END + 0.1f) {
			top = Mth.lerp(ease(Mth.clamp((t - FILL_START) / (FILL_END - FILL_START), 0, 1)), 0.16f, 1.05f);
			bottom = t < CLEAR_START ? 0.12f : Mth.lerp(ease(Mth.clamp((t - CLEAR_START) / (CLEAR_END - CLEAR_START), 0, 1)), 0.12f, 1.1f);
			alpha = 0.6f;
		}
		return new Frame(pose, bottom, top, alpha);
	}

	/** CS:GO-style look-over: turn it all the way round, tilting to show both sides. */
	private static Pose inspect(float u) {
		float spin;
		if (u < 0.45f) {
			spin = 180 * ease(u / 0.45f);
		} else if (u < 0.55f) {
			spin = 180;
		} else {
			spin = 180 + 180 * ease((u - 0.55f) / 0.45f);
		}
		float bump = Mth.sin(u * Mth.PI);
		float wobble = Mth.sin(u * Mth.PI * 2);
		return new Pose(
				READY.x + INSPECT_SHIFT.x * bump,
				READY.y + INSPECT_SHIFT.y * bump,
				READY.z + INSPECT_SHIFT.z * bump,
				READY.pitch + 14 * bump,
				READY.roll - 16 * bump + 8 * wobble,
				READY.spin + spin);
	}

	private static float ease(float x) {
		x = Mth.clamp(x, 0, 1);
		return x * x * (3 - 2 * x);
	}

	/** Places the bong so its mouthpiece sits at MOUTH_TIP. */
	private static Pose mouthPose(float pitch, float roll, float spin) {
		Pose p = new Pose(0, 0, 0, pitch, roll, spin);
		Vector3f tipOffset = p.rotation().transform(new Vector3f(0, HEIGHT * 0.5f, 0));
		return new Pose(MOUTH_TIP.x - tipOffset.x, MOUTH_TIP.y - tipOffset.y, MOUTH_TIP.z - tipOffset.z, pitch, roll, spin);
	}

	/** Centre position plus orientation in degrees. */
	public record Pose(float x, float y, float z, float pitch, float roll, float spin) {
		/** Full rotation (including the spin around the bong's own axis). */
		public Quaternionf rotation() {
			return tilt().rotateY((float) Math.toRadians(spin));
		}

		/** Rotation without the spin: the hand holds the bong in this frame, so it can twirl inside the grip. */
		public Quaternionf tilt() {
			return new Quaternionf().rotateX((float) Math.toRadians(pitch)).rotateZ((float) Math.toRadians(roll));
		}

		Pose offset(float dx, float dy, float dz) {
			return new Pose(x + dx, y + dy, z + dz, pitch, roll, spin);
		}

		static Pose lerp(Pose a, Pose b, float t) {
			return new Pose(Mth.lerp(t, a.x, b.x), Mth.lerp(t, a.y, b.y), Mth.lerp(t, a.z, b.z),
					Mth.lerp(t, a.pitch, b.pitch), Mth.lerp(t, a.roll, b.roll), Mth.lerp(t, a.spin, b.spin));
		}
	}

	/** For the screenshot test: jump to a given moment. */
	public static void debugSet(Mode m, int ticks) {
		mode = m;
		age = ticks;
	}
}
