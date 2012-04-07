package com.md_5.jumpwarps;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Warper extends JavaPlugin implements Listener {

    private final String identifier = "[server]";
    private final int mat = Material.OBSIDIAN.getId();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.setJoinMessage(null);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.setQuitMessage(null);
    }

    @EventHandler
    public void onPlayerQuit(PlayerKickEvent event) {
        event.setLeaveMessage(null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (event.getLine(0).equals(identifier) && !event.getPlayer().isOp()) {
            event.getPlayer().sendMessage(ChatColor.RED + "You are not allowed to do that!");
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location location = event.getTo();
        World world = location.getWorld();

        if (world.getBlockAt(location).getType() == Material.PORTAL && world.getBlockAt(event.getFrom()).getType() != Material.PORTAL) {
            Set<Block> portalBlocks = getPortalNear(world, location.getBlockX(), location.getBlockY(), location.getBlockZ());
            Sign sign = null;
            for (Block block : portalBlocks) {
                Block relative;
                for (BlockFace bf : BlockFace.values()) {
                    relative = block.getRelative(bf);
                    if (relative.getType() == Material.WALL_SIGN) {
                        if (((Sign) relative.getState()).getLine(0).equals(identifier)) {
                            sign = (Sign) relative.getState();
                            break;
                        }
                    }
                }
                if (sign != null) {
                    event.setCancelled(true);
                    event.getPlayer().kickPlayer("[Redirect] You aren't on the proxy: " + sign.getLine(1));
                    break;
                }
            }
        }
    }

    private Set<Block> getPortalNear(World world, int x, int y, int z) {
        byte b0 = 0;
        byte b1 = 0;
        if (world.getBlockTypeIdAt(x - 1, y, z) == mat || world.getBlockTypeIdAt(x + 1, y, z) == mat) {
            b0 = 1;
        }
        if (world.getBlockTypeIdAt(x, y, z - 1) == mat || world.getBlockTypeIdAt(x, y, z + 1) == mat) {
            b1 = 1;
        }

        Set<Block> blocks = new HashSet<Block>();

        if (world.getBlockTypeIdAt(x - b0, y, z - b1) == 0) {
            x -= b0;
            z -= b1;
        }

        for (byte i = -1; i <= 2; ++i) {
            for (byte j = -1; j <= 3; ++j) {
                boolean flag = i == -1 || i == 2 || j == -1 || j == 3;

                if (i != -1 && i != 2 || j != -1 && j != 3) {
                    if (flag) {
                        blocks.add(world.getBlockAt(x + b0 * i, y + j, z + b1 * i));
                    }
                }
            }
        }
        return blocks;
    }
}
