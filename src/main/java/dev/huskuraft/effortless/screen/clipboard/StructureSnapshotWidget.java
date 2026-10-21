package dev.huskuraft.effortless.screen.clipboard;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import dev.huskuraft.universal.api.core.BlockPosition;
import dev.huskuraft.universal.api.gui.AbstractWidget;
import dev.huskuraft.universal.api.math.BoundingBox3i;
import dev.huskuraft.universal.api.math.MathUtils;
import dev.huskuraft.universal.api.math.Vector3d;
import dev.huskuraft.universal.api.math.Vector3f;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;
import dev.huskuraft.effortless.building.clipboard.Snapshot;
import dev.huskuraft.effortless.renderer.opertaion.BlockRenderLayers;

public final class StructureSnapshotWidget extends AbstractWidget {

    private static final float DEFAULT_YAW = 45f;
    private static final float DEFAULT_PITCH = -30f;
    private static final float MIN_ZOOM = 0.5f;
    private static final float MAX_ZOOM = 4f;
    private static final double MIN_VIEW_SPAN = 6;

    private static final int AXIS_LENGTH = 12;
    private static final int AXIS_X_COLOR = 0xFFFF5555;
    private static final int AXIS_Y_COLOR = 0xFF55FF55;
    private static final int AXIS_Z_COLOR = 0xFF5599FF;

    private static final int ORIGIN_FILL_COLOR = 0x553F8CFF;
    private static final int ORIGIN_SELECTED_FILL_COLOR = 0x903F8CFF;
    private static final int ORIGIN_OUTLINE_COLOR = 0xFF3F8CFF;
    private static final int ORIGIN_XRAY_COLOR = 0x703F8CFF;
    private static final int REGION_OUTLINE_COLOR = 0x50FFFFFF;
    private static final int HOVER_OUTLINE_COLOR = 0xC0FFFFFF;
    // In screen pixels. Thus the zoom does not change the width.
    private static final double OUTLINE_WIDTH = 1.5;
    private static final double REGION_OUTLINE_WIDTH = 1;
    // Prevents z-fighting with the faces of a block at the same position.
    private static final double HIGHLIGHT_INFLATE = 0.02;

    private Snapshot snapshot;
    private Set<BlockPosition> pickableBlocks = Set.of();
    private boolean interactive = false;
    private boolean originSelected = false;
    private Consumer<BlockPosition> blockRightClickListener;
    private Text hint = Text.empty();
    private Text originHint = Text.empty();
    private Text label = Text.empty();
    private float yaw = DEFAULT_YAW;
    private float pitch = DEFAULT_PITCH;
    private float zoom = 1f;

    // Values from the last render. pickBlock uses them.
    private double lastScale = 0;
    private double lastRadius = 0;
    private Vector3d lastCenter = Vector3d.ZERO;
    @Nullable
    private BlockPosition hoveredBlock;

    public StructureSnapshotWidget(Entrance entrance, int x, int y, int width, int height, Snapshot snapshot) {
        super(entrance, x, y, width, height, Text.empty());
        setSnapshot(snapshot);
    }

    @Override
    public void onCreate() {
    }

    // Turns a position relative to the view center, the same as the pose in renderWidget. The scale is not applied.
    private Vector3d project(Vector3d vector) {
        return vector.rotY(Math.toRadians(yaw)).rotX(Math.toRadians(pitch));
    }

    private Vector3d unproject(Vector3d vector) {
        return vector.rotX(-Math.toRadians(pitch)).rotY(-Math.toRadians(yaw));
    }

    // The copied area and the origin. The origin can be outside the copied area.
    private BoundingBox3i getViewRegion() {
        var region = snapshot.getRegion();
        if (region == null) {
            return BoundingBox3i.of(0, 0, 0, 0, 0, 0);
        }
        return BoundingBox3i.of(Math.min(region.minX(), 0), Math.min(region.minY(), 0), Math.min(region.minZ(), 0), Math.max(region.maxX(), 0), Math.max(region.maxY(), 0), Math.max(region.maxZ(), 0));
    }

