package nesoi.aysihuniks.nclaim.service;

import io.papermc.paper.util.Tick;
import lombok.RequiredArgsConstructor;
import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.enums.Direction;
import nesoi.aysihuniks.nclaim.model.Claim;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
public class ClaimVisualizerService {
    private final NClaim plugin;
    private final Map<UUID, BukkitTask> activeVisualizations = new HashMap<>();
    private final Set<UUID> playersInPreviewMode = new HashSet<>(); 

    private static final int VISUALIZE_HALF_HEIGHT = 24;
    private static final int VISUALIZE_STEP = 4;

    // TODO: Reuse particles

    public void showClaimBorders(Player player, Claim claim) {
        AtomicInteger counter = new AtomicInteger(0);
//        if (!player.isOnline() || !player.getWorld().equals(claim.getChunk().getWorld()) || counter.get() >= 40) { // 20s
//            cancelVisualization(player);
//            return;
//        }
//        for (Map.Entry<Chunk, Collection<Direction>> edgeEntry : claim.getEdgeChunkDirections().entrySet()) {
//            Collection<Direction> edgeDirections = edgeEntry.getValue();
//            for (Direction direction : edgeDirections) {
//                visualizeChunkBorder(player, edgeEntry.getKey(), direction);
//            }
//
//            if (edgeDirections.contains(Direction.NORTH) && edgeDirections.contains(Direction.EAST))
//                visualizeChunkCorner(player, edgeEntry.getKey(), Direction.NORTH_EAST);
//            if (edgeDirections.contains(Direction.NORTH) && edgeDirections.contains(Direction.WEST))
//                visualizeChunkCorner(player, edgeEntry.getKey(), Direction.NORTH_WEST);
//            if (edgeDirections.contains(Direction.SOUTH) && edgeDirections.contains(Direction.EAST))
//                visualizeChunkCorner(player, edgeEntry.getKey(), Direction.SOUTH_EAST);
//            if (edgeDirections.contains(Direction.SOUTH) && edgeDirections.contains(Direction.WEST))
//                visualizeChunkCorner(player, edgeEntry.getKey(), Direction.SOUTH_WEST);
//        }
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !player.getWorld().equals(claim.getChunk().getWorld()) || counter.get() >= 40) { // 20s
                cancelVisualization(player);
                return;
            }
            for (Map.Entry<Chunk, Collection<Direction>> edgeEntry : claim.getEdgeChunkDirections().entrySet()) {
                Collection<Direction> edgeDirections = edgeEntry.getValue();
                for (Direction direction : edgeDirections) {
                    visualizeChunkBorder(player, edgeEntry.getKey(), direction);
                }

                if (edgeDirections.contains(Direction.NORTH) && edgeDirections.contains(Direction.EAST))
                    visualizeChunkCorner(player, edgeEntry.getKey(), Direction.NORTH_EAST);
                if (edgeDirections.contains(Direction.NORTH) && edgeDirections.contains(Direction.WEST))
                    visualizeChunkCorner(player, edgeEntry.getKey(), Direction.NORTH_WEST);
                if (edgeDirections.contains(Direction.SOUTH) && edgeDirections.contains(Direction.EAST))
                    visualizeChunkCorner(player, edgeEntry.getKey(), Direction.SOUTH_EAST);
                if (edgeDirections.contains(Direction.SOUTH) && edgeDirections.contains(Direction.WEST))
                    visualizeChunkCorner(player, edgeEntry.getKey(), Direction.SOUTH_WEST);
            }


