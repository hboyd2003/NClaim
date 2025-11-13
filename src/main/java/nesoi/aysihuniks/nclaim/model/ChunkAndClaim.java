package nesoi.aysihuniks.nclaim.model;

import org.bukkit.Chunk;
import org.jetbrains.annotations.Nullable;

public record ChunkAndClaim(Chunk chunk, Claim claim, String error) {

    public ChunkAndClaim(@Nullable Chunk chunk, @Nullable Claim claim, @Nullable String error) {
        this.chunk = chunk;
        this.claim = claim;
        this.error = error;
    }

}
