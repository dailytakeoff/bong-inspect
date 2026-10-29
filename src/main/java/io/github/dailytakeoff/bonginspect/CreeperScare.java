package io.github.dailytakeoff.bonginspect;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Prank: every so often a creeper sneaks up right behind you and starts hissing.
 *
 * A few footsteps from behind, then the fuse hiss, and a fake creeper (it only exists on this
 * screen) standing there swelling and flashing. Right when it would blow, it fizzles out in a
 * puff of smoke. Nothing explodes, nothing gets hurt, and other players don't see or hear it.
 */
public final class CreeperScare {
	/** Random scares: one every 4 to 12 minutes of play. */
	private static final int MIN_GAP = 4 * 60 * 20;
	private static final int MAX_GAP = 12 * 60 * 20;
	/** After taking a hit there's a chance of a paranoia scare 15-45 s later. */
	private static final float AFTER_HIT_CHANCE = 0.35f;
	private static final int AFTER_HIT_MIN = 15 * 20;
	private static final int AFTER_HIT_MAX = 45 * 20;

	/** How far behind the player the creeper stands (blocks). */
	private static final double DISTANCE = 1.7;
	/** Footsteps (ticks since the scare started), each one a bit closer. */
	private static final int[] STEPS = {0, 8, 16};
	private static final double STEP_SPACING = 0.6;
	private static final int HISS = 26;
	/** A real creeper's fuse is 30 ticks: it fizzles exactly when it would have blown. */
	private static final int FIZZLE = HISS + 30;

	/** Negative, so the fake can never clash with a real entity id from the server. */
	private static final int FAKE_ID = -0x0C4EE9;

	private static int countdown = -1;
	private static int age = -1;
	/** Where the creeper stands, and which way is "behind" (flat, unit length). */
	private static Vec3 spot;
	private static Vec3 behind;
	private static BlockPos ground;
	private static Creeper fake;
	private static ClientLevel fakeLevel;

	private CreeperScare() {
	}

	public static boolean active() {
		return age >= 0;
	}

	/** Start a scare on the next tick (the keybind). */
	public static void now() {
		if (!active()) {
			countdown = 0;
		}
	}

	/** Called when the player takes a hit: sometimes brings the next scare forward. */
	public static void afterHit() {
		if (active() || ThreadLocalRandom.current().nextFloat() >= AFTER_HIT_CHANCE) {
			return;
		}
		int soon = gap(AFTER_HIT_MIN, AFTER_HIT_MAX);
		if (countdown < 0 || soon < countdown) {
			countdown = soon;
		}
	}

