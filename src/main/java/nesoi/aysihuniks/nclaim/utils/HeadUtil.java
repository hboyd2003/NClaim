package nesoi.aysihuniks.nclaim.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

@SuppressWarnings("ALL")
@Getter
public class HeadUtil {

    public static ItemStack createHead(final String texture) {
        ResolvableProfile resolvableProfile = ResolvableProfile.resolvableProfile().addProperty(new ProfileProperty("textures", texture)).build();

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.setData(DataComponentTypes.PROFILE, resolvableProfile);

        return head;
    }

    public static ItemStack createHead(final PlayerProfile playerProfile) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile(playerProfile));

        return head;

    }
}