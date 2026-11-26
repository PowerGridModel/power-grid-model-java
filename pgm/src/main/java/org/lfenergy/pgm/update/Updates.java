package org.lfenergy.pgm.update;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Batch of updates applied to the grid input in one native calculation.
 *
 * <p>Every update must contain measurements for the same sensors and setpoints for the same appliances, each at
 * most once; their order is free.</p>
 */
public record Updates(List<Update> updates) {

    public Updates {
        updates = List.copyOf(Objects.requireNonNull(updates, "updates"));
        if (updates.isEmpty()) {
            throw new IllegalArgumentException("Updates must contain at least one update");
        }
        requireSameComponents(updates, "measurements", Update::measurements, Measurement::id);
        requireSameComponents(updates, "setpoints", Update::setpoints, Setpoint::id);
    }

    private static <T> void requireSameComponents(
        final List<Update> updates,
        final String kind,
        final Function<Update, List<T>> values,
        final ToIntFunction<T> id
    ) {

        final Set<ComponentKey> reference = componentsOf(values.apply(updates.getFirst()), id, 0);
        for (int s = 1; s < updates.size(); s++) {
            if (!componentsOf(values.apply(updates.get(s)), id, s).equals(reference)) {
                throw new IllegalArgumentException("Update %d has other %s than update 0".formatted(s, kind));
            }
        }
    }

    private static <T> Set<ComponentKey> componentsOf(
        final List<T> values,
        final ToIntFunction<T> id,
        final int index
    ) {

        final Set<ComponentKey> keys = new HashSet<>();
        for (final T value : values) {
            if (!keys.add(new ComponentKey(value.getClass(), id.applyAsInt(value)))) {
                throw new IllegalArgumentException("Update %d has %s %d more than once"
                    .formatted(index, value.getClass().getSimpleName(), id.applyAsInt(value)));
            }
        }
        return keys;
    }

    private record ComponentKey(Class<?> type, int id) { }
}
