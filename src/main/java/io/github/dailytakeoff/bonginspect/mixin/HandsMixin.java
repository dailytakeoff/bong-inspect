package io.github.dailytakeoff.bonginspect.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.dailytakeoff.bonginspect.BongAnimation;
import io.github.dailytakeoff.bonginspect.BongRenderer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the animation plays, the normal first-person hands/items are replaced by the bong and the arm holding it. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class HandsMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void bonginspect$render(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState,
			FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		if (!BongAnimation.active()) {
			return;
		}
		BongRenderer.render(partialTicks, poseStack, collector);
		ci.cancel();
	}
}
