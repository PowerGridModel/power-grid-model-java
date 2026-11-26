package org.lfenergy.pgm;

import static org.lfenergy.pgm.CalculationMethod.ITERATIVE_LINEAR;
import static org.lfenergy.pgm.CalculationMethod.NEWTON_RAPHSON;

/**
 * Solver method and convergence settings for a calculation.
 *
 * @param method calculation algorithm
 * @param errorTolerance voltage deviation tolerance in per unit
 * @param maxIterations maximum solver iterations
 * @param continueOnBatchError when {@code true}, an update that fails is returned as a
 *     {@link org.lfenergy.pgm.output.SymOutput.Failure}; when {@code false}, any failing update makes the
 *     calculation throw a {@link BatchCalculationException}
 */
public record CalculationOptions(
    CalculationMethod method,
    double errorTolerance,
    int maxIterations,
    boolean continueOnBatchError
) {
    public CalculationOptions {
        if (!Double.isFinite(errorTolerance) || errorTolerance <= 0.0) {
            throw new IllegalArgumentException("Error tolerance must be finite and greater than zero");
        }
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("Maximum iterations must be greater than zero");
        }
    }

    /** Creates options that stop the calculation when any update fails. */
    public CalculationOptions(
        final CalculationMethod method,
        final double errorTolerance,
        final int maxIterations
    ) {
        this(method, errorTolerance, maxIterations, false);
    }

    /** Newton-Raphson; a failing update is returned as a failure and does not stop the others. */
    public static CalculationOptions powerFlowDefaults() {
        return new CalculationOptions(
            NEWTON_RAPHSON,
            1e-8,
            20,
            true
        );
    }

    /** Iterative-linear; a failing update is returned as a failure and does not stop the others. */
    public static CalculationOptions stateEstimationDefaults() {
        return new CalculationOptions(
            ITERATIVE_LINEAR,
            1e-8,
            20,
            true
        );
    }
}
