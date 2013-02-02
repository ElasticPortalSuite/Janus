package net.md_5.janus.util;

import net.md_5.janus.PlaneAlignment;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

public class PendingFrame {

    private final List<Location> frameBlocks;
    private final PlaneAlignment alignment;
    private final String server;
    private final Material frameMaterial;

    public PendingFrame(List<Block> frameBlocks, PlaneAlignment alignment, String server, Material frameMaterial) {
        this.frameBlocks = blockListToLocationList(frameBlocks);
        this.alignment = alignment;
        this.server = server;
        this.frameMaterial = frameMaterial;
    }

    public List<Block> getFrameBlocks() {
        return locationListToBlockList(this.frameBlocks);
    }

    public PlaneAlignment getAlignment() {
        return alignment;
    }

    public String getServer() {
        return server;
    }

    public Material getFrameMaterial() {
        return frameMaterial;
    }

    private static List<Location> blockListToLocationList(List<Block> blocks) {
        ArrayList<Location> locations = new ArrayList<Location>(blocks.size());
        for (Block block : blocks) {
            locations.add(block.getLocation());
        }
        return locations;
    }

    public static List<Block> locationListToBlockList(List<Location> locations) {
        ArrayList<Block> blocks = new ArrayList<Block>(locations.size());
        for (Location location : locations) {
            blocks.add(location.getBlock());
        }
        return blocks;
    }
}
