package gym.gui;

import gym.model.GymMember;
import gym.model.PremiumMember;
import gym.model.Plan;
import gym.model.RegularMember;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapts a List<GymMember> to a JTable. The GUI equivalent of
 * TablePrinter's roster table -- same columns, same polymorphic source.
 */
public final class MemberTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    private static final String[] COLUMNS = {
            "ID", "Name", "Type", "Location", "Plan / Trainer",
            "Attendance", "Points", "Monthly due", "Status"
    };

    private transient List<GymMember> rows = new ArrayList<GymMember>();

    /** Replaces the visible rows (used by the search/filter bar). */
    public void setRows(List<GymMember> members) {
        this.rows = new ArrayList<GymMember>(members);
        fireTableDataChanged();
    }

    public GymMember getMemberAt(int rowIndex) {
        return rows.get(rowIndex);
    }

    public int indexOfId(int id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getId() == id) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    /** Typed columns so JTable's row sorter sorts numerically, not as text. */
    @Override
    public Class<?> getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case 0:
            case 5:
                return Integer.class;
            case 6:
            case 7:
                return Double.class;
            default:
                return String.class;
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        GymMember m = rows.get(rowIndex);
        switch (columnIndex) {
            case 0: return Integer.valueOf(m.getId());
            case 1: return m.getName();
            case 2: return m.getMemberType();
            case 3: return m.getLocation();
            case 4: return planOrTrainer(m);
            case 5: return Integer.valueOf(m.getAttendance());
            case 6: return Double.valueOf(m.getLoyaltyPoints());
            case 7: return Double.valueOf(m.getMonthlyDue());
            case 8: return m.isActive() ? "Active" : "Inactive";
            default: return "";
        }
    }

    /** The one column whose meaning depends on the concrete type. */
    private String planOrTrainer(GymMember m) {
        if (m instanceof RegularMember) {
            Plan plan = ((RegularMember) m).getPlan();
            return plan.getLabel();
        }
        if (m instanceof PremiumMember) {
            return ((PremiumMember) m).getPersonalTrainer();
        }
        return "-";
    }
}
