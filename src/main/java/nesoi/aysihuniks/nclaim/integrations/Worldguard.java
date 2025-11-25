package nesoi.aysihuniks.nclaim.integrations;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import nesoi.aysihuniks.nclaim.NClaim;
import org.bukkit.Location;

import java.util.Optional;

public class Worldguard {
    private final WorldGuard worldguard;

    public Worldguard() {
        this.worldguard = WorldGuard.getInstance();
    }


    public boolean isInBlacklistedRegion(Location location) {
        RegionContainer regionContainer = worldguard.getPlatform().getRegionContainer();
        Optional<ApplicableRegionSet> regions = Optional.ofNullable(location.getWorld())
                .map(world -> regionContainer.get(BukkitAdapter.adapt(world)))
                .map(regionManager -> regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(location)));

        if (regions.isPresent()) {
            for (ProtectedRegion region : regions.get()) {
                if (NClaim.inst().getNconfig().getBlacklistedRegions().contains(region.getId()))
                    return false;
            }
        }
        return true;
    }
}
