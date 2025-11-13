package nesoi.aysihuniks.nclaim.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.nandayo.dapi.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Scanner;
import java.util.function.Consumer;

public class UpdateChecker {

    private final int resourceId;
    private final Plugin plugin;

    public UpdateChecker(Plugin plugin, int resourceId) {
        this.resourceId = resourceId;
        this.plugin = plugin;
    }

    public void getVersion(final Consumer<String> consumer) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try (InputStream is = new URI("https://api.spigotmc.org/legacy/update.php?resource=" + this.resourceId + "/~").toURL().openStream();
                 Scanner scann = new Scanner(is)) {
                if (scann.hasNext()) {
                    consumer.accept(scann.next());
                }
            } catch (IOException | URISyntaxException e) {
                Util.log("&cUnable to check for updates: " + e.getMessage());
            }
        });
    }
}
