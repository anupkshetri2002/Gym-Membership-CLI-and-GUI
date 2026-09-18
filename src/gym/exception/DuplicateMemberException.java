package gym.exception;

/** Thrown when an ID that is already registered is added again. */
public class DuplicateMemberException extends GymSystemException {

    private static final long serialVersionUID = 1L;

    public DuplicateMemberException(int id) {
        super("A member with ID " + id + " is already registered.");
    }
}
