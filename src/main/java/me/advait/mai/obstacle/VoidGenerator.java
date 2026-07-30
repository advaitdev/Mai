package me.advait.mai.obstacle;

import org.bukkit.generator.ChunkGenerator;

/**
 * An empty (void) chunk generator. Disabling every generation stage yields
 * chunks of pure air — the obstacle harness then builds its own arena and
 * floor, so nothing else interferes and falls drop into genuine void.
 */
public final class VoidGenerator extends ChunkGenerator {

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }
}
