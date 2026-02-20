package me.advait.mai;

import me.advait.mai.body.Humanoid;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;

public class Catalog {

    private static final Catalog INSTANCE = new Catalog();

    public static Catalog getInstance() {
        return INSTANCE;
    }

    private final List<Humanoid> humanoids = new ArrayList<>();

    public void registerHumanoid(String npcName) {
        Humanoid humanoid = new Humanoid(npcName);
        humanoid.spawn(Bukkit.getWorlds().getFirst().getSpawnLocation());
        humanoids.add(humanoid);
    }

    public List<Humanoid> getAllHumanoids() {
        return humanoids;
    }

    public void killAll() {
        for (Humanoid h : humanoids) {
            if (h.getEntity() != null && h.getEntity().isValid()) {
                h.getEntity().remove();
            }
        }
        humanoids.clear();
    }

}
