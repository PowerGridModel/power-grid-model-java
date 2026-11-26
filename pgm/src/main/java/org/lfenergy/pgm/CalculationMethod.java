package org.lfenergy.pgm;

/** Calculation algorithms supported by the Java facade. */
public enum CalculationMethod {
    LINEAR(0),
    NEWTON_RAPHSON(1),
    ITERATIVE_LINEAR(2),
    ITERATIVE_CURRENT(3),
    LINEAR_CURRENT(4);

    private final int nativeValue;

    CalculationMethod(int nativeValue) {
        this.nativeValue = nativeValue;
    }

    int nativeValue() {
        return nativeValue;
    }
}
