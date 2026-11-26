package org.lfenergy.pgm;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.lfenergy.pgm.input.Input;
import org.lfenergy.pgm.input.LoadGenType;
import org.lfenergy.pgm.input.MeasuredTerminalType;
import org.lfenergy.pgm.input.Source;
import org.lfenergy.pgm.input.SymLoad;
import org.lfenergy.pgm.output.ApplianceOutput;
import org.lfenergy.pgm.output.ApplianceType;
import org.lfenergy.pgm.output.Output;
import org.lfenergy.pgm.output.SymOutput;
import org.lfenergy.pgm.output.SymPowerSensorOutput;
import org.lfenergy.pgm.output.SymVoltageSensorOutput;
import org.lfenergy.pgm.update.Measurement.SymPowerSensorMeasurement;
import org.lfenergy.pgm.update.Updates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lfenergy.pgm.fixtures.InputFixtures.constantPowerLoad;
import static org.lfenergy.pgm.fixtures.InputFixtures.input;
import static org.lfenergy.pgm.fixtures.InputFixtures.line;
import static org.lfenergy.pgm.fixtures.InputFixtures.node;
import static org.lfenergy.pgm.fixtures.InputFixtures.powerSensor;
import static org.lfenergy.pgm.fixtures.InputFixtures.source;
import static org.lfenergy.pgm.fixtures.InputFixtures.voltageSensor;
import static org.lfenergy.pgm.fixtures.OutputLookup.applianceOf;
import static org.lfenergy.pgm.fixtures.OutputLookup.outputOf;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.loadSetpoint;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.measurements;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.powerMeasurement;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.setpoints;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.sourceSetpoint;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.update;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.updates;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.voltageMeasurement;

class PowerGridModelTest {

    private static final int NODE = 1;
    private static final int LOAD = 2;
    private static final int SOURCE = 3;
    private static final int VOLTAGE_SENSOR = 4;
    private static final int POWER_SENSOR = 5;

    private static final Instant FIRST = Instant.parse("2026-10-12T10:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-10-12T10:05:00Z");

    private static Input singleNodeInputWithSensors() {

        return input()
            .with(node(NODE, 400))
            .with(source(SOURCE, NODE, 1e10))
            .with(constantPowerLoad(LOAD, NODE))
            .with(voltageSensor(VOLTAGE_SENSOR, NODE))
            .with(powerSensor(POWER_SENSOR, LOAD, MeasuredTerminalType.LOAD))
            .build();
    }

    private static Input singleNodeInputWithoutSensors() {

        return input()
            .with(node(NODE, 400))
            .with(source(SOURCE, NODE, 1e10))
            .with(constantPowerLoad(LOAD, NODE))
            .build();
    }

    @Test
    void calculatesPowerFlowForEveryTimestampedUpdate() {
        // Given an input that is created once and two updates with a different load
        final Input input = singleNodeInputWithSensors();
        final double firstActivePower = 1_000;
        final double secondActivePower = 2_000;
        final double reactivePower = 200;
        final Updates updates = updates(
            update(FIRST,
                measurements(
                    voltageMeasurement(VOLTAGE_SENSOR, 1, 400, 0.0),
                    powerMeasurement(POWER_SENSOR, firstActivePower, reactivePower, 10, 10)),
                setpoints(loadSetpoint(LOAD, firstActivePower, reactivePower))),
            update(SECOND,
                measurements(
                    voltageMeasurement(VOLTAGE_SENSOR, 1, 400, 0.0),
                    powerMeasurement(POWER_SENSOR, secondActivePower, reactivePower, 10, 10)),
                setpoints(loadSetpoint(LOAD, secondActivePower, reactivePower))));

        // When the power flow is calculated
        final List<SymOutput> outputs;
        try (PowerGridModel model = new PowerGridModel(input)) {
            outputs = model.calculatePowerFlow(updates);
        }

        // Then there is one output per update, in order, carrying the timestamp
        assertEquals(List.of(FIRST, SECOND), outputs.stream().map(SymOutput::timestamp).toList());

        // And the load reflects the values of its own update
        final ApplianceOutput firstLoad = applianceOf(outputOf(outputs.get(0)), ApplianceType.SYM_LOAD);
        assertEquals(LOAD, firstLoad.id());
        assertEquals(firstActivePower, firstLoad.p(), 1);
        assertEquals(reactivePower, firstLoad.q(), 1);
        assertEquals(firstActivePower / Math.hypot(firstActivePower, reactivePower), firstLoad.pf(), 1e-3);

        final ApplianceOutput secondLoad = applianceOf(outputOf(outputs.get(1)), ApplianceType.SYM_LOAD);
        assertEquals(secondActivePower, secondLoad.p(), 1);
        assertEquals(reactivePower, secondLoad.q(), 1);
        assertEquals(secondActivePower / Math.hypot(secondActivePower, reactivePower), secondLoad.pf(), 1e-3);

        // And the current of the load matches its apparent power at the node voltage
        final double voltage = outputOf(outputs.get(0)).nodes().getFirst().u();
        assertEquals(Math.hypot(firstActivePower, reactivePower) / (Math.sqrt(3) * voltage), firstLoad.i(), 1e-3);

        // And power flow reports no sensor residuals, although the input has sensors
        for (final SymOutput symOutput : outputs) {
            assertTrue(outputOf(symOutput).symVoltageSensors().isEmpty());
            assertTrue(outputOf(symOutput).symPowerSensors().isEmpty());
        }
    }

