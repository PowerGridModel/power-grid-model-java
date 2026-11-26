package org.lfenergy.pgm.output;

import java.util.Objects;

/**
 * Power flow at a source or load. An appliance has a single connection, so it has no side.
 *
 * @param type      kind of appliance
 * @param id        appliance ID
 * @param energized whether the appliance is energized
 * @param p         active power, in W
 * @param q         reactive power, in var
 * @param i         current, in A
 * @param pf        power factor as calculated by PGM
 */
public record ApplianceOutput(
    ApplianceType type,
    int id,
    boolean energized,
    double p,
    double q,
    double i,
    double pf
) {
    public ApplianceOutput {
        Objects.requireNonNull(type, "type");
    }
}
