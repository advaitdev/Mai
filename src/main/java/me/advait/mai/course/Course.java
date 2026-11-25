package me.advait.mai.course;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A course: a captured box of real terrain, a start (exact position and
 * facing) and a finish (the spot to stand on). Humans and bots run the same
 * copy of it in the course world, so their times compare.
 *
 * <p>Blocks are stored as a palette of block-data strings and one palette
 * index per cell, indexed {@code (x * sizeY + y) * sizeZ + z}. Positions are
 * relative to the box's minimum corner; the box keeps its source Y, only X
 * and Z move.
 */
public record Course(String name, String description, String source,
                     int baseY, int sizeX, int sizeY, int sizeZ,
                     List<String> palette, int[] blocks,
                     double startX, double startY, double startZ, float startYaw,
                     double endX, double endY, double endZ,
                     int timeoutTicks) {

    private static final String MAGIC = "MAICOURSE";
    private static final int VERSION = 1;

    public int index(int x, int y, int z) { return (x * sizeY + y) * sizeZ + z; }

    /** Straight-line distance start to finish, for display. */
    public double distance() {
        return Math.sqrt(Math.pow(endX - startX, 2) + Math.pow(endY - startY, 2) + Math.pow(endZ - startZ, 2));
    }

    public void save(File file) throws IOException {
        file.getParentFile().mkdirs();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(file)))) {
            out.writeUTF(MAGIC);
            out.writeInt(VERSION);
            out.writeUTF(name);
            out.writeUTF(description);
            out.writeUTF(source);
            out.writeInt(baseY);
            out.writeInt(sizeX);
            out.writeInt(sizeY);
            out.writeInt(sizeZ);
            out.writeInt(palette.size());
            for (String s : palette) out.writeUTF(s);
            for (int b : blocks) writeVarInt(out, b);
            out.writeDouble(startX);
            out.writeDouble(startY);
            out.writeDouble(startZ);
            out.writeFloat(startYaw);
            out.writeDouble(endX);
            out.writeDouble(endY);
            out.writeDouble(endZ);
            out.writeInt(timeoutTicks);
        }
    }

    public static Course load(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new FileInputStream(file)))) {
            if (!MAGIC.equals(in.readUTF())) throw new IOException("not a course file");
            int version = in.readInt();
            if (version != VERSION) throw new IOException("unsupported course version " + version);
            String name = in.readUTF(), description = in.readUTF(), source = in.readUTF();
            int baseY = in.readInt(), sx = in.readInt(), sy = in.readInt(), sz = in.readInt();
            int n = in.readInt();
            String[] palette = new String[n];
            for (int i = 0; i < n; i++) palette[i] = in.readUTF();
            int[] blocks = new int[sx * sy * sz];
            for (int i = 0; i < blocks.length; i++) blocks[i] = readVarInt(in);
            return new Course(name, description, source, baseY, sx, sy, sz, List.of(palette), blocks,
                    in.readDouble(), in.readDouble(), in.readDouble(), in.readFloat(),
                    in.readDouble(), in.readDouble(), in.readDouble(), in.readInt());
        }
    }

    private static void writeVarInt(DataOutputStream out, int v) throws IOException {
        while ((v & ~0x7F) != 0) {
            out.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.writeByte(v);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int v = 0, shift = 0, b;
        do {
            b = in.readUnsignedByte();
            v |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return v;
    }
}
