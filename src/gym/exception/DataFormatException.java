package gym.exception;

/** Thrown when a row in the CSV file cannot be parsed into a member. */
public class DataFormatException extends GymSystemException {

    private static final long serialVersionUID = 1L;

    public DataFormatException(int lineNumber, String reason) {
        super("Line " + lineNumber + " is malformed: " + reason);
    }

    public DataFormatException(String message) {
        super(message);
    }
}
