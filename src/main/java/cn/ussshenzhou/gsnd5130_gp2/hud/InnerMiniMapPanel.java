package cn.ussshenzhou.gsnd5130_gp2.hud;

import cn.ussshenzhou.gsnd5130_gp2.config.MiniMap;
import cn.ussshenzhou.gsnd5130_gp2.config.RefreshRate;
import cn.ussshenzhou.t88.config.ConfigHelper;
import cn.ussshenzhou.t88.gui.widegt.TPanel;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * @author USS_Shenzhou
 */
public class InnerMiniMapPanel extends TPanel {
    private static final ObjectArrayList<SectionRenderDispatcher.RenderSection> VISIBLE_SECTIONS = new ObjectArrayList<>(10000);
    private static int minimapSize, minimapFrameSize;
    private static TextureTarget target;
    private static ProjectionMatrixBuffer projectionMatrixBuffer;
    private static final Matrix4f MODEL_VIEW_MATRIX = new Matrix4f();
    private static final Matrix4f PROJECTION_MATRIX = new Matrix4f();
    private static final Matrix4f CULLING_PROJECTION_MATRIX = new Matrix4f();
    private static final Quaternionf QUATERNION = new Quaternionf();
    private static final Vector4fc CLEAR_COLOR = new Vector4f(0);
    private static final float PI = (float) Math.PI;
    private static final int DISTANCE_TO_CORNER_X = 20;
    private static final int DISTANCE_TO_CORNER_Y = 20;
    private static boolean shouldDraw = true, drawRequested = false;
    private static final int DIRECTION_LABEL_SIZE = 10;
    private static final int DIRECTION_LABEL_BACKGROUND_COLOR = 0x80000000;
    /**
     * Height of the visible part of a capital letter in the default font, which is shorter than {@link Font#lineHeight}.
     */
    private static final int CAPITAL_LETTER_HEIGHT = 7;
    private static final List<Component> DIRECTION_LABELS = List.of(
            Component.literal("N").withStyle(ChatFormatting.BOLD),
            Component.literal("E"),
            Component.literal("S"),
            Component.literal("W")
    );
    /**
     * Clockwise angle from screen up to north in the latest requested minimap texture.
     * The texture is not redrawn every frame, so the labels follow the texture instead of the camera.
     */
    private static float textureNorthAngle = 0;

    public InnerMiniMapPanel() {
    }

    private static void calculateMinimapSize() {
        var window = Minecraft.getInstance().getWindow();
        minimapSize = (int) (((int) (window.getHeight() * 0.236 / guiScale())) * guiScale());
        minimapFrameSize = Math.max(1, (int) (minimapSize * ConfigHelper.getConfigRead(MiniMap.class).ssaaRatio));
    }

    @Override
    public void layout() {
        calculateMinimapSize();
        if (target == null) {
            target = new TextureTarget("Minimap", minimapFrameSize, minimapFrameSize, true, GpuFormat.RGBA8_UNORM);
            projectionMatrixBuffer = new ProjectionMatrixBuffer("minimap");
        } else {
            target.resize(minimapFrameSize, minimapFrameSize);
        }
        super.layout();
    }

    private int life = 0;

    @Override
    public void tickT() {
        if (ConfigHelper.getConfigRead(MiniMap.class).refreshRate == RefreshRate.TICK) {
            shouldDraw = true;
        } else if (ConfigHelper.getConfigRead(MiniMap.class).refreshRate == RefreshRate.TICK_20) {
            life++;
            if (life == 20) {
                life = 0;
                shouldDraw = true;
            }
        }
        super.tickT();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int pMouseX, int pMouseY, float pPartialTick) {
        super.extractRenderState(graphics, pMouseX, pMouseY, pPartialTick);
        renderMap(graphics);
    }

    private void renderMap(GuiGraphicsExtractor graphics) {
        if (target == null) {
            return;
        }
        switch (ConfigHelper.getConfigRead(MiniMap.class).refreshRate) {
            case EVERY_FRAME -> shouldDraw = true;
            case ANOTHER_FRAME -> shouldDraw = !shouldDraw;
            default -> {
            }
        }
        if (shouldDraw) {
            drawRequested = true;
            textureNorthAngle = northAngle();
        }
        if (ConfigHelper.getConfigRead(MiniMap.class).refreshRate == RefreshRate.TICK || ConfigHelper.getConfigRead(MiniMap.class).refreshRate == RefreshRate.TICK_20) {
            shouldDraw = false;
        }

        blit(graphics);
        graphics.outline(x1() - 1, y1() - 1, getMinimapSize() + 2, getMinimapSize() + 2, 0xffffffff);
        renderDirectionLabels(graphics);
    }

