package gym.ui;

import gym.exception.DuplicateMemberException;
import gym.exception.GymSystemException;
import gym.exception.InvalidInputException;
import gym.exception.MemberNotFoundException;
import gym.io.MemberFileHandler;
import gym.io.TransactionFileHandler;
import gym.model.GymMember;
import gym.model.Plan;
import gym.model.PremiumMember;
import gym.model.RegularMember;
import gym.model.transaction.Transaction;
import gym.repository.MemberRepository;
import gym.service.ActivityLog;
import gym.service.EvaluationService;
import gym.service.MembershipService;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The text-based user interface: menus, prompts, feedback.
 *
 * This is the ONLY class that catches GymSystemException and prints to the
 * user. Everything below it throws; nothing below it knows the console exists.
 */
public class ConsoleApp {

    private final MemberRepository repository;
    private final MembershipService membershipService;
    private final EvaluationService evaluationService;
    private final MemberFileHandler memberFile;
    private final TransactionFileHandler transactionFile;
    private final ActivityLog log;
    private final List<Transaction> ledger;
    private final InputReader in;
    private final TablePrinter out;

    private final String memberFilePath;
    private final String transactionFilePath;
    private boolean unsavedChanges = false;
    private int ledgerSavedUpTo = 0;

    public ConsoleApp(MemberRepository repository, MembershipService membershipService,
                      EvaluationService evaluationService, MemberFileHandler memberFile,
                      TransactionFileHandler transactionFile, ActivityLog log,
                      List<Transaction> ledger, InputReader in, TablePrinter out,
                      String memberFilePath, String transactionFilePath) {
        this.repository = repository;
        this.membershipService = membershipService;
        this.evaluationService = evaluationService;
        this.memberFile = memberFile;
        this.transactionFile = transactionFile;
        this.log = log;
        this.ledger = ledger;
        this.in = in;
        this.out = out;
        this.memberFilePath = memberFilePath;
        this.transactionFilePath = transactionFilePath;
    }

    // ================================================================= STARTUP

    public void run() {
        printBanner();
        loadDataFile();

        try {
            mainLoop();
        } catch (InputExhaustedException e) {
            System.out.println();
            out.printInfo("Input ended. Saving before shutdown...");
            saveAll(true);
        }
    }

    private void printBanner() {
        System.out.println();
        System.out.println("############################################################");
        System.out.println("#                                                          #");
        System.out.println("#          GYM MEMBERSHIP MANAGEMENT SYSTEM  v1.0          #");
        System.out.println("#                                                          #");
        System.out.println("############################################################");
    }

    /**
     * EXCEPTION HANDLING: a missing or unreadable data file is not fatal. The
     * admin is told exactly what went wrong and offered an empty roster.
     */
    private void loadDataFile() {
        out.printHeading("Loading data");
        List<String> warnings = new ArrayList<String>();

        try {
            List<GymMember> loaded = memberFile.load(memberFilePath, warnings);
            int added = repository.addAll(loaded, warnings);
            out.printSuccess("Loaded " + added + " member(s) from " + memberFilePath);

            if (!warnings.isEmpty()) {
                out.printError(warnings.size() + " row(s) were rejected:");
                for (String w : warnings) {
                    System.out.println("       - " + w);
                }
            }
            log.push("Loaded " + added + " members from file");

        } catch (FileNotFoundException e) {
            out.printError(e.getMessage());
            out.printInfo("Starting with an empty roster. Records you add will be");
            out.printInfo("written to " + memberFilePath + " when you save.");
            log.push("Data file missing -- started empty");

        } catch (IOException e) {
            out.printError("Could not read the data file: " + e.getMessage());
            out.printInfo("Starting with an empty roster.");
            log.push("Data file unreadable -- started empty");
        }
    }

    // =============================================================== MAIN MENU

