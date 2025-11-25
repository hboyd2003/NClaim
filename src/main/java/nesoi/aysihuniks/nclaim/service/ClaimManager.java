package nesoi.aysihuniks.nclaim.service;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import lombok.RequiredArgsConstructor;
import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.api.events.ClaimEnterEvent;
import nesoi.aysihuniks.nclaim.api.events.ClaimLeaveEvent;
import nesoi.aysihuniks.nclaim.enums.Setting;
import nesoi.aysihuniks.nclaim.integrations.EnhancedPets;
import nesoi.aysihuniks.nclaim.ui.claim.management.ClaimManagementMenu;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.enums.Permission;
import nesoi.aysihuniks.nclaim.utils.LangManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.nandayo.dapi.message.ChannelType;
import org.nandayo.dapi.object.DEntityType;

import java.util.*;

@RequiredArgsConstructor
public class ClaimManager implements Listener {
    private final NClaim plugin;
    private final ClaimCoopManager coopManager;
    private final Map<UUID, Long> messageCooldown = new HashMap<>();

    private void sendCooldownMessage(Player player, String message) {
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        long lastMessageTime = messageCooldown.getOrDefault(playerUUID, 0L);
        if (currentTime - lastMessageTime >= 15000) {
            ChannelType.CHAT.send(player, message);
            messageCooldown.put(playerUUID, currentTime);
        }
    }

    private boolean hasClaimBypass(Player player, String specificBypass) {
        return player.hasPermission("nclaim.bypass.*") || player.hasPermission("nclaim.bypass." + specificBypass);
    }

    private boolean isNotClaimMember(Player player, Claim claim) {
        if (claim == null) return true;
        UUID uuid = player.getUniqueId();
        return !claim.getOwner().equals(uuid) && !claim.getCoopPlayers().contains(uuid);
    }

    private boolean cancelIfNotClaimMember(Player player, Claim claim, Cancellable event) {
        if (claim == null) return false;
        if (hasClaimBypass(player, "")) return false;
        if (isNotClaimMember(player, claim)) {
            event.setCancelled(true);
            sendCooldownMessage(player, plugin.getLangManager().getString("command.permission_denied"));
            return true;
        }
        return false;
    }

    private void cancelIfNoPermission(Player player, Claim claim, Permission permission, Cancellable event, String bypassType) {
        if (claim == null) return;
        if (hasClaimBypass(player, bypassType)) return;
        if (!coopManager.hasPermission(player, claim, permission)) {
            event.setCancelled(true);
            sendCooldownMessage(player, plugin.getLangManager().getString("command.permission_denied"));
        }
    }

