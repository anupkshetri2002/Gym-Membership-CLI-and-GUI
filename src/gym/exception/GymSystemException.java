package gym.exception;

/**
 * Base type for every checked exception raised by the gym system.
 * Having a common parent lets the UI layer catch one type and still
 * print a meaningful, domain-specific message.
 */
public class GymSystemException extends Exception {

    private static final long serialVersionUID = 1L;

    public GymSystemException(String message) {
        super(message);
    }

    public GymSystemException(String message, Throwable cause) {
        super(message, cause);
    }
}
