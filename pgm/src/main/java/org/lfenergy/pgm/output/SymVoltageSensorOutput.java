package org.lfenergy.pgm.output;

import org.jspecify.annotations.Nullable;

/**
 * Deviation between measured and calculated voltage at a voltage sensor.
 *
 * @param uResidual      {@code null} when the voltage was not measured in this update
 * @param uAngleResidual {@code null} when the angle was not measured in this update
 */
public record SymVoltageSensorOutput(
    int id,
    boolean energized,
    @Nullable Double uResidual,
    @Nullable Double uAngleResidual
) {
}
