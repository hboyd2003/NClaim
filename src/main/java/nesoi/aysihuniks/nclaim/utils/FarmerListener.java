package nesoi.aysihuniks.nclaim.utils;

import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.api.events.*;
import nesoi.aysihuniks.nclaim.model.Claim;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.nandayo.dapi.message.ChannelType;
import xyz.geik.farmer.Main;
import xyz.geik.farmer.api.handlers.FarmerBoughtEvent;
import xyz.geik.farmer.api.managers.FarmerManager;
import xyz.geik.farmer.model.Farmer;
import xyz.geik.farmer.model.user.FarmerPerm;
import xyz.geik.farmer.model.user.User;

import java.util.Optional;
import java.util.UUID;

public class FarmerListener implements Listener {

    @EventHandler
    public void buyClaim(ClaimCreateEvent event) {
        String regionID = event.getClaim().getRegionID();
        if(Main.getConfigFile().getSettings().isAutoCreateFarmer()) {
            new Farmer(regionID, 0, event.getSender().getUniqueId());
            ChannelType.CHAT.send(event.getSender(), Main.getLangFile().getMessages().getBoughtFarmer());
        }
    }

    @EventHandler
    public void addCoop(ClaimCoopAddEvent event) {
        String regionID = event.getClaim().getRegionID();
        if (!FarmerManager.getFarmers().containsKey(regionID)) return;
        Player coopPlayer = event.getCoopPlayer();
        FarmerManager.getFarmers().get(regionID).addUser(coopPlayer.getUniqueId(), coopPlayer.getName(), FarmerPerm.COOP);
    }

    @EventHandler
    public void kickCoop(ClaimCoopRemoveEvent event) {
        String regionID = event.getClaim().getRegionID();
        if (!FarmerManager.getFarmers().containsKey(regionID)) return;
        UUID coopPlayer = event.getCoopPlayerUUID();
        Optional<User> user = FarmerManager.getFarmers().get(regionID).getUsers().stream().filter(u -> u.getUuid().equals(coopPlayer)).findFirst();
        user.ifPresent(FarmerManager.getFarmers().get(regionID)::removeUser);
    }

    @EventHandler
    public void removeClaim(ClaimRemoveEvent event) {
        String regionID = event.getClaim().getRegionID();
        if (!FarmerManager.getFarmers().containsKey(regionID)) return;
        FarmerManager.getFarmers().remove(regionID);
    }

    @EventHandler
    public void buyFarmer(FarmerBoughtEvent event) {
        String farmerRegionId = event.getFarmer().getRegionID();

        Optional<Claim> claim = Claim.getClaims().stream()
                .filter(c -> c.getRegionID().equals(farmerRegionId))
                .findFirst();

        if (claim.isEmpty()) return;

        for (UUID coopPlayer : claim.get().getCoopPlayers()) {
            String playerName = NClaim.inst().getServer().getOfflinePlayer(coopPlayer).getName();
            event.getFarmer().addUser(coopPlayer, playerName, FarmerPerm.COOP);
        }
    }
}
