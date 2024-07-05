package com.khazoda.basic_storage.mixin;

import com.khazoda.basic_storage.Constants;
import com.khazoda.basic_storage.inventory.BigStackInventory;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(HopperBlockEntity.class)
public class HopperBlockEntityMixin {

  @Unique
  private static Inventory crateInventory;

//  @Inject(method = "getBlockInventoryAt", at = @At(value = "TAIL"), locals = LocalCapture.CAPTURE_FAILHARD)
//  private static void captureInventoryReference(World world, BlockPos pos, BlockState state, CallbackInfoReturnable<Inventory> cir) {
//    crateInventory = cir.getReturnValue();
//  }
//  @Inject(method = "canMergeItems", at = @At("HEAD"), cancellable = true)
//  private static void checkInventoryIsFullForCrateFromHopper(ItemStack first, ItemStack second, CallbackInfoReturnable<Boolean> cir) {
//    cir.setReturnValue(first.getCount() <= crateInventory.getMaxCountPerStack() && ItemStack.areItemsAndComponentsEqual(first, second));
//  }

//  @Inject(method = "insert", at = @At(value = "TAIL"), locals = LocalCapture.CAPTURE_FAILHARD)
//  private static void injected(World world, BlockPos pos, HopperBlockEntity blockEntity, CallbackInfoReturnable<Boolean> cir) {
//    crateInventory = cir.
//  }



//    /* If inventory type seems to be a BigStackInventory (crate's inventory interface) */
//    if (inventory.getMaxCountPerStack() == Constants.CRATE_MAX_COUNT) {
//
//      BigStackInventory bigInventory = (BigStackInventory) inventory;
//      if (bigInventory.getStack().getCount() >= Constants.CRATE_MAX_COUNT) {
//        cir.setReturnValue(true);
//      } else {
//        cir.setReturnValue(false);
//      }
//    }

}
