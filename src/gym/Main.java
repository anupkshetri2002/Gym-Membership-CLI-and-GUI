package gym;

import gym.gui.SwingApp;
import gym.io.MemberFileHandler;
import gym.io.TransactionFileHandler;
import gym.model.transaction.Transaction;
import gym.repository.MemberRepository;
import gym.service.ActivityLog;
import gym.service.EvaluationService;
import gym.service.MembershipService;
import gym.ui.ConsoleApp;
import gym.ui.InputReader;
import gym.ui.TablePrinter;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Entry point. Its only job is to build the objects, wire them together and
 * hand control to a UI -- no business logic lives here.
 *
 * The program ships with two front ends over the same service layer:
 *   --cli   text menus in the terminal  (ConsoleApp)  -- the default
 *   --gui   Swing desktop window        (SwingApp)
 *
 * Remaining arguments, in order:
 *   path to the members CSV      (default data/members.csv)
 *   path to the transaction log  (default data/transactions.csv)
 */
public class Main {

    private static final String DEFAULT_MEMBER_FILE = "data/members.csv";
    private static final String DEFAULT_TRANSACTION_FILE = "data/transactions.csv";

    public static void main(String[] args) {

        boolean gui = false;
        List<String> paths = new ArrayList<String>();

        for (String arg : args) {
            if ("--gui".equalsIgnoreCase(arg) || "-g".equals(arg)) {
                gui = true;
            } else if ("--cli".equalsIgnoreCase(arg) || "-c".equals(arg)) {
                gui = false;
            } else if ("--help".equalsIgnoreCase(arg) || "-h".equals(arg)) {
                printUsage();
                return;
            } else if (arg.startsWith("-")) {
                System.err.println("Unknown option: " + arg);
                printUsage();
                return;
            } else {
                paths.add(arg);
            }
        }

        String memberFilePath = paths.size() > 0 ? paths.get(0) : DEFAULT_MEMBER_FILE;
        String transactionFilePath = paths.size() > 1 ? paths.get(1) : DEFAULT_TRANSACTION_FILE;

        if (gui) {
            // SwingApp builds its own object graph on the event dispatch thread
            // and owns the window's lifetime, so there is nothing to close here.
            SwingApp.launch(memberFilePath, transactionFilePath);
            return;
        }

        runConsole(memberFilePath, transactionFilePath);
    }

    private static void runConsole(String memberFilePath, String transactionFilePath) {

        Scanner scanner = new Scanner(System.in);

        try {
            MemberRepository repository = new MemberRepository();
            ActivityLog log = new ActivityLog();
            List<Transaction> ledger = new ArrayList<Transaction>();

            MembershipService membershipService = new MembershipService(repository, log, ledger);
            EvaluationService evaluationService = new EvaluationService(repository, log, ledger);

            MemberFileHandler memberFile = new MemberFileHandler();
            TransactionFileHandler transactionFile = new TransactionFileHandler();

            InputReader in = new InputReader(scanner);
            TablePrinter out = new TablePrinter();

            ConsoleApp app = new ConsoleApp(repository, membershipService, evaluationService,
                    memberFile, transactionFile, log, ledger, in, out,
                    memberFilePath, transactionFilePath);

            app.run();

        } catch (RuntimeException e) {
            // Outermost safety net: the program reports the failure instead of
            // dumping a raw stack trace at the admin.
            System.err.println();
            System.err.println("  A fatal error stopped the program: " + e);
            System.err.println("  Please report this along with what you were doing at the time.");
        } finally {
            scanner.close();
        }
    }

    private static void printUsage() {
        System.out.println();
        System.out.println("  Gym Membership Management System");
        System.out.println();
        System.out.println("  java -cp out gym.Main [--gui | --cli] [membersFile] [ledgerFile]");
        System.out.println();
        System.out.println("    --gui, -g   open the Swing desktop window");
        System.out.println("    --cli, -c   text menus in this terminal (default)");
        System.out.println("    --help, -h  show this message");
        System.out.println();
        System.out.println("  Defaults: " + DEFAULT_MEMBER_FILE + " and " + DEFAULT_TRANSACTION_FILE);
        System.out.println();
    }
}