    @Override
    public void renderWidget(Renderer renderer, int mouseX, int mouseY, float deltaTick) {
        var viewRegion = getViewRegion();
        var center = new Vector3d((viewRegion.minX() + viewRegion.maxX() + 1) / 2.0, (viewRegion.minY() + viewRegion.maxY() + 1) / 2.0, (viewRegion.minZ() + viewRegion.maxZ() + 1) / 2.0);
        var minX = 0.0;
        var maxX = 0.0;
        var minY = 0.0;
        var maxY = 0.0;
        var radius = 1.0;
        for (var cornerX : new int[]{viewRegion.minX(), viewRegion.maxX() + 1}) {
            for (var cornerY : new int[]{viewRegion.minY(), viewRegion.maxY() + 1}) {
                for (var cornerZ : new int[]{viewRegion.minZ(), viewRegion.maxZ() + 1}) {
                    var offset = new Vector3d(cornerX, cornerY, cornerZ).sub(center);
                    radius = Math.max(radius, offset.length());
                    var projected = project(offset);
                    minX = Math.min(minX, projected.x());
                    maxX = Math.max(maxX, projected.x());
                    minY = Math.min(minY, projected.y());
                    maxY = Math.max(maxY, projected.y());
                }
            }
        }
        // The view is centered. Thus it must fit the larger side.
        var spanX = Math.max(Math.max(-minX, maxX) * 2, MIN_VIEW_SPAN);
        var spanY = Math.max(Math.max(-minY, maxY) * 2, MIN_VIEW_SPAN);
        var scale = Math.min(getWidth() / spanX, getHeight() / spanY) * zoom;

        this.lastScale = scale;
        this.lastRadius = radius;
        this.lastCenter = center;
        this.hoveredBlock = interactive && isMouseOver(mouseX, mouseY) ? pickBlock(mouseX, mouseY) : null;

        renderer.pushScissor(getX(), getY(), getWidth(), getHeight());
        renderer.pushPose();
        renderer.translate(getX() + getWidth() / 2f, getY() + getHeight() / 2f, 100);
        // Decrease the depth to keep the blocks between the background and the overlay. Tooltips use a depth of 400.
        renderer.scale(-scale, -scale, -Math.min(scale, 100 / radius));

        renderer.rotate(Vector3f.XP.rotationDegrees(pitch));
        renderer.rotate(Vector3f.YP.rotationDegrees(yaw));
        renderer.pushPose();
        renderer.translate(-center.x(), -center.y(), -center.z());
        // Use full light. The light of the world at these positions can make the preview dark.
        for (var blockData : snapshot.blockData()) {
            if (blockData.blockState() != null) {
                renderer.pushPose();
                renderer.translate(blockData.blockPosition().x(), blockData.blockPosition().y(), blockData.blockPosition().z());
                renderer.renderBlockStateFullBright(BlockRenderLayers.block(Color.WHITE.getRGB()), getEntrance().getClient().getWorld(), blockData.blockPosition().add(getEntrance().getClient().getPlayer().getPosition().toVector3i()), blockData.blockState());
                renderer.popPose();
            }
            if (blockData.blockEntity() != null) {
                renderer.pushPose();
                renderer.translate(blockData.blockPosition().x(), blockData.blockPosition().y(), blockData.blockPosition().z());
                renderer.renderBlockEntityFullBright(BlockRenderLayers.block(Color.WHITE.getRGB()), getEntrance().getClient().getWorld(), blockData.blockPosition().add(getEntrance().getClient().getPlayer().getPosition().toVector3i()), blockData.blockEntity());
                renderer.popPose();
            }
        }
        // The highlights are translucent. Draw the blocks behind them first.
        renderer.flush();
        if (!snapshot.isEmpty()) {
            var region = snapshot.getRegion();
            renderEdges(renderer, region.minX(), region.minY(), region.minZ(), region.maxX() + 1, region.maxY() + 1, region.maxZ() + 1, REGION_OUTLINE_WIDTH / scale, REGION_OUTLINE_COLOR);
            renderBox(renderer, -HIGHLIGHT_INFLATE, -HIGHLIGHT_INFLATE, -HIGHLIGHT_INFLATE, 1 + HIGHLIGHT_INFLATE, 1 + HIGHLIGHT_INFLATE, 1 + HIGHLIGHT_INFLATE, originSelected ? ORIGIN_SELECTED_FILL_COLOR : ORIGIN_FILL_COLOR);
            renderEdges(renderer, -HIGHLIGHT_INFLATE, -HIGHLIGHT_INFLATE, -HIGHLIGHT_INFLATE, 1 + HIGHLIGHT_INFLATE, 1 + HIGHLIGHT_INFLATE, 1 + HIGHLIGHT_INFLATE, OUTLINE_WIDTH / scale, ORIGIN_OUTLINE_COLOR);
            if (hoveredBlock != null) {
                renderEdges(renderer, hoveredBlock.x() - HIGHLIGHT_INFLATE, hoveredBlock.y() - HIGHLIGHT_INFLATE, hoveredBlock.z() - HIGHLIGHT_INFLATE, hoveredBlock.x() + 1 + HIGHLIGHT_INFLATE, hoveredBlock.y() + 1 + HIGHLIGHT_INFLATE, hoveredBlock.z() + 1 + HIGHLIGHT_INFLATE, OUTLINE_WIDTH / scale, HOVER_OUTLINE_COLOR);
            }
        }
        renderer.flush();
        renderer.popPose();
        renderer.popPose();

        if (!snapshot.isEmpty()) {
            // Draw the origin again with a flat depth. Thus the blocks do not hide it.
            renderer.pushPose();
            renderer.translate(getX() + getWidth() / 2f, getY() + getHeight() / 2f, 250);
            renderer.scale(-scale, -scale, -0.01);
            renderer.rotate(Vector3f.XP.rotationDegrees(pitch));
            renderer.rotate(Vector3f.YP.rotationDegrees(yaw));
            renderer.translate(-center.x(), -center.y(), -center.z());
            renderEdges(renderer, 0, 0, 0, 1, 1, 1, OUTLINE_WIDTH / scale, ORIGIN_XRAY_COLOR);
            renderer.flush();
            renderer.popPose();
        }

        renderer.pushPose();
        renderer.translate(0, 0, 300);
        if (interactive) {
            renderAxes(renderer, getRight() - AXIS_LENGTH - 8, getBottom() - AXIS_LENGTH - 8);
        }
        renderer.renderText(getTypeface(), hint, getX() + 4, getY() + 4, 0xFFFFFFFF, true);
        renderer.renderText(getTypeface(), originHint, getX() + 4, getY() + 4 + getTypeface().getLineHeight() + 1, 0xFFFFFFFF, true);
        renderer.renderText(getTypeface(), label, getX() + 4, getBottom() - getTypeface().getLineHeight() - 3, 0xFFFFFFFF, true);
        renderer.popPose();
        renderer.popScissor();
    }

