package org.lfenergy.pgm.input;

/** Load behavior supported by PGM. */
public enum LoadGenType {
    CONSTANT_POWER(0),
    CONSTANT_IMPEDANCE(1),
    CONSTANT_CURRENT(2);

    private final int nativeValue;

    LoadGenType(int nativeValue) {
        this.nativeValue = nativeValue;
    }

    public int nativeValue() {
        return nativeValue;
    }
}
