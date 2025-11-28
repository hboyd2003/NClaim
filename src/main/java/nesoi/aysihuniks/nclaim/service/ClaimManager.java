package nesoi.aysihuniks.nclaim.service;

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
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.ItemType;
import org.jetbrains.annotations.NotNull;
import org.nandayo.dapi.message.ChannelType;

import java.util.*;

@SuppressWarnings("UnstableApiUsage")
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

    private boolean cancelIfNotClaimMember(@NotNull Player player, @NotNull Claim claim, Cancellable event) {
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.isCancelled()) return;
        Chunk fromChunk = event.getFrom().getChunk();
        Chunk toChunk = event.getTo().getChunk();

        if (fromChunk.getChunkKey() == toChunk.getChunkKey()) return; // Return if we stay within the same chunk

        if (NClaim.inst().getNconfig().getBlacklistedWorlds().contains(event.getPlayer().getWorld().getName())) return;

        if (NClaim.inst().isWorldGuardEnabled() && NClaim.inst().getWorldguard().isInBlacklistedRegion(event.getTo()))
            return;

        Optional<Claim> fromClaim = Claim.getClaim(fromChunk);
        Optional<Claim> toClaim = Claim.getClaim(toChunk);
        if (fromClaim.isPresent() && toClaim.isPresent() && fromClaim.get().equals(toClaim.get())) return;

        // Moved into unclaimed
        if (fromClaim.isPresent() && toClaim.isEmpty()) {
            ClaimLeaveEvent leaveEvent = new ClaimLeaveEvent(event.getPlayer(), fromClaim.get());
            Bukkit.getPluginManager().callEvent(leaveEvent);
            LangManager.sendSortedMessage(event.getPlayer(), plugin.getLangManager().getString("move.unclaimed_chunk"));
        } else if (toClaim.isPresent()) { // Moved into claim
            Bukkit.getPluginManager().callEvent(new ClaimEnterEvent(toClaim.get(), event.getPlayer()));

            boolean isPvpEnabled = NClaim.inst().getClaimSettingsManager().isSettingEnabled(toClaim.get(), Setting.CLAIM_PVP);
            String pvpStatus = plugin.getLangManager().getString(isPvpEnabled ? "move.pvp_enabled" : "move.pvp_disabled");

            OfflinePlayer owner = Bukkit.getOfflinePlayer(toClaim.get().getOwner());

            LangManager.sendSortedMessage(event.getPlayer(), plugin.getLangManager().getString("move.claimed_chunk")
                    .replace("{owner}", owner.getName() != null ? owner.getName() : "Unknown")
                    .replace("{claim_name}", toClaim.get().getClaimName())
                    .replace("{pvp_status}", pvpStatus));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.isCancelled()) return;

        Optional<Claim> explodeClaim = Claim.getClaim(event.getLocation().getChunk());
        if (explodeClaim.isEmpty()) return;

        Setting setting = switch (event.getEntity()) {
            case TNTPrimed ignored -> Setting.TNT_DESTRUCTION;
            case ExplosiveMinecart ignored -> Setting.TNT_DESTRUCTION;
            case Creeper ignored -> Setting.CREEPER_GRIEFING;
            case Fireball ignored -> Setting.GHAST_GRIEFING;
            default -> null;
        };
        if (setting == null) return;

        event.blockList().removeIf(block -> {
            Optional<Claim> blockClaim = Claim.getClaim(block.getChunk());
            return blockClaim.isPresent() && !plugin.getClaimSettingsManager().isSettingEnabled(blockClaim.get(), setting);
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityChangeBlockEvent(EntityChangeBlockEvent event) {
        Optional<Claim> claim = Claim.getClaim(event.getBlock().getChunk());
        if (claim.isEmpty()) return;

        Setting setting = switch (event.getEntity().getType()) {
            case ENDERMAN -> Setting.ENDERMAN_GRIEFING;
            case SILVERFISH ->  Setting.SILVERFISH_GRIEFING;
            case ZOMBIE ->  Setting.ZOMBIE_GRIEFING;
            default -> null;
        };
        if (setting == null) return;

        event.setCancelled(!claim.get().getSettings().isEnabled(setting));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled() || event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        Player player = event.getPlayer();
        Optional<Claim> targetClaim = Claim.getClaim(event.getTo().getChunk());
        if (targetClaim.isEmpty()) return;

        cancelIfNotClaimMember(player, targetClaim.get(), event);
        cancelIfNoPermission(player, targetClaim.get(), Permission.USE_ENDER_PEARL, event, "teleport");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        Hanging hanging = event.getEntity();
        if (!(hanging instanceof ItemFrame || hanging instanceof Painting)) return;

        if (!(event.getRemover() instanceof Player player)) return;

        Optional<Claim> claim = Claim.getClaim(hanging.getLocation().getChunk());
        if (claim.isEmpty()) return;

        if (cancelIfNotClaimMember(player, claim.get(), event)) return;
        cancelIfNoPermission(player, claim.get(), Permission.BREAK_BLOCKS, event, "interact");
    }

    @EventHandler
    public void onHangingPlaceEvent(HangingPlaceEvent event) {
        if (event.getPlayer() == null) return;

        Hanging hanging = event.getEntity();
        if (!(hanging instanceof ItemFrame || hanging instanceof Painting)) return;

        Optional<Claim> claim = Claim.getClaim(hanging.getLocation().getChunk());
        if (claim.isEmpty()) return;

        if (cancelIfNotClaimMember(event.getPlayer(), claim.get(), event)) return;
        cancelIfNoPermission(event.getPlayer(), claim.get(), Permission.PLACE_BLOCKS, event, "interact");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Optional<Claim> claim = Claim.getClaim(event.getEntity().getLocation().getChunk());
        if (claim.isEmpty()) return;

        Entity damaged = event.getEntity();
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile) {
            damager = (Entity) projectile.getShooter();
        }

        // PvP
        if (damaged instanceof Player
                && damager instanceof Player
                && !hasClaimBypass((Player) damager, "pvp")) {
            boolean damagedNotInPVPClaim = Claim.getClaim(damaged.getLocation().getChunk())
                    .map(damagedClaim-> !plugin.getClaimSettingsManager().isSettingEnabled(damagedClaim, Setting.CLAIM_PVP))
                    .orElse(false);

            boolean damagerNotInPVPClaim = Claim.getClaim(damager.getLocation().getChunk())
                    .map(damagerClaim-> !plugin.getClaimSettingsManager().isSettingEnabled(damagerClaim, Setting.CLAIM_PVP))
                    .orElse(false);

            if (damagedNotInPVPClaim || damagerNotInPVPClaim) {
                event.setCancelled(true);
                return;
            }
        }

        // Player removes item from item frame
        if (damaged instanceof ItemFrame
                && damager instanceof Player damagerPlayer) {
            if (cancelIfNotClaimMember(damagerPlayer, claim.get(), event)) return;

            cancelIfNoPermission(damagerPlayer, claim.get(), Permission.INTERACT_ITEM_FRAME, event, "interact");
        }

        // Damage to non-player entity
        if (damager instanceof Player damagerPlayer
                && (damaged instanceof ArmorStand
                    || damaged instanceof Painting
                    || damaged instanceof ItemFrame
                    || damaged instanceof Boat)) {

            if (cancelIfNotClaimMember(damagerPlayer, claim.get(), event)) return;
            cancelIfNoPermission(damagerPlayer, claim.get(), Permission.BREAK_BLOCKS, event, "interact");
            return;
        }

        boolean mobAttackingEnabled = plugin.getClaimSettingsManager().isSettingEnabled(claim.get(), Setting.MOB_ATTACKING);

        // Monster damaging player
        if (damaged instanceof Player damagedPlayer
                && damager instanceof Monster
                && !mobAttackingEnabled
                && isNotClaimMember(damagedPlayer, claim.get())) {
            event.setCancelled(true);
            return;
        }

        // Player damaging monster
        if (damaged instanceof Monster
                && damager instanceof Player damagerPlayer
                && !hasClaimBypass(damagerPlayer, "mob_attacking")
                && !mobAttackingEnabled
                && isNotClaimMember(damagerPlayer, claim.get())) {
            event.setCancelled(true);
            return;
        }

        // Explosion damaging entity
        if (damaged instanceof LivingEntity
                || damaged instanceof ItemFrame
                || damaged instanceof Painting) {
            Setting setting = switch (damager) {
                case TNTPrimed ignored -> Setting.TNT_DESTRUCTION;
                case ExplosiveMinecart ignored -> Setting.TNT_DESTRUCTION;
                case Creeper ignored -> Setting.CREEPER_GRIEFING;
                case Fireball ignored -> Setting.GHAST_GRIEFING;
                case null, default -> null;
            };
            if (setting == null) return;

            if (!plugin.getClaimSettingsManager().isSettingEnabled(claim.get(), setting)) event.setCancelled(true);
        }
    }

    @EventHandler
    public void windCharge(ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof Projectile projectile)) return;

        if (projectile.getType() != EntityType.WIND_CHARGE
                && projectile.getType() != EntityType.BREEZE_WIND_CHARGE) return;

        Optional<Claim> claim = Claim.getClaim(projectile.getLocation().getChunk());
        if (claim.isEmpty()) return;

        if ((!(projectile.getShooter() instanceof Player player))) return;

        if (cancelIfNotClaimMember(player, claim.get(), event)) return;
        cancelIfNoPermission(player, claim.get(), Permission.PLACE_BLOCKS, event, "place");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile.getType() != EntityType.WIND_CHARGE && projectile.getType() != EntityType.BREEZE_WIND_CHARGE) return;

        Block hitBlock = event.getHitBlock();
        if (hitBlock == null || !(projectile.getShooter() instanceof Player player)) return;

        Optional<Claim> claim = Claim.getClaim(hitBlock.getChunk());
        if (claim.isEmpty()) return;

        if (cancelIfNotClaimMember(player, claim.get(), event)) return;

        Permission permission;
        if (Tag.DOORS.isTagged(hitBlock.getType())) permission = Permission.USE_DOORS;
        else if (Tag.PRESSURE_PLATES.isTagged(hitBlock.getType())) permission = Permission.USE_PRESSURE_PLATES;
        else if (Tag.TRAPDOORS.isTagged(hitBlock.getType())) permission = Permission.USE_TRAPDOORS;
        else if (Tag.FENCE_GATES.isTagged(hitBlock.getType())) permission = Permission.USE_GATES;
        else return;

        cancelIfNoPermission(player, claim.get(), permission, event, "interact");
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.isCancelled()) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;

        Optional<Claim> claim = Claim.getClaim(event.getLocation().getChunk());
        if (claim.isEmpty()) return;

        Setting setting = event.getEntity() instanceof Monster ? Setting.MONSTER_SPAWNING : Setting.ANIMAL_SPAWNING;
        if (!plugin.getClaimSettingsManager().isSettingEnabled(claim.get(), setting)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        Block block = event.getBlock();

        Optional<Claim> claim = Claim.getClaim(block.getChunk());
        if (claim.isEmpty()) return;

        if (cancelIfNotClaimMember(player, claim.get(), event)) return;

        if (block.getType() == claim.get().getClaimBlockType()
                && block.getLocation().equals(claim.get().getClaimBlockLocation())
                && !player.isSneaking()) {
            event.setCancelled(true);
            return;
        }
        Permission permission = block.getType() == Material.SPAWNER ? Permission.BREAK_SPAWNER : Permission.BREAK_BLOCKS;
        cancelIfNoPermission(player, claim.get(), permission, event, "break");
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        Optional<Claim> claim = Claim.getClaim(event.getBlock().getChunk());
        if (claim.isEmpty()) return;

        if (cancelIfNotClaimMember(event.getPlayer(), claim.get(), event)) return;

        Permission permission = event.getBlock().getType() == Material.SPAWNER ? Permission.PLACE_SPAWNER : Permission.PLACE_BLOCKS;
        cancelIfNoPermission(event.getPlayer(), claim.get(), permission, event, "place");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.useInteractedBlock() == Event.Result.DENY) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();

        Optional<Claim> claim = Claim.getClaim(block.getChunk());
        if (claim.isEmpty()) return;

        // Claim block
        if (block.getType() == claim.get().getClaimBlockType()
                && claim.get().getClaimBlockLocation().equals(block.getLocation())) {
            if ((player.hasPermission("nclaim.admin") ||
                    coopManager.isClaimOwner(claim.get(), player) ||
                    coopManager.hasPermission(player, claim.get(), Permission.OPEN_CLAIM_MENU))
                    && event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
                new ClaimManagementMenu(player, claim.get(), player.hasPermission("nclaim.admin") && player.isSneaking());
            }
            event.setCancelled(true);
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

        Optional<Claim> claim = Claim.getClaim(event.getRightClicked().getLocation().getChunk());
        if (claim.isEmpty()) return;

        if (cancelIfNotClaimMember(event.getPlayer(), claim.get(), event)) return;
        cancelIfNoPermission(event.getPlayer(), claim.get(), Permission.INTERACT_ARMOR_STAND, event, "interact");
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

        Optional<Claim> claim = Claim.getClaim(event.getEntity().getLocation().getChunk());
        if (claim.isEmpty()) return;

        if (cancelIfNotClaimMember(event.getPlayer(), claim.get(), event)) return;
        cancelIfNoPermission(event.getPlayer(), claim.get(), Permission.LEASH_MOBS, event, "interact");
    }

    @EventHandler
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        if (event.isCancelled()
                || hasClaimBypass(event.getPlayer(), "interact")) return;

        Optional<Claim> claim = Claim.getClaim(event.getBlock().getChunk());
        if (claim.isEmpty()) return;

        if (cancelIfNotClaimMember(event.getPlayer(), claim.get(), event)) return;

        Permission permission = switch (event.getBlock().getType()) {
            case WATER -> event.getBucket().asItemType() == ItemType.WATER_BUCKET
                    ? Permission.MISC_ENTITY_INTERACT
                    : Permission.TAKE_WATER;
            case LAVA -> Permission.TAKE_LAVA;
            case POWDER_SNOW, POWDER_SNOW_CAULDRON -> Permission.TAKE_POWDERED_SNOW;
            default -> null;
        };
        if (permission == null) return;
        cancelIfNoPermission(event.getPlayer(), claim.get(), permission, event, "interact");
    }

    @EventHandler
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.isCancelled()
                || hasClaimBypass(event.getPlayer(), "interact")) return;

        Optional<Claim> claim = Claim.getClaim(event.getBlock().getChunk());
        if (claim.isEmpty()) return;

        if (cancelIfNotClaimMember(event.getPlayer(), claim.get(), event)) return;

        Permission permission = switch (event.getBucket()) {
            case Material.WATER_BUCKET,
                 Material.AXOLOTL_BUCKET,
                 Material.COD_BUCKET,
                 Material.PUFFERFISH_BUCKET,
                 Material.SALMON_BUCKET,
                 Material.TADPOLE_BUCKET,
                 Material.TROPICAL_FISH_BUCKET -> Permission.PLACE_WATER;
            case Material.LAVA_BUCKET -> Permission.PLACE_LAVA;
            case Material.POWDER_SNOW_BUCKET -> Permission.PLACE_POWDERED_SNOW;
            default -> throw new IllegalStateException("Unexpected value for bucket use: " + event.getBucket());
        };
        cancelIfNoPermission(event.getPlayer(), claim.get(), permission, event, "interact");
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
        if (toClaim.isEmpty() || toClaim.get().getSettings().isEnabled(Setting.ALLOW_LIQUID_INFLOW)) return;

        if (fromClaim.isEmpty() || !fromClaim.get().getOwner().equals(toClaim.get().getOwner()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!(event.getBlock().getType() == Material.FARMLAND && event.getEntity() instanceof Player player)) return;

        Optional<Claim> claim = Claim.getClaim(event.getBlock().getChunk());
        if (claim.isEmpty()) return;

        if (cancelIfNotClaimMember(player, claim.get(), event)) event.setCancelled(true);

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