            counter.incrementAndGet();
        }, 0L, Tick.tick().fromDuration(Duration.ofMillis(500)));

        activeVisualizations.put(player.getUniqueId(), task);

    }

    public void showChunkBorders(Player player, Chunk chunk) {
        cancelVisualization(player);
        playersInPreviewMode.add(player.getUniqueId());

        AtomicInteger counter = new AtomicInteger(0);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !player.getWorld().equals(chunk.getWorld()) || counter.get() >= 40) {
                cancelVisualization(player);
                return;
            }

            visualizeChunkBorders(player, chunk);
            counter.incrementAndGet();
        }, 0L, Tick.tick().fromDuration(Duration.ofMillis(500)));

        activeVisualizations.put(player.getUniqueId(), task);
    }

    public void showChunkBorders(Player player) {
        cancelVisualization(player);
        playersInPreviewMode.add(player.getUniqueId());

        AtomicInteger counter = new AtomicInteger(0);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || counter.get() >= 10) {
                cancelVisualization(player);
                return;
            }
            Chunk currentChunk = player.getLocation().getChunk();
            visualizeChunkBorders(player, currentChunk);
            counter.incrementAndGet();
        }, 0L, 20L);

        activeVisualizations.put(player.getUniqueId(), task);
    }

    public void cancelVisualization(Player player) {
        BukkitTask task = activeVisualizations.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        playersInPreviewMode.remove(player.getUniqueId());
    }

    private void visualizeChunkBorder(Player player, Chunk chunk, Direction direction) {
        if (direction.z() != 0 && direction.x() != 0) // No ordinal direction has a zero in their vector
            throw new IllegalArgumentException("Cannot visualize non-cardinal direction: " + direction);

        World world = chunk.getWorld();
        int chunkX = chunk.getX() * 16;
        int chunkZ = chunk.getZ() * 16;
        double playerY = player.getEyeLocation().getY();

        int startOffsetX = 8 * (1 + direction.x() - direction.z());
        int startOffsetZ = 8 * (1 - direction.x() + direction.z());
        int endOffset = 8 * (1 + direction.x() + direction.z());

        Location startLocation = new Location(world, chunkX + startOffsetX, 0, chunkZ + startOffsetZ);
        Location endLocation = new Location(world, chunkX + endOffset, 0, chunkZ + endOffset);

        // Horizontal lines
        int nearestStep = ((int) (playerY / VISUALIZE_STEP)) * VISUALIZE_STEP; // Clamp to nearest step
        for (double y = nearestStep - VISUALIZE_HALF_HEIGHT; y <= nearestStep + VISUALIZE_HALF_HEIGHT; y += VISUALIZE_STEP) {
            startLocation.setY(y);
            endLocation.setY(y);
            spawnParticleLine(player, startLocation, endLocation);
        }
    }

    private void visualizeChunkCorner(Player player, Chunk chunk, Direction direction) {
        if (direction.z() == 0 || direction.x() == 0) // Only cardinal directions have 0 for one of their vectors
            throw new IllegalArgumentException("Cannot visualize non-corner direction: " + direction);

        World world = chunk.getWorld();
        int chunkX = chunk.getX() * 16;
        int chunkZ = chunk.getZ() * 16;
        double playerY = player.getEyeLocation().getY();

        int offsetX = direction.x() == 1 ? 16 : 0;
        int offsetZ = direction.z() == 1 ? 16 : 0;
        int nearestStep = ((int) (playerY / VISUALIZE_STEP)) * VISUALIZE_STEP; // Clamp to nearest step

        Location startLocation = new Location(world, chunkX + offsetX, nearestStep - VISUALIZE_HALF_HEIGHT, chunkZ + offsetZ);
        Location endLocation = new Location(world, chunkX + offsetX, nearestStep + VISUALIZE_HALF_HEIGHT, chunkZ + offsetZ);

        spawnParticleLine(player, startLocation, endLocation);
    }

    private void spawnParticleLine(Player player, Location startLocation, Location endLocation) {
//        Plugin plugin = NClaim.inst();
//        double distance = startLocation.distance(endLocation);
//        //Vector direction = endLocation.toVector().subtract(startLocation.toVector()).normalize();
//        Location middle = startLocation.clone().toVector().midpoint(endLocation.toVector()).toLocation(player.getWorld());
//        middle.setDirection(direction.vector());
//        BlockDisplay textDisplay = player.getWorld().spawn(startLocation.clone().setDirection(direction.vector()), BlockDisplay.class, entity -> {
//            GlassPane glassPane = (GlassPane) Material.BLUE_STAINED_GLASS_PANE.createBlockData();
////            glassPane.setFace(BlockFace.EAST, true);
////            glassPane.setFace(BlockFace.WEST, true);
//            entity.setBlock(glassPane);
//            //entity.setGlowing(true);
//            entity.setVisibleByDefault(false);
//            entity.setPersistent(false);
//            entity.setTransformationMatrix(
//                    new Matrix4f()
//                            .translation(1f, -0.5f, -0.5f)
//                            .rotateAround(
//                                    new Quaternionf().rotateZ((float) Math.toRadians(90)),
//                                    -0.5f, -0.5f, -0.5f
//                            )
//                            //.translation(-0.5f, -0.5f, -0.5f)
//                            .scale(new Vector3f(1f, (float) distance, 1f)));
////                    new Transformation(
////                            new Vector3f(),
////                            new AxisAngle4f(),
////                            new Vector3f(1f, 2f, 1f),
////                            new AxisAngle4f((float) Math.toRadians(90), 1, 0, 0)
////                    )
////            );
//        });
//
//        player.showEntity(plugin, textDisplay);



        double distance = startLocation.distance(endLocation);
        Vector particleDirection = endLocation.toVector().subtract(startLocation.toVector()).normalize();

        for (double d = 0; d <= distance; d += 0.5) {
            Location particleLoc = startLocation.clone().add(particleDirection.clone().multiply(d));
            Particle.END_ROD.builder()
                    .offset(0, 0.005, 0) // Velocity
                    .extra(1) // Velocity scale
                    .receivers(player)
                    .location(particleLoc)
                    .count(0)
                    .force(true)
                    .spawn();
        }
    }

    private void visualizeChunkBorders(Player player, Chunk chunk) {
        visualizeChunkBorder(player, chunk, Direction.NORTH);
        visualizeChunkBorder(player, chunk, Direction.SOUTH);
        visualizeChunkBorder(player, chunk, Direction.EAST);
        visualizeChunkBorder(player, chunk, Direction.WEST);

        visualizeChunkCorner(player, chunk, Direction.NORTH_WEST);
        visualizeChunkCorner(player, chunk, Direction.NORTH_EAST);
        visualizeChunkCorner(player, chunk, Direction.SOUTH_WEST);
        visualizeChunkCorner(player, chunk, Direction.SOUTH_EAST);
    }
}