    private Player getPlayerFromEntity(Entity entity) {
        if (entity instanceof Player) {
            return (Player) entity;
        } else if (entity instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        Chunk fromChunk = event.getFrom().getChunk();
        Chunk toChunk = event.getTo().getChunk();
        if (fromChunk.equals(toChunk)) return;
        if (NClaim.inst().getNconfig().getBlacklistedWorlds().contains(player.getWorld().getName())) return;
        if (NClaim.inst().isWorldGuardEnabled()) {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            World world = event.getTo().getWorld();
            if (world != null) {
                RegionManager regions = container.get(BukkitAdapter.adapt(world));
                if (regions != null) {
                    ApplicableRegionSet regionSet = regions.getApplicableRegions(BukkitAdapter.asBlockVector(event.getTo()));
                    for (ProtectedRegion region : regionSet) {
                        if (NClaim.inst().getNconfig().getBlacklistedRegions().contains(region.getId())) {
                            return;
                        }
                    }
                }
            }
        }
        Optional<Claim> fromClaim = Claim.getClaim(fromChunk);
        Optional<Claim> toClaim = Claim.getClaim(toChunk);
        if (fromClaim.isPresent() && (!fromClaim.equals(toClaim))) {
            ClaimLeaveEvent leaveEvent = new ClaimLeaveEvent(player, fromClaim.get());
            Bukkit.getPluginManager().callEvent(leaveEvent);
            LangManager.sendSortedMessage(player, plugin.getLangManager().getString("move.unclaimed_chunk"));
        }
        if (toClaim.isPresent() && (fromClaim.isEmpty() || !fromClaim.equals(toClaim))) {
            ClaimEnterEvent enterEvent = new ClaimEnterEvent(toClaim.get(), player);
            Bukkit.getPluginManager().callEvent(enterEvent);
            boolean isPvpEnabled = NClaim.inst().getClaimSettingsManager().isSettingEnabled(toClaim.get(), Setting.CLAIM_PVP);
            String pvpStatus = plugin.getLangManager().getString(isPvpEnabled ? "move.pvp_enabled" : "move.pvp_disabled");
            OfflinePlayer owner = Bukkit.getOfflinePlayer(toClaim.get().getOwner());
            LangManager.sendSortedMessage(player, plugin.getLangManager().getString("move.claimed_chunk")
                    .replace("{owner}", owner.getName() != null ? owner.getName() : "Unknown")
                    .replace("{claim_name}", toClaim.get().getClaimName())
                    .replace("{pvp_status}", pvpStatus));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.isCancelled()) return;
        Location explodeLocation = event.getLocation();
        Optional<Claim> explodeClaim = Claim.getClaim(explodeLocation.getChunk());
        Entity explodingEntity = event.getEntity();
        boolean isTNT = explodingEntity.getType() == NClaim.getEntityType(DEntityType.TNT, DEntityType.PRIMED_TNT) ||
                explodingEntity.getType() == NClaim.getEntityType(DEntityType.MINECART_TNT, DEntityType.TNT_MINECART);
        boolean isCreeper = explodingEntity.getType() == EntityType.CREEPER;
        List<Block> blocksToRemove = new ArrayList<>();
        for (Block block : event.blockList()) {
            Optional<Claim> blockClaim = Claim.getClaim(block.getChunk());
            if (blockClaim.isPresent()) {
                if ((isTNT && !plugin.getClaimSettingsManager().isSettingEnabled(blockClaim.get(), Setting.TNT_DAMAGE)) ||
                        (isCreeper && !plugin.getClaimSettingsManager().isSettingEnabled(blockClaim.get(), Setting.CREEPER_DAMAGE))) {
                    blocksToRemove.add(block);
                }
            }
        }
        event.blockList().removeAll(blocksToRemove);
        if (explodeClaim.isPresent()) {
            if ((isTNT && !plugin.getClaimSettingsManager().isSettingEnabled(explodeClaim.get(), Setting.TNT_DAMAGE)) ||
                    (isCreeper && !plugin.getClaimSettingsManager().isSettingEnabled(explodeClaim.get(), Setting.CREEPER_DAMAGE))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) return;
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        Player player = event.getPlayer();
        Location location = event.getTo();
        Chunk chunk = location.getChunk();
        Optional<Claim> targetClaim = Claim.getClaim(chunk);
        if (targetClaim.isEmpty()) return;
        cancelIfNotClaimMember(player, targetClaim.get(), event);
        cancelIfNoPermission(player, targetClaim.get(), Permission.USE_ENDER_PEARL, event, "teleport");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        Entity remover = event.getRemover();
        Hanging hanging = event.getEntity();
        if (!(hanging instanceof ItemFrame || hanging instanceof Painting)) return;
        Player player = getPlayerFromEntity(remover);
        if (player == null) return;
        Optional<Claim> claim = Claim.getClaim(hanging.getLocation().getChunk());
        if (claim.isEmpty()) return;
        if (cancelIfNotClaimMember(player, claim.get(), event)) return;
        Permission perm = hanging instanceof Painting ? Permission.PLACE_BLOCKS : Permission.INTERACT_ITEM_FRAME;
        cancelIfNoPermission(player, claim.get(), perm, event, "interact");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player damaged) {
            Player damager = getPlayerFromEntity(event.getDamager());
            if (damager != null && !hasClaimBypass(damager, "pvp")) {
                Optional<Claim> damagedClaim = Claim.getClaim(damaged.getLocation().getChunk());
                Optional<Claim> damagerClaim = Claim.getClaim(damager.getLocation().getChunk());
                if ((damagedClaim.isPresent() && !plugin.getClaimSettingsManager().isSettingEnabled(damagedClaim.get(), Setting.CLAIM_PVP)) ||
                        (damagerClaim.isPresent() && !plugin.getClaimSettingsManager().isSettingEnabled(damagerClaim.get(), Setting.CLAIM_PVP))) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        if (event.getEntity() instanceof ItemFrame || event.getEntity() instanceof Painting || event.getEntity() instanceof ArmorStand) {
            Player player = getPlayerFromEntity(event.getDamager());
            if (player == null) return;
            Optional<Claim> claim = Claim.getClaim(event.getEntity().getLocation().getChunk());
            if (claim.isEmpty()) return;
            if (cancelIfNotClaimMember(player, claim.get(), event)) return;
            Permission perm = Permission.INTERACT_ITEM_FRAME;
            if (event.getEntity() instanceof Painting) perm = Permission.PLACE_BLOCKS;
            else if (event.getEntity() instanceof ArmorStand) perm = Permission.INTERACT_ARMOR_STAND;
            cancelIfNoPermission(player, claim.get(), perm, event, "interact");
            return;
        }
        if (event.getEntity() instanceof Player player && event.getDamager() instanceof Monster) {
            Optional<Claim> claim = Claim.getClaim(player.getLocation().getChunk());
            if (claim.isPresent()) {
                boolean mobAttackingEnabled = plugin.getClaimSettingsManager().isSettingEnabled(claim.get(), Setting.MOB_ATTACKING);
                if (!mobAttackingEnabled && isNotClaimMember(player, claim.get())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        if (event.getEntity() instanceof Monster && event.getDamager() instanceof Player damager) {
            Optional<Claim> claim = Claim.getClaim(event.getEntity().getLocation().getChunk());
            if (claim.isPresent() && !hasClaimBypass(damager, "mob_attacking")) {
                boolean mobAttackingEnabled = plugin.getClaimSettingsManager().isSettingEnabled(claim.get(), Setting.MOB_ATTACKING);
                if (!mobAttackingEnabled && isNotClaimMember(damager, claim.get())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        if (event.getEntity() instanceof Villager) {
            Player damager = getPlayerFromEntity(event.getDamager());
            if (damager == null) return;
            Optional<Claim> claim = Claim.getClaim(event.getEntity().getLocation().getChunk());
            if (claim.isEmpty()) return;
            if (cancelIfNotClaimMember(damager, claim.get(), event)) return;
            cancelIfNoPermission(damager, claim.get(), Permission.INTERACT_VILLAGER, event, "interact");
            return;
        }
        if (event.getEntity() instanceof LivingEntity) {
            Entity damager = event.getDamager();
            Optional<Claim> claim = Claim.getClaim(event.getEntity().getLocation().getChunk());
            if (claim.isPresent()) {
                if ((damager instanceof TNTPrimed || damager instanceof ExplosiveMinecart) &&
                        !plugin.getClaimSettingsManager().isSettingEnabled(claim.get(), Setting.TNT_DAMAGE)) {
                    event.setCancelled(true);
                } else if (damager instanceof Creeper &&
                        !plugin.getClaimSettingsManager().isSettingEnabled(claim.get(), Setting.CREEPER_DAMAGE)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void windCharge(ExplosionPrimeEvent event) {
        EntityType type = event.getEntity().getType();
        if (type == EntityType.WIND_CHARGE || type == EntityType.BREEZE_WIND_CHARGE) {
            Optional<Claim> claim = Claim.getClaim(event.getEntity().getLocation().getChunk());
            if (claim.isPresent() && event.getEntity() instanceof Projectile projectile) {
                if (projectile.getShooter() instanceof Player player) {
                    if (cancelIfNotClaimMember(player, claim.get(), event)) return;
                    cancelIfNoPermission(player, claim.get(), Permission.PLACE_BLOCKS, event, "place");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!projectile.getType().name().equalsIgnoreCase("WIND_CHARGE")) return;
        Block hitBlock = event.getHitBlock();
        if (hitBlock == null || !(projectile.getShooter() instanceof Player player)) return;
        Optional<Claim> claim = Claim.getClaim(hitBlock.getChunk());
        if (claim.isEmpty()) return;
        if (cancelIfNotClaimMember(player, claim.get(), event)) return;
        Material type = hitBlock.getType();
        if (Tag.BUTTONS.isTagged(type) || Tag.PRESSURE_PLATES.isTagged(type) ||
                Tag.DOORS.isTagged(type) || Tag.TRAPDOORS.isTagged(type) || Tag.FENCE_GATES.isTagged(type)) {
            cancelIfNoPermission(player, claim.get(), Permission.USE_DOORS, event, "interact");
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.isCancelled()) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        Optional<Claim> claim = Claim.getClaim(event.getLocation().getChunk());
        if (claim.isEmpty()) return;
        if (event.getEntity() instanceof Monster) {
            if (!plugin.getClaimSettingsManager().isSettingEnabled(claim.get(), Setting.MONSTER_SPAWNING)) {
                event.setCancelled(true);
            }
        } else {
            if (!plugin.getClaimSettingsManager().isSettingEnabled(claim.get(), Setting.ANIMAL_SPAWNING)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Optional<Claim> claim = Claim.getClaim(block.getChunk());
        if (claim.isPresent() && block.getType() == claim.get().getClaimBlockType() &&
                block.getLocation().equals(claim.get().getClaimBlockLocation()) && !player.isSneaking()) {
            event.setCancelled(true);
            return;
        }
        if (claim.isEmpty()) return;
        if (cancelIfNotClaimMember(player, claim.get(), event)) return;
        Permission permission = Permission.BREAK_BLOCKS;
        if (block.getType() == Material.SPAWNER) permission = Permission.BREAK_SPAWNER;
        else if (block.getType() == Material.ITEM_FRAME || block.getType() == Material.GLOW_ITEM_FRAME) permission = Permission.INTERACT_ITEM_FRAME;
        else if (block.getType() == Material.PAINTING) permission = Permission.PLACE_BLOCKS;
        cancelIfNoPermission(player, claim.get(), permission, event, "break");
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Optional<Claim> claim = Claim.getClaim(block.getChunk());
        if (claim.isEmpty()) return;
        if (cancelIfNotClaimMember(player, claim.get(), event)) return;
        Permission permission = blockPlacePermission(event);
        cancelIfNoPermission(player, claim.get(), permission, event, "place");
    }

    private Permission blockPlacePermission(BlockPlaceEvent event) {
        Material type = event.getBlock().getType();
        return switch (type) {
            case SPAWNER -> Permission.PLACE_SPAWNER;
            case CAMPFIRE -> Permission.USE_CAMPFIRE;
            case SOUL_CAMPFIRE -> Permission.USE_SOUL_CAMPFIRE;
            default -> Permission.PLACE_BLOCKS;
        };
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.useInteractedBlock() == Event.Result.DENY) return;
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;
        Optional<Claim> claim = Claim.getClaim(block.getChunk());
        if (claim.isEmpty()) return;

        if (block.getType() == claim.get().getClaimBlockType() && claim.get().getClaimBlockLocation().equals(block.getLocation())) {
            if (player.hasPermission("nclaim.admin") && player.isSneaking() && event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
                new ClaimManagementMenu(player, claim.get(), true);
            }
            else if ((player.hasPermission("nclaim.admin") ||
                    coopManager.isClaimOwner(claim.get(), player) ||
                    coopManager.hasPermission(player, claim.get(), Permission.OPEN_CLAIM_MENU))
                    && event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
                new ClaimManagementMenu(player, claim.get(), false);
            }
            return;
        }
        if (hasClaimBypass(player, "interact")) return;
        if (cancelIfNotClaimMember(player, claim.get(), event)) return;
        Permission permission = getInteractPermission(block.getType());
        if (permission == null) return;
        cancelIfNoPermission(player, claim.get(), permission, event, "interact");
    }

    private Permission getInteractPermission(Material type) {
        Permission permission = switch (type) {
            case SPAWNER -> Permission.PLACE_SPAWNER;
            case CHEST -> Permission.USE_CHEST;
            case TRAPPED_CHEST -> Permission.USE_TRAPPED_CHEST;
            case BARREL -> Permission.USE_BARREL;
            case SWEET_BERRY_BUSH -> Permission.PLACE_BLOCKS;
            case HOPPER -> Permission.USE_HOPPER;
            case DISPENSER -> Permission.USE_DISPENSER;
            case DROPPER -> Permission.USE_DROPPER;
            case REPEATER -> Permission.USE_REPEATER;
            case COMPARATOR -> Permission.USE_COMPARATOR;
            case LEVER -> Permission.USE_LEVERS;
            case CRAFTING_TABLE -> Permission.USE_CRAFTING;
            case ENCHANTING_TABLE -> Permission.USE_ENCHANTING;
            case GRINDSTONE -> Permission.USE_GRINDSTONE;
            case STONECUTTER -> Permission.USE_STONECUTTER;
            case LOOM -> Permission.USE_LOOM;
            case SMITHING_TABLE -> Permission.USE_SMITHING;
            case CARTOGRAPHY_TABLE -> Permission.USE_CARTOGRAPHY;
            case BREWING_STAND -> Permission.USE_BREWING;
            case BELL -> Permission.USE_BELL;
            case BEACON -> Permission.USE_BEACON;
            case JUKEBOX -> Permission.USE_JUKEBOX;
            case NOTE_BLOCK -> Permission.USE_NOTEBLOCK;
            case CAMPFIRE -> Permission.USE_CAMPFIRE;
            case SOUL_CAMPFIRE -> Permission.USE_SOUL_CAMPFIRE;
            case ITEM_FRAME, GLOW_ITEM_FRAME -> Permission.INTERACT_ITEM_FRAME;
            case ARMOR_STAND -> Permission.INTERACT_ARMOR_STAND;
            default -> null;
        };

        if (permission != null) return permission;

        if (Tag.BUTTONS.isTagged(type)) return Permission.USE_BUTTONS;
        else if (Tag.SHULKER_BOXES.isTagged(type)) return Permission.USE_SHULKER;
        else if (Tag.PRESSURE_PLATES.isTagged(type)) return Permission.USE_PRESSURE_PLATES;
        else if (Tag.DOORS.isTagged(type)) return Permission.USE_DOORS;
        else if (Tag.TRAPDOORS.isTagged(type)) return Permission.USE_TRAPDOORS;
        else if (Tag.FENCE_GATES.isTagged(type)) return Permission.USE_GATES;
        else if (Tag.ANVIL.isTagged(type)) return Permission.USE_ANVIL;
        else if (Tag.BEDS.isTagged(type)) return Permission.USE_BED;

        return null;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        // All registered pets to bypass interaction permissions
        EnhancedPets enhancedPets = NClaim.inst().getEnhancedPets();
        if (enhancedPets != null && enhancedPets.isPetOwnedBy(entity.getUniqueId(), player.getUniqueId())) {
            return;
        }

        Optional<Claim> claim = Claim.getClaim(entity.getLocation().getChunk());
        if (claim.isEmpty()) return;
        if (cancelIfNotClaimMember(player, claim.get(), event)) return;
        Permission permission = switch (entity) {
            case Villager ignored -> Permission.INTERACT_VILLAGER;
            case ItemFrame ignored -> Permission.INTERACT_ITEM_FRAME;
            default -> Permission.MISC_ENTITY_INTERACT;
        };

        cancelIfNoPermission(player, claim.get(), permission, event, "interact");
    }

    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        Optional<Claim> claim = Claim.getClaim(event.getRightClicked().getLocation().getChunk());
        if (claim.isEmpty()) return;
        if (cancelIfNotClaimMember(player, claim.get(), event)) return;
        cancelIfNoPermission(player, claim.get(), Permission.INTERACT_ARMOR_STAND, event, "interact");
    }

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player player)) return;
        Optional<Claim> claim = Claim.getClaim(event.getVehicle().getLocation().getChunk());
        if (claim.isEmpty()) return;
        if (cancelIfNotClaimMember(player, claim.get(), event)) return;
        cancelIfNoPermission(player, claim.get(), Permission.RIDE_ENTITIES, event, "interact");
    }

    @EventHandler
    public void onPlayerLeashEntity(PlayerLeashEntityEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        Optional<Claim> claim = Claim.getClaim(event.getEntity().getLocation().getChunk());
        if (claim.isEmpty()) return;
        if (cancelIfNotClaimMember(player, claim.get(), event)) return;
        cancelIfNoPermission(player, claim.get(), Permission.LEASH_MOBS, event, "interact");
    }

    @EventHandler
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Optional<Claim> claim = Claim.getClaim(event.getBlock().getChunk());
        if (claim.isEmpty()) return;
        if (hasClaimBypass(player, "interact")) return;
        if (cancelIfNotClaimMember(player, claim.get(), event)) return;
        Permission permission = null;
        if (event.getBlockClicked().getType() == Material.WATER) permission = Permission.TAKE_WATER;
        else if (event.getBlockClicked().getType() == Material.LAVA) permission = Permission.TAKE_LAVA;
        if (permission != null) {
            cancelIfNoPermission(player, claim.get(), permission, event, "interact");
        }
    }

    @EventHandler
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        Optional<Claim> claim = Claim.getClaim(event.getBlock().getChunk());
        if (claim.isEmpty()) return;
        if (hasClaimBypass(player, "interact")) return;
        if (cancelIfNotClaimMember(player, claim.get(), event)) return;
        Permission permission = null;
        if (event.getBucket() == Material.WATER_BUCKET) permission = Permission.PLACE_WATER;
        else if (event.getBucket() == Material.LAVA_BUCKET) permission = Permission.PLACE_LAVA;
        if (permission != null) {
            cancelIfNoPermission(player, claim.get(), permission, event, "interact");
        }
    }

    @EventHandler
    public void onBlockFromTo(BlockFromToEvent event) {
        if (event.isCancelled()) return;
        Block fromBlock = event.getBlock();
        Block toBlock = event.getToBlock();
        if (fromBlock.getType() != Material.WATER && fromBlock.getType() != Material.LAVA) {
            return;
        }
        Optional<Claim> fromClaim = Claim.getClaim(fromBlock.getChunk());
        Optional<Claim> toClaim = Claim.getClaim(toBlock.getChunk());
        if (fromClaim.isEmpty() && toClaim.isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getBlock().getType() == Material.FARMLAND && event.getEntity() instanceof Player player) {
            Optional<Claim> claim = Claim.getClaim(event.getBlock().getChunk());
            if (claim.isEmpty()) return;
            if (cancelIfNotClaimMember(player, claim.get(), event)) event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRedstoneUpdate(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        Optional<Claim> sourceClaim = Claim.getClaim(block.getChunk());
        for (BlockFace face : BlockFace.values()) {
            Optional<Claim> targetClaim = Claim.getClaim(block.getRelative(face).getChunk());
            if (targetClaim.isPresent()
                    && sourceClaim.isPresent()
                    && !targetClaim.get().getOwner().equals(sourceClaim.get().getOwner())
                    && !targetClaim.get().getCoopPlayers().contains(sourceClaim.get().getOwner())) {
                event.setNewCurrent(event.getNewCurrent());
                return;
            }
        }
    }
}