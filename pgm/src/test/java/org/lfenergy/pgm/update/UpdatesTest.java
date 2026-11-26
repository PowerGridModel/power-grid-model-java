package org.lfenergy.pgm.update;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.loadSetpoint;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.measurements;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.powerMeasurement;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.setpoints;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.sourceSetpoint;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.update;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.updates;
import static org.lfenergy.pgm.fixtures.UpdateFixtures.voltageMeasurement;

class UpdatesTest {

    private static final Instant FIRST = Instant.parse("2026-10-12T10:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-10-12T10:05:00Z");

    @Test
    void acceptsUpdatesWithTheSameMeasurementsAndSetpoints() {
        final Update first = update(FIRST,
            measurements(voltageMeasurement(4, 1, 400, 0.0)),
            setpoints(loadSetpoint(2, 1_000, 200)));
        final Update second = update(SECOND,
            measurements(voltageMeasurement(4, 1, 401, 0.0)),
            setpoints(loadSetpoint(2, 2_000, 200)));

        final Updates updates = updates(first, second);

        assertEquals(2, updates.updates().size());
        assertEquals(FIRST, updates.updates().get(0).timestamp());
        assertEquals(SECOND, updates.updates().get(1).timestamp());
    }

    @Test
    void acceptsMeasurementsAndSetpointsInAnotherOrder() {
        final Update first = update(FIRST,
            measurements(voltageMeasurement(4, 1, 400, 0.0), powerMeasurement(5, 1, 1, 1, 1)),
            setpoints(loadSetpoint(2, 1_000, 200), sourceSetpoint(3, 1.0)));
        final Update reversed = update(SECOND,
            measurements(powerMeasurement(5, 1, 1, 1, 1), voltageMeasurement(4, 1, 400, 0.0)),
            setpoints(sourceSetpoint(3, 1.0), loadSetpoint(2, 2_000, 200)));

        assertEquals(2, updates(first, reversed).updates().size());
    }

    @Test
    void acceptsTheSameIdForDifferentKinds() {
        final Update first = update(FIRST,
            measurements(voltageMeasurement(1, 1, 400, 0.0), powerMeasurement(1, 1, 1, 1, 1)),
            setpoints(loadSetpoint(1, 1_000, 200), sourceSetpoint(1, 1.0)));

        assertEquals(1, updates(first).updates().size());
    }

    @Test
    void rejectsNoUpdates() {
        assertThrows(IllegalArgumentException.class, () -> updates());
    }

    @Test
    void rejectsMissingUpdateList() {
        assertThrows(NullPointerException.class, () -> new Updates(null));
    }

    @Test
    void rejectsMissingMeasurementsOrSetpoints() {
        assertThrows(NullPointerException.class, () -> new Update(FIRST, null, setpoints()));
        assertThrows(NullPointerException.class, () -> new Update(FIRST, measurements(), null));
    }

    @Test
    void rejectsUpdateWithFewerMeasurements() {
        final Update first = update(FIRST, measurements(voltageMeasurement(4, 1, 400, 0.0)), setpoints());
        final Update fewer = update(SECOND, measurements(), setpoints());

        assertThrows(IllegalArgumentException.class, () -> updates(first, fewer));
    }

    @Test
    void rejectsUpdateWithFewerSetpoints() {
        final Update first = update(FIRST, measurements(), setpoints(loadSetpoint(2, 1_000, 200)));
        final Update fewer = update(SECOND, measurements(), setpoints());

        assertThrows(IllegalArgumentException.class, () -> updates(first, fewer));
    }

    @Test
    void rejectsUpdateWithAnotherId() {
        final Update first = update(FIRST, measurements(), setpoints(loadSetpoint(2, 1_000, 200)));
        final Update otherId = update(SECOND, measurements(), setpoints(loadSetpoint(3, 1_000, 200)));

        assertThrows(IllegalArgumentException.class, () -> updates(first, otherId));
    }

    @Test
    void rejectsUpdateWithAnotherKindUnderTheSameId() {
        final Update first = update(FIRST,
            measurements(voltageMeasurement(1, 1, 400, 0.0)),
            setpoints(loadSetpoint(1, 1_000, 200)));
        final Update otherSetpointKind = update(SECOND,
            measurements(voltageMeasurement(1, 1, 400, 0.0)),
            setpoints(sourceSetpoint(1, 1.0)));
        final Update otherMeasurementKind = update(SECOND,
            measurements(powerMeasurement(1, 1, 1, 1, 1)),
            setpoints(loadSetpoint(1, 1_000, 200)));

        assertThrows(IllegalArgumentException.class, () -> updates(first, otherSetpointKind));
        assertThrows(IllegalArgumentException.class, () -> updates(first, otherMeasurementKind));
    }

    @Test
    void rejectsTheSameMeasurementTwice() {
        final Update twice = update(FIRST,
            measurements(voltageMeasurement(4, 1, 400, 0.0), voltageMeasurement(4, 1, 401, 0.0)),
            setpoints());

        assertThrows(IllegalArgumentException.class, () -> updates(twice));
    }

    @Test
    void rejectsTheSameSetpointTwice() {
        final Update twice = update(FIRST,
            measurements(),
            setpoints(loadSetpoint(2, 1_000, 200), loadSetpoint(2, 2_000, 200)));

        assertThrows(IllegalArgumentException.class, () -> updates(twice));
    }
}
