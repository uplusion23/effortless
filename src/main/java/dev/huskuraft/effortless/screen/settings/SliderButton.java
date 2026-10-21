package dev.huskuraft.effortless.screen.settings;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.math.MathUtils;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.Text;
import dev.huskuraft.universal.api.texture.TextureFactory;

// A button that selects an integer when the player clicks or drags along it.
public class SliderButton extends Button {

    private static final int HANDLE_WIDTH = 8;

    private final IntFunction<Text> formatter;
    private IntConsumer valueChangeListener;
    private int min;
    private int max;
    private int value;

    public SliderButton(Entrance entrance, int x, int y, int width, int height, int min, int max, int value, IntFunction<Text> formatter) {
        super(entrance, x, y, width, height, Text.empty());
        this.formatter = formatter;
        this.min = min;
        this.max = max;
        this.value = clamp(value, min, max);
        setMessage(formatter.apply(this.value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        var clamped = clamp(value, min, max);
        if (clamped == this.value) {
            return;
        }
        this.value = clamped;
        setMessage(formatter.apply(clamped));
        if (valueChangeListener != null) {
            valueChangeListener.accept(clamped);
        }
    }

    public void setRange(int min, int max) {
        this.min = min;
        this.max = max;
        setValue(value);
    }

    public void setValueChangeListener(IntConsumer valueChangeListener) {
        this.valueChangeListener = valueChangeListener;
    }

    private void setValueFromMouse(double mouseX) {
        var position = Math.max(0.0, Math.min((mouseX - getX() - HANDLE_WIDTH / 2.0) / (getWidth() - HANDLE_WIDTH), 1.0));
        setValue((int) Math.round(min + position * (max - min)));
    }

    @Override
    protected void onPress() {
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        setValueFromMouse(mouseX);
    }

    @Override
    public void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
        setValueFromMouse(mouseX);
    }

    @Override
    public void renderWidget(Renderer renderer, int mouseX, int mouseY, float deltaTick) {
        renderer.renderSprite(TextureFactory.getInstance().getButtonTextureSprite(false, false), getX(), getY(), getWidth(), getHeight());
        var position = max == min ? 0 : (double) (value - min) / (max - min);
        var handleX = getX() + (int) Math.round(position * (getWidth() - HANDLE_WIDTH));
        renderer.renderSprite(TextureFactory.getInstance().getButtonTextureSprite(isActive(), isHoveredOrFocused()), handleX, getY(), HANDLE_WIDTH, getHeight());
        renderScrollingString(renderer, getTypeface(), 2, (isActive() ? 0xFFFFFF : 0xA0A0A0) | (int) MathUtils.ceil(getAlpha() * 255.0F) << 24);
    }

}
