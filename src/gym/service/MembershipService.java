package gym.service;

import gym.exception.InvalidInputException;
import gym.exception.MemberNotFoundException;
import gym.model.GymMember;
import gym.model.Plan;
import gym.model.PremiumMember;
import gym.model.RegularMember;
import gym.model.transaction.PaymentTransaction;
import gym.model.transaction.Transaction;
import gym.repository.MemberRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Day-to-day operations on an existing member: attendance, plan upgrades and
 * payments. Knows nothing about the console and nothing about files -- it is
 * pure business logic sitting between the UI and the repository.
 */
public class MembershipService {

    private final MemberRepository repository;
    private final ActivityLog log;
    private final List<Transaction> ledger;

    public MembershipService(MemberRepository repository, ActivityLog log, List<Transaction> ledger) {
        this.repository = repository;
        this.log = log;
        this.ledger = ledger;
    }

    /**
     * POLYMORPHISM IN ONE LINE: the reference is a GymMember, so the call site
     * has no idea whether 5 or 10 loyalty points are about to be awarded.
     */
    public GymMember markAttendance(int id) throws MemberNotFoundException, InvalidInputException {
        GymMember member = repository.findById(id);
        if (!member.isActive()) {
            throw new InvalidInputException("Member " + id + " is inactive -- reactivate before marking attendance.");
        }
        member.markAttendance();
        log.push("Attendance marked for #" + id + " (" + member.getName() + ")");
        return member;
    }

    /** Mark attendance for several members in one go. */
    public List<String> markAttendanceBatch(List<Integer> ids) {
        List<String> results = new ArrayList<String>();
        for (Integer id : ids) {
            try {
                GymMember m = markAttendance(id);
                results.add("OK   #" + id + " " + m.getName()
                        + " -> " + m.getAttendance() + " sessions, "
                        + String.format("%.1f", m.getLoyaltyPoints()) + " points");
            } catch (MemberNotFoundException | InvalidInputException e) {
                results.add("FAIL #" + id + " " + e.getMessage());
            }
        }
        return results;
    }

    public void upgradePlan(int id, Plan target) throws MemberNotFoundException, InvalidInputException {
        GymMember member = repository.findById(id);
        if (!(member instanceof RegularMember)) {
            throw new InvalidInputException("Member " + id + " is a premium member and has no plan to upgrade.");
        }
        RegularMember regular = (RegularMember) member;
        Plan previous = regular.getPlan();
        regular.upgradePlan(target);
        log.push("Plan upgrade #" + id + ": " + previous.getLabel() + " -> " + target.getLabel());
    }

    public void recordPayment(int id, double amount) throws MemberNotFoundException, InvalidInputException {
        GymMember member = repository.findById(id);
        PaymentTransaction payment = new PaymentTransaction(
                member.getId(), member.getName(),
                member.getMemberType() + " payment received", amount);

        payment.applyTo(member);      // polymorphic: premium pays down the balance
        ledger.add(payment);
        log.push(String.format("Payment of Rs. %.2f recorded for #%d", amount, id));
    }

    public double applyDiscount(int id) throws MemberNotFoundException, InvalidInputException {
        GymMember member = repository.findById(id);
        if (!(member instanceof PremiumMember)) {
            throw new InvalidInputException("Only premium members earn a discount.");
        }
        PremiumMember premium = (PremiumMember) member;
        double discount = premium.calculateDiscount();
        if (discount == 0.0) {
            throw new InvalidInputException("Member " + id + " has not paid in full, so no discount applies yet.");
        }
        log.push(String.format("Discount of Rs. %.2f confirmed for #%d", discount, id));
        return discount;
    }

    public void setActive(int id, boolean active) throws MemberNotFoundException {
        GymMember member = repository.findById(id);
        if (active) {
            member.activateMembership();
        } else {
            member.deactivateMembership();
        }
        log.push("Member #" + id + " marked " + (active ? "ACTIVE" : "INACTIVE"));
    }

    public void revertMember(int id, String reason) throws MemberNotFoundException, InvalidInputException {
        GymMember member = repository.findById(id);
        member.revertMember(reason);     // polymorphic: refund logic differs by type
        log.push("Member #" + id + " reverted (" + reason + ")");
    }
}
