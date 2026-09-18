package gym.model;

import gym.contract.CsvSerializable;
import gym.contract.Evaluable;
import gym.exception.InvalidInputException;

import java.time.LocalDate;

/**
 * ABSTRACT BASE CLASS of the member hierarchy.
 *
 * Holds everything every gym member has, and defines the operations whose
 * *behaviour* differs between member types as abstract methods. A caller can
 * hold a GymMember reference, call markAttendance() on it, and get regular or
 * premium behaviour without ever asking which type it is -- that is the
 * polymorphism this system is built around.
 *
 * ENCAPSULATION: every field is private. The only way to change state is
 * through a setter that validates first, so an object can never hold a blank
 * name or a negative attendance count.
 */
public abstract class GymMember implements CsvSerializable, Evaluable {

    private final int id;
    private String name;
    private String location;
    private String phone;
    private String email;
    private String gender;
    private LocalDate dob;
    private LocalDate membershipStartDate;

    private int attendance;            // lifetime sessions attended
    private int attendanceThisCycle;   // sessions since the last evaluation
    private double loyaltyPoints;
    private boolean active;

    protected GymMember(int id, String name, String location, String phone, String email,
                        String gender, LocalDate dob, LocalDate membershipStartDate)
            throws InvalidInputException {

        if (id <= 0) {
            throw new InvalidInputException("Member ID must be a positive number.");
        }
        this.id = id;
        setName(name);
        setLocation(location);
        setPhone(phone);
        setEmail(email);
        setGender(gender);
        setDob(dob);
        setMembershipStartDate(membershipStartDate);
        this.attendance = 0;
        this.attendanceThisCycle = 0;
        this.loyaltyPoints = 0.0;
        this.active = true;
    }

    // ---------------------------------------------------------------- abstract

    /** Record one gym visit. Point rate differs per member type. */
    public abstract void markAttendance();

    /** What this member currently owes. */
    public abstract double getMonthlyDue();

    /** "REGULAR" or "PREMIUM" -- also used as the CSV type tag. */
    public abstract String getMemberType();

    /** Print a full detail block for this member. */
    public abstract void display();

    /** Undo everything and return the member to a fresh state. */
    public abstract void revertMember(String reason) throws InvalidInputException;

    // Inherited abstract contracts, restated for readability:
    //   String toCsvRecord()          from CsvSerializable
    //   EvaluationOutcome evaluate()  from Evaluable

    // ---------------------------------------------------------------- concrete

    public void activateMembership() {
        this.active = true;
    }

    public void deactivateMembership() {
        this.active = false;
    }

    /** Shared reset used by both subclasses' revertMember implementations. */
    protected void resetBaseState() {
        this.attendance = 0;
        this.attendanceThisCycle = 0;
        this.loyaltyPoints = 0.0;
        this.active = false;
    }

    /** Add (or, with a negative delta, deduct) loyalty points. Never goes below zero. */
    public void adjustLoyaltyPoints(double delta) {
        this.loyaltyPoints += delta;
        if (this.loyaltyPoints < 0.0) {
            this.loyaltyPoints = 0.0;
        }
    }

    /** Called by the subclasses from markAttendance(). */
    protected void recordVisit(double pointsPerVisit) {
        this.attendance++;
        this.attendanceThisCycle++;
        adjustLoyaltyPoints(pointsPerVisit);
    }

    /** Called by EvaluationService once a cycle has been graded. */
    public void startNewCycle() {
        this.attendanceThisCycle = 0;
    }

    /** One-line summary used by the table view. */
    public String toSummaryRow() {
        return String.format("%-6d %-22s %-9s %-14s %6d %10.1f  %-8s",
                id,
                truncate(name, 22),
                getMemberType(),
                truncate(location, 14),
                attendance,
                loyaltyPoints,
                active ? "Active" : "Inactive");
    }

    protected static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + ".";
    }

    // ---------------------------------------------------------------- accessors

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public final void setName(String name) throws InvalidInputException {
        if (name == null || name.trim().isEmpty()) {
            throw new InvalidInputException("Name cannot be blank.");
        }
        this.name = name.trim();
    }

    public String getLocation() {
        return location;
    }

    public final void setLocation(String location) throws InvalidInputException {
        if (location == null || location.trim().isEmpty()) {
            throw new InvalidInputException("Location cannot be blank.");
        }
        this.location = location.trim();
    }

    public String getPhone() {
        return phone;
    }

    public final void setPhone(String phone) throws InvalidInputException {
        if (phone == null || !phone.trim().matches("\\d{7,15}")) {
            throw new InvalidInputException("Phone must be 7 to 15 digits, got '" + phone + "'.");
        }
        this.phone = phone.trim();
    }

    public String getEmail() {
        return email;
    }

    public final void setEmail(String email) throws InvalidInputException {
        if (email == null || !email.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new InvalidInputException("'" + email + "' is not a valid email address.");
        }
        this.email = email.trim();
    }

    public String getGender() {
        return gender;
    }

    public final void setGender(String gender) throws InvalidInputException {
        if (gender == null || gender.trim().isEmpty()) {
            throw new InvalidInputException("Gender cannot be blank.");
        }
        this.gender = gender.trim();
    }

    public LocalDate getDob() {
        return dob;
    }

    public final void setDob(LocalDate dob) throws InvalidInputException {
        if (dob == null || dob.isAfter(LocalDate.now())) {
            throw new InvalidInputException("Date of birth must be a past date.");
        }
        this.dob = dob;
    }

    public LocalDate getMembershipStartDate() {
        return membershipStartDate;
    }

    public final void setMembershipStartDate(LocalDate startDate) throws InvalidInputException {
        if (startDate == null) {
            throw new InvalidInputException("Membership start date is required.");
        }
        this.membershipStartDate = startDate;
    }

    public int getAttendance() {
        return attendance;
    }

    public int getAttendanceThisCycle() {
        return attendanceThisCycle;
    }

    public double getLoyaltyPoints() {
        return loyaltyPoints;
    }

    public boolean isActive() {
        return active;
    }

    /** Restore-from-file only: the CSV loader replays saved counters. */
    public void restoreCounters(int attendance, int attendanceThisCycle,
                                double loyaltyPoints, boolean active) {
        this.attendance = Math.max(0, attendance);
        this.attendanceThisCycle = Math.max(0, attendanceThisCycle);
        this.loyaltyPoints = Math.max(0.0, loyaltyPoints);
        this.active = active;
    }

    @Override
    public String toString() {
        return getMemberType() + " #" + id + " " + name;
    }
}
