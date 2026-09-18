package gym.ui;

import gym.model.GymMember;

import java.util.List;

/** All formatted console output that is not a single member's detail block. */
public class TablePrinter {

    private static final String RULE =
            "----------------------------------------------------------------------------------";

    public void printMemberTable(String title, List<GymMember> members) {
        System.out.println();
        System.out.println(RULE);
        System.out.println("  " + title + "  (" + members.size() + " record"
                + (members.size() == 1 ? "" : "s") + ")");
        System.out.println(RULE);

        if (members.isEmpty()) {
            System.out.println("  No matching records.");
            System.out.println(RULE);
            return;
        }

        System.out.printf("  %-6s %-22s %-9s %-14s %6s %10s  %-8s%n",
                "ID", "NAME", "TYPE", "LOCATION", "ATTND", "POINTS", "STATUS");
        System.out.println(RULE);
        for (GymMember m : members) {
            System.out.println("  " + m.toSummaryRow());
        }
        System.out.println(RULE);
    }

    public void printLines(List<String> lines) {
        for (String line : lines) {
            System.out.println(line);
        }
    }

    public void printHeading(String heading) {
        System.out.println();
        System.out.println("  === " + heading + " ===");
    }

    public void printSuccess(String message) {
        System.out.println("  [OK] " + message);
    }

    public void printError(String message) {
        System.out.println("  [!!] " + message);
    }

    public void printInfo(String message) {
        System.out.println("  [--] " + message);
    }
}
