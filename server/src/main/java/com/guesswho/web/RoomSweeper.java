package com.guesswho.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes rooms whose time is up, on a timer.
 *
 * <p>Expiring lazily, when somebody reads a room, never reaches the rows that
 * actually pile up. A room created and abandoned is one nobody will ever read
 * again — which is exactly the case the expiry exists for, and exactly the case
 * a lazy sweep misses.</p>
 *
 * <p>Rooms already stop working the moment they expire, because every lookup
 * checks the deadline. This only stops the table growing.</p>
 */
@Component
@ConditionalOnProperty(name = "guesswho.rooms.sweep.enabled", matchIfMissing = true)
public class RoomSweeper {
    private static final Logger LOG = LoggerFactory.getLogger(RoomSweeper.class);
    private final RoomService rooms;

    /**
     * @param rooms the rooms to sweep
     */
    public RoomSweeper(RoomService rooms) {
        this.rooms = rooms;
    }

    /**
     * Removes expired rooms and the move keys belonging to them.
     *
     * <p>Failure is logged and nothing else. A sweep that could bring the
     * server down would be a worse problem than the rows it was tidying, and
     * the next run in five minutes will try again.</p>
     */
    //Five minutes: often enough that nothing accumulates, rare enough to
    //cost nothing. As a property rather than a constant so a deployment can
    //change it, and because an annotation cannot hold a computed value.
    @Scheduled(
            fixedDelayString = "${guesswho.rooms.sweep.interval-millis:300000}",
            initialDelayString = "${guesswho.rooms.sweep.interval-millis:300000}")
    public void sweep() {
        try {
            int removed = rooms.sweepExpired();
            if (removed > 0) {
                LOG.info("Removed {} expired room(s)", removed);
            }
        }
        catch (RuntimeException failed) {
            LOG.warn("Could not sweep expired rooms; will try again", failed);
        }
    }
}
