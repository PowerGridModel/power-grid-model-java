package org.lfenergy.pgm;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.lfenergy.pgm.input.Input;
import org.lfenergy.pgm.input.Line;
import org.lfenergy.pgm.input.MeasuredTerminalType;
import org.lfenergy.pgm.input.Source;
import org.lfenergy.pgm.input.Transformer;
import org.lfenergy.pgm.output.ApplianceOutput;
import org.lfenergy.pgm.output.ApplianceType;
import org.lfenergy.pgm.output.BranchOutput;
import org.lfenergy.pgm.output.BranchType;
import org.lfenergy.pgm.output.Output;
import org.lfenergy.pgm.output.SymOutput;
import org.lfenergy.pgm.output.TerminalSide;
import org.lfenergy.pgm.update.Updates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lfenergy.pgm.fixtures.InputFixtures.constantPowerLoad;
import static org.lfenergy.pgm.fixtures.InputFixtures.input;
import static org.lfenergy.pgm.fixtures.InputFixtures.line;
import static org.lfenergy.pgm.fixtures.InputFixtures.node;
import static org.lfenergy.pgm.fixtures.InputFixtures.powerSensor;
import static org.lfenergy.pgm.fixtures.InputFixtures.source;
import static org.lfenergy.pgm.fixtures.InputFixtures.transformer;
import static org.lfenergy.pgm.fixtures.InputFixtures.voltageSensor;
import static org.lfenergy.pgm.fixtures.OutputLookup.applianceOf;
import static org.lfenergy.pgm.fixtures.OutputLookup.branchOf;
import static org.lfenergy.pgm.fixtures.OutputLookup.outputOf;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.loadSetpoint;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.measurements;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.powerMeasurement;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.setpoints;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.sourceSetpoint;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.update;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.updates;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.voltageMeasurement;

class PowerGridModelNetworkTest {

    private static final int HV_NODE_SOURCE_SIDE = 1;
    private static final int HV_NODE_TRANSFORMER_SIDE = 2;
    private static final int LV_NODE = 3;
    private static final int LINE = 10;
    private static final int TRANSFORMER = 11;
    private static final int SOURCE = 12;
    private static final int LOAD = 13;
    private static final int LOAD_POWER_SENSOR = 14;
    private static final int LINE_POWER_SENSOR = 15;
    private static final int SOURCE_POWER_SENSOR = 16;
    private static final int HV_VOLTAGE_SENSOR = 17;
    private static final int LV_VOLTAGE_SENSOR = 18;

    private static final Instant FIRST = Instant.parse("2026-10-12T10:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-10-12T10:15:00Z");

    private static Input feeder() {

        return input()
            .with(node(HV_NODE_SOURCE_SIDE, 10_500))
            .with(node(HV_NODE_TRANSFORMER_SIDE, 10_500))
            .with(node(LV_NODE, 400))
            .with(line(LINE, HV_NODE_SOURCE_SIDE, HV_NODE_TRANSFORMER_SIDE, 0.1, 0.1, 1e-7, 500))
            .with(transformer(TRANSFORMER, HV_NODE_TRANSFORMER_SIDE, LV_NODE, 630_000, 10_500, 400, 0.06, 6_000, 1_000, 0.01))
            .with(source(SOURCE, HV_NODE_SOURCE_SIDE, 1e10))
            .with(constantPowerLoad(LOAD, LV_NODE))
            .with(voltageSensor(HV_VOLTAGE_SENSOR, HV_NODE_SOURCE_SIDE))
            .with(voltageSensor(LV_VOLTAGE_SENSOR, LV_NODE))
            .with(powerSensor(LOAD_POWER_SENSOR, LOAD, MeasuredTerminalType.LOAD))
            .with(powerSensor(LINE_POWER_SENSOR, LINE, MeasuredTerminalType.BRANCH_FROM))
            .with(powerSensor(SOURCE_POWER_SENSOR, SOURCE, MeasuredTerminalType.SOURCE))
            .build();
    }

