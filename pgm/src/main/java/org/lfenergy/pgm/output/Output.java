package org.lfenergy.pgm.output;

import java.util.List;

/**
 * Immutable PGM node, branch, appliance and sensor calculation outputs. The sensor lists are only filled by
 * state estimation; power flow leaves them empty.
 */
public record Output(
    List<NodeOutput> nodes,
    List<BranchOutput> branches,
    List<ApplianceOutput> appliances,
    List<SymVoltageSensorOutput> symVoltageSensors,
    List<SymPowerSensorOutput> symPowerSensors
) {
    public Output {
        nodes = List.copyOf(nodes);
        branches = List.copyOf(branches);
        appliances = List.copyOf(appliances);
        symVoltageSensors = List.copyOf(symVoltageSensors);
        symPowerSensors = List.copyOf(symPowerSensors);
    }

    public Output(List<NodeOutput> nodes) {
        this(nodes, List.of(), List.of(), List.of(), List.of());
    }
}
