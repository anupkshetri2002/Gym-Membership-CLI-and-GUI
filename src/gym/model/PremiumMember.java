package gym.model;

import gym.contract.Discountable;
import gym.exception.DataFormatException;
import gym.exception.InvalidInputException;
import gym.util.CsvUtil;

import java.time.LocalDate;

/**
 * INHERITANCE + a second ABSTRACTION: a PremiumMember is a GymMember that is
 * additionally Discountable. Regular members are not, which is exactly why the
 * discount capability is an interface and not a method on the base class.
 *
 * Premium members pay one annual charge (in instalments if they want), get a
 * personal trainer, and earn double loyalty points per visit.
 */
public class PremiumMember extends GymMember implements Discountable {

    public static final double PREMIUM_CHARGE = 50000.0;
    private static final double DISCOUNT_RATE = 0.10;
    private static final double POINTS_PER_VISIT = 10.0;

    private String personalTrainer;
    private double paidAmount;
    private boolean fullPayment;
    private double discountAmount;

    public PremiumMember(int id, String name, String location, String phone, String email,
                         String gender, LocalDate dob, LocalDate membershipStartDate,
                         String personalTrainer) throws InvalidInputException {

        super(id, name, location, phone, email, gender, dob, membershipStartDate);
        setPersonalTrainer(personalTrainer);
        this.paidAmount = 0.0;
        this.fullPayment = false;
        this.discountAmount = 0.0;
    }

    // ------------------------------------------------------- POLYMORPHIC methods

    /** Double the regular rate -- same call site, different result. */
    @Override
    public void markAttendance() {
        recordVisit(POINTS_PER_VISIT);
    }

    /** Premium members owe the outstanding balance, not a monthly plan fee. */
    @Override
    public double getMonthlyDue() {
        double due = PREMIUM_CHARGE - paidAmount;
        return due < 0.0 ? 0.0 : due;
    }

    @Override
    public String getMemberType() {
        return "PREMIUM";
    }

    @Override
    public void display() {
        System.out.println("------------------------------------------------------------");
        System.out.println("  PREMIUM MEMBER");
        System.out.println("------------------------------------------------------------");
        System.out.printf("  ID                 : %d%n", getId());
        System.out.printf("  Name               : %s%n", getName());
        System.out.printf("  Location           : %s%n", getLocation());
        System.out.printf("  Phone / Email      : %s / %s%n", getPhone(), getEmail());
        System.out.printf("  Gender / DOB       : %s / %s%n", getGender(), getDob());
        System.out.printf("  Member since       : %s%n", getMembershipStartDate());
        System.out.printf("  Personal trainer   : %s%n", personalTrainer);
        System.out.printf("  Premium charge     : Rs. %.2f%n", PREMIUM_CHARGE);
        System.out.printf("  Paid / Outstanding : Rs. %.2f / Rs. %.2f%n", paidAmount, getMonthlyDue());
        System.out.printf("  Full payment       : %s%n", fullPayment ? "Yes" : "No");
        System.out.printf("  Discount earned    : Rs. %.2f%n", discountAmount);
        System.out.printf("  Attendance         : %d total, %d this cycle%n",
                getAttendance(), getAttendanceThisCycle());
        System.out.printf("  Loyalty points     : %.1f%n", getLoyaltyPoints());
        System.out.printf("  Status             : %s%n", isActive() ? "Active" : "Inactive");
        System.out.println("------------------------------------------------------------");
    }

    /**
     * Premium members are held to a higher bar than regular members, and an
     * unpaid balance is itself a negative signal.
     */
    @Override
    public EvaluationOutcome evaluate() {
        int cycle = getAttendanceThisCycle();
        if (!isActive()) {
            return new EvaluationOutcome(EvaluationOutcome.Status.INACTIVE,
                    "Membership is currently suspended.", -10.0);
        }
        if (!fullPayment && getMonthlyDue() > 0.0) {
            return new EvaluationOutcome(EvaluationOutcome.Status.AT_RISK,
                    String.format("Outstanding balance of Rs. %.2f.", getMonthlyDue()), -10.0);
        }
        if (cycle >= 20) {
            return new EvaluationOutcome(EvaluationOutcome.Status.EXCELLENT,
                    "Attended " + cycle + " sessions this cycle.", 60.0);
        }
        if (cycle >= 12) {
            return new EvaluationOutcome(EvaluationOutcome.Status.GOOD,
                    "Attended " + cycle + " sessions this cycle.", 25.0);
        }
        if (cycle >= 4) {
            return new EvaluationOutcome(EvaluationOutcome.Status.AVERAGE,
                    "Attended " + cycle + " sessions this cycle.", 0.0);
        }
        if (cycle > 0) {
            return new EvaluationOutcome(EvaluationOutcome.Status.AT_RISK,
                    "Only " + cycle + " session(s) this cycle for a premium plan.", -30.0);
        }
        return new EvaluationOutcome(EvaluationOutcome.Status.INACTIVE,
                "No attendance recorded this cycle.", -50.0);
    }