    @Test
    void calculatesPowerFlowOverLineAndTransformer() {
        // Given the feeder and two updates in which the load draws 100 kW and 200 kW
        final Input input = feeder();
        final double firstLoadPower = 100_000;
        final double secondLoadPower = 200_000;
        final double loadReactivePower = 10_000;
        final Updates updates = updates(
            update(FIRST,
                measurements(),
                setpoints(
                    sourceSetpoint(SOURCE, 1.0),
                    loadSetpoint(LOAD, firstLoadPower, loadReactivePower))),
            update(SECOND,
                measurements(),
                setpoints(
                    sourceSetpoint(SOURCE, 1.0),
                    loadSetpoint(LOAD, secondLoadPower, loadReactivePower))));

        // When the power flow is calculated
        final List<SymOutput> outputs;
        try (PowerGridModel model = new PowerGridModel(input)) {
            outputs = model.calculatePowerFlow(updates);
        }

        // Then every node is energized with a voltage
        assertEquals(2, outputs.size());
        for (final SymOutput symOutput : outputs) {
            assertEquals(3, outputOf(symOutput).nodes().size());
            assertTrue(outputOf(symOutput).nodes().stream().allMatch(n -> n.energized() && n.u() > 0));
        }

        // And the load draws what was set in each update
        final Output first = outputOf(outputs.get(0));
        final Output second = outputOf(outputs.get(1));
        assertEquals(firstLoadPower, applianceOf(first, ApplianceType.SYM_LOAD).p(), 1);
        assertEquals(secondLoadPower, applianceOf(second, ApplianceType.SYM_LOAD).p(), 1);

        // And the source supplies the load plus the losses, which all pass through the line
        final double firstSourcePower = applianceOf(first, ApplianceType.SOURCE).p();
        assertTrue(firstSourcePower > firstLoadPower, "the source also supplies the losses");
        assertEquals(firstSourcePower, branchOf(first, BranchType.LINE, TerminalSide.FROM).p(), 1);

        // And power leaves the branches at their to-side
        assertTrue(branchOf(first, BranchType.LINE, TerminalSide.TO).p() < 0);
        assertTrue(branchOf(first, BranchType.TRANSFORMER, TerminalSide.TO).p() < 0);

        // And every power factor is a valid ratio
        for (final BranchOutput branch : first.branches()) {
            assertTrue(branch.energized(), branch.type() + " energized");
            assertTrue(branch.pf() >= 0 && branch.pf() <= 1, branch.type() + " power factor");
        }
        for (final ApplianceOutput appliance : first.appliances()) {
            assertTrue(appliance.energized(), appliance.type() + " energized");
            assertTrue(appliance.pf() >= 0 && appliance.pf() <= 1, appliance.type() + " power factor");
        }

        // And every branch has a from-side and a to-side output, every appliance one output
        assertEquals(List.of(BranchType.LINE, BranchType.LINE, BranchType.TRANSFORMER, BranchType.TRANSFORMER),
            first.branches().stream().map(BranchOutput::type).toList());
        assertEquals(List.of(TerminalSide.FROM, TerminalSide.TO, TerminalSide.FROM, TerminalSide.TO),
            first.branches().stream().map(BranchOutput::side).toList());
        assertEquals(List.of(LINE, LINE, TRANSFORMER, TRANSFORMER), first.branches().stream().map(BranchOutput::id).toList());
        assertEquals(List.of(ApplianceType.SOURCE, ApplianceType.SYM_LOAD),
            first.appliances().stream().map(ApplianceOutput::type).toList());
        assertEquals(List.of(SOURCE, LOAD), first.appliances().stream().map(ApplianceOutput::id).toList());

        // And the current through the line matches its power at the medium voltage
        final BranchOutput lineFrom = branchOf(first, BranchType.LINE, TerminalSide.FROM);
        final double lineVoltage = first.nodes().stream().filter(n -> n.id() == HV_NODE_SOURCE_SIDE).findFirst().orElseThrow().u();
        assertEquals(Math.hypot(lineFrom.p(), lineFrom.q()) / (Math.sqrt(3) * lineVoltage),
            lineFrom.i(), 1e-3 * lineFrom.i());

        // And power flow reports no sensor residuals
        assertTrue(first.symVoltageSensors().isEmpty());
        assertTrue(first.symPowerSensors().isEmpty());
    }

