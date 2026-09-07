package mchorse.bbs_mod.ui.dashboard;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIWelcomePanel.Step;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class UIWelcomePanel extends UIElement
{
    public enum Step
    {
        WELCOME, LAYOUT_PICKER, CONFIRMATION
    }

    public enum LayoutPreset
    {
        CUSTOM(UIKeys.WELCOME_LAYOUT_CUSTOM_NAME, UIKeys.WELCOME_LAYOUT_CUSTOM_DESC, null),
        PREMIERE(UIKeys.WELCOME_LAYOUT_PREMIERE_NAME, UIKeys.WELCOME_LAYOUT_PREMIERE_DESC, "premiere_style"),
        HORIZONTAL(UIKeys.WELCOME_LAYOUT_HORIZONTAL_NAME, UIKeys.WELCOME_LAYOUT_HORIZONTAL_DESC, "horizontal_simplified"),
        VERTICAL(UIKeys.WELCOME_LAYOUT_VERTICAL_NAME, UIKeys.WELCOME_LAYOUT_VERTICAL_DESC, "vertical_simplified");

        public final IKey title;
        public final IKey description;
        public final String resource;

        private LayoutPreset(IKey title, IKey description, String resource)
        {
            this.title = title;
            this.description = description;
            this.resource = resource;
        }
    }

    private Step step = Step.WELCOME;
    private LayoutPreset selectedPreset = LayoutPreset.CUSTOM;
    private long startTime = System.currentTimeMillis();
    private long stepStartTime = System.currentTimeMillis();

    public UIButton buttonStart;
    public UIButton buttonBack;
    public UIButton buttonConfirmStep;
    public UIButton buttonChangeLayout;
    public UIButton buttonFinish;

    public UIWelcomePanel()
    {
        super();

        this.mousePropagation = EventPropagation.BLOCK;
        this.keyboardPropagation = EventPropagation.BLOCK;

        this.buttonStart = new UIButton(UIKeys.WELCOME_START, (b) ->
        {
            this.setStep(Step.LAYOUT_PICKER);
        });
        this.buttonBack = new UIButton(UIKeys.WELCOME_BACK, (b) ->
        {
            this.setStep(Step.WELCOME);
        });
        this.buttonConfirmStep = new UIButton(UIKeys.WELCOME_CONFIRM_STEP, (b) ->
        {
            this.setStep(Step.CONFIRMATION);
        });
        this.buttonChangeLayout = new UIButton(UIKeys.WELCOME_CHANGE_LAYOUT, (b) ->
        {
            this.setStep(Step.LAYOUT_PICKER);
        });
        this.buttonFinish = new UIButton(UIKeys.WELCOME_START_CREATING, (b) ->
        {
            this.finishSetup();
        });

        this.buttonStart.culled = false;
        this.buttonBack.culled = false;
        this.buttonConfirmStep.culled = false;
        this.buttonChangeLayout.culled = false;
        this.buttonFinish.culled = false;

        this.add(this.buttonStart, this.buttonBack, this.buttonConfirmStep, this.buttonChangeLayout, this.buttonFinish);
        this.setStep(Step.WELCOME);
    }

    public void setStep(Step step)
    {
        this.step = step;
        this.stepStartTime = System.currentTimeMillis();

        this.buttonStart.setVisible(step == Step.WELCOME);
        this.buttonBack.setVisible(step == Step.LAYOUT_PICKER);
        this.buttonConfirmStep.setVisible(step == Step.LAYOUT_PICKER);
        this.buttonChangeLayout.setVisible(step == Step.CONFIRMATION);
        this.buttonFinish.setVisible(step == Step.CONFIRMATION);

        this.resize();
    }

    private MapType loadPresetResource(String resource)
    {
        try (InputStream stream = UIFilmPanel.class.getResourceAsStream("/assets/bbs/layout_presets/" + resource + ".json"))
        {
            if (stream == null)
            {
                return null;
            }

            BaseType data = DataToString.fromString(new String(stream.readAllBytes(), StandardCharsets.UTF_8));

            return data != null && data.isMap() ? data.asMap() : null;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    private void finishSetup()
    {
        UIFilmPanel filmPanel = null;

        try
        {
            UIDashboard dashboard = BBSModClient.getDashboard();

            if (dashboard != null)
            {
                filmPanel = dashboard.getPanel(UIFilmPanel.class);
            }
        }
        catch (Exception e)
        {}

        if (this.selectedPreset == LayoutPreset.CUSTOM || this.selectedPreset.resource == null)
        {
            /* Keep current or previous layout completely untouched */
        }
        else
        {
            MapType data = this.loadPresetResource(this.selectedPreset.resource);

            if (data != null)
            {
                if (filmPanel != null)
                {
                    filmPanel.applyFilmLayoutFromPreset(data);
                }
                else
                {
                    BaseType layoutData = data.get("film_layout");

                    if (layoutData != null)
                    {
                        EditorLayoutNode root = EditorLayoutNode.fromData(layoutData);

                        if (root != null)
                        {
                            BBSSettings.editorLayoutSettings.setFilmLayoutRoot(root);
                        }
                    }

                    if (data.has("video_frame_width") && data.has("video_frame_height"))
                    {
                        BBSSettings.videoSettings.width.set(data.getInt("video_frame_width"));
                        BBSSettings.videoSettings.height.set(data.getInt("video_frame_height"));
                    }

                    if (data.has("timeline_toolbar_docks"))
                    {
                        BBSSettings.timelineToolbarDocks.applyPreset(data);
                    }
                }
            }
        }

        BBSSettings.welcomePanelSeen21.set(true);
        this.removeFromParent();
    }

    private EditorLayoutNode getCurrentActiveLayoutRoot()
    {
        EditorLayoutNode root = BBSSettings.editorLayoutSettings.getFilmLayoutRoot();

        if (root == null)
        {
            root = EditorLayoutNode.defaultFilmLayout();
        }

        return root;
    }

    private boolean hasPanelInNode(EditorLayoutNode node, String panelId)
    {
        if (node == null || panelId == null)
        {
            return false;
        }

        if (node instanceof EditorLayoutNode.PanelNode pn)
        {
            return panelId.equals(pn.getPanelId());
        }

        if (node instanceof EditorLayoutNode.SplitterNode sn)
        {
            return this.hasPanelInNode(sn.getFirst(), panelId) || this.hasPanelInNode(sn.getSecond(), panelId);
        }

        if (node instanceof EditorLayoutNode.TabbedNode tn)
        {
            for (EditorLayoutNode tab : tn.tabs)
            {
                if (this.hasPanelInNode(tab, panelId))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private IKey getPanelTitleKey(String panelId)
    {
        if ("preview".equals(panelId))
        {
            return UIKeys.WELCOME_PANEL_VISOR;
        }
        if ("cameraTimeline".equals(panelId))
        {
            return UIKeys.WELCOME_PANEL_CAMERA_TIMELINE;
        }
        if ("actionTimeline".equals(panelId))
        {
            return UIKeys.WELCOME_PANEL_ACTION_TIMELINE;
        }
        if ("replayTimeline".equals(panelId))
        {
            return UIKeys.WELCOME_PANEL_REPLAY_TIMELINE;
        }
        if ("editArea".equals(panelId) || "actionEditArea".equals(panelId) || "unifiedEditArea".equals(panelId))
        {
            return UIKeys.WELCOME_PANEL_PROPERTIES;
        }
        if ("cameraEditArea".equals(panelId))
        {
            return UIKeys.WELCOME_PANEL_CAMERA_PROPERTIES;
        }
        if ("replaysPanel".equals(panelId))
        {
            return UIKeys.WELCOME_PANEL_REPLAYS;
        }
        if ("anchoredReplaysPropertiesPanel".equals(panelId))
        {
            return UIKeys.WELCOME_PANEL_GENERAL;
        }

        return UIKeys.WELCOME_PANEL_GENERAL_PROPERTIES;
    }

    private PanelType getPanelType(String panelId)
    {
        if ("preview".equals(panelId))
        {
            return PanelType.VIEWPORT;
        }
        if ("cameraTimeline".equals(panelId) || "actionTimeline".equals(panelId))
        {
            return PanelType.TIMELINE;
        }
        if ("replayTimeline".equals(panelId))
        {
            return PanelType.REPLAY_KEYFRAMES;
        }
        if ("replaysPanel".equals(panelId))
        {
            return PanelType.REPLAYS;
        }

        return PanelType.PROPERTIES;
    }

    private void renderLayoutNode(Batcher2D batcher, FontRenderer font, EditorLayoutNode node, float x, float y, float w, float h, boolean isLarge)
    {
        if (node == null || w <= 1.0F || h <= 1.0F)
        {
            return;
        }

        if (node instanceof EditorLayoutNode.SplitterNode splitter)
        {
            if (splitter.isHorizontal())
            {
                float w1 = w * splitter.getRatio();

                this.renderLayoutNode(batcher, font, splitter.getFirst(), x, y, w1, h, isLarge);
                this.renderLayoutNode(batcher, font, splitter.getSecond(), x + w1, y, w - w1, h, isLarge);
            }
            else
            {
                float h1 = h * splitter.getRatio();

                this.renderLayoutNode(batcher, font, splitter.getFirst(), x, y, w, h1, isLarge);
                this.renderLayoutNode(batcher, font, splitter.getSecond(), x, y + h1, w, h - h1, isLarge);
            }
        }
        else if (node instanceof EditorLayoutNode.TabbedNode tabbed)
        {
            IKey[] tabTitles = new IKey[tabbed.tabs.size()];
            PanelType activeType = PanelType.PROPERTIES;

            for (int i = 0; i < tabbed.tabs.size(); i++)
            {
                EditorLayoutNode tab = tabbed.tabs.get(i);
                String tabId = tab instanceof EditorLayoutNode.PanelNode pn ? pn.getPanelId() : "";

                tabTitles[i] = this.getPanelTitleKey(tabId);

                if (i == tabbed.activeTab)
                {
                    activeType = this.getPanelType(tabId);
                }
            }

            this.drawTabbedPanel(batcher, font, tabTitles, tabbed.activeTab, x, y, w, h, isLarge, activeType);
        }
        else if (node instanceof EditorLayoutNode.PanelNode panel)
        {
            String pid = panel.getPanelId();
            IKey title = this.getPanelTitleKey(pid);
            PanelType type = this.getPanelType(pid);

            this.drawPanel(batcher, font, title, x, y, w, h, isLarge, type);
        }
    }

    private void renderCustomLayout(Batcher2D batcher, FontRenderer font, float x, float y, float w, float h, boolean isLarge)
    {
        EditorLayoutNode root = this.getCurrentActiveLayoutRoot();
        boolean hasReplays = this.hasPanelInNode(root, "replaysPanel");

        if (!hasReplays)
        {
            float sideW = Math.max(isLarge ? 48.0F : 24.0F, w * 0.15F);
            float mainW = w - sideW;

            this.renderLayoutNode(batcher, font, root, x, y, mainW, h, isLarge);

            float sideTopH = h * 0.55F;
            float sideBotH = h - sideTopH;
            this.drawPanel(batcher, font, UIKeys.WELCOME_PANEL_REPLAYS, x + mainW, y, sideW, sideTopH, isLarge, PanelType.REPLAYS);
            this.drawPanel(batcher, font, UIKeys.WELCOME_PANEL_GENERAL, x + mainW, y + sideTopH, sideW, sideBotH, isLarge, PanelType.PROPERTIES);
        }
        else
        {
            this.renderLayoutNode(batcher, font, root, x, y, w, h, isLarge);
        }
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            if (this.step == Step.CONFIRMATION)
            {
                this.setStep(Step.LAYOUT_PICKER);
                return true;
            }
            else if (this.step == Step.LAYOUT_PICKER)
            {
                this.setStep(Step.WELCOME);
                return true;
            }
            return true;
        }
        if (context.isPressed(GLFW.GLFW_KEY_ENTER) || context.isPressed(GLFW.GLFW_KEY_KP_ENTER))
        {
            if (this.step == Step.WELCOME)
            {
                this.setStep(Step.LAYOUT_PICKER);
                return true;
            }
            else if (this.step == Step.LAYOUT_PICKER)
            {
                this.setStep(Step.CONFIRMATION);
                return true;
            }
            else if (this.step == Step.CONFIRMATION)
            {
                this.finishSetup();
                return true;
            }
        }
        return super.subKeyPressed(context);
    }

    private static final float REF_WIDTH = 810F;
    private static final float REF_HEIGHT = 440F;

    private float getUiScale()
    {
        if (this.area.w <= 0 || this.area.h <= 0)
        {
            return 1F;
        }

        float scaleX = (float) this.area.w / REF_WIDTH;
        float scaleY = (float) this.area.h / REF_HEIGHT;

        return Math.min(1F, Math.min(scaleX, scaleY));
    }

    private float getVirtualX()
    {
        float scale = this.getUiScale();

        return scale > 0F ? (this.area.w / scale - REF_WIDTH) / 2F : 0F;
    }

    private float getVirtualY()
    {
        float scale = this.getUiScale();

        return scale > 0F ? (this.area.h / scale - REF_HEIGHT) / 2F : 0F;
    }

    private float getVirtualWidth()
    {
        return REF_WIDTH;
    }

    private float getVirtualHeight()
    {
        return REF_HEIGHT;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        float scale = this.getUiScale();
        int mouseX = context.mouseX;
        int mouseY = context.mouseY;

        if (scale < 1F && scale > 0F)
        {
            mouseX = (int) (context.mouseX / scale);
            mouseY = (int) (context.mouseY / scale);
        }

        if (this.step == Step.LAYOUT_PICKER)
        {
            float vX = this.getVirtualX();
            float vY = this.getVirtualY();
            float vMx = vX + REF_WIDTH / 2F;

            int cardW = 175;
            int cardH = 225;
            int gap = 12;
            int totalW = cardW * 4 + gap * 3;
            int startX = (int) (vMx - totalW / 2F);
            int startY = (int) (vY + 70);

            LayoutPreset[] presets = LayoutPreset.values();

            for (int i = 0; i < presets.length; i++)
            {
                int cardX = startX + i * (cardW + gap);
                int cardY = startY;

                if (mouseX >= cardX && mouseX <= cardX + cardW
                    && mouseY >= cardY && mouseY <= cardY + cardH)
                {
                    this.selectedPreset = presets[i];
                    return true;
                }
            }
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected IUIElement childrenMouseClicked(UIContext context)
    {
        float scale = this.getUiScale();

        if (scale < 1F && scale > 0F)
        {
            int origX = context.mouseX;
            int origY = context.mouseY;
            context.mouseX = (int) (origX / scale);
            context.mouseY = (int) (origY / scale);

            IUIElement res = super.childrenMouseClicked(context);

            context.mouseX = origX;
            context.mouseY = origY;
            return res;
        }

        return super.childrenMouseClicked(context);
    }

    @Override
    protected IUIElement childrenMouseReleased(UIContext context)
    {
        float scale = this.getUiScale();

        if (scale < 1F && scale > 0F)
        {
            int origX = context.mouseX;
            int origY = context.mouseY;
            context.mouseX = (int) (origX / scale);
            context.mouseY = (int) (origY / scale);

            IUIElement res = super.childrenMouseReleased(context);

            context.mouseX = origX;
            context.mouseY = origY;
            return res;
        }

        return super.childrenMouseReleased(context);
    }

    @Override
    protected IUIElement childrenMouseScrolled(UIContext context)
    {
        float scale = this.getUiScale();

        if (scale < 1F && scale > 0F)
        {
            int origX = context.mouseX;
            int origY = context.mouseY;
            context.mouseX = (int) (origX / scale);
            context.mouseY = (int) (origY / scale);

            IUIElement res = super.childrenMouseScrolled(context);

            context.mouseX = origX;
            context.mouseY = origY;
            return res;
        }

        return super.childrenMouseScrolled(context);
    }

    @Override
    public void resize()
    {
        int btnW = 180;
        int btnH = 22;

        float vX = this.getVirtualX();
        float vY = this.getVirtualY();
        float vMx = vX + REF_WIDTH / 2F;

        if (this.step == Step.WELCOME)
        {
            this.buttonStart.resetFlex().set((int) (vMx - btnW / 2F), (int) (vY + 310), btnW, btnH);
        }
        else if (this.step == Step.LAYOUT_PICKER)
        {
            int gap = 16;
            int totalW = 120 + gap + btnW;

            this.buttonBack.resetFlex().set((int) (vMx - totalW / 2F), (int) (vY + 380), 120, btnH);
            this.buttonConfirmStep.resetFlex().set((int) (vMx - totalW / 2F + 120 + gap), (int) (vY + 380), btnW, btnH);
        }
        else if (this.step == Step.CONFIRMATION)
        {
            int finishW = 210;
            int gap = 16;
            int totalW = 130 + gap + finishW;

            this.buttonChangeLayout.resetFlex().set((int) (vMx - totalW / 2F), (int) (vY + 380), 130, btnH);
            this.buttonFinish.resetFlex().set((int) (vMx - totalW / 2F + 130 + gap), (int) (vY + 380), finishW, btnH);
        }

        super.resize();
    }

    private void outline(Batcher2D batcher, float x1, float y1, float x2, float y2, int color)
    {
        batcher.box(x1, y1, x2, y1 + 1, color);
        batcher.box(x1, y2 - 1, x2, y2, color);
        batcher.box(x1, y1, x1 + 1, y2, color);
        batcher.box(x2 - 1, y1, x2, y2, color);
    }

    private void outlineThick(Batcher2D batcher, float x1, float y1, float x2, float y2, int color, int thickness)
    {
        batcher.box(x1, y1, x2, y1 + thickness, color);
        batcher.box(x1, y2 - thickness, x2, y2, color);
        batcher.box(x1, y1, x1 + thickness, y2, color);
        batcher.box(x2 - thickness, y1, x2, y2, color);
    }

    private void drawPlayerHead(DrawContext drawContext, Identifier skinTexture, int x, int y, int size)
    {
        drawContext.drawTexture(RenderLayer::getGuiTextured, skinTexture, x, y, 8F, 8F, size, size, 8, 8, 64, 64);
        drawContext.drawTexture(RenderLayer::getGuiTextured, skinTexture, x, y, 40F, 8F, size, size, 8, 8, 64, 64);
    }

    @Override
    public void render(UIContext context)
    {
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xEB0F0F11);

        long now = System.currentTimeMillis();
        float elapsed = (now - this.startTime) / 1000.0F;
        float stepElapsed = (now - this.stepStartTime) / 1000.0F;
        float stepAlpha = Math.min(stepElapsed / 0.35F, 1.0F);

        float scale = this.getUiScale();
        int origMouseX = context.mouseX;
        int origMouseY = context.mouseY;

        if (scale < 1.0F && scale > 0.0F)
        {
            context.mouseX = (int) (origMouseX / scale);
            context.mouseY = (int) (origMouseY / scale);
        }

        MatrixStack matrices = context.batcher.getContext().getMatrices();
        matrices.push();

        if (scale < 1.0F && scale > 0.0F)
        {
            matrices.scale(scale, scale, 1.0F);
        }

        if (this.step == Step.WELCOME)
        {
            this.renderWelcomeStep(context, elapsed, stepAlpha);
        }
        else if (this.step == Step.LAYOUT_PICKER)
        {
            this.renderLayoutPickerStep(context, stepAlpha);
        }
        else if (this.step == Step.CONFIRMATION)
        {
            this.renderConfirmationStep(context, stepAlpha);
        }

        super.render(context);

        matrices.pop();

        context.mouseX = origMouseX;
        context.mouseY = origMouseY;
    }

    private void renderWelcomeStep(UIContext context, float elapsed, float alpha)
    {
        float vX = this.getVirtualX();
        float vY = this.getVirtualY();
        float vMx = vX + REF_WIDTH / 2F;
        float vMy = vY + REF_HEIGHT / 2F;

        float floatOffset = (float) Math.sin(elapsed * 2.5F) * 5.0F;
        Texture logo = BBSModClient.getTextures().getTexture(Link.assets("textures/bbs_cml.png"));

        if (logo != null)
        {
            float logoW = logo.width * 4.5F;
            float logoH = logo.height * 4.5F;
            float logoX = vMx - logoW / 2.0F;
            float logoY = vMy - 130 + floatOffset;

            context.batcher.texturedBox(logo, Colors.setA(Colors.WHITE, alpha), logoX, logoY, logoW, logoH, 0, 0,
                logo.width, logo.height);
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        String username = mc.player != null ? mc.player.getGameProfile().getName() : mc.getSession().getUsername();
        Identifier skinTexture = null;

        if (mc.player != null)
        {
            try
            {
                skinTexture = mc.getSkinProvider().getSkinTextures(mc.player.getGameProfile()).texture();
            }
            catch (Exception e)
            {}
        }

        FontRenderer font = context.batcher.getFont();
        String welcomePart1 = UIKeys.WELCOME_TITLE1.get();
        String welcomePart2 = " " + username + UIKeys.WELCOME_TITLE2.get();

        int w1 = font.getWidth(welcomePart1);
        int headSize = 14;
        int gapText = 4;
        int w2 = font.getWidth(welcomePart2);
        int totalW = w1 + headSize + gapText + w2;

        float titleScale = 1.25F;
        float realX = vMx;
        float greetRealY = vMy - 10;

        float drawX = (realX / titleScale) - (totalW / 2.0F);
        float drawY = greetRealY / titleScale;

        MatrixStack matrices = context.batcher.getContext().getMatrices();
        matrices.push();
        matrices.scale(titleScale, titleScale, 1.0F);

        context.batcher.textShadow(welcomePart1, drawX, drawY, Colors.setA(Colors.WHITE, alpha));
        float headX = drawX + w1;
        float headY = drawY - 2;

        if (skinTexture != null)
        {
            this.drawPlayerHead(context.batcher.getContext(), skinTexture, (int) headX, (int) headY, headSize);
        }
        else
        {
            context.batcher.iconArea(Icons.USER, Colors.setA(Colors.WHITE, alpha), headX, headY, headSize, headSize);
        }

        context.batcher.textShadow(welcomePart2, headX + headSize + gapText, drawY, Colors.setA(Colors.WHITE, alpha));
        matrices.pop();

        String subtitle = UIKeys.WELCOME_SUBTITLE.get();
        int subWidth = 380;
        int subX = (int) (vMx - subWidth / 2.0F);
        int subY = (int) (vMy + 18);

        context.batcher.wallText(subtitle, subX, subY, Colors.setA(0xCCCCCC, alpha), subWidth, 12, 0.5F, 0.0F);

        this.buttonStart.custom = true;
        this.buttonStart.customColor = Colors.setA(BBSSettings.primaryColor.get(), alpha);
        this.buttonStart.textColor = Colors.setA(Colors.WHITE, alpha);
    }

    private void renderLayoutPickerStep(UIContext context, float alpha)
    {
        float vX = this.getVirtualX();
        float vY = this.getVirtualY();
        float vMx = vX + REF_WIDTH / 2F;

        FontRenderer font = context.batcher.getFont();

        /* Top Header */
        String title = UIKeys.WELCOME_LAYOUT_TITLE.get();
        float titleScale = 1.3F;
        float titleW = font.getWidth(title);
        float titleX = (vMx / titleScale) - (titleW / 2F);
        float titleY = (vY + 24) / titleScale;

        MatrixStack matrices = context.batcher.getContext().getMatrices();
        matrices.push();
        matrices.scale(titleScale, titleScale, 1F);
        context.batcher.textShadow(title, titleX, titleY, Colors.setA(Colors.WHITE, alpha));
        matrices.pop();

        String desc = UIKeys.WELCOME_LAYOUT_DESC.get();
        int descWidth = 460;
        int descX = (int) (vMx - descWidth / 2F);
        int descY = (int) (vY + 44);
        context.batcher.wallText(desc, descX, descY, Colors.setA(0xAAAAAA, alpha), descWidth, 11, 0.5F, 0F);

        /* 4 Layout Cards starting comfortably below description */
        int cardW = 175;
        int cardH = 225;
        int gap = 12;
        int totalW = cardW * 4 + gap * 3;
        int startX = (int) (vMx - totalW / 2F);
        int startY = (int) (vY + 70);

        LayoutPreset[] presets = LayoutPreset.values();

        for (int i = 0; i < presets.length; i++)
        {
            LayoutPreset preset = presets[i];
            int cardX = startX + i * (cardW + gap);
            int cardY = startY;

            boolean isHovered = context.mouseX >= cardX && context.mouseX <= cardX + cardW
                && context.mouseY >= cardY && context.mouseY <= cardY + cardH;
            boolean isSelected = this.selectedPreset == preset;

            this.renderPresetCard(context, preset, cardX, cardY, cardW, cardH, isSelected, isHovered, alpha);
        }

        /* Buttons styling */
        this.buttonBack.custom = true;
        this.buttonBack.customColor = Colors.setA(0x3A3A42, alpha);
        this.buttonBack.textColor = Colors.setA(Colors.WHITE, alpha);

        this.buttonConfirmStep.custom = true;
        this.buttonConfirmStep.customColor = Colors.setA(BBSSettings.primaryColor.get(), alpha);
        this.buttonConfirmStep.textColor = Colors.setA(Colors.WHITE, alpha);
    }

    private void renderConfirmationStep(UIContext context, float alpha)
    {
        float vX = this.getVirtualX();
        float vY = this.getVirtualY();
        float vMx = vX + REF_WIDTH / 2F;

        FontRenderer font = context.batcher.getFont();

        /* Top Header */
        String title = UIKeys.WELCOME_CONFIRM_TITLE.get();
        float titleScale = 1.35F;
        float titleW = font.getWidth(title);
        float titleX = (vMx / titleScale) - (titleW / 2F);
        float titleY = (vY + 24) / titleScale;

        MatrixStack matrices = context.batcher.getContext().getMatrices();
        matrices.push();
        matrices.scale(titleScale, titleScale, 1F);
        context.batcher.textShadow(title, titleX, titleY, Colors.setA(Colors.WHITE, alpha));
        matrices.pop();

        String desc = String.format(UIKeys.WELCOME_CONFIRM_DESC.get(), this.selectedPreset.title.get());
        int descWidth = 480;
        int descX = (int) (vMx - descWidth / 2F);
        int descY = (int) (vY + 44);
        context.batcher.wallText(desc, descX, descY, Colors.setA(0xAAAAAA, alpha), descWidth, 11, 0.5F, 0F);

        /* Large Preview Box starting comfortably below description */
        int previewW = 420;
        int previewH = 250;
        int previewX = (int) (vMx - previewW / 2F);
        int previewY = (int) (vY + 68);

        context.batcher.box(previewX, previewY, previewX + previewW, previewY + previewH, 0xFA16161A);
        int primary = BBSSettings.primaryColor.get() | Colors.A100;
        this.outlineThick(context.batcher, previewX, previewY, previewX + previewW, previewY + previewH, primary, 2);

        this.renderPresetPreview(context.batcher, font, this.selectedPreset, previewX + 4, previewY + 4, previewW - 8, previewH - 8, true);

        /* Buttons styling */
        this.buttonChangeLayout.custom = true;
        this.buttonChangeLayout.customColor = Colors.setA(0x3A3A42, alpha);
        this.buttonChangeLayout.textColor = Colors.setA(Colors.WHITE, alpha);

        this.buttonFinish.custom = true;
        this.buttonFinish.customColor = Colors.setA(BBSSettings.primaryColor.get(), alpha);
        this.buttonFinish.textColor = Colors.setA(Colors.WHITE, alpha);
    }

    private void renderPresetCard(UIContext context, LayoutPreset preset, int x, int y, int w, int h, boolean isSelected, boolean isHovered, float alpha)
    {
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();

        int bgColor = isSelected ? 0xFA202028 : (isHovered ? 0xFA1E1E24 : 0xFA16161A);
        batcher.box(x, y, x + w, y + h, bgColor);

        if (isSelected)
        {
            int primary = BBSSettings.primaryColor.get() | Colors.A100;
            this.outlineThick(batcher, x, y, x + w, y + h, primary, 2);

            int badgeSize = 14;
            int badgeX = x + w - badgeSize - 6;
            int badgeY = y + 6;
            batcher.box(badgeX, badgeY, badgeX + badgeSize, badgeY + badgeSize, primary);
            batcher.iconArea(Icons.CHECKMARK, Colors.WHITE, badgeX + 1, badgeY + 1, badgeSize - 2, badgeSize - 2);
        }
        else if (isHovered)
        {
            this.outline(batcher, x, y, x + w, y + h, 0x80FFFFFF);
        }
        else
        {
            this.outline(batcher, x, y, x + w, y + h, 0x30FFFFFF);
        }

        /* Card Title */
        String title = preset.title.get();
        int titleColor = isSelected ? (BBSSettings.primaryColor.get() | Colors.A100) : Colors.WHITE;
        batcher.textShadow(title, x + 8, y + 8, titleColor);

        /* Accurate Layout Diagram Box */
        int diagX = x + 8;
        int diagY = y + 24;
        int diagW = w - 16;
        int diagH = 108;

        batcher.box(diagX, diagY, diagX + diagW, diagY + diagH, 0xFF0B0B0E);
        this.outline(batcher, diagX, diagY, diagX + diagW, diagY + diagH, 0x40FFFFFF);

        this.renderPresetPreview(batcher, font, preset, diagX + 1, diagY + 1, diagW - 2, diagH - 2, false);

        /* Description */
        String desc = preset.description.get();
        int descX = x + 8;
        int descY = diagY + diagH + 8;
        int descW = w - 16;
        batcher.wallText(desc, descX, descY, 0xFFA0A0A8, descW, 10, 0.0F, 0.0F);
    }

    private void renderPresetPreview(Batcher2D batcher, FontRenderer font, LayoutPreset preset, float x, float y, float w, float h, boolean isLarge)
    {
        switch (preset)
        {
            case CUSTOM:
                this.renderCustomLayout(batcher, font, x, y, w, h, isLarge);
                break;
            case PREMIERE:
                this.renderPremiereLayout(batcher, font, x, y, w, h, isLarge);
                break;
            case HORIZONTAL:
                this.renderHorizontalLayout(batcher, font, x, y, w, h, isLarge);
                break;
            case VERTICAL:
                this.renderVerticalLayout(batcher, font, x, y, w, h, isLarge);
                break;
        }
    }

    private void renderPremiereLayout(Batcher2D batcher, FontRenderer font, float x, float y, float w, float h, boolean isLarge)
    {
        /* Exact Premiere Style matching real screenshot:
           Main Area (86% width):
             Top Row (55% height):
               Left (52% width): Tabbed [ Replay (Active) | Acción ] with keyframes and waveform
               Right (48% width): Visor 3D
             Bottom Row (45% height):
               Left (28% width): Propiedades (Inspector with Audio button)
               Right (72% width): Línea de tiempo de cámara (full wide camera timeline)
           Right Sidebar (14% width, 100% height):
             Top (55% height): Reproducciones list
             Bottom (45% height): General / Elegir / Editar panel */
        float sideW = Math.max(isLarge ? 48.0F : 24.0F, w * 0.15F);
        float mainW = w - sideW;

        /* Main Area - Top Row */
        float topH = h * 0.55F;
        float botH = h - topH;
        float topReplayW = mainW * 0.52F;
        float topVisorW = mainW - topReplayW;

        this.drawTabbedPanel(batcher, font, new IKey[] {UIKeys.WELCOME_PANEL_REPLAY_TIMELINE, UIKeys.WELCOME_PANEL_ACTION_TIMELINE}, 0, x, y, topReplayW, topH, isLarge, PanelType.REPLAY_KEYFRAMES);
        this.drawPanel(batcher, font, UIKeys.WELCOME_PANEL_VISOR, x + topReplayW, y, topVisorW, topH, isLarge, PanelType.VIEWPORT);

        /* Main Area - Bottom Row */
        float botPropsW = mainW * 0.28F;
        float botCamW = mainW - botPropsW;

        this.drawPanel(batcher, font, UIKeys.WELCOME_PANEL_PROPERTIES, x, y + topH, botPropsW, botH, isLarge, PanelType.PROPERTIES);
        this.drawPanel(batcher, font, UIKeys.WELCOME_PANEL_CAMERA_TIMELINE, x + botPropsW, y + topH, botCamW, botH, isLarge, PanelType.TIMELINE);

        /* Right Sidebar - Full Height Replays */
        float sideTopH = h * 0.55F;
        float sideBotH = h - sideTopH;
        this.drawPanel(batcher, font, UIKeys.WELCOME_PANEL_REPLAYS, x + mainW, y, sideW, sideTopH, isLarge, PanelType.REPLAYS);
        this.drawPanel(batcher, font, UIKeys.WELCOME_PANEL_GENERAL, x + mainW, y + sideTopH, sideW, sideBotH, isLarge, PanelType.PROPERTIES);
    }

    private void renderHorizontalLayout(Batcher2D batcher, FontRenderer font, float x, float y, float w, float h, boolean isLarge)
    {
        /* Exact Horizontal Simplified:
           Top: Left [ Visor ] (58%) + Right [ Propiedades generales ] (42%)
           Bottom: Left [ Línea de tiempo (Tabbed Cámara / Replay / Acción) ] (68%) + Right [ Reproducciones / General ] (32%) */
        float topH = h * 0.54F;
        float botH = h - topH;

        /* Top row */
        float topVisorW = w * 0.58F;
        this.drawPanel(batcher, font, UIKeys.WELCOME_PANEL_VISOR, x, y, topVisorW, topH, isLarge, PanelType.VIEWPORT);
        this.drawPanel(batcher, font, UIKeys.WELCOME_PANEL_GENERAL_PROPERTIES, x + topVisorW, y, w - topVisorW, topH, isLarge, PanelType.PROPERTIES);

        /* Bottom row */
        float botTimelineW = w * 0.68F;
        this.drawTabbedPanel(batcher, font, new IKey[] {UIKeys.WELCOME_PANEL_CAMERA_TIMELINE, UIKeys.WELCOME_PANEL_REPLAY_TIMELINE, UIKeys.WELCOME_PANEL_ACTION_TIMELINE}, 0, x, y + topH, botTimelineW, botH, isLarge, PanelType.TIMELINE);
        this.drawPanel(batcher, font, UIKeys.WELCOME_PANEL_REPLAYS, x + botTimelineW, y + topH, w - botTimelineW, botH, isLarge, PanelType.REPLAYS);
    }

    private void renderVerticalLayout(Batcher2D batcher, FontRenderer font, float x, float y, float w, float h, boolean isLarge)
    {
        /* Exact Vertical 9:16:
           Left Column (23%): Top [ Propiedades ] + Bottom [ Reproducciones ]
           Middle Column (45%): Tabbed [ Cámara / Replay / Acción ]
           Right Column (32%): Tall Vertical 9:16 Visor */
        float col1W = w * 0.23F;
        float col3W = w * 0.32F;
        float col2W = w - col1W - col3W;

        /* Column 1 */
        float c1TopH = h * 0.44F;
        this.drawPanel(batcher, font, UIKeys.WELCOME_PANEL_GENERAL_PROPERTIES, x, y, col1W, c1TopH, isLarge, PanelType.PROPERTIES);
        this.drawPanel(batcher, font, UIKeys.WELCOME_PANEL_REPLAYS, x, y + c1TopH, col1W, h - c1TopH, isLarge, PanelType.REPLAYS);

        /* Column 2 */
        this.drawTabbedPanel(batcher, font, new IKey[] {UIKeys.WELCOME_PANEL_CAMERA_TIMELINE, UIKeys.WELCOME_PANEL_REPLAY_TIMELINE, UIKeys.WELCOME_PANEL_ACTION_TIMELINE}, 0, x + col1W, y, col2W, h, isLarge, PanelType.TIMELINE);

        /* Column 3: Tall Vertical Viewport */
        this.drawPanel(batcher, font, UIKeys.WELCOME_PANEL_VISOR_VERTICAL, x + col1W + col2W, y, col3W, h, isLarge, PanelType.VIEWPORT);
    }

    private enum PanelType
    {
        VIEWPORT, TIMELINE, REPLAY_KEYFRAMES, PROPERTIES, REPLAYS
    }

    private void drawPanel(Batcher2D batcher, FontRenderer font, IKey title, float x, float y, float w, float h, boolean isLarge, PanelType type)
    {
        if (w <= 1 || h <= 1)
        {
            return;
        }

        /* Panel background & outline */
        batcher.box(x, y, x + w, y + h, 0xFF141418);
        this.outline(batcher, x, y, x + w, y + h, 0xFF282832);

        /* Header bar */
        float hdrH = isLarge ? 12.0F : 8.0F;
        batcher.box(x, y, x + w, y + hdrH, 0xFF1C1C23);
        batcher.box(x, y + hdrH - 1, x + w, y + hdrH, 0xFF2A2A35);

        if (w >= 18 && hdrH >= 5)
        {
            float scale = isLarge ? 0.55F : 0.38F;
            MatrixStack matrices = batcher.getContext().getMatrices();
            matrices.push();
            matrices.scale(scale, scale, 1.0F);
            batcher.textShadow(title.get(), (x + 3) / scale, (y + (isLarge ? 2.5F : 1.5F)) / scale, 0xFFE0E0E0);
            matrices.pop();
        }

        /* Body content */
        float bodyY = y + hdrH;
        float bodyH = h - hdrH;

        if (bodyH <= 1)
        {
            return;
        }

        if (type == PanelType.VIEWPORT)
        {
            this.drawViewportBody(batcher, x, bodyY, w, bodyH, isLarge);
        }
        else if (type == PanelType.TIMELINE)
        {
            this.drawTimelineBody(batcher, x, bodyY, w, bodyH, isLarge);
        }
        else if (type == PanelType.REPLAY_KEYFRAMES)
        {
            this.drawReplayKeyframesBody(batcher, x, bodyY, w, bodyH, isLarge);
        }
        else if (type == PanelType.PROPERTIES)
        {
            this.drawPropertiesBody(batcher, x, bodyY, w, bodyH, isLarge);
        }
        else if (type == PanelType.REPLAYS)
        {
            this.drawReplaysBody(batcher, x, bodyY, w, bodyH, isLarge);
        }
    }

    private void drawTabbedPanel(Batcher2D batcher, FontRenderer font, IKey[] tabs, int activeTab, float x, float y, float w, float h, boolean isLarge, PanelType type)
    {
        if (w <= 1 || h <= 1)
        {
            return;
        }

        /* Tab bar */
        float tabH = isLarge ? 13.0F : 8.5F;
        batcher.box(x, y, x + w, y + tabH, 0xFF121216);
        batcher.box(x, y + tabH - 1, x + w, y + tabH, 0xFF25252E);

        float tabW = Math.min(isLarge ? 110.0F : 55.0F, w / tabs.length);

        for (int i = 0; i < tabs.length; i++)
        {
            float tx = x + i * tabW;
            boolean isActive = i == activeTab;

            if (isActive)
            {
                batcher.box(tx, y, tx + tabW, y + tabH, 0xFF1A1A22);
                batcher.box(tx, y + tabH - (isLarge ? 2.0F : 1.2F), tx + tabW, y + tabH, BBSSettings.primaryColor.get() | Colors.A100);
            }

            if (tabW >= 16)
            {
                float scale = isLarge ? 0.50F : 0.35F;
                MatrixStack matrices = batcher.getContext().getMatrices();
                matrices.push();
                matrices.scale(scale, scale, 1.0F);
                batcher.textShadow(tabs[i].get(), (tx + 3) / scale, (y + (isLarge ? 2.5F : 1.5F)) / scale, isActive ? 0xFFFFFFFF : 0xFF7A7A85);
                matrices.pop();
            }
        }

        /* Body */
        float bodyY = y + tabH;
        float bodyH = h - tabH;

        if (bodyH > 1)
        {
            if (type == PanelType.REPLAY_KEYFRAMES)
            {
                this.drawReplayKeyframesBody(batcher, x, bodyY, w, bodyH, isLarge);
            }
            else if (type == PanelType.TIMELINE)
            {
                this.drawTimelineBody(batcher, x, bodyY, w, bodyH, isLarge);
            }
            else
            {
                this.drawPropertiesBody(batcher, x, bodyY, w, bodyH, isLarge);
            }
        }

        this.outline(batcher, x, y, x + w, y + h, 0xFF282832);
    }

    private void drawSteve(Batcher2D batcher, float x, float y, float w, float h, boolean isLarge)
    {
        /* Steve Minecraft character matching accurate pixel art proportions with a clean faceless design */
        float unit = isLarge ? 1.65F : 0.82F;
        float centerX = x + w * 0.50F;
        float groundY = y + h * 0.62F + (isLarge ? 12.0F : 6.0F);

        float headW = 8 * unit;
        float headH = 8 * unit;
        float torsoW = 8 * unit;
        float torsoH = 12 * unit;
        float armW = 4 * unit;
        float armH = 12 * unit;
        float legsW = 8 * unit;
        float legsH = 12 * unit;

        float totalH = headH + torsoH + legsH; /* 32 units */
        float topY = groundY - totalH;

        /* --- HEAD (8x8) --- */
        float headX = centerX - headW / 2.0F;
        float headY = topY;

        /* Base skin */
        batcher.box(headX, headY, headX + headW, headY + headH, 0xFFD69A70);

        /* Brown Hair on top and sides */
        batcher.box(headX, headY, headX + headW, headY + 2.0F * unit, 0xFF4A3222);
        batcher.box(headX, headY + 2.0F * unit, headX + 1.0F * unit, headY + 4.5F * unit, 0xFF4A3222);
        batcher.box(headX + 7.0F * unit, headY + 2.0F * unit, headX + headW, headY + 4.5F * unit, 0xFF4A3222);

        /* --- TORSO (8x12) --- */
        float torsoX = centerX - torsoW / 2.0F;
        float torsoY = headY + headH;

        /* Cyan Shirt */
        batcher.box(torsoX, torsoY, torsoX + torsoW, torsoY + torsoH, 0xFF00BCC6);
        /* Neck V-Cut showing skin */
        batcher.box(torsoX + 2.5F * unit, torsoY, torsoX + 5.5F * unit, torsoY + 2.0F * unit, 0xFFD69A70);
        batcher.box(torsoX + 3.0F * unit, torsoY + 2.0F * unit, torsoX + 5.0F * unit, torsoY + 3.0F * unit, 0xFFD69A70);

        /* --- ARMS (4x12 each) --- */
        /* Left Arm */
        float leftArmX = torsoX - armW;
        batcher.box(leftArmX, torsoY, leftArmX + armW, torsoY + 4.0F * unit, 0xFF00BCC6); /* Sleeve */
        batcher.box(leftArmX, torsoY + 4.0F * unit, leftArmX + armW, torsoY + armH, 0xFFD69A70); /* Bare arm & hand */

        /* Right Arm */
        float rightArmX = torsoX + torsoW;
        batcher.box(rightArmX, torsoY, rightArmX + armW, torsoY + 4.0F * unit, 0xFF00BCC6); /* Sleeve */
        batcher.box(rightArmX, torsoY + 4.0F * unit, rightArmX + armW, torsoY + armH, 0xFFD69A70); /* Bare arm & hand */

        /* --- LEGS & SHOES (8x12) --- */
        float legsX = torsoX;
        float legsY = torsoY + torsoH;

        /* Blue Denim Pants (10 units high) */
        batcher.box(legsX, legsY, legsX + legsW, legsY + 10.0F * unit, 0xFF5448DE);
        /* Left/Right leg division line */
        batcher.box(legsX + 3.8F * unit, legsY, legsX + 4.2F * unit, legsY + 10.0F * unit, 0xFF3D32B0);
        /* Subtle knee shading */
        batcher.box(legsX + 1.0F * unit, legsY + 5.0F * unit, legsX + 3.0F * unit, legsY + 6.0F * unit, 0xFF463BB8);
        batcher.box(legsX + 5.0F * unit, legsY + 5.0F * unit, legsX + 7.0F * unit, legsY + 6.0F * unit, 0xFF463BB8);

        /* Grey Shoes (2 units high) */
        batcher.box(legsX, legsY + 10.0F * unit, legsX + legsW, legsY + legsH, 0xFF7A7570);
        batcher.box(legsX + 3.8F * unit, legsY + 10.0F * unit, legsX + 4.2F * unit, legsY + legsH, 0xFF504D4A);
    }

    private void drawViewportBody(Batcher2D batcher, float x, float y, float w, float h, boolean isLarge)
    {
        /* Sky & Grass */
        float skyH = h * 0.62F;
        batcher.box(x, y, x + w, y + skyH, 0xFF355E8D);
        batcher.box(x, y + skyH, x + w, y + h, 0xFF3B6833);

        /* Draw Classic Steve */
        this.drawSteve(batcher, x, y, w, h, isLarge);

        /* 3D Axis Gizmo in bottom-left */
        batcher.box(x + 3, y + h - (isLarge ? 12 : 7), x + (isLarge ? 10 : 6), y + h - (isLarge ? 11 : 6), 0xFFFF3333);
        batcher.box(x + 3, y + h - (isLarge ? 12 : 7), x + 4, y + h - (isLarge ? 5 : 2), 0xFF33FF33);

        /* Bottom transport toolbar */
        float barH = isLarge ? 10.0F : 6.0F;
        batcher.box(x, y + h - barH, x + w, y + h, 0xB0101015);
    }

    private void drawTimelineBody(Batcher2D batcher, float x, float y, float w, float h, boolean isLarge)
    {
        /* Dark grid canvas */
        batcher.box(x, y, x + w, y + h, 0xFF0D0D10);

        /* Time ruler */
        float rulerH = isLarge ? 10.0F : 6.5F;
        batcher.box(x, y, x + w, y + rulerH, 0xFF17171F);

        for (float rx = x + (isLarge ? 18 : 10); rx < x + w - 4; rx += (isLarge ? 22 : 12))
        {
            batcher.box(rx, y + rulerH - 2, rx + 1, y + rulerH, 0xFF50505E);
        }

        /* Tracks and Clips */
        float trackY = y + rulerH + 2;
        float trackH = isLarge ? 8.0F : 5.0F;

        if (h - rulerH > 10)
        {
            /* Clip 1: Red Curva */
            float clip1W = Math.min(w * 0.68F, w - 8);
            batcher.box(x + 4, trackY, x + 4 + clip1W, trackY + trackH, 0xFFE05244);

            /* Clip 2: Green Estacionario */
            if (trackY + trackH * 2 + 2 < y + h - (isLarge ? 10 : 5))
            {
                float clip2W = Math.min(w * 0.45F, w - 16);
                batcher.box(x + 6, trackY + trackH + 2, x + 6 + clip2W, trackY + trackH * 2 + 2, 0xFF2B9E5A);
            }

            /* Clip 3: Yellow Audio */
            if (trackY + trackH * 3 + 4 < y + h - (isLarge ? 10 : 5))
            {
                float clip3W = Math.min(w * 0.50F, w - 12);
                batcher.box(x + 8, trackY + trackH * 2 + 4, x + 8 + clip3W, trackY + trackH * 3 + 4, 0xFFCC9922);
            }
        }

        /* Lime playhead line */
        float playheadX = x + w * 0.24F;
        batcher.box(playheadX, y, playheadX + 1, y + h, 0xFF00FF66);

        /* Bottom transport toolbar */
        float barH = isLarge ? 10.0F : 5.0F;
        batcher.box(x, y + h - barH, x + w, y + h, 0xFF141419);
    }

    private void drawReplayKeyframesBody(Batcher2D batcher, float x, float y, float w, float h, boolean isLarge)
    {
        /* Dark canvas */
        batcher.box(x, y, x + w, y + h, 0xFF0D0D10);

        /* Left actor hierarchy column */
        float treeW = Math.min(isLarge ? 55.0F : 28.0F, w * 0.32F);
        batcher.box(x, y, x + treeW, y + h, 0xFF141418);
        batcher.box(x + treeW - 1, y, x + treeW, y + h, 0xFF25252E);

        /* Active actor header (player/alex) */
        float actorH = isLarge ? 8.0F : 5.0F;
        batcher.box(x + 1, y + 1, x + treeW - 1, y + 1 + actorH, 0xFF165BAA);

        /* Channel rows */
        float chH = isLarge ? 5.5F : 3.5F;
        float chGap = isLarge ? 3.0F : 2.0F;
        float cy = y + actorH + 3.0F;

        while (cy + chH < y + h - (isLarge ? 8.0F : 4.0F))
        {
            batcher.box(x + (isLarge ? 6.0F : 3.0F), cy, x + treeW - 2.0F, cy + chH, 0xFF1E1E26);
            cy += chH + chGap;
        }

        /* Timeline grid area */
        float gridX = x + treeW;
        float gridW = w - treeW;

        /* Ruler */
        float rulerH = isLarge ? 8.0F : 5.0F;
        batcher.box(gridX, y, gridX + gridW, y + rulerH, 0xFF17171F);

        for (float rx = gridX + (isLarge ? 12 : 7); rx < gridX + gridW - 2; rx += (isLarge ? 18 : 10))
        {
            batcher.box(rx, y + rulerH - 2, rx + 1, y + rulerH, 0xFF50505E);
        }

        /* Waveform representation at top of timeline */
        float waveY = y + rulerH + (isLarge ? 2.0F : 1.0F);
        float waveH = isLarge ? 6.0F : 3.5F;
        batcher.box(gridX + 4, waveY + waveH * 0.4F, gridX + Math.min(gridW * 0.7F, gridW - 6), waveY + waveH * 0.6F, 0xFFE0E0E0);
        batcher.box(gridX + 8, waveY, gridX + Math.min(gridW * 0.4F, gridW - 12), waveY + waveH, 0x80FFFFFF);

        /* Horizontal Keyframe Channel Lines & Keyframe Dots */
        float trackStartY = waveY + waveH + 3.0F;
        int[] trackColors = {0xFF2EA860, 0xFF80D040, 0xFFDDBB30, 0xFF2EA860, 0xFF3588D0};
        float[] kfPositions = {0.15F, 0.35F, 0.55F, 0.80F};

        int tIdx = 0;
        float trackY = trackStartY;

        while (trackY + 2 < y + h - (isLarge ? 8.0F : 4.0F))
        {
            int col = trackColors[tIdx % trackColors.length];
            /* Track guide line */
            batcher.box(gridX + 2, trackY, gridX + gridW - 2, trackY + 1, col);

            /* Keyframe diamond/dots */
            for (float pos : kfPositions)
            {
                float kx = gridX + 4 + (gridW - 12) * pos;
                if (kx < gridX + gridW - 4)
                {
                    float kSize = isLarge ? 2.5F : 1.5F;
                    batcher.box(kx - kSize, trackY - kSize + 0.5F, kx + kSize, trackY + kSize + 0.5F, 0xFFFFFFFF);
                }
            }

            trackY += isLarge ? 7.0F : 4.5F;
            tIdx++;
        }

        /* Lime playhead line */
        float playheadX = gridX + gridW * 0.22F;
        batcher.box(playheadX, y, playheadX + 1, y + h, 0xFF00FF66);

        /* Bottom transport toolbar */
        float barH = isLarge ? 8.0F : 4.5F;
        batcher.box(x, y + h - barH, x + w, y + h, 0xFF141419);
    }

    private void drawPropertiesBody(Batcher2D batcher, float x, float y, float w, float h, boolean isLarge)
    {
        batcher.box(x, y, x + w, y + h, 0xFF141418);

        float curY = y + 3;
        float fieldH = isLarge ? 6.0F : 3.5F;
        float gap = isLarge ? 4.0F : 2.5F;

        while (curY + fieldH < y + h - 2)
        {
            batcher.box(x + 4, curY, x + w - 4, curY + fieldH, 0xFF22222B);
            curY += fieldH + gap;
        }
    }

    private void drawReplaysBody(Batcher2D batcher, float x, float y, float w, float h, boolean isLarge)
    {
        batcher.box(x, y, x + w, y + h, 0xFF121216);

        /* Selected player/alex row in bright blue */
        float rowH = isLarge ? 10.0F : 6.0F;
        batcher.box(x + 2, y + 2, x + w - 2, y + 2 + rowH, 0xFF165BAA);

        /* Second item */
        if (y + 4 + rowH * 2 < y + h - (isLarge ? 12 : 7))
        {
            batcher.box(x + 2, y + 3 + rowH, x + w - 2, y + 3 + rowH * 2, 0xFF1A1A22);
        }

        /* Bottom General / Elegir button */
        float btnH = isLarge ? 10.0F : 6.0F;
        batcher.box(x + 3, y + h - btnH - 2, x + w - 3, y + h - 2, 0xFF2566B0);
    }
}
