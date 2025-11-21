package nesoi.aysihuniks.nclaim.enums;

import lombok.Getter;

@Getter
public enum Direction {
    NORTH(0, -1),
    SOUTH(0, 1),
    EAST(1, 0),
    WEST(-1, 0);

    private final int x;
    private final int z;
    Direction(int x, int z) {
        this.x = x;
        this.z = z;
    }
}
