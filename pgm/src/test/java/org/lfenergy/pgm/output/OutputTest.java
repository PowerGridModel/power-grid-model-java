package org.lfenergy.pgm.output;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputTest {

    @Test
    void createsAnOutputFromNodesOnly() {
        final NodeOutput node = new NodeOutput(1, true, 400, 1.0, 0.0, 0, 0);

        final Output output = new Output(List.of(node));

        assertEquals(List.of(node), output.nodes());
        assertTrue(output.branches().isEmpty());
        assertTrue(output.appliances().isEmpty());
        assertTrue(output.symVoltageSensors().isEmpty());
        assertTrue(output.symPowerSensors().isEmpty());
    }

    @Test
    void keepsTheTimestampOfSucceededAndFailedUpdates() {
        final Instant timestamp = Instant.parse("2026-10-12T10:00:00Z");
        final SymOutput success = new SymOutput.Success(timestamp, new Output(List.of()));
        final SymOutput failure = new SymOutput.Failure(timestamp, "Iteration failed to converge");

        assertEquals(timestamp, success.timestamp());
        assertEquals(timestamp, failure.timestamp());
    }

    @Test
    void rejectsMissingOutcome() {
        final Instant timestamp = Instant.parse("2026-10-12T10:00:00Z");

        assertThrows(NullPointerException.class, () -> new SymOutput.Success(timestamp, null));
        assertThrows(NullPointerException.class, () -> new SymOutput.Failure(timestamp, null));
    }

    @Test
    void rejectsMissingTypeOrSide() {
        assertThrows(NullPointerException.class, () -> new BranchOutput(null, 1, TerminalSide.FROM, true, 0, 0, 0, 0));
        assertThrows(NullPointerException.class, () -> new BranchOutput(BranchType.LINE, 1, null, true, 0, 0, 0, 0));
        assertThrows(NullPointerException.class, () -> new ApplianceOutput(null, 1, true, 0, 0, 0, 0));
    }
}
