package gym.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC-4180 style CSV helper. Written by hand rather than pulled from a
 * library because the brief requires plain Java file handling.
 *
 * Handles the one case a naive String.split(",") gets wrong: a field that
 * itself contains a comma, which is quoted in the file.
 */
public final class CsvUtil {

    private CsvUtil() {
        // utility class, never instantiated
    }

    /** Quote a field only if it needs it. */
    public static String escape(String field) {
        if (field == null) {
            return "";
        }
        boolean needsQuotes = field.contains(",") || field.contains("\"") || field.contains("\n");
        if (!needsQuotes) {
            return field;
        }
        return "\"" + field.replace("\"", "\"\"") + "\"";
    }

    /** Join already-escaped-or-not values into one CSV line. */
    public static String join(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(values[i] == null ? "" : values[i].toString()));
        }
        return sb.toString();
    }

    /** Split one CSV line into fields, respecting quoted sections. */
    public static String[] parseLine(String line) {
        List<String> fields = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    // a doubled quote inside quotes is a literal quote
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString().trim());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString().trim());
        return fields.toArray(new String[0]);
    }
}