    private static void renderBox(Renderer renderer, double x0, double y0, double z0, double x1, double y1, double z1, int color) {
        var layer = BlockRenderLayers.planes();
        var xyz = new Vector3d(x0, y0, z0);
        var Xyz = new Vector3d(x1, y0, z0);
        var xYz = new Vector3d(x0, y1, z0);
        var XYz = new Vector3d(x1, y1, z0);
        var xyZ = new Vector3d(x0, y0, z1);
        var XyZ = new Vector3d(x1, y0, z1);
        var xYZ = new Vector3d(x0, y1, z1);
        var XYZ = new Vector3d(x1, y1, z1);
        renderer.renderQuad(layer, xYz, XYz, Xyz, xyz, 0, color, null);
        renderer.renderQuad(layer, XYZ, xYZ, xyZ, XyZ, 0, color, null);
        renderer.renderQuad(layer, XYz, XYZ, XyZ, Xyz, 0, color, null);
        renderer.renderQuad(layer, xYZ, xYz, xyz, xyZ, 0, color, null);
        renderer.renderQuad(layer, xYZ, XYZ, XYz, xYz, 0, color, null);
        renderer.renderQuad(layer, xyz, Xyz, XyZ, xyZ, 0, color, null);
    }

    private static void renderEdges(Renderer renderer, double x0, double y0, double z0, double x1, double y1, double z1, double width, int color) {
        var w = width / 2;
        for (var y : new double[]{y0, y1}) {
            for (var z : new double[]{z0, z1}) {
                renderBox(renderer, x0 - w, y - w, z - w, x1 + w, y + w, z + w, color);
            }
        }
        for (var x : new double[]{x0, x1}) {
            for (var z : new double[]{z0, z1}) {
                renderBox(renderer, x - w, y0 + w, z - w, x + w, y1 - w, z + w, color);
            }
        }
        for (var x : new double[]{x0, x1}) {
            for (var y : new double[]{y0, y1}) {
                renderBox(renderer, x - w, y - w, z0 + w, x + w, y + w, z1 - w, color);
            }
        }
    }

