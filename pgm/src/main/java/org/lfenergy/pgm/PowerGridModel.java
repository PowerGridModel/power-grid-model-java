package org.lfenergy.pgm;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.lfenergy.pgm.input.Input;
import org.lfenergy.pgm.input.Line;
import org.lfenergy.pgm.input.Node;
import org.lfenergy.pgm.input.Source;
import org.lfenergy.pgm.input.SymLoad;
import org.lfenergy.pgm.input.SymPowerSensor;
import org.lfenergy.pgm.input.SymVoltageSensor;
import org.lfenergy.pgm.input.Transformer;
import org.lfenergy.pgm.loader.PGMLoader;
import org.lfenergy.pgm.output.ApplianceOutput;
import org.lfenergy.pgm.output.ApplianceType;
import org.lfenergy.pgm.output.BranchOutput;
import org.lfenergy.pgm.output.BranchType;
import org.lfenergy.pgm.output.NodeOutput;
import org.lfenergy.pgm.output.Output;
import org.lfenergy.pgm.output.SymOutput;
import org.lfenergy.pgm.output.SymPowerSensorOutput;
import org.lfenergy.pgm.output.SymVoltageSensorOutput;
import org.lfenergy.pgm.output.TerminalSide;
import org.lfenergy.pgm.update.Measurement;
import org.lfenergy.pgm.update.Measurement.SymPowerSensorMeasurement;
import org.lfenergy.pgm.update.Measurement.SymVoltageSensorMeasurement;
import org.lfenergy.pgm.update.Setpoint;
import org.lfenergy.pgm.update.Setpoint.SourceSetpoint;
import org.lfenergy.pgm.update.Setpoint.SymLoadSetpoint;
import org.lfenergy.pgm.update.Update;
import org.lfenergy.pgm.update.Updates;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Power Grid Model that loads its {@link Input} once and runs calculations on it.
 *
 * <p>The constructor creates the native PGM model from the input data. Each
 * calculation then applies the {@link Updates} as a single native batch, without touching the input data. Close the model to release the native resources. Data is exchanged with
 * PGM in the columnar format: one native array per attribute.</p>
 *
 * <p>Instances are not thread-safe: the native handle carries error state.</p>
 */
public final class PowerGridModel implements AutoCloseable {

    private static final int PGM_SYMMETRIC = 1;
    /** PGM's missing value for an int8 attribute. */
    private static final int NULL_INT8 = Byte.MIN_VALUE;

    private static final String NODE = "node";
    private static final String LINE = "line";
    private static final String TRANSFORMER = "transformer";
    private static final String SOURCE = "source";
    private static final String SYM_LOAD = "sym_load";
    private static final String SYM_VOLTAGE_SENSOR = "sym_voltage_sensor";
    private static final String SYM_POWER_SENSOR = "sym_power_sensor";
    private static final String ENERGIZED = "energized";

    public static final double DEFAULT_SYSTEM_FREQUENCY = 50.0;

    private final Input input;
    private final MemorySegment handle;
    private final MemorySegment nativeModel;

    private boolean closed;

    /**
     * Creates the model with a system frequency of {@value #DEFAULT_SYSTEM_FREQUENCY} Hz.
     */
    public PowerGridModel(final Input input) {

        this(input, DEFAULT_SYSTEM_FREQUENCY);
    }

    /**
     * Loads the native library and creates the PGM model from the input data.
     */
    public PowerGridModel(
        final Input input,
        final double systemFrequency
    ) {

        this.input = Objects.requireNonNull(input, "input");
        if (!Double.isFinite(systemFrequency) || systemFrequency <= 0.0) {
            throw new IllegalArgumentException("System frequency must be finite and greater than zero");
        }
        final PGMLoader loader = new PGMLoader();
        loader.autoload();
        loader.check(true);
        this.handle = createHandle();
        this.nativeModel = createNativeModel(handle, input, systemFrequency);
    }

