package me.advait.mai.planner;
import me.advait.mai.body.Humanoid;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.checkerframework.checker.units.qual.A;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;

/*
https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/inventory/ItemStack.html
https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Material.html#getMaterial(java.lang.String)
https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/inventory/Inventory.html
https://www.spigotmc.org/threads/how-to-get-item-crafting-recipes.444639/
https://bukkit.org/threads/getting-an-items-recipe.490989/
https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Bukkit.html#getRecipesFor(org.bukkit.inventory.ItemStack)
https://docs.oracle.com/javase/8/docs/api/java/util/List.html
 */

public class CraftingPlanner {
    private ArrayList<ItemStack> inventory;
    public CraftingPlanner(Humanoid humanoid){
        this.inventory = new ArrayList<ItemStack>();
        for(ItemStack inventorySlot : humanoid.getInventory()){
            this.inventory.add(inventorySlot);
        }
    }
    public CraftingPlanner(Player player){
        this.inventory = new ArrayList<ItemStack>();
        for(ItemStack inventorySlot : player.getInventory()){
            this.inventory.add(inventorySlot);
        }
    }

    private ArrayList<ItemStack> getIngredients(ItemStack goal){
        ArrayList<ItemStack> ingredients = new ArrayList<ItemStack>();
        List<Recipe> recipes = Bukkit.getRecipesFor(goal);
        if(recipes.isEmpty())
            return new ArrayList<ItemStack>();
        Recipe recipe = recipes.getFirst();
        int amount = Math.ceilDiv ( goal.getAmount(), recipe.getResult().getAmount());
        if (recipe instanceof ShapedRecipe){
            Map<Character, ItemStack> recipeIngredients = ((ShapedRecipe)recipe).getIngredientMap();
            Set<Character> keys = recipeIngredients.keySet();
            for (Character key : keys){
                if(recipeIngredients.get(key) == null)
                    continue;
                Material ingredientMaterial = recipeIngredients.get(key).getType();
                boolean foundIngredient = false;
                for (ItemStack ingredient : ingredients) {
                    if (ingredient.getType().equals(ingredientMaterial)) {
                        ingredient.setAmount(ingredient.getAmount() + amount);
                        foundIngredient = true;
                    }
                }
                if (!foundIngredient){
                    ingredients.add(new ItemStack(ingredientMaterial, amount));
                }
            }
        }
        if(recipe instanceof ShapelessRecipe){
            List<ItemStack> temp = ((ShapelessRecipe) recipe).getIngredientList();
            for(ItemStack item : temp){
                item.setAmount(item.getAmount()*amount);
            }
            ingredients.addAll(temp);
        }
        return ingredients;
    }

    public String craftItem(String name, int amount){
        for(ItemStack inventorySlot : inventory){
            if(inventorySlot != null) {Bukkit.broadcastMessage(inventorySlot.toString());}
            else{Bukkit.broadcastMessage("Null");}
        }
        Material material = Material.getMaterial(name);
        ItemStack goal;
        if (material == null)
            return ("Unable to find material " + name);
        else
            goal = new ItemStack(material, amount);
        ArrayList<ItemStack> temp = new ArrayList<ItemStack>();
        ArrayList<ItemStack> queue = new ArrayList<ItemStack>();
        queue.add(goal);
        while (!queue.isEmpty()){
            ItemStack currentItem = queue.removeFirst();
            for(int i = 0; i < inventory.size(); i++){
                if (inventory.get(i) == null){
                    Bukkit.broadcastMessage("MUSTAAAAAAAAAAAARD");
                    inventory.set(i,currentItem);
                    break;
                }
            }
            temp = getIngredients(currentItem);
            if(!temp.isEmpty()){
                queue.addAll(temp);
            }
        }
        for(ItemStack inventorySlot : inventory){
            if(inventorySlot != null) {Bukkit.broadcastMessage(inventorySlot.toString());}
            else{Bukkit.broadcastMessage("Null");}
        }

        String test = "";
        for(ItemStack ingredient : inventory){
            if (ingredient != null){
                test += ingredient.toString();
            }
        }

        return test;
        //return "Item crafted successfully";
    }

}
