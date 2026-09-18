package gym.contract;

import gym.model.EvaluationOutcome;

/**
 * ABSTRACTION: anything the periodic review can grade.
 * EvaluationService depends on this, not on the concrete member classes.
 */
public interface Evaluable {

    /** Grade this entity for the current cycle. */
    EvaluationOutcome evaluate();
}
