package gym.io;

import gym.exception.DataFormatException;
import gym.exception.InvalidInputException;
import gym.model.GymMember;
import gym.model.PremiumMember;
import gym.model.RegularMember;
import gym.util.CsvUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the member roster as CSV.
 *
 * EXCEPTION POLICY: one bad row must not lose the other eleven. Each line is
 * parsed inside its own try/catch; a failure is collected as a warning and the
 * loader moves on. Only a genuinely unreadable file propagates an IOException
 * up to the UI.
 */
public class MemberFileHandler {

    public static final String HEADER =
            "type,id,name,location,phone,email,gender,dob,startDate,"
          + "attendance,attendanceThisCycle,loyaltyPoints,active,"
          + "extra1,extra2,extra3,extra4";

    /**
     * @param warnings out-parameter: one message per skipped row.
     * @return every member that parsed successfully.
     */
    public List<GymMember> load(String path, List<String> warnings)
            throws FileNotFoundException, IOException {

        List<GymMember> loaded = new ArrayList<GymMember>();
        File file = new File(path);

        if (!file.exists()) {
            throw new FileNotFoundException("Data file not found: " + file.getAbsolutePath());
        }
        if (!file.canRead()) {
            throw new IOException("Data file exists but cannot be read: " + file.getAbsolutePath());
        }

        // try-with-resources: the reader is closed even if parsing throws.
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (lineNumber == 1 && line.toLowerCase().startsWith("type,")) {
                    continue;   // header row
                }

                try {
                    loaded.add(parseRow(line, lineNumber));
                } catch (DataFormatException e) {
                    warnings.add("Skipped -- " + e.getMessage());
                } catch (InvalidInputException e) {
                    warnings.add("Skipped line " + lineNumber + " -- " + e.getMessage());
                }
            }
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                    // closing failed; nothing useful left to do
                }
            }
        }
        return loaded;
    }

    /** Dispatches on the type tag in column 0 -- the CSV equivalent of polymorphism. */
    private GymMember parseRow(String line, int lineNumber)
            throws DataFormatException, InvalidInputException {

        String[] fields = CsvUtil.parseLine(line);
        if (fields.length == 0 || fields[0].isEmpty()) {
            throw new DataFormatException(lineNumber, "the member type column is empty.");
        }

        String type = fields[0].trim().toUpperCase();
        if ("REGULAR".equals(type)) {
            return RegularMember.fromCsv(fields, lineNumber);
        }
        if ("PREMIUM".equals(type)) {
            return PremiumMember.fromCsv(fields, lineNumber);
        }
        throw new DataFormatException(lineNumber, "unknown member type '" + fields[0] + "'.");
    }

    /**
     * Writes to a temporary file first and only then replaces the real one, so
     * a crash halfway through cannot leave a half-written roster on disk.
     */
    public void save(String path, List<GymMember> members) throws IOException {
        File target = new File(path);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directory " + parent.getAbsolutePath());
        }
        File temp = new File(path + ".tmp");

        PrintWriter writer = null;
        try {
            writer = new PrintWriter(temp, "UTF-8");
            writer.println(HEADER);
            for (GymMember m : members) {
                writer.println(m.toCsvRecord());   // polymorphic call
            }
            writer.flush();
            if (writer.checkError()) {
                throw new IOException("An error occurred while writing " + temp.getAbsolutePath());
            }
        } finally {
            if (writer != null) {
                writer.close();
            }
        }

        if (target.exists() && !target.delete()) {
            throw new IOException("Could not replace the existing file " + target.getAbsolutePath());
        }
        if (!temp.renameTo(target)) {
            throw new IOException("Could not rename the temporary file to " + target.getAbsolutePath());
        }
    }
}
