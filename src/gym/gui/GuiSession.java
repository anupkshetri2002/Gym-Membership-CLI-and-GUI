package gym.gui;

import gym.exception.MemberNotFoundException;
import gym.io.MemberFileHandler;
import gym.io.TransactionFileHandler;
import gym.model.GymMember;
import gym.model.transaction.Transaction;
import gym.repository.MemberRepository;
import gym.service.ActivityLog;
import gym.service.EvaluationService;
import gym.service.MembershipService;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything the GUI needs that is not Swing: the repository, the services,
 * the file handlers and the unsaved-changes bookkeeping.
 *
 * This is the GUI's mirror of the state ConsoleApp keeps privately. Keeping it
 * free of Swing means the save / reload rules live in one place and can be
 * tested without opening a window.
 */
public class GuiSession {

    private final MemberRepository repository;
    private final MembershipService membershipService;
    private final EvaluationService evaluationService;
    private final MemberFileHandler memberFile;
    private final TransactionFileHandler transactionFile;
    private final ActivityLog log;
    private final List<Transaction> ledger;

    private final String memberFilePath;
    private final String transactionFilePath;

    private boolean unsavedChanges = false;
    private int ledgerSavedUpTo = 0;

    public GuiSession(String memberFilePath, String transactionFilePath) {
        this.repository = new MemberRepository();
        this.log = new ActivityLog();
        this.ledger = new ArrayList<Transaction>();
        this.membershipService = new MembershipService(repository, log, ledger);
        this.evaluationService = new EvaluationService(repository, log, ledger);
        this.memberFile = new MemberFileHandler();
        this.transactionFile = new TransactionFileHandler();
        this.memberFilePath = memberFilePath;
        this.transactionFilePath = transactionFilePath;
    }

    // ------------------------------------------------------------- accessors

    public MemberRepository getRepository() {
        return repository;
    }

    public MembershipService getMembershipService() {
        return membershipService;
    }

    public EvaluationService getEvaluationService() {
        return evaluationService;
    }

    public ActivityLog getLog() {
        return log;
    }

    public List<Transaction> getLedger() {
        return ledger;
    }

    public String getMemberFilePath() {
        return memberFilePath;
    }

    public String getTransactionFilePath() {
        return transactionFilePath;
    }

    public boolean hasUnsavedChanges() {
        return unsavedChanges;
    }

    /** Called by the UI after anything that mutates a member. */
    public void markDirty() {
        unsavedChanges = true;
    }

    public boolean isLedgerUpToDate() {
        return ledgerSavedUpTo >= ledger.size();
    }

    // ------------------------------------------------------------ operations

    /**
     * Loads the member file into the repository.
     *
     * @param warnings filled with one line per rejected row
     * @return the number of members loaded
     * @throws FileNotFoundException if the file is not there -- the caller
     *         decides whether to carry on with an empty roster
     */
    public int load(List<String> warnings) throws FileNotFoundException, IOException {
        List<GymMember> loaded = memberFile.load(memberFilePath, warnings);
        int added = repository.addAll(loaded, warnings);
        unsavedChanges = false;
        return added;
    }

    /** Writes the roster back to disk and clears the unsaved flag. */
    public void saveMembers() throws IOException {
        memberFile.save(memberFilePath, repository.getAll());
        unsavedChanges = false;
        log.push("Saved " + repository.size() + " member(s) to " + memberFilePath);
    }

    /**
     * Appends only the ledger rows that have not been written yet.
     *
     * @return the number of rows appended (0 if the file was already current)
     */
    public int saveLedger() throws IOException {
        if (isLedgerUpToDate()) {
            return 0;
        }
        int from = ledgerSavedUpTo;
        transactionFile.append(transactionFilePath, ledger.subList(from, ledger.size()));
        int written = ledger.size() - from;
        ledgerSavedUpTo = ledger.size();
        log.push("Appended " + written + " ledger row(s) to " + transactionFilePath);
        return written;
    }

    /**
     * Runs an evaluation cycle and immediately persists the transactions it
     * produced, matching what the console menu does.
     *
     * @param ledgerError one-element array; set to the failure message if the
     *        ledger could not be written (the cycle itself still succeeded)
     * @return the report lines
     */
    public List<String> runEvaluation(String[] ledgerError) {
        int ledgerBefore = ledger.size();
        List<String> report = evaluationService.runCycle();
        unsavedChanges = true;
        try {
            transactionFile.append(transactionFilePath,
                    ledger.subList(ledgerBefore, ledger.size()));
            ledgerSavedUpTo = ledger.size();
        } catch (IOException e) {
            ledgerError[0] = e.getMessage();
        }
        return report;
    }

    /** Discards in-memory changes and re-reads the file from scratch. */
    public int reload(List<String> warnings) throws FileNotFoundException, IOException {
        List<GymMember> loaded = memberFile.load(memberFilePath, warnings);
        // Rebuild from scratch -- the repository has no bulk clear, so the
        // roster is emptied by id, exactly as the console menu does it.
        for (GymMember m : new ArrayList<GymMember>(repository.getAll())) {
            try {
                repository.delete(m.getId());
            } catch (MemberNotFoundException ignored) {
                // cannot happen; the id came from the repository itself
            }
        }
        int added = repository.addAll(loaded, warnings);
        unsavedChanges = false;
        log.push("Reloaded " + added + " member(s) from " + memberFilePath);
        return added;
    }
}
