package org.lfenergy.pgm.update;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** The measurements and setpoints of one update, with an optional caller-supplied timestamp. */
public record Update(Instant timestamp, List<Measurement> measurements, List<Setpoint> setpoints) {

    public Update {
        measurements = List.copyOf(Objects.requireNonNull(measurements, "measurements"));
        setpoints = List.copyOf(Objects.requireNonNull(setpoints, "setpoints"));
    }
}
