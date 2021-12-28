package net.mcmerdith.signshopcustomsell.util;

import lombok.EqualsAndHashCode;
import org.bukkit.inventory.ItemStack;

@EqualsAndHashCode
public class WrappedItem {
    @EqualsAndHashCode.Exclude public final ItemStack baseStack;
    public final ItemStack stack;
    public final int durability;

    public WrappedItem(ItemStack unsafeStack, boolean stripNBT) {
        this.baseStack = unsafeStack;

        ItemStack stack = unsafeStack.clone();
        stack.setAmount(1);

        this.durability = ItemUtil.getItemDurability(stack);

        if (stripNBT) stack = ItemUtil.removeNBT(stack);
        this.stack = stack;
    }


    /***
     * Get the percentage of remaining durability
     * @return durability factor
     */
    public double getDamageMultiplier() {
        if (durability < 1) {
            return 1.0D;
        } else {
            int maxDur = stack.getType().getMaxDurability();
            return (double) (maxDur - durability) / maxDur;
        }
    }

    /***
     * Check if the wrapped items type is the same as anothers
     * @param item item to check
     * @return if the items are the same type
     */
    public boolean isSimilar(WrappedItem item) {
        if (item == null) return false;
        return this.stack.getType() == item.stack.getType();
    }
}
