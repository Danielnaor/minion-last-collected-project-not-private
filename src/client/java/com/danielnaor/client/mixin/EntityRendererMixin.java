package com.danielnaor.client.mixin;

import com.danielnaor.client.minion.MinionTracker;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void minionLastCollected$addLabel(
		T entity,
		S renderState,
		float partialTick,
		CallbackInfo callbackInfo
	) {
		if (!(entity instanceof ArmorStand)) {
			return;
		}

		String label = MinionTracker.labelFor(entity.position());
		if (label == null) {
			return;
		}

		renderState.nameTag = Component.literal(label);
		Vec3 attachment = entity.getAttachments().getNullable(
			EntityAttachment.NAME_TAG,
			0,
			entity.getYRot(partialTick)
		);
		renderState.nameTagAttachment = attachment != null
			? attachment.add(0.0, 0.35, 0.0)
			: new Vec3(0.0, entity.getBbHeight() + 0.35, 0.0);
	}
}
