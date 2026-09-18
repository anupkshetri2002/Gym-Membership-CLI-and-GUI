package gym.gui;

import gym.model.GymMember;
import gym.model.PremiumMember;
import gym.model.RegularMember;

/**
 * Builds the detail block the GUI shows for a selected member.
 *
 * GymMember.display() writes straight to System.out, which suits the console
 * but cannot be shown in a text pane, so this class produces the same
 * information as a String. It is deliberately the only place in the GUI that
 * asks a member what concrete type it is.
 */
public final class MemberDetails {

    private MemberDetails() {
        // utility class
    }

    public static String of(GymMember m) {
        if (m == null) {
            return "  Select a member to see the full record.";
        }
        StringBuilder sb = new StringBuilder();
        line(sb, "------------------------------------------------------------");
        line(sb, "  " + m.getMemberType() + " MEMBER");
        line(sb, "------------------------------------------------------------");
        field(sb, "ID", String.valueOf(m.getId()));
        field(sb, "Name", m.getName());
        field(sb, "Location", m.getLocation());
        field(sb, "Phone / Email", m.getPhone() + " / " + m.getEmail());
        field(sb, "Gender / DOB", m.getGender() + " / " + m.getDob());
        field(sb, "Member since", String.valueOf(m.getMembershipStartDate()));

        if (m instanceof RegularMember) {
            RegularMember r = (RegularMember) m;
            field(sb, "Plan", String.format("%s (Rs. %.2f per month)",
                    r.getPlan().getLabel(), r.getPlan().getPrice()));
            field(sb, "Attendance", String.format("%d total, %d this cycle (limit %d)",
                    r.getAttendance(), r.getAttendanceThisCycle(), RegularMember.ATTENDANCE_LIMIT));
            field(sb, "Loyalty points", String.format("%.1f", r.getLoyaltyPoints()));
            field(sb, "Upgrade eligible", r.isEligibleForUpgrade() ? "Yes" : "No");
            field(sb, "Referral source", r.getReferralSource());
            field(sb, "Status", r.isActive() ? "Active" : "Inactive");
            if (r.getRemovalReason() != null && !r.getRemovalReason().isEmpty()) {
                field(sb, "Last removal reason", r.getRemovalReason());
            }
        } else if (m instanceof PremiumMember) {
            PremiumMember p = (PremiumMember) m;
            field(sb, "Personal trainer", p.getPersonalTrainer());
            field(sb, "Premium charge", String.format("Rs. %.2f", PremiumMember.PREMIUM_CHARGE));
            field(sb, "Paid / Outstanding", String.format("Rs. %.2f / Rs. %.2f",
                    p.getPaidAmount(), p.getMonthlyDue()));
            field(sb, "Full payment", p.isFullPayment() ? "Yes" : "No");
            field(sb, "Discount earned", String.format("Rs. %.2f", p.getDiscountAmount()));
            field(sb, "Attendance", String.format("%d total, %d this cycle",
                    p.getAttendance(), p.getAttendanceThisCycle()));
            field(sb, "Loyalty points", String.format("%.1f", p.getLoyaltyPoints()));
            field(sb, "Status", p.isActive() ? "Active" : "Inactive");
        }
        line(sb, "------------------------------------------------------------");
        return sb.toString();
    }

    private static void field(StringBuilder sb, String label, String value) {
        line(sb, String.format("  %-19s: %s", label, value));
    }

    private static void line(StringBuilder sb, String text) {
        sb.append(text).append(System.lineSeparator());
    }
}
