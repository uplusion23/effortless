package dev.huskuraft.effortless.building.session;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.huskuraft.universal.api.core.BlockInteraction;
import dev.huskuraft.universal.api.core.BlockState;
import dev.huskuraft.universal.api.core.InteractionHand;
import dev.huskuraft.universal.api.core.Items;
import dev.huskuraft.universal.api.core.Player;
import dev.huskuraft.universal.api.core.World;
import dev.huskuraft.universal.api.math.BoundingBox3i;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.tag.RecordTag;
import dev.huskuraft.effortless.building.BuildState;
import dev.huskuraft.effortless.building.BuildType;
import dev.huskuraft.effortless.building.Context;
import dev.huskuraft.effortless.building.Storage;
import dev.huskuraft.effortless.building.clipboard.Snapshot;
import dev.huskuraft.effortless.building.interceptor.BuildInterceptor;
import dev.huskuraft.effortless.building.interceptor.FtbChunksInterceptor;
import dev.huskuraft.effortless.building.interceptor.OpenPacInterceptor;
import dev.huskuraft.effortless.building.operation.Operation;
import dev.huskuraft.effortless.building.operation.OperationFilter;
import dev.huskuraft.effortless.building.operation.batch.BatchOperation;
import dev.huskuraft.effortless.building.operation.batch.BatchOperationResult;
import dev.huskuraft.effortless.building.operation.batch.DeferredBatchOperation;
import dev.huskuraft.effortless.building.operation.block.BlockInteractOperation;
import dev.huskuraft.effortless.building.operation.block.BlockOperation;
import dev.huskuraft.effortless.building.operation.block.BlockStateCopyOperation;
import dev.huskuraft.effortless.building.operation.block.BlockStateCopyOperationResult;
import dev.huskuraft.effortless.building.operation.block.BlockStateUpdateOperation;
import dev.huskuraft.effortless.building.pattern.randomize.ItemRandomizer;
import dev.huskuraft.effortless.networking.packets.player.PlayerSnapshotCapturePacket;

public class BatchBuildSession implements Session {

    private final Entrance entrance;
    private final Player player;
    private final World world;
    private final Context context;
    private final List<BuildInterceptor> interceptors;

    private BatchOperationResult lastResult;

    public BatchBuildSession(Entrance entrance, Player player, Context context) {
        this.entrance = entrance;
        this.world = player.getWorld();
        this.player = player;
        this.context = context;
        this.interceptors = createInterceptors(entrance, player.getWorld(), player, context);
    }

