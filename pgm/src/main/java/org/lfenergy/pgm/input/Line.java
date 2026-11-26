package org.lfenergy.pgm.input;

import org.jspecify.annotations.Nullable;

/**
 * A symmetric line; impedance is in ohms, capacitance in farads, and current in amperes.
 *
 * @param iN rated current; {@code null} when unknown
 */
public record Line(
    int id,
    int fromNode,
    int toNode,
    double r1,
    double x1,
    double c1,
    double tan1,
    @Nullable Double iN,
    boolean fromStatus,
    boolean toStatus
) {
}