    private void mainLoop() {
        boolean running = true;
        while (running) {
            printMainMenu();
            int choice = in.readInt("  Choice: ", 0, 9);
            System.out.println();

            switch (choice) {
                case 1: addMemberMenu();       break;
                case 2: viewAllMembers();      break;
                case 3: searchMenu();          break;
                case 4: updateMenu();          break;
                case 5: deleteMemberMenu();    break;
                case 6: attendanceMenu();      break;
                case 7: runEvaluation();       break;
                case 8: reportsMenu();         break;
                case 9: dataMenu();            break;
                case 0: running = exitMenu();  break;
                default: out.printError("Unknown option."); break;
            }
        }
    }

    private void printMainMenu() {
        System.out.println();
        System.out.println("============================================================");
        System.out.printf ("   MAIN MENU        members: %-3d   active: %-3d   %s%n",
                repository.size(), repository.countActive(),
                unsavedChanges ? "*UNSAVED*" : "saved");
        System.out.println("============================================================");
        System.out.println("   1. Add a new member");
        System.out.println("   2. View all members");
        System.out.println("   3. Search / query members");
        System.out.println("   4. Update a member record");
        System.out.println("   5. Delete a member record");
        System.out.println("   6. Attendance and payments");
        System.out.println("   7. Run periodic evaluation (rewards / penalties)");
        System.out.println("   8. Reports");
        System.out.println("   9. Data file and activity log");
        System.out.println("   0. Exit");
        System.out.println("------------------------------------------------------------");
    }

    // ============================================================ 1. ADD

    private void addMemberMenu() {
        out.printHeading("Add a new member");
        System.out.println("   1. Regular member");
        System.out.println("   2. Premium member");
        System.out.println("   0. Back");
        int type = in.readInt("  Choice: ", 0, 2);
        if (type == 0) {
            return;
        }
        String memberType = (type == 1) ? "REGULAR" : "PREMIUM";

        try {
            int suggested = repository.nextId(memberType);
            int id = in.readIntOrDefault("  Member ID [" + suggested + "]: ", suggested);
            if (repository.exists(id)) {
                throw new DuplicateMemberException(id);
            }

            String name     = in.readNonEmpty("  Full name          : ");
            String location = in.readNonEmpty("  Location           : ");
            String phone    = in.readPhone   ("  Phone (7-15 digits): ");
            String email    = in.readEmail   ("  Email              : ");
            String gender   = in.readNonEmpty("  Gender             : ");
            LocalDate dob   = in.readPastDate("  Date of birth (YYYY-MM-DD): ");
            LocalDate start = in.readDateOrDefault(
                    "  Membership start [" + LocalDate.now() + "]: ", LocalDate.now());

            GymMember member;
            if (type == 1) {
                String referral = in.readOrDefault("  Referral source [None]: ", "None");
                member = new RegularMember(id, name, location, phone, email, gender, dob, start, referral);
            } else {
                String trainer = in.readNonEmpty("  Personal trainer   : ");
                member = new PremiumMember(id, name, location, phone, email, gender, dob, start, trainer);
            }

            repository.add(member);
            unsavedChanges = true;
            log.push("Added " + memberType + " member #" + id + " " + name);
            out.printSuccess("Member #" + id + " added.");
            member.display();

        } catch (GymSystemException e) {
            out.printError(e.getMessage());
        }
    }

    // ============================================================ 2. VIEW ALL

    private void viewAllMembers() {
        out.printMemberTable("ALL MEMBERS", repository.getAll());
        if (repository.size() > 0 && in.readYesNo("  Show full details for one member?")) {
            showOneMember();
        }
    }

    private void showOneMember() {
        try {
            int id = in.readInt("  Member ID: ");
            repository.findById(id).display();     // polymorphic display()
        } catch (MemberNotFoundException e) {
            out.printError(e.getMessage());
        }
    }

    // ============================================================ 3. SEARCH

