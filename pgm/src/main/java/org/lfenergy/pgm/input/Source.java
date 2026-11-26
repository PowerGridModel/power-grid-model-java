package org.lfenergy.pgm.input;

import org.jspecify.annotations.Nullable;

/**
 * An external-grid source connected to a node.
 *
 * @param uRef reference voltage in per unit; {@code null} when every update supplies it as a setpoint.
 *             Power flow requires it here or as a setpoint
 * @param sk   short-circuit power in volt-amperes; {@code null} for PGM's default of 1e10
 */
public record Source(
    int id,
    int node,
    @Nullable Double uRef,
    @Nullable Double sk,
    boolean status
) {
}
