package gym.gui;

import gym.exception.InvalidInputException;
import gym.model.GymMember;
import gym.model.PremiumMember;
import gym.model.RegularMember;
import gym.repository.MemberRepository;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * One modal form used for both "add a new member" and "update this member".
 *
 * All validation is delegated to the model's own setters and constructors, so
 * the rules cannot drift apart from the console UI. The dialog's only job is to
 * catch InvalidInputException and show the message next to the form.
 */
public final class MemberFormDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final boolean editing;
    private final transient MemberRepository repository;
    private final transient GymMember existing;

    private final JComboBox<String> typeBox =
            new JComboBox<String>(new String[] {"REGULAR", "PREMIUM"});
    private final JTextField idField = new JTextField(12);
    private final JTextField nameField = new JTextField(20);
    private final JTextField locationField = new JTextField(20);
    private final JTextField phoneField = new JTextField(20);
    private final JTextField emailField = new JTextField(20);
    private final JComboBox<String> genderBox =
            new JComboBox<String>(new String[] {"Male", "Female", "Other"});
    private final JTextField dobField = new JTextField(20);
    private final JTextField startField = new JTextField(20);
    private final JLabel extraLabel = new JLabel("Referral source:");
    private final JTextField extraField = new JTextField(20);
    private final JLabel errorLabel = new JLabel(" ");

    /** Null until the user presses Save on a valid form. */
    private transient GymMember result;

    private MemberFormDialog(Window owner, String title,
                             MemberRepository repository, GymMember existing) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.repository = repository;
        this.existing = existing;
        this.editing = (existing != null);
        buildUi();
        prefill();
        pack();
        setLocationRelativeTo(owner);
    }

    /** Opens the form for a new member; returns null if the user cancelled. */
    public static GymMember showAdd(Window owner, MemberRepository repository) {
        MemberFormDialog d = new MemberFormDialog(owner, "Add a new member", repository, null);
        d.setVisible(true);
        return d.result;
    }

    /**
     * Opens the form for an existing member. The member is edited in place, so
     * a non-null return means the caller should mark the roster dirty.
     */
    public static GymMember showEdit(Window owner, MemberRepository repository, GymMember member) {
        MemberFormDialog d = new MemberFormDialog(owner,
                "Update member " + member.getId(), repository, member);
        d.setVisible(true);
        return d.result;
    }

    // -------------------------------------------------------------------- ui

    private void buildUi() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addRow(form, c, row++, new JLabel("Member type:"), typeBox);
        addRow(form, c, row++, new JLabel("ID:"), idField);
        addRow(form, c, row++, new JLabel("Name:"), nameField);
        addRow(form, c, row++, new JLabel("Location:"), locationField);
        addRow(form, c, row++, new JLabel("Phone:"), phoneField);
        addRow(form, c, row++, new JLabel("Email:"), emailField);
        addRow(form, c, row++, new JLabel("Gender:"), genderBox);
        addRow(form, c, row++, new JLabel("Date of birth (YYYY-MM-DD):"), dobField);
        addRow(form, c, row++, new JLabel("Member since (YYYY-MM-DD):"), startField);
        addRow(form, c, row++, extraLabel, extraField);

        errorLabel.setForeground(new Color(0xB0, 0x00, 0x20));
        errorLabel.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 16));

        JButton save = new JButton(editing ? "Save changes" : "Add member");
        JButton cancel = new JButton("Cancel");
        save.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                submit();
            }
        });
        cancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        buttons.add(Box.createHorizontalGlue());
        buttons.add(cancel);
        buttons.add(Box.createRigidArea(new Dimension(8, 0)));
        buttons.add(save);

        JPanel south = new JPanel(new BorderLayout());
        south.add(errorLabel, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(save);

        typeBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onTypeChanged();
            }
        });
    }

    private void addRow(JPanel p, GridBagConstraints c, int row,
                        Component label, Component field) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        p.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        p.add(field, c);
    }

    private void onTypeChanged() {
        boolean premium = "PREMIUM".equals(typeBox.getSelectedItem());
        extraLabel.setText(premium ? "Personal trainer:" : "Referral source:");
        if (!editing) {
            idField.setText(String.valueOf(repository.nextId(premium ? "PREMIUM" : "REGULAR")));
        }
    }

    private void prefill() {
        if (editing) {
            typeBox.setSelectedItem(existing.getMemberType());
            typeBox.setEnabled(false);
            idField.setText(String.valueOf(existing.getId()));
            idField.setEnabled(false);
            nameField.setText(existing.getName());
            locationField.setText(existing.getLocation());
            phoneField.setText(existing.getPhone());
            emailField.setText(existing.getEmail());
            genderBox.setSelectedItem(existing.getGender());
            dobField.setText(String.valueOf(existing.getDob()));
            startField.setText(String.valueOf(existing.getMembershipStartDate()));
            if (existing instanceof PremiumMember) {
                extraLabel.setText("Personal trainer:");
                extraField.setText(((PremiumMember) existing).getPersonalTrainer());
            } else if (existing instanceof RegularMember) {
                extraLabel.setText("Referral source:");
                extraField.setText(((RegularMember) existing).getReferralSource());
            }
        } else {
            onTypeChanged();
            startField.setText(String.valueOf(LocalDate.now()));
        }
    }

    // -------------------------------------------------------------- submission

    private void submit() {
        errorLabel.setText(" ");
        try {
            LocalDate dob = parseDate(dobField.getText(), "date of birth");
            LocalDate start = parseDate(startField.getText(), "membership start date");

            if (editing) {
                applyEdits(dob, start);
                result = existing;
            } else {
                result = buildNew(dob, start);
            }
            dispose();

        } catch (InvalidInputException e) {
            errorLabel.setText(e.getMessage());
            pack();
        } catch (NumberFormatException e) {
            errorLabel.setText("The ID must be a whole number.");
            pack();
        }
    }

    private LocalDate parseDate(String text, String what) throws InvalidInputException {
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidInputException(
                    "Could not read the " + what + " -- use the format YYYY-MM-DD.");
        }
    }

    /**
     * Edits go through the member's own validating setters one at a time, so a
     * rejected value leaves every earlier field already applied -- the same
     * behaviour as the console's field-by-field update menu.
     */
    private void applyEdits(LocalDate dob, LocalDate start) throws InvalidInputException {
        existing.setName(nameField.getText());
        existing.setLocation(locationField.getText());
        existing.setPhone(phoneField.getText());
        existing.setEmail(emailField.getText());
        existing.setGender(String.valueOf(genderBox.getSelectedItem()));
        existing.setDob(dob);
        existing.setMembershipStartDate(start);
        if (existing instanceof PremiumMember) {
            ((PremiumMember) existing).setPersonalTrainer(extraField.getText());
        } else if (existing instanceof RegularMember) {
            ((RegularMember) existing).setReferralSource(extraField.getText());
        }
    }

    private GymMember buildNew(LocalDate dob, LocalDate start) throws InvalidInputException {
        int id = Integer.parseInt(idField.getText().trim());
        if (repository.exists(id)) {
            throw new InvalidInputException(
                    "A member with ID " + id + " is already registered.");
        }
        String gender = String.valueOf(genderBox.getSelectedItem());
        if ("PREMIUM".equals(typeBox.getSelectedItem())) {
            return new PremiumMember(id, nameField.getText(), locationField.getText(),
                    phoneField.getText(), emailField.getText(), gender, dob, start,
                    extraField.getText());
        }
        return new RegularMember(id, nameField.getText(), locationField.getText(),
                phoneField.getText(), emailField.getText(), gender, dob, start,
                extraField.getText());
    }

    /** Convenience for callers that want a plain warning box. */
    static void warn(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Gym System",
                JOptionPane.WARNING_MESSAGE);
    }
}
