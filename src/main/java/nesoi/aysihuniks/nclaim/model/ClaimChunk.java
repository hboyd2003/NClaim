package nesoi.aysihuniks.nclaim.model;

import org.bukkit.Chunk;
import org.bukkit.World;

public record ClaimChunk(int x, int z) {
    public static ClaimChunk fromChunk(Chunk chunk) {
        return new ClaimChunk(chunk.getX(), chunk.getZ());
    }

    public Chunk toChunk(World world) {
        return world.getChunkAt(this.x, this.z);
    }

    public Long serialize() {
        return (long) x & 0xffffffffL | ((long) z & 0xffffffffL) << 32;
    }

    public static ClaimChunk deserialize(long chunk) {
        return new ClaimChunk((int) (chunk & 0xffffffffL),  (int) (chunk >>> 32));
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(x);
        result = 31 * result + Integer.hashCode(z);
        return result;
    }
}
