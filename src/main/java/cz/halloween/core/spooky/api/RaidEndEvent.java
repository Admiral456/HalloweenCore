/* Adapted from SpookySeason 1.7.0 by BoondockSulfur; MIT license. See licenses/spookyseason-MIT.txt. */
package cz.halloween.core.spooky.api;

import java.util.Map;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired once when a raid ends by victory, defeat or abort. Synchronous: on Paper on the main
 * thread, on Folia on the global region thread.
 *
 * <p>Plugins without a compile-time dependency on HalloweenCore can read the result through
 * reflection via {@link #toMap()}, which returns plain Java types only.
 */
public class RaidEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final RaidResult result;

    public RaidEndEvent(RaidResult result) {
        super(false);
        this.result = result;
    }

    public RaidResult getResult() {
        return this.result;
    }

    /** The result in the exchange format, see {@link RaidResult#toMap()}. */
    public Map<String, Object> toMap() {
        return this.result.toMap();
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
