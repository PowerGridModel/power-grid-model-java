package org.lfenergy.pgm.output;

import java.time.Instant;
import java.util.Objects;

/**
 * Outcome of one update, retaining the update's timestamp. An update either succeeds with an
 * {@link Output} or, when the calculation continues on batch errors, fails with PGM's error message.
 */
public sealed interface SymOutput permits SymOutput.Success, SymOutput.Failure {

    Instant timestamp();

    /** An update that was calculated. */
    record Success(Instant timestamp, Output output) implements SymOutput {
        public Success {
            Objects.requireNonNull(output, "output");
        }
    }

    /** An update that PGM could not calculate, for example because the solver did not converge. */
    record Failure(Instant timestamp, String error) implements SymOutput {
        public Failure {
            Objects.requireNonNull(error, "error");
        }
    }
}
