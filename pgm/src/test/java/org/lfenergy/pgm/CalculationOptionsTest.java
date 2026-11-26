package org.lfenergy.pgm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculationOptionsTest {

    @Test
    void keepsTheValuesItIsGiven() {
        final CalculationMethod method = CalculationMethod.NEWTON_RAPHSON;
        final double errorTolerance = 1e-6;
        final int maxIterations = 15;

        final CalculationOptions options = new CalculationOptions(method, errorTolerance, maxIterations);

        assertEquals(method, options.method());
        assertEquals(errorTolerance, options.errorTolerance());
        assertEquals(maxIterations, options.maxIterations());
        assertFalse(options.continueOnBatchError());
    }

    @Test
    void keepsTheChoiceToContinueOnBatchErrors() {
        final CalculationOptions options = new CalculationOptions(CalculationMethod.NEWTON_RAPHSON, 1e-6, 15, true);

        assertTrue(options.continueOnBatchError());
    }

    @Test
    void continuesOnBatchErrorsWithTheDefaults() {
        assertTrue(CalculationOptions.powerFlowDefaults().continueOnBatchError());
        assertTrue(CalculationOptions.stateEstimationDefaults().continueOnBatchError());
    }

    @Test
    void offersNewtonRaphsonAsPowerFlowDefault() {
        final CalculationOptions options = CalculationOptions.powerFlowDefaults();

        assertEquals(CalculationMethod.NEWTON_RAPHSON, options.method());
    }

    @Test
    void offersIterativeLinearAsStateEstimationDefault() {
        final CalculationOptions options = CalculationOptions.stateEstimationDefaults();

        assertEquals(CalculationMethod.ITERATIVE_LINEAR, options.method());
    }

    @Test
    void rejectsToleranceThatIsNotPositiveAndFinite() {
        assertThrows(IllegalArgumentException.class, () -> new CalculationOptions(CalculationMethod.NEWTON_RAPHSON, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> new CalculationOptions(CalculationMethod.NEWTON_RAPHSON, -1e-8, 20));
        assertThrows(IllegalArgumentException.class,
            () -> new CalculationOptions(CalculationMethod.NEWTON_RAPHSON, Double.NaN, 20));
    }

    @Test
    void rejectsIterationLimitThatIsNotPositive() {
        assertThrows(IllegalArgumentException.class, () -> new CalculationOptions(CalculationMethod.NEWTON_RAPHSON, 1e-8, 0));
    }
}
