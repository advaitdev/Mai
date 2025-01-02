package me.advait.mai;

import me.advait.mai.body.Humanoid;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;

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
        humanoid.getNpc().spawn(Bukkit.getWorlds().getFirst().getSpawnLocation());
        humanoids.add(humanoid);
    }

    public List<Humanoid> getAllHumanoids() {
        return humanoids;
    }

    public void killAll() {
        CitizensAPI.getNPCRegistries().forEach(registry -> registry.sorted().forEach(NPC::destroy));
        humanoids.clear();
    }

}
