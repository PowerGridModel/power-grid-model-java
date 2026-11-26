package org.lfenergy.pgm.input;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Immutable grid input: component and sensor definitions (sensors are only needed for state estimation), without any measured or update values.
 */
public record Input(
    List<Node> nodes,
    List<Line> lines,
    List<Transformer> transformers,
    List<Source> sources,
    List<SymLoad> symLoads,
    List<SymVoltageSensor> symVoltageSensors,
    List<SymPowerSensor> symPowerSensors
) {

    public Input {
        nodes = List.copyOf(nodes);
        lines = List.copyOf(lines);
        transformers = List.copyOf(transformers);
        sources = List.copyOf(sources);
        symLoads = List.copyOf(symLoads);
        symVoltageSensors = List.copyOf(symVoltageSensors);
        symPowerSensors = List.copyOf(symPowerSensors);
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("Input must contain at least one node");
        }

        final List<Integer> ids = Stream.of(
                nodes.stream().map(Node::id),
                lines.stream().map(Line::id),
                transformers.stream().map(Transformer::id),
                sources.stream().map(Source::id),
                symLoads.stream().map(SymLoad::id),
                symVoltageSensors.stream().map(SymVoltageSensor::id),
                symPowerSensors.stream().map(SymPowerSensor::id))
            .flatMap(Function.identity())
            .toList();

        final Set<Integer> seen = new HashSet<>();
        ids.forEach(id -> { if (!seen.add(id)) throw new IllegalArgumentException("Duplicate component id %d".formatted(id)); });
    }
}
