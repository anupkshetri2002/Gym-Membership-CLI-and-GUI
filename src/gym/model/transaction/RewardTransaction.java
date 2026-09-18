package gym.model.transaction;

import gym.model.GymMember;
import gym.model.RegularMember;

/**
 * POSITIVE feedback event. Credits loyalty points and, for a regular member
 * who has hit the attendance limit, unlocks the plan upgrade.
 */
public class RewardTransaction extends Transaction {

    private final boolean unlocksUpgrade;

    public RewardTransaction(int memberId, String memberName, String description,
                             double points, boolean unlocksUpgrade) {
        super(memberId, memberName, description, points);
        this.unlocksUpgrade = unlocksUpgrade;
    }

    @Override
    public String getType() {
        return "REWARD";
    }

    @Override
    public void applyTo(GymMember member) {
        member.adjustLoyaltyPoints(getAmount());
        if (unlocksUpgrade && member instanceof RegularMember) {
            ((RegularMember) member).setEligibleForUpgrade(true);
        }
    }

    @Override
    public String describeEffect() {
        String effect = String.format("+%.0f loyalty points", getAmount());
        if (unlocksUpgrade) {
            effect += ", plan upgrade unlocked";
        }
        return effect;
    }
}
