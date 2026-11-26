package org.lfenergy.pgm.fixtures;

import org.lfenergy.pgm.output.ApplianceOutput;
import org.lfenergy.pgm.output.ApplianceType;
import org.lfenergy.pgm.output.BranchOutput;
import org.lfenergy.pgm.output.BranchType;
import org.lfenergy.pgm.output.Output;
import org.lfenergy.pgm.output.SymOutput;
import org.lfenergy.pgm.output.TerminalSide;

public final class OutputLookup {

    private OutputLookup() { }

    public static Output outputOf(final SymOutput symOutput) {

        if (symOutput instanceof SymOutput.Success(var timestamp, var output)) {
            return output;
        }
        throw new AssertionError("Update failed: " + symOutput);
    }

    public static BranchOutput branchOf(final Output output, final BranchType type, final TerminalSide side) {

        return output.branches().stream()
            .filter(branch -> branch.type() == type && branch.side() == side)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No " + side + " output for " + type));
    }

    public static ApplianceOutput applianceOf(final Output output, final ApplianceType type) {

        return output.appliances().stream()
            .filter(appliance -> appliance.type() == type)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No output for " + type));
    }
}
