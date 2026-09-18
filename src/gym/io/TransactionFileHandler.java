package gym.io;

import gym.model.transaction.Transaction;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Append-only audit ledger. Every reward, penalty and payment ends up here, so
 * the admin can prove after the fact why a member lost points or was suspended.
 */
public class TransactionFileHandler {

    public static final String HEADER = "date,type,memberId,memberName,amount,description";

    /** Appends the given transactions, writing a header first if the file is new. */
    public void append(String path, List<Transaction> transactions) throws IOException {
        if (transactions.isEmpty()) {
            return;
        }
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directory " + parent.getAbsolutePath());
        }
        boolean isNew = !file.exists() || file.length() == 0;

        // try-with-resources so the stream closes on any exit path
        try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
            if (isNew) {
                writer.println(HEADER);
            }
            for (Transaction t : transactions) {
                writer.println(t.toCsvRecord());   // polymorphic call
            }
            writer.flush();
            if (writer.checkError()) {
                throw new IOException("An error occurred while writing " + file.getAbsolutePath());
            }
        }
    }
}
