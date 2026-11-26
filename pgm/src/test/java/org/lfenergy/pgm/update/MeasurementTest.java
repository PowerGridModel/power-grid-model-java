package org.lfenergy.pgm.update;

import org.junit.jupiter.api.Test;
import org.lfenergy.pgm.update.Measurement.SymVoltageSensorMeasurement;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MeasurementTest {

    @Test
    void rejectsAMeasuredAngleWithoutAMeasuredVoltage() {
        assertThrows(IllegalArgumentException.class, () -> new SymVoltageSensorMeasurement(1, 1, null, 0.0));
    }
}
