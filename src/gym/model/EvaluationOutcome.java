package gym.model;

/**
 * The immutable result of grading one member for one cycle.
 * EvaluationService turns an outcome into reward or penalty transactions.
 */
public class EvaluationOutcome {

    /** Ordered best-to-worst. AT_RISK and INACTIVE are the negative events. */
    public enum Status {
        EXCELLENT("Excellent"),
        GOOD("Good"),
        AVERAGE("Average"),
        AT_RISK("At risk"),
        INACTIVE("Inactive");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public boolean isPositive() {
            return this == EXCELLENT || this == GOOD;
        }

        public boolean isNegative() {
            return this == AT_RISK || this == INACTIVE;
        }
    }

    private final Status status;
    private final String message;
    private final double pointsDelta;

    public EvaluationOutcome(Status status, String message, double pointsDelta) {
        this.status = status;
        this.message = message;
        this.pointsDelta = pointsDelta;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    /** Positive to reward, negative to penalise. */
    public double getPointsDelta() {
        return pointsDelta;
    }

    @Override
    public String toString() {
        return status.getLabel() + " -- " + message;
    }
}
