package org.lfenergy.pgm.output;

import java.util.Objects;

/**
 * Power flow at one side of a line or transformer. A branch has two outputs, one per {@link TerminalSide}.
 *
 * @param type      kind of branch
 * @param id        branch ID
 * @param side      terminal the values belong to
 * @param energized whether the branch is energized
 * @param p         active power flowing into the branch at this side, in W
 * @param q         reactive power flowing into the branch at this side, in var
 * @param i         current at this side, in A
 * @param pf        absolute power factor at this side, derived from active and reactive power
 */
public record BranchOutput(
    BranchType type,
    int id,
    TerminalSide side,
    boolean energized,
    double p,
    double q,
    double i,
    double pf
) {
    public BranchOutput {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(side, "side");
    }
}