    private void searchMenu() {
        out.printHeading("Search / query members");
        System.out.println("   1. By ID          (exact, uses the HashMap index)");
        System.out.println("   2. By name        (partial match)");
        System.out.println("   3. By member type (regular / premium)");
        System.out.println("   4. By location");
        System.out.println("   5. By plan        (regular members only)");
        System.out.println("   6. By status      (active / inactive)");
        System.out.println("   0. Back");
        int choice = in.readInt("  Choice: ", 0, 6);

        switch (choice) {
            case 1:
                try {
                    GymMember m = repository.findById(in.readInt("  Member ID: "));
                    m.display();
                } catch (MemberNotFoundException e) {
                    out.printError(e.getMessage());
                }
                break;
            case 2: {
                String fragment = in.readNonEmpty("  Name contains: ");
                out.printMemberTable("NAME CONTAINS '" + fragment + "'", repository.findByName(fragment));
                break;
            }
            case 3: {
                System.out.println("   1. Regular    2. Premium");
                int t = in.readInt("  Choice: ", 1, 2);
                String type = (t == 1) ? "REGULAR" : "PREMIUM";
                out.printMemberTable(type + " MEMBERS", repository.findByType(type));
                break;
            }
            case 4: {
                String loc = in.readNonEmpty("  Location contains: ");
                out.printMemberTable("LOCATION CONTAINS '" + loc + "'", repository.findByLocation(loc));
                break;
            }
            case 5: {
                Plan plan = in.readPlan("  Plan (BASIC / STANDARD / DELUXE): ");
                out.printMemberTable(plan.getLabel().toUpperCase() + " PLAN MEMBERS", repository.findByPlan(plan));
                break;
            }
            case 6: {
                boolean active = in.readYesNo("  Show active members?");
                out.printMemberTable(active ? "ACTIVE MEMBERS" : "INACTIVE MEMBERS",
                        repository.findByActiveStatus(active));
                break;
            }
            default:
                break;
        }
    }

    // ============================================================ 4. UPDATE

    private void updateMenu() {
        out.printHeading("Update a member record");
        System.out.println("   1. Contact details (location / phone / email)");
        System.out.println("   2. Upgrade plan            (regular members)");
        System.out.println("   3. Change personal trainer (premium members)");
        System.out.println("   4. Activate / deactivate membership");
        System.out.println("   5. Revert member to a fresh state");
        System.out.println("   0. Back");
        int choice = in.readInt("  Choice: ", 0, 5);
        if (choice == 0) {
            return;
        }

        try {
            int id = in.readInt("  Member ID: ");
            GymMember member = repository.findById(id);

            switch (choice) {
                case 1:
                    member.setLocation(in.readOrDefault(
                            "  Location [" + member.getLocation() + "]: ", member.getLocation()));
                    String newPhone = in.readOrDefault(
                            "  Phone    [" + member.getPhone() + "]: ", member.getPhone());
                    member.setPhone(newPhone);
                    String newEmail = in.readOrDefault(
                            "  Email    [" + member.getEmail() + "]: ", member.getEmail());
                    member.setEmail(newEmail);
                    repository.update(member);
                    out.printSuccess("Contact details updated for #" + id + ".");
                    log.push("Updated contact details for #" + id);
                    break;

                case 2: {
                    if (!(member instanceof RegularMember)) {
                        throw new InvalidInputException("Member " + id + " is not a regular member.");
                    }
                    RegularMember regular = (RegularMember) member;
                    out.printInfo("Current plan: " + regular.getPlan().getLabel()
                            + " | attendance " + regular.getAttendance() + "/" + RegularMember.ATTENDANCE_LIMIT
                            + " | eligible: " + (regular.isEligibleForUpgrade() ? "yes" : "no"));
                    Plan target = in.readPlan("  New plan (BASIC / STANDARD / DELUXE): ");
                    membershipService.upgradePlan(id, target);
                    out.printSuccess("Plan upgraded to " + target.getLabel() + ".");
                    break;
                }

                case 3: {
                    if (!(member instanceof PremiumMember)) {
                        throw new InvalidInputException("Member " + id + " is not a premium member.");
                    }
                    PremiumMember premium = (PremiumMember) member;
                    premium.setPersonalTrainer(in.readNonEmpty(
                            "  New trainer (current: " + premium.getPersonalTrainer() + "): "));
                    out.printSuccess("Trainer updated.");
                    log.push("Changed trainer for #" + id);
                    break;
                }

                case 4: {
                    boolean activate = in.readYesNo("  Set membership ACTIVE?");
                    membershipService.setActive(id, activate);
                    out.printSuccess("Member #" + id + " is now " + (activate ? "active" : "inactive") + ".");
                    break;
                }

                case 5: {
                    out.printInfo("Reverting clears attendance, points and payments.");
                    if (in.readYesNo("  Are you sure?")) {
                        String reason = in.readNonEmpty("  Reason: ");
                        membershipService.revertMember(id, reason);
                        out.printSuccess("Member #" + id + " reverted.");
                    } else {
                        out.printInfo("Cancelled.");
                    }
                    break;
                }

                default:
                    break;
            }
            unsavedChanges = true;

        } catch (GymSystemException e) {
            out.printError(e.getMessage());
        }
    }

