package gym.model.transaction;

import gym.exception.InvalidInputException;
import gym.model.GymMember;
import gym.model.PremiumMember;

/**
 * A money movement. For a premium member it routes into payDueAmount(); for a
 * regular member it is recorded as a plan fee against the ledger only.
 *
 * Note that this subclass is the one whose applyTo can fail, which is why the
 * base class declares 'throws InvalidInputException' -- the caller draining the
 * queue has to handle it.
 */
public class PaymentTransaction extends Transaction {

    public PaymentTransaction(int memberId, String memberName, String description, double amount) {
        super(memberId, memberName, description, amount);
    }

    @Override
    public String getType() {
        return "PAYMENT";
    }

    @Override
    public void applyTo(GymMember member) throws InvalidInputException {
        if (member instanceof PremiumMember) {
            ((PremiumMember) member).payDueAmount(getAmount());
        }
        // Regular members pay a flat monthly plan fee; nothing on the member
        // object changes, the ledger entry is the record.
    }

    @Override
    public String describeEffect() {
        return String.format("Rs. %.2f received", getAmount());
    }
}
