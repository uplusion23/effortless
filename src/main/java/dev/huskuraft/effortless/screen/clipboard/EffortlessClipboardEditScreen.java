package dev.huskuraft.effortless.screen.clipboard;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import dev.huskuraft.universal.api.core.Axis;
import dev.huskuraft.universal.api.core.BlockPosition;
import dev.huskuraft.universal.api.gui.AbstractPanelScreen;
import dev.huskuraft.universal.api.gui.AbstractWidget;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.gui.tooltip.TooltipHelper;
import dev.huskuraft.universal.api.input.Keys;
import dev.huskuraft.universal.api.math.Vector3i;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.Text;
import dev.huskuraft.effortless.building.clipboard.Orientation;
import dev.huskuraft.effortless.building.clipboard.Snapshot;
import dev.huskuraft.effortless.building.clipboard.SnapshotTransform;

public class EffortlessClipboardEditScreen extends AbstractPanelScreen {

    // The maximum distance of the origin from the copied area.
    private static final int MAX_ORIGIN_OFFSET = 32;
    private static final int ORIGIN_BUTTON_SIZE = 20;
    private static final int ORIGIN_BUTTON_SPACING = 2;

    private final Consumer<Snapshot> consumer;
    private final Snapshot originalSnapshot;
    // The snapshot is the original snapshot with the orientation and the offset applied.
    private Orientation orientation = Orientation.IDENTITY;
    private Vector3i offset = Vector3i.ZERO;
    private Snapshot snapshot;
    private boolean isOriginSelected = false;
    private AbstractWidget titleTextWidget;
    private StructureSnapshotWidget snapshotWidget;
    private final Map<Vector3i, Button> originButtons = new LinkedHashMap<>();
    private Button rotateXButton;
    private Button rotateYButton;
    private Button rotateZButton;
    private Button flipXButton;
    private Button flipYButton;
    private Button flipZButton;
    private Button cancelButton;
    private Button resetButton;
    private Button doneButton;

    public EffortlessClipboardEditScreen(Entrance entrance, Snapshot snapshot, Consumer<Snapshot> consumer) {
        super(entrance, Text.translate("effortless.clipboard.editor.title"), PANEL_WIDTH_60, PANEL_HEIGHT_FULL);
        this.consumer = consumer;
        this.originalSnapshot = snapshot;
        this.snapshot = snapshot;
    }

