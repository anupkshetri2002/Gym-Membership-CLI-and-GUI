package gym.model;

import gym.exception.DataFormatException;
import gym.exception.InvalidInputException;
import gym.util.CsvUtil;

import java.time.LocalDate;

/**
 * INHERITANCE: a RegularMember is a GymMember with a plan and an upgrade path.
 *
 * Regular members earn 5 loyalty points per visit and unlock a plan upgrade
 * once they have attended {@value #ATTENDANCE_LIMIT} sessions.
 */
public class RegularMember extends GymMember {

    public static final int ATTENDANCE_LIMIT = 30;
    private static final double POINTS_PER_VISIT = 5.0;

    private Plan plan;
    private boolean eligibleForUpgrade;
    private String referralSource;
    private String removalReason;

    public RegularMember(int id, String name, String location, String phone, String email,
                         String gender, LocalDate dob, LocalDate membershipStartDate,
                         String referralSource) throws InvalidInputException {

        super(id, name, location, phone, email, gender, dob, membershipStartDate);
        this.plan = Plan.BASIC;
        this.eligibleForUpgrade = false;
        this.referralSource = (referralSource == null || referralSource.trim().isEmpty())
                ? "None" : referralSource.trim();
        this.removalReason = "";
    }

    // ------------------------------------------------------- POLYMORPHIC methods

    /** 5 points per visit, and the upgrade flag flips at the attendance limit. */
    @Override
    public void markAttendance() {
        recordVisit(POINTS_PER_VISIT);
        if (getAttendance() >= ATTENDANCE_LIMIT) {
            this.eligibleForUpgrade = true;
        }
    }

    @Override
    public double getMonthlyDue() {
        return plan.getPrice();
    }

    @Override
    public String getMemberType() {
        return "REGULAR";
    }

    @Override
    public void display() {
        System.out.println("------------------------------------------------------------");
        System.out.println("  REGULAR MEMBER");
        System.out.println("------------------------------------------------------------");
        System.out.printf("  ID                 : %d%n", getId());
        System.out.printf("  Name               : %s%n", getName());
        System.out.printf("  Location           : %s%n", getLocation());
        System.out.printf("  Phone / Email      : %s / %s%n", getPhone(), getEmail());
        System.out.printf("  Gender / DOB       : %s / %s%n", getGender(), getDob());
        System.out.printf("  Member since       : %s%n", getMembershipStartDate());
        System.out.printf("  Plan               : %s (Rs. %.2f per month)%n", plan.getLabel(), plan.getPrice());
        System.out.printf("  Attendance         : %d total, %d this cycle (limit %d)%n",
                getAttendance(), getAttendanceThisCycle(), ATTENDANCE_LIMIT);
        System.out.printf("  Loyalty points     : %.1f%n", getLoyaltyPoints());
        System.out.printf("  Upgrade eligible   : %s%n", eligibleForUpgrade ? "Yes" : "No");
        System.out.printf("  Referral source    : %s%n", referralSource);
        System.out.printf("  Status             : %s%n", isActive() ? "Active" : "Inactive");
        if (!removalReason.isEmpty()) {
            System.out.printf("  Last removal reason: %s%n", removalReason);
        }
        System.out.println("------------------------------------------------------------");
    }

    /**
     * Regular members are graded on a lower bar than premium members --
     * same method name, different rule. That difference is the point.
     */
    @Override
    public EvaluationOutcome evaluate() {
        int cycle = getAttendanceThisCycle();
        if (!isActive()) {
            return new EvaluationOutcome(EvaluationOutcome.Status.INACTIVE,
                    "Membership is currently suspended.", -10.0);
        }
        if (cycle >= 16) {
            return new EvaluationOutcome(EvaluationOutcome.Status.EXCELLENT,
                    "Attended " + cycle + " sessions this cycle.", 50.0);
        }
        if (cycle >= 10) {
            return new EvaluationOutcome(EvaluationOutcome.Status.GOOD,
                    "Attended " + cycle + " sessions this cycle.", 20.0);
        }
        if (cycle >= 4) {
            return new EvaluationOutcome(EvaluationOutcome.Status.AVERAGE,
                    "Attended " + cycle + " sessions this cycle.", 0.0);
        }
        if (cycle > 0) {
            return new EvaluationOutcome(EvaluationOutcome.Status.AT_RISK,
                    "Only " + cycle + " session(s) this cycle.", -25.0);
        }
        return new EvaluationOutcome(EvaluationOutcome.Status.INACTIVE,
                "No attendance recorded this cycle.", -40.0);
    }

