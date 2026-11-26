package org.lfenergy.pgm.input;

/** Definition of a power sensor on a branch or appliance terminal; measured values are supplied per update as measurements. */
public record SymPowerSensor(
    int id,
    int measuredObject,
    MeasuredTerminalType measuredTerminalType
) { }