    /**
     * Creates the native model from the input data and destroys the handle when that fails.
     */
    private static MemorySegment createNativeModel(
        final MemorySegment handle,
        final Input input,
        final double systemFrequency
    ) {

        MemorySegment model = MemorySegment.NULL;
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment nativeInput = buildInputDataSet(handle, arena, input);
            try {
                model = PowerGridModelC.PGM_create_model(handle, systemFrequency, nativeInput);
                ensureNoError(handle);
                requireNative(model, "create model");
            } finally {
                PowerGridModelC.PGM_destroy_dataset_const(nativeInput);
            }
            return model;
        } catch (RuntimeException e) {
            if (!model.equals(MemorySegment.NULL)) {
                PowerGridModelC.PGM_destroy_model(model);
            }
            PowerGridModelC.PGM_destroy_handle(handle);
            throw e;
        }
    }

    @Override
    public void close() {

        if (closed) {
            return;
        }
        closed = true;
        PowerGridModelC.PGM_destroy_model(nativeModel);
        PowerGridModelC.PGM_destroy_handle(handle);
    }

    /**
     * Calculates power-flow updates using Newton-Raphson defaults.
     */
    public List<SymOutput> calculatePowerFlow(final Updates updates) {

        return calculatePowerFlow(updates, CalculationOptions.powerFlowDefaults());
    }

    /**
     * Calculates power-flow updates as one native batch, in input order.
     *
     * @param updates measurements and setpoints per update
     * @param options solver and convergence settings
     */
    public List<SymOutput> calculatePowerFlow(
        final Updates updates,
        final CalculationOptions options
    ) {

        requireOpen();
        Objects.requireNonNull(options, "options");
        if (options.method() == CalculationMethod.ITERATIVE_LINEAR) {
            throw new IllegalArgumentException("Iterative-linear is a state-estimation method, not a power-flow method");
        }
        final List<Update> batch = Objects.requireNonNull(updates, "updates").updates();
        requirePowerFlowValues(batch);
        return calculate(batch, options, CalculationType.POWER_FLOW);
    }

    /**
     * Calculates state-estimation updates using iterative-linear defaults.
     */
    public List<SymOutput> calculateStateEstimation(final Updates updates) {

        return calculateStateEstimation(updates, CalculationOptions.stateEstimationDefaults());
    }

    /**
     * Calculates state-estimation updates as one native batch, in input order.
     *
     * @param updates measurements and setpoints per update; must contain measurements
     * @param options solver and convergence settings
     */
    public List<SymOutput> calculateStateEstimation(
        final Updates updates,
        final CalculationOptions options
    ) {

        requireOpen();
        Objects.requireNonNull(options, "options");
        if (options.method() != CalculationMethod.ITERATIVE_LINEAR
            && options.method() != CalculationMethod.NEWTON_RAPHSON) {
            throw new IllegalArgumentException("State estimation requires iterative-linear or Newton-Raphson");
        }
        final List<Update> batch = Objects.requireNonNull(updates, "updates").updates();
        requireStateEstimationSensors(batch);
        return calculate(batch, options, CalculationType.STATE_ESTIMATION);
    }

    private static MemorySegment createHandle() {

        final MemorySegment newHandle = PowerGridModelC.PGM_create_handle();
        requireNative(newHandle, "create handle");
        return newHandle;
    }

    private void requireOpen() {

        if (closed) {
            throw new IllegalStateException("Model is closed");
        }
    }

    /**
     * Power flow needs the power of every load and the reference voltage of every source, from the input or
     * from a setpoint. Every update has the same setpoints, so the first one is checked.
     */
    private void requirePowerFlowValues(final List<Update> updates) {

        final Set<Integer> loadSetpoints = setpointIds(updates.getFirst(), SymLoadSetpoint.class);
        for (SymLoad load : input.symLoads()) {
            if ((load.pSpecified() == null || load.qSpecified() == null) && !loadSetpoints.contains(load.id())) {
                throw new IllegalArgumentException(
                    "Power flow requires the power of load %d in the input or as a setpoint".formatted(load.id()));
            }
        }
        final Set<Integer> sourceSetpoints = setpointIds(updates.getFirst(), SourceSetpoint.class);
        for (Source source : input.sources()) {
            if (source.uRef() == null && !sourceSetpoints.contains(source.id())) {
                throw new IllegalArgumentException(
                    "Power flow requires the reference voltage of source %d in the input or as a setpoint"
                        .formatted(source.id()));
            }
        }
    }

    private static Set<Integer> setpointIds(
        final Update update,
        final Class<? extends Setpoint> type
    ) {

        return update.setpoints().stream()
            .filter(type::isInstance)
            .map(Setpoint::id)
            .collect(Collectors.toSet());
    }

    private void requireStateEstimationSensors(final List<Update> updates) {

        if (input.symVoltageSensors().isEmpty()) {
            throw new IllegalArgumentException("State estimation requires at least one voltage sensor in the input");
        }
        if (input.symPowerSensors().isEmpty()) {
            throw new IllegalArgumentException("State estimation requires at least one power sensor in the input");
        }
        if (updates.getFirst().measurements().isEmpty()) {
            throw new IllegalArgumentException("State estimation requires measurements");
        }
    }

    private List<SymOutput> calculate(
        final List<Update> updates,
        final CalculationOptions options,
        final CalculationType type
    ) {

        // The arena owns every attribute array, so it must outlive the calculation, the output reads
        // and the destruction of the native datasets that point into it.
        try (Arena arena = Arena.ofConfined()) {
            final List<Runnable> cleanup = new ArrayList<>();
            try {
                final MemorySegment updateData = buildUpdateDataset(arena, updates);
                cleanup.add(() -> PowerGridModelC.PGM_destroy_dataset_const(updateData));
                final OutputColumns output = allocateOutput(arena, updates.size(), type, cleanup);
                final MemorySegment nativeOptions = createNativeOptions(type, options, cleanup);
                PowerGridModelC.PGM_calculate(handle, nativeModel, nativeOptions, output.dataset(), updateData);
                final Map<Integer, String> failures = failedUpdates(handle);
                if (!failures.isEmpty() && !options.continueOnBatchError()) {
                    throw new BatchCalculationException(failures.entrySet().stream()
                        .map(f -> new BatchCalculationException.FailedUpdate(
                            f.getKey(), updates.get(f.getKey()).timestamp(), f.getValue()))
                        .toList());
                }
                return readOutputs(output, updates, failures);
            } finally {
                for (int i = cleanup.size() - 1; i >= 0; i--) {
                    cleanup.get(i).run();
                }
            }
        }
    }

    private MemorySegment createNativeOptions(
        final CalculationType type,
        final CalculationOptions options,
        final List<Runnable> cleanup
    ) {

        final MemorySegment nativeOptions = PowerGridModelC.PGM_create_options(handle);
        ensureNoError(handle);
        requireNative(nativeOptions, "create options");
        cleanup.add(() -> PowerGridModelC.PGM_destroy_options(nativeOptions));
        PowerGridModelC.PGM_set_calculation_type(handle, nativeOptions, type.nativeValue);
        PowerGridModelC.PGM_set_calculation_method(handle, nativeOptions, options.method().nativeValue());
        PowerGridModelC.PGM_set_symmetric(handle, nativeOptions, PGM_SYMMETRIC);
        PowerGridModelC.PGM_set_err_tol(handle, nativeOptions, options.errorTolerance());
        PowerGridModelC.PGM_set_max_iter(handle, nativeOptions, options.maxIterations());
        ensureNoError(handle);
        return nativeOptions;
    }

    /**
     * Returns PGM's error message per failed update index, or an empty map when every update succeeded.
     * Errors that are not about individual updates are thrown. The messages are copied before the next
     * native call can overwrite them.
     */
    private static Map<Integer, String> failedUpdates(final MemorySegment handle) {

        if (PowerGridModelC.PGM_error_code(handle) != PowerGridModelC.PGM_batch_error()) {
            ensureNoError(handle);
            return Map.of();
        }
        final long count = PowerGridModelC.PGM_n_failed_scenarios(handle);
        final MemorySegment indices = PowerGridModelC.PGM_failed_scenarios(handle);
        final MemorySegment messages = PowerGridModelC.PGM_batch_errors(handle);
        final Map<Integer, String> failures = new TreeMap<>();
        for (long i = 0; i < count; i++) {
            final MemorySegment message = messages.getAtIndex(PowerGridModelC.C_POINTER, i);
            failures.put(Math.toIntExact(indices.getAtIndex(PowerGridModelC.PGM_Idx, i)),
                message.equals(MemorySegment.NULL) ? "<no message>" : message.getString(0, UTF_8));
        }
        return failures;
    }

    /**
     * Allocates the output arrays. Power flow does not allocate sensor output, because PGM leaves it undefined
     * for power flow; the sensor output lists are then empty.
     */
    private OutputColumns allocateOutput(
        final Arena arena,
        final int updates,
        final CalculationType type,
        final List<Runnable> cleanup
    ) {

        final MemorySegment dataset = PowerGridModelC.PGM_create_dataset_mutable(
            handle, arena.allocateFrom("sym_output"), 1, updates);
        ensureNoError(handle);
        requireNative(dataset, "create output dataset");
        cleanup.add(() -> PowerGridModelC.PGM_destroy_dataset_mutable(dataset));
        final NodeOutputColumns nodes = addNodeOutput(dataset, arena, input.nodes().size(), updates);
        final List<BranchOutputColumns> branches = new ArrayList<>();
        addBranchOutput(handle, dataset, arena, BranchType.LINE, input.lines().size(), updates, branches);
        addBranchOutput(handle, dataset, arena, BranchType.TRANSFORMER, input.transformers().size(), updates,
            branches);
        final List<ApplianceOutputColumns> appliances = new ArrayList<>();
        addApplianceOutput(handle, dataset, arena, ApplianceType.SOURCE, input.sources().size(), updates,
            appliances);
        addApplianceOutput(handle, dataset, arena, ApplianceType.SYM_LOAD, input.symLoads().size(), updates,
            appliances);
        if (type == CalculationType.POWER_FLOW) {
            return new OutputColumns(dataset, nodes, branches, appliances, null, null);
        }
        return new OutputColumns(dataset, nodes, branches, appliances,
            addSensorOutput(handle, dataset, arena, SYM_VOLTAGE_SENSOR, "u_residual", "u_angle_residual",
                input.symVoltageSensors().size(), updates),
            addSensorOutput(handle, dataset, arena, SYM_POWER_SENSOR, "p_residual", "q_residual",
                input.symPowerSensors().size(), updates));
    }

    private NodeOutputColumns addNodeOutput(
        final MemorySegment dataset,
        final Arena arena,
        final int nodeCount,
        final int updates
    ) {

        addOutputComponent(handle, dataset, arena, NODE, nodeCount, updates);
        return new NodeOutputColumns(
            addOutputAttribute(handle, dataset, arena, NODE, "id", Integer.BYTES, nodeCount, updates),
            addOutputAttribute(handle, dataset, arena, NODE, ENERGIZED, Byte.BYTES, nodeCount, updates),
            addOutputAttribute(handle, dataset, arena, NODE, "u", Double.BYTES, nodeCount, updates),
            addOutputAttribute(handle, dataset, arena, NODE, "u_pu", Double.BYTES, nodeCount, updates),
            addOutputAttribute(handle, dataset, arena, NODE, "u_angle", Double.BYTES, nodeCount, updates),
            addOutputAttribute(handle, dataset, arena, NODE, "p", Double.BYTES, nodeCount, updates),
            addOutputAttribute(handle, dataset, arena, NODE, "q", Double.BYTES, nodeCount, updates));
    }

    /**
     * Reads the output of every update that succeeded; the output of a failed update is undefined, so it is
     * not read.
     */
    private List<SymOutput> readOutputs(
        final OutputColumns output,
        final List<Update> updates,
        final Map<Integer, String> failures
    ) {

        final int updateCount = updates.size();
        final List<SymOutput> results = new ArrayList<>(updateCount);
        for (int s = 0; s < updateCount; s++) {
            final Update update = updates.get(s);
            final @Nullable String error = failures.get(s);
            results.add(error == null
                ? new SymOutput.Success(update.timestamp(), readUpdateOutput(output, update, s, updateCount))
                : new SymOutput.Failure(update.timestamp(), error));
        }
        return List.copyOf(results);
    }

    private Output readUpdateOutput(
        final OutputColumns output,
        final Update update,
        final int updateIndex,
        final int updateCount
    ) {

        final List<BranchOutput> branchOutputs = new ArrayList<>();
        for (BranchOutputColumns branchOutput : output.branches()) {
            readBranchOutputs(branchOutput, updateIndex, updateCount, branchOutputs);
        }
        final List<ApplianceOutput> applianceOutputs = new ArrayList<>();
        for (ApplianceOutputColumns applianceOutput : output.appliances()) {
            readApplianceOutputs(applianceOutput, updateIndex, updateCount, applianceOutputs);
        }
        final Map<Integer, SymVoltageSensorMeasurement> voltageMeasurements =
            measurementsById(update, SymVoltageSensorMeasurement.class);
        final List<SymVoltageSensorOutput> voltageSensorOutputs = new ArrayList<>();
        readSensorOutputs(output.voltageSensors(), updateIndex, updateCount, (sensorId, sensorEnergized, first, second) -> {
            final @Nullable SymVoltageSensorMeasurement measurement = voltageMeasurements.get(sensorId);
            voltageSensorOutputs.add(new SymVoltageSensorOutput(sensorId, sensorEnergized,
                residual(measurement == null ? null : measurement.uMeasured(), first),
                residual(measurement == null ? null : measurement.uAngleMeasured(), second)));
        });
        final Map<Integer, SymPowerSensorMeasurement> powerMeasurements =
            measurementsById(update, SymPowerSensorMeasurement.class);
        final List<SymPowerSensorOutput> powerSensorOutputs = new ArrayList<>();
        readSensorOutputs(output.powerSensors(), updateIndex, updateCount, (sensorId, sensorEnergized, first, second) -> {
            final @Nullable SymPowerSensorMeasurement measurement = powerMeasurements.get(sensorId);
            powerSensorOutputs.add(new SymPowerSensorOutput(sensorId, sensorEnergized,
                residual(measurement == null ? null : measurement.pMeasured(), first),
                residual(measurement == null ? null : measurement.qMeasured(), second)));
        });
        return new Output(readNodeOutputs(output.nodes(), updateIndex), branchOutputs, applianceOutputs,
            voltageSensorOutputs, powerSensorOutputs);
    }

    private static <T extends Measurement> Map<Integer, T> measurementsById(
        final Update update,
        final Class<T> type
    ) {

        return update.measurements().stream()
            .filter(type::isInstance)
            .map(type::cast)
            .collect(Collectors.toMap(Measurement::id, Function.identity()));
    }

    /**
     * The residual of an unmeasured value is meaningless, because the measurement was written as ignored.
     */
    private static @Nullable Double residual(
        final @Nullable Double measured,
        final double residual
    ) {

        return measured == null || Double.isNaN(residual) ? null : residual;
    }

    private List<NodeOutput> readNodeOutputs(
        final NodeOutputColumns columns,
        final int updateIndex
    ) {

        final int nodeCount = input.nodes().size();
        final List<NodeOutput> nodeOutputs = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            final long index = (long) updateIndex * nodeCount + i;
            nodeOutputs.add(new NodeOutput(
                columns.ids().getAtIndex(PowerGridModelC.PGM_ID, index),
                columns.energized().getAtIndex(PowerGridModelC.C_CHAR, index) != 0,
                columns.u().getAtIndex(PowerGridModelC.C_DOUBLE, index),
                columns.uPu().getAtIndex(PowerGridModelC.C_DOUBLE, index),
                columns.uAngle().getAtIndex(PowerGridModelC.C_DOUBLE, index),
                columns.p().getAtIndex(PowerGridModelC.C_DOUBLE, index),
                columns.q().getAtIndex(PowerGridModelC.C_DOUBLE, index)));
        }
        return nodeOutputs;
    }

    private static MemorySegment buildInputDataSet(
        final MemorySegment handle,
        final Arena arena,
        final Input input
    ) {

        final MemorySegment dataset = PowerGridModelC.PGM_create_dataset_const(handle, arena.allocateFrom("input"), 0, 1);
        try {
            ensureNoError(handle);
            requireNative(dataset, "create input dataset");
            final Columns columns = new Columns(handle, dataset, arena);
            addNodes(columns, input.nodes());
            addLines(columns, input.lines());
            addTransformers(columns, input.transformers());
            addLoads(columns, input.symLoads());
            addSources(columns, input.sources());
            addVoltageSensors(columns, input.symVoltageSensors());
            addPowerSensors(columns, input.symPowerSensors());
            return dataset;
        } catch (RuntimeException e) {
            PowerGridModelC.PGM_destroy_dataset_const(dataset);
            throw e;
        }
    }

    private static void addNodes(
        final Columns columns,
        final List<Node> nodes
    ) {

        final Columns.Component<Node> node = columns.component(NODE, nodes);
        node.ints("id", Node::id);
        node.doubles("u_rated", Node::uRated);
    }

    private static void addLines(
        final Columns columns,
        final List<Line> lines
    ) {

        final Columns.Component<Line> line = columns.component(LINE, lines);
        line.ints("id", Line::id);
        line.ints("from_node", Line::fromNode);
        line.ints("to_node", Line::toNode);
        line.flags("from_status", Line::fromStatus);
        line.flags("to_status", Line::toStatus);
        line.doubles("r1", Line::r1);
        line.doubles("x1", Line::x1);
        line.doubles("c1", Line::c1);
        line.doubles("tan1", Line::tan1);
        line.optionalDoubles("i_n", Line::iN);
    }

    private static void addTransformers(
        final Columns columns,
        final List<Transformer> transformers
    ) {

        final Columns.Component<Transformer> transformer = columns.component(TRANSFORMER, transformers);
        transformer.ints("id", Transformer::id);
        transformer.ints("from_node", Transformer::fromNode);
        transformer.ints("to_node", Transformer::toNode);
        transformer.flags("from_status", Transformer::fromStatus);
        transformer.flags("to_status", Transformer::toStatus);
        transformer.bytes("winding_from", Transformer::windingFrom);
        transformer.bytes("winding_to", Transformer::windingTo);
        transformer.doubles("sn", Transformer::sn);
        transformer.doubles("u1", Transformer::u1);
        transformer.doubles("u2", Transformer::u2);
        transformer.doubles("uk", Transformer::uk);
        transformer.doubles("pk", Transformer::pk);
        transformer.doubles("p0", Transformer::p0);
        transformer.doubles("i0", Transformer::i0);
        transformer.bytes("clock", Transformer::clock);
        transformer.bytes("tap_side", Transformer::tapSide);
        transformer.optionalBytes("tap_pos", Transformer::tapPos);
        transformer.optionalBytes("tap_nom", Transformer::tapNom);
        transformer.bytes("tap_min", Transformer::tapMin);
        transformer.bytes("tap_max", Transformer::tapMax);
        transformer.doubles("tap_size", Transformer::tapSize);
    }

    private static void addLoads(
        final Columns columns,
        final List<SymLoad> loads
    ) {

        final Columns.Component<SymLoad> load = columns.component(SYM_LOAD, loads);
        load.ints("id", SymLoad::id);
        load.ints("node", SymLoad::node);
        load.flags("status", SymLoad::status);
        load.bytes("type", l -> l.type().nativeValue());
        load.optionalDoubles("p_specified", SymLoad::pSpecified);
        load.optionalDoubles("q_specified", SymLoad::qSpecified);
    }

    private static void addSources(
        final Columns columns,
        final List<Source> sources
    ) {

        final Columns.Component<Source> source = columns.component(SOURCE, sources);
        source.ints("id", Source::id);
        source.ints("node", Source::node);
        source.flags("status", Source::status);
        source.optionalDoubles("u_ref", Source::uRef);
        source.optionalDoubles("sk", Source::sk);
    }

    private static void addVoltageSensors(
        final Columns columns,
        final List<SymVoltageSensor> voltageSensors
    ) {

        final Columns.Component<SymVoltageSensor> sensor = columns.component(SYM_VOLTAGE_SENSOR, voltageSensors);
        sensor.ints("id", SymVoltageSensor::id);
        sensor.ints("measured_object", SymVoltageSensor::measuredObject);
    }

    private static void addPowerSensors(
        final Columns columns,
        final List<SymPowerSensor> powerSensors
    ) {

        final Columns.Component<SymPowerSensor> sensor = columns.component(SYM_POWER_SENSOR, powerSensors);
        sensor.ints("id", SymPowerSensor::id);
        sensor.ints("measured_object", SymPowerSensor::measuredObject);
        sensor.bytes("measured_terminal_type", p -> p.measuredTerminalType().nativeValue());
    }

    /**
     * Builds PGM's batch "update" dataset from the updates; each component's rows are laid out update by
     * update, sorted by id.
     */
    private MemorySegment buildUpdateDataset(
        final Arena arena,
        final List<Update> updates
    ) {

        final int updateCount = updates.size();
        final MemorySegment dataset = PowerGridModelC.PGM_create_dataset_const(
            handle, arena.allocateFrom("update"), 1, updateCount);
        try {
            ensureNoError(handle);
            requireNative(dataset, "create update dataset");
            final Columns columns = new Columns(handle, dataset, arena);
            addVoltageSensorMeasurements(columns,
                measurementsOf(updates, SymVoltageSensorMeasurement.class), updateCount);
            addPowerSensorMeasurements(columns,
                measurementsOf(updates, SymPowerSensorMeasurement.class), updateCount);
            addLoadSetpoints(columns, setpointsOf(updates, SymLoadSetpoint.class), updateCount);
            addSourceSetpoints(columns, setpointsOf(updates, SourceSetpoint.class), updateCount);
            return dataset;
        } catch (RuntimeException e) {
            PowerGridModelC.PGM_destroy_dataset_const(dataset);
            throw e;
        }
    }

    private static <T extends Measurement> List<T> measurementsOf(
        final List<Update> updates,
        final Class<T> type
    ) {

        return valuesOf(updates, Update::measurements, type, Measurement::id);
    }

    private static <T extends Setpoint> List<T> setpointsOf(
        final List<Update> updates,
        final Class<T> type
    ) {

        return valuesOf(updates, Update::setpoints, type, Setpoint::id);
    }

    private static <T> List<T> valuesOf(
        final List<Update> updates,
        final Function<Update, List<?>> values,
        final Class<T> type,
        final ToIntFunction<? super T> id
    ) {

        return updates.stream()
            .flatMap(update -> values.apply(update).stream()
                .filter(type::isInstance)
                .map(type::cast)
                .sorted(Comparator.comparingInt(id)))
            .toList();
    }

    private static void addVoltageSensorMeasurements(
        final Columns columns,
        final List<SymVoltageSensorMeasurement> measurements,
        final int updates
    ) {

        final Columns.Component<SymVoltageSensorMeasurement> sensor =
            columns.component(SYM_VOLTAGE_SENSOR, measurements, updates);
        sensor.ints("id", SymVoltageSensorMeasurement::id);
        sensor.doubles("u_sigma", u -> sigma(u.uMeasured(), u.uSigma()));
        sensor.doubles("u_measured", u -> valueOrZero(u.uMeasured()));
        sensor.optionalDoubles("u_angle_measured", SymVoltageSensorMeasurement::uAngleMeasured);
    }

    private static void addPowerSensorMeasurements(
        final Columns columns,
        final List<SymPowerSensorMeasurement> measurements,
        final int updates
    ) {

        final Columns.Component<SymPowerSensorMeasurement> sensor =
            columns.component(SYM_POWER_SENSOR, measurements, updates);
        sensor.ints("id", SymPowerSensorMeasurement::id);
        sensor.doubles("p_sigma", p -> sigma(p.pMeasured(), p.pSigma()));
        sensor.doubles("q_sigma", p -> sigma(p.qMeasured(), p.qSigma()));
        sensor.doubles("p_measured", p -> valueOrZero(p.pMeasured()));
        sensor.doubles("q_measured", p -> valueOrZero(p.qMeasured()));
    }

    private static void addLoadSetpoints(
        final Columns columns,
        final List<SymLoadSetpoint> setpoints,
        final int updates
    ) {

        final Columns.Component<SymLoadSetpoint> load = columns.component(SYM_LOAD, setpoints, updates);
        load.ints("id", SymLoadSetpoint::id);
        load.doubles("p_specified", SymLoadSetpoint::pSpecified);
        load.doubles("q_specified", SymLoadSetpoint::qSpecified);
    }

    private static void addSourceSetpoints(
        final Columns columns,
        final List<SourceSetpoint> setpoints,
        final int updates
    ) {

        final Columns.Component<SourceSetpoint> source = columns.component(SOURCE, setpoints, updates);
        source.ints("id", SourceSetpoint::id);
        source.doubles("u_ref", SourceSetpoint::uRef);
    }

    /**
     * PGM ignores a measured value with an infinite standard deviation, which is how an unmeasured value is written.
     */
    private static double sigma(
        final @Nullable Double measured,
        final double standardDeviation
    ) {

        return measured == null ? Double.POSITIVE_INFINITY : standardDeviation;
    }

    private static double valueOrZero(final @Nullable Double measured) {

        return measured == null ? 0.0 : measured;
    }

    private static MemorySegment addOutputAttribute(
        final MemorySegment handle,
        final MemorySegment output,
        final Arena arena,
        final String component,
        final String attribute,
        final long elementSize,
        final int count,
        final int scenarios
    ) {

        final MemorySegment data = arena.allocate(elementSize * count * scenarios, elementSize);
        PowerGridModelC.PGM_dataset_mutable_add_attribute_buffer(
            handle, output, arena.allocateFrom(component), arena.allocateFrom(attribute), data);
        ensureNoError(handle);
        return data;
    }

    private static void addBranchOutput(
        final MemorySegment handle,
        final MemorySegment output,
        final Arena arena,
        final BranchType type,
        final int count,
        final int scenarios,
        final List<BranchOutputColumns> outputs
    ) {

        if (count == 0) {
            return;
        }
        final String component = componentName(type);
        addOutputComponent(handle, output, arena, component, count, scenarios);
        outputs.add(new BranchOutputColumns(type,
            addOutputAttribute(handle, output, arena, component, "id", Integer.BYTES, count, scenarios),
            addOutputAttribute(handle, output, arena, component, ENERGIZED, Byte.BYTES, count, scenarios),
            addOutputAttribute(handle, output, arena, component, "p_from", Double.BYTES, count, scenarios),
            addOutputAttribute(handle, output, arena, component, "q_from", Double.BYTES, count, scenarios),
            addOutputAttribute(handle, output, arena, component, "i_from", Double.BYTES, count, scenarios),
            addOutputAttribute(handle, output, arena, component, "p_to", Double.BYTES, count, scenarios),
            addOutputAttribute(handle, output, arena, component, "q_to", Double.BYTES, count, scenarios),
            addOutputAttribute(handle, output, arena, component, "i_to", Double.BYTES, count, scenarios)));
    }

    private static void addApplianceOutput(
        final MemorySegment handle,
        final MemorySegment output,
        final Arena arena,
        final ApplianceType type,
        final int count,
        final int scenarios,
        final List<ApplianceOutputColumns> outputs
    ) {

        if (count == 0) {
            return;
        }
        final String component = componentName(type);
        addOutputComponent(handle, output, arena, component, count, scenarios);
        outputs.add(new ApplianceOutputColumns(type,
            addOutputAttribute(handle, output, arena, component, "id", Integer.BYTES, count, scenarios),
            addOutputAttribute(handle, output, arena, component, ENERGIZED, Byte.BYTES, count, scenarios),
            addOutputAttribute(handle, output, arena, component, "p", Double.BYTES, count, scenarios),
            addOutputAttribute(handle, output, arena, component, "q", Double.BYTES, count, scenarios),
            addOutputAttribute(handle, output, arena, component, "i", Double.BYTES, count, scenarios),
            addOutputAttribute(handle, output, arena, component, "pf", Double.BYTES, count, scenarios)));
    }

    private static String componentName(final BranchType type) {

        return switch (type) {
            case LINE -> LINE;
            case TRANSFORMER -> TRANSFORMER;
        };
    }

    private static String componentName(final ApplianceType type) {

        return switch (type) {
            case SOURCE -> SOURCE;
            case SYM_LOAD -> SYM_LOAD;
        };
    }

    private static void addOutputComponent(
        final MemorySegment handle,
        final MemorySegment output,
        final Arena arena,
        final String component,
        final int count,
        final int scenarios
    ) {

        PowerGridModelC.PGM_dataset_mutable_add_buffer(
            handle, output, arena.allocateFrom(component), count, (long) count * scenarios,
            MemorySegment.NULL, MemorySegment.NULL);
        ensureNoError(handle);
    }

    private static void readBranchOutputs(
        final BranchOutputColumns columns,
        final int updateIndex,
        final int updateCount,
        final List<BranchOutput> outputs
    ) {

        final long perUpdate = columns.ids().byteSize() / Integer.BYTES / updateCount;
        for (long k = 0; k < perUpdate; k++) {
            final long i = updateIndex * perUpdate + k;
            final int id = columns.ids().getAtIndex(PowerGridModelC.PGM_ID, i);
            final boolean energized = columns.energized().getAtIndex(PowerGridModelC.C_CHAR, i) != 0;
            final double pFrom = columns.pFrom().getAtIndex(PowerGridModelC.C_DOUBLE, i);
            final double qFrom = columns.qFrom().getAtIndex(PowerGridModelC.C_DOUBLE, i);
            final double pTo = columns.pTo().getAtIndex(PowerGridModelC.C_DOUBLE, i);
            final double qTo = columns.qTo().getAtIndex(PowerGridModelC.C_DOUBLE, i);
            outputs.add(new BranchOutput(columns.type(), id, TerminalSide.FROM, energized, pFrom, qFrom,
                columns.iFrom().getAtIndex(PowerGridModelC.C_DOUBLE, i), powerFactor(pFrom, qFrom)));
            outputs.add(new BranchOutput(columns.type(), id, TerminalSide.TO, energized, pTo, qTo,
                columns.iTo().getAtIndex(PowerGridModelC.C_DOUBLE, i), powerFactor(pTo, qTo)));
        }
    }

    private static void readApplianceOutputs(
        final ApplianceOutputColumns columns,
        final int updateIndex,
        final int updateCount,
        final List<ApplianceOutput> outputs
    ) {

        final long perUpdate = columns.ids().byteSize() / Integer.BYTES / updateCount;
        for (long k = 0; k < perUpdate; k++) {
            final long i = updateIndex * perUpdate + k;
            outputs.add(new ApplianceOutput(columns.type(),
                columns.ids().getAtIndex(PowerGridModelC.PGM_ID, i),
                columns.energized().getAtIndex(PowerGridModelC.C_CHAR, i) != 0,
                columns.p().getAtIndex(PowerGridModelC.C_DOUBLE, i),
                columns.q().getAtIndex(PowerGridModelC.C_DOUBLE, i),
                columns.i().getAtIndex(PowerGridModelC.C_DOUBLE, i),
                columns.pf().getAtIndex(PowerGridModelC.C_DOUBLE, i)));
        }
    }

    private enum CalculationType {
        POWER_FLOW(0),
        STATE_ESTIMATION(1);

        private final int nativeValue;

        CalculationType(final int nativeValue) {

            this.nativeValue = nativeValue;
        }
    }

    private record OutputColumns(
        MemorySegment dataset,
        NodeOutputColumns nodes,
        List<BranchOutputColumns> branches,
        List<ApplianceOutputColumns> appliances,
        @Nullable SensorOutputColumns voltageSensors,
        @Nullable SensorOutputColumns powerSensors
    ) {

    }

    private record NodeOutputColumns(
        MemorySegment ids,
        MemorySegment energized,
        MemorySegment u,
        MemorySegment uPu,
        MemorySegment uAngle,
        MemorySegment p,
        MemorySegment q
    ) {

    }

    private record BranchOutputColumns(
        BranchType type,
        MemorySegment ids,
        MemorySegment energized,
        MemorySegment pFrom,
        MemorySegment qFrom,
        MemorySegment iFrom,
        MemorySegment pTo,
        MemorySegment qTo,
        MemorySegment iTo
    ) {

    }

    private record ApplianceOutputColumns(
        ApplianceType type,
        MemorySegment ids,
        MemorySegment energized,
        MemorySegment p,
        MemorySegment q,
        MemorySegment i,
        MemorySegment pf
    ) {

    }

    private record SensorOutputColumns(
        MemorySegment ids,
        MemorySegment energized,
        MemorySegment first,
        MemorySegment second
    ) {

    }

    @FunctionalInterface
    private interface SensorOutputSink {

        void accept(
            int id,
            boolean energized,
            double first,
            double second
        );
    }

    private static double powerFactor(
        final double activePower,
        final double reactivePower
    ) {

        final double apparentPower = Math.hypot(activePower, reactivePower);
        return apparentPower == 0.0 ? 0.0 : Math.abs(activePower) / apparentPower;
    }

    private static @Nullable SensorOutputColumns addSensorOutput(
        final MemorySegment handle,
        final MemorySegment output,
        final Arena arena,
        final String component,
        final String firstAttribute,
        final String secondAttribute,
        final int count,
        final int updates
    ) {

        if (count == 0) {
            return null;
        }
        addOutputComponent(handle, output, arena, component, count, updates);
        return new SensorOutputColumns(
            addOutputAttribute(handle, output, arena, component, "id", Integer.BYTES, count, updates),
            addOutputAttribute(handle, output, arena, component, ENERGIZED, Byte.BYTES, count, updates),
            addOutputAttribute(handle, output, arena, component, firstAttribute, Double.BYTES, count, updates),
            addOutputAttribute(handle, output, arena, component, secondAttribute, Double.BYTES, count, updates));
    }

    private static void readSensorOutputs(
        final @Nullable SensorOutputColumns columns,
        final int updateIndex,
        final int updateCount,
        final SensorOutputSink sink
    ) {

        if (columns == null) {
            return;
        }
        final long perUpdate = columns.ids().byteSize() / Integer.BYTES / updateCount;
        for (long k = 0; k < perUpdate; k++) {
            final long i = updateIndex * perUpdate + k;
            sink.accept(
                columns.ids().getAtIndex(PowerGridModelC.PGM_ID, i),
                columns.energized().getAtIndex(PowerGridModelC.C_CHAR, i) != 0,
                columns.first().getAtIndex(PowerGridModelC.C_DOUBLE, i),
                columns.second().getAtIndex(PowerGridModelC.C_DOUBLE, i));
        }
    }

    private static void ensureNoError(final MemorySegment handle) {

        final long errorCode = PowerGridModelC.PGM_error_code(handle);
        if (errorCode != PowerGridModelC.PGM_no_error()) {
            final MemorySegment message = PowerGridModelC.PGM_error_message(handle);
            final String description = message.equals(MemorySegment.NULL) ? "<no message>" : message.getString(0, UTF_8);
            throw new IllegalStateException("PGM error %d: %s".formatted(errorCode, description));
        }
    }

    private static void requireNative(
        final MemorySegment pointer,
        final String operation
    ) {

        if (pointer.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("PGM could not %s".formatted(operation));
        }
    }

    /**
     * Registers components and one native array per attribute on a const dataset.
     */
    private static final class Columns {

        private final MemorySegment handle;
        private final MemorySegment dataset;
        private final Arena arena;

        private Columns(
            final MemorySegment handle,
            final MemorySegment dataset,
            final Arena arena
        ) {

            this.handle = handle;
            this.dataset = dataset;
            this.arena = arena;
        }

        /**
         * Registers a component with the same number of rows in every scenario; a component without items is
         * not registered and ignores its attributes.
         */
        private <T> Component<T> component(
            final String name,
            final List<T> items
        ) {

            return component(name, items, 1);
        }

        private <T> Component<T> component(
            final String name,
            final List<T> items,
            final int scenarios
        ) {

            if (!items.isEmpty()) {
                PowerGridModelC.PGM_dataset_const_add_buffer(
                    handle, dataset, arena.allocateFrom(name), items.size() / scenarios, items.size(),
                    MemorySegment.NULL, MemorySegment.NULL);
                ensureNoError(handle);
            }
            return new Component<>(name, items);
        }

        private final class Component<T> {

            private final String name;
            private final List<T> items;

            private Component(
                final String name,
                final List<T> items
            ) {

                this.name = name;
                this.items = items;
            }

            private void ints(
                final String attribute,
                final ToIntFunction<T> getter
            ) {

                if (items.isEmpty()) {
                    return;
                }
                final MemorySegment data = arena.allocate((long) items.size() * Integer.BYTES, Integer.BYTES);
                for (int i = 0; i < items.size(); i++) {
                    data.setAtIndex(PowerGridModelC.PGM_ID, i, getter.applyAsInt(items.get(i)));
                }
                attach(attribute, data);
            }

            private void bytes(
                final String attribute,
                final ToIntFunction<T> getter
            ) {

                if (items.isEmpty()) {
                    return;
                }
                final MemorySegment data = arena.allocate(items.size(), Byte.BYTES);
                for (int i = 0; i < items.size(); i++) {
                    data.setAtIndex(PowerGridModelC.C_CHAR, i, (byte) getter.applyAsInt(items.get(i)));
                }
                attach(attribute, data);
            }

            private void flags(
                final String attribute,
                final Predicate<T> getter
            ) {

                bytes(attribute, item -> getter.test(item) ? 1 : 0);
            }

            private void optionalBytes(
                final String attribute,
                final Function<T, @Nullable Integer> getter
            ) {

                bytes(attribute, item -> {
                    final Integer value = getter.apply(item);
                    return value == null ? NULL_INT8 : value;
                });
            }

            /**
             * Writes a required attribute; NaN is rejected, because PGM reads it as a missing value.
             */
            private void doubles(
                final String attribute,
                final ToDoubleFunction<T> getter
            ) {

                if (items.isEmpty()) {
                    return;
                }
                final MemorySegment data = arena.allocate((long) items.size() * Double.BYTES, Double.BYTES);
                for (int i = 0; i < items.size(); i++) {
                    final T item = items.get(i);
                    final double value = getter.applyAsDouble(item);
                    if (Double.isNaN(value)) {
                        throw new IllegalArgumentException(
                            "%s.%s must not be NaN: %s".formatted(name, attribute, item));
                    }
                    data.setAtIndex(PowerGridModelC.C_DOUBLE, i, value);
                }
                attach(attribute, data);
            }

            /**
             * Writes an optional attribute; {@code null} becomes NaN, PGM's missing value.
             */
            private void optionalDoubles(
                final String attribute,
                final Function<T, @Nullable Double> getter
            ) {

                if (items.isEmpty()) {
                    return;
                }
                final MemorySegment data = arena.allocate((long) items.size() * Double.BYTES, Double.BYTES);
                for (int i = 0; i < items.size(); i++) {
                    final T item = items.get(i);
                    final Double value = getter.apply(item);
                    if (value != null && value.isNaN()) {
                        throw new IllegalArgumentException(
                            "%s.%s must not be NaN; use null for a missing value: %s".formatted(name, attribute, item));
                    }
                    data.setAtIndex(PowerGridModelC.C_DOUBLE, i, value == null ? Double.NaN : value);
                }
                attach(attribute, data);
            }

            private void attach(
                final String attribute,
                final MemorySegment data
            ) {

                PowerGridModelC.PGM_dataset_const_add_attribute_buffer(
                    handle, dataset, arena.allocateFrom(name), arena.allocateFrom(attribute), data);
                ensureNoError(handle);
            }
        }
    }
}
