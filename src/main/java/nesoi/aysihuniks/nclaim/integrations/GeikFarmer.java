package nesoi.aysihuniks.nclaim.integrations;

import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.utils.FarmerListener;
import org.bukkit.Location;
import org.nandayo.dapi.util.Util;
import xyz.geik.farmer.Main;
import xyz.geik.farmer.integrations.Integrations;

import java.util.UUID;

public class GeikFarmer extends Integrations {

    public GeikFarmer() {
        super(new FarmerListener());
    }

    @Override
    public UUID getOwnerUUID(String regionId) {
        return Claim.getClaims().stream()
                .filter(claim -> claim.getRegionID().equals(regionId))
                .findFirst()
                .map(Claim::getOwner)
                .orElse(null);
    }

    @Override
    public UUID getOwnerUUID(Location location) {
        return Claim.getClaim(location.getChunk())
                .map(Claim::getOwner)
                .orElse(null);
    }

    @Override
    public String getRegionID(Location location) {
        return Claim.getClaim(location.getChunk())
                .map(Claim::getRegionID)
                .orElse(null);
    }


    public static void registerIntegration() {
        if (NClaim.inst().getServer().getPluginManager().getPlugin("Farmer") != null) {
            try {
                Main.setIntegration(new GeikFarmer());
                Util.log("&aFarmer integration enabled successfully!");
            } catch (Exception e) {
                Util.log("&cFailed to initialize Farmer integration: " + e.getMessage());
            }
        } else {
            Util.log("&eFarmer plugin not found! Farmer integration disabled.");
        }
    }
}
