package gym.gui;

import gym.exception.GymSystemException;
import gym.exception.MemberNotFoundException;
import gym.model.GymMember;
import gym.model.Plan;
import gym.model.PremiumMember;
import gym.model.RegularMember;
import gym.model.transaction.Transaction;
import gym.repository.MemberRepository;
import gym.service.MembershipService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The graphical front end. It is a peer of ConsoleApp, not a replacement:
 * both talk to the same repository and services and neither knows about the
 * other, which is exactly what the ui -> service -> repository layering is for.
 *
 * Swing rule observed throughout: every component is created and touched on the
 * event dispatch thread, which is why launch() goes through invokeLater.
 */
public final class SwingApp extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final String[] FILTERS = {
            "All members", "ID", "Name contains", "Type", "Location", "Plan", "Status"
    };

    private final transient GuiSession session;
    private final MemberTableModel tableModel = new MemberTableModel();
    private final JTable table = new JTable(tableModel);
    private final JTextArea detailArea = new JTextArea();
    private final JTextArea evaluationArea = new JTextArea();
    private final JTextArea reportArea = new JTextArea();
    private final JTextArea logArea = new JTextArea();
    private final JComboBox<String> filterBox = new JComboBox<String>(FILTERS);
    private final JTextField filterField = new JTextField(16);
    private final JLabel statusLabel = new JLabel();

    public SwingApp(GuiSession session) {
        super("Gym Membership Management System");
        this.session = session;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        buildUi();
        loadAtStartup();
        refreshTable();
        setSize(1120, 700);
        setMinimumSize(new Dimension(880, 560));
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmExit();
            }
        });
    }

    /**
     * Entry point used by Main when started with --gui.
     *
     * @param memberFilePath      roster CSV
     * @param transactionFilePath append-only ledger
     */
    public static void launch(final String memberFilePath, final String transactionFilePath) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                    // A missing system look and feel is not worth failing over;
                    // Swing falls back to Metal and the app still works.
                }
                GuiSession session = new GuiSession(memberFilePath, transactionFilePath);
                new SwingApp(session).setVisible(true);
            }
        });
    }

    // -------------------------------------------------------------------- ui

    private void buildUi() {
        setJMenuBar(buildMenuBar());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Members", buildMembersTab());
        tabs.addTab("Evaluation", buildEvaluationTab());
        tabs.addTab("Reports", buildReportsTab());
        tabs.addTab("Activity log", buildLogTab());

        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        JMenuItem save = item("Save roster", KeyEvent.VK_S, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                saveMembers();
            }
        });
        save.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK));
        file.add(save);
        file.add(item("Save transaction ledger", KeyEvent.VK_L, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                saveLedger();
            }
        }));
        file.add(item("Reload from file", KeyEvent.VK_R, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                reload();
            }
        }));
        file.addSeparator();
        file.add(item("Exit", KeyEvent.VK_X, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                confirmExit();
            }
        }));
        bar.add(file);

        JMenu help = new JMenu("Help");
        help.setMnemonic(KeyEvent.VK_H);
        help.add(item("About", KeyEvent.VK_A, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(SwingApp.this,
                        "Gym Membership Management System v1.0\n\n"
                                + "Roster file : " + session.getMemberFilePath() + "\n"
                                + "Ledger file : " + session.getTransactionFilePath() + "\n\n"
                                + "The same program also runs as a console app:\n"
                                + "  java -cp out gym.Main --cli",
                        "About", JOptionPane.INFORMATION_MESSAGE);
            }
        }));
        bar.add(help);
        return bar;
    }

    private JMenuItem item(String text, int mnemonic, ActionListener action) {
        JMenuItem mi = new JMenuItem(text, mnemonic);
        mi.addActionListener(action);
        return mi;
    }

    private JComponent buildMembersTab() {
        // ---- search / filter bar
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.X_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        top.add(new JLabel("Show: "));
        top.add(filterBox);
        top.add(Box.createRigidArea(new Dimension(8, 0)));
        top.add(filterField);
        JButton apply = new JButton("Search");
        JButton clear = new JButton("Clear");
        top.add(Box.createRigidArea(new Dimension(8, 0)));
        top.add(apply);
        top.add(Box.createRigidArea(new Dimension(4, 0)));
        top.add(clear);
        top.add(Box.createHorizontalGlue());

        ActionListener doFilter = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                refreshTable();
            }
        };
        apply.addActionListener(doFilter);
        filterField.addActionListener(doFilter);
        filterBox.addActionListener(doFilter);
        clear.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                filterField.setText("");
                filterBox.setSelectedIndex(0);
                refreshTable();
            }
        });

        // ---- table
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setRowSorter(new TableRowSorter<MemberTableModel>(tableModel));
        table.setFillsViewportHeight(true);
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    detailArea.setText(MemberDetails.of(selectedMember()));
                    detailArea.setCaretPosition(0);
                }
            }
        });

        monospace(detailArea);
        detailArea.setEditable(false);
        detailArea.setText(MemberDetails.of(null));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(table), new JScrollPane(detailArea));
        split.setResizeWeight(0.62);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(top, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        panel.add(buildActionBar(), BorderLayout.SOUTH);
        return panel;
    }

    /** The GUI's answer to console menus 1, 4, 5 and 6. */
    private JComponent buildActionBar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

        bar.add(button("Add member", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                addMember();
            }
        }));
        bar.add(button("Edit", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                editMember();
            }
        }));
        bar.add(button("Delete", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                deleteMember();
            }
        }));
        bar.add(Box.createRigidArea(new Dimension(16, 0)));
        bar.add(button("Mark attendance", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                markAttendance();
            }
        }));
        bar.add(button("Record payment", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                recordPayment();
            }
        }));
        bar.add(button("Show discount", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showDiscount();
            }
        }));
        bar.add(Box.createRigidArea(new Dimension(16, 0)));
        bar.add(button("Upgrade plan", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                upgradePlan();
            }
        }));
        bar.add(button("Activate / Deactivate", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                toggleActive();
            }
        }));
        bar.add(button("Revert", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                revertMember();
            }
        }));
        bar.add(Box.createHorizontalGlue());
        return bar;
    }

    private JButton button(String text, ActionListener action) {
        JButton b = new JButton(text);
        b.addActionListener(action);
        return b;
    }

    private JComponent buildEvaluationTab() {
        monospace(evaluationArea);
        evaluationArea.setEditable(false);
        evaluationArea.setText("  Press \"Run evaluation cycle\" to grade every member.\n"
                + "  Rewards and penalties are applied and appended to the ledger file.");

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.X_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        top.add(button("Run evaluation cycle", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                runEvaluation();
            }
        }));
        top.add(Box.createHorizontalGlue());

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(evaluationArea), BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildReportsTab() {
        monospace(reportArea);
        reportArea.setEditable(false);
        reportArea.setText("  Pick a report above.");

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.X_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        top.add(button("Sorted by name", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                reportSortedByName();
            }
        }));
        top.add(button("Top loyalty", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                reportTopLoyalty();
            }
        }));
        top.add(button("Revenue", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                reportRevenue();
            }
        }));
        top.add(button("Collection benchmark", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                reportBenchmark();
            }
        }));
        top.add(button("Transaction ledger", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                reportLedger();
            }
        }));
        top.add(Box.createHorizontalGlue());

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(reportArea), BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildLogTab() {
        monospace(logArea);
        logArea.setEditable(false);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.X_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        top.add(button("Refresh", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                refreshLog();
            }
        }));
        top.add(Box.createHorizontalGlue());

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        return panel;
    }

    private void monospace(JTextArea area) {
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
    }

    // ------------------------------------------------------------ member ops

    private GymMember selectedMember() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        return tableModel.getMemberAt(table.convertRowIndexToModel(viewRow));
    }

    /**
     * Returns the selected member, or null after telling the user to pick one.
     * Every action button starts here so the "nothing selected" case is handled
     * once instead of nine times.
     */
    private GymMember requireSelection() {
        GymMember m = selectedMember();
        if (m == null) {
            info("Select a member in the table first.");
        }
        return m;
    }

    private void addMember() {
        GymMember created = MemberFormDialog.showAdd(this, session.getRepository());
        if (created == null) {
            return;
        }
        try {
            session.getRepository().add(created);
            session.markDirty();
            session.getLog().push("Added member " + created.getId() + " (" + created.getName() + ")");
            refreshTable();
            selectById(created.getId());
            info("Member " + created.getId() + " added.");
        } catch (GymSystemException e) {
            error(e.getMessage());
        }
    }

    private void editMember() {
        GymMember m = requireSelection();
        if (m == null) {
            return;
        }
        GymMember edited = MemberFormDialog.showEdit(this, session.getRepository(), m);
        if (edited == null) {
            return;
        }
        try {
            session.getRepository().update(edited);
            session.markDirty();
            session.getLog().push("Updated member " + edited.getId());
            refreshTable();
            selectById(edited.getId());
        } catch (MemberNotFoundException e) {
            error(e.getMessage());
        }
    }

    private void deleteMember() {
        GymMember m = requireSelection();
        if (m == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete member " + m.getId() + " (" + m.getName() + ")?\nThis cannot be undone.",
                "Confirm delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            session.getRepository().delete(m.getId());
            session.markDirty();
            session.getLog().push("Deleted member " + m.getId());
            refreshTable();
            info("Member " + m.getId() + " deleted.");
        } catch (GymSystemException e) {
            error(e.getMessage());
        }
    }

    private void markAttendance() {
        GymMember m = requireSelection();
        if (m == null) {
            return;
        }
        try {
            MembershipService service = session.getMembershipService();
            double before = m.getLoyaltyPoints();
            GymMember updated = service.markAttendance(m.getId());
            session.markDirty();
            refreshTable();
            selectById(updated.getId());
            info(String.format("Attendance marked for %s.%nSessions: %d   Points: %.1f -> %.1f",
                    updated.getName(), updated.getAttendance(), before, updated.getLoyaltyPoints()));
        } catch (GymSystemException e) {
            error(e.getMessage());
        }
    }

    private void recordPayment() {
        GymMember m = requireSelection();
        if (m == null) {
            return;
        }
        String text = JOptionPane.showInputDialog(this,
                String.format("Amount to record for %s (outstanding Rs. %.2f):",
                        m.getName(), m.getMonthlyDue()),
                "Record payment", JOptionPane.QUESTION_MESSAGE);
        if (text == null) {
            return;
        }
        try {
            double amount = Double.parseDouble(text.trim());
            session.getMembershipService().recordPayment(m.getId(), amount);
            session.markDirty();
            refreshTable();
            selectById(m.getId());
            info(String.format("Payment of Rs. %.2f recorded.", amount));
        } catch (NumberFormatException e) {
            error("\"" + text.trim() + "\" is not a number.");
        } catch (GymSystemException e) {
            error(e.getMessage());
        }
    }

    private void showDiscount() {
        GymMember m = requireSelection();
        if (m == null) {
            return;
        }
        try {
            double discount = session.getMembershipService().applyDiscount(m.getId());
            info(String.format("Discount for %s: Rs. %.2f", m.getName(), discount));
            refreshTable();
            selectById(m.getId());
        } catch (GymSystemException e) {
            error(e.getMessage());
        }
    }

    private void upgradePlan() {
        GymMember m = requireSelection();
        if (m == null) {
            return;
        }
        if (!(m instanceof RegularMember)) {
            error("Member " + m.getId() + " is not a regular member.");
            return;
        }
        Plan[] plans = Plan.values();
        String[] labels = new String[plans.length];
        for (int i = 0; i < plans.length; i++) {
            labels[i] = plans[i].getLabel() + String.format("  (Rs. %.2f)", plans[i].getPrice());
        }
        int index = JOptionPane.showOptionDialog(this,
                "Current plan: " + ((RegularMember) m).getPlan().getLabel()
                        + "\nUpgrade member " + m.getId() + " to:",
                "Upgrade plan", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, labels, labels[labels.length - 1]);
        if (index < 0) {
            return;
        }
        try {
            session.getMembershipService().upgradePlan(m.getId(), plans[index]);
            session.markDirty();
            refreshTable();
            selectById(m.getId());
            info("Member " + m.getId() + " upgraded to " + plans[index].getLabel() + ".");
        } catch (GymSystemException e) {
            error(e.getMessage());
        }
    }

    private void toggleActive() {
        GymMember m = requireSelection();
        if (m == null) {
            return;
        }
        boolean target = !m.isActive();
        try {
            session.getMembershipService().setActive(m.getId(), target);
            session.markDirty();
            refreshTable();
            selectById(m.getId());
            info("Member " + m.getId() + " is now " + (target ? "active" : "inactive") + ".");
        } catch (GymSystemException e) {
            error(e.getMessage());
        }
    }

    private void revertMember() {
        GymMember m = requireSelection();
        if (m == null) {
            return;
        }
        String reason = JOptionPane.showInputDialog(this,
                "Reverting member " + m.getId() + " clears the plan, points and counters.\n"
                        + "Reason:",
                "Revert member", JOptionPane.QUESTION_MESSAGE);
        if (reason == null) {
            return;
        }
        try {
            session.getMembershipService().revertMember(m.getId(), reason);
            session.markDirty();
            refreshTable();
            selectById(m.getId());
            info("Member " + m.getId() + " reverted.");
        } catch (GymSystemException e) {
            error(e.getMessage());
        }
    }

    // -------------------------------------------------------------- evaluation

    private void runEvaluation() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Run an evaluation cycle over all " + session.getRepository().size()
                        + " member(s)?\nRewards and penalties will be applied and the\n"
                        + "attendance counters for this cycle will reset.",
                "Confirm evaluation", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        String[] ledgerError = new String[1];
        List<String> report = session.runEvaluation(ledgerError);
        evaluationArea.setText(join(report));
        evaluationArea.setCaretPosition(0);
        refreshTable();
        refreshLog();
        if (ledgerError[0] != null) {
            error("The cycle ran, but the ledger could not be written: " + ledgerError[0]);
        }
    }

    // ----------------------------------------------------------------- reports

    private void reportSortedByName() {
        StringBuilder sb = new StringBuilder();
        sb.append("  MEMBERS SORTED BY NAME  (TreeMap, keys ordered on insert)").append(nl());
        sb.append("  ------------------------------------------------------------").append(nl());
        Map<String, GymMember> sorted = session.getRepository().sortedByName();
        if (sorted.isEmpty()) {
            sb.append("  No members.").append(nl());
        }
        for (Map.Entry<String, GymMember> entry : sorted.entrySet()) {
            GymMember m = entry.getValue();
            sb.append(String.format("  %-26s #%-6d %-8s %s", m.getName(), m.getId(),
                    m.getMemberType(), m.isActive() ? "Active" : "Inactive")).append(nl());
        }
        showReport(sb.toString());
    }

    private void reportTopLoyalty() {
        StringBuilder sb = new StringBuilder();
        sb.append("  TOP MEMBERS BY LOYALTY POINTS").append(nl());
        sb.append("  ------------------------------------------------------------").append(nl());
        List<GymMember> top = session.getRepository().topByLoyalty(10);
        if (top.isEmpty()) {
            sb.append("  No members.").append(nl());
        }
        int rank = 1;
        for (GymMember m : top) {
            sb.append(String.format("  %2d. %-26s #%-6d %8.1f points",
                    rank++, m.getName(), m.getId(), m.getLoyaltyPoints())).append(nl());
        }
        showReport(sb.toString());
    }

    private void reportRevenue() {
        MemberRepository repo = session.getRepository();
        double regularDue = 0;
        double premiumOutstanding = 0;
        int regulars = 0;
        int premiums = 0;
        for (GymMember m : repo.getAll()) {
            if (m instanceof PremiumMember) {
                premiums++;
                premiumOutstanding += m.getMonthlyDue();
            } else {
                regulars++;
                regularDue += m.getMonthlyDue();
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("  REVENUE SUMMARY").append(nl());
        sb.append("  ------------------------------------------------------------").append(nl());
        sb.append(String.format("  Regular members        : %d", regulars)).append(nl());
        sb.append(String.format("  Monthly plan fees due  : Rs. %.2f", regularDue)).append(nl());
        sb.append(String.format("  Premium members        : %d", premiums)).append(nl());
        sb.append(String.format("  Premium outstanding    : Rs. %.2f", premiumOutstanding)).append(nl());
        sb.append("  ------------------------------------------------------------").append(nl());
        sb.append(String.format("  Total outstanding      : Rs. %.2f", repo.totalOutstanding())).append(nl());
        showReport(sb.toString());
    }

    private void reportBenchmark() {
        StringBuilder sb = new StringBuilder();
        sb.append("  COLLECTION BENCHMARK  (ArrayList scan vs HashMap index)").append(nl());
        sb.append("  ------------------------------------------------------------").append(nl());
        sb.append(session.getRepository().benchmarkLookup(200000)).append(nl());
        showReport(sb.toString());
    }

    private void reportLedger() {
        List<Transaction> ledger = session.getLedger();
        StringBuilder sb = new StringBuilder();
        sb.append("  TRANSACTION LEDGER  (" + ledger.size() + " entries this session)").append(nl());
        sb.append("  ------------------------------------------------------------").append(nl());
        if (ledger.isEmpty()) {
            sb.append("  Nothing yet -- run an evaluation cycle or record a payment.").append(nl());
        }
        for (Transaction t : ledger) {
            sb.append("  ").append(t).append(nl());
        }
        showReport(sb.toString());
    }

    private void showReport(String text) {
        reportArea.setText(text);
        reportArea.setCaretPosition(0);
    }

    // -------------------------------------------------------------- file menu

    private void loadAtStartup() {
        List<String> warnings = new ArrayList<String>();
        try {
            int loaded = session.load(warnings);
            session.getLog().push("Loaded " + loaded + " member(s) from " + session.getMemberFilePath());
            if (!warnings.isEmpty()) {
                error("Loaded " + loaded + " member(s). "
                        + warnings.size() + " row(s) were rejected:\n\n" + join(warnings));
            }
        } catch (FileNotFoundException e) {
            error("Could not find " + session.getMemberFilePath()
                    + ".\nStarting with an empty roster -- saving will create the file.");
        } catch (IOException e) {
            error("Could not read " + session.getMemberFilePath() + ": " + e.getMessage()
                    + "\nStarting with an empty roster.");
        }
        refreshLog();
    }

    private void saveMembers() {
        try {
            session.saveMembers();
            refreshStatus();
            refreshLog();
            info("Roster saved to " + session.getMemberFilePath() + ".");
        } catch (IOException e) {
            error("Could not save the roster: " + e.getMessage());
        }
    }

    private void saveLedger() {
        try {
            int written = session.saveLedger();
            refreshLog();
            if (written == 0) {
                info("The ledger file is already up to date.");
            } else {
                info(written + " ledger row(s) appended to " + session.getTransactionFilePath() + ".");
            }
        } catch (IOException e) {
            error("Could not write the ledger: " + e.getMessage());
        }
    }

    private void reload() {
        if (session.hasUnsavedChanges()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "You have unsaved changes. Reload anyway?\nThe changes will be lost.",
                    "Confirm reload", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }
        List<String> warnings = new ArrayList<String>();
        try {
            int loaded = session.reload(warnings);
            refreshTable();
            refreshLog();
            if (warnings.isEmpty()) {
                info("Reloaded " + loaded + " member(s).");
            } else {
                error("Reloaded " + loaded + " member(s). "
                        + warnings.size() + " row(s) were rejected:\n\n" + join(warnings));
            }
        } catch (IOException e) {
            error("Reload failed: " + e.getMessage());
        }
    }

    private void confirmExit() {
        if (session.hasUnsavedChanges() || !session.isLedgerUpToDate()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "You have unsaved changes. Save before exiting?",
                    "Exit", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                return;
            }
            if (choice == JOptionPane.YES_OPTION) {
                try {
                    session.saveMembers();
                    session.saveLedger();
                } catch (IOException e) {
                    int retry = JOptionPane.showConfirmDialog(this,
                            "Saving failed: " + e.getMessage() + "\nExit anyway?",
                            "Exit", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
                    if (retry != JOptionPane.YES_OPTION) {
                        return;
                    }
                }
            }
        }
        dispose();
    }

    // -------------------------------------------------------------- refreshing

    /** Re-applies the current filter and repaints the table and status bar. */
    private void refreshTable() {
        tableModel.setRows(currentFilterResult());
        refreshStatus();
        detailArea.setText(MemberDetails.of(selectedMember()));
    }

    private List<GymMember> currentFilterResult() {
        MemberRepository repo = session.getRepository();
        String term = filterField.getText().trim();
        int mode = filterBox.getSelectedIndex();
        if (mode == 0 || term.isEmpty()) {
            return repo.getAll();
        }
        try {
            switch (mode) {
                case 1: {
                    List<GymMember> one = new ArrayList<GymMember>();
                    one.add(repo.findById(Integer.parseInt(term)));
                    return one;
                }
                case 2:
                    return repo.findByName(term);
                case 3:
                    return repo.findByType(term);
                case 4:
                    return repo.findByLocation(term);
                case 5:
                    return repo.findByPlan(Plan.parse(term));
                case 6:
                    return repo.findByActiveStatus(
                            term.toLowerCase().startsWith("a") || term.equalsIgnoreCase("true"));
                default:
                    return repo.getAll();
            }
        } catch (NumberFormatException e) {
            return new ArrayList<GymMember>();
        } catch (GymSystemException e) {
            // No match, or an unparseable plan name -- an empty table says that
            // clearly enough without interrupting the user with a dialog.
            return new ArrayList<GymMember>();
        }
    }

    private void selectById(int id) {
        int modelRow = tableModel.indexOfId(id);
        if (modelRow < 0) {
            return;
        }
        int viewRow = table.convertRowIndexToView(modelRow);
        table.setRowSelectionInterval(viewRow, viewRow);
        table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
    }

    private void refreshStatus() {
        MemberRepository repo = session.getRepository();
        statusLabel.setText(String.format(
                "  members: %d      active: %d      shown: %d      cycle: %d      %s      file: %s",
                repo.size(), repo.countActive(), tableModel.getRowCount(),
                session.getEvaluationService().getCycleNumber(),
                session.hasUnsavedChanges() ? "*UNSAVED*" : "saved",
                session.getMemberFilePath()));
    }

    private void refreshLog() {
        List<String> recent = session.getLog().recent(50);
        if (recent.isEmpty()) {
            logArea.setText("  Nothing logged yet.");
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("  RECENT ACTIVITY  (newest first, ")
              .append(session.getLog().size()).append(" total)").append(nl());
            sb.append("  ------------------------------------------------------------").append(nl());
            for (String event : recent) {
                sb.append("  ").append(event).append(nl());
            }
            logArea.setText(sb.toString());
        }
        logArea.setCaretPosition(0);
    }

    // ------------------------------------------------------------- small stuff

    private void info(String message) {
        JOptionPane.showMessageDialog(this, message, "Gym System", JOptionPane.INFORMATION_MESSAGE);
        refreshStatus();
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(this, message, "Gym System", JOptionPane.ERROR_MESSAGE);
        refreshStatus();
    }

    private String join(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append(nl());
        }
        return sb.toString();
    }

    private String nl() {
        return System.lineSeparator();
    }
}
