package net.mcmerdith.signshopcustomsell.operations;

import net.mcmerdith.signshopcustomsell.util.ItemUtil;
import net.mcmerdith.signshopcustomsell.util.WrappedItem;
import org.bukkit.inventory.ItemStack;
import org.wargamer2010.signshop.configuration.SignShopConfig;
import org.wargamer2010.signshop.operations.SignShopArguments;
import org.wargamer2010.signshop.operations.SignShopOperation;
import org.wargamer2010.signshop.util.itemUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class takeCustomVariablePlayerItems implements SignShopOperation {
    private static class TransactionItem {
        protected final WrappedItem item;
        protected final int count;
        protected final double amountMultiplier;

        protected TransactionItem(WrappedItem item, int count, double amountMultiplier) {
            this.item = item;
            this.count = count;
            this.amountMultiplier = amountMultiplier;
        }
    }

    /***
     * Get items that can be used in the transaction
     * Damageable types can have multiple multipliers
     * Null represents an invalid transaction
     * @param ssArgs Arguments for the Sign
     * @return Map of each shop item to how many to take
     */
    private List<TransactionItem>getItemsSuitableForTransaction(SignShopArguments ssArgs) {
        // Settings
        boolean needsAll = !ssArgs.isOperationParameter("any");
        boolean acceptDamaged = ssArgs.isOperationParameter("acceptdamaged");
        boolean ignorenbt = ssArgs.isOperationParameter("nonbt");
        boolean variableAmount = SignShopConfig.getAllowVariableAmounts() && !ssArgs.isOperationParameter("novariable");

        // Shop Items
        Map<WrappedItem, Integer> shopItems = ItemUtil.mapStacks(ssArgs.getItems().get(), ignorenbt);

        // Player Items
        Map<WrappedItem, Integer> playerItems = ItemUtil.mapStacks(ssArgs.getPlayer().get().getInventoryContents(), ignorenbt);

        // If the transaction should fail
        AtomicBoolean fail = new AtomicBoolean(false);

        List<TransactionItem> suitable = new ArrayList<>();

        // Check each of the shops items
        shopItems.forEach((shopStack, shopCount) -> {
            // Check if the player has the items the shop requires
            // Get player items with the same type
            List<WrappedItem> similarPlayerItems = playerItems.keySet().stream().filter(item -> item.isSimilar(shopStack)).collect(Collectors.toList());

            // Check if no matches
            if (similarPlayerItems.isEmpty()) {
                // If they don't, and they must have all items, the transaction cannot be completed
                if (needsAll) fail.set(true);
                // Skip the remainder of this iteration
                return;
            }

            // For each matching item check if they are suitable
            similarPlayerItems.forEach(currentPlayerItem -> {
                // Direct check
                // Check if there is already a suitable item of this type
                if (suitable.stream().anyMatch(transactionItem -> transactionItem.item.isSimilar(currentPlayerItem)))
                    return; // If we have already filled the requirement, skip it.

                int count = playerItems.get(currentPlayerItem);

                // Get the multiplier
                double multiplier = (double) count / shopCount;
                if (multiplier > 1.0D) {
                    // Bound it to [0,1]
                    multiplier = 1.0D;

                    // Player has more than shop needs, only remove exactly what the shop wants
                    count = shopCount;
                }

                // Check multiplier
                if (multiplier < 1.0D && !variableAmount) {
                    // If player doesn't have enough and VariableAmounts is not enabled, this item isn't suitable
                    // Only fail if we have to have ALL items
                    if (needsAll) fail.set(true);
                } else {
                    // If player has enough OR VariableAmounts is enabled, check durability
                    if (currentPlayerItem.durability > 0 && !acceptDamaged) {
                        // If the item is damaged AND we don't accept damaged items, it's not suitable
                        // Only fail if we have to have ALL items
                        if (needsAll) fail.set(true);
                    } else {
                        // If the item passes all these checks, it's suitable
                        suitable.add(new TransactionItem(currentPlayerItem, count, multiplier));
                    }
                }
            });
        });

        // If the transaction cannot be completed return nothing
        if (fail.get() || suitable.size() == 0) return null;

        return suitable;
    }

    @Override
    public Boolean setupOperation(SignShopArguments ssArgs) {
        // No linked chests
        if (ssArgs.getContainables().isEmpty()) {
            if (ssArgs.isOperationParameter("allowNoChests"))
                return true;
            ssArgs.getPlayer().get().sendMessage(SignShopConfig.getError("chest_missing", ssArgs.getMessageParts()));
            return false;
        }

        // Get items from all chests
        ItemStack[] isTotalItems = itemUtil.getAllItemStacksForContainables(ssArgs.getContainables().get());

        // Chests are empty
        if (isTotalItems.length == 0) {
            ssArgs.getPlayer().get().sendMessage(SignShopConfig.getError("chest_empty", ssArgs.getMessageParts()));
            return false;
        }

        // Update the items we're working with
        ssArgs.getItems().set(isTotalItems);
        ssArgs.setMessagePart("!items", itemUtil.itemStackToString(ssArgs.getItems().get()));
        return true;
    }

    @Override
    public Boolean checkRequirements(SignShopArguments ssArgs, Boolean activeCheck) {
        // If player exists (NOT if they are online)
        if (!ssArgs.isPlayerOnline())
            return true;

        // If shop has no define items
        if (ssArgs.getItems().get() == null) {
            ssArgs.getPlayer().get().sendMessage(SignShopConfig.getError("no_items_defined_for_shop", ssArgs.getMessageParts()));
            return false;
        }

        // Get the items that could be used for the transaction
        List<TransactionItem> availableItems = getItemsSuitableForTransaction(ssArgs);

        if (availableItems == null) {
            ssArgs.sendFailedRequirementsMessage("player_doesnt_have_items");
            return false;
        }

        // Get the transaction item wrappers
        List<TransactionItem> transactionItems = filterItems(availableItems, ssArgs.isOperationParameter("any"));
        // Get the unwrapped itemstacks
        ItemStack[] unwrappedTransactionItems = unwrapTranscationItems(transactionItems);

        // Update the items in the transaction
        ssArgs.getItems().set(unwrappedTransactionItems);
        // Update the message with the items selected for the transaction
        ssArgs.setMessagePart("!items", itemUtil.itemStackToString(unwrappedTransactionItems));

        // Calculate a weighted average for the available multipliers
        double totalMultiplier = 0.0D;
        for (TransactionItem transactionItem : transactionItems)
            totalMultiplier += transactionItem.amountMultiplier * transactionItem.item.getDamageMultiplier();
        totalMultiplier /= transactionItems.size();

        // Set the price
        ssArgs.getPrice().set(ssArgs.getPrice().get() * totalMultiplier);

        return true;
    }

    @Override
    public Boolean runOperation(SignShopArguments ssArgs) {
        if (!checkRequirements(ssArgs, true))
            return false;
        boolean transactedAll = ssArgs.getPlayer().get().takePlayerItems(ssArgs.getItems().get()).isEmpty();
        if (!transactedAll)
            ssArgs.getPlayer().get().sendMessage(SignShopConfig.getError("could_not_complete_operation", null));
        return transactedAll;
    }

    private List<TransactionItem> filterItems(List<TransactionItem> items, boolean pickOne) {
        List<TransactionItem> transactionItems = new ArrayList<>();
        if (pickOne) {
            // Pick one
            transactionItems.add(
                    items.get((new Random()).nextInt(items.size()))
            );
        } else {
            // All items
            transactionItems.addAll(items);
        }

        return transactionItems;
    }

    private ItemStack[] unwrapTranscationItems(List<TransactionItem> items) {
        return items.stream().map(transactionItem -> transactionItem.item.baseStack).toArray(ItemStack[]::new);
    }
}
