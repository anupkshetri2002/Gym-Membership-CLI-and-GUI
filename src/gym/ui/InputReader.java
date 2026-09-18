package gym.ui;

import gym.exception.InvalidInputException;
import gym.model.Plan;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

/**
 * Every piece of console input goes through this class.
 *
 * Centralising it means the parsing exceptions -- NumberFormatException,
 * DateTimeParseException -- are caught in exactly one place and turned into a
 * re-prompt. No other class in the system ever touches a Scanner, so no other
 * class can crash on a typo.
 */
public class InputReader {

    private final Scanner scanner;

    public InputReader(Scanner scanner) {
        this.scanner = scanner;
    }

    /** Raw line. Throws InputExhaustedException at end of stream. */
    public String readLine(String prompt) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            System.out.println();
            throw new InputExhaustedException();
        }
        return scanner.nextLine().trim();
    }

    public String readNonEmpty(String prompt) {
        while (true) {
            String value = readLine(prompt);
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("  ! This field cannot be blank. Please try again.");
        }
    }

    /** Reads a line, returning the fallback when the user just presses Enter. */
    public String readOrDefault(String prompt, String fallback) {
        String value = readLine(prompt);
        return value.isEmpty() ? fallback : value;
    }

    public int readInt(String prompt) {
        while (true) {
            String raw = readLine(prompt);
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                System.out.println("  ! '" + raw + "' is not a whole number. Please try again.");
            }
        }
    }

    public int readInt(String prompt, int min, int max) {
        while (true) {
            int value = readInt(prompt);
            if (value >= min && value <= max) {
                return value;
            }
            System.out.println("  ! Please enter a number between " + min + " and " + max + ".");
        }
    }

    /** Same as readInt but returns the fallback on a blank line. */
    public int readIntOrDefault(String prompt, int fallback) {
        while (true) {
            String raw = readLine(prompt);
            if (raw.isEmpty()) {
                return fallback;
            }
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                System.out.println("  ! '" + raw + "' is not a whole number. Press Enter to accept "
                        + fallback + ", or type a number.");
            }
        }
    }

    public double readDouble(String prompt) {
        while (true) {
            String raw = readLine(prompt);
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                System.out.println("  ! '" + raw + "' is not a valid amount. Please try again.");
            }
        }
    }

    /** Re-prompts until the phone matches the same rule GymMember enforces. */
    public String readPhone(String prompt) {
        while (true) {
            String raw = readNonEmpty(prompt);
            if (raw.matches("\\d{7,15}")) {
                return raw;
            }
            System.out.println("  ! A phone number must be 7 to 15 digits with no spaces or dashes.");
        }
    }

    /** Re-prompts until the address looks like an email. */
    public String readEmail(String prompt) {
        while (true) {
            String raw = readNonEmpty(prompt);
            if (raw.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
                return raw;
            }
            System.out.println("  ! That is not a valid email address, e.g. name@example.com.");
        }
    }

    /** Re-prompts until the date is valid AND in the past. */
    public LocalDate readPastDate(String prompt) {
        while (true) {
            LocalDate date = readDate(prompt);
            if (!date.isAfter(LocalDate.now())) {
                return date;
            }
            System.out.println("  ! That date is in the future. Please enter a past date.");
        }
    }

    public LocalDate readDate(String prompt) {
        while (true) {
            String raw = readLine(prompt);
            try {
                return LocalDate.parse(raw);
            } catch (DateTimeParseException e) {
                System.out.println("  ! Dates must be in YYYY-MM-DD form, e.g. 2001-04-12.");
            }
        }
    }

    public LocalDate readDateOrDefault(String prompt, LocalDate fallback) {
        while (true) {
            String raw = readLine(prompt);
            if (raw.isEmpty()) {
                return fallback;
            }
            try {
                return LocalDate.parse(raw);
            } catch (DateTimeParseException e) {
                System.out.println("  ! Dates must be in YYYY-MM-DD form. Press Enter to keep " + fallback + ".");
            }
        }
    }

    public boolean readYesNo(String prompt) {
        while (true) {
            String raw = readLine(prompt + " (y/n): ").toLowerCase();
            if (raw.equals("y") || raw.equals("yes")) {
                return true;
            }
            if (raw.equals("n") || raw.equals("no")) {
                return false;
            }
            System.out.println("  ! Please answer y or n.");
        }
    }

    public Plan readPlan(String prompt) {
        while (true) {
            String raw = readLine(prompt);
            try {
                return Plan.parse(raw);
            } catch (InvalidInputException e) {
                System.out.println("  ! " + e.getMessage());
            }
        }
    }

    public void pause() {
        readLine("\n  Press Enter to continue...");
    }
}