    /** Refunds whatever was paid, minus the discount already granted. */
    @Override
    public void revertMember(String reason) throws InvalidInputException {
        if (reason == null || reason.trim().isEmpty()) {
            throw new InvalidInputException("A removal reason is required.");
        }
        resetBaseState();
        this.paidAmount = 0.0;
        this.fullPayment = false;
        this.discountAmount = 0.0;
        this.personalTrainer = "Unassigned";
    }

    // ------------------------------------------------------------ own behaviour

    /** Accept an instalment. Over-payment and non-positive amounts are rejected. */
    public void payDueAmount(double amount) throws InvalidInputException {
        if (amount <= 0.0) {
            throw new InvalidInputException("Payment amount must be greater than zero.");
        }
        if (fullPayment) {
            throw new InvalidInputException("Member " + getId() + " has already paid in full.");
        }
        if (amount > getMonthlyDue()) {
            throw new InvalidInputException(String.format(
                    "Payment of Rs. %.2f exceeds the outstanding balance of Rs. %.2f.",
                    amount, getMonthlyDue()));
        }
        this.paidAmount += amount;
        if (this.paidAmount >= PREMIUM_CHARGE) {
            this.fullPayment = true;
            calculateDiscount();
        }
    }

    /** From Discountable: 10% of the premium charge, granted only on full payment. */
    @Override
    public double calculateDiscount() {
        if (fullPayment) {
            this.discountAmount = PREMIUM_CHARGE * DISCOUNT_RATE;
        } else {
            this.discountAmount = 0.0;
        }
        return this.discountAmount;
    }

    public String getPersonalTrainer() {
        return personalTrainer;
    }

    public final void setPersonalTrainer(String personalTrainer) throws InvalidInputException {
        if (personalTrainer == null || personalTrainer.trim().isEmpty()) {
            throw new InvalidInputException("A personal trainer must be assigned to a premium member.");
        }
        this.personalTrainer = personalTrainer.trim();
    }

    public double getPaidAmount() {
        return paidAmount;
    }

    public boolean isFullPayment() {
        return fullPayment;
    }

    public double getDiscountAmount() {
        return discountAmount;
    }

    // ------------------------------------------------------------------- CSV

    @Override
    public String toCsvRecord() {
        return CsvUtil.join(
                getMemberType(), getId(), getName(), getLocation(), getPhone(), getEmail(),
                getGender(), getDob(), getMembershipStartDate(),
                getAttendance(), getAttendanceThisCycle(),
                String.format("%.1f", getLoyaltyPoints()), isActive(),
                personalTrainer, String.format("%.2f", paidAmount), fullPayment,
                String.format("%.2f", discountAmount));
    }

    /** Rebuild a PremiumMember from a parsed CSV row. */
    public static PremiumMember fromCsv(String[] f, int lineNumber)
            throws DataFormatException, InvalidInputException {

        if (f.length < 17) {
            throw new DataFormatException(lineNumber,
                    "expected 17 columns for a PREMIUM row but found " + f.length + ".");
        }
        try {
            PremiumMember m = new PremiumMember(
                    Integer.parseInt(f[1]), f[2], f[3], f[4], f[5], f[6],
                    LocalDate.parse(f[7]), LocalDate.parse(f[8]), f[13]);

            m.restoreCounters(Integer.parseInt(f[9]), Integer.parseInt(f[10]),
                    Double.parseDouble(f[11]), Boolean.parseBoolean(f[12]));
            m.paidAmount = Double.parseDouble(f[14]);
            m.fullPayment = Boolean.parseBoolean(f[15]);
            m.discountAmount = Double.parseDouble(f[16]);
            return m;
        } catch (NumberFormatException e) {
            throw new DataFormatException(lineNumber, "a numeric column is not a number (" + e.getMessage() + ").");
        } catch (java.time.format.DateTimeParseException e) {
            throw new DataFormatException(lineNumber, "a date column is not in YYYY-MM-DD form (" + e.getParsedString() + ").");
        }
    }
}