    @Override
    public void revertMember(String reason) throws InvalidInputException {
        if (reason == null || reason.trim().isEmpty()) {
            throw new InvalidInputException("A removal reason is required.");
        }
        resetBaseState();
        this.plan = Plan.BASIC;
        this.eligibleForUpgrade = false;
        this.removalReason = reason.trim();
    }

    // ------------------------------------------------------------ own behaviour

    /**
     * Move to a better plan. Rejects the three ways this can go wrong so the
     * object cannot end up in a state the rules do not allow.
     */
    public void upgradePlan(Plan target) throws InvalidInputException {
        if (target == null) {
            throw new InvalidInputException("Target plan is required.");
        }
        // Most specific message first, so the admin is told the real reason.
        if (target == plan) {
            throw new InvalidInputException("Member " + getId() + " is already on the "
                    + target.getLabel() + " plan.");
        }
        if (target.getRank() < plan.getRank()) {
            throw new InvalidInputException("Cannot downgrade member " + getId() + " from "
                    + plan.getLabel() + " to " + target.getLabel() + ".");
        }
        if (!eligibleForUpgrade) {
            if (getAttendance() >= ATTENDANCE_LIMIT) {
                throw new InvalidInputException("Member " + getId()
                        + " has already used the upgrade unlocked by their attendance."
                        + " Another " + ATTENDANCE_LIMIT + " sessions are needed for the next one.");
            }
            throw new InvalidInputException("Member " + getId() + " needs " + ATTENDANCE_LIMIT
                    + " sessions to unlock an upgrade (currently " + getAttendance() + ").");
        }
        this.plan = target;
        this.eligibleForUpgrade = false;
        adjustLoyaltyPoints(25.0);   // upgrade bonus
    }

    public Plan getPlan() {
        return plan;
    }

    public boolean isEligibleForUpgrade() {
        return eligibleForUpgrade;
    }

    public void setEligibleForUpgrade(boolean eligible) {
        this.eligibleForUpgrade = eligible;
    }

    public String getReferralSource() {
        return referralSource;
    }

    public void setReferralSource(String referralSource) {
        this.referralSource = (referralSource == null || referralSource.trim().isEmpty())
                ? "None" : referralSource.trim();
    }

    public String getRemovalReason() {
        return removalReason;
    }

    // ------------------------------------------------------------------- CSV

    @Override
    public String toCsvRecord() {
        return CsvUtil.join(
                getMemberType(), getId(), getName(), getLocation(), getPhone(), getEmail(),
                getGender(), getDob(), getMembershipStartDate(),
                getAttendance(), getAttendanceThisCycle(),
                String.format("%.1f", getLoyaltyPoints()), isActive(),
                plan.name(), eligibleForUpgrade, referralSource, removalReason);
    }

    /** Rebuild a RegularMember from a parsed CSV row. */
    public static RegularMember fromCsv(String[] f, int lineNumber)
            throws DataFormatException, InvalidInputException {

        if (f.length < 17) {
            throw new DataFormatException(lineNumber,
                    "expected 17 columns for a REGULAR row but found " + f.length + ".");
        }
        try {
            RegularMember m = new RegularMember(
                    Integer.parseInt(f[1]), f[2], f[3], f[4], f[5], f[6],
                    LocalDate.parse(f[7]), LocalDate.parse(f[8]), f[15]);

            m.restoreCounters(Integer.parseInt(f[9]), Integer.parseInt(f[10]),
                    Double.parseDouble(f[11]), Boolean.parseBoolean(f[12]));
            m.plan = Plan.parse(f[13]);
            m.eligibleForUpgrade = Boolean.parseBoolean(f[14]);
            m.removalReason = f[16];
            return m;
        } catch (NumberFormatException e) {
            throw new DataFormatException(lineNumber, "a numeric column is not a number (" + e.getMessage() + ").");
        } catch (java.time.format.DateTimeParseException e) {
            throw new DataFormatException(lineNumber, "a date column is not in YYYY-MM-DD form (" + e.getParsedString() + ").");
        }
    }
}
