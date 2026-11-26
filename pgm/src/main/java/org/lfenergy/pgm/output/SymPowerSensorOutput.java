package org.lfenergy.pgm.output;

import org.jspecify.annotations.Nullable;

/**
 * Deviation between measured and calculated power at a power sensor.
 *
 * @param pResidual {@code null} when the active power was not measured in this update
 * @param qResidual {@code null} when the reactive power was not measured in this update
 */
public record SymPowerSensorOutput(
    int id,
    boolean energized,
    @Nullable Double pResidual,
    @Nullable Double qResidual
) {
}
