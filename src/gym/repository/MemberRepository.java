package gym.repository;

import gym.exception.DuplicateMemberException;
import gym.exception.MemberNotFoundException;
import gym.model.GymMember;
import gym.model.Plan;
import gym.model.RegularMember;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The single owner of member data. Nothing else in the system is allowed to
 * hold the backing list, which is why getAll() hands back an unmodifiable view.
 *
 * COLLECTIONS USED HERE, AND WHY:
 *
 *  - ArrayList<GymMember> is the primary store. The dominant operations are
 *    "walk every member" (display, evaluate, save) and "append one", both of
 *    which an ArrayList does in O(1) amortised time with excellent locality.
 *
 *  - HashMap<Integer, GymMember> is a secondary index on the ID. Without it,
 *    findById would be an O(n) linear scan of the list; with it, the lookup is
 *    O(1). The cost is one extra reference per member and the discipline of
 *    keeping the two in sync on every add and delete. benchmarkLookup() below
 *    measures the difference so the improvement can be demonstrated rather
 *    than merely claimed.
 *
 *  - TreeMap<String, GymMember> backs the sorted-by-name report. A TreeMap
 *    keeps its keys ordered on insert (O(log n) each), so the report never has
 *    to call Collections.sort() over the whole list.
 */
public class MemberRepository {

    private final List<GymMember> members = new ArrayList<GymMember>();
    private final Map<Integer, GymMember> idIndex = new HashMap<Integer, GymMember>();

    // ------------------------------------------------------------------ CREATE

    public void add(GymMember member) throws DuplicateMemberException {
        if (idIndex.containsKey(member.getId())) {
            throw new DuplicateMemberException(member.getId());
        }
        members.add(member);
        idIndex.put(member.getId(), member);
    }

    /** Bulk load from file. Duplicate IDs are reported, not silently dropped. */
    public int addAll(List<GymMember> loaded, List<String> warnings) {
        int added = 0;
        for (GymMember m : loaded) {
            try {
                add(m);
                added++;
            } catch (DuplicateMemberException e) {
                warnings.add(e.getMessage());
            }
        }
        return added;
    }

    // -------------------------------------------------------------------- READ

    /** O(1) thanks to the HashMap index. */
    public GymMember findById(int id) throws MemberNotFoundException {
        GymMember found = idIndex.get(id);
        if (found == null) {
            throw new MemberNotFoundException(id);
        }
        return found;
    }

    public boolean exists(int id) {
        return idIndex.containsKey(id);
    }

    /** Partial, case-insensitive name match. */
    public List<GymMember> findByName(String fragment) {
        List<GymMember> hits = new ArrayList<GymMember>();
        if (fragment == null) {
            return hits;
        }
        String needle = fragment.trim().toLowerCase();
        for (GymMember m : members) {
            if (m.getName().toLowerCase().contains(needle)) {
                hits.add(m);
            }
        }
        return hits;
    }

    public List<GymMember> findByType(String type) {
        List<GymMember> hits = new ArrayList<GymMember>();
        for (GymMember m : members) {
            if (m.getMemberType().equalsIgnoreCase(type)) {
                hits.add(m);
            }
        }
        return hits;
    }

    public List<GymMember> findByLocation(String location) {
        List<GymMember> hits = new ArrayList<GymMember>();
        if (location == null) {
            return hits;
        }
        String needle = location.trim().toLowerCase();
        for (GymMember m : members) {
            if (m.getLocation().toLowerCase().contains(needle)) {
                hits.add(m);
            }
        }
        return hits;
    }

    public List<GymMember> findByActiveStatus(boolean active) {
        List<GymMember> hits = new ArrayList<GymMember>();
        for (GymMember m : members) {
            if (m.isActive() == active) {
                hits.add(m);
            }
        }
        return hits;
    }

    public List<GymMember> findByPlan(Plan plan) {
        List<GymMember> hits = new ArrayList<GymMember>();
        for (GymMember m : members) {
            if (m instanceof RegularMember && ((RegularMember) m).getPlan() == plan) {
                hits.add(m);
            }
        }
        return hits;
    }

    public List<GymMember> getAll() {
        return Collections.unmodifiableList(members);
    }

    public int size() {
        return members.size();
    }

    public int countActive() {
        int n = 0;
        for (GymMember m : members) {
            if (m.isActive()) {
                n++;
            }
        }
        return n;
    }

    /** Next free ID in the block that member type uses (1001+ / 2001+). */
    public int nextId(String memberType) {
        int candidate = "PREMIUM".equalsIgnoreCase(memberType) ? 2001 : 1001;
        while (idIndex.containsKey(candidate)) {
            candidate++;
        }
        return candidate;
    }

    // ------------------------------------------------------------------ UPDATE

    /**
     * Members are mutated in place through their own validating setters, so an
     * explicit update() only has to confirm the record is still registered.
     */
    public void update(GymMember member) throws MemberNotFoundException {
        if (!idIndex.containsKey(member.getId())) {
            throw new MemberNotFoundException(member.getId());
        }
        idIndex.put(member.getId(), member);
    }

    // ------------------------------------------------------------------ DELETE

    public GymMember delete(int id) throws MemberNotFoundException {
        GymMember removed = idIndex.remove(id);
        if (removed == null) {
            throw new MemberNotFoundException(id);
        }
        members.remove(removed);
        return removed;
    }

    // ----------------------------------------------------------------- REPORTS

    /** TreeMap keeps names in order for free. Key is name + id to allow duplicates. */
    public TreeMap<String, GymMember> sortedByName() {
        TreeMap<String, GymMember> sorted = new TreeMap<String, GymMember>(String.CASE_INSENSITIVE_ORDER);
        for (GymMember m : members) {
            sorted.put(m.getName() + " #" + m.getId(), m);
        }
        return sorted;
    }

    /** Top n members by loyalty points, highest first. */
    public List<GymMember> topByLoyalty(int n) {
        List<GymMember> copy = new ArrayList<GymMember>(members);
        copy.sort(new Comparator<GymMember>() {
            @Override
            public int compare(GymMember a, GymMember b) {
                return Double.compare(b.getLoyaltyPoints(), a.getLoyaltyPoints());
            }
        });
        return copy.subList(0, Math.min(n, copy.size()));
    }

    public double totalOutstanding() {
        double sum = 0.0;
        for (GymMember m : members) {
            sum += m.getMonthlyDue();
        }
        return sum;
    }

    /**
     * Demonstrates the benefit of the HashMap index: runs the same number of
     * lookups both ways and reports the elapsed nanoseconds.
     */
    public String benchmarkLookup(int iterations) {
        if (members.isEmpty()) {
            return "No members loaded -- nothing to benchmark.";
        }
        int[] ids = new int[members.size()];
        for (int i = 0; i < members.size(); i++) {
            ids[i] = members.get(i).getId();
        }

        // 1. linear scan of the ArrayList
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            int target = ids[i % ids.length];
            for (GymMember m : members) {
                if (m.getId() == target) {
                    break;
                }
            }
        }
        long listNanos = System.nanoTime() - start;

        // 2. HashMap index
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            idIndex.get(ids[i % ids.length]);
        }
        long mapNanos = System.nanoTime() - start;

        double speedup = mapNanos == 0 ? 0 : (double) listNanos / (double) mapNanos;
        return String.format(
                "%d lookups over %d members%n"
              + "  ArrayList linear scan : %,12d ns%n"
              + "  HashMap index         : %,12d ns%n"
              + "  Speed-up              : %.1fx",
                iterations, members.size(), listNanos, mapNanos, speedup);
    }
}