    @Test
    void appliesSetpointsByIdRegardlessOfTheirOrderInAnUpdate() {
        // Given two loads on one node and two updates that list the same setpoints in another order
        final int otherLoad = 6;
        final Input input = input()
            .with(node(NODE, 400))
            .with(source(SOURCE, NODE, 1e10))
            .with(constantPowerLoad(LOAD, NODE))
            .with(constantPowerLoad(otherLoad, NODE))
            .build();
        final Updates updates = updates(
            update(FIRST,
                measurements(),
                setpoints(
                    loadSetpoint(LOAD, 1_000, 100),
                    loadSetpoint(otherLoad, 3_000, 300))),
            update(SECOND,
                measurements(),
                setpoints(
                    loadSetpoint(otherLoad, 3_000, 300),
                    loadSetpoint(LOAD, 1_000, 100))));

        // When the power flow is calculated
        final List<SymOutput> outputs;
        try (PowerGridModel model = new PowerGridModel(input)) {
            outputs = model.calculatePowerFlow(updates);
        }

        // Then each load draws its own setpoint in both updates
        for (final SymOutput symOutput : outputs) {
            final List<ApplianceOutput> loads = outputOf(symOutput).appliances().stream()
                .filter(a -> a.type() == ApplianceType.SYM_LOAD)
                .toList();
            assertEquals(1_000, loads.stream().filter(a -> a.id() == LOAD).findFirst().orElseThrow()
                .p(), 1);
            assertEquals(3_000, loads.stream().filter(a -> a.id() == otherLoad).findFirst().orElseThrow()
                .p(), 1);
        }
    }

    @Test
    void calculatesStateEstimationForEveryTimestampedUpdate() {
        // Given an input with sensors and two updates with a different measured power
        final Input input = singleNodeInputWithSensors();
        final double firstActivePower = 1_000;
        final double secondActivePower = 2_000;
        final double reactivePower = 200;
        final Updates updates = updates(
            update(FIRST,
                measurements(
                    voltageMeasurement(VOLTAGE_SENSOR, 1, 400, 0.0),
                    powerMeasurement(POWER_SENSOR, firstActivePower, reactivePower, 10, 10)),
                setpoints()),
            update(SECOND,
                measurements(
                    voltageMeasurement(VOLTAGE_SENSOR, 1, 400, 0.0),
                    powerMeasurement(POWER_SENSOR, secondActivePower, reactivePower, 10, 10)),
                setpoints()));

        // When the state is estimated
        final List<SymOutput> outputs;
        try (PowerGridModel model = new PowerGridModel(input)) {
            outputs = model.calculateStateEstimation(updates);
        }

        // Then the estimated load follows the measurement of each update
        assertEquals(List.of(FIRST, SECOND), outputs.stream().map(SymOutput::timestamp).toList());
        assertEquals(firstActivePower,
            applianceOf(outputOf(outputs.get(0)), ApplianceType.SYM_LOAD).p(), 1);
        assertEquals(secondActivePower,
            applianceOf(outputOf(outputs.get(1)), ApplianceType.SYM_LOAD).p(), 1);

        // And the residuals of the sensors are known
        for (final SymOutput symOutput : outputs) {
            final Output output = outputOf(symOutput);
            assertTrue(Double.isFinite(output.symVoltageSensors().getFirst().uResidual()));
            assertTrue(Double.isFinite(output.symPowerSensors().getFirst().pResidual()));
        }
    }

