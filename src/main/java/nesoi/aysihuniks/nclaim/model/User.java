package nesoi.aysihuniks.nclaim.model;

import lombok.Getter;
import lombok.Setter;
import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

@Getter
@Setter
public class User {
    private static final Collection<User> users = new ArrayList<>();
    private final UUID uuid;
    private double balance;
    private String skinTexture;
    private final Collection<Claim> playerClaims;
    private final Collection<Claim> coopClaims;

    public User(UUID uuid, double balance, String skinTexture, Collection<Claim> playerClaims, Collection<Claim> coopClaims) {
        this.uuid = uuid;
        this.balance = balance;
        this.skinTexture = skinTexture;
        this.playerClaims = playerClaims;
        this.coopClaims = coopClaims;
        users.add(this);
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public String getFormattedBalance() {
        return String.format("%.2f", this.balance);
    }

    public void addBalance(double amount) {
        this.balance += amount;
    }

    public static User getUser(UUID uuid) {
        return users.stream()
                .filter(user -> user.getUuid().equals(uuid))
                .findFirst()
                .orElse(null);
    }

    public static void loadUser(UUID uuid) {
        if (getUser(uuid) != null) return;

        loadFromDatabase(NClaim.inst().getDatabaseManager(), uuid);
    }

    private static void loadFromDatabase(DatabaseManager dbManager, UUID uuid) {
        User user = dbManager.loadUser(uuid);
        if (user == null) {
            user = createNewUser(uuid);
        }
        updateClaimCollections(user);
    }

    private static User createNewUser(UUID uuid) {
        return new User(uuid, 0.0, null, new ArrayList<>(), new ArrayList<>());
    }

    private static void updateClaimCollections(User user) {
        user.getPlayerClaims().clear();
        user.getCoopClaims().clear();

        user.getPlayerClaims().addAll(Claim.getClaims().stream()
                .filter(c -> c.getOwner().equals(user.getUuid()))
                .toList());

        user.getCoopClaims().addAll(Claim.getClaims().stream()
                .filter(c -> c.getCoopPlayers().contains(user.getUuid()))
                .toList());
    }

    public static void saveUser(UUID uuid) {
        User user = getUser(uuid);
        if (user == null) return;

        NClaim.inst().getDatabaseManager().saveUser(user);

    }
}