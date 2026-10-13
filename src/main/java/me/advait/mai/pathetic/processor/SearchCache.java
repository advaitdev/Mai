package me.advait.mai.pathetic.processor;

import de.bsommerfeld.pathetic.api.pathing.context.EnvironmentContext;
import de.bsommerfeld.pathetic.api.provider.NavigationPointProvider;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.movement.MaterialProvider;
import me.advait.mai.pathetic.movement.MovementType;
import org.bukkit.Material;

import java.util.Arrays;

/**
 * Per-search scratch state shared by {@link HumanoidValidationProcessor} and
 * {@link HumanoidCostProcessor}. One instance is created per pathfinder
 * build (i.e. per path request) in {@code PatheticAgent}, and Pathetic runs
 * a search on a single executor thread, so nothing here is synchronized.
 *
 * <p>Two caches:
 * <ul>
 *   <li><b>Materials</b>: an open-addressing {@code long to Material} map in
 *       front of the navigation-point provider. A* re-reads the same columns
 *       hundreds of times (every expanded node probes its neighbours at
 *       feet/head/floor level, and neighbour sets overlap heavily); each raw
 *       lookup allocates a PathPosition and a navigation point and walks the
 *       chunk-snapshot map. Caching turns that into one array probe.
 *   <li><b>Classification</b>: Pathetic validates a transition and then,
 *       if valid, costs it, both via {@code registry.classify}. A one-entry
 *       cache keyed on (previous, current) makes the second call free.
 * </ul>
 */
public final class SearchCache {

    private static final long EMPTY = Long.MIN_VALUE;

    private long[] keys;
    private Material[] values;
    private int size;
    private int mask;

    private NavigationPointProvider provider;
    private EnvironmentContext env;
    private MaterialProvider cachedProvider;

    // One-entry classification cache.
    private long lastPrev = EMPTY, lastCurr = EMPTY;
    private MovementType lastType;
    private boolean lastValid;

    public SearchCache() {
        allocate(1 << 12);
    }

    private void allocate(int capacity) {
        keys = new long[capacity];
        values = new Material[capacity];
        Arrays.fill(keys, EMPTY);
        size = 0;
        mask = capacity - 1;
    }

    // =========================================================================
    // Materials
    // =========================================================================

    /** Returns a caching {@link MaterialProvider} bound to this search's provider. */
    public MaterialProvider materials(NavigationPointProvider provider, EnvironmentContext env) {
        if (cachedProvider == null || this.provider != provider || this.env != env) {
            this.provider = provider;
            this.env = env;
            MaterialProvider raw = MaterialProvider.fromNavigationProvider(provider, env);
            this.cachedProvider = (x, y, z) -> get(x, y, z, raw);
        }
        return cachedProvider;
    }

    private Material get(int x, int y, int z, MaterialProvider raw) {
        long key = pack(x, y, z);
        int i = hash(key) & mask;
        while (true) {
            long k = keys[i];
            if (k == key) return values[i];
            if (k == EMPTY) break;
            i = (i + 1) & mask;
        }
        Material m = raw.getMaterial(x, y, z);
        if (size * 2 >= keys.length) {
            rehash();
            i = hash(key) & mask;
            while (keys[i] != EMPTY) i = (i + 1) & mask;
        }
        keys[i] = key;
        values[i] = m;
        size++;
        return m;
    }

    private void rehash() {
        long[] oldKeys = keys;
        Material[] oldValues = values;
        allocate(oldKeys.length << 1);
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] == EMPTY) continue;
            int j = hash(oldKeys[i]) & mask;
            while (keys[j] != EMPTY) j = (j + 1) & mask;
            keys[j] = oldKeys[i];
            values[j] = oldValues[i];
            size++;
        }
    }

    /** Packs block coordinates into one long (26/12/26 bits, like BlockPos). */
    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFFL);
    }

    static long pack(PathPosition p) {
        return pack(p.getFlooredX(), p.getFlooredY(), p.getFlooredZ());
    }

    private static int hash(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32));
    }

    // =========================================================================
    // Classification
    // =========================================================================

    public void storeClassification(PathPosition previous, PathPosition current, MovementType type) {
        lastPrev = pack(previous);
        lastCurr = pack(current);
        lastType = type;
        lastValid = true;
    }

    public boolean isCached(PathPosition previous, PathPosition current) {
        return lastValid && pack(previous) == lastPrev && pack(current) == lastCurr;
    }

    public MovementType cachedType() {
        return lastType;
    }

    public int materialEntries() {
        return size;
    }
}
