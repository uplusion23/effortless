package dev.huskuraft.effortless.building.mirror;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import dev.huskuraft.universal.api.core.Axis;
import dev.huskuraft.universal.api.math.Vector3d;
import dev.huskuraft.effortless.building.pattern.mirror.MirrorTransformer;

// The mirrors that the player placed in the world. There is a maximum of one mirror for each axis.
// They are independent of the pattern.
public record Mirror(
        List<MirrorTransformer> transformers
) {

    public static final Mirror EMPTY = new Mirror(List.of());

    // Use the maximum size. Thus the mirror covers all positions in reach.
    public static MirrorTransformer create(Vector3d position, Axis axis) {
        return new MirrorTransformer(position, axis).withSize(MirrorTransformer.SIZE_RANGE.max());
    }

    public Optional<MirrorTransformer> get(Axis axis) {
        return transformers.stream().filter(transformer -> transformer.axis() == axis).findFirst();
    }

    public boolean has(Axis axis) {
        return get(axis).isPresent();
    }

    public boolean isEmpty() {
        return transformers.isEmpty();
    }

    // Replaces the mirror on the same axis.
    public Mirror with(MirrorTransformer transformer) {
        return new Mirror(Stream.concat(without(transformer.axis()).transformers().stream(), Stream.of(transformer)).sorted(Comparator.comparing(MirrorTransformer::axis)).toList());
    }

    public Mirror without(Axis axis) {
        return new Mirror(transformers.stream().filter(transformer -> transformer.axis() != axis).toList());
    }

    public float volumeMultiplier() {
        var multiplier = 1f;
        for (var transformer : transformers) {
            if (transformer.isValid()) {
                multiplier = multiplier * transformer.volumeMultiplier();
            }
        }
        return multiplier;
    }

}
