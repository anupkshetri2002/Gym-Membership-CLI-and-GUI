package gym.contract;

/**
 * ABSTRACTION: anything that knows how to flatten itself into one CSV row.
 * The file-handling layer depends on this interface only, so it never needs
 * to know whether it is writing a RegularMember, a PremiumMember or a
 * Transaction.
 */
public interface CsvSerializable {

    /** @return a single comma-separated line, without a trailing newline. */
    String toCsvRecord();
}