    // ============================================================ 5. DELETE

    private void deleteMemberMenu() {
        out.printHeading("Delete a member record");
        try {
            int id = in.readInt("  Member ID: ");
            GymMember member = repository.findById(id);
            member.display();

            if (in.readYesNo("  Permanently delete this record?")) {
                repository.delete(id);
                unsavedChanges = true;
                log.push("Deleted member #" + id + " " + member.getName());
                out.printSuccess("Member #" + id + " deleted.");
            } else {
                out.printInfo("Cancelled -- nothing was deleted.");
            }
        } catch (GymSystemException e) {
            out.printError(e.getMessage());
        }
    }

    // ============================================== 6. ATTENDANCE & PAYMENTS

    private void attendanceMenu() {
        out.printHeading("Attendance and payments");
        System.out.println("   1. Mark attendance for one member");
        System.out.println("   2. Mark attendance for several members");
        System.out.println("   3. Record a payment");
        System.out.println("   4. Show discount (premium members)");
        System.out.println("   5. Show amount due");
        System.out.println("   0. Back");
        int choice = in.readInt("  Choice: ", 0, 5);
        if (choice == 0) {
            return;
        }

        try {
            switch (choice) {
                case 1: {
                    int id = in.readInt("  Member ID: ");
                    GymMember m = membershipService.markAttendance(id);
                    unsavedChanges = true;
                    out.printSuccess(String.format(
                            "%s now has %d session(s) and %.1f loyalty point(s).",
                            m.getName(), m.getAttendance(), m.getLoyaltyPoints()));
                    if (m instanceof RegularMember && ((RegularMember) m).isEligibleForUpgrade()) {
                        out.printInfo("This member is now eligible for a plan upgrade.");
                    }
                    break;
                }

                case 2: {
                    String raw = in.readNonEmpty("  Member IDs, comma separated: ");
                    List<Integer> ids = new ArrayList<Integer>();
                    for (String part : raw.split(",")) {
                        try {
                            ids.add(Integer.valueOf(part.trim()));
                        } catch (NumberFormatException e) {
                            out.printError("Ignoring '" + part.trim() + "' -- not a number.");
                        }
                    }
                    List<String> results = membershipService.markAttendanceBatch(ids);
                    System.out.println();
                    for (String r : results) {
                        System.out.println("    " + r);
                    }
                    unsavedChanges = true;
                    break;
                }

                case 3: {
                    int id = in.readInt("  Member ID: ");
                    GymMember m = repository.findById(id);
                    out.printInfo(String.format("Amount currently due: Rs. %.2f", m.getMonthlyDue()));
                    double amount = in.readDouble("  Payment amount: ");
                    membershipService.recordPayment(id, amount);
                    unsavedChanges = true;
                    out.printSuccess(String.format("Payment recorded. Outstanding: Rs. %.2f", m.getMonthlyDue()));
                    if (m instanceof PremiumMember && ((PremiumMember) m).isFullPayment()) {
                        out.printSuccess(String.format("Paid in full -- discount of Rs. %.2f earned.",
                                ((PremiumMember) m).getDiscountAmount()));
                    }
                    break;
                }

                case 4: {
                    int id = in.readInt("  Member ID: ");
                    double discount = membershipService.applyDiscount(id);
                    out.printSuccess(String.format("Discount earned: Rs. %.2f", discount));
                    break;
                }

                case 5: {
                    int id = in.readInt("  Member ID: ");
                    GymMember m = repository.findById(id);
                    out.printInfo(String.format("%s (%s) owes Rs. %.2f",
                            m.getName(), m.getMemberType(), m.getMonthlyDue()));
                    break;
                }

                default:
                    break;
            }
        } catch (GymSystemException e) {
            out.printError(e.getMessage());
        }
    }

