package nesoi.aysihuniks.nclaim.integrations;

import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.manager.PetManager;

import java.util.UUID;

public class EnhancedPets {
    private final PetManager petManager;

    public EnhancedPets() {
        petManager = Enhancedpets.getInstance().getPetManager();
    }

    public boolean isManagedPet(UUID uuid) {
        return petManager.isManagedPet(uuid);
    }

    public boolean isPetOwnedBy(UUID petUUID, UUID ownerUUID) {
        if (!petManager.isManagedPet(petUUID)) return false;

        return petManager.getPetData(petUUID).getOwnerUUID().equals(ownerUUID);
    }
}
