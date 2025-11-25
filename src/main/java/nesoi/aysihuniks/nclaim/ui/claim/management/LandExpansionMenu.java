package nesoi.aysihuniks.nclaim.ui.claim.management;

import com.google.common.collect.Sets;
import lombok.Getter;
import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.enums.Direction;
import nesoi.aysihuniks.nclaim.model.Coordinate2D;
import nesoi.aysihuniks.nclaim.model.InventorySlot;
import nesoi.aysihuniks.nclaim.ui.shared.BackgroundMenu;
import nesoi.aysihuniks.nclaim.ui.shared.BaseMenu;
import nesoi.aysihuniks.nclaim.ui.shared.ConfirmMenu;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.utils.HeadUtil;
import nesoi.aysihuniks.nclaim.utils.MessageType;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nandayo.dapi.guimanager.MenuType;
import org.nandayo.dapi.guimanager.button.Button;
import org.nandayo.dapi.guimanager.button.SingleSlotButton;
import org.nandayo.dapi.util.ItemCreator;
import org.nandayo.dapi.util.Util;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LandExpansionMenu extends BaseMenu {

    private final @NotNull Claim claim;
    private final boolean admin;
    private final Coordinate2D centerChunk;
    private final static InventorySlot centerSlot = new InventorySlot(2, 4);

    private LandExpansionMenu(@NotNull Player player, @NotNull Claim claim, boolean admin, Coordinate2D centerChunk) {
        super("claim_expand_menu");
        this.claim = claim;
        this.admin = admin;
        this.centerChunk = centerChunk;
        setupMenu(player);
    }

    public LandExpansionMenu(@NotNull Player player, @NotNull Claim claim, boolean admin) {
        this(player, claim, admin, Coordinate2D.ofChunk(claim.getChunk()));
    }

    @Override
    public Function<Integer, SingleSlotButton> backgroundButtonFunction() {
        return slot -> {
            InventorySlot inventorySlot = InventorySlot.fromInteger(slot);
            if (inventorySlot.getRow() == 0
                    || inventorySlot.getRow() == 5
                    || inventorySlot.getColumn() == 0
                    || inventorySlot.getColumn() == 8) {
                return BackgroundMenu.getBackground1Button(slot);
            }
            return BackgroundMenu.getBackground2Button(slot);
        };
    }

    private void setupMenu(Player player) {
        createInventory(MenuType.CHEST_6_ROWS, getString("title").replace("{claim_name}", claim.getClaimName()));

        addButton(new Button() {
            @Override
            public @NotNull Set<Integer> getSlots() {
                return Sets.newHashSet(0);
            }

            @Override
            public ItemStack getItem() {
                return ItemCreator.of(getMaterialFullPath("back"))
                        .name(NClaim.inst().getGuiLangManager().getString("back.display_name"))
                        .get();
            }

            @Override
            public void onClick(@NotNull Player player, @NotNull ClickType clickType) {
                MessageType.MENU_BACK.playSound(player);
                new ClaimManagementMenu(player, claim, admin);
            }
        });

        for(ScrollButton scrollButton : ScrollButton.values()) {
            addButton(new Button() {
                @Override
                protected @NotNull Set<Integer> getSlots() {
                    return Sets.newHashSet(scrollButton.getInventorySlot().asSlot());
                }

                @Override
                public @Nullable ItemStack getItem() {
                    String directionKey = "scroll_button.directions." + scrollButton.name();
                    String localizedDirection = NClaim.inst().getGuiLangManager().getString(directionKey);
                    return ItemCreator.of(Material.OAK_BUTTON)
                            .name(getString("scroll_button.display_name").replace("{direction}", localizedDirection))
                            .get();
                }

                @Override
                public void onClick(@NotNull Player p, @NotNull ClickType clickType) {
                    if(!canScroll(scrollButton.direction)) {
                        MessageType.WRONG.playSound(player);
                        return;
                    }
                    new LandExpansionMenu(p, claim, admin, centerChunk.offsetInDirection(scrollButton.direction, 1));
                }
            });
        }

        // Add chunks set in on all sides by 1
        for (int slot = 0; slot + 1 <= 6 * 9; slot++) {
            InventorySlot inventorySlot = InventorySlot.fromInteger(slot);
            // Ignore border
            if (inventorySlot.getRow() == 0
                    || inventorySlot.getRow() == 5
                    || inventorySlot.getColumn() == 0
                    || inventorySlot.getColumn() == 8) continue;
            addChunkButton(inventorySlot, player);
        }
        displayTo(player);
    }

    private void addChunkButton(InventorySlot slot, Player player) {
        Chunk thatChunk = findChunkFromSlot(slot);
        Optional<Claim> thatClaim = Claim.getClaim(thatChunk);

        String configPath;

        boolean purchasable = false;

        if (!this.claim.isChunkAdjacent(thatChunk) && !admin) {
            configPath = "not_adjacent";
        } else if (thatClaim.isEmpty()) {
            configPath = "expand";
            purchasable = true;
        } else if (claim.getLands().contains(NClaim.serializeChunk(thatChunk))) {
            configPath = "claimed";
        } else if (claim == thatClaim.get()) {
            configPath = "center";
        } else {
            configPath = "claimed_another_player";
        }

        if (player.getLocation().getChunk().equals(thatChunk))
            configPath += "_at_chunk";

        ItemStack material = getMaterial(configPath);
        if (material.getType() == Material.PLAYER_HEAD) {
            material = HeadUtil.createHead(player.getPlayerProfile());
        }

        addButton(createChunkButton(slot.asSlot(), configPath, material, thatChunk, purchasable));
    }

    private Button createChunkButton(int slot, String configPath, ItemStack material, Chunk thatChunk, boolean purchasable) {
        return new Button() {
            @Override
            public @NotNull Set<Integer> getSlots() {
                return Sets.newHashSet(slot);
            }

            @Override
            public ItemStack getItem() {
                double landPrice = calculateChunkPrice();
                String displayName = getString(configPath + ".display_name");
                List<String> lore = new ArrayList<>(getStringList(configPath + ".lore"));

                String priceStr = admin
                        ? NClaim.inst().getGuiLangManager().getString("free")
                        : landPrice < 0
                            ? "N/A"
                            : String.valueOf(landPrice);
                lore.replaceAll(s -> s.replace("{price}", priceStr));

                return ItemCreator.of(material)
                        .name(displayName)
                        .lore(lore)
                        .get();
            }

            @Override
            public void onClick(@NotNull Player player, @NotNull ClickType clickType) {
                if (clickType.isLeftClick() && purchasable) {
                    Consumer<String> onFinish = (result) -> {
                        if ("confirmed".equals(result)) {
                            NClaim.inst().getClaimService().buyLand(claim, player, thatChunk, admin);
                            new LandExpansionMenu(player, claim, admin, centerChunk);
                        } else if ("declined".equals(result)) {
                            new LandExpansionMenu(player, claim, admin, centerChunk);
                        }
                    };

                    new ConfirmMenu(player,
                            NClaim.inst().getGuiLangManager().getString("confirm_menu.children.claim_expand.display_name"),
                            NClaim.inst().getGuiLangManager().getStringList("confirm_menu.children.claim_expand.lore")
                                    .stream()
                                    .map(s -> s.replace("{price}", String.valueOf(calculateChunkPrice())).replace("{claim_name}", claim.getClaimName()))
                                    .collect(Collectors.toList()),
                            onFinish);
                } else if (clickType.isRightClick()) {
                    player.closeInventory();
                    NClaim.inst().getClaimVisualizerService().showClaimBorders(player, thatChunk);
                }
            }
        };
    }

    private double calculateChunkPrice() {
        int currentChunkCount = 1 + claim.getLands().size();
        int nextChunkNumber = currentChunkCount + 1;

        return NClaim.inst().getNconfig().getTieredPrice(nextChunkNumber);
    }

    private Chunk findChunkFromSlot(InventorySlot slot) {
        return claim.getChunk().getWorld().getChunkAt(
                centerChunk.x() + (slot.getColumn() - centerSlot.getColumn()),
                centerChunk.z() + (slot.getRow() - centerSlot.getRow()));
    }


    private boolean canScroll(Direction direction) {
        Coordinate2D farthestChunkCoords = Coordinate2D.ofChunk(claim.getFarthestChunk(direction));
        Coordinate2D farthestPurchasableChunk = farthestChunkCoords.offsetInDirection(direction, 3);
        Coordinate2D newCenterChunk = this.centerChunk.offsetInDirection(direction, 1);

        Coordinate2D distance = newCenterChunk.distanceFrom(farthestPurchasableChunk);
        return switch (direction) {
            case NORTH -> distance.z() >= 4 - centerSlot.getRow(); // TODO: Why 4? It should be 5
            case SOUTH -> distance.z() > centerSlot.getRow();
            case EAST -> distance.x() >= centerSlot.getColumn();
            case WEST -> distance.x() >= 8 - centerSlot.getColumn();
        };
    }

    @Getter
    private enum ScrollButton {
        NORTH(new InventorySlot(0, 4), Direction.NORTH),
        SOUTH(new InventorySlot(5, 4), Direction.SOUTH),
        EAST(new InventorySlot(2, 8), Direction.EAST),
        WEST(new InventorySlot(2, 0), Direction.WEST);


        private final InventorySlot inventorySlot;
        private final Direction direction;
        ScrollButton(InventorySlot inventorySlot, Direction direction) {
            this.inventorySlot = inventorySlot;
            this.direction = direction;
        }
    }
}