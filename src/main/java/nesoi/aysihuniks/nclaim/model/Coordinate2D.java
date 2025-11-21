package nesoi.aysihuniks.nclaim.model;

import nesoi.aysihuniks.nclaim.enums.Direction;
import org.bukkit.Chunk;

public record Coordinate2D(int x, int z) {
    public static Coordinate2D ofChunk(Chunk chunk) {
        return new Coordinate2D(chunk.getX(), chunk.getZ());
    }

    public Coordinate2D add(Coordinate2D coordinate2D) {
        return new Coordinate2D(coordinate2D.x + x, coordinate2D.z + z);
    }

    public Coordinate2D distanceFrom(Coordinate2D coordinate2D) {
        return new Coordinate2D(Math.abs(coordinate2D.x - x), Math.abs(coordinate2D.z - z));
    }

    public Coordinate2D offsetInDirection(Direction direction, int offset) {
        return new Coordinate2D(x + (direction.getX() * offset), z + (direction.getZ() * offset));
    }
}
