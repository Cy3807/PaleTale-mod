package cn.paletale.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads already-loaded sign block entities around the player; never queries the server or other players. */
final class ShopScanner {
    record Found(ShopSigns.Reading reading, List<String> raw, double distance) {}
    record Sign(String world, int x, int y, int z, List<String> raw, double distance, String chestDir) {}
    private ShopScanner() {}
    static String world(Minecraft client) { return client.level.dimension().identifier().toString(); }
    /** Main-thread lookup at a known bound position; never loads a chunk or retains its mutable entity. */
    static ShopScanData.Sample snapshot(Minecraft client, ShopApi.Position position, ShopSigns.Reading previous, long sentAt) {
        if(!client.isSameThread())throw new IllegalStateException("World snapshots require the client thread");
        if(client.level==null||client.player==null||!world(client).equals(position.world())
                ||ShopScanData.distanceSquared(position,client.player.getX(),client.player.getY(),client.player.getZ())>48*48)return null;
        var chunk=client.level.getChunkSource().getChunkNow(position.x()>>4,position.z()>>4);
        if(chunk==null||!(chunk.getBlockEntities().get(new BlockPos(position.x(),position.y(),position.z())) instanceof SignBlockEntity sign))return null;
        List<String> lines=new ArrayList<>(4);
        for(var line:sign.getFrontText().getMessages(false))lines.add(line.getString());
        return new ShopScanData.Sample(position,lines,chestDirOf(sign),previous,sentAt);
    }
    /** Every sign within {@code radius} blocks (horizontal chunks sweep, spherical distance filter). */
    static List<Sign> signs(Minecraft client, int radius) {
        List<Sign> found = new ArrayList<>();
        if (client.level == null || client.player == null) return found;
        String world = world(client);
        int cx = client.player.blockPosition().getX() >> 4, cz = client.player.blockPosition().getZ() >> 4, chunks = (radius >> 4) + 1;
        double limit = (double) radius * radius;
        for (int dx = -chunks; dx <= chunks; dx++) for (int dz = -chunks; dz <= chunks; dz++) {
            LevelChunk chunk = client.level.getChunkSource().getChunkNow(cx + dx, cz + dz);
            if (chunk == null) continue;
            for (var entity : chunk.getBlockEntities().values()) {
                if (!(entity instanceof SignBlockEntity sign) || found.size() >= 400) continue;
                BlockPos pos = sign.getBlockPos();
                double distance = client.player.position().distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
                if (distance > limit) continue;
                List<String> raw = new ArrayList<>();
                for (var line : sign.getFrontText().getMessages(false)) raw.add(line.getString());
                found.add(new Sign(world, pos.getX(), pos.getY(), pos.getZ(), raw, Math.sqrt(distance), chestDirOf(sign)));
            }
        }
        found.sort(java.util.Comparator.comparingDouble(Sign::distance));
        return found;
    }
    /**
     * The direction of the block a wall sign is attached to (the chest sits there): the opposite of the way the sign faces, as x+ / x- / z+ / z-.
     * Only that one block is considered; a standing sign (no facing) has no known chest.
     */
    static String chestDirOf(SignBlockEntity sign) {
        var state = sign.getBlockState();
        if (!state.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) return null;
        return switch (state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).getOpposite()) {
            case EAST -> "x+"; case WEST -> "x-"; case SOUTH -> "z+"; case NORTH -> "z-"; default -> null;
        };
    }
    static List<Found> shops(Minecraft client, int radius) {
        List<Found> shops = new ArrayList<>();
        for (Sign sign : signs(client, radius)) {
            Optional<ShopSigns.Reading> parsed = ShopSigns.parse(sign.world(), sign.x(), sign.y(), sign.z(), sign.raw());
            parsed.ifPresent(reading -> shops.add(new Found(sign.chestDir() == null ? reading : reading.withChestDir(sign.chestDir()), sign.raw(), sign.distance())));
        }
        return shops;
    }
}