    @Test
    void estimatesStateWithTheMeasuredVoltageAngle() {
        final Input input = singleNodeInputWithSensors();
        final double activePower = 1_000;
        final double reactivePower = 200;
        final Updates updates = updates(
            update(FIRST,
                measurements(
                    voltageMeasurement(VOLTAGE_SENSOR, 0.01, 400, 0.0),
                    powerMeasurement(POWER_SENSOR, activePower, reactivePower, 10, 10)),
                setpoints()),
            update(SECOND,
                measurements(
                    voltageMeasurement(VOLTAGE_SENSOR, 0.01, 400, 0.5),
                    powerMeasurement(POWER_SENSOR, activePower, reactivePower, 10, 10)),
                setpoints()));

        final List<SymOutput> outputs;
        try (PowerGridModel model = new PowerGridModel(input)) {
            outputs = model.calculateStateEstimation(updates);
        }

        final double firstAngle = outputOf(outputs.get(0)).nodes().getFirst().uAngle();
        final double secondAngle = outputOf(outputs.get(1)).nodes().getFirst().uAngle();
        assertEquals(0.0, firstAngle, 1e-3);
        assertEquals(0.5, secondAngle, 1e-3);
    }

    @Test
    void returnsOutputsThatCannotBeModified() {
        final Input input = singleNodeInputWithoutSensors();
        final Updates updates = updates(update(FIRST, measurements(), setpoints(loadSetpoint(LOAD, 1_000, 200))));

        final List<SymOutput> outputs;
        try (PowerGridModel model = new PowerGridModel(input)) {
            outputs = model.calculatePowerFlow(updates);
        }

        assertThrows(UnsupportedOperationException.class, outputs::clear);
    }

    @Test
    void calculatesPowerFlowForUpdateWithoutMeasurementsOrSetpoints() {
        final Input input = singleNodeInputWithSensors();
        final Updates updates = updates(update(FIRST, measurements(), setpoints()));

        final List<SymOutput> outputs;
        try (PowerGridModel model = new PowerGridModel(input)) {
            outputs = model.calculatePowerFlow(updates);
        }

        assertEquals(1, outputs.size());
        assertEquals(400, outputOf(outputs.getFirst()).nodes().getFirst().u(), 1);
    }

    @Test
    void calculatesPowerFlowWithoutSensorsInInput() {
        final Input input = singleNodeInputWithoutSensors();
        final Updates updates = updates(update(FIRST, measurements(), setpoints(loadSetpoint(LOAD, 1_000, 200))));

        final List<SymOutput> outputs;
        try (PowerGridModel model = new PowerGridModel(input)) {
            outputs = model.calculatePowerFlow(updates);
        }

        final Output output = outputOf(outputs.getFirst());
        assertEquals(400, output.nodes().getFirst().u(), 1);
        assertTrue(output.symVoltageSensors().isEmpty());
        assertTrue(output.symPowerSensors().isEmpty());
    }

    @Test
    void rejectsStateEstimationWithoutSensorsInInput() {
        final Input input = singleNodeInputWithoutSensors();
        final Updates updates = updates(
            update(FIRST, measurements(voltageMeasurement(VOLTAGE_SENSOR, 1, 400, 0.0)), setpoints()));

        try (PowerGridModel model = new PowerGridModel(input)) {
            assertThrows(IllegalArgumentException.class, () -> model.calculateStateEstimation(updates));
        }
    }

    @Test
    void rejectsStateEstimationWithoutPowerSensorInInput() {
        final Input input = input()
            .with(node(NODE, 400))
            .with(source(SOURCE, NODE, 1e10))
            .with(voltageSensor(VOLTAGE_SENSOR, NODE))
            .build();
        final Updates updates = updates(
            update(FIRST, measurements(voltageMeasurement(VOLTAGE_SENSOR, 1, 400, 0.0)), setpoints()));

        try (PowerGridModel model = new PowerGridModel(input)) {
            assertThrows(IllegalArgumentException.class, () -> model.calculateStateEstimation(updates));
        }
    }

    @Test
    void rejectsStateEstimationWithoutSensorMeasurements() {
        final Input input = singleNodeInputWithSensors();
        final Updates updates = updates(update(FIRST, measurements(), setpoints(loadSetpoint(LOAD, 1_000, 200))));

        try (PowerGridModel model = new PowerGridModel(input)) {
            assertThrows(IllegalArgumentException.class, () -> model.calculateStateEstimation(updates));
        }
    }

