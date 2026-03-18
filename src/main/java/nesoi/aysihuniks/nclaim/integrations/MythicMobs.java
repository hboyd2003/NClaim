package nesoi.aysihuniks.nclaim.integrations;

import io.lumine.mythic.api.mobs.entities.SpawnReason;
import io.lumine.mythic.bukkit.events.MythicMobPreSpawnEvent;
import nesoi.aysihuniks.nclaim.enums.Setting;
import nesoi.aysihuniks.nclaim.model.Claim;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MythicMobs implements Listener {
    @EventHandler
    private void onMythicMobSpawn(MythicMobPreSpawnEvent event) {
        if (event.getSpawnReason() == SpawnReason.COMMAND) return; // Allow spawns by command to bypass

        Claim.getClaim(event.getLocation().getChunk())
                .map(Claim::getSettings)
                .map(claimSetting -> claimSetting.isEnabled(Setting.MONSTER_SPAWNING)) // Consider all MythicMobs to be monsters since it is impossible to tell the difference.
                .ifPresent(monsterSpawningEnabled -> {
                    if (!monsterSpawningEnabled) event.setCancelled(true);
                });
    }
}