    /**
     * The camera is updated before the GUI is extracted, so this matches the rotation that {@link #prepare} uses later in the same frame.
     */
    private static float northAngle() {
        if (!ConfigHelper.getConfigRead(MiniMap.class).followPlayerRotation) {
            return 0;
        }
        return PI - Minecraft.getInstance().gameRenderer.mainCamera().yRot() * PI / 180;
    }

    /**
     * Each label is placed where the line from the center toward its direction meets the border,
     * so it slides along the inside of the border as the map rotates.
     */
    private void renderDirectionLabels(GuiGraphicsExtractor graphics) {
        Font font = Minecraft.getInstance().font;
        float halfLabelSize = DIRECTION_LABEL_SIZE / 2f;
        float centerX = (x1() + x2()) / 2f;
        float centerY = (y1() + y2()) / 2f;
        float reach = getMinimapSize() / 2f - halfLabelSize;
        for (int i = 0; i < DIRECTION_LABELS.size(); i++) {
            float angle = textureNorthAngle + i * PI / 2;
            float directionX = (float) Math.sin(angle);
            float directionY = (float) -Math.cos(angle);
            float distance = reach / Math.max(Math.abs(directionX), Math.abs(directionY));
            float labelX = Mth.clamp(snapToPixel(centerX + directionX * distance), x1() + halfLabelSize, x2() - halfLabelSize) - halfLabelSize;
            float labelY = Mth.clamp(snapToPixel(centerY + directionY * distance), y1() + halfLabelSize, y2() - halfLabelSize) - halfLabelSize;
            Component label = DIRECTION_LABELS.get(i);
            // The font width includes 1 trailing spacing after the last glyph.
            float textX = snapToPixel((DIRECTION_LABEL_SIZE - (font.width(label) - 1)) / 2f);
            float textY = snapToPixel((DIRECTION_LABEL_SIZE - CAPITAL_LETTER_HEIGHT) / 2f);
            graphics.pose().pushMatrix();
            graphics.pose().translate(labelX, labelY);
            graphics.fill(0, 0, DIRECTION_LABEL_SIZE, DIRECTION_LABEL_SIZE, DIRECTION_LABEL_BACKGROUND_COLOR);
            graphics.pose().translate(textX, textY);
            graphics.text(font, label, 0, 0, 0xffffffff, false);
            graphics.pose().popMatrix();
        }
    }

    private static float snapToPixel(float guiCoordinate) {
        return Math.round(guiCoordinate * guiScale()) / guiScale();
    }

