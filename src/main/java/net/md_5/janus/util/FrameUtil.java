package net.md_5.janus.util;

import net.md_5.janus.PlaneAlignment;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class FrameUtil {

    public static Collection<Block> getPortalAbleBlocks(List<Block> frameBlocks, PlaneAlignment alignment, Material frameMaterial, Player player) {
        int minHorizontal = Integer.MAX_VALUE;
        int maxHorizontal = Integer.MIN_VALUE;
        int minVertical = Integer.MAX_VALUE;
        int maxVertical = Integer.MIN_VALUE;

        Block min;
        Block max;
        if (alignment == PlaneAlignment.NORTH_SOUTH) {
            for (Block block : frameBlocks) {
                if (block.getLocation().getBlockZ() > maxHorizontal) {
                    maxHorizontal = block.getLocation().getBlockZ();
                }
                if (block.getLocation().getBlockZ() < minHorizontal) {
                    minHorizontal = block.getLocation().getBlockZ();
                }
                if (block.getLocation().getBlockY() > maxVertical) {
                    maxVertical = block.getLocation().getBlockY();
                }
                if (block.getLocation().getBlockY() < minVertical) {
                    minVertical = block.getLocation().getBlockY();
                }
            }
            min = new Location(frameBlocks.get(0).getWorld(), frameBlocks.get(0).getX(), minVertical, minHorizontal).getBlock();
            max = new Location(frameBlocks.get(0).getWorld(), frameBlocks.get(0).getX(), maxVertical, maxHorizontal).getBlock();
        } else {
            for (Block block : frameBlocks) {
                if (block.getLocation().getBlockX() > maxHorizontal) {
                    maxHorizontal = block.getLocation().getBlockX();
                }
                if (block.getLocation().getBlockX() < minHorizontal) {
                    minHorizontal = block.getLocation().getBlockX();
                }
                if (block.getLocation().getBlockY() > maxVertical) {
                    maxVertical = block.getLocation().getBlockY();
                }
                if (block.getLocation().getBlockY() < minVertical) {
                    minVertical = block.getLocation().getBlockY();
                }
            }
            min = new Location(frameBlocks.get(0).getWorld(), minHorizontal, minVertical, frameBlocks.get(0).getZ()).getBlock();
            max = new Location(frameBlocks.get(0).getWorld(), maxHorizontal, maxVertical, frameBlocks.get(0).getZ()).getBlock();
        }

        int verticalRange = (max.getLocation().getBlockY() - min.getLocation().getBlockY()) + 1;
        int horizontalRange;

        BlockFace relative;
        if (alignment == PlaneAlignment.NORTH_SOUTH) {
            relative = BlockFace.SOUTH;
            horizontalRange = (max.getLocation().getBlockZ() - min.getLocation().getBlockZ()) + 1;
        } else {
            relative = BlockFace.EAST;
            horizontalRange = (max.getLocation().getBlockX() - min.getLocation().getBlockX()) + 1;
        }

        Set<Block> portalAbleBlocks = new HashSet<Block>();
        for (int i = 0; i < verticalRange; i++) {
            Set<Block> candidateBlocks = new HashSet<Block>();

            for (int x = 0; x < horizontalRange; x++) {
                Block block = min.getRelative(BlockFace.UP, i).getRelative(relative, x);
                if ((x + 1) == horizontalRange) {
                    continue;
                }
                Block next = min.getRelative(BlockFace.UP, i).getRelative(relative, (x + 1));

                if (block.getType() == frameMaterial && next.getType() != frameMaterial) {
                    x++;

                    while (true) {
                        if (x > horizontalRange) {
                            candidateBlocks.clear();
                            break;
                        }
                        next = min.getRelative(BlockFace.UP, i).getRelative(relative, x);
                        if (next.getType() == frameMaterial) {
                            portalAbleBlocks.addAll(candidateBlocks);
                            break;
                        } else {
                            candidateBlocks.add(next);
                        }
                        x++;
                    }
                }
            }
        }

        // Make sure that each and every single portalable block is bounded by the frame material on all four sides (top, bottom, left, right)
        Iterator<Block> iterator = portalAbleBlocks.iterator();
        while (iterator.hasNext()) {
            Block portalBlock = iterator.next();
            boolean surrounded = surroundedBy(portalBlock, frameMaterial, verticalRange, BlockFace.UP)
                    && surroundedBy(portalBlock, frameMaterial, verticalRange, BlockFace.DOWN);
            if (alignment == PlaneAlignment.NORTH_SOUTH) {
                surrounded = surrounded && surroundedBy(portalBlock, frameMaterial, horizontalRange, BlockFace.NORTH)
                        && surroundedBy(portalBlock, frameMaterial, horizontalRange, BlockFace.SOUTH);
            } else {
                surrounded = surrounded && surroundedBy(portalBlock, frameMaterial, horizontalRange, BlockFace.EAST)
                        && surroundedBy(portalBlock, frameMaterial, horizontalRange, BlockFace.WEST);
            }

            if (!surrounded) {
                iterator.remove();
            }
        }

        return portalAbleBlocks;
    }


    static boolean surroundedBy(Block block, Material check, int limit, BlockFace face) {
        int count = 0;
        while(true) {
            if (count > limit) {
                return false;
            }
            if ((block = block.getRelative(face)).getType() == check) {
                return true;
            }
            count++;
        }
    }

    public static Block nextBlock(Block lastBlock, Material type, BlockFace[] searchFaces, PlaneAlignment alignment, Collection<Block> existingBlocks) {
        Block newBlock = null;
        // Search for another portal block in all the provided faces
        for (BlockFace face : searchFaces) {
            Block block = lastBlock.getRelative(face);
            if (block.getType() == type && !existingBlocks.contains(block)) {
                newBlock = block;
            }
        }
        if (newBlock != null) {
            return newBlock;
        } else {
            // Search for another portal block in the diagonal blocks
            if (alignment == PlaneAlignment.NORTH_SOUTH) {
                newBlock = conflictResolution(lastBlock, type, existingBlocks, BlockFace.UP, BlockFace.NORTH, BlockFace.UP, BlockFace.SOUTH,
                        BlockFace.DOWN, BlockFace.NORTH, BlockFace.DOWN, BlockFace.SOUTH);
            } else {
                newBlock = conflictResolution(lastBlock, type, existingBlocks, BlockFace.UP, BlockFace.WEST, BlockFace.UP, BlockFace.EAST,
                        BlockFace.DOWN, BlockFace.WEST, BlockFace.DOWN, BlockFace.EAST);
            }
        }
        return newBlock;
    }

    static Block conflictResolution(Block block, Material type, Collection<Block> existingBlocks, BlockFace... faces) {
        if ((faces.length % 2) != 0) {
            throw new IllegalArgumentException("Array size must be divisible by 2");
        }
        for (int i = 0; i < faces.length; i += 2) {
            Block newBlock = block.getRelative(faces[i]).getRelative(faces[i + 1]);
            if (newBlock.getType() == type && !existingBlocks.contains(newBlock)) {
                return newBlock;
            }
        }
        return null;
    }


}
