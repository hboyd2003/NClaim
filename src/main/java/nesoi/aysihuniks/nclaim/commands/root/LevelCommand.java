package nesoi.aysihuniks.nclaim.commands.root;

import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.commands.BaseCommand;
import nesoi.aysihuniks.nclaim.model.Claim;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.nandayo.dapi.message.ChannelType;

import java.util.Optional;

public class LevelCommand extends BaseCommand {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            ChannelType.CHAT.send(sender, NClaim.inst().getLangManager().getString("command.must_be_player"));
            return true;
        }

        if (!player.hasPermission("nclaim.level") && !player.hasPermission("nclaim.use")) {
            ChannelType.CHAT.send(player, NClaim.inst().getLangManager().getString("command.permission_denied"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("queue")) {
            NClaim.inst().getBlockValueManager().checkQueueStatus(player.getUniqueId());
            return true;
        }

        Optional<Claim> claim = Claim.getClaim(player.getLocation().getChunk());

        if (claim.isEmpty()) {
            ChannelType.CHAT.send(player, NClaim.inst().getLangManager().getString("command.not_in_claim"));
            return true;
        }

        if (!claim.get().getOwner().equals(player.getUniqueId()) && !player.hasPermission("nclaim.admin")) {
            ChannelType.CHAT.send(player, NClaim.inst().getLangManager().getString("claim.not_yours"));
            return true;
        }

        NClaim.inst().getBlockValueManager().requestClaimCalculation(
                player.getUniqueId(),
                player.getName(),
                claim.get()
        );

        return true;
    }
}