package dev.huskuraft.effortless.building.clipboard;

import java.util.List;

import dev.huskuraft.effortless.building.Option;

public record Clipboard(
        boolean enabled,
        Snapshot snapshot,
        // If true, the air in the copied area replaces the blocks in the world.
        boolean pasteAir
) implements Option  {
    public static Clipboard DISABLED = new Clipboard(false, new Snapshot("", 0, List.of()), false);

    public static Clipboard of(boolean enabled, Snapshot snapshot) {
        return new Clipboard(enabled, snapshot, false);
    }

    public Clipboard withEnabled(boolean enabled) {
        return new Clipboard(enabled, snapshot, pasteAir);
    }

    public Clipboard withSnapshot(Snapshot snapshot) {
        return new Clipboard(enabled, snapshot, pasteAir);
    }

    public Clipboard withPasteAir(boolean pasteAir) {
        return new Clipboard(enabled, snapshot, pasteAir);
    }

    public Clipboard toggled() {
        return new Clipboard(!enabled, snapshot, pasteAir);
    }

    public boolean isEmpty() {
        return snapshot.isEmpty();
    }

    public boolean copyAir() {
        return false;
    }

    public int getPasteSize() {
        return pasteAir ? (int) Math.min(snapshot.getRegionVolume(), Integer.MAX_VALUE) : snapshot.blockData().size();
    }

    @Override
    public String getName() {
        if (enabled) {
            return "clipboard_enabled";
        } else {
            return "clipboard_disabled";
        }
    }

    @Override
    public String getCategory() {
        return "clipboard";
    }
}
