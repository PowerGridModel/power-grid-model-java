package org.lfenergy.pgm.input;

/** Definition of a voltage sensor on a node; measured values are supplied per update as measurements. */
public record SymVoltageSensor(
    int id,
    int measuredObject
) { }
