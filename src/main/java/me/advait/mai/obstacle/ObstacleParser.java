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

    private static final int MAX_DIM = 32;

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
                    case 'S', 's' -> {
                        if (startRow != -1) throw new ParseException("Multiple start (S) markers found.");
                        startRow = r; startCol = c;
                        grid[r][c] = ObstacleFile.Cell.stack(suffixHeight(tok, "S"));
                    }
                    case 'E', 'e' -> {
                        if (endRow != -1) throw new ParseException("Multiple end (E) markers found.");
                        endRow = r; endCol = c;
                        grid[r][c] = ObstacleFile.Cell.stack(suffixHeight(tok, "E"));
                    }
                    default -> {
                        int h = parseInt(tok, "grid cell");
                        if (h < 0) throw new ParseException("Negative cell height: " + tok);
                        grid[r][c] = ObstacleFile.Cell.stack(h);
                    }
                }
            }
        }

        if (startRow == -1) throw new ParseException("No start (S) marker found.");
        if (endRow == -1) throw new ParseException("No end (E) marker found.");

        return new ObstacleFile(stem, name, description, material, floorMaterial,
                timeoutTicks, spawnYaw, originY, botTool, botBlock, botBlockCount,
                ceiling, grid, startRow, startCol, endRow, endCol);
    }

    private static int suffixHeight(String tok, String marker) throws ParseException {
        if (tok.length() == 1) return 0;
        return parseInt(tok.substring(1), marker + " pillar height");
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
