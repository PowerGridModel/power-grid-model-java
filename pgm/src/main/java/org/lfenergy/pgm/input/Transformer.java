package org.lfenergy.pgm.input;

import org.jspecify.annotations.Nullable;

/**
 * Symmetric two-winding transformer, with impedance and ratings in SI units.
 *
 * @param tapPos {@code null} for the nominal tap position
 * @param tapNom {@code null} for PGM's default of 0
 */
public record Transformer(
    int id,
    int fromNode,
    int toNode,
    int windingFrom,
    int windingTo,
    double sn,
    double u1,
    double u2,
    double uk,
    double pk,
    double p0,
    double i0,
    int clock,
    int tapSide,
    @Nullable Integer tapPos,
    @Nullable Integer tapNom,
    int tapMin,
    int tapMax,
    double tapSize,
    boolean fromStatus,
    boolean toStatus
) {
}
