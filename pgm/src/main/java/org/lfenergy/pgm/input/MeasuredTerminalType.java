package org.lfenergy.pgm.input;

/** The measured terminal, using PGM's from/to sign convention. */
public enum MeasuredTerminalType {
    BRANCH_FROM(0),
    BRANCH_TO(1),
    SOURCE(2),
    LOAD(4);

    private final int nativeValue;

    MeasuredTerminalType(int nativeValue) {
        this.nativeValue = nativeValue;
    }

    public int nativeValue() {
        return nativeValue;
    }
}
