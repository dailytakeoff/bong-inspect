package io.github.dailytakeoff.bonginspect;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Draws the bong (your FBX model, glass texture) and the arm holding it, in first person. */
public final class BongRenderer {
	private static final Identifier GLASS = Identifier.fromNamespaceAndPath(BongInspectClient.MOD_ID, "textures/entity/glass.png");
	/** A texel in the solid white bottom row of the atlas, used for water and smoke inside the glass. */
	private static final float WHITE_U = 0.5f / 128f;
	private static final float WHITE_V = 1f - 0.5f / 256f;
	private static final float WATER_LINE = 0.14f;

	private static final Supplier<Mesh> MESH = Suppliers.memoize(BongRenderer::load);
	private static final Supplier<ModelPart> WIDE = Suppliers.memoize(() -> bakeArms(false));
	private static final Supplier<ModelPart> SLIM = Suppliers.memoize(() -> bakeArms(true));

	/** Where the hand grips: on the tube, as a fraction of the bong's height. */
	private static final float GRIP_HEIGHT = 0.53f;

	private BongRenderer() {
	}

	/** Called from the first-person hands renderer while the animation runs (vanilla hands are skipped). */
	public static void render(float partial, PoseStack ps, SubmitNodeCollector collector) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		Mesh mesh = MESH.get();
		if (player == null || mc.level == null || mesh == null) {
			return;
		}
		BongAnimation.Frame f = BongAnimation.frame(partial);
		BongAnimation.Pose pose = f.pose();
		if (player.getMainArm() == HumanoidArm.LEFT) {
			// left-handed: mirror the whole animation to the left side
			pose = new BongAnimation.Pose(-pose.x(), pose.y(), pose.z(), pose.pitch(), -pose.roll(), 180 - pose.spin());
		}
		int light = LightCoordsUtil.getLightCoords(mc.level, BlockPos.containing(player.getEyePosition(partial)));

		ps.pushPose();
		ps.translate(pose.x(), pose.y(), pose.z());
		ps.mulPose(pose.rotation());
		ps.scale(BongAnimation.HEIGHT, BongAnimation.HEIGHT, BongAnimation.HEIGHT);
		ps.translate(0, -0.5f, 0);
		collector.submitCustomGeometry(ps, RenderTypes.entityTranslucent(GLASS), (p, buf) -> mesh.emit(p, buf, light, f));
		ps.popPose();

