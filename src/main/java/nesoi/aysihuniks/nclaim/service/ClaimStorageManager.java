package nesoi.aysihuniks.nclaim.service;

import lombok.RequiredArgsConstructor;
import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.model.Claim;
import org.nandayo.dapi.util.Util;

import java.util.*;

@RequiredArgsConstructor
public class ClaimStorageManager {
    private final NClaim plugin;

    private volatile boolean isLoading = false;

    public void loadClaims() {
        if (isLoading) {
            return;
        }

        isLoading = true;

        try {
            Claim.claims.clear();
            List<Claim> loadedClaims = plugin.getDatabaseManager().loadAllClaims();
            Claim.claims.addAll(loadedClaims);

            for (Claim claim : Claim.claims) {
                long value = plugin.getBlockValueManager().calculateClaimValue(claim);
                claim.setClaimValue(value);
            }

            Util.log("&aLoaded " + Claim.claims.size() + " claims from database.");
        } finally {
            isLoading = false;
        }
    }

    public void saveClaims() {
        try {
            plugin.getDatabaseManager().saveClaimsBatch(new ArrayList<>(Claim.claims));
            Util.log("&eSaved " + Claim.claims.size() + " claims to database.");
        } catch (Exception e) {
            Util.log("&cFailed to save claims to database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveClaim(Claim claim) {
            plugin.getDatabaseManager().saveClaim(claim);
    }
}