package nesoi.aysihuniks.nclaim.enums;

import lombok.Getter;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;

public enum Direction {
    // Cardinal
    NORTH(new Vector(0, 0, -1)),
    SOUTH(new Vector(0, 0, 1)),
    EAST(new Vector(1, 0, 0)),
    WEST(new Vector(-1, 0, 0)),

    // Ordinal
    NORTH_EAST(new Vector(1, 0, -1)),
    NORTH_WEST(new Vector(-1, 0, -1)),
    SOUTH_EAST(new Vector(1, 0, 1)),
    SOUTH_WEST(new Vector(-1, 0, 1));

    private final Vector vector;
    Direction(Vector vector) {
        this.vector = vector;
    }

    public Vector vector() {
        return vector;
    }

    public int x() {
        return (int) vector.getX();
    }

    public int z() {
        return (int) vector.getZ();
    }

    public static Collection<Direction> getCardinalDirections() {
        return List.of(NORTH, SOUTH, EAST, WEST);
    }

    public static Collection<Direction> getCornerDirections() {
        return List.of(NORTH_EAST, NORTH_WEST, SOUTH_EAST, SOUTH_WEST);
    }
}