	/** Called every client tick. */
	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null) {
			// left the world: the fake went with it
			fake = null;
			fakeLevel = null;
			age = -1;
			countdown = -1;
			return;
		}
		if (fakeLevel != null && fakeLevel != level) {
			// changed dimension mid-scare
			fake = null;
			fakeLevel = null;
			age = -1;
		}
		if (mc.isPaused()) {
			return;
		}
		if (active()) {
			step(player, level);
			return;
		}
		if (countdown < 0) {
			countdown = gap(MIN_GAP, MAX_GAP);
		}
		if (countdown > 0) {
			countdown--;
		}
		if (countdown == 0 && mc.gui.screen() == null && player.isAlive()) {
			start(player, level);
		}
	}

	private static void start(LocalPlayer player, ClientLevel level) {
		countdown = -1;
		// straight behind the player, however far up or down they're looking
		double yaw = Math.toRadians(player.getYRot());
		behind = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw));
		Vec3 target = player.position().add(behind.scale(DISTANCE));
		ground = null;
		Vec3 standing = clearPathBehind(level, player) ? findStandingSpot(level, target) : null;
		spot = standing != null ? standing : target;

		if (standing != null) {
			Creeper c = new Creeper(EntityType.CREEPER, level);
			c.setId(FAKE_ID);
			// it makes no sounds of its own: every sound comes from here
			c.setSilent(true);
			c.setNoAi(true);
			float face = faceYaw(spot, player);
			c.snapTo(spot.x, spot.y, spot.z, face, 0);
			c.setYHeadRot(face);
			c.setYBodyRot(face);
			level.addEntity(c);
			fake = c;
			fakeLevel = level;
		}
		age = 0;
		events(player, level);
	}

	private static void step(LocalPlayer player, ClientLevel level) {
		age++;
		events(player, level);
	}

	/** Whatever happens at the current tick of the scare. */
	private static void events(LocalPlayer player, ClientLevel level) {
		if (fake != null) {
			// keep staring at the player
			float face = faceYaw(fake.position(), player);
			fake.setYRot(face);
			fake.setYHeadRot(face);
		}
		for (int i = 0; i < STEPS.length; i++) {
			if (age == STEPS[i]) {
				footstep(level, STEPS.length - 1 - i);
			}
		}
		if (age == HISS) {
			level.playLocalSound(spot.x, spot.y + 1, spot.z, SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1.0f, 0.5f, false);
			if (fake != null) {
				// the creeper's own tick does the swelling and white flashing from here
				fake.setSwellDir(1);
			}
		}
		if (age >= FIZZLE) {
			fizzle(level);
			age = -1;
		}
	}

	/** A footstep on whatever the creeper is standing on, stepsLeft steps further back. */
	private static void footstep(ClientLevel level, int stepsLeft) {
		SoundType sound = ground != null ? level.getBlockState(ground).getSoundType() : SoundType.GRASS;
		Vec3 p = spot.add(behind.scale(stepsLeft * STEP_SPACING));
		level.playLocalSound(p.x, p.y, p.z, sound.getStepSound(), SoundSource.HOSTILE,
				sound.getVolume() * 0.2f, sound.getPitch(), false);
	}

	/** Instead of exploding: a puff of smoke and a fizz, like it got put out. */
	private static void fizzle(ClientLevel level) {
		Vec3 p = fake != null ? fake.position() : spot;
		if (fake != null) {
			level.removeEntity(fake.getId(), Entity.RemovalReason.DISCARDED);
			fake = null;
			fakeLevel = null;
		}
		ThreadLocalRandom r = ThreadLocalRandom.current();
		for (int i = 0; i < 24; i++) {
			level.addParticle(ParticleTypes.POOF,
					p.x + (r.nextDouble() - 0.5) * 0.8, p.y + r.nextDouble() * 1.6, p.z + (r.nextDouble() - 0.5) * 0.8,
					r.nextGaussian() * 0.02, 0.02 + r.nextDouble() * 0.03, r.nextGaussian() * 0.02);
		}
		level.playLocalSound(p.x, p.y + 1, p.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.5f, 1.6f, false);
	}

	/**
	 * A spot near the target where a creeper could stand: something solid underneath and two
	 * blocks of room. Checks a little above and below so it works on slopes and stairs.
	 */
	private static Vec3 findStandingSpot(ClientLevel level, Vec3 target) {
		BlockPos start = BlockPos.containing(target);
		for (int dy = 1; dy >= -2; dy--) {
			BlockPos feet = start.offset(0, dy, 0);
			BlockPos below = feet.below();
			VoxelShape floor = level.getBlockState(below).getCollisionShape(level, below);
			if (floor.isEmpty() || !open(level, feet) || !open(level, feet.above())) {
				continue;
			}
			ground = below;
			return new Vec3(target.x, below.getY() + floor.max(Direction.Axis.Y), target.z);
		}
		return null;
	}

	/** No wall between the player and the spot (otherwise it's sound only). */
	private static boolean clearPathBehind(ClientLevel level, LocalPlayer player) {
		for (double d = 0.6; d < DISTANCE; d += 0.5) {
			Vec3 p = player.position().add(behind.scale(d));
			if (!open(level, BlockPos.containing(p.add(0, 0.5, 0))) || !open(level, BlockPos.containing(p.add(0, 1.5, 0)))) {
				return false;
			}
		}
		return true;
	}

	private static boolean open(ClientLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/** Yaw for something at `from` to look at the player. */
	private static float faceYaw(Vec3 from, LocalPlayer player) {
		double dx = player.getX() - from.x;
		double dz = player.getZ() - from.z;
		return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
	}

	private static int gap(int min, int max) {
		return ThreadLocalRandom.current().nextInt(min, max + 1);
	}

	/** For the screenshot test. */
	public static String debugState() {
		return "age=" + age + " spot=" + spot + " ground=" + ground + " fake=" + (fake != null ? fake.position() : "none");
	}
}
