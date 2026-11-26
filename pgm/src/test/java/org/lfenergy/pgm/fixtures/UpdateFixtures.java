package org.lfenergy.pgm.fixtures;

import java.time.Instant;
import java.util.List;

import org.lfenergy.pgm.update.Measurement;
import org.lfenergy.pgm.update.Measurement.SymPowerSensorMeasurement;
import org.lfenergy.pgm.update.Measurement.SymVoltageSensorMeasurement;
import org.lfenergy.pgm.update.Setpoint;
import org.lfenergy.pgm.update.Setpoint.SourceSetpoint;
import org.lfenergy.pgm.update.Setpoint.SymLoadSetpoint;
import org.lfenergy.pgm.update.Update;
import org.lfenergy.pgm.update.Updates;

public final class UpdateFixtures {

    private UpdateFixtures() { }

    public static SourceSetpoint sourceSetpoint(final int id, final double uRef) {

        return new SourceSetpoint(id, uRef);
    }

    public static SymLoadSetpoint loadSetpoint(final int id, final double pSpecified, final double qSpecified) {

        return new SymLoadSetpoint(id, pSpecified, qSpecified);
    }

    public static SymVoltageSensorMeasurement voltageMeasurement(
        final int id,
        final double uSigma,
        final double uMeasured,
        final double uAngleMeasured
    ) {

        return new SymVoltageSensorMeasurement(id, uSigma, uMeasured, uAngleMeasured);
    }

    public static SymVoltageSensorMeasurement voltageMeasurement(
        final int id,
        final double uSigma,
        final double uMeasured
    ) {

        return new SymVoltageSensorMeasurement(id, uSigma, uMeasured, null);
    }

    public static SymPowerSensorMeasurement powerMeasurement(
        final int id,
        final double pMeasured,
        final double qMeasured,
        final double pSigma,
        final double qSigma
    ) {

        return new SymPowerSensorMeasurement(id, pMeasured, qMeasured, pSigma, qSigma);
    }

    public static List<Measurement> measurements(final Measurement... measurements) {

        return List.of(measurements);
    }

    public static List<Setpoint> setpoints(final Setpoint... setpoints) {

        return List.of(setpoints);
    }

    public static Update update(
        final Instant timestamp,
        final List<Measurement> measurements,
        final List<Setpoint> setpoints
    ) {

        return new Update(timestamp, measurements, setpoints);
    }

    public static Updates updates(final Update... updates) {

        return new Updates(List.of(updates));
    }
}
