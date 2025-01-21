package me.advait.mai.planner;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.*;
import java.util.ArrayList;
import java.util.List;

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
    public String craftItem(String name, int amount){
        Material material = Material.getMaterial(name);
        ItemStack goal;
        if (material == null)
            return ("Unable to find material " + name);
        else
            goal = new ItemStack(material, amount);
        ArrayList<ItemStack> ingredients = new ArrayList<ItemStack>();
        List<Recipe> recipes = Bukkit.getRecipesFor(goal);


        return "Item crafted successfully";
    }
}
