package net.md_5.janus.util;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.Collection;

public class BlockPosition {

    private final int x;
    private final int y;
    private final int z;

    public BlockPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static BlockPosition fromLocation(Location location) {
        return new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public BlockPosition setX(int x) {
        return new BlockPosition(x, this.y, this.z);
    }

    public BlockPosition setY(int y) {
        return new BlockPosition(this.x, y, this.z);
    }

    public BlockPosition setZ(int z) {
        return new BlockPosition(this.x, this.y, z);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockPosition)) return false;

        BlockPosition that = (BlockPosition) o;

        return x == that.x && y == that.y && z == that.z;

    }

    @Override
    public int hashCode() {
        return (x ^ y ^ z) % 3;
    }

    public static void addAllFromLocation(Collection<BlockPosition> positions, Collection<Block> blocks) {
        for (Block block : blocks) {
            positions.add(fromLocation(block.getLocation()));
        }
    }

    @Override
    public String toString() {
        return "BlockPosition{ " +
                x +
                ", " + y +
                ", " + z +
                '}';
    }
}
