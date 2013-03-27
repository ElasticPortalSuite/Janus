package net.md_5.janus.util;

import org.bukkit.block.Block;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Portal {

    private Set<BlockPosition> frameBlocks;
    private Set<BlockPosition> portalBlocks;
    private String targetServer;
    private String world;
    private int id;

    public Portal(String targetServer, String world, Collection<Block> frame, Collection<Block> portal, int id) {
        this.id = id;
        this.frameBlocks = new HashSet<BlockPosition>(frame.size());
        this.portalBlocks = new HashSet<BlockPosition>(portal.size());

        BlockPosition.addAllFromLocation(frameBlocks, frame);
        BlockPosition.addAllFromLocation(portalBlocks, portal);

        this.targetServer = targetServer;
        this.world = world;
    }

    public Set<BlockPosition> getFrameBlocks() {
        return frameBlocks;
    }

    public Set<BlockPosition> getPortalBlocks() {
        return portalBlocks;
    }

    public String getTargetServer() {
        return targetServer;
    }

    public String getWorld() {
        return world;
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Portal");
        sb.append("{targetServer='").append(targetServer).append('\'');
        sb.append(", world='").append(world).append('\'');
        sb.append(", portalBlocks=").append(portalBlocks);
        sb.append(", frameBlocks=").append(frameBlocks);
        sb.append('}');
        return sb.toString();
    }

}
