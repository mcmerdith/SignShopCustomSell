package net.mcmerdith.signshopcustomsell.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemUtil {
    /***
     * Map an array of ItemStacks
     * Damageable items will be seperated if they are damaged, otherwise items will be combined by type
     * @param unsafeStacks Stacks to map
     * @param stripNBT If nbt should be stripped from the result
     * @return A map of each ItemStack to the amount of items
     */
    @NotNull
    public static Map<WrappedItem, Integer> mapStacks(ItemStack[] unsafeStacks, boolean stripNBT) {
        // Make a safe copy
        ItemStack[] stacks = cloneStacks(unsafeStacks);

        // Initialize the return value
        Map<WrappedItem, Integer> mapped = new HashMap<>();

        // If we were passed nothing, we're done
        if(stacks == null) return mapped;

        // Temporarily store how many items are in the stack
        int tempAmount;
        for (ItemStack currentStack : stacks) {
            // If no stack, oh well
            if (currentStack == null) continue;

            // Update the current amount
            tempAmount = currentStack.getAmount();

            // Wrap the item
            WrappedItem item = new WrappedItem(currentStack, stripNBT);

            // If we've already mapped this item, add the saved amount
            if (mapped.containsKey(item)) tempAmount += mapped.get(item);

            // Update the current value
            mapped.put(item, tempAmount);
        }
        return mapped;
    }

    /***
     * Create a safe copy of an ItemStack array
     * @param unsafeStacks Stacks to copy
     * @return A safe copy of the array
     */
    public static ItemStack[] cloneStacks(ItemStack[] unsafeStacks) {
        if (unsafeStacks == null) return null;

        ItemStack[] stacks = new ItemStack[unsafeStacks.length];

        for (int i = 0; i < unsafeStacks.length; i++) {
            ItemStack stack = unsafeStacks[i];
            if (stack != null) stacks[i] = unsafeStacks[i];
        }

        return stacks;
    }

    /***
     * Strip the NBT from an ItemStack
     * @param stack Stack to strip NBT from
     * @return A copy of the ItemStack without NBT
     */
    public static ItemStack removeNBT(ItemStack stack) {
        if (stack == null) return null;

        ItemStack tempStack = new ItemStack(stack.getType(), stack.getAmount());
        int durability = getItemDurability(stack);
        if (durability > -1) ((Damageable) tempStack.getItemMeta()).setDamage(durability);

        return tempStack;
    }


    /***
     * Get the durability
     * @param item Item to check
     * @return The item's durability (or -1 if item is not damageable)
     */
    public static int getItemDurability(ItemStack item) {
        if (!(item.getItemMeta() instanceof Damageable)) return -1;
        return ((Damageable) item.getItemMeta()).getDamage();
    }
}
