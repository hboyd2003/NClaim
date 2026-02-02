package nesoi.aysihuniks.nclaim.model;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.*;

@Getter
@NoArgsConstructor
public class CoopData {
    private HashSet<UUID> coopPlayers = new HashSet<>();
    private HashMap<UUID, Date> joinDates = new HashMap<>();
    private HashMap<UUID, CoopPermission> permissions = new HashMap<>();

    public CoopData(HashSet<UUID> coopPlayers,
                    HashMap<UUID, Date> joinDates,
                    HashMap<UUID, CoopPermission> permissions) {
        this.coopPlayers = coopPlayers;
        this.joinDates = joinDates;
        this.permissions = permissions;
    }
}
