package nesoi.aysihuniks.nclaim.integrations;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.enums.Balance;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.model.User;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Expansion extends PlaceholderExpansion {
    private static final Pattern WORLD_CHUNK_REGEX = Pattern.compile("((?:.*_)*.*)_((?:-\\d|\\d)\\d*)_((?:-\\d|\\d)\\d*)");

    private final NClaim plugin;

    public Expansion(NClaim plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getAuthor() {
        return "aysihuniks";
    }

    @Override
    public @NotNull String getIdentifier() {
        return "nclaim";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (params.isEmpty()) {
            return null;
        }

        if (params.equals("player_balance")) {
            return handlePlayerBalance(player);
        }

        if (params.startsWith("get_")) {
            return handleConfigPlaceholder(params);
        }

        if (params.startsWith("claim_main_value_")) {
            return handleClaimMainValue(params);
        }

        if (params.startsWith("claim_total_value_")) {
            return handleClaimTotalValue(params);
        }

        if (params.startsWith("block_value_")) {
            return handleBlockValue(params);
        }

        if (params.equals("coop_count") || params.startsWith("coop_count_") && params.split("_").length <= 3) {
            return handleCoopCount(params, player);
        }
        if (params.equals("claim_count") || params.startsWith("claim_count_") && params.split("_").length <= 3) {
            return handleClaimCount(params, player);
        }

        if (params.startsWith("expiration_")
                || params.startsWith("owner_")
                || params.startsWith("claim_name_")
                || params.startsWith("coop_count_")
                || params.startsWith("total_size_")) {
            return handleClaimInfo(params);
        }

        if (params.equals("name")) {
            return handleCurrentChunkName(player);
        }

        if (params.equals("owner")) {
            return handleCurrentChunkOwner(player);
        }

        return null;
    }

    private @Nullable String handlePlayerBalance(Player player) {
        if (player == null) return null;
        if (NClaim.inst().getBalanceSystem() == Balance.VAULT) {
            return String.valueOf(NClaim.inst().getEconomy().getBalance(player));
        } else {
            return String.valueOf(User.getUser(player.getUniqueId()).getBalance());
        }
    }

    private @Nullable String handleConfigPlaceholder(String params) {
        String[] parts = params.split("_");
        if (parts.length < 3) {
            return "Invalid config placeholder format";
        }

        String dataType = parts[1];
        String path = parts[2];

        return switch (dataType) {
            case "string" -> plugin.getConfigManager().getString(path, "Null");
            case "int" -> String.valueOf(plugin.getConfigManager().getInt(path, 0));
            case "boolean" -> String.valueOf(plugin.getConfigManager().getBoolean(path, false));
            case "list" -> handleListConfig(parts, path);
            case null, default -> "Unknown config data type: " + dataType;
        };
    }

    private String handleListConfig(String[] parts, String path) {
        if (parts.length < 4) {
            return "Invalid list placeholder: Index required";
        }
        try {
            int index = Integer.parseInt(parts[3]);
            List<String> list = plugin.getConfigManager().getStringList(path);
            if (list == null || list.isEmpty()) {
                return "List Not Found";
            }
            if (index < 0 || index >= list.size()) {
                return "Invalid Index";
            }
            return list.get(index);
        } catch (NumberFormatException e) {
            return "Invalid Index Format";
        }
    }

    private long getChunkValue(String params, boolean includeAllChunks) throws ParseException, IllegalArgumentException {
        Claim claim = parseClaimInfo(params);

        if (includeAllChunks) {
            return plugin.getBlockValueManager().calculateClaimValue(claim);
        } else {
            return plugin.getBlockValueManager().calculateChunkValue(claim.getChunk());
        }
    }

    private @NotNull String handleClaimMainValue(String params) {
        try {
            return String.valueOf(getChunkValue(params, false));
        } catch (ParseException | IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    private @NotNull String handleClaimTotalValue(String params) {
        try {
            return String.valueOf(getChunkValue(params, true));
        } catch (ParseException | IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    private @NotNull String handleBlockValue(String params) {
        String materialName = params.substring("block_value_".length()).toUpperCase();
        try {
            Material material = Material.valueOf(materialName);
            return String.valueOf(plugin.getBlockValueManager().getBlockValue(material));
        } catch (IllegalArgumentException e) {
            return "Invalid material: " + materialName;
        }
    }

    private @Nullable String handleClaimInfo(String params) {
        String prefix;
        if (params.startsWith("expiration_")) {
            prefix = "expiration";
        } else if (params.startsWith("owner_")) {
            prefix = "owner";
        } else if (params.startsWith("claim_owner_")) {
            prefix = "claim_name";
        } else if (params.startsWith("coop_count_")) {
            prefix = "coop_count";
        } else if (params.startsWith("total_size_")) {
            prefix = "total_size";
        } else {
            return "Invalid placeholder format";
        }

        Claim claim;
        try {
            claim = parseClaimInfo(params.replace(prefix + "_", ""));
        } catch (ParseException | IllegalArgumentException e) {
            return e.getMessage();
        }

        return switch (prefix) {
            case "expiration" -> plugin.getClaimExpirationManager().getFormattedTimeLeft(claim);
            case "owner" -> Bukkit.getOfflinePlayer(claim.getOwner()).getName() != null ? Bukkit.getOfflinePlayer(claim.getOwner()).getName() : "Owner not found";
            case "coop_count" -> String.valueOf(claim.getCoopPlayers().size());
            case "total_size" -> String.valueOf(1 + claim.getLands().size());
            case "claim_name" -> claim.getClaimName();
            case null, default -> "Unknown placeholder prefix: " + prefix;
        };
    }

    private Claim parseClaimInfo(String input) throws ParseException {
        Matcher chunkMatcher = WORLD_CHUNK_REGEX.matcher(input);

        if (!chunkMatcher.matches()) throw new ParseException("Invalid claim info format", -1);

        World world = Bukkit.getWorld(chunkMatcher.group(1));
        if (world == null) throw new IllegalArgumentException("Unknown world: " + chunkMatcher.group(1));

        String chunkXStr = chunkMatcher.group(2);
        int chunkX;
        try {
            chunkX = Integer.parseInt(chunkXStr);
        } catch (NumberFormatException e) {
            throw new ParseException("Failed to parse \"" + chunkXStr + "\" into X coordinate integer", input.indexOf(chunkXStr));
        }

        String chunkZStr = chunkMatcher.group(3);
        int chunkZ;
        try {
            chunkZ = Integer.parseInt(chunkZStr);
        } catch (NumberFormatException e) {
            throw new ParseException("Failed to parse \"" + chunkXStr + "\" into Z coordinate integer", input.indexOf(chunkXStr));
        }


        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        Claim claim = Claim.getClaim(chunk);
        if (claim == null) throw new IllegalArgumentException("Claim not found");

        return claim;
    }

    private @NotNull String handleClaimCount(String params, Player player) {
        User user = getUserFromPlaceholder(params, player, "claim_count");
        if (user == null) return "User not found";
        int claimCount = user.getPlayerClaims().size();
        return String.valueOf(claimCount);
    }

    private @NotNull String handleCoopCount(String params, Player player) {
        User user = getUserFromPlaceholder(params, player, "coop_count");
        if (user == null) return "User not found";
        int coopCount = user.getCoopClaims().size();

        return String.valueOf(coopCount);
    }

    private @Nullable User getUserFromPlaceholder(String params, Player player, String prefix) {
        String[] parts = params.split("_", 3);

        String playerName = null;
        if (parts.length == 2) {
            if (player == null) return null;
            playerName = player.getName();
        } else if (parts.length == 3) {
            playerName = parts[2];
        }

        if (playerName == null || playerName.isEmpty()) return null;
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        return User.getUser(target.getUniqueId());
    }

    private @Nullable String handleCurrentChunkOwner(Player player) {
        if (player == null) return null;
        Chunk chunk = player.getLocation().getChunk();
        Claim claim = Claim.getClaim(chunk);
        if (claim == null) {
            return NClaim.inst().getLangManager().getString("claim.no_owner");
        }
        OfflinePlayer owner = Bukkit.getOfflinePlayer(claim.getOwner());
        return owner.getName() != null ? NClaim.inst().getLangManager().getString("claim.owner").replace("{owner}", owner.getName()) : NClaim.inst().getLangManager().getString("claim.no_owner");
    }

    private String handleCurrentChunkName(Player player) {
        if (player == null) return null;
        Chunk chunk = player.getLocation().getChunk();
        Claim claim = Claim.getClaim(chunk);
        if (claim == null) {
            return NClaim.inst().getLangManager().getString("claim.no_name");
        }

        return NClaim.inst().getLangManager().getString("claim.name").replace("{claim_name}", claim.getClaimName());
    }
}