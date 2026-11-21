package me.advait.mai.obstacle;

import org.bukkit.Material;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses {@code .obstacle} text into an {@link ObstacleFile}. The format is two
 * sections separated by a line containing exactly {@code ---}: a YAML-style
 * key/value header above, and a whitespace-separated ASCII grid below. See
 * {@code src/main/resources/obstacles/} for examples and {@code CLAUDE.md} for
 * the full grammar.
 *
 * <p>All failures throw {@link ParseException} with a human-readable message so
 * the command surface can report them verbatim.
 */
public final class ObstacleParser {

    private static final int MAX_DIM = 64;

    public static final class ParseException extends Exception {
        public ParseException(String message) { super(message); }
    }

    private ObstacleParser() {}

    public static ObstacleFile parse(Path file) throws ParseException {
        String stem = file.getFileName().toString().replaceFirst("\\.obstacle$", "");
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new ParseException("Could not read file: " + e.getMessage());
        }
        return parse(stem, lines);
    }

    static ObstacleFile parse(String stem, List<String> lines) throws ParseException {
        int sep = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).strip().equals("---")) { sep = i; break; }
        }
        if (sep < 0) {
            throw new ParseException("Missing '---' separator between header and grid.");
        }

        // --- Header ---
        String name = null, description = "";
        Material material = Material.GOLD_BLOCK;
        Material floorMaterial = null;
        Material botTool = null, botBlock = null;
        int botBlockCount = 0, ceiling = 0;
        boolean expectNoPath = false;
        java.util.Map<Character, org.bukkit.block.data.BlockData> legend = new java.util.HashMap<>();
        java.util.List<ObstacleFile.Item> botItems = new java.util.ArrayList<>();
        java.util.List<String> placeSpecs = new java.util.ArrayList<>();
        double[] knockback = null;
        int timeoutTicks = 200, spawnYaw = 0, originY = 120;

        for (int i = 0; i < sep; i++) {
            String raw = lines.get(i).strip();
            if (raw.isEmpty() || raw.startsWith("#")) continue;
            int colon = raw.indexOf(':');
            if (colon < 0) throw new ParseException("Malformed header line (expected 'key: value'): " + raw);
            String key = raw.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = raw.substring(colon + 1).strip();
            switch (key) {
                case "name" -> name = value;
                case "description" -> description = value;
                case "material" -> material = resolveMaterial(value, "material");
                case "floor_material" -> floorMaterial = resolveMaterial(value, "floor_material");
                case "timeout_ticks" -> timeoutTicks = parseInt(value, "timeout_ticks");
                case "spawn_yaw" -> spawnYaw = parseInt(value, "spawn_yaw");
                case "origin_y" -> originY = parseInt(value, "origin_y");
                case "bot_tool" -> botTool = resolveItem(value, "bot_tool");
                case "bot_blocks" -> {
                    // "<Material> [count]" — default count 256
                    String[] parts = value.split("\\s+");
                    botBlock = resolveMaterial(parts[0], "bot_blocks");
                    botBlockCount = parts.length > 1 ? parseInt(parts[1], "bot_blocks count") : 256;
                }
                case "ceiling" -> ceiling = parseInt(value, "ceiling");
                case "blocks" -> parseLegend(value, legend);
                case "bot_items" -> parseItems(value, botItems);
                case "place" -> placeSpecs.add(value);
                case "knockback" -> {
                    // "<tick> <vx> <vy> <vz>": shove the bot at that tick, like a hit
                    String[] kb = value.trim().split("\\s+");
                    if (kb.length != 4) throw new ParseException("knockback must be '<tick> <vx> <vy> <vz>': " + value);
                    try {
                        knockback = new double[]{Integer.parseInt(kb[0]), Double.parseDouble(kb[1]),
                                Double.parseDouble(kb[2]), Double.parseDouble(kb[3])};
                    } catch (NumberFormatException e) {
                        throw new ParseException("knockback must be '<tick> <vx> <vy> <vz>': " + value);
                    }
                }
                case "expect" -> {
                    switch (value.toLowerCase(Locale.ROOT)) {
                        case "pass" -> expectNoPath = false;
                        case "no_path" -> expectNoPath = true;
                        default -> throw new ParseException("expect must be 'pass' or 'no_path': " + value);
                    }
                }
                default -> throw new ParseException("Unknown header key: " + key);
            }
        }
        if (name == null || name.isEmpty()) {
            throw new ParseException("Header is missing required 'name' key.");
        }
        if (floorMaterial == null) floorMaterial = material;

        // --- Grid ---
        List<String[]> rowTokens = new ArrayList<>();
        int maxCols = 0;
        for (int i = sep + 1; i < lines.size(); i++) {
            String raw = lines.get(i).strip();
            if (raw.isEmpty() || raw.startsWith("#")) continue;
            String[] tokens = raw.split("\\s+");
            rowTokens.add(tokens);
            maxCols = Math.max(maxCols, tokens.length);
        }
        if (rowTokens.isEmpty()) throw new ParseException("Grid is empty.");
        int rows = rowTokens.size();
        if (rows > MAX_DIM || maxCols > MAX_DIM) {
            throw new ParseException("Grid exceeds " + MAX_DIM + "x" + MAX_DIM + " (got " + rows + "x" + maxCols + ").");
        }

        ObstacleFile.Cell[][] grid = new ObstacleFile.Cell[rows][maxCols];
        int startRow = -1, startCol = -1, endRow = -1, endCol = -1;

        for (int r = 0; r < rows; r++) {
            String[] tokens = rowTokens.get(r);
            for (int c = 0; c < maxCols; c++) {
                if (c >= tokens.length) { grid[r][c] = ObstacleFile.Cell.FLOOR; continue; }
                String tok = tokens[c];
                char head = tok.charAt(0);
                switch (head) {
                    case '.' -> grid[r][c] = ObstacleFile.Cell.FLOOR;
                    case '_' -> grid[r][c] = ObstacleFile.Cell.VOID;
                    case 'S' -> {
                        if (startRow != -1) throw new ParseException("Multiple start (S) markers found.");
                        startRow = r; startCol = c;
                        grid[r][c] = stackCell(tok.substring(1), legend, "S");
                    }
                    case 'E' -> {
                        if (endRow != -1) throw new ParseException("Multiple end (E) markers found.");
                        endRow = r; endCol = c;
                        grid[r][c] = stackCell(tok.substring(1), legend, "E");
                    }
                    default -> grid[r][c] = stackCell(tok, legend, "grid cell");
                }
            }
        }

        if (startRow == -1) throw new ParseException("No start (S) marker found.");
        if (endRow == -1) throw new ParseException("No end (E) marker found.");

        java.util.List<ObstacleFile.Placed> placed = new java.util.ArrayList<>();
        for (String spec : placeSpecs) parsePlaced(spec, placed, rows, maxCols);

        return new ObstacleFile(stem, name, description, material, floorMaterial,
                timeoutTicks, spawnYaw, originY, botTool, botBlock, botBlockCount,
                ceiling, expectNoPath, grid, startRow, startCol, endRow, endCol,
                java.util.List.copyOf(botItems), java.util.List.copyOf(placed), knockback);
    }

    /**
     * A stack token: optional digits (blocks of {@code material}) followed by
     * an optional lowercase legend letter (one more block of that type on
     * top). {@code 2} = two blocks, {@code a} = one 'a' block, {@code 2a} =
     * two blocks then an 'a'. {@code N*x} is N blocks of legend {@code x}
     * (a pond: {@code 3*w}); {@code 2+3*w} is two blocks then three 'w'.
     * Empty = floor only.
     */
    private static ObstacleFile.Cell stackCell(String tok, java.util.Map<Character, org.bukkit.block.data.BlockData> legend,
                                               String what) throws ParseException {
        if (tok.isEmpty()) return ObstacleFile.Cell.stack(0);
        char last = tok.charAt(tok.length() - 1);
        if (Character.isDigit(last)) {
            int h = parseInt(tok, what + " height");
            if (h < 0) throw new ParseException("Negative cell height: " + tok);
            return ObstacleFile.Cell.stack(h);
        }
        org.bukkit.block.data.BlockData data = legend.get(last);
        if (data == null) throw new ParseException("Unknown block letter '" + last + "' in " + what + " (define it in 'blocks:')");
        String digits = tok.substring(0, tok.length() - 1);
        int star = digits.indexOf('*');
        if (star >= 0) {
            // [base+]N*x
            String head = digits.substring(0, star);
            int plus = head.indexOf('+');
            int base = plus >= 0 ? parseInt(head.substring(0, plus), what + " height") : 0;
            int n = parseInt(plus >= 0 ? head.substring(plus + 1) : head, what + " repeat");
            if (n < 1 || star != digits.length() - 1) throw new ParseException("Bad repeat token: " + tok);
            return new ObstacleFile.Cell(true, base + n, data, n);
        }
        int base = digits.isEmpty() ? 0 : parseInt(digits, what + " height");
        return new ObstacleFile.Cell(true, base + 1, data, 1);
    }

    /** {@code bot_items: water_bucket cobblestone:64} */
    private static void parseItems(String value, java.util.List<ObstacleFile.Item> items) throws ParseException {
        for (String entry : value.trim().split("\\s+")) {
            if (entry.isEmpty()) continue;
            String[] parts = entry.split(":");
            Material m = resolveItem(parts[0], "bot_items");
            int count = parts.length > 1 ? parseInt(parts[1], "bot_items count") : 1;
            items.add(new ObstacleFile.Item(m, count));
        }
    }

    /**
     * {@code place: 3,2,0=oak_log 4,3,1=ladder[facing=west]}: column, height
     * above the floor (1 = first block above it), row.
     */
    private static void parsePlaced(String value, java.util.List<ObstacleFile.Placed> out,
                                    int rows, int cols) throws ParseException {
        for (String entry : value.trim().split("\\s+")) {
            if (entry.isEmpty()) continue;
            int eq = entry.indexOf('=');
            if (eq < 0) throw new ParseException("place: entries must be 'col,height,row=block': " + entry);
            String[] xyz = entry.substring(0, eq).split(",");
            if (xyz.length != 3) throw new ParseException("place: coordinates must be col,height,row: " + entry);
            int col = parseInt(xyz[0], "place col"), h = parseInt(xyz[1], "place height"), row = parseInt(xyz[2], "place row");
            if (col < 0 || col >= cols || row < 0 || row >= rows || h < 0)
                throw new ParseException("place: out of the grid: " + entry);
            String spec = entry.substring(eq + 1);
            String id = spec.contains(":") ? spec : "minecraft:" + spec.toLowerCase(Locale.ROOT);
            try {
                out.add(new ObstacleFile.Placed(col, h, row, org.bukkit.Bukkit.createBlockData(id)));
            } catch (IllegalArgumentException e) {
                throw new ParseException("Invalid block in place: " + spec);
            }
        }
    }

    /** {@code blocks: a=OAK_SLAB b=oak_stairs[facing=east] d=OAK_DOOR} */
    private static void parseLegend(String value, java.util.Map<Character, org.bukkit.block.data.BlockData> legend)
            throws ParseException {
        for (String entry : value.trim().split("\\s+")) {
            int eq = entry.indexOf('=');
            if (eq != 1 || !Character.isLowerCase(entry.charAt(0)) || entry.charAt(0) == 's' || entry.charAt(0) == 'e') {
                throw new ParseException("blocks: entries must be '<letter>=<block>' with a lowercase letter other than s/e: " + entry);
            }
            String spec = entry.substring(2);
            try {
                String id = spec.contains(":") ? spec : "minecraft:" + spec.toLowerCase(Locale.ROOT);
                legend.put(entry.charAt(0), org.bukkit.Bukkit.createBlockData(id));
            } catch (IllegalArgumentException e) {
                throw new ParseException("Invalid block in blocks: " + spec);
            }
        }
    }

    private static Material resolveMaterial(String value, String key) throws ParseException {
        Material m = Material.matchMaterial(value);
        if (m == null || !m.isBlock()) {
            throw new ParseException("Invalid block material for " + key + ": " + value);
        }
        return m;
    }

    private static Material resolveItem(String value, String key) throws ParseException {
        Material m = Material.matchMaterial(value);
        if (m == null) throw new ParseException("Invalid item for " + key + ": " + value);
        return m;
    }

    private static int parseInt(String value, String key) throws ParseException {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            throw new ParseException("Invalid integer for " + key + ": " + value);
        }
    }
}
