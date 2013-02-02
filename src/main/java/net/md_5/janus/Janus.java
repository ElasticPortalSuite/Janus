package net.md_5.janus;

import net.md_5.janus.util.BlockPosition;
import net.md_5.janus.util.FrameUtil;
import net.md_5.janus.util.PendingFrame;
import net.md_5.janus.util.Portal;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class Janus extends JavaPlugin implements Listener {

    private int lastId = 0;
    private boolean blockMessages = false;
    private int safetyLimit;
    private Set<Integer> frameIds;
    private Map<String, String> pendingPortalCreation = new HashMap<String, String>(); // maps player name -> server for portal creation
    private Map<Player, PendingFrame> pendingConfirmation = new WeakHashMap<Player, PendingFrame>(); // map players -> frame blocks

    private Map<Integer, Portal> portalIDMap = new HashMap<Integer, Portal>();
    /**
     * Maps world name -> (block position -> portal id)
     */
    private Map<String, Map<BlockPosition, Integer>> fastLookupMap = new HashMap<String, Map<BlockPosition, Integer>>();
    FileConfiguration portalConfig;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();

        try {
            this.loadConfig();
        } catch (Exception e) {
            this.getLogger().severe(ChatColor.RED + e.getMessage());
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        this.getServer().getPluginManager().registerEvents(this, this);
    }

    private void loadConfig() throws IOException, InvalidConfigurationException {
        if (this.getConfig().isInt("safety-limit") && !(this.getConfig().getInt("safety-limit") > 0)) {
            throw new IllegalArgumentException("Safety limit undefined");
        } else {
            this.safetyLimit = this.getConfig().getInt("safety-limit");
        }
        List<Integer> frameIds = this.getConfig().getIntegerList("frame-ids");
        if (frameIds == null) {
            throw new IllegalArgumentException("Frame IDs undefined");
        } else {
            this.frameIds = new HashSet<Integer>(frameIds.size());
            this.frameIds.addAll(frameIds);
        }

        this.portalConfig = new YamlConfiguration();
        File file = new File(getDataFolder(), "portals.yml");
        if (!file.exists()) {
            file.createNewFile();
        }
        portalConfig.load(file);

        ConfigurationSection section;
        if (!portalConfig.isConfigurationSection("portals")) {
            return;
        }
        for (String key : (section = portalConfig.getConfigurationSection("portals")).getKeys(false)) {
            try {
                int value = Integer.valueOf(key);
                if (value < 0) {
                    this.getLogger().warning("Picked up an invalid id at portals." + key);
                    continue;
                }
                if (value > lastId) {
                    this.lastId = value;
                }
                ConfigurationSection subsection = section.getConfigurationSection(key);

                String targetServer = subsection.getString("server");
                String worldName = subsection.getString("world");
                World world = this.getServer().getWorld(subsection.getString("world"));

                List<Block> frameBlocks = PendingFrame.locationListToBlockList(
                        integersToLocations(subsection.getIntegerList("frame-blocks"), world));
                List<Block> portalBlocks = PendingFrame.locationListToBlockList(
                        integersToLocations(subsection.getIntegerList("portal-blocks"), world));

                Portal portal = new Portal(targetServer, worldName, frameBlocks, portalBlocks);
                this.portalIDMap.put(value, portal);

                if (fastLookupMap.containsKey(worldName)) {
                    Map<BlockPosition, Integer> map = fastLookupMap.get(worldName);
                    for (Block block : portalBlocks) {
                        map.put(BlockPosition.fromLocation(block.getLocation()), value);
                    }
                } else {
                    Map<BlockPosition, Integer> map = new HashMap<BlockPosition, Integer>(portalBlocks.size());
                    for (Block block : portalBlocks) {
                        map.put(BlockPosition.fromLocation(block.getLocation()), value);
                    }
                    this.fastLookupMap.put(worldName, map);
                }
            } catch (NumberFormatException e) {
                this.getLogger().warning("Picked up an invalid key at portals." + key);
            }
        }
        this.blockMessages = this.getConfig().getBoolean("block-messages");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players may alter portals");
            return true;
        }
        if (args.length >= 1) {

            if (args[0].equalsIgnoreCase("create")) {
                if (args.length < 2 || args.length > 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /janus create <server>");
                    return true;
                }

                sender.sendMessage(ChatColor.AQUA + "The next portal frame block you punch will activate that portal to " +
                        ChatColor.GREEN + ChatColor.BOLD + args[1]);
                this.pendingPortalCreation.put(sender.getName(), args[1]);
            }

            if (args[0].equalsIgnoreCase("cancel")) {
                this.pendingPortalCreation.remove(sender.getName());
                sender.sendMessage(ChatColor.GREEN + "You are no longer in portal creation mode");
            }

            if (args[0].equalsIgnoreCase("confirm")) {
                Player player = (Player) sender;

                PendingFrame pendingFrame = this.pendingConfirmation.get(player);

                this.pendingConfirmation.remove(sender);

                final List<Block> frameBlocks = pendingFrame.getFrameBlocks();
                Collection<Block> portalBlocks = FrameUtil.getPortalAbleBlocks(frameBlocks, pendingFrame.getAlignment(),
                        pendingFrame.getFrameMaterial(), player);
                if (!portalConfig.isConfigurationSection("portals")) {
                    portalConfig.createSection("portals");
                }
                ConfigurationSection section = portalConfig.getConfigurationSection("portals").createSection(String.valueOf(++lastId));
                String world = player.getLocation().getWorld().getName();

                section.set("server", pendingFrame.getServer());
                section.set("world", world);
                section.set("frame-blocks", blockLocationsToIntegerList(frameBlocks));
                section.set("portal-blocks", blockLocationsToIntegerList(portalBlocks));

                try {
                    portalConfig.save(new File(getDataFolder(), "portals.yml"));
                } catch (IOException e) {
                    sender.sendMessage(ChatColor.RED + "An error occurred while trying to create this portal, check server log");
                    e.printStackTrace();
                    return true;
                }

                Portal portal = new Portal(pendingFrame.getServer(), world, frameBlocks, portalBlocks);
                this.portalIDMap.put(lastId, portal);

                if (fastLookupMap.containsKey(world)) {
                    Map<BlockPosition, Integer> map = fastLookupMap.get(world);
                    for (Block block : portalBlocks) {
                        map.put(BlockPosition.fromLocation(block.getLocation()), lastId);
                    }
                } else {
                    Map<BlockPosition, Integer> map = new HashMap<BlockPosition, Integer>(portalBlocks.size());
                    for (Block block : portalBlocks) {
                        map.put(BlockPosition.fromLocation(block.getLocation()), lastId);
                    }
                    this.fastLookupMap.put(world, map);
                }

                player.sendMessage(ChatColor.AQUA + "Portal successfully created");
            }

            if (args[0].equalsIgnoreCase("reload")) {
                try {
                    this.loadConfig();
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "An exception occured, please check server log");
                }
            }

        } else {
            sender.sendMessage(ChatColor.RED + "Usage: ");
            sender.sendMessage(ChatColor.RED + "/janus create <server>");
            sender.sendMessage(ChatColor.RED + "/janus cancel");
            sender.sendMessage(ChatColor.RED + "/janus remove");
        }
        return true;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (!this.pendingPortalCreation.containsKey(event.getPlayer().getName())) {
                return;
            }
            if (!frameIds.contains(event.getClickedBlock().getTypeId())) {
                event.getPlayer().sendMessage(ChatColor.RED + "That isn't a valid frame block. Use /janus cancel if you want to cancel portal creation");
                return;
            }
            event.setCancelled(true);

            Material frameMaterial = event.getClickedBlock().getType();
            Block clickedBlock = event.getClickedBlock();
            int numBlocks = 0;
            PlaneAlignment alignment;

            // Attempt to resolve plane alignment, i.e are we dealing with a west <-> east portal or north <-> south portal
            alignment: {
                if (clickedBlock.getRelative(BlockFace.UP).getType() == frameMaterial) {
                    // Oh cool, there's stuff above, lets get the top most block of the portal then
                    Block upperMost = clickedBlock;
                    while (true) {
                        if (numBlocks >= safetyLimit) {
                            event.getPlayer().sendMessage(ChatColor.RED + "This portal's frame is too big (limit: " + safetyLimit + ")");
                            return;
                        }
                        upperMost = upperMost.getRelative(BlockFace.UP);
                        if (upperMost.getType() != frameMaterial) {
                            upperMost = upperMost.getRelative(BlockFace.DOWN);
                            break;
                        } else {
                            numBlocks++;
                        }
                    }
                    // Now lets see which way the portal's top most block leads to
                    boolean northSouth = upperMost.getRelative(BlockFace.NORTH).getType() == frameMaterial || upperMost.getRelative(BlockFace.SOUTH).getType() == frameMaterial
                            || upperMost.getRelative(BlockFace.UP).getRelative(BlockFace.NORTH).getType() == frameMaterial || upperMost.getRelative(BlockFace.UP).getRelative(BlockFace.SOUTH).getType() == frameMaterial
                            || upperMost.getRelative(BlockFace.DOWN).getRelative(BlockFace.NORTH).getType() == frameMaterial || upperMost.getRelative(BlockFace.DOWN).getRelative(BlockFace.SOUTH).getType() == frameMaterial;

                    boolean eastWest = upperMost.getRelative(BlockFace.WEST).getType() == frameMaterial || upperMost.getRelative(BlockFace.EAST).getType() == frameMaterial
                            || upperMost.getRelative(BlockFace.UP).getRelative(BlockFace.WEST).getType() == frameMaterial || upperMost.getRelative(BlockFace.UP).getRelative(BlockFace.EAST).getType() == frameMaterial
                            || upperMost.getRelative(BlockFace.DOWN).getRelative(BlockFace.WEST).getType() == frameMaterial || upperMost.getRelative(BlockFace.DOWN).getRelative(BlockFace.EAST).getType() == frameMaterial;

                    PlaneAlignment decision = conflictResolution(northSouth, eastWest, event.getPlayer());
                    if (decision == null) {
                        return;
                    } else {
                        alignment = decision;
                        break alignment;
                    }

                } else if (clickedBlock.getRelative(BlockFace.DOWN).getType() == frameMaterial) {
                    // Frame goes downwards from here
                    Block lowerMost = clickedBlock;
                    while (true) {
                        if (numBlocks >= safetyLimit) {
                            event.getPlayer().sendMessage(ChatColor.RED + "This portal's frame is too big (limit: " + safetyLimit + ")");
                            this.pendingPortalCreation.remove(event.getPlayer().getName());
                            return;
                        }
                        lowerMost = lowerMost.getRelative(BlockFace.DOWN);
                        if (lowerMost.getType() != frameMaterial) {
                            lowerMost = lowerMost.getRelative(BlockFace.UP);
                            break;
                        } else {
                            numBlocks++;
                        }
                    }

                    // Now lets see which way the portal's bottom most block leads to
                    boolean northSouth = lowerMost.getRelative(BlockFace.NORTH).getType() == frameMaterial || lowerMost.getRelative(BlockFace.SOUTH).getType() == frameMaterial
                            || lowerMost.getRelative(BlockFace.DOWN).getRelative(BlockFace.NORTH).getType() == frameMaterial || lowerMost.getRelative(BlockFace.DOWN).getRelative(BlockFace.SOUTH).getType() == frameMaterial
                            || lowerMost.getRelative(BlockFace.UP).getRelative(BlockFace.NORTH).getType() == frameMaterial || lowerMost.getRelative(BlockFace.UP).getRelative(BlockFace.SOUTH).getType() == frameMaterial;

                    boolean eastWest = lowerMost.getRelative(BlockFace.WEST).getType() == frameMaterial || lowerMost.getRelative(BlockFace.EAST).getType() == frameMaterial
                            || lowerMost.getRelative(BlockFace.DOWN).getRelative(BlockFace.WEST).getType() == frameMaterial || lowerMost.getRelative(BlockFace.DOWN).getRelative(BlockFace.EAST).getType() == frameMaterial
                            || lowerMost.getRelative(BlockFace.UP).getRelative(BlockFace.WEST).getType() == frameMaterial || lowerMost.getRelative(BlockFace.UP).getRelative(BlockFace.EAST).getType() == frameMaterial;

                    PlaneAlignment decision = conflictResolution(northSouth, eastWest, event.getPlayer());
                    if (decision == null) {
                        return;
                    } else {
                        alignment = decision;
                        break alignment;
                    }
                } else {
                    // Nothing up and down, so lets check the alignment directly
                    boolean northSouth = clickedBlock.getRelative(BlockFace.NORTH).getType() == frameMaterial || clickedBlock.getRelative(BlockFace.SOUTH).getType() == frameMaterial
                            || clickedBlock.getRelative(BlockFace.DOWN).getRelative(BlockFace.NORTH).getType() == frameMaterial || clickedBlock.getRelative(BlockFace.DOWN).getRelative(BlockFace.SOUTH).getType() == frameMaterial
                            || clickedBlock.getRelative(BlockFace.UP).getRelative(BlockFace.NORTH).getType() == frameMaterial || clickedBlock.getRelative(BlockFace.UP).getRelative(BlockFace.SOUTH).getType() == frameMaterial;

                    boolean eastWest = clickedBlock.getRelative(BlockFace.WEST).getType() == frameMaterial || clickedBlock.getRelative(BlockFace.EAST).getType() == frameMaterial
                            || clickedBlock.getRelative(BlockFace.DOWN).getRelative(BlockFace.WEST).getType() == frameMaterial || clickedBlock.getRelative(BlockFace.DOWN).getRelative(BlockFace.EAST).getType() == frameMaterial
                            || clickedBlock.getRelative(BlockFace.UP).getRelative(BlockFace.WEST).getType() == frameMaterial || clickedBlock.getRelative(BlockFace.UP).getRelative(BlockFace.EAST).getType() == frameMaterial;

                    PlaneAlignment decision = conflictResolution(northSouth, eastWest, event.getPlayer());
                    if (decision == null) {
                        return;
                    } else {
                        alignment = decision;
                        break alignment;
                    }
                }
            }

            BlockFace[] faces;
            if (alignment == PlaneAlignment.NORTH_SOUTH) {
                faces = new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH};
            } else {
                faces = new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.EAST, BlockFace.WEST};
            }

            Block nextBlock = clickedBlock;
            List<Block> frameBlocks = new ArrayList<Block>();
            numBlocks = 0;
            while ((nextBlock = FrameUtil.nextBlock(nextBlock, frameMaterial, faces, alignment, frameBlocks)) != null) {
                if (numBlocks >= safetyLimit) {
                    event.getPlayer().sendMessage(ChatColor.RED + "This portal's frame is too big (limit: " + safetyLimit + ")");
                    return;
                }
                frameBlocks.add(nextBlock);
                numBlocks++;
            }

            for (Block block : frameBlocks) {
                // Verify that this block is connected to at least two other frame blocks
                List<Block> existing = new ArrayList<Block>(2);
                existing.add(block);
                Block other = FrameUtil.nextBlock(block, frameMaterial, faces, alignment, existing);
                existing.add(other);
                Block second = FrameUtil.nextBlock(block, frameMaterial, faces, alignment, existing);

                if (second == null || other == null) {
                    event.getPlayer().sendMessage(ChatColor.RED + "The portal's frame must be completely closed");
                    return;
                }
            }

            event.getPlayer().sendMessage(ChatColor.AQUA + "Portal frame detected! The frame blocks will now blink 6 times, please use '/janus confirm' to confirm the frame");
            new FrameConfirmationTask(event.getPlayer(), frameBlocks, frameMaterial, clickedBlock.getData()).runTaskTimer(this, 5, 5);
            this.pendingConfirmation.put(event.getPlayer(), new PendingFrame(frameBlocks, alignment, this.pendingPortalCreation.get(event.getPlayer().getName()), frameMaterial));
            this.pendingPortalCreation.remove(event.getPlayer().getName());
        }
    }

    private PlaneAlignment conflictResolution(boolean northSouth, boolean eastWest, Player player) {
        if (northSouth && eastWest) {
            // Uhh...wat, this portal goes in both planes. No thanks.
            player.sendMessage(ChatColor.RED + "Portals can't stretch in both north-south and east-west space");
            return null;
        } else if (northSouth) {
            return PlaneAlignment.NORTH_SOUTH;
        } else {
            return PlaneAlignment.WEST_EAST;
        }
    }

    private List<Integer> blockLocationsToIntegerList(Collection<Block> blocks) {
        List<Integer> integers = new ArrayList<Integer>(blocks.size() * 3);
        for (Block block : blocks) {
            Location location = block.getLocation();
            integers.add(location.getBlockX());
            integers.add(location.getBlockY());
            integers.add(location.getBlockZ());
        }
        return integers;
    }

    private List<Location> integersToLocations(List<Integer> integers, World world) {
        if ((integers.size() % 3) != 0) {
            throw new IllegalArgumentException("Number of integers must be divisible by 3");
        }
        List<Location> locations = new ArrayList<Location>(integers.size() / 3);
        for (int i = 0; i < integers.size(); i += 3) {
            locations.add(new Location(world, integers.get(i), integers.get(i + 1), integers.get(i + 2)));
        }
        return locations;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (blockMessages) {
            event.setJoinMessage(null);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (blockMessages) {
            event.setQuitMessage(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().distanceSquared(event.getTo()) < 0.01) {
            return;
        }
        String world = event.getTo().getWorld().getName();
        BlockPosition to = BlockPosition.fromLocation(event.getTo());
        if (fastLookupMap.containsKey(world)) {
            Map<BlockPosition, Integer> map = fastLookupMap.get(world);
            Integer id;
            if ((id = map.get(to)) != null) {
                String targetServer = portalIDMap.get(id).getTargetServer();
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(b);
                try {
                    out.writeUTF("Connect");
                    out.writeUTF(targetServer);
                } catch (IOException ex) {
                    // Impossible
                }

                event.getPlayer().sendMessage("lol going to " + targetServer);
                event.getPlayer().sendPluginMessage(this, "BungeeCord", b.toByteArray());
            }
        }
    }
}