    @Override
    public void onCreate() {
        this.titleTextWidget = addWidget(new TextWidget(getEntrance(), getLeft() + getWidth() / 2, getTop() + PANEL_TITLE_HEIGHT_1 - 10, getScreenTitle().withColor(AbstractPanelScreen.TITLE_COLOR), TextWidget.Gravity.CENTER));

        this.snapshotWidget = addWidget(new StructureSnapshotWidget(getEntrance(), getLeft() + PADDINGS_H, getTop() + PANEL_TITLE_HEIGHT_1, getWidth() - PADDINGS_H * 2, getHeight() - PANEL_TITLE_HEIGHT_1 - PANEL_BUTTON_ROW_HEIGHT_3, snapshot));
        this.snapshotWidget.setInteractive(true);
        this.snapshotWidget.setOnBlockRightClicked(blockPosition -> {
            if (blockPosition.equals(BlockPosition.at(0, 0, 0))) {
                this.isOriginSelected = !isOriginSelected;
                onReload();
            } else {
                moveOrigin(blockPosition.toVector3i());
            }
        });

        // Two columns of buttons for each axis, in the top right corner of the preview.
        // They show after a right click on the origin.
        var directions = new Vector3i[]{Vector3i.UNIT_MINUS_X, Vector3i.UNIT_X, Vector3i.UNIT_MINUS_Y, Vector3i.UNIT_Y, Vector3i.UNIT_MINUS_Z, Vector3i.UNIT_Z};
        for (var i = 0; i < directions.length; i++) {
            var direction = directions[i];
            var x = snapshotWidget.getRight() - 4 - (2 - i % 2) * ORIGIN_BUTTON_SIZE - (1 - i % 2) * ORIGIN_BUTTON_SPACING;
            var y = snapshotWidget.getTop() + 4 + (i / 2) * (ORIGIN_BUTTON_SIZE + ORIGIN_BUTTON_SPACING);
            originButtons.put(direction, addWidget(Button.builder(getEntrance(), getDirectionName(direction), button -> {
                moveOrigin(direction);
            }).setBounds(x, y, ORIGIN_BUTTON_SIZE, ORIGIN_BUTTON_SIZE).build()));
        }

        this.rotateXButton = addWidget(Button.builder(getEntrance(), Text.translate("effortless.clipboard.editor.rotate_x"), button -> {
            rotate(SnapshotTransform.ROTATE_X);
        }).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 2f, 0f, 1 / 3f).build());
        this.rotateYButton = addWidget(Button.builder(getEntrance(), Text.translate("effortless.clipboard.editor.rotate_y"), button -> {
            rotate(SnapshotTransform.ROTATE_Y);
        }).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 2f, 1 / 3f, 1 / 3f).build());
        this.rotateZButton = addWidget(Button.builder(getEntrance(), Text.translate("effortless.clipboard.editor.rotate_z"), button -> {
            rotate(SnapshotTransform.ROTATE_Z);
        }).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 2f, 2 / 3f, 1 / 3f).build());

        this.flipXButton = addWidget(Button.builder(getEntrance(), Text.translate("effortless.clipboard.editor.flip_x"), button -> {
            transform(SnapshotTransform.MIRROR_X);
        }).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 1f, 0f, 1 / 3f).build());
        this.flipYButton = addWidget(Button.builder(getEntrance(), Text.translate("effortless.clipboard.editor.flip_y"), button -> {
            transform(SnapshotTransform.MIRROR_Y);
        }).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 1f, 1 / 3f, 1 / 3f).build());
        this.flipZButton = addWidget(Button.builder(getEntrance(), Text.translate("effortless.clipboard.editor.flip_z"), button -> {
            transform(SnapshotTransform.MIRROR_Z);
        }).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 1f, 2 / 3f, 1 / 3f).build());

        this.cancelButton = addWidget(Button.builder(getEntrance(), Text.translate("effortless.button.cancel"), button -> {
            detach();
        }).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 0f, 0f, 1 / 3f).build());
        this.resetButton = addWidget(Button.builder(getEntrance(), Text.translate("effortless.button.reset"), button -> {
            this.orientation = Orientation.IDENTITY;
            this.offset = Vector3i.ZERO;
            this.isOriginSelected = false;
            this.snapshot = originalSnapshot;
            this.snapshotWidget.resetView();
            onReload();
        }).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 0f, 1 / 3f, 1 / 3f).build());
        this.doneButton = addWidget(Button.builder(getEntrance(), Text.translate("effortless.button.done"), button -> {
            consumer.accept(snapshot);
            detach();
        }).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 0f, 2 / 3f, 1 / 3f).build());
    }

    private static Text getDirectionName(Vector3i direction) {
        var sign = direction.x() + direction.y() + direction.z() > 0 ? "+" : "-";
        if (direction.x() != 0) {
            return Text.text(sign + "X").withColor(0xFF5555);
        }
        if (direction.y() != 0) {
            return Text.text(sign + "Y").withColor(0x55FF55);
        }
        return Text.text(sign + "Z").withColor(0x5599FF);
    }

    private void rotate(SnapshotTransform transform) {
        // Three quarter turns are equal to one quarter turn in the opposite direction.
        var turns = Keys.KEY_LEFT_SHIFT.isDown() || Keys.KEY_RIGHT_SHIFT.isDown() ? 3 : 1;
        for (var i = 0; i < turns; i++) {
            turn(transform.getOrientation());
        }
        updateSnapshot();
    }

    private void transform(SnapshotTransform transform) {
        turn(transform.getOrientation());
        updateSnapshot();
    }

    // Turns around the origin. Thus the offset also turns.
    private void turn(Orientation turn) {
        this.orientation = orientation.then(turn);
        this.offset = turn.apply(offset);
    }

    // The position is relative to the current origin.
    private void moveOrigin(Vector3i position) {
        if (!canMoveOrigin(position)) {
            return;
        }
        this.offset = offset.sub(position);
        updateSnapshot();
    }

    private boolean canMoveOrigin(Vector3i position) {
        var outside = getOutsideOffset(position);
        return Math.abs(outside.x()) <= MAX_ORIGIN_OFFSET && Math.abs(outside.y()) <= MAX_ORIGIN_OFFSET && Math.abs(outside.z()) <= MAX_ORIGIN_OFFSET;
    }

    // Returns zero if the position is in the copied area.
    private Vector3i getOutsideOffset(Vector3i position) {
        var region = snapshot.getRegion();
        if (region == null) {
            return Vector3i.ZERO;
        }
        return new Vector3i(
                position.x() < region.minX() ? position.x() - region.minX() : Math.max(position.x() - region.maxX(), 0),
                position.y() < region.minY() ? position.y() - region.minY() : Math.max(position.y() - region.maxY(), 0),
                position.z() < region.minZ() ? position.z() - region.minZ() : Math.max(position.z() - region.maxZ(), 0)
        );
    }

    private int getOutsideDistance(Vector3i position) {
        var outside = getOutsideOffset(position);
        return Math.abs(outside.x()) + Math.abs(outside.y()) + Math.abs(outside.z());
    }

    // Always transform the original snapshot. Refer to Orientation.
    private void updateSnapshot() {
        this.snapshot = originalSnapshot.transform(orientation, offset);
        onReload();
    }

    @Override
    public void onReload() {
        var box = snapshot.box();
        var label = Text.translate("effortless.clipboard.editor.size", box.x(), box.y(), box.z(), snapshot.blockData().size());
        var originOffset = getOutsideOffset(Vector3i.ZERO);
        if (!originOffset.equals(Vector3i.ZERO)) {
            label = label.append(Text.translate("effortless.clipboard.editor.origin_offset", originOffset.x(), originOffset.y(), originOffset.z()));
        }
        this.snapshotWidget.setSnapshot(snapshot);
        this.snapshotWidget.setLabel(label);
        this.snapshotWidget.setOriginSelected(isOriginSelected);

        originButtons.forEach((direction, button) -> {
            button.setVisible(isOriginSelected);
            button.setActive(canMoveOrigin(direction));
            // Outside the copied area, the origin becomes an offset. The copied area does not change.
            var isExpanding = getOutsideDistance(direction) > getOutsideDistance(Vector3i.ZERO);
            var title = Text.translate(isExpanding ? "effortless.clipboard.editor.expand_origin" : "effortless.clipboard.editor.move_origin", getDirectionName(direction));
            button.setTooltip(TooltipHelper.makeSummary(getTypeface(), title, Text.translate(isExpanding ? "effortless.clipboard.editor.expand_origin.tooltip" : "effortless.clipboard.editor.move_origin.tooltip")));
        });

        this.rotateXButton.setTooltip(TooltipHelper.makeSummary(getTypeface(), Text.translate("effortless.clipboard.editor.rotate_x"), Text.translate("effortless.clipboard.editor.rotate.tooltip", Axis.X.getDisplayName())));
        this.rotateYButton.setTooltip(TooltipHelper.makeSummary(getTypeface(), Text.translate("effortless.clipboard.editor.rotate_y"), Text.translate("effortless.clipboard.editor.rotate.tooltip", Axis.Y.getDisplayName())));
        this.rotateZButton.setTooltip(TooltipHelper.makeSummary(getTypeface(), Text.translate("effortless.clipboard.editor.rotate_z"), Text.translate("effortless.clipboard.editor.rotate.tooltip", Axis.Z.getDisplayName())));
        this.flipXButton.setTooltip(TooltipHelper.makeSummary(getTypeface(), Text.translate("effortless.clipboard.editor.flip_x"), Text.translate("effortless.clipboard.editor.flip.tooltip", Axis.X.getDisplayName())));
        this.flipYButton.setTooltip(TooltipHelper.makeSummary(getTypeface(), Text.translate("effortless.clipboard.editor.flip_y"), Text.translate("effortless.clipboard.editor.flip.tooltip", Axis.Y.getDisplayName())));
        this.flipZButton.setTooltip(TooltipHelper.makeSummary(getTypeface(), Text.translate("effortless.clipboard.editor.flip_z"), Text.translate("effortless.clipboard.editor.flip.tooltip", Axis.Z.getDisplayName())));
        this.resetButton.setTooltip(TooltipHelper.makeSummary(getTypeface(), Text.translate("effortless.button.reset"), Text.translate("effortless.clipboard.editor.reset.tooltip")));
        this.resetButton.setActive(!orientation.equals(Orientation.IDENTITY) || !offset.equals(Vector3i.ZERO));
    }

}
