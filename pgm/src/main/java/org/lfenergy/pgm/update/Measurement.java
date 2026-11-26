package org.lfenergy.pgm.update;

import org.jspecify.annotations.Nullable;
import org.lfenergy.pgm.update.Measurement.SymPowerSensorMeasurement;
import org.lfenergy.pgm.update.Measurement.SymVoltageSensorMeasurement;

/**
 * What a sensor observed in one update, with its uncertainty; state estimation is driven by these. A
 * {@code null} measured value means the sensor did not measure it in this update.
 */
public sealed interface Measurement permits SymVoltageSensorMeasurement, SymPowerSensorMeasurement {

    /** The id of the sensor in the input. */
    int id();

    /**
     * Voltage sensor measurement.
     *
     * @param uSigma         standard deviation of the voltage measurement in volts
     * @param uMeasured      voltage in volts; {@code null} when not measured
     * @param uAngleMeasured voltage angle in radians; {@code null} when the sensor measures magnitude only
     */
    record SymVoltageSensorMeasurement(
        int id,
        double uSigma,
        @Nullable Double uMeasured,
        @Nullable Double uAngleMeasured
    ) implements Measurement {

        public SymVoltageSensorMeasurement {
            if (uMeasured == null && uAngleMeasured != null) {
                throw new IllegalArgumentException(
                    "Voltage sensor %d has a measured angle without a measured voltage".formatted(id));
            }
        }
    }

    /**
     * Power sensor measurement.
     *
     * @param pMeasured active power in watts; {@code null} when not measured
     * @param qMeasured reactive power in vars; {@code null} when not measured
     * @param pSigma    standard deviation of the active power measurement in watts
     * @param qSigma    standard deviation of the reactive power measurement in vars
     */
    record SymPowerSensorMeasurement(
        int id,
        @Nullable Double pMeasured,
        @Nullable Double qMeasured,
        double pSigma,
        double qSigma
    ) implements Measurement { }
}
