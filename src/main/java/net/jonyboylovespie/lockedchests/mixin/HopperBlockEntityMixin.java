package net.jonyboylovespie.lockedchests.mixin;

import net.jonyboylovespie.lockedchests.LockedChests;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.Hopper;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.jonyboylovespie.lockedchests.LockedChests.file;
import static net.jonyboylovespie.lockedchests.LockedChests.readArrayFromFile;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {

    @Inject(method = "getInputInventory", at = @At("HEAD"), cancellable = true)
    private static void onGetInputInventory(World world, Hopper hopper, CallbackInfoReturnable<Inventory> cir) {
        boolean canPullFromChest = true;
        LockedChests.ChestOwnership[] lockedChests = readArrayFromFile(file);
        BlockPos sourcePos = BlockPos.ofFloored(hopper.getHopperX(), hopper.getHopperY() + 1.0, hopper.getHopperZ());
        if (world.getBlockState(sourcePos).getBlock() == Blocks.CHEST) {
            for (LockedChests.ChestOwnership lockedChest : lockedChests) {
                BlockPos blockPos = lockedChest.blockPos;
                if (blockPos.toString().equals(sourcePos.toString()) || blockPos.toString().equals(LockedChests.getOtherBlockPos(sourcePos, world.getBlockState(sourcePos)).toString())) {
                    canPullFromChest = false;
                }
            }
        }
        if (!canPullFromChest) {
            cir.setReturnValue(new Inventory() {
                @Override
                public int size() {
                    return 0;
                }

                @Override
                public boolean isEmpty() {
                    return false;
                }

                @Override
                public ItemStack getStack(int slot) {
                    return null;
                }

                @Override
                public ItemStack removeStack(int slot, int amount) {
                    return null;
                }

                @Override
                public ItemStack removeStack(int slot) {
                    return null;
                }

                @Override
                public void setStack(int slot, ItemStack stack) {

                }

                @Override
                public void markDirty() {

                }

                @Override
                public boolean canPlayerUse(PlayerEntity player) {
                    return false;
                }

                @Override
                public void clear() {

                }
            });
        }
    }
}