    // Finds the first block or origin under the mouse. It walks the grid along the view ray with the Amanatides and Woo algorithm.
    @Nullable
    private BlockPosition pickBlock(double mouseX, double mouseY) {
        if (lastScale <= 0 || snapshot.isEmpty()) {
            return null;
        }
        var screenCenterX = getX() + getWidth() / 2.0;
        var screenCenterY = getY() + getHeight() / 2.0;
        // The pose flips x and y. The screen depth increases toward the viewer, but the turned z increases away from the viewer.
        var point = unproject(new Vector3d((screenCenterX - mouseX) / lastScale, (screenCenterY - mouseY) / lastScale, 0)).add(lastCenter);
        var direction = unproject(new Vector3d(0, 0, 1));
        var length = lastRadius * 2 + 4;
        var start = point.sub(direction.mul(length / 2));

        var x = (int) Math.floor(start.x());
        var y = (int) Math.floor(start.y());
        var z = (int) Math.floor(start.z());
        var stepX = direction.x() > 0 ? 1 : -1;
        var stepY = direction.y() > 0 ? 1 : -1;
        var stepZ = direction.z() > 0 ? 1 : -1;
        var deltaX = direction.x() == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / direction.x());
        var deltaY = direction.y() == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / direction.y());
        var deltaZ = direction.z() == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / direction.z());
        var nextX = direction.x() == 0 ? Double.POSITIVE_INFINITY : (direction.x() > 0 ? x + 1 - start.x() : start.x() - x) * deltaX;
        var nextY = direction.y() == 0 ? Double.POSITIVE_INFINITY : (direction.y() > 0 ? y + 1 - start.y() : start.y() - y) * deltaY;
        var nextZ = direction.z() == 0 ? Double.POSITIVE_INFINITY : (direction.z() > 0 ? z + 1 - start.z() : start.z() - z) * deltaZ;
        var distance = 0.0;
        while (distance <= length) {
            var blockPosition = BlockPosition.at(x, y, z);
            if (pickableBlocks.contains(blockPosition)) {
                return blockPosition;
            }
            if (nextX < nextY && nextX < nextZ) {
                x += stepX;
                distance = nextX;
                nextX += deltaX;
            } else if (nextY < nextZ) {
                y += stepY;
                distance = nextY;
                nextY += deltaY;
            } else {
                z += stepZ;
                distance = nextZ;
                nextZ += deltaZ;
            }
        }
        return null;
    }

    private void renderAxes(Renderer renderer, int originX, int originY) {
        renderAxis(renderer, originX, originY, new Vector3d(1, 0, 0), "X", AXIS_X_COLOR);
        renderAxis(renderer, originX, originY, new Vector3d(0, 1, 0), "Y", AXIS_Y_COLOR);
        renderAxis(renderer, originX, originY, new Vector3d(0, 0, 1), "Z", AXIS_Z_COLOR);
    }

    private void renderAxis(Renderer renderer, int originX, int originY, Vector3d axis, String name, int color) {
        // The pose has a negative scale. Thus the projected axes flip on the screen.
        var projected = project(axis);
        var dx = -projected.x();
        var dy = -projected.y();
        for (var i = 0; i <= AXIS_LENGTH; i++) {
            var x = originX + (int) Math.round(dx * i);
            var y = originY + (int) Math.round(dy * i);
            renderer.renderRect(x, y, x + 1, y + 1, color);
        }
        var labelX = originX + (int) Math.round(dx * (AXIS_LENGTH + 5)) - getTypeface().measureWidth(name) / 2;
        var labelY = originY + (int) Math.round(dy * (AXIS_LENGTH + 5)) - getTypeface().getLineHeight() / 2;
        renderer.renderText(getTypeface(), name, labelX, labelY, color, true);
    }

    private int backgroundColor = 0xdc000000;


    @Override
    public void renderWidgetBackground(Renderer renderer, int mouseX, int mouseY, float deltaTick) {
        renderer.renderGradientRect(getLeft(), getTop(), getRight(), getBottom(), backgroundColor, backgroundColor);
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (interactive && button == 1 && isMouseOver(mouseX, mouseY)) {
            var blockPosition = pickBlock(mouseX, mouseY);
            if (blockPosition != null && blockRightClickListener != null) {
                blockRightClickListener.accept(blockPosition);
            } else {
                resetView();
            }
            return true;
        }
        return super.onMouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!interactive) {
            return false;
        }
        this.yaw = (float) ((yaw + deltaX * 1.5) % 360);
        this.pitch = (float) MathUtils.clamp(pitch - deltaY * 1.5, -90, 90);
        return true;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double amountX, double amountY) {
        if (!interactive) {
            return false;
        }
        this.zoom = (float) MathUtils.clamp(zoom * Math.pow(1.1, amountY), MIN_ZOOM, MAX_ZOOM);
        return true;
    }

    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public void setSnapshot(Snapshot snapshot) {
        if (snapshot == this.snapshot) {
            return;
        }
        this.snapshot = snapshot;
        var pickableBlocks = new HashSet<BlockPosition>();
        for (var blockData : snapshot.blockData()) {
            if (blockData.blockState() != null) {
                pickableBlocks.add(blockData.blockPosition());
            }
        }
        // The player can also select the origin if it is air.
        pickableBlocks.add(BlockPosition.at(0, 0, 0));
        this.pickableBlocks = pickableBlocks;
    }

    // If true, the player can drag to turn the preview, scroll to zoom, and right click a block.
    // A right click on an empty position resets the view.
    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
        setFocusable(interactive);
        this.hint = interactive ? Text.translate("effortless.clipboard.editor.preview.hint").withStyle(ChatFormatting.GRAY) : Text.empty();
    }

    // The listener gets positions relative to the origin.
    public void setOnBlockRightClicked(Consumer<BlockPosition> listener) {
        this.blockRightClickListener = listener;
        this.originHint = listener != null ? Text.translate("effortless.clipboard.editor.preview.origin_hint").withStyle(ChatFormatting.GRAY) : Text.empty();
    }

    public void setOriginSelected(boolean originSelected) {
        this.originSelected = originSelected;
    }

    public void setLabel(Text label) {
        this.label = label;
    }

    public void resetView() {
        this.yaw = DEFAULT_YAW;
        this.pitch = DEFAULT_PITCH;
        this.zoom = 1f;
    }
}