    @Test
    void rejectsCalculationAfterClose() {
        final PowerGridModel model = new PowerGridModel(singleNodeInputWithoutSensors());
        model.close();
        model.close();
        final Updates updates = updates(update(FIRST, measurements(), setpoints(loadSetpoint(LOAD, 1_000, 200))));

        assertThrows(IllegalStateException.class, () -> model.calculatePowerFlow(updates));
    }

    @Test
    void rejectsNullArguments() {
        final Updates updates = updates(update(FIRST, measurements(), setpoints(loadSetpoint(LOAD, 1_000, 200))));
        final Input input = singleNodeInputWithSensors();

        assertThrows(NullPointerException.class, () -> new PowerGridModel(null));
        try (PowerGridModel model = new PowerGridModel(input)) {
            assertThrows(NullPointerException.class, () -> model.calculatePowerFlow(null));
            assertThrows(NullPointerException.class, () -> model.calculatePowerFlow(updates, null));
            assertThrows(NullPointerException.class, () -> model.calculateStateEstimation(updates, null));
        }
    }

    @Test
    void rejectsInvalidSystemFrequency() {
        final Input input = singleNodeInputWithoutSensors();

        assertThrows(IllegalArgumentException.class, () -> new PowerGridModel(input, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new PowerGridModel(input, -50.0));
        assertThrows(IllegalArgumentException.class, () -> new PowerGridModel(input, Double.NaN));
    }

    @Test
    void acceptsAnExplicitSystemFrequency() {
        final Input input = singleNodeInputWithoutSensors();
        final Updates updates = updates(update(FIRST, measurements(), setpoints(loadSetpoint(LOAD, 1_000, 200))));

        final List<SymOutput> outputs;
        try (PowerGridModel model = new PowerGridModel(input, 60.0)) {
            outputs = model.calculatePowerFlow(updates);
        }

        assertFalse(outputs.isEmpty());
    }

    @Test
    void rejectsMethodsThatDoNotFitTheCalculation() {
        final Input input = singleNodeInputWithSensors();
        final Updates updates = updates(update(FIRST,
            measurements(
                voltageMeasurement(VOLTAGE_SENSOR, 1, 400, 0.0),
                powerMeasurement(POWER_SENSOR, 1_000, 200, 10, 10)),
            setpoints()));
        final CalculationOptions iterativeLinear = new CalculationOptions(CalculationMethod.ITERATIVE_LINEAR, 1e-8, 20);
        final CalculationOptions linear = new CalculationOptions(CalculationMethod.LINEAR, 1e-8, 20);

        try (PowerGridModel model = new PowerGridModel(input)) {
            assertThrows(IllegalArgumentException.class, () -> model.calculatePowerFlow(updates, iterativeLinear));
            assertThrows(IllegalArgumentException.class, () -> model.calculateStateEstimation(updates, linear));
        }
    }

    @Test
    void reportsTheNativeErrorWhenTheInputIsInconsistent() {
        final Input input = input()
            .with(node(NODE, 10_500))
            .with(line(10, NODE, 99, 0.1, 0.1, 1e-7, 500))
            .build();

        assertThrows(IllegalStateException.class, () -> new PowerGridModel(input));
    }

    @Test
    void calculatesPowerFlowWithLoadPowerAndReferenceVoltageOnlyAsSetpoints() {
        final Input input = input()
            .with(node(NODE, 400))
            .with(new Source(SOURCE, NODE, null, null, true))
            .with(new SymLoad(LOAD, NODE, LoadGenType.CONSTANT_POWER, null, null, true))
            .build();
        final Updates updates = updates(update(FIRST,
            measurements(),
            setpoints(sourceSetpoint(SOURCE, 1.05), loadSetpoint(LOAD, 1_000, 200))));

        final List<SymOutput> outputs;
        try (PowerGridModel model = new PowerGridModel(input)) {
            outputs = model.calculatePowerFlow(updates);
        }

        final Output output = outputOf(outputs.getFirst());
        assertEquals(1.05, output.nodes().getFirst().uPu(), 1e-3);
        assertEquals(1_000, applianceOf(output, ApplianceType.SYM_LOAD).p(), 1);
    }

    @Test
    void rejectsPowerFlowWithoutLoadPower() {
        final Input input = input()
            .with(node(NODE, 400))
            .with(source(SOURCE, NODE, 1e10))
            .with(new SymLoad(LOAD, NODE, LoadGenType.CONSTANT_POWER, null, null, true))
            .build();
        final Updates updates = updates(update(FIRST, measurements(), setpoints()));

        try (PowerGridModel model = new PowerGridModel(input)) {
            assertThrows(IllegalArgumentException.class, () -> model.calculatePowerFlow(updates));
        }
    }

    @Test
    void rejectsPowerFlowWithoutReferenceVoltage() {
        final Input input = input()
            .with(node(NODE, 400))
            .with(new Source(SOURCE, NODE, null, 1e10, true))
            .with(constantPowerLoad(LOAD, NODE))
            .build();
        final Updates updates = updates(update(FIRST, measurements(), setpoints(loadSetpoint(LOAD, 1_000, 200))));

        try (PowerGridModel model = new PowerGridModel(input)) {
            assertThrows(IllegalArgumentException.class, () -> model.calculatePowerFlow(updates));
        }
    }

    @Test
    void estimatesStateWithoutLoadPowerInTheInput() {
        final Input input = input()
            .with(node(NODE, 400))
            .with(source(SOURCE, NODE, 1e10))
            .with(new SymLoad(LOAD, NODE, LoadGenType.CONSTANT_POWER, null, null, true))
            .with(voltageSensor(VOLTAGE_SENSOR, NODE))
            .with(powerSensor(POWER_SENSOR, LOAD, MeasuredTerminalType.LOAD))
            .build();
        final Updates updates = updates(update(FIRST,
            measurements(
                voltageMeasurement(VOLTAGE_SENSOR, 1, 400, 0.0),
                powerMeasurement(POWER_SENSOR, 1_000, 200, 10, 10)),
            setpoints()));

        final List<SymOutput> outputs;
        try (PowerGridModel model = new PowerGridModel(input)) {
            outputs = model.calculateStateEstimation(updates);
        }

        assertEquals(400, outputOf(outputs.getFirst()).nodes().getFirst().u(), 1);
    }

    @Test
    void returnsNoResidualForValuesThatWereNotMeasured() {
        final Input input = singleNodeInputWithSensors();
        final Updates updates = updates(update(FIRST,
            measurements(
                voltageMeasurement(VOLTAGE_SENSOR, 1, 400, 0.0),
                new SymPowerSensorMeasurement(POWER_SENSOR, null, 200.0, 10, 10)),
            setpoints()));

        final List<SymOutput> outputs;
        try (PowerGridModel model = new PowerGridModel(input)) {
            outputs = model.calculateStateEstimation(updates);
        }

        final SymPowerSensorOutput power = outputOf(outputs.getFirst()).symPowerSensors().getFirst();
        assertNull(power.pResidual());
        assertNotNull(power.qResidual());
    }

    @Test
    void returnsNoAngleResidualForAMagnitudeOnlyVoltageMeasurement() {
        final Input input = singleNodeInputWithSensors();
        final Updates updates = updates(update(FIRST,
            measurements(
                voltageMeasurement(VOLTAGE_SENSOR, 1, 400),
                powerMeasurement(POWER_SENSOR, 1_000, 200, 10, 10)),
            setpoints()));

        final List<SymOutput> outputs;
        try (PowerGridModel model = new PowerGridModel(input)) {
            outputs = model.calculateStateEstimation(updates);
        }

        final SymVoltageSensorOutput voltage = outputOf(outputs.getFirst()).symVoltageSensors().getFirst();
        assertNotNull(voltage.uResidual());
        assertNull(voltage.uAngleResidual());
    }

    @Test
    void rejectsNaNInTheInput() {
        final Input input = input()
            .with(node(NODE, Double.NaN))
            .with(source(SOURCE, NODE, 1e10))
            .build();

        assertThrows(IllegalArgumentException.class, () -> new PowerGridModel(input));
    }

    @Test
    void rejectsNaNInMeasurements() {
        final Input input = singleNodeInputWithSensors();
        final Updates nanStandardDeviation = updates(update(FIRST,
            measurements(
                voltageMeasurement(VOLTAGE_SENSOR, Double.NaN, 400),
                powerMeasurement(POWER_SENSOR, 1_000, 200, 10, 10)),
            setpoints()));
        final Updates nanAngle = updates(update(FIRST,
            measurements(
                voltageMeasurement(VOLTAGE_SENSOR, 1, 400, Double.NaN),
                powerMeasurement(POWER_SENSOR, 1_000, 200, 10, 10)),
            setpoints()));

        try (PowerGridModel model = new PowerGridModel(input)) {
            assertThrows(IllegalArgumentException.class, () -> model.calculateStateEstimation(nanStandardDeviation));
            assertThrows(IllegalArgumentException.class, () -> model.calculateStateEstimation(nanAngle));
        }
    }
}
