package io.github.alexanderrebello.chunkanchor.anchor;

import java.util.UUID;

/**
 * A placed chunk anchor.
 * <p>
 * Fuel works like a furnace: inserted fuel items are stored as {@link #storedFuel()} and only
 * start burning one at a time. {@link #burnRemaining()} is the charge left in the item that is
 * currently burning; it is lost when the anchor is broken, stored items are dropped.
 */
public final class Anchor {

    private static final double EPSILON = 1e-9;

    private final BlockKey key;
    private final UUID owner;
    private String ownerName;
    private final String tierId;
    private int storedFuel;
    private double burnRemaining;
    private boolean enabled;

    public Anchor(BlockKey key, UUID owner, String ownerName, String tierId,
                  int storedFuel, double burnRemaining, boolean enabled) {
        this.key = key;
        this.owner = owner;
        this.ownerName = ownerName;
        this.tierId = tierId;
        this.storedFuel = Math.max(0, storedFuel);
        this.burnRemaining = Math.max(0, burnRemaining);
        this.enabled = enabled;
    }

    public BlockKey key() { return key; }
    public UUID owner() { return owner; }
    public String ownerName() { return ownerName; }
    public void ownerName(String name) { this.ownerName = name; }
    public String tierId() { return tierId; }
    public int storedFuel() { return storedFuel; }
    public double burnRemaining() { return burnRemaining; }
    public boolean enabled() { return enabled; }
    public void enabled(boolean enabled) { this.enabled = enabled; }

    public boolean hasCharge() {
        return burnRemaining > EPSILON || storedFuel > 0;
    }

    public boolean isRunning() {
        return enabled && hasCharge();
    }

    /** Total charge: what is left in the burning item plus all stored items. */
    public double charge(double fuelValue) {
        return burnRemaining + storedFuel * fuelValue;
    }

    /** How many more fuel items fit before the capacity is reached. */
    public int freeFuelSlots(double capacity, double fuelValue) {
        double free = capacity - charge(fuelValue);
        return free < fuelValue - EPSILON ? 0 : (int) Math.floor((free + EPSILON) / fuelValue);
    }

    public void addFuel(int amount) {
        storedFuel += Math.max(0, amount);
    }

    /** Removes all stored (not yet burning) fuel and returns how many items that was. */
    public int takeStoredFuel() {
        int taken = storedFuel;
        storedFuel = 0;
        return taken;
    }

    /** Burns the given amount of charge, starting new fuel items as needed. */
    public void consume(double amount, double fuelValue) {
        while (amount > EPSILON) {
            if (burnRemaining >= amount) {
                burnRemaining -= amount;
                break;
            }
            amount -= burnRemaining;
            burnRemaining = 0;
            if (storedFuel <= 0) {
                break;
            }
            storedFuel--;
            burnRemaining = fuelValue;
        }
        if (burnRemaining < EPSILON) {
            burnRemaining = 0;
        }
    }
}