		renderArm(ps, collector, player, pose, light);
	}

	// ------------------------------------------------------------------ arm

	private static ModelPart bakeArms(boolean slim) {
		return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot();
	}

	/** Right (or left, for left-handed players) arm from off-screen shoulder to the grip on the tube. */
	private static void renderArm(PoseStack ps, SubmitNodeCollector collector, LocalPlayer player, BongAnimation.Pose pose, int light) {
		boolean right = player.getMainArm() == HumanoidArm.RIGHT;
		float side = right ? 1 : -1;
		Quaternionf tilt = pose.tilt();
		float h = BongAnimation.HEIGHT;
		Vector3f center = new Vector3f(pose.x(), pose.y(), pose.z());
		// tube axis point at grip height, and the fist a little to the side of it / toward the camera
		Vector3f axisPoint = tilt.transform(new Vector3f(0, (GRIP_HEIGHT - 0.5f) * h, 0)).add(center);
		Vector3f hand = tilt.transform(new Vector3f(0.05f * side, (GRIP_HEIGHT - 0.5f) * h - 0.02f, 0.05f)).add(center);
		Vector3f shoulder = new Vector3f(0.62f * side, -0.95f, -0.15f);

		PlayerSkin skin = player.getSkin();
		boolean slim = skin.model() == PlayerModelType.SLIM;
		ModelPart root = slim ? SLIM.get() : WIDE.get();
		ModelPart arm = root.getChild(right ? "right_arm" : "left_arm");
		arm.resetPose();
		arm.setPos(0, 0, 0);
		arm.setRotation(0, 0, 0);
		arm.getChild(right ? "right_sleeve" : "left_sleeve").visible =
				player.isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE);
		RenderType type = RenderTypes.entityTranslucent(skin.body().texturePath());

		// palm faces the tube
		Vector3f palm = new Vector3f(axisPoint).sub(hand);
		segment(ps, collector, arm, right, slim, shoulder, hand, palm, type, light);
	}

	/** Draws the arm as a straight block from 'from' (shoulder end) to 'to' (hand end). */
	private static void segment(PoseStack ps, SubmitNodeCollector collector, ModelPart arm, boolean isRight, boolean slim,
			Vector3f from, Vector3f to, Vector3f palmHint, RenderType type, int light) {
		Vector3f y = new Vector3f(to).sub(from);
		float len = y.length();
		if (len < 1e-3f) {
			return;
		}
		y.div(len);
		Vector3f palm = new Vector3f(palmHint).sub(new Vector3f(y).mul(palmHint.dot(y)));
		if (palm.lengthSquared() < 1e-6f) {
			palm.set(-1, 0, 0);
		}
		palm.normalize();
		// the palm of the right arm model faces +x, of the left arm -x
		Vector3f x = isRight ? palm : palm.negate(new Vector3f());
		Vector3f z = new Vector3f(x).cross(y);
		float hx = isRight ? (slim ? -0.5f : -1f) : (slim ? 0.5f : 1f);
		final float armLen = 12 / 16f;
		final float thick = 0.85f;
		ps.pushPose();
		ps.mulPose(new Matrix4f(
				x.x, x.y, x.z, 0,
				y.x, y.y, y.z, 0,
				z.x, z.y, z.z, 0,
				to.x, to.y, to.z, 1));
		ps.scale(thick, len / armLen, thick);
		ps.translate(-hx / 16f, -10 / 16f, 0);
		collector.submitModelPart(arm, ps, type, light, OverlayTexture.NO_OVERLAY, null);
		ps.popPose();
	}

	// ------------------------------------------------------------------ mesh

	private static Mesh load() {
		try (InputStream raw = BongRenderer.class.getResourceAsStream("/assets/bonginspect/models/bong.bin.gz")) {
			if (raw == null) {
				BongInspectClient.LOGGER.error("bong.bin.gz missing from the jar");
				return null;
			}
			DataInputStream in = new DataInputStream(new GZIPInputStream(raw, 1 << 16));
			byte[] magic = new byte[4];
			in.readFully(magic);
			int tris = in.readInt();
			float[] v = new float[tris * 3 * 8];
			for (int i = 0; i < v.length; i++) {
				v[i] = in.readFloat();
			}
			byte[] grip = new byte[tris];
			in.readFully(grip);
			BongInspectClient.LOGGER.info("Loaded bong model: {} triangles", tris);
			return new Mesh(tris, v, grip);
		} catch (IOException e) {
			BongInspectClient.LOGGER.error("Could not load the bong model", e);
			return null;
		}
	}

	/** Triangles with position, normal and uv. Drawn as quads (last corner doubled) because entity render types use quads. */
	private record Mesh(int tris, float[] v, byte[] grip) {
		void emit(PoseStack.Pose pose, VertexConsumer buf, int light, BongAnimation.Frame f) {
			Matrix4f m = pose.pose();
			// 1) the glass itself
			for (int t = 0; t < tris; t++) {
				int color = grip[t] != 0 ? 0xFFB4D2DA : 0xFFFFFFFF;
				int base = t * 24;
				for (int k = 0; k < 4; k++) {
					int o = base + Math.min(k, 2) * 8;
					buf.addVertex(m, v[o], v[o + 1], v[o + 2]).setColor(color).setUv(v[o + 6], v[o + 7])
							.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, v[o + 3], v[o + 4], v[o + 5]);
				}
			}
			// 2) water in the base and smoke filling the chamber, drawn just inside the glass
			int[] col = new int[3];
			for (int t = 0; t < tris; t++) {
				int base = t * 24;
				boolean any = false;
				for (int k = 0; k < 3; k++) {
					col[k] = inside(v[base + k * 8 + 1], f);
					any |= (col[k] >>> 24) > 2;
				}
				if (!any) {
					continue;
				}
				for (int k = 0; k < 4; k++) {
					int kk = Math.min(k, 2);
					int o = base + kk * 8;
					float s = 0.004f;
					buf.addVertex(m, v[o] - v[o + 3] * s, v[o + 1] - v[o + 4] * s, v[o + 2] - v[o + 5] * s)
							.setColor(col[kk]).setUv(WHITE_U, WHITE_V)
							.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, v[o + 3], v[o + 4], v[o + 5]);
				}
			}
		}

		/** ARGB of what's inside the glass at height y (0 = base, 1 = tip). */
		private static int inside(float y, BongAnimation.Frame f) {
			if (y < WATER_LINE) {
				float a = 0.30f * Mth.clamp((WATER_LINE - y) / 0.015f, 0, 1);
				return argb(a, 0x6FB7DE);
			}
			if (f.milkAlpha() <= 0) {
				return 0;
			}
			float a = f.milkAlpha()
					* Mth.clamp((f.milkTop() - y) / 0.06f, 0, 1)
					* Mth.clamp((y - f.milkBottom()) / 0.06f, 0, 1);
			return argb(a, 0xEDEDED);
		}

		private static int argb(float a, int rgb) {
			return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | rgb;
		}
	}
}
