package cn.ussshenzhou.gsnd5130_gp2.gui;

import cn.ussshenzhou.gsnd5130_gp2.config.MiniMap;
import cn.ussshenzhou.gsnd5130_gp2.config.ScreenCorner;
import cn.ussshenzhou.gsnd5130_gp2.record.PositionRecorder;
import cn.ussshenzhou.t88.config.ConfigHelper;
import cn.ussshenzhou.t88.gui.advanced.TConstrainedEditBox;
import cn.ussshenzhou.t88.gui.advanced.TOptionsPanel;
import cn.ussshenzhou.t88.gui.screen.TScreen;
import cn.ussshenzhou.t88.gui.widegt.TButton;
import cn.ussshenzhou.t88.gui.widegt.TEditBox;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * @author USS_Shenzhou
 */
public class RecordingSetupScreen extends TScreen {
    private static final int PANEL_WIDTH = 240;
    /**
     * Three rows of 20 with a gap of 4 above each, plus the 5 bottom margin of the options container,
     * so that no scrollbar appears.
     */
    private static final int PANEL_HEIGHT = 3 * (20 + 4) + 5;
    private static final int START_BUTTON_WIDTH = 80;
    private static final int GAP_BETWEEN_PANEL_AND_BUTTON = 6;
    private static final int GAP_BETWEEN_BUTTONS = 6;
    private static final int BUTTON_TEXT_PADDING = 8;
    private static final Component START_TEXT = Component.literal("Start");
    private static final Component START_WITHOUT_RECORDING_TEXT = Component.literal("Start Without Recording");
    private final TOptionsPanel optionsPanel = new TOptionsPanel();
    private final TEditBox playerIdBox = new TEditBox();
    private final TEditBox roundBox = new TEditBox();
    private final TButton startButton = new TButton(START_TEXT, button -> start());
    /**
     * Does the same as closing this screen with ESC.
     */
    private final TButton startWithoutRecordingButton = new TButton(START_WITHOUT_RECORDING_TEXT, button -> onClose(true));

    public RecordingSetupScreen() {
        super(Component.literal("Recording Setup"));
        this.add(optionsPanel);
        this.add(startButton);
        this.add(startWithoutRecordingButton);
        playerIdBox.setMaxLength(64);
        roundBox.setMaxLength(64);
        playerIdBox.addResponder(value -> onInputChanged(playerIdBox, value));
        roundBox.addResponder(value -> onInputChanged(roundBox, value));
        optionsPanel.addOption(Component.literal("Player ID"), playerIdBox);
        optionsPanel.addOption(Component.literal("Round"), roundBox);
        optionsPanel.addOptionCycleButtonInit(Component.literal("Minimap Position"), List.of(ScreenCorner.values()),
                corner -> button -> {
                    ConfigHelper.getConfigWrite(MiniMap.class, miniMap -> miniMap.corner = corner);
                    SettingsScreen.resizeMiniMap();
                },
                entry -> entry.getContent() == ConfigHelper.getConfigRead(MiniMap.class).corner);
        startButton.active = false;
    }

    private void onInputChanged(TEditBox editBox, String value) {
        editBox.setTextColor(value.isBlank() || PositionRecorder.isValidFileNamePart(value.trim())
                ? TConstrainedEditBox.WHITE_TEXT_COLOR
                : TConstrainedEditBox.RED_TEXT_COLOR);
        startButton.active = PositionRecorder.isValidFileNamePart(playerIdBox.getValue().trim())
                && PositionRecorder.isValidFileNamePart(roundBox.getValue().trim());
        startButton.setMessage(START_TEXT);
    }

    private void start() {
        if (PositionRecorder.start(playerIdBox.getValue().trim(), roundBox.getValue().trim())) {
            onClose(true);
        } else {
            startButton.setMessage(Component.literal("Failed, see log"));
        }
    }

    /**
     * No blur and no dark overlay, so that the minimap stays visible while its position is being chosen.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.minecraft.gui.hud.extractDeferredSubtitles();
    }

    @Override
    public void layout() {
        int top = (height - PANEL_HEIGHT - GAP_BETWEEN_PANEL_AND_BUTTON - TButton.RECOMMEND_SIZE.y) / 2;
        optionsPanel.setBounds((width - PANEL_WIDTH) / 2, top, PANEL_WIDTH, PANEL_HEIGHT);
        int buttonY = top + PANEL_HEIGHT + GAP_BETWEEN_PANEL_AND_BUTTON;
        int startWithoutRecordingButtonWidth = font.width(START_WITHOUT_RECORDING_TEXT) + 2 * BUTTON_TEXT_PADDING;
        int startButtonX = (width - START_BUTTON_WIDTH - GAP_BETWEEN_BUTTONS - startWithoutRecordingButtonWidth) / 2;
        startButton.setBounds(startButtonX, buttonY, START_BUTTON_WIDTH, TButton.RECOMMEND_SIZE.y);
        startWithoutRecordingButton.setBounds(startButtonX + START_BUTTON_WIDTH + GAP_BETWEEN_BUTTONS, buttonY, startWithoutRecordingButtonWidth, TButton.RECOMMEND_SIZE.y);
        super.layout();
    }
}
