package org.lfenergy.pgm.update;

import org.lfenergy.pgm.update.Setpoint.SourceSetpoint;
import org.lfenergy.pgm.update.Setpoint.SymLoadSetpoint;

/** A value imposed on an appliance in one update, without uncertainty; power flow is driven by these. */
public sealed interface Setpoint permits SymLoadSetpoint, SourceSetpoint {

    /** The id of the appliance in the input. */
    int id();

    record SymLoadSetpoint(
        int id,
        double pSpecified,
        double qSpecified
    ) implements Setpoint { }

    record SourceSetpoint(
        int id,
        double uRef
    ) implements Setpoint { }
}
