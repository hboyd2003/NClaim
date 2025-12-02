package nesoi.aysihuniks.nclaim.service;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
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

    private boolean cancelIfNoPermission(Player player, Claim claim, Permission permission, Cancellable event, String bypassType) {
        if (claim == null
                || hasClaimBypass(player, bypassType)
                || coopManager.hasPermission(player, claim, permission)) return false;

        event.setCancelled(true);
        sendCooldownMessage(player, plugin.getLangManager().getString("command.permission_denied"));
        return true;
    }

    private boolean hasNoPermission(Player player, Claim claim, Permission permission, String bypassType) {
        if (claim == null
                || hasClaimBypass(player, bypassType)
                || coopManager.hasPermission(player, claim, permission)) return false;

        sendCooldownMessage(player, plugin.getLangManager().getString("command.permission_denied"));
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
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
        Optional<Claim> explodeClaim = Claim.getClaim(event.getLocation().getChunk());
        if (explodeClaim.isEmpty()) return;

        if (event.getEntity() instanceof AbstractWindCharge) {
            Entity shooter = (Entity) ((AbstractWindCharge) event.getEntity()).getShooter();
            if (!(shooter instanceof Player)) return;

            if (hasClaimBypass((Player) shooter, "interact")) return;
            if (isNotClaimMember((Player) shooter, explodeClaim.get())) {
                event.blockList().clear();
                return;
            }

            cancelIfNotClaimMember((Player) shooter, explodeClaim.get(), event);

            event.blockList().removeIf(block -> {
                Optional<Claim> blockClaim = Claim.getClaim(block.getChunk());

                Permission permission = null;
                if (Tag.DOORS.isTagged(block.getType())) permission = Permission.USE_DOORS;
                else if (Tag.PRESSURE_PLATES.isTagged(block.getType())) permission = Permission.USE_PRESSURE_PLATES;
                else if (Tag.TRAPDOORS.isTagged(block.getType())) permission = Permission.USE_TRAPDOORS;
                else if (Tag.PRESSURE_PLATES.isTagged(block.getType())) permission = Permission.USE_PRESSURE_PLATES;
                else if (Tag.FENCE_GATES.isTagged(block.getType())) permission = Permission.USE_GATES;
                else if (Tag.BUTTONS.isTagged(block.getType())) permission = Permission.USE_BUTTONS;
                else if (block.getType() == Material.LEVER) permission = Permission.USE_LEVERS;

                if (permission == null) return false;

                return blockClaim.isPresent() && !coopManager.hasPermission((Player) shooter, blockClaim.get(), permission);
            });
            return;
        }

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

        event.setCancelled(!shouldAffect(damaged, damager, claim.get()));
    }

    public boolean shouldAffect(Entity affected, Entity affecter, Claim claim) {
        // PvP
        if (affected instanceof Player
                && affecter instanceof Player) {
            if (hasClaimBypass((Player) affecter, "pvp")) return true;
            boolean damagedNotInPVPClaim = !plugin.getClaimSettingsManager().isSettingEnabled(claim, Setting.CLAIM_PVP);

            boolean damagerNotInPVPClaim = Claim.getClaim(affecter.getLocation().getChunk())
                    .map(damagerClaim -> !plugin.getClaimSettingsManager().isSettingEnabled(damagerClaim, Setting.CLAIM_PVP))
                    .orElse(false);

            return !damagedNotInPVPClaim && !damagerNotInPVPClaim;
        }

        // Player removes item from item frame
        if (affected instanceof ItemFrame
                && affecter instanceof Player damagerPlayer) {
            //if (cancelIfNotClaimMember(damagerPlayer, claim, event)) return;
            if (hasClaimBypass(damagerPlayer, "") || isNotClaimMember(damagerPlayer, claim)) return false;

            //cancelIfNoPermission(damagerPlayer, claim, Permission.INTERACT_ITEM_FRAME, event, "interact");
            if (hasNoPermission(damagerPlayer, claim, Permission.INTERACT_ITEM_FRAME,"interact")) return false;
        }

        // Damage to non-player entity
        if (affecter instanceof Player damagerPlayer
                && (affected instanceof ArmorStand
                || affected instanceof Painting
                || affected instanceof ItemFrame
                || affected instanceof Boat)) {

            return !hasClaimBypass(damagerPlayer, "")
                    && !isNotClaimMember(damagerPlayer, claim)
                    && !hasNoPermission(damagerPlayer, claim, Permission.INTERACT_ITEM_FRAME, "interact");
        }

        boolean mobAttackingEnabled = plugin.getClaimSettingsManager().isSettingEnabled(claim, Setting.MOB_ATTACKING);

        // Monster damaging player
        if (affected instanceof Player damagedPlayer
                && affecter instanceof Monster
                && !mobAttackingEnabled
                && isNotClaimMember(damagedPlayer, claim)) {
            return false;
        }

        // Player damaging monster
        if (affected instanceof Monster
                && affecter instanceof Player damagerPlayer
                && !hasClaimBypass(damagerPlayer, "mob_attacking")
                && !mobAttackingEnabled
                && isNotClaimMember(damagerPlayer, claim)) {
            return false;
        }

        // Explosion damaging entity
        if (affected instanceof LivingEntity
                || affected instanceof ItemFrame
                || affected instanceof Painting) {
            Setting setting = switch (affecter) {
                case TNTPrimed ignored -> Setting.TNT_DESTRUCTION;
                case ExplosiveMinecart ignored -> Setting.TNT_DESTRUCTION;
                case Creeper ignored -> Setting.CREEPER_GRIEFING;
                case Fireball ignored -> Setting.GHAST_GRIEFING;
                case null, default -> null;
            };

            //event.setCancelled(setting != null && plugin.getClaimSettingsManager().isSettingEnabled(claim, setting));
            return setting == null || !plugin.getClaimSettingsManager().isSettingEnabled(claim, setting);
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityKnockbackByEntity(ProjectileHitEvent event) {

    }

    @EventHandler
    public void onEntityInteract(EntityInteractEvent event) {
        if (!(event.getEntity() instanceof Projectile projectile)) return;

        Optional<Claim> claim = Claim.getClaim(event.getBlock().getChunk());
        if (claim.isEmpty()) return;

        // TODO: Skeleton, Breeze, etc interactions (don't forget tridents!)

        if (!(projectile.getShooter() instanceof Player damager)) return;
        Permission permission;
        if (Tag.BUTTONS.isTagged(event.getBlock().getType())) permission = Permission.USE_BUTTONS;
        else if (Tag.PRESSURE_PLATES.isTagged(event.getBlock().getType())) permission = Permission.USE_PRESSURE_PLATES;
        else if (event.getBlock().getType() == Material.BELL) permission = Permission.USE_BELL;
        else return;

        if (cancelIfNoPermission(damager, claim.get(), permission, event, "interact"))
            event.setCancelled(true);
    }

    @EventHandler
    public void onEntityKnockback(EntityKnockbackByEntityEvent event) {
        Entity knockbacked = event.getEntity();
        Optional<Claim> claim = Claim.getClaim(knockbacked.getChunk());
        if (claim.isEmpty()) return;

        Entity knockbacker = event.getHitBy();

        if (knockbacker == knockbacked) return;
        if (knockbacker instanceof Projectile projectile) knockbacker = (Entity) projectile.getShooter();


        event.setCancelled(!shouldAffect(knockbacked, knockbacker, claim.get()));
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
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

        Permission permission = getInteractPermission(block.getType());
        if (permission == null) return; // We only care about "known" interactions
        if (cancelIfNotClaimMember(player, claim.get(), event)) return;

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