    // ============================================================ 7. EVALUATE

    private void runEvaluation() {
        out.printHeading("Periodic evaluation");
        out.printInfo("Every member is graded on this cycle's attendance and payment status.");
        out.printInfo("Rewards and penalties are applied, then counters reset.");
        if (!in.readYesNo("  Run the evaluation now?")) {
            out.printInfo("Cancelled.");
            return;
        }

        int ledgerBefore = ledger.size();
        out.printLines(evaluationService.runCycle());
        unsavedChanges = true;

        // Append only the new transactions to the audit file.
        List<Transaction> fresh = new ArrayList<Transaction>(
                ledger.subList(ledgerBefore, ledger.size()));
        try {
            transactionFile.append(transactionFilePath, fresh);
            ledgerSavedUpTo = ledger.size();
            out.printSuccess(fresh.size() + " transaction(s) appended to " + transactionFilePath);
        } catch (IOException e) {
            out.printError("Evaluation succeeded but the ledger could not be written: " + e.getMessage());
            out.printInfo("The results are still held in memory and will be retried on save.");
        }
    }

    // ============================================================ 8. REPORTS

    private void reportsMenu() {
        out.printHeading("Reports");
        System.out.println("   1. Members sorted by name  (TreeMap)");
        System.out.println("   2. Top 5 by loyalty points");
        System.out.println("   3. Revenue and outstanding summary");
        System.out.println("   4. Collection benchmark: ArrayList scan vs HashMap index");
        System.out.println("   5. Transaction ledger (this session)");
        System.out.println("   0. Back");
        int choice = in.readInt("  Choice: ", 0, 5);

        switch (choice) {
            case 1: {
                System.out.println();
                System.out.println("  MEMBERS SORTED BY NAME");
                System.out.println("  ----------------------------------------------------");
                for (Map.Entry<String, GymMember> entry : repository.sortedByName().entrySet()) {
                    System.out.printf("    %-30s %-8s %s%n",
                            entry.getKey(),
                            entry.getValue().getMemberType(),
                            entry.getValue().isActive() ? "Active" : "Inactive");
                }
                break;
            }
            case 2:
                out.printMemberTable("TOP 5 BY LOYALTY POINTS", repository.topByLoyalty(5));
                break;
            case 3: {
                double regularRevenue = 0.0;
                double premiumPaid = 0.0;
                for (GymMember m : repository.getAll()) {
                    if (m instanceof RegularMember) {
                        regularRevenue += ((RegularMember) m).getPlan().getPrice();
                    } else if (m instanceof PremiumMember) {
                        premiumPaid += ((PremiumMember) m).getPaidAmount();
                    }
                }
                System.out.println();
                System.out.println("  REVENUE SUMMARY");
                System.out.println("  ----------------------------------------------------");
                System.out.printf("    Regular plan fees (per month) : Rs. %,12.2f%n", regularRevenue);
                System.out.printf("    Premium payments received     : Rs. %,12.2f%n", premiumPaid);
                System.out.printf("    Total outstanding             : Rs. %,12.2f%n", repository.totalOutstanding());
                System.out.printf("    Active / total members        : %d / %d%n",
                        repository.countActive(), repository.size());
                break;
            }
            case 4:
                System.out.println();
                System.out.println("  COLLECTION BENCHMARK");
                System.out.println("  ----------------------------------------------------");
                System.out.println(indent(repository.benchmarkLookup(200000)));
                System.out.println();
                System.out.println("    Both structures hold the same objects. The ArrayList");
                System.out.println("    is the store; the HashMap is an index over it.");
                break;
            case 5: {
                System.out.println();
                System.out.println("  TRANSACTION LEDGER (" + ledger.size() + " entries)");
                System.out.println("  ----------------------------------------------------");
                if (ledger.isEmpty()) {
                    System.out.println("    Nothing recorded yet.");
                } else {
                    for (Transaction t : ledger) {
                        System.out.println("    " + t);
                    }
                }
                break;
            }
            default:
                break;
        }
    }

