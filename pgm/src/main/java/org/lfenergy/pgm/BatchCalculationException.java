package org.lfenergy.pgm;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Thrown when one or more updates of a batch could not be calculated and
 * {@link CalculationOptions#continueOnBatchError()} is {@code false}.
 */
public final class BatchCalculationException extends RuntimeException {

    private final transient List<FailedUpdate> failedUpdates;

    public BatchCalculationException(final List<FailedUpdate> failedUpdates) {

        super(message(failedUpdates));
        this.failedUpdates = List.copyOf(failedUpdates);
    }

    /** The updates that failed, in input order. */
    public List<FailedUpdate> failedUpdates() {

        return failedUpdates;
    }

    private static String message(final List<FailedUpdate> failedUpdates) {

        return "PGM could not calculate %d update(s): %s".formatted(failedUpdates.size(),
            failedUpdates.stream()
                .map(f -> "#%d (%s): %s".formatted(f.index(), f.timestamp(), f.error()))
                .collect(Collectors.joining("; ")));
    }

    /**
     * An update that PGM could not calculate.
     *
     * @param index position of the update in the batch
     * @param timestamp timestamp of the update
     * @param error PGM's error message
     */
    public record FailedUpdate(int index, Instant timestamp, String error) {

        public FailedUpdate {
            Objects.requireNonNull(error, "error");
        }
    }
}
