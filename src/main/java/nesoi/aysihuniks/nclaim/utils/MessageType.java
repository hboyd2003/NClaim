package nesoi.aysihuniks.nclaim.utils;

import nesoi.aysihuniks.nclaim.NClaim;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public abstract class MessageType {
    public static final MessageType CONFIRM = new MessageType() {
        @Override
        public void playSound(Player player) {
            player.playSound(Sound.sound()
                    .type(Key.key("block.note_block.chime"))
                    .source(Sound.Source.NEUTRAL)
                    .build());

            Bukkit.getScheduler().runTaskLater(NClaim.inst(),
                    task -> player.playSound(Sound.sound()
                            .type(Key.key("block.note_block.chime"))
                            .source(Sound.Source.NEUTRAL)
                            .pitch(2.0f)
                            .build()),
                    2L);
        }
    };

    public static final MessageType FAIL = new MessageType() {
        @Override
        public void playSound(Player player) {
            player.playSound(Sound.sound()
                    .type(Key.key("entity.enderman.teleport.chime"))
                    .source(Sound.Source.NEUTRAL)
                    .volume(0.5f)
                    .pitch(0.5f)
                    .build());

            Bukkit.getScheduler().runTaskLater(NClaim.inst(),
                    task -> player.playSound(Sound.sound()
                            .type(Key.key("block.anvil.land"))
                            .source(Sound.Source.NEUTRAL)
                            .volume(0.3f)
                            .pitch(0.5f)
                            .build()),
                    2L);
        }
    };

    public static final MessageType WARN = new MessageType() {
        @Override
        public void playSound(Player player) {
            player.playSound(Sound.sound()
                    .type(Key.key("block.note_block.bass"))
                    .source(Sound.Source.NEUTRAL)
                    .pitch(0.5f)
                    .build());

            Bukkit.getScheduler().runTaskLater(NClaim.inst(),
                    task -> player.playSound(Sound.sound()
                            .type(Key.key("block.note_block.bass"))
                            .source(Sound.Source.NEUTRAL)
                            .pitch(0.6f)
                            .build()),
                    2L);
        }
    };

    public static final MessageType VALUE_INCREASE = new MessageType() {
        @Override
        public void playSound(Player player) {
            player.playSound(Sound.sound()
                    .type(Key.key("ui.button.click"))
                    .source(Sound.Source.NEUTRAL)
                    .volume(0.4f)
                    .pitch(1.8f)
                    .build());

            player.playSound(Sound.sound()
                    .type(Key.key("block.note_block.pling"))
                    .source(Sound.Source.NEUTRAL)
                    .volume(0.3f)
                    .pitch(1.6f)
                    .build());
        }
    };

    public static final MessageType VALUE_DECREASE = new MessageType() {
        @Override
        public void playSound(Player player) {
            player.playSound(Sound.sound()
                    .type(Key.key("ui.button.click"))
                    .source(Sound.Source.NEUTRAL)
                    .volume(0.4f)
                    .pitch(1.4f)
                    .build());

            player.playSound(Sound.sound()
                    .type(Key.key("block.note_block.pling"))
                    .source(Sound.Source.NEUTRAL)
                    .volume(0.3f)
                    .pitch(0.8f)
                    .build());
        }
    };


    public static final MessageType MENU_REFRESH = new MessageType() {
        @Override
        public void playSound(Player player) {
            player.playSound(Sound.sound()
                    .type(Key.key("block.piston.contract"))
                    .source(Sound.Source.NEUTRAL)
                    .volume(0.3f)
                    .pitch(1.5f)
                    .build());

            Bukkit.getScheduler().runTaskLater(NClaim.inst(),
                    task -> player.playSound(Sound.sound()
                            .type(Key.key("block.metal_pressure_plate.click_on"))
                            .source(Sound.Source.NEUTRAL)
                            .volume(0.4f)
                            .pitch(1.6f)
                            .build()),
                    1L);

            Bukkit.getScheduler().runTaskLater(NClaim.inst(),
                    task -> player.playSound(Sound.sound()
                            .type(Key.key("block.iron_trapdoor.close"))
                            .source(Sound.Source.NEUTRAL)
                            .volume(0.3f)
                            .pitch(2.0f)
                            .build()),
                    3L);
        }
    };


    public static final MessageType SEARCH_OPEN = new MessageType() {
        @Override
        public void playSound(Player player) {
            player.playSound(Sound.sound()
                    .type(Key.key("ui.cartography_table.take_result"))
                    .source(Sound.Source.UI)
                    .volume(0.6f)
                    .pitch(1.2f)
                    .build());

            Bukkit.getScheduler().runTaskLater(NClaim.inst(),
                    task -> player.playSound(Sound.sound()
                            .type(Key.key("item.book.page_turn"))
                            .source(Sound.Source.NEUTRAL)
                            .volume(0.4f)
                            .pitch( 1.5f)
                            .build()),
                    1L);

            Bukkit.getScheduler().runTaskLater(NClaim.inst(),
                    task -> player.playSound(Sound.sound()
                            .type(Key.key("block.note_block.hat"))
                            .source(Sound.Source.NEUTRAL)
                            .volume(0.3f)
                            .pitch(1.8f)
                            .build()),
                    3L);
        }
    };


    public static final MessageType MENU_BACK = new MessageType() {
        @Override
        public void playSound(Player player) {
            player.playSound(Sound.sound()
                    .type(Key.key("item.bundle.drop_contents"))
                    .source(Sound.Source.NEUTRAL)
                    .volume( 0.5f)
                    .pitch(1.2f)
                    .build());

            Bukkit.getScheduler().runTaskLater(NClaim.inst(),
                    task -> player.playSound(Sound.sound()
                            .type(Key.key("block.note_block.hat"))
                            .source(Sound.Source.NEUTRAL)
                            .volume(0.3f)
                            .pitch(0.8f)
                            .build()),
                    2L);
        }
    };

    public static final MessageType MENU_FORWARD = new MessageType() {
        @Override
        public void playSound(Player player) {
            player.playSound(Sound.sound()
                    .type(Key.key("block.enchantment_table.use"))
                    .source(Sound.Source.NEUTRAL)
                    .volume(0.5f)
                    .pitch(1.5f)
                    .build());

            Bukkit.getScheduler().runTaskLater(NClaim.inst(),
                    task -> player.playSound(Sound.sound()
                            .type(Key.key("entity.experience_orb.pickup"))
                            .source(Sound.Source.NEUTRAL)
                            .volume(0.3f)
                            .pitch(1.8f)
                            .build()),
                    2L);
        }
    };

    public static final MessageType MENU_SELECT = new MessageType() {
        @Override
        public void playSound(Player player) {
            player.playSound(Sound.sound()
                    .type(Key.key("ui.button.click"))
                    .source(Sound.Source.UI)
                    .volume(0.7f)
                    .pitch(1.2f)
                    .build());

            Bukkit.getScheduler().runTaskLater(NClaim.inst(),
                    task -> player.playSound(Sound.sound()
                            .type(Key.key("entity.player.levelup"))
                            .source(Sound.Source.NEUTRAL)
                            .volume(0.4f)
                            .pitch(2.0f)
                            .build()),
                    2L);

            Bukkit.getScheduler().runTaskLater(NClaim.inst(),
                    task -> player.playSound(Sound.sound()
                            .type(Key.key("block.note_block.bell"))
                            .source(Sound.Source.NEUTRAL)
                            .volume(0.3f)
                            .pitch(1.8f)
                            .build()),
                    3L);
        }
    };

    public static final MessageType MENU_DESELECT = new MessageType() {
        @Override
        public void playSound(Player player) {
            player.playSound(Sound.sound()
                    .type(Key.key("ui.button.click"))
                    .source(Sound.Source.UI)
                    .volume(0.7f)
                    .pitch(0.8f)
                    .build());

            Bukkit.getScheduler().runTaskLater(NClaim.inst(),
                    task -> player.playSound(Sound.sound()
                            .type(Key.key("block.wooden_button.click_off"))
                            .source(Sound.Source.NEUTRAL)
                            .volume(0.4f)
                            .pitch(0.7f)
                            .build()),
                    2L);

            Bukkit.getScheduler().runTaskLater(NClaim.inst(),
                    task -> player.playSound(Sound.sound()
                            .type(Key.key("block.note_block.hat"))
                            .source(Sound.Source.NEUTRAL)
                            .volume(0.3f)
                            .pitch(0.6f)
                            .build()),
                    1L);
        }
    };

    public static final MessageType TELEPORT = new MessageType() {
        @Override
        public void playSound(Player player) {
            player.playSound(Sound.sound()
                    .type(Key.key("block.enchantment_table.use"))
                    .source(Sound.Source.UI)
                    .volume(0.7f)
                    .pitch(1.0f)
                    .build());

            player.playSound(Sound.sound()
                    .type(Key.key("block.beacon.ambient"))
                    .source(Sound.Source.UI)
                    .volume(0.4f)
                    .pitch(2.0f)
                    .build());

            Bukkit.getScheduler().runTaskLater(NClaim.inst(),
                    task -> player.playSound(Sound.sound()
                            .type(Key.key("entity.experience_orb.pickup"))
                            .source(Sound.Source.NEUTRAL)
                            .volume(0.5f)
                            .pitch(1.8f)
                            .build()),
                    2L);

            Bukkit.getScheduler().runTaskLater(NClaim.inst(),
                    task -> player.playSound(Sound.sound()
                            .type(Key.key("block.amethyst_block.chime"))
                            .source(Sound.Source.NEUTRAL)
                            .volume(0.3f)
                            .pitch(1.5f)
                            .build()),
                    4L);
        }
    };

    public static MessageType silent() {
        return new MessageType() {
            @Override
            public void playSound(Player player) {}
        };
    }

    public abstract void playSound(Player player);
}