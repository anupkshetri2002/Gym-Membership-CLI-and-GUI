package gym.service;

import gym.exception.InvalidInputException;
import gym.model.EvaluationOutcome;
import gym.model.GymMember;
import gym.model.PremiumMember;
import gym.model.RegularMember;
import gym.model.transaction.PenaltyTransaction;
import gym.model.transaction.RewardTransaction;
import gym.model.transaction.Transaction;
import gym.repository.MemberRepository;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * PERIODIC EVALUATION ENGINE -- the "monitor status, trigger positive and
 * negative feedback events, execute reward or penalty actions" requirement.
 *
 * Runs in three clearly separated phases:
 *
 *   1. GRADE   every member grades itself via evaluate(). The rules differ per
 *              member type, but this loop does not know that.
 *   2. QUEUE   the outcomes are turned into Transaction objects and pushed onto
 *              a LinkedList used as a FIFO Queue.
 *   3. APPLY   the queue is drained head-first and each transaction applies
 *              itself. One loop, three behaviours.
 *
 * COLLECTION CHOICE: LinkedList as a Queue. Draining means repeatedly removing
 * from the front, which is O(1) for a LinkedList and O(n) for an ArrayList
 * (ArrayList.remove(0) shifts every remaining element). Keeping the batch as a
 * queue also makes the order of application explicit and auditable, which
 * matters when a penalty can suspend a membership that a later transaction
 * would otherwise touch.
 */
public class EvaluationService {

    /** How many cycles' worth of points earns the free-month bonus. */
    private static final double LOYALTY_BONUS_THRESHOLD = 500.0;

    private final MemberRepository repository;
    private final ActivityLog log;
    private final List<Transaction> ledger;
    private int cycleNumber = 0;

    public EvaluationService(MemberRepository repository, ActivityLog log, List<Transaction> ledger) {
        this.repository = repository;
        this.log = log;
        this.ledger = ledger;
    }

    /** One full evaluation cycle. Returns a printable report, line by line. */
    public List<String> runCycle() {
        cycleNumber++;
        List<String> report = new ArrayList<String>();
        List<Transaction> applied = new ArrayList<Transaction>();

        report.add("");
        report.add("=============================================================");
        report.add("  PERIODIC EVALUATION -- CYCLE " + cycleNumber);
        report.add("=============================================================");

        if (repository.size() == 0) {
            report.add("  No members to evaluate.");
            return report;
        }

        int rewards = 0;
        int penalties = 0;

        for (GymMember member : repository.getAll()) {

            // ---- PHASE 1: grade (polymorphic -- regular and premium differ)
            EvaluationOutcome outcome = member.evaluate();

            // ---- PHASE 2: build the transaction queue for this member
            Queue<Transaction> batch = buildBatch(member, outcome);

            double pointsBefore = member.getLoyaltyPoints();
            boolean wasActive = member.isActive();

            report.add("");
            report.add(String.format("  #%-5d %-24s %-8s  %s",
                    member.getId(), member.getName(), member.getMemberType(),
                    outcome.getStatus().getLabel().toUpperCase()));
            report.add("        " + outcome.getMessage());

            // ---- PHASE 3: drain the queue, applying each transaction
            Transaction t;
            while ((t = batch.poll()) != null) {
                try {
                    t.applyTo(member);
                    ledger.add(t);
                    applied.add(t);
                    report.add("        -> " + t.getType() + ": " + t.describeEffect());
                    if ("REWARD".equals(t.getType())) {
                        rewards++;
                    } else if ("PENALTY".equals(t.getType())) {
                        penalties++;
                    }
                } catch (InvalidInputException e) {
                    report.add("        -> SKIPPED (" + e.getMessage() + ")");
                }
            }

            if (batchWasEmpty(applied, member)) {
                report.add("        -> no action taken");
            }

            report.add(String.format("        points %.1f -> %.1f%s",
                    pointsBefore, member.getLoyaltyPoints(),
                    wasActive && !member.isActive() ? "   [membership suspended]" : ""));

            member.startNewCycle();
        }

        report.add("");
        report.add("-------------------------------------------------------------");
        report.add(String.format("  %d member(s) evaluated | %d reward(s) | %d penalty(ies)",
                repository.size(), rewards, penalties));
        report.add("  Attendance counters reset for the next cycle.");
        report.add("-------------------------------------------------------------");

        log.push("Evaluation cycle " + cycleNumber + " completed: "
                + rewards + " rewards, " + penalties + " penalties");
        return report;
    }

    /**
     * Turn one outcome into zero or more transactions.
     * A LinkedList is used here specifically for its O(1) head removal.
     */
    private Queue<Transaction> buildBatch(GymMember member, EvaluationOutcome outcome) {
        Queue<Transaction> batch = new LinkedList<Transaction>();
        double delta = outcome.getPointsDelta();

        if (outcome.getStatus().isPositive() && delta > 0) {
            boolean unlocksUpgrade = member instanceof RegularMember
                    && member.getAttendance() >= RegularMember.ATTENDANCE_LIMIT;
            batch.add(new RewardTransaction(member.getId(), member.getName(),
                    "Attendance reward: " + outcome.getMessage(), delta, unlocksUpgrade));
        }

        if (outcome.getStatus().isNegative() && delta < 0) {
            boolean suspend = outcome.getStatus() == EvaluationOutcome.Status.INACTIVE
                    && member.isActive();
            batch.add(new PenaltyTransaction(member.getId(), member.getName(),
                    "Attendance penalty: " + outcome.getMessage(), delta, suspend));
        }

        // Extra negative event, independent of attendance: an unpaid balance.
        if (member instanceof PremiumMember) {
            PremiumMember premium = (PremiumMember) member;
            if (!premium.isFullPayment() && premium.getMonthlyDue() > 0.0
                    && outcome.getStatus() != EvaluationOutcome.Status.AT_RISK) {
                batch.add(new PenaltyTransaction(member.getId(), member.getName(),
                        String.format("Outstanding balance Rs. %.2f", premium.getMonthlyDue()),
                        -10.0, false));
            }
        }

        // Extra positive event: long-term loyalty milestone.
        if (member.getLoyaltyPoints() >= LOYALTY_BONUS_THRESHOLD) {
            batch.add(new RewardTransaction(member.getId(), member.getName(),
                    "Loyalty milestone reached (free month credited)", 100.0, false));
        }

        return batch;
    }

    private boolean batchWasEmpty(List<Transaction> applied, GymMember member) {
        for (int i = applied.size() - 1; i >= 0; i--) {
            if (applied.get(i).getMemberId() == member.getId()) {
                return false;
            }
        }
        return true;
    }

    public int getCycleNumber() {
        return cycleNumber;
    }
}
