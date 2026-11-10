package cn.paletale.companion;

/** Strict user-entered block coordinates; the caller always assigns the main world. */
public record NavCoordinates(int x, Integer y, int z) {
    public static NavCoordinates parse(String value) {
        if (value == null || !value.strip().matches("[+-]?\\d{1,8}(?:\\s+[+-]?\\d{1,8}){1,2}")) return null;
        String[] parts = value.strip().split("\\s+");
        int x = Integer.parseInt(parts[0]), z = Integer.parseInt(parts[parts.length - 1]);
        Integer y = parts.length == 3 ? Integer.valueOf(parts[1]) : null;
        if (Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || (y != null && (y < -2048 || y > 2047))) return null;
        return new NavCoordinates(x, y, z);
    }
}