    @Override
    public World getWorld() {
        return world;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    private static List<BuildInterceptor> createInterceptors(Entrance entrance, World world, Player player, Context context) {
        return Stream.of(
                new OpenPacInterceptor(entrance),
                new FtbChunksInterceptor(entrance)
        ).filter(BuildInterceptor::isEnabled).collect(Collectors.toUnmodifiableList());
    }

    public Entrance getEntrance() {
        return entrance;
    }

    protected BlockOperation createBlockPlaceOperationFromInteraction(Player player, World world, Context context, Storage storage, BlockInteraction interaction, BlockState blockState, RecordTag entityTag) {
        return new BlockStateUpdateOperation(this, context, storage, interaction, blockState, entityTag, context.extras().extras());
    }

    protected BlockOperation createBlockBreakOperationFromInteraction(Player player, World world, Context context, Storage storage, BlockInteraction interaction) {
        return new BlockStateUpdateOperation(this, context, storage, interaction, Items.AIR.item().getBlock().getDefaultBlockState(), null, context.extras().extras());
    }

    protected BlockOperation createBlockInteractOperationFromInteraction(Player player, World world, Context context, Storage storage, BlockInteraction interaction) {
        return new BlockInteractOperation(this, context, storage, interaction, context.extras().extras());
    }

    protected BlockOperation createBlockCopyOperationFromInteraction(Player player, World world, Context context, Storage storage, BlockInteraction interaction) {
        return new BlockStateCopyOperation(this, context, storage, interaction, context.extras().extras());
    }

    protected BatchOperation create(World world, Player player, Context context) {
        var storage = Storage.create(player, context.isPreviewType() || context.isBuildClientType()); // TODO: 21/5/24 use storage from context
        var inHandTransformer = ItemRandomizer.single(null, player.getItemStack(InteractionHand.MAIN).getItem());
        var operations = (BatchOperation) new DeferredBatchOperation(context, () -> switch (context.buildState()) {
            case IDLE -> Stream.<BlockOperation>empty();
            case BREAK_BLOCK ->
                    context.collectInteractions().map(interaction -> createBlockBreakOperationFromInteraction(player, world, context, storage, interaction));
            case PLACE_BLOCK ->
                    context.collectInteractions().map(interaction -> createBlockPlaceOperationFromInteraction(player, world, context, storage, interaction, Items.AIR.item().getBlock().getDefaultBlockState(), null));
            case INTERACT_BLOCK ->
                    context.collectInteractions().map(interaction -> createBlockInteractOperationFromInteraction(player, world, context, storage, interaction));
            case COPY_STRUCTURE ->
                    context.collectInteractions().map(interaction -> createBlockCopyOperationFromInteraction(player, world, context, storage, interaction));
            case PASTE_STRUCTURE -> {
                var origin = context.getInteraction(0);
                var blocks = context.clipboard().snapshot().blockData().stream().map(blockSnapshot -> {
                    var interaction = origin.withBlockPosition(origin.blockPosition().add(blockSnapshot.blockPosition()));
                    return createBlockPlaceOperationFromInteraction(player, world, context, storage, interaction, blockSnapshot.blockState(), blockSnapshot.entityTag());
                });
                // A client can send a region of any size. Make sure that the region is in the paste limit before the iteration.
                if (!context.clipboard().pasteAir() || context.clipboard().snapshot().getRegionVolume() > context.configs().constraintConfig().maxStructureCopyPasteVolume()) {
                    yield blocks;
                }
                var air = context.clipboard().snapshot().getAirPositions().map(blockPosition -> createBlockBreakOperationFromInteraction(player, world, context, storage, origin.withBlockPosition(origin.blockPosition().add(blockPosition))));
                yield Stream.concat(air, blocks);
            }
            // Minecraft placed this block. Build only its mirrored copies.
            case MIRROR_BLOCK -> context.clipboard().snapshot().blockData().stream().limit(1).map(blockSnapshot -> createBlockPlaceOperationFromInteraction(player, world, context, storage, context.getInteraction(0), blockSnapshot.blockState(), null));
        });
        if (context.buildState() == BuildState.PLACE_BLOCK) {
            operations = (BatchOperation) inHandTransformer.transform(operations);
        }
        if (context.pattern().enabled()) {
            for (var transformer : context.pattern().transformers()) {
                if (transformer.isValid()) {
                    operations = (BatchOperation) transformer.transform(operations);
                }
            }
        }
        if (context.buildState() == BuildState.MIRROR_BLOCK) {
            operations = context.isMirrored() ? operations.flatten().map(operation -> mirrorCopies(context, operation)) : operations.filter(operation -> false);
        } else if (context.isMirrored()) {
            if (context.buildState() == BuildState.BREAK_BLOCK) {
                operations = operations.flatten().map(operation -> mirrorBreak(context, operation));
            } else {
                operations = (BatchOperation) mirror(context, operations);
            }
        }
        operations = operations.flatten().filter(Objects::nonNull).filter(OperationFilter.distinctBlockOperations());

        return operations;
    }

    private static Operation mirror(Context context, Operation operation) {
        for (var transformer : context.mirror().transformers()) {
            if (transformer.isValid()) {
                operation = transformer.transform(operation);
            }
        }
        return operation;
    }

    private static Operation mirrorCopies(Context context, Operation operation) {
        var mirrored = mirror(context, operation);
        if (!(operation instanceof BlockOperation blockOperation) || !(mirrored instanceof BatchOperation batchOperation)) {
            return mirrored;
        }
        var blockPosition = blockOperation.getBlockPosition();
        return batchOperation.flatten().filter(copy -> !(copy instanceof BlockOperation blockCopy) || !blockCopy.getBlockPosition().equals(blockPosition));
    }

    // Mirror each broken block separately. Thus the mirror break mode can compare each copy with the block.
    private static Operation mirrorBreak(Context context, Operation operation) {
        var mirrored = mirror(context, operation);
        if (!(operation instanceof BlockOperation blockOperation) || !(mirrored instanceof BatchOperation batchOperation)) {
            return mirrored;
        }
        var blockPosition = blockOperation.getBlockPosition();
        var blockState = blockOperation.getBlockStateInWorld();
        var mirrorBreakMode = context.configs().builderConfig().mirrorBreakMode();
        return batchOperation.flatten().filter(copy -> !(copy instanceof BlockOperation blockCopy) || blockCopy.getBlockPosition().equals(blockPosition) || mirrorBreakMode.shouldBreak(blockState, blockCopy.getBlockStateInWorld()));
    }

    public List<BuildInterceptor> getInterceptors() {
        return interceptors;
    }

    @Override
    public synchronized BatchOperationResult commit() {
        if (lastResult == null) {
            lastResult = create(world, player, context).commit();
            saveClipboard();
        }
        return lastResult;
    }

    protected void saveClipboard() {
        if (world.isClient()) {
            return;
        }
        if (context.buildState() != BuildState.COPY_STRUCTURE) {
            return;
        }
        if (context.buildType() != BuildType.BUILD) {
            return;
        }
        if (!context.clipboard().enabled()) {
            return;
        }
        var results = lastResult.getResults().stream().map(BlockStateCopyOperationResult.class::cast).toList();
        // The block data does not contain air. The region contains all copied positions, air included.
        var region = results.isEmpty() ? null : BoundingBox3i.of(
                results.stream().mapToInt(result -> result.getRelativePosition().x()).min().getAsInt(),
                results.stream().mapToInt(result -> result.getRelativePosition().y()).min().getAsInt(),
                results.stream().mapToInt(result -> result.getRelativePosition().z()).min().getAsInt(),
                results.stream().mapToInt(result -> result.getRelativePosition().x()).max().getAsInt(),
                results.stream().mapToInt(result -> result.getRelativePosition().y()).max().getAsInt(),
                results.stream().mapToInt(result -> result.getRelativePosition().z()).max().getAsInt()
        );
        var snapshot = new Snapshot("", System.currentTimeMillis(), results.stream().map(BlockStateCopyOperationResult::getBlockData).filter(blockData -> context.clipboard().copyAir() || (blockData.blockState() != null && !blockData.blockState().isAir())).toList(), region);
        getEntrance().getChannel().sendPacket(new PlayerSnapshotCapturePacket(player.getId(), snapshot), player);
    }

}
