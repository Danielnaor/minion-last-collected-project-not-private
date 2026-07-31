package com.danielnaor.client.mixin;

import com.danielnaor.client.minion.MinionTracker;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Inject(method = "slotClicked", at = @At("HEAD"))
	private void minionLastCollected$onSlotClicked(
		Slot slot,
		int slotId,
		int mouseButton,
		ClickType clickType,
		CallbackInfo callbackInfo
	) {
		MinionTracker.onSlotClicked(slot, slotId);
	}
}