    /**
     * Cutout leaves and water leave alpha below 1 in the target.
     * Blitting without blending keeps whatever is behind the minimap from showing through them.
     */
    private void blit(GuiGraphicsExtractor graphics) {
        graphics.submitGuiElementRenderState(new BlitRenderState(
                RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND,
                TextureSetup.singleTexture(target.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
                new Matrix3x2f(graphics.pose()),
                x1(), y1(), x2(), y2(),
                0, 1, 1, 0,
                -1,
                graphics.peekScissorStack()
        ));
    }

    /**
     * The GUI is extracted before the level is rendered in the same frame.
     * Drawing here, after the level, uses the camera, lightmap and chunk buffers of the current frame.
     */
    public static void drawIfRequested() {
        if (!drawRequested) {
            return;
        }
        drawRequested = false;
        drawTarget();
    }

    private static void drawTarget() {
        var minecraft = Minecraft.getInstance();
        LevelRenderer levelRenderer = minecraft.levelRenderer;
        SectionRenderDispatcher sectionRenderDispatcher = levelRenderer.sectionRenderDispatcher();
        if (sectionRenderDispatcher == null || levelRenderer.viewArea() == null) {
            return;
        }
        prepare(minecraft);
        Vec3 pos = minecraft.gameRenderer.mainCamera().position();
        Frustum frustum = new Frustum(MODEL_VIEW_MATRIX, CULLING_PROJECTION_MATRIX);
        frustum.prepare(pos.x, pos.y, pos.z);
        levelRenderer.sectionOcclusionGraph().getOctree().visitNodes((node, fullyVisible, depth, isClose) -> {
            SectionRenderDispatcher.RenderSection section = node.getSection();
            if (section != null) {
                VISIBLE_SECTIONS.add(section);
            }
        }, frustum, 32);

        GpuTextureView blockAtlas = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>> drawGroups = new EnumMap<>(ChunkSectionLayer.class);
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            drawGroups.put(layer, new Int2ObjectOpenHashMap<>());
        }
        List<DynamicUniforms.ChunkSectionInfo> sectionInfos = new ArrayList<>();
        int largestIndexCount = 0;
        long now = Util.getMillis();
        sectionRenderDispatcher.lock();
        try {
            for (SectionRenderDispatcher.RenderSection section : VISIBLE_SECTIONS) {
                SectionMesh sectionMesh = section.getSectionMesh();
                BlockPos renderOrigin = section.getRenderOrigin();
                int uboIndex = -1;
                for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
                    SectionMesh.SectionDraw draw = sectionMesh.getSectionDraw(layer);
                    SectionRenderDispatcher.RenderSectionBufferSlice slice = sectionRenderDispatcher.getRenderSectionSlice(sectionMesh, layer);
                    if (slice == null || draw == null || (draw.hasCustomIndexBuffer() && slice.indexBuffer() == null)) {
                        continue;
                    }
                    if (uboIndex == -1) {
                        uboIndex = sectionInfos.size();
                        sectionInfos.add(new DynamicUniforms.ChunkSectionInfo(
                                MODEL_VIEW_MATRIX,
                                renderOrigin.getX(),
                                renderOrigin.getY(),
                                renderOrigin.getZ(),
                                section.getVisibility(now),
                                blockAtlas.getWidth(0),
                                blockAtlas.getHeight(0)
                        ));
                    }
                    int combinedHash = 173;
                    GpuBuffer vertexBuffer = slice.vertexBuffer();
                    if (layer != ChunkSectionLayer.TRANSLUCENT) {
                        combinedHash = 31 * combinedHash + vertexBuffer.hashCode();
                    }
                    int firstIndex = 0;
                    GpuBuffer indexBuffer = null;
                    IndexType indexType = null;
                    if (draw.hasCustomIndexBuffer()) {
                        indexBuffer = slice.indexBuffer();
                        indexType = draw.indexType();
                        if (layer != ChunkSectionLayer.TRANSLUCENT) {
                            combinedHash = 31 * combinedHash + indexBuffer.hashCode();
                            combinedHash = 31 * combinedHash + indexType.hashCode();
                        }
                        firstIndex = (int) (slice.indexBufferOffset() / indexType.bytes);
                    } else {
                        largestIndexCount = Math.max(largestIndexCount, draw.indexCount());
                    }
                    int finalUboIndex = uboIndex;
                    int baseVertex = (int) (slice.vertexBufferOffset() / layer.vertexFormat().getVertexSize());
                    drawGroups.get(layer)
                            .computeIfAbsent(combinedHash, hash -> new ArrayList<>())
                            .add(new RenderPass.Draw<>(
                                    0,
                                    vertexBuffer,
                                    indexBuffer,
                                    indexType,
                                    firstIndex,
                                    draw.indexCount(),
                                    baseVertex,
                                    (sectionUbos, uploader) -> uploader.upload("ChunkSection", sectionUbos[finalUboIndex])
                            ));
                }
            }
        } finally {
            sectionRenderDispatcher.unlock();
            VISIBLE_SECTIONS.clear();
        }
        GpuBufferSlice[] chunkSectionInfos = RenderSystem.getDynamicUniforms().writeChunkSections(sectionInfos.toArray(new DynamicUniforms.ChunkSectionInfo[0]));
        renderLayers(minecraft, blockAtlas, drawGroups, largestIndexCount, chunkSectionInfos);
    }

    private static void renderLayers(Minecraft minecraft, GpuTextureView blockAtlas, EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>> drawGroups, int largestIndexCount, GpuBufferSlice[] chunkSectionInfos) {
        RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer defaultIndexBuffer = largestIndexCount == 0 ? null : autoIndices.getBuffer(largestIndexCount);
        IndexType defaultIndexType = largestIndexCount == 0 ? null : autoIndices.type();
        GpuBufferSlice projectionMatrix = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType projectionType = RenderSystem.getProjectionType();
        GpuBufferSlice fog = RenderSystem.getShaderFog();
        RenderSystem.setProjectionMatrix(projectionMatrixBuffer.getBuffer(PROJECTION_MATRIX), ProjectionType.ORTHOGRAPHIC);
        RenderSystem.setShaderFog(minecraft.gameRenderer.fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
        var commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        commandEncoder.clearColorAndDepthTextures(target.getColorTexture(), CLEAR_COLOR, target.getDepthTexture(), 0);
        try (RenderPass renderPass = commandEncoder.createRenderPass(() -> "Minimap", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.bindTexture("Sampler0", blockAtlas, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, true));
            renderPass.bindTexture("Sampler2", minecraft.gameRenderer.levelLightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
                renderPass.setPipeline(layer.pipeline());
                for (List<RenderPass.Draw<GpuBufferSlice[]>> draws : drawGroups.get(layer).values()) {
                    if (layer == ChunkSectionLayer.TRANSLUCENT) {
                        draws = draws.reversed();
                    }
                    renderPass.drawMultipleIndexed(draws, defaultIndexBuffer, defaultIndexType, List.of("ChunkSection"), chunkSectionInfos);
                }
            }
        } finally {
            RenderSystem.setProjectionMatrix(projectionMatrix, projectionType);
            RenderSystem.setShaderFog(fog);
        }
    }

    private static float guiScale() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }

    protected float screenX1() {
        return x1() * guiScale();
    }

    protected float screenY1() {
        return y1() * guiScale();
    }

    public static int getMinimapScreenSize() {
        return minimapSize;
    }

    public static int getMinimapSize() {
        return (int) (minimapSize / guiScale());
    }

    protected int x1() {
        var window = Minecraft.getInstance().getWindow();
        return switch (ConfigHelper.getConfigRead(MiniMap.class).corner) {
            case TOP_LEFT, BOTTOM_LEFT -> DISTANCE_TO_CORNER_X;
            case TOP_RIGHT, BOTTOM_RIGHT -> window.getGuiScaledWidth() - DISTANCE_TO_CORNER_X - getMinimapSize();
        };
    }

    protected int x2() {
        return x1() + getMinimapSize();
    }

    protected int y1() {
        var window = Minecraft.getInstance().getWindow();
        return switch (ConfigHelper.getConfigRead(MiniMap.class).corner) {
            case TOP_LEFT, TOP_RIGHT -> DISTANCE_TO_CORNER_Y;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> window.getGuiScaledHeight() - DISTANCE_TO_CORNER_Y - getMinimapSize();
        };
    }

    protected int y2() {
        return y1() + getMinimapSize();
    }

    private static void prepare(Minecraft minecraft) {
        var cfgR = ConfigHelper.getConfigRead(MiniMap.class);
        var camera = minecraft.gameRenderer.mainCamera();
        MODEL_VIEW_MATRIX.identity();
        QUATERNION.identity().rotationYXZ(0, PI / 2, 0);
        if (cfgR.followPlayerRotation) {
            QUATERNION.rotateY(PI + camera.yRot() * PI / 180);
        }
        MODEL_VIEW_MATRIX.rotation(QUATERNION);
        int mapCoverBlocksHalf = cfgR.getCoverRange() / 2;
        CULLING_PROJECTION_MATRIX.setOrtho(-mapCoverBlocksHalf, mapCoverBlocksHalf, -mapCoverBlocksHalf, mapCoverBlocksHalf, -1000, 1000);
        // Reversed-Z, same as net.minecraft.client.renderer.Projection.
        PROJECTION_MATRIX.setOrtho(-mapCoverBlocksHalf, mapCoverBlocksHalf, -mapCoverBlocksHalf, mapCoverBlocksHalf, 1000, -1000, RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
        VISIBLE_SECTIONS.clear();
    }
}
