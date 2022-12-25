package com.lauriethefish.betterportals.bukkit.portal.spawning;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.lauriethefish.betterportals.api.PortalDirection;
import com.lauriethefish.betterportals.bukkit.config.PortalSpawnConfig;
import com.lauriethefish.betterportals.bukkit.config.WorldLink;
import com.lauriethefish.betterportals.bukkit.portal.blend.IDimensionBlendManager;
import com.lauriethefish.betterportals.bukkit.util.MaterialUtil;
import com.lauriethefish.betterportals.shared.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.BlockState;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@Singleton
public class PortalSpawner implements IPortalSpawner {
    // How many blocks to force portals to spawn inside the border
    private static final double BORDER_PADDING = 10.0;

    private final JavaPlugin pl;
    private final PortalSpawnConfig spawnConfig;
    private final Logger logger;

    private final ExistingPortalChecker existingPortalChecker;
    private final NewPortalChecker newPortalChecker;
    private final IDimensionBlendManager dimensionBlendManager;

    @Inject
    public PortalSpawner(JavaPlugin pl, PortalSpawnConfig spawnConfig, Logger logger, ExistingPortalChecker existingPortalChecker, NewPortalChecker newPortalChecker, IDimensionBlendManager dimensionBlendManager) {
        this.pl = pl;
        this.spawnConfig = spawnConfig;
        this.logger = logger;
        this.existingPortalChecker = existingPortalChecker;
        this.newPortalChecker = newPortalChecker;
        this.dimensionBlendManager = dimensionBlendManager;
    }

    @Override
    public boolean findAndSpawnDestination(@NotNull Location originPosition, @NotNull Vector originSize, Consumer<PortalSpawnPosition> onFinish) {
        World originWorld = originPosition.getWorld();
        assert originWorld != null;

        WorldLink link = spawnConfig.getWorldLink(originWorld);
        if(link == null) {
            logger.fine("Unable to find world link for lit portal at origin position %s with size %s", originPosition, originSize);
            return false;
        }

        Location rawDestPos = link.moveFromOriginWorld(originPosition);
        Location destinationPosition = limitByWorldBorder(rawDestPos);
        logger.fine("Preferred destination position: %s", destinationPosition.toVector());

        PortalSpawningContext context = new PortalSpawningContext(link, destinationPosition, originSize);
        logger.fine("Searching for existing position");
        startAsyncCheck(context, existingPortalChecker, (existingPosition) -> {
            if(existingPosition != null) {
                logger.fine("Creating with existing position");
                if(spawnPortal(existingPosition, originPosition)) {
                    onFinish.accept(existingPosition);
                    return;
                }
                logger.fine("Existing position is obstructed, invalidating");
            }
            logger.fine("Searching for new position");
            // Find a new spawn position if there aren't any valid existing ones
            startAsyncCheck(context, newPortalChecker, (newSpawnPos) -> {
                // Give up and just use the preferred destination position, which is probably in a wall, but it's our only real option
                if(newSpawnPos == null) {
                    logger.warning("Unable to find destination for a portal. This shouldn't happen really");
                    newSpawnPos = new PortalSpawnPosition(destinationPosition, originSize, PortalDirection.EAST);
                }

                logger.fine("Creating with new position");
                spawnPortal(newSpawnPos, originPosition);
                onFinish.accept(newSpawnPos);
            });
        });

        return true;
    }

    // Portal spawn checks are done over a number of ticks to avoid slowing down the server
    private void startAsyncCheck(PortalSpawningContext context, IChunkChecker chunkChecker, Consumer<PortalSpawnPosition> onFinish) {
        new AsyncPortalChecker(context, chunkChecker, onFinish, pl, logger, spawnConfig);
    }

    /**
     * Moves this location to be {@link PortalSpawner#BORDER_PADDING} blocks inside the world border.
     * @param location The location to be limited
     * @return The location inside the world border
     */
    private Location limitByWorldBorder(Location location) {
        WorldBorder border = Objects.requireNonNull(location.getWorld()).getWorldBorder();
        // We need the radius, not the diameter
        double paddedSize = Math.max(border.getSize() / 2 - BORDER_PADDING, 1);
        logger.finer("Padded size %s", paddedSize);

        Location borderRelativePos = location.clone().subtract(border.getCenter());
        borderRelativePos.setX(Math.min(paddedSize, borderRelativePos.getX()));
        borderRelativePos.setX(Math.max(-paddedSize, borderRelativePos.getX()));
        borderRelativePos.setZ(Math.min(paddedSize, borderRelativePos.getZ()));
        borderRelativePos.setZ(Math.max(-paddedSize, borderRelativePos.getZ()));

        borderRelativePos.add(border.getCenter());
        return borderRelativePos;
    }

    /**
     * Spawns a portal at the specified spawn position, with the 4 corners filled and the portal blocks.
     * <br>Will also perform the dimension blend if it's enabled.
     * @param position Position to spawn the portal at
     * @return whether portal was spawned
     */
    @SuppressWarnings("deprecation")
    private boolean spawnPortal(PortalSpawnPosition position, Location originPos) {
        if(spawnConfig.isDimensionBlendEnabled()) {
            dimensionBlendManager.performBlend(originPos.clone().add(position.getSize().clone().multiply(0.5)), position.getPosition());
        }

        Vector size = position.getSize().clone().add(new Vector(1.0, 1.0, 0.0));
        PortalDirection direction = position.getDirection();

        List<BlockState> statesToUpdate = new ArrayList<>();
        for(int x = 0; x <= size.getX(); x++) {
            for(int y = 0; y <= size.getY(); y++) {
                Vector frameRelativePos = new Vector(x, y, 0.0);

                Location blockPos = position.getPosition().clone().add(position.getDirection().swapVector(frameRelativePos));
                boolean isFrameBlock = x == 0 || x == size.getX() || y == 0 || y == size.getY();

                // This is done with a BlockState to avoid updating physics, since otherwise our portal blocks would get removed during creation
                BlockState state = blockPos.getBlock().getState();
                Material currentFrame = state.getType();

                if(isFrameBlock) {
                    // Don't replace non-air corner blocks
                    if(((x == 0 && y == 0) || (x == size.getX() && y == 0) || (x == 0 && y == size.getY()) || (x == size.getX() && y == size.getY())) && !MaterialUtil.isAir(currentFrame)) {
                        continue;
                    }
                    // Don't replace valid portal frames
                    if(spawnConfig.isPortalFrame(currentFrame)) {
                        continue;
                    }
                    // Something's changed in the old frame, invalidate
                    if(!spawnConfig.isReplaceable(currentFrame)) {
                        return false;
                    }
                    state.setType(Material.OBSIDIAN);
                } else {
                    // Something's blocking inner portal space, invalidate
                    if(!MaterialUtil.isAir(currentFrame) && currentFrame != MaterialUtil.PORTAL_MATERIAL && !spawnConfig.isReplaceable(currentFrame)) {
                        return false;
                    }
                    state.setType(MaterialUtil.PORTAL_MATERIAL);
                    // Make sure to rotate the portal blocks for NORTH/SOUTH portals
                    if(direction == PortalDirection.EAST || direction == PortalDirection.WEST) {
                        state.setRawData((byte) 2);
                    }
                }

                statesToUpdate.add(state);
            }
        }
        statesToUpdate.forEach(state -> state.update(true, false));
        return true;
    }
}
