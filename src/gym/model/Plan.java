package gym.model;

import gym.exception.InvalidInputException;

/**
 * The three plans a RegularMember can hold. Using an enum instead of a String
 * means an invalid plan name cannot exist at runtime, and the ordinal gives us
 * a free ranking for upgrade/downgrade checks.
 */
public enum Plan {

    BASIC("Basic", 6500.0),
    STANDARD("Standard", 12500.0),
    DELUXE("Deluxe", 18500.0);

    private final String label;
    private final double price;

    Plan(String label, double price) {
        this.label = label;
        this.price = price;
    }

    public String getLabel() {
        return label;
    }

    public double getPrice() {
        return price;
    }

    /** Higher rank == better plan. Used to reject downgrades. */
    public int getRank() {
        return ordinal();
    }

    /** Case-insensitive parse that fails loudly instead of returning null. */
    public static Plan parse(String text) throws InvalidInputException {
        if (text == null) {
            throw new InvalidInputException("Plan name cannot be empty.");
        }
        for (Plan p : values()) {
            if (p.name().equalsIgnoreCase(text.trim())) {
                return p;
            }
        }
        throw new InvalidInputException("Unknown plan '" + text + "'. Valid plans: BASIC, STANDARD, DELUXE.");
    }

    @Override
    public String toString() {
        return label;
    }
}
