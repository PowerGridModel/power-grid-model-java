package org.lfenergy.pgm.input;

/** A network bus, with rated line-to-line voltage in volts. */
public record Node(int id, double uRated) {
}
