package net.jonyboylovespie.lockedchests.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(Blocks.class)
public class BlastResistanceMixin {

    @ModifyExpressionValue(method = "<clinit>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/AbstractBlock$Settings;strength(F)Lnet/minecraft/block/AbstractBlock$Settings;", ordinal = 0),
            slice = @Slice(from=@At(value="CONSTANT",args="stringValue=chest")))
    private static AbstractBlock.Settings Change(AbstractBlock.Settings settings){
        settings.resistance(Float.MAX_VALUE);
        return settings;
    }
}