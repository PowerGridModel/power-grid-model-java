package org.lfenergy.pgm.input;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.lfenergy.pgm.fixtures.InputFixtures.constantPowerLoad;
import static org.lfenergy.pgm.fixtures.InputFixtures.input;
import static org.lfenergy.pgm.fixtures.InputFixtures.node;
import static org.lfenergy.pgm.fixtures.InputFixtures.source;

class InputTest {

    @Test
    void keepsTheComponentsItIsGiven() {
        final Node node = node(1, 400);
        final Source source = source(2, 1, 1e10);
        final SymLoad load = constantPowerLoad(3, 1);

        final Input input = input().with(node).with(source).with(load).build();

        assertEquals(List.of(node), input.nodes());
        assertEquals(List.of(source), input.sources());
        assertEquals(List.of(load), input.symLoads());
    }

    @Test
    void rejectsAnInputWithoutNodes() {
        assertThrows(IllegalArgumentException.class, () -> input().build());
    }

    @Test
    void rejectsDuplicateIdsWithinAKind() {
        final var builder = input().with(node(1, 400)).with(node(1, 10_500));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void rejectsDuplicateIdsAcrossKinds() {
        final var builder = input().with(node(1, 400)).with(source(1, 1, 1e10));

        assertThrows(IllegalArgumentException.class, builder::build);
    }
}
