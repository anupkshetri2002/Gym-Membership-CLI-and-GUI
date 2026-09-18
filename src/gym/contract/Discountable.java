package gym.contract;

/**
 * ABSTRACTION: implemented only by member types that can earn a discount.
 * RegularMember deliberately does NOT implement it, which is why the
 * capability lives in an interface rather than in the GymMember base class.
 */
public interface Discountable {

    /** @return the discount earned, or 0.0 if none has been earned yet. */
    double calculateDiscount();
}