    private String indent(String block) {
        return "    " + block.replace("\n", "\n    ");
    }

    // ============================================================ 9. DATA

    private void dataMenu() {
        out.printHeading("Data file and activity log");
        System.out.println("   1. Save members to file");
        System.out.println("   2. Save transaction ledger to file");
        System.out.println("   3. Reload members from file (discards unsaved changes)");
        System.out.println("   4. View activity log");
        System.out.println("   0. Back");
        int choice = in.readInt("  Choice: ", 0, 4);

        switch (choice) {
            case 1: saveMembers(); break;
            case 2: saveLedger();  break;
            case 3:
                if (unsavedChanges && !in.readYesNo("  You have unsaved changes. Reload anyway?")) {
                    out.printInfo("Cancelled.");
                    break;
                }
                reload();
                break;
            case 4: {
                System.out.println();
                System.out.println("  ACTIVITY LOG (most recent first)");
                System.out.println("  ----------------------------------------------------");
                List<String> recent = log.recent(20);
                if (recent.isEmpty()) {
                    System.out.println("    Nothing logged yet.");
                }
                for (String e : recent) {
                    System.out.println("    " + e);
                }
                break;
            }
            default:
                break;
        }
    }

    private void saveMembers() {
        try {
            memberFile.save(memberFilePath, repository.getAll());
            unsavedChanges = false;
            log.push("Saved " + repository.size() + " members to file");
            out.printSuccess("Saved " + repository.size() + " member(s) to " + memberFilePath);
        } catch (IOException e) {
            out.printError("Could not save the roster: " + e.getMessage());
            out.printInfo("Your changes are still in memory. Fix the problem and try again.");
        }
    }

    private void saveLedger() {
        if (ledgerSavedUpTo >= ledger.size()) {
            out.printInfo("The ledger file is already up to date.");
            return;
        }
        List<Transaction> pending = new ArrayList<Transaction>(
                ledger.subList(ledgerSavedUpTo, ledger.size()));
        try {
            transactionFile.append(transactionFilePath, pending);
            ledgerSavedUpTo = ledger.size();
            out.printSuccess(pending.size() + " transaction(s) appended to " + transactionFilePath);
        } catch (IOException e) {
            out.printError("Could not write the ledger: " + e.getMessage());
        }
    }

    private void reload() {
        List<String> warnings = new ArrayList<String>();
        try {
            List<GymMember> loaded = memberFile.load(memberFilePath, warnings);
            // rebuild from scratch
            for (GymMember m : new ArrayList<GymMember>(repository.getAll())) {
                try {
                    repository.delete(m.getId());
                } catch (MemberNotFoundException ignored) {
                    // cannot happen; the id came from the repository itself
                }
            }
            int added = repository.addAll(loaded, warnings);
            unsavedChanges = false;
            out.printSuccess("Reloaded " + added + " member(s).");
            for (String w : warnings) {
                out.printError(w);
            }
        } catch (IOException e) {
            out.printError("Reload failed: " + e.getMessage());
        }
    }

    private void saveAll(boolean quiet) {
        try {
            memberFile.save(memberFilePath, repository.getAll());
            if (!quiet) {
                out.printSuccess("Roster saved.");
            }
            unsavedChanges = false;
        } catch (IOException e) {
            out.printError("Could not save the roster: " + e.getMessage());
        }
        if (ledgerSavedUpTo < ledger.size()) {
            saveLedger();
        }
    }

    // ============================================================ 0. EXIT

    /** @return false to stop the main loop. */
    private boolean exitMenu() {
        if (unsavedChanges) {
            if (in.readYesNo("  You have unsaved changes. Save before exiting?")) {
                saveAll(false);
            } else {
                out.printInfo("Exiting without saving.");
            }
        }
        System.out.println();
        System.out.println("  Thank you for using the Gym Membership Management System.");
        System.out.println("  Goodbye.");
        System.out.println();
        return false;
    }
}
