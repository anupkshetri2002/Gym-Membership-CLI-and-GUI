package gym.exception;

/**
 * Thrown when a value breaks a business rule: a blank name, a negative
 * payment, an illegal plan downgrade, and so on. Validation lives in the
 * model so no caller can bypass it.
 */
public class InvalidInputException extends GymSystemException {

    private static final long serialVersionUID = 1L;

    public InvalidInputException(String message) {
        super(message);
    }
}
