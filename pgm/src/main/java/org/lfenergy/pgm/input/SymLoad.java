package org.lfenergy.pgm.input;

import org.jspecify.annotations.Nullable;

/**
 * A load connected to a node; active and reactive power are in watts and vars.
 *
 * @param pSpecified {@code null} when every update supplies it as a setpoint, or for state estimation only.
 *                   Power flow requires it here or as a setpoint
 * @param qSpecified {@code null} under the same conditions as {@code pSpecified}
 */
public record SymLoad(
    int id,
    int node,
    LoadGenType type,
    @Nullable Double pSpecified,
    @Nullable Double qSpecified,
    boolean status
) {
}
