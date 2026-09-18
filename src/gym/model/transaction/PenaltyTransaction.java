package gym.model.transaction;

import gym.model.GymMember;

/**
 * NEGATIVE feedback event. Deducts loyalty points and, in the worst case,
 * suspends the membership outright.
 */
public class PenaltyTransaction extends Transaction {

    private final boolean suspendMembership;

    public PenaltyTransaction(int memberId, String memberName, String description,
                              double pointsLost, boolean suspendMembership) {
        super(memberId, memberName, description, pointsLost);
        this.suspendMembership = suspendMembership;
    }

    @Override
    public String getType() {
        return "PENALTY";
    }

    @Override
    public void applyTo(GymMember member) {
        member.adjustLoyaltyPoints(-Math.abs(getAmount()));
        if (suspendMembership) {
            member.deactivateMembership();
        }
    }

    @Override
    public String describeEffect() {
        String effect = String.format("-%.0f loyalty points", Math.abs(getAmount()));
        if (suspendMembership) {
            effect += ", membership SUSPENDED";
        }
        return effect;
    }
}