    @Test
    void estimatesStateOverLineAndTransformer() {
        // Given the feeder and two updates in which every sensor measures a different power
        final Input input = feeder();
        final double firstPower = 100_000;
        final double secondPower = 200_000;
        final double reactivePower = 10_000;
        final Updates updates = updates(
            update(FIRST,
                measurements(
                    voltageMeasurement(HV_VOLTAGE_SENSOR, 10, 10_500, 0.0),
                    voltageMeasurement(LV_VOLTAGE_SENSOR, 1, 400),
                    powerMeasurement(LOAD_POWER_SENSOR, firstPower, reactivePower, 100, 100),
                    powerMeasurement(LINE_POWER_SENSOR, firstPower, reactivePower, 100, 100),
                    powerMeasurement(SOURCE_POWER_SENSOR, firstPower, reactivePower, 100, 100)),
                setpoints()),
            update(SECOND,
                measurements(
                    voltageMeasurement(HV_VOLTAGE_SENSOR, 10, 10_500, 0.0),
                    voltageMeasurement(LV_VOLTAGE_SENSOR, 1, 400),
                    powerMeasurement(LOAD_POWER_SENSOR, secondPower, reactivePower, 100, 100),
                    powerMeasurement(LINE_POWER_SENSOR, secondPower, reactivePower, 100, 100),
                    powerMeasurement(SOURCE_POWER_SENSOR, secondPower, reactivePower, 100, 100)),
                setpoints()));

        // When the state is estimated
        final List<SymOutput> outputs;
        try (PowerGridModel model = new PowerGridModel(input)) {
            outputs = model.calculateStateEstimation(updates);
        }

        // Then the outputs keep the timestamps of the updates
        assertEquals(List.of(FIRST, SECOND), outputs.stream().map(SymOutput::timestamp).toList());

        // And every update has estimated nodes and a residual for each sensor
        for (final SymOutput symOutput : outputs) {
            final Output output = outputOf(symOutput);
            assertTrue(output.nodes().stream().allMatch(n -> Double.isFinite(n.u())));
            assertEquals(2, output.symVoltageSensors().size());
            assertEquals(3, output.symPowerSensors().size());
        }

        // And the estimated load power moves with the measured power
        final double estimatedFirst = applianceOf(outputOf(outputs.get(0)), ApplianceType.SYM_LOAD).p();
        final double estimatedSecond = applianceOf(outputOf(outputs.get(1)), ApplianceType.SYM_LOAD).p();
        assertTrue(estimatedSecond > estimatedFirst);
    }

    @Test
    void acceptsExplicitCalculationOptions() {
        // Given updates with sensor values and options that name a method, tolerance and iteration limit
        final Input input = feeder();
        final Updates updates = updates(update(FIRST,
            measurements(
                voltageMeasurement(HV_VOLTAGE_SENSOR, 10, 10_500, 0.0),
                voltageMeasurement(LV_VOLTAGE_SENSOR, 1, 400),
                powerMeasurement(LOAD_POWER_SENSOR, 100_000, 10_000, 100, 100),
                powerMeasurement(LINE_POWER_SENSOR, 100_000, 10_000, 100, 100),
                powerMeasurement(SOURCE_POWER_SENSOR, 100_000, 10_000, 100, 100)),
            setpoints(
                sourceSetpoint(SOURCE, 1.0),
                loadSetpoint(LOAD, 100_000, 10_000))));
        final CalculationOptions linear = new CalculationOptions(CalculationMethod.LINEAR, 1e-6, 5);
        final CalculationOptions newtonRaphson = new CalculationOptions(CalculationMethod.NEWTON_RAPHSON, 1e-6, 20);

        // When both calculations run with those options
        final List<SymOutput> powerFlow;
        final List<SymOutput> stateEstimation;
        try (PowerGridModel model = new PowerGridModel(input, 60.0)) {
            powerFlow = model.calculatePowerFlow(updates, linear);
            stateEstimation = model.calculateStateEstimation(updates, newtonRaphson);
        }

        // Then each returns one output for the one update
        assertEquals(1, powerFlow.size());
        assertEquals(1, stateEstimation.size());
    }

