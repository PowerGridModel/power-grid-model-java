package org.lfenergy.pgm.fixtures;

import java.util.ArrayList;
import java.util.List;

import org.lfenergy.pgm.input.Input;
import org.lfenergy.pgm.input.Line;
import org.lfenergy.pgm.input.LoadGenType;
import org.lfenergy.pgm.input.MeasuredTerminalType;
import org.lfenergy.pgm.input.Node;
import org.lfenergy.pgm.input.Source;
import org.lfenergy.pgm.input.SymLoad;
import org.lfenergy.pgm.input.SymPowerSensor;
import org.lfenergy.pgm.input.SymVoltageSensor;
import org.lfenergy.pgm.input.Transformer;

public final class InputFixtures {

    private InputFixtures() { }

    public static Node node(final int id, final double uRated) {

        return new Node(id, uRated);
    }

    public static Line line(
        final int id,
        final int fromNode,
        final int toNode,
        final double r1,
        final double x1,
        final double c1,
        final double iN
    ) {

        return new Line(id, fromNode, toNode, r1, x1, c1, 0.0, iN, true, true);
    }

    public static Transformer transformer(
        final int id,
        final int fromNode,
        final int toNode,
        final double sn,
        final double u1,
        final double u2,
        final double uk,
        final double pk,
        final double p0,
        final double i0
    ) {

        final int windingTriangle = 2;
        final int windingWyeGrounded = 1;
        final int clock = 5;
        final int tapSideFrom = 0;
        final int tapPos = 0;
        final int tapNom = 0;
        final int tapMin = -5;
        final int tapMax = 5;
        final double tapSize = 250;
        return new Transformer(id, fromNode, toNode, windingTriangle, windingWyeGrounded, sn, u1,
            u2, uk, pk, p0, i0, clock, tapSideFrom,
            tapPos, tapNom, tapMin, tapMax, tapSize, true, true);
    }

    public static Source source(final int id, final int node, final double sk) {

        return new Source(id, node, 1.0, sk, true);
    }

    public static SymLoad constantPowerLoad(final int id, final int node) {

        return new SymLoad(id, node, LoadGenType.CONSTANT_POWER, 0.0, 0.0, true);
    }

    public static SymVoltageSensor voltageSensor(final int id, final int measuredObject) {

        return new SymVoltageSensor(id, measuredObject);
    }

    public static SymPowerSensor powerSensor(final int id, final int measuredObject, final MeasuredTerminalType measuredTerminalType) {

        return new SymPowerSensor(id, measuredObject, measuredTerminalType);
    }

    public static Builder input() {

        return new Builder();
    }

    public static final class Builder {

        private final List<Node> nodes = new ArrayList<>();
        private final List<Line> lines = new ArrayList<>();
        private final List<Transformer> transformers = new ArrayList<>();
        private final List<Source> sources = new ArrayList<>();
        private final List<SymLoad> symLoads = new ArrayList<>();
        private final List<SymVoltageSensor> symVoltageSensors = new ArrayList<>();
        private final List<SymPowerSensor> symPowerSensors = new ArrayList<>();

        public Builder with(final Node node) {

            nodes.add(node);
            return this;
        }

        public Builder with(final Line line) {

            lines.add(line);
            return this;
        }

        public Builder with(final Transformer transformer) {

            transformers.add(transformer);
            return this;
        }

        public Builder with(final Source source) {

            sources.add(source);
            return this;
        }

        public Builder with(final SymLoad load) {

            symLoads.add(load);
            return this;
        }

        public Builder with(final SymVoltageSensor sensor) {

            symVoltageSensors.add(sensor);
            return this;
        }

        public Builder with(final SymPowerSensor sensor) {

            symPowerSensors.add(sensor);
            return this;
        }

        public Input build() {

            return new Input(nodes, lines, transformers, sources, symLoads, symVoltageSensors, symPowerSensors);
        }
    }
}
