package dev.huskuraft.effortless.screen.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.EffortlessKeys;
import dev.huskuraft.effortless.building.config.BuilderConfig;
import dev.huskuraft.effortless.building.config.ClientConfig;
import dev.huskuraft.effortless.building.mirror.MirrorBreakMode;
import dev.huskuraft.universal.api.gui.AbstractPanelScreen;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.gui.tooltip.TooltipHelper;
import dev.huskuraft.universal.api.input.Key;
import dev.huskuraft.universal.api.input.KeyBindingOwner;
import dev.huskuraft.universal.api.input.KeyCodes;
import dev.huskuraft.universal.api.input.OptionKeys;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;

// Settings for the mirror break mode and the Effortless key bindings. The key bindings are saved with the game options.
public class EffortlessControlsSettingsScreen extends AbstractPanelScreen {

    // Key bindings change immediately. Cancel restores these keys.
    private final Map<EffortlessKeys, Key> originalKeys = new EnumMap<>(EffortlessKeys.class);
    private final List<SettingOptionsList.ButtonEntry<EffortlessKeys>> keyEntries = new ArrayList<>();
    private ClientConfig config;
    private EffortlessKeys listeningKey = null;
    private SettingOptionsList.SelectorEntry<MirrorBreakMode> mirrorBreakModeEntry;

    public EffortlessControlsSettingsScreen(Entrance entrance) {
        super(entrance, Text.translate("effortless.controls_settings.title"), PANEL_WIDTH_60, PANEL_HEIGHT_FULL);
        this.config = getEntrance().getConfigStorage().get();
        for (var key : EffortlessKeys.values()) {
            originalKeys.put(key, key.getKeyBinding().getKey());
        }
    }

    @Override
    protected EffortlessClient getEntrance() {
        return (EffortlessClient) super.getEntrance();
    }

    @Override
    public void onCreate() {
        addWidget(new TextWidget(getEntrance(), getLeft() + getWidth() / 2, getTop() + PANEL_TITLE_HEIGHT_1 - 10, getScreenTitle().withColor(AbstractPanelScreen.TITLE_COLOR), TextWidget.Gravity.CENTER));

        var entries = addWidget(new SettingOptionsList(getEntrance(), getLeft() + PADDINGS_H, getTop() + PANEL_TITLE_HEIGHT_1, getWidth() - PADDINGS_H * 2 - 8, getHeight() - PANEL_TITLE_HEIGHT_1 - PANEL_BUTTON_ROW_HEIGHT_1, false, false));

        this.mirrorBreakModeEntry = entries.addSelectorEntry(Text.translate("effortless.controls_settings.mirror_break_mode"), null,
                Arrays.stream(MirrorBreakMode.values()).map(mode -> Text.translate("effortless.mirror_break_mode.%s".formatted(mode.getName()))).toList(),
                List.of(MirrorBreakMode.values()),
                config.builderConfig().mirrorBreakMode(),
                value -> this.config = this.config.withBuilderConfig(this.config.builderConfig().withMirrorBreakMode(value)));

        for (var key : EffortlessKeys.values()) {
            keyEntries.add(entries.addTab(Text.translate(key.getKeyBinding().getName()), null, key, null, (entry, value) -> {
                entry.getButton().setMessage(getKeyMessage(value));
                entry.getButton().setOnPressListener(button -> {
                    this.listeningKey = value;
                    updateKeyEntries();
                });
            }));
        }

        addWidget(Button.builder(getEntrance(), Text.translate("effortless.button.cancel"), button -> {
            originalKeys.forEach((key, originalKey) -> {
                if (!key.getKeyBinding().getKey().getName().equals(originalKey.getName())) {
                    key.getKeyBinding().setKey(originalKey);
                }
            });
            detach();
        }).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 0f, 0f, 1 / 3f).build());

        addWidget(Button.builder(getEntrance(), Text.translate("effortless.button.reset"), button -> {
            for (var key : EffortlessKeys.values()) {
                key.getKeyBinding().setKey(key.getKeyBinding().getDefaultKey());
            }
            this.listeningKey = null;
            this.mirrorBreakModeEntry.setItem(BuilderConfig.DEFAULT.mirrorBreakMode());
            updateKeyEntries();
        }).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 0f, 1 / 3f, 1 / 3f).build());

        addWidget(Button.builder(getEntrance(), Text.translate("effortless.button.save"), button -> {
            getEntrance().getConfigStorage().update(config -> this.config);
            detachAll();
        }).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 0f, 2 / 3f, 1 / 3f).build());
    }

    @Override
    public void onReload() {
        var tooltip = new ArrayList<Text>();
        tooltip.add(Text.translate("effortless.controls_settings.mirror_break_mode").withStyle(ChatFormatting.WHITE));
        tooltip.add(TooltipHelper.holdShiftForSummary());
        if (TooltipHelper.isSummaryButtonDown()) {
            tooltip.add(Text.empty());
            tooltip.addAll(TooltipHelper.wrapLines(getTypeface(), Text.translate("effortless.controls_settings.mirror_break_mode.tooltip").withStyle(ChatFormatting.GRAY)));
        }
        this.mirrorBreakModeEntry.setTooltip(tooltip);
    }

    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (listeningKey != null) {
            // Escape removes the binding, the same as in the controls screen of Minecraft.
            listeningKey.getKeyBinding().setKeyCode(keyCode == KeyCodes.KEY_ESCAPE.value() ? KeyCodes.KEY_UNKNOWN.value() : keyCode);
            this.listeningKey = null;
            updateKeyEntries();
            return true;
        }
        return super.onKeyPressed(keyCode, scanCode, modifiers);
    }

    private void updateKeyEntries() {
        for (var entry : keyEntries) {
            entry.setItem(entry.getItem());
        }
    }

    private Text getKeyMessage(EffortlessKeys key) {
        var name = key.getKeyBinding().getKey().getNameText();
        if (key == listeningKey) {
            return Text.text("> ").append(name).append(" <").withStyle(ChatFormatting.YELLOW);
        }
        if (isConflicting(key)) {
            return name.withStyle(ChatFormatting.RED);
        }
        return name;
    }

    // True if another Effortless or Minecraft key binding uses the same key.
    private boolean isConflicting(EffortlessKeys key) {
        var keyName = key.getKeyBinding().getKey().getName();
        if (key.getKeyBinding().getKey().getValue() == KeyCodes.KEY_UNKNOWN.value()) {
            return false;
        }
        return Stream.concat(Arrays.stream(EffortlessKeys.values()).filter(other -> other != key), Arrays.stream(OptionKeys.values()))
                .map(KeyBindingOwner::getKeyBinding)
                .anyMatch(other -> other.getKey().getName().equals(keyName));
    }

}
