package org.lfenergy.pgm.output;

/** Estimated or calculated electrical state and power injection at a node. */
public record NodeOutput(
    int id,
    boolean energized,
    double u,
    double uPu,
    double uAngle,
    double p,
    double q
) {
}
