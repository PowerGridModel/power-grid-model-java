package org.lfenergy.pgm;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.lfenergy.pgm.input.Input;
import org.lfenergy.pgm.input.MeasuredTerminalType;
import org.lfenergy.pgm.output.NodeOutput;
import org.lfenergy.pgm.output.Output;
import org.lfenergy.pgm.update.Updates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lfenergy.pgm.fixtures.InputFixtures.constantPowerLoad;
import static org.lfenergy.pgm.fixtures.InputFixtures.input;
import static org.lfenergy.pgm.fixtures.InputFixtures.line;
import static org.lfenergy.pgm.fixtures.InputFixtures.node;
import static org.lfenergy.pgm.fixtures.InputFixtures.powerSensor;
import static org.lfenergy.pgm.fixtures.InputFixtures.source;
import static org.lfenergy.pgm.fixtures.InputFixtures.voltageSensor;
import static org.lfenergy.pgm.fixtures.OutputLookup.outputOf;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.measurements;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.powerMeasurement;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.setpoints;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.update;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.updates;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.voltageMeasurement;

class PowerGridModelExampleTest {

    @Test
    void threeNodeRingWithMeasurements() {
        // Given a ring of three 10.5 kV nodes, fed by one source and loaded at two nodes
        final Input input = input()
            .with(node(1, 10_500))
            .with(node(2, 10_500))
            .with(node(6, 10_500))
            .with(line(3, 1, 2, 0.25, 0.2, 10e-6, 1_000))
            .with(line(5, 2, 6, 0.25, 0.2, 10e-6, 1_000))
            .with(line(8, 1, 6, 0.25, 0.2, 10e-6, 1_000))
            .with(source(10, 1, 1e9))
            .with(constantPowerLoad(4, 2))
            .with(constantPowerLoad(7, 6))
            .with(voltageSensor(11, 1))
            .with(voltageSensor(12, 2))
            .with(voltageSensor(13, 6))
            .with(powerSensor(14, 3, MeasuredTerminalType.BRANCH_TO))
            .with(powerSensor(15, 5, MeasuredTerminalType.BRANCH_FROM))
            .with(powerSensor(16, 8, MeasuredTerminalType.BRANCH_TO))
            .build();

        // And one update with the values the sensors measured
        final Updates updates = updates(update(Instant.EPOCH,
            measurements(
                voltageMeasurement(11, 1.0, 10_489.37, 0.0),
                voltageMeasurement(12, 1.0, 9_997.32, 0.0),
                voltageMeasurement(13, 1.0, 10_102.01, 0.0),
                powerMeasurement(14, -1.66e7, -3.82e6, 1e3, 1e3),
                powerMeasurement(15, -3.36e6, -1.17e6, 1e3, 1e3),
                powerMeasurement(16, -1.33e7, -2.88e6, 1e3, 1e3)),
            setpoints()));

        // When the state is estimated and the power flow is calculated on the same model
        final Output estimated;
        final Output calculated;
        try (PowerGridModel model = new PowerGridModel(input)) {
            estimated = outputOf(model.calculateStateEstimation(updates).getFirst());
            calculated = outputOf(model.calculatePowerFlow(updates).getFirst());
        }

        // Then both outputs describe the three energized nodes, the first being the source node at 1.0 p.u.
        for (final Output output : List.of(estimated, calculated)) {
            assertEquals(3, output.nodes().size());
            final NodeOutput sourceNode = output.nodes().getFirst();
            assertEquals(1, sourceNode.id());
            assertEquals(1.0, sourceNode.uPu(), 0.05);
            assertTrue(output.nodes().stream().allMatch(NodeOutput::energized));
        }
    }
}
