package dev.huskuraft.effortless.renderer.pattern;

import java.awt.*;

import dev.huskuraft.universal.api.core.Axis;
import dev.huskuraft.universal.api.math.Vector3d;
import dev.huskuraft.universal.api.platform.ClientEntrance;
import dev.huskuraft.universal.api.renderer.Renderer;

public class MirrorPlaneRenderer extends TransformerRenderer {

    private static final int PLACING_ALPHA = 96;
    private static final int PLACED_ALPHA = 28;

    public MirrorPlaneRenderer(ClientEntrance entrance) {
        super(entrance);
    }

    private static Color getColor(Axis axis, int alpha) {
        return switch (axis) {
            case X -> new Color(235, 64, 64, alpha);
            case Y -> new Color(64, 220, 64, alpha);
            case Z -> new Color(64, 128, 235, alpha);
        };
    }

    // Moves the position along the plane to the camera. Thus the plane fills the render distance in all directions.
    private static Vector3d alignToCamera(Vector3d position, Vector3d camera, Axis... axes) {
        for (var axis : axes) {
            position = switch (axis) {
                case X -> position.withX(camera.x());
                case Y -> position.withY(camera.y());
                case Z -> position.withZ(camera.z());
            };
        }
        return position;
    }

    @Override
    public void render(Renderer renderer, float deltaTick) {
        var player = getEntrance().getClient().getPlayer();
        if (player == null) {
            return;
        }
        var structureBuilder = getEntrance().getStructureBuilder();
        var camera = renderer.getCamera().position();
        var range = getEntrance().getClientManager().getRunningClient().getOptions().renderDistance() * 16 + 16;

        for (var transformer : structureBuilder.getContext(player).mirror().transformers()) {
            renderPlane(renderer, transformer.position(), transformer.axis(), camera, range, getColor(transformer.axis(), PLACED_ALPHA));
        }

        var placement = structureBuilder.getMirrorPlacement(player, deltaTick);
        if (placement == null) {
            return;
        }
        renderPlane(renderer, placement.position(), placement.axis(), camera, range, getColor(placement.axis(), PLACING_ALPHA));
        // Draw lines on the plane through the target position.
        for (var axis : Axis.values()) {
            if (axis == placement.axis()) {
                continue;
            }
            var center = alignToCamera(placement.position(), camera, axis);
            var offset = switch (axis) {
                case X -> new Vector3d(range, 0, 0);
                case Y -> new Vector3d(0, range, 0);
                case Z -> new Vector3d(0, 0, range);
            };
            renderAACuboidLine(renderer, center.sub(offset), center.add(offset), 1 / 32f, getColor(placement.axis(), 255).getRGB(), true);
        }
    }

    private void renderPlane(Renderer renderer, Vector3d position, Axis axis, Vector3d camera, int range, Color color) {
        var inPlaneAxes = switch (axis) {
            case X -> new Axis[]{Axis.Y, Axis.Z};
            case Y -> new Axis[]{Axis.X, Axis.Z};
            case Z -> new Axis[]{Axis.X, Axis.Y};
        };
        renderPlaneByAxis(renderer, alignToCamera(position, camera, inPlaneAxes), range, axis, color);
    }

}
