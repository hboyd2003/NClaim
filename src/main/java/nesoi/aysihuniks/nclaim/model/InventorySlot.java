package nesoi.aysihuniks.nclaim.model;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Range;

@Setter
@Getter
public class InventorySlot {
    @Range(from=0, to=Integer.MAX_VALUE) private int row;
    @Range(from=0, to=8) private int column;

    public InventorySlot(@Range(from=0, to=Integer.MAX_VALUE) int row, @Range(from=0, to=8) int column) {
        this.row = row;
        this.column = column;
    }

    public static InventorySlot fromInteger(int slot) {
        return new InventorySlot(slot / 9, slot % 9);
    }

    public static InventorySlot fromCoordinate2D(Coordinate2D coordinate2D) {
        return new InventorySlot(-coordinate2D.z(), coordinate2D.x());
    }

    public int asSlot() {
        return row * 9 + column;
    }

    public void setColumn(@Range(from=0, to=8) int column) {
        this.column = column;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        InventorySlot that = (InventorySlot) o;
        return row == that.row && column == that.column;
    }
}

