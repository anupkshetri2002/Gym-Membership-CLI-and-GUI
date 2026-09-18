package gym.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A bounded, newest-first record of what the admin has done this session.
 *
 * COLLECTION CHOICE: ArrayDeque. Every entry is added at the head and the
 * oldest is dropped from the tail once the log is full -- both O(1). Doing the
 * same thing with an ArrayList would mean add(0, item), which shifts every
 * existing element and is O(n) per write.
 */
public class ActivityLog {

    private static final int MAX_ENTRIES = 50;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Deque<String> events = new ArrayDeque<String>();

    public void push(String event) {
        events.addFirst("[" + LocalDateTime.now().format(STAMP) + "] " + event);
        while (events.size() > MAX_ENTRIES) {
            events.removeLast();
        }
    }

    /** The n most recent events, newest first. */
    public List<String> recent(int n) {
        List<String> out = new ArrayList<String>();
        int taken = 0;
        for (String e : events) {
            if (taken++ >= n) {
                break;
            }
            out.add(e);
        }
        return out;
    }

    public int size() {
        return events.size();
    }
}
