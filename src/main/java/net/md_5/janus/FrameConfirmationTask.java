package net.md_5.janus;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.material.Wool;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;

public class FrameConfirmationTask extends BukkitRunnable {

    private Player player;
    private Collection<Block> blocks;
    private Material frameMaterial;
    private byte data;
    private boolean useOrange;
    private boolean previewState;

    private int invokes = 0;

    public FrameConfirmationTask(Player player, Collection<Block> frameBlocks, Material frameMaterial, byte data) {
        this.player = player;
        this.blocks = frameBlocks;

        useOrange = frameMaterial != Material.WOOL && new Wool(frameMaterial, data).getColor() != DyeColor.ORANGE;
        this.frameMaterial = frameMaterial;
        this.data = data;

        previewState = false;
    }

    public void run() {
        if (invokes >= 12 || !player.isOnline()) {
            this.cancel();
            return;
        }
        invokes++;
        if (previewState) {
            for (Block block : blocks) {
                player.sendBlockChange(block.getLocation(), frameMaterial.getId(), data);
            }
            previewState = false;
        } else {
            if (useOrange) {
                for (Block block : blocks) {
                    player.sendBlockChange(block.getLocation(), Material.WOOL.getId(), DyeColor.ORANGE.getDyeData());
                }
            } else {
                for (Block block : blocks) {
                    player.sendBlockChange(block.getLocation(), Material.WOOL, DyeColor.PINK.getDyeData());
                }
            }
            previewState = true;
        }
    }
}