    @Test
    void appliesPgmDefaultsForOptionalInputValues() {
        final Input explicit = feeder();
        final Input defaults = input()
            .with(node(HV_NODE_SOURCE_SIDE, 10_500))
            .with(node(HV_NODE_TRANSFORMER_SIDE, 10_500))
            .with(node(LV_NODE, 400))
            .with(new Line(LINE, HV_NODE_SOURCE_SIDE, HV_NODE_TRANSFORMER_SIDE, 0.1, 0.1, 1e-7, 0.0, null, true, true))
            .with(new Transformer(TRANSFORMER, HV_NODE_TRANSFORMER_SIDE, LV_NODE, 2, 1, 630_000, 10_500, 400, 0.06,
                6_000, 1_000, 0.01, 5, 0, null, null, -5, 5, 250, true, true))
            .with(new Source(SOURCE, HV_NODE_SOURCE_SIDE, 1.0, null, true))
            .with(constantPowerLoad(LOAD, LV_NODE))
            .build();
        final Updates updates = updates(update(FIRST, measurements(), setpoints(loadSetpoint(LOAD, 100_000, 10_000))));

        final Output explicitOutput;
        try (PowerGridModel model = new PowerGridModel(explicit)) {
            explicitOutput = outputOf(model.calculatePowerFlow(updates).getFirst());
        }
        final Output defaultOutput;
        try (PowerGridModel model = new PowerGridModel(defaults)) {
            defaultOutput = outputOf(model.calculatePowerFlow(updates).getFirst());
        }

        for (int i = 0; i < explicitOutput.nodes().size(); i++) {
            assertEquals(explicitOutput.nodes().get(i).u(), defaultOutput.nodes().get(i).u(), 1e-6);
        }
    }

    /** An update whose load is far beyond what the feeder can carry, so the power flow cannot converge. */
    private static Updates oneFeasibleAndOneInfeasibleUpdate() {

        return updates(
            update(FIRST, measurements(), setpoints(sourceSetpoint(SOURCE, 1.0), loadSetpoint(LOAD, 100_000, 10_000))),
            update(SECOND, measurements(), setpoints(sourceSetpoint(SOURCE, 1.0), loadSetpoint(LOAD, 1e12, 1e11))));
    }

    @Test
    void returnsAFailureForAnUpdateThatCannotBeCalculatedByDefault() {
        // Given one update that can be calculated and one that cannot
        final Updates updates = oneFeasibleAndOneInfeasibleUpdate();

        // When the power flow runs with the default options
        final List<SymOutput> outputs;
        try (PowerGridModel model = new PowerGridModel(feeder())) {
            outputs = model.calculatePowerFlow(updates);
        }

        // Then the first update succeeds and the second fails with PGM's message, both keeping their timestamp
        assertEquals(List.of(FIRST, SECOND), outputs.stream().map(SymOutput::timestamp).toList());
        assertEquals(100_000, applianceOf(outputOf(outputs.get(0)), ApplianceType.SYM_LOAD).p(), 1);
        final SymOutput.Failure failure = assertInstanceOf(SymOutput.Failure.class, outputs.get(1));
        assertTrue(!failure.error().isBlank());
    }

    @Test
    void throwsForAnUpdateThatCannotBeCalculatedWhenStoppingOnBatchErrors() {
        // Given one update that can be calculated and one that cannot
        final Updates updates = oneFeasibleAndOneInfeasibleUpdate();
        final CalculationOptions options = new CalculationOptions(CalculationMethod.NEWTON_RAPHSON, 1e-8, 20, false);

        // When the power flow stops on batch errors
        final BatchCalculationException exception;
        try (PowerGridModel model = new PowerGridModel(feeder())) {
            exception = assertThrows(BatchCalculationException.class, () -> model.calculatePowerFlow(updates, options));

            // Then the model can still be used afterwards
            final Updates feasible = updates(update(FIRST, measurements(),
                setpoints(sourceSetpoint(SOURCE, 1.0), loadSetpoint(LOAD, 100_000, 10_000))));
            assertEquals(1, model.calculatePowerFlow(feasible).size());
        }

        // And the exception names only the failed update
        assertEquals(1, exception.failedUpdates().size());
        final BatchCalculationException.FailedUpdate failed = exception.failedUpdates().getFirst();
        assertEquals(1, failed.index());
        assertEquals(SECOND, failed.timestamp());
        assertTrue(exception.getMessage().contains(failed.error()));
    }
}
