package gym.model.transaction;

import gym.contract.CsvSerializable;
import gym.exception.InvalidInputException;
import gym.model.GymMember;
import gym.util.CsvUtil;

import java.time.LocalDate;

/**
 * SECOND ABSTRACT HIERARCHY.
 *
 * A Transaction is one thing that happened to one member: a reward, a penalty
 * or a payment. EvaluationService builds a queue of these and then drains it
 * with a single loop:
 *
 *     while ((t = queue.poll()) != null) t.applyTo(member);
 *
 * One call site, three completely different effects. This is the clearest
 * polymorphism in the system, and it keeps the evaluation rules separate from
 * the code that carries them out.
 */
public abstract class Transaction implements CsvSerializable {

    private final int memberId;
    private final String memberName;
    private final LocalDate date;
    private final String description;
    private final double amount;

    protected Transaction(int memberId, String memberName, String description, double amount) {
        this.memberId = memberId;
        this.memberName = memberName;
        this.date = LocalDate.now();
        this.description = description;
        this.amount = amount;
    }

    /** "REWARD", "PENALTY" or "PAYMENT". */
    public abstract String getType();

    /** Carry out this transaction against the member it belongs to. */
    public abstract void applyTo(GymMember member) throws InvalidInputException;

    /** Human-readable one-liner for the on-screen evaluation report. */
    public abstract String describeEffect();

    public int getMemberId() {
        return memberId;
    }

    public String getMemberName() {
        return memberName;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getDescription() {
        return description;
    }

    public double getAmount() {
        return amount;
    }

    @Override
    public String toCsvRecord() {
        return CsvUtil.join(date, getType(), memberId, memberName,
                String.format("%.2f", amount), description);
    }

    @Override
    public String toString() {
        return String.format("[%s] %-7s #%d %-20s %s",
                date, getType(), memberId, memberName, description);
    }
}
