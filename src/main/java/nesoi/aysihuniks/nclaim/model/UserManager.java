package nesoi.aysihuniks.nclaim.model;

import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.utils.MessageType;
import nesoi.aysihuniks.nclaim.utils.UpdateChecker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.nandayo.dapi.message.ChannelType;

import java.util.UUID;

public class UserManager implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();

        User.loadUser(playerUUID);
        if (event.getPlayer().isOp() || event.getPlayer().hasPermission("nclaim.admin")) {
            if (NClaim.inst().getConfigManager().getBoolean("check_for_updates", true)) {
                UpdateChecker checker = new UpdateChecker(NClaim.inst(), 122527);
                checker.getVersion(latestVersion -> {
                    String currentVersion = NClaim.inst().getDescription().getVersion().split("-")[0]; // Ignore "special" version part
                    if (!currentVersion.equalsIgnoreCase(latestVersion)) {
                        ChannelType.CHAT.sendWithPrefix(event.getPlayer(), "&aA new version is available: &ev" + latestVersion + " &8(&7You are using: &ev" + currentVersion + "&8)");
                        MessageType.CONFIRM.playSound(event.getPlayer());
                    }
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();

        User.saveUser(playerUUID);
    }
}
