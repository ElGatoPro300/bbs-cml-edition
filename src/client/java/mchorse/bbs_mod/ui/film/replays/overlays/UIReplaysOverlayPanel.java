package mchorse.bbs_mod.ui.film.replays.overlays;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.MobCemPoseCapture;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.utils.ShadowSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplayList;
import mchorse.bbs_mod.ui.forms.UINestedEdit;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIDataUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import com.mojang.logging.LogUtils;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;

public class UIReplaysOverlayPanel extends UIOverlayPanel
{
    private static final int DOCKED_REPLAYS_HEIGHT = 170;
    private static final int DOCKED_TOP_SECTION_MIN = 16;
    private static final int DOCKED_BOTTOM_SECTION_MIN = 70;
    private static final int DOCKED_REPLAYS_HEIGHT_MAX = 2000;
    private static final int DOCKED_RESIZER_HEIGHT = 6;
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final List<Consumer<UIReplaysOverlayPanel>> extensions = new ArrayList<>();

    public UIReplayList replays;

    public UIElement replayProperties;
    public UIElement groupProperties;
    public UINestedEdit pickEdit;
    public UIToggle enabled;
    public UIToggle groupEnabled;
    public UITextbox label;
    public UITextbox groupLabel;
    public UITextbox nameTag;
    public UIToggle shadow;
    public UITrackpad shadowSize;
    public UITrackpad shadowSizeZ;
    public UIIcon shadowSizeLink;
    public UITrackpad shadowOpacity;
    public UITrackpad shadowOffsetX;
    public UITrackpad shadowOffsetY;
    public UITrackpad shadowOffsetZ;
    private boolean linkShadowSize = true;
    public UITrackpad looping;
    public UIToggle actor;
    public UIToggle fp;
    public UIToggle vanillaMobPlayback;
    public UIToggle relative;
    public UIElement relativeRow;
    public UITrackpad relativeOffsetX;
    public UITrackpad relativeOffsetY;
    public UITrackpad relativeOffsetZ;
    public UIToggle axesPreview;
    public UIButton pickAxesPreviewBone;
    public UIToggle dropItemsOnDeath;
    public UIButton replaceReplayInventory;
    public UIIcon reloadReplay;
    public UIIcon addReplay;
    public UIIcon dupeReplay;
    public UIIcon removeReplay;

    /* Item drop velocity configuration */
    public UITrackpad dropVelocityMinX;
    public UITrackpad dropVelocityMaxX;
    public UITrackpad dropVelocityMinY;
    public UITrackpad dropVelocityMaxY;
    public UITrackpad dropVelocityMinZ;
    public UITrackpad dropVelocityMaxZ;
    public UIElement dropVelocityLabel;
    public UIElement dropVelocityRowX;
    public UIElement dropVelocityRowY;
    public UIElement dropVelocityRowZ;
    public UIElement dropVelocityGroup;
    public UIElement itemDropsContent;
    public UIElement playbackContent;
    public UIDraggable dockedResizer;

    private UIElement propertiesHost;
    private boolean propertiesExternal;
    private Consumer<Replay> callback;
    private final UIFilmPanel filmPanel;
    private boolean docked;
    private int dockedReplaysHeight = BBSSettings.editorAnchoredReplaysPanelHeight == null ? DOCKED_REPLAYS_HEIGHT : BBSSettings.editorAnchoredReplaysPanelHeight.get();

    public UIReplaysOverlayPanel(UIFilmPanel filmPanel, Consumer<Replay> callback)
    {
        super(UIKeys.FILM_REPLAY_TITLE);

        this.filmPanel = filmPanel;
        this.callback = callback;
        this.replays = new UIReplayList((l) ->
        {
            Replay replay = l.isEmpty() ? null : l.get(l.size() - 1);

            this.setReplay(replay);

            if (this.callback != null && l.size() <= 1)
            {
                this.callback.accept(replay);
            }
        }, this, filmPanel);

        this.pickEdit = new UINestedEdit((editing) ->
        {
            if (this.replays.getCurrent().isEmpty())
            {
                return;
            }

            this.replays.openFormEditor(this.replays.getCurrent().get(0).form, editing, (form) ->
            {
                this.pickEdit.setForm(form);
                Replay replay = this.replays.getCurrentFirst();

                if (replay != null)
                {
                    MobCemPoseCapture.syncReplay(replay);
                    boolean isMobForm = form instanceof MobForm;

                    this.updateVanillaMobPlaybackVisibility(isMobForm);

                    if (isMobForm)
                    {
                        this.vanillaMobPlayback.setValue(replay.vanillaMobPlayback.get());
                    }

                    this.filmPanel.getController().createEntities();
                }
            });
        });
        this.keys().register(Keys.FORMS_PICK, () -> this.pickEdit.pick.clickItself()).inside().active(() -> !this.replays.getCurrent().isEmpty());
        this.keys().register(Keys.FORMS_EDIT, () -> this.pickEdit.edit.clickItself()).inside().active(() -> !this.replays.getCurrent().isEmpty());
        this.pickEdit.pick.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PICK_FORM);
        this.pickEdit.edit.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_EDIT_FORM);
        this.enabled = new UIToggle(UIKeys.CAMERA_PANELS_ENABLED, (b) ->
        {
            this.edit((replay) -> replay.enabled.set(b.getValue()));
            filmPanel.getController().createEntities();
        });
        this.groupEnabled = new UIToggle(UIKeys.CAMERA_PANELS_ENABLED, (b) ->
        {
            List<Replay> current = this.replays.getCurrent();

            for (Replay replay : current)
            {
                if (replay.isGroup.get())
                {
                    this.cascadeGroupEnabled(replay, b.getValue());
                }
            }
        });
        this.label = new UITextbox(1000, (s) -> this.edit((replay) ->
        {
            replay.label.set(s);
            LOGGER.info("Replay display name changed: replayId={}, label={}", replay.getId(), s);
        }));
        this.label.textbox.setPlaceholder(UIKeys.FILM_REPLAY_LABEL);

        this.groupLabel = new UITextbox(1000, (s) ->
        {
             this.edit((replay) ->
             {
                 if (replay.isGroup.get())
                 {
                     replay.label.set(s);
                     this.replays.buildVisualList();
                     this.replays.setCurrentDirect(replay);
                 }
             });
        });
        this.groupLabel.textbox.setPlaceholder(UIKeys.FILM_REPLAY_LABEL);
        this.nameTag = new UITextbox(1000, (s) -> this.edit((replay) -> replay.nameTag.set(s)));
        this.nameTag.textbox.setPlaceholder(UIKeys.FILM_REPLAY_NAME_TAG);
        this.shadow = new UIToggle(UIKeys.FILM_REPLAY_SHADOW, (b) -> this.edit((replay) -> replay.shadow.set(b.getValue())));
        this.shadowOpacity = new UITrackpad((v) -> this.editShadow((settings) -> settings.opacity = v.floatValue()));
        this.shadowOpacity.limit(0F, 1F).tooltip(UIKeys.FILM_REPLAY_SHADOW_OPACITY);
        this.shadowSize = new UITrackpad((v) -> this.setShadowSizeX(v.floatValue()));
        this.shadowSize.limit(0D).tooltip(UIKeys.FILM_REPLAY_SHADOW_SIZE_X);
        this.shadowSize.textbox.setColor(Colors.RED);
        this.shadowSizeZ = new UITrackpad((v) -> this.setShadowSizeZ(v.floatValue()));
        this.shadowSizeZ.limit(0D).tooltip(UIKeys.FILM_REPLAY_SHADOW_SIZE_Y);
        this.shadowSizeZ.textbox.setColor(Colors.GREEN);
        this.shadowSizeLink = new UIIcon(Icons.LINK, (b) -> this.toggleShadowSizeLink());
        this.shadowSizeLink.tooltip(UIKeys.FILM_REPLAY_SHADOW_SIZE_LINK);
        this.shadowSizeLink.iconColor(Colors.GRAY).activeColor(Colors.A100 + Colors.ACTIVE);
        this.updateShadowSizeLinkIcon();
        this.shadowOffsetX = new UITrackpad((v) -> this.editShadow((settings) -> settings.offsetX = v.floatValue()));
        this.shadowOffsetX.tooltip(UIKeys.FILM_REPLAY_SHADOW_OFFSET_X);
        this.shadowOffsetX.textbox.setColor(Colors.RED);
        this.shadowOffsetY = new UITrackpad((v) -> this.editShadow((settings) -> settings.offsetY = v.floatValue()));
        this.shadowOffsetY.tooltip(UIKeys.FILM_REPLAY_SHADOW_OFFSET_Y);
        this.shadowOffsetY.textbox.setColor(Colors.GREEN);
        this.shadowOffsetZ = new UITrackpad((v) -> this.editShadow((settings) -> settings.offsetZ = v.floatValue()));
        this.shadowOffsetZ.tooltip(UIKeys.FILM_REPLAY_SHADOW_OFFSET_Z);
        this.shadowOffsetZ.textbox.setColor(Colors.BLUE);
        this.looping = new UITrackpad((v) -> this.edit((replay) -> replay.looping.set(v.intValue())));
        this.looping.limit(0).integer().tooltip(UIKeys.FILM_REPLAY_LOOPING_TOOLTIP);
        this.actor = new UIToggle(UIKeys.FILM_REPLAY_ACTOR, (b) ->
        {
            String keepActiveTab = null;

            if (this.filmPanel != null)
            {
                String propertiesId = this.filmPanel.shouldRedirectProperties() ? "unifiedEditArea" : "editArea";

                keepActiveTab = this.filmPanel.getActiveTabPanelId(propertiesId);

                if (keepActiveTab == null)
                {
                    keepActiveTab = this.filmPanel.getActiveTabPanelId("replaysPanel");
                }

                if (keepActiveTab == null)
                {
                    keepActiveTab = this.filmPanel.getActiveTabPanelId("replaysPropertiesPanel");
                }

                this.filmPanel.beginSuppressLinkedPropertiesTabFocus();
            }

            try
            {
                /* Sync server tick first so combat HP rebuild matches the film cursor. */
                this.filmPanel.notifyServer(ActionState.SEEK);
                this.edit((replay) ->
                {
                    replay.actor.set(b.getValue());

                    /* Relative is stub/camera render only — clear it with actor mode. */
                    if (b.getValue() && replay.relative.get())
                    {
                        replay.relative.set(false);
                    }
                });
                this.updateRelativeAvailability(b.getValue());
                this.filmPanel.replayEditor.updateChannelsList(true);
                /* Rebuild client stubs at the current tick. Server combat state is
                 * refreshed via syncData → ActionPlayer.goTo(tick) when "actor" syncs. */
                this.filmPanel.getController().createEntities();
            }
            finally
            {
                if (this.filmPanel != null)
                {
                    this.filmPanel.endSuppressLinkedPropertiesTabFocus();

                    if (keepActiveTab != null)
                    {
                        this.filmPanel.focusPanelTab(keepActiveTab);
                    }
                }
            }
        });
        this.actor.tooltip(UIKeys.FILM_REPLAY_ACTOR_TOOLTIP);
        this.fp = new UIToggle(UIKeys.FILM_REPLAY_FP, (b) ->
        {
            Replay current = this.replays.getCurrentFirst();

            if (current == null)
            {
                return;
            }

            for (Replay replay : this.replays.getList())
            {
                if (replay.fp.get())
                {
                    replay.fp.set(false);
                }
            }

            current.fp.set(b.getValue());
        });
        this.vanillaMobPlayback = new UIToggle(UIKeys.FILM_REPLAY_VANILLA_MOB_PLAYBACK, (b) ->
        {
            Replay current = this.replays.getCurrentFirst();

            if (current != null)
            {
                current.vanillaMobPlayback.set(b.getValue());
                current.vanillaMobPlaybackSerialized = true;
            }

            filmPanel.getController().createEntities();
        });
        this.vanillaMobPlayback.tooltip(UIKeys.FILM_REPLAY_VANILLA_MOB_PLAYBACK_TOOLTIP);
        this.relative = new UIToggle(UIKeys.CAMERA_PANELS_RELATIVE, (b) -> this.edit((replay) -> replay.relative.set(b.getValue())));
        this.relative.tooltip(UIKeys.FILM_REPLAY_RELATIVE_TOOLTIP);
        this.relativeOffsetX = new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.relativeOffset, (value) -> value.get().x = v)));
        this.relativeOffsetY = new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.relativeOffset, (value) -> value.get().y = v)));
        this.relativeOffsetZ = new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.relativeOffset, (value) -> value.get().z = v)));
        this.axesPreview = new UIToggle(UIKeys.FILM_REPLAY_AXES_PREVIEW, (b) ->
        {
            this.edit((replay) -> replay.axesPreview.set(b.getValue()));
        });
        this.pickAxesPreviewBone = new UIButton(UIKeys.FILM_REPLAY_PICK_AXES_PREVIEW, (b) ->
        {
            Replay replay = filmPanel.replayEditor.getReplay();

            UIAnchorKeyframeFactory.displayAttachments(filmPanel, filmPanel.getData().replays.getList().indexOf(replay), replay.axesPreviewBone.get(), (s) ->
            {
                this.edit((r) -> r.axesPreviewBone.set(s));
            });
        });
        this.dropItemsOnDeath = new UIToggle(UIKeys.FILM_REPLAY_DROP_ITEMS_ON_DEATH, (b) ->
        {
            this.edit((replay) -> replay.dropItemsOnDeath.set(b.getValue()));
            this.updateDropVelocityVisibility(b.getValue());
        });
        this.dropItemsOnDeath.tooltip(UIKeys.FILM_REPLAY_DROP_ITEMS_ON_DEATH_TOOLTIP);
        this.replaceReplayInventory = new UIButton(UIKeys.FILM_REPLACE_INVENTORY, (b) ->
        {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;

            if (player != null)
            {
                this.edit((replay) -> BaseValue.edit(replay.inventory, (inv) -> inv.fromPlayer(player)));
            }
        });
        this.replaceReplayInventory.tooltip(UIKeys.FILM_REPLACE_INVENTORY_TOOLTIP);

        this.reloadReplay = new UIIcon(Icons.REFRESH, (b) -> this.replays.reloadReplays());
        this.reloadReplay.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_RELOAD);

        this.addReplay = new UIIcon(Icons.ADD, (b) -> this.replays.addReplay());
        this.addReplay.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_ADD);

        this.dupeReplay = new UIIcon(Icons.DUPE, (b) -> this.replays.dupeReplay());
        this.dupeReplay.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_DUPE);

        this.removeReplay = new UIIcon(Icons.REMOVE, (b) -> this.replays.removeReplay());
        this.removeReplay.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_REMOVE);

        this.dupeReplay.setEnabled(false);
        this.removeReplay.setEnabled(false);

        this.icons.add(this.reloadReplay, this.addReplay, this.dupeReplay, this.removeReplay);

        this.keys().register(Keys.REPLAYS_REMOVE, () -> this.replays.removeReplay()).inside()
            .active(() -> !this.replays.getCurrent().isEmpty());

        /* Item drop velocity configuration */
        this.dropVelocityMinX = new UITrackpad((v) -> this.edit((replay) -> replay.dropVelocityMinX.set(v.floatValue())));
        this.dropVelocityMinX.tooltip(UIKeys.FILM_REPLAY_DROP_VELOCITY_MIN_X);
        this.dropVelocityMaxX = new UITrackpad((v) -> this.edit((replay) -> replay.dropVelocityMaxX.set(v.floatValue())));
        this.dropVelocityMaxX.tooltip(UIKeys.FILM_REPLAY_DROP_VELOCITY_MAX_X);
        this.dropVelocityMinY = new UITrackpad((v) -> this.edit((replay) -> replay.dropVelocityMinY.set(v.floatValue())));
        this.dropVelocityMinY.tooltip(UIKeys.FILM_REPLAY_DROP_VELOCITY_MIN_Y);
        this.dropVelocityMaxY = new UITrackpad((v) -> this.edit((replay) -> replay.dropVelocityMaxY.set(v.floatValue())));
        this.dropVelocityMaxY.tooltip(UIKeys.FILM_REPLAY_DROP_VELOCITY_MAX_Y);
        this.dropVelocityMinZ = new UITrackpad((v) -> this.edit((replay) -> replay.dropVelocityMinZ.set(v.floatValue())));
        this.dropVelocityMinZ.tooltip(UIKeys.FILM_REPLAY_DROP_VELOCITY_MIN_Z);
        this.dropVelocityMaxZ = new UITrackpad((v) -> this.edit((replay) -> replay.dropVelocityMaxZ.set(v.floatValue())));
        this.dropVelocityMaxZ.tooltip(UIKeys.FILM_REPLAY_DROP_VELOCITY_MAX_Z);


        this.relativeRow = UI.row(this.relativeOffsetX, this.relativeOffsetY, this.relativeOffsetZ);
        this.dropVelocityLabel = UI.label(UIKeys.FILM_REPLAY_DROP_VELOCITY);
        this.dropVelocityRowX = UI.row(5, 0, this.dropVelocityMinX, this.dropVelocityMaxX);
        this.dropVelocityRowY = UI.row(5, 0, this.dropVelocityMinY, this.dropVelocityMaxY);
        this.dropVelocityRowZ = UI.row(5, 0, this.dropVelocityMinZ, this.dropVelocityMaxZ);

        this.replayProperties = UI.scrollView(6, 6);

        this.addPropertySection(UIKeys.FILM_REPLAY_SECTION_GENERAL,
            this.pickEdit, this.enabled, this.label, this.nameTag
        );
        UISection shadowSection = this.addPropertySection(UIKeys.FILM_REPLAY_SHADOW,
            this.shadow,
            UI.label(UIKeys.FILM_REPLAY_SHADOW_OPACITY),
            this.shadowOpacity,
            UI.label(UIKeys.FILM_REPLAY_SHADOW_WIDTH),
            UI.row(this.shadowSize, this.shadowSizeLink, this.shadowSizeZ),
            UI.label(UIKeys.FILM_REPLAY_SHADOW_OFFSET),
            UI.row(this.shadowOffsetX, this.shadowOffsetY, this.shadowOffsetZ)
        );

        shadowSection.context((menu) ->
        {
            menu.action(Icons.CLOSE, UIKeys.FILM_REPLAY_SHADOW_RESET_ALL, this::resetShadowAll);
            menu.action(Icons.VISIBLE, UIKeys.FILM_REPLAY_SHADOW_RESET_OPACITY, this::resetShadowOpacity);
            menu.action(Icons.SCALE, UIKeys.FILM_REPLAY_SHADOW_RESET_WIDTH, this::resetShadowWidth);
            menu.action(Icons.ALL_DIRECTIONS, UIKeys.FILM_REPLAY_SHADOW_RESET_OFFSET, this::resetShadowOffset);
        });

        UISection playbackSection = this.addPropertySection(UIKeys.FILM_REPLAY_SECTION_PLAYBACK,
            this.looping, this.actor, this.fp
        );
        this.playbackContent = playbackSection.fields;

        this.addPropertySection(UIKeys.FILM_REPLAY_SECTION_POSITIONING,
            this.relative, this.relativeRow, this.axesPreview, this.pickAxesPreviewBone
        );
        this.dropVelocityGroup = UI.column(4,
            this.dropVelocityLabel,
            this.dropVelocityRowX, this.dropVelocityRowY, this.dropVelocityRowZ,
            this.replaceReplayInventory
        );
        UISection itemDropsSection = this.addPropertySection(UIKeys.FILM_REPLAY_SECTION_ITEM_DROPS,
            this.dropItemsOnDeath
        );
        this.itemDropsContent = itemDropsSection.fields;

        this.groupProperties = UI.scrollView(6, 6);
        UISection groupSection = new UISection(UIKeys.FILM_REPLAY_SECTION_GENERAL);
        groupSection.fields.add(this.groupEnabled, this.groupLabel);
        this.groupProperties.add(groupSection);
        this.dockedResizer = new UIDraggable((context) ->
        {
            int bottomHeight = this.content.area.ey() - context.mouseY;
            int maxHeight = Math.min(DOCKED_REPLAYS_HEIGHT_MAX, Math.max(DOCKED_BOTTOM_SECTION_MIN, this.content.area.h - DOCKED_TOP_SECTION_MIN - DOCKED_RESIZER_HEIGHT));

            this.dockedReplaysHeight = MathUtils.clamp(bottomHeight, DOCKED_BOTTOM_SECTION_MIN, maxHeight);
            this.persistDockedReplaysHeight();
            this.updateDockedLayout();
            this.resize();
        }).rendering((context) ->
        {
            int color = Colors.setA(BBSSettings.primaryColor.get(), this.dockedResizer.isDragging() || this.dockedResizer.area.isInside(context) ? 0.75F : 0.45F);

            context.batcher.box(this.dockedResizer.area.x, this.dockedResizer.area.y + 2, this.dockedResizer.area.ex(), this.dockedResizer.area.ey() - 2, color);
        }).dragEnd(this::flushDockedReplaysHeight);

        this.content.add(this.replays, this.replayProperties, this.groupProperties, this.dockedResizer);
        this.replayProperties.relative(this.content).x(0).y(0).w(1F).h(1F);
        this.groupProperties.relative(this.content).x(0).y(0).w(1F).h(1F);
        this.updateDockedLayout();

        for (Consumer<UIReplaysOverlayPanel> consumer : extensions)
        {
            consumer.accept(this);
        }
    }

    private UISection addPropertySection(IKey title, UIElement... content)
    {
        UISection section = new UISection(title);

        for (UIElement element : content)
        {
            if (element != null)
            {
                section.fields.add(element);
            }
        }

        this.replayProperties.add(section);

        return section;
    }

    public void attachPropertiesHost(UIElement host)
    {
        this.propertiesHost = host;
        this.setPropertiesExternal(true);
    }

    public void setPropertiesExternal(boolean external)
    {
        if (this.propertiesExternal == external)
        {
            return;
        }

        this.propertiesExternal = external;
        UIElement target = external ? this.propertiesHost : this.content;

        if (target == null)
        {
            return;
        }

        /* The UI framework doesn't guarantee that adding an element to another parent
           automatically detaches it from its previous one. Ensure these property panels
           exist in exactly one place to avoid rendering/hit-testing even when the
           external host window is hidden or collapsed. */
        this.replayProperties.removeFromParent();
        this.groupProperties.removeFromParent();

        target.add(this.replayProperties, this.groupProperties);
        this.replayProperties.relative(target).x(0).y(0).w(1F).h(1F);
        this.groupProperties.relative(target).x(0).y(0).w(1F).h(1F);
        this.updateDockedLayout();

        if (this.propertiesHost != null)
        {
            this.propertiesHost.resize();
        }

        this.resize();
    }

    public boolean isPropertiesExternal()
    {
        return this.propertiesExternal;
    }

    public void setDocked(boolean docked)
    {
        this.docked = docked;

        if (docked)
        {
            if (BBSSettings.editorAnchoredReplaysPanelHeight != null)
            {
                this.dockedReplaysHeight = BBSSettings.editorAnchoredReplaysPanelHeight.get();
            }

            /* The surrounding window/layout provides the card title bar, so this panel
             * hides its own title (collapsed to a zero area so it can't intercept list
             * clicks) and the content fills the whole element. */
            this.title.setVisible(false);
            this.icons.setVisible(false);
            this.close.setVisible(false);
            this.title.relative(this).xy(0, 0).w(0).h(0);
            this.content.relative(this).xy(0, 0).w(1F).h(1F);
        }
        else
        {
            this.title.setVisible(true);
            this.icons.setVisible(true);
            this.close.setVisible(true);

            this.title.labelAnchor(0, 0.5F).relative(this).xy(6, 0).w(0.6F).h(20);
            this.icons.relative(this).x(1F, -20).y(0).w(20).h(1F).column(0).stretch();
            this.content.relative(this).xy(0, 20).w(1F, -20).h(1F, -20);
        }

        this.updateDockedLayout();
    }

    private void updateDockedLayout()
    {
        if (this.docked)
        {
            if (this.propertiesExternal)
            {
                this.replays.relative(this.content).x(0).y(0).w(1F).h(1F);
                this.dockedResizer.setVisible(false);

                return;
            }

            int maxHeight = Math.min(DOCKED_REPLAYS_HEIGHT_MAX, Math.max(DOCKED_BOTTOM_SECTION_MIN, this.content.area.h - DOCKED_TOP_SECTION_MIN - DOCKED_RESIZER_HEIGHT));
            this.dockedReplaysHeight = MathUtils.clamp(this.dockedReplaysHeight, DOCKED_BOTTOM_SECTION_MIN, DOCKED_REPLAYS_HEIGHT_MAX);

            boolean clampToAvailableHeight = this.content.area.h >= DOCKED_TOP_SECTION_MIN + DOCKED_BOTTOM_SECTION_MIN + DOCKED_RESIZER_HEIGHT;
            int dockedHeight = this.dockedReplaysHeight;

            if (clampToAvailableHeight)
            {
                dockedHeight = MathUtils.clamp(dockedHeight, DOCKED_BOTTOM_SECTION_MIN, maxHeight);
                this.dockedReplaysHeight = dockedHeight;
            }

            int replaysHeight = -(dockedHeight + DOCKED_RESIZER_HEIGHT);

            this.replays.relative(this.content).x(0).y(0).w(1F).h(1F, replaysHeight);
            this.dockedResizer.relative(this.content).x(0).y(1F, -(dockedHeight + DOCKED_RESIZER_HEIGHT)).w(1F).h(0F, DOCKED_RESIZER_HEIGHT);
            this.replayProperties.relative(this.content).x(0).y(1F, -dockedHeight).w(1F).h(0F, dockedHeight);
            this.groupProperties.relative(this.content).x(0).y(1F, -dockedHeight).w(1F).h(0F, dockedHeight);
            this.dockedResizer.setVisible(true);
        }
        else
        {
            this.replays.relative(this.content).x(0).y(0).w(0.5F).h(1F);
            this.replayProperties.relative(this.replays).x(1F).y(0).w(1F, -20).h(1F);
            this.groupProperties.relative(this.replays).x(1F).y(0).w(1F, -20).h(1F);
            this.dockedResizer.setVisible(false);
        }
    }

    private void persistDockedReplaysHeight()
    {
        if (BBSSettings.editorAnchoredReplaysPanelHeight != null)
        {
            BBSSettings.editorAnchoredReplaysPanelHeight.set(this.dockedReplaysHeight);
        }
    }

    private void flushDockedReplaysHeight()
    {
        this.persistDockedReplaysHeight();

        Settings settings = BBSMod.getSettings().modules.get("bbs");

        if (settings != null)
        {
            settings.save();
        }
    }

    @Override
    public void resize()
    {
        super.resize();
        this.updateDockedLayout();
    }

    public boolean isDocked()
    {
        return this.docked;
    }

    private void edit(Consumer<Replay> consumer)
    {
        if (consumer != null)
        {
            List<Replay> current = this.replays.getCurrent();

            for (Replay replay : current)
            {
                consumer.accept(replay);
            }
        }
    }

    private void cascadeGroupEnabled(Replay group, boolean enabled)
    {
        group.enabled.set(enabled);

        Film film = this.filmPanel.getData();

        if (film == null)
        {
            return;
        }

        String groupPath = this.getGroupFullPath(group);

        for (Replay replay : film.replays.getAllTyped())
        {
            if (replay == group)
            {
                continue;
            }

            String path = replay.group.get();

            if (path.equals(groupPath) || path.startsWith(groupPath + "/"))
            {
                replay.enabled.set(enabled);
            }
        }

        this.filmPanel.getController().createEntities();
        this.replays.update();
    }

    private String getGroupFullPath(Replay group)
    {
        String path = group.group.get();

        return path.isEmpty() ? group.uuid.get() : path + "/" + group.uuid.get();
    }

    public void syncReplaySelection(Replay replay, boolean scroll)
    {
        this.setReplay(replay);

        if (replay == null)
        {
            this.replays.deselect();
            this.replays.update();

            return;
        }

        this.replays.ensureVisible(replay);

        if (scroll)
        {
            this.replays.setCurrentScroll(replay);
        }
        else
        {
            this.replays.setCurrentDirect(replay);
        }

        this.replays.update();
    }

    public void setReplay(Replay replay)
    {
        boolean hasReplay = replay != null;
        boolean isGroup = hasReplay && replay.isGroup.get();

        this.dupeReplay.setEnabled(hasReplay && !isGroup);
        this.removeReplay.setEnabled(hasReplay);

        this.replayProperties.setVisible(hasReplay && !isGroup);
        this.groupProperties.setVisible(hasReplay && isGroup);

        if (hasReplay)
        {
            if (isGroup)
            {
                this.groupLabel.setText(replay.label.get());
                this.groupEnabled.setValue(replay.enabled.get());
            }
            else
            {
                this.label.setText(replay.label.get());

                this.pickEdit.setForm(replay.form.get());
                this.enabled.setValue(replay.enabled.get());
                this.nameTag.setText(replay.nameTag.get());
                this.shadow.setValue(replay.shadow.get());

                ShadowSettings shadow = BaseFilmController.resolveShadowSettings(replay, 0F);

                this.shadowSize.setValue(shadow.widthX);
                this.shadowSizeZ.setValue(shadow.widthZ);
                this.shadowOpacity.setValue(shadow.opacity);
                this.shadowOffsetX.setValue(shadow.offsetX);
                this.shadowOffsetY.setValue(shadow.offsetY);
                this.shadowOffsetZ.setValue(shadow.offsetZ);
                this.looping.setValue(replay.looping.get());
                this.actor.setValue(replay.actor.get());
                this.fp.setValue(replay.fp.get());
                MobCemPoseCapture.syncReplay(replay);
                boolean isMobForm = replay.form.get() instanceof MobForm;

                this.updateVanillaMobPlaybackVisibility(isMobForm);

                if (isMobForm)
                {
                    this.vanillaMobPlayback.setValue(replay.vanillaMobPlayback.get());
                }

                this.relative.setValue(replay.relative.get());
                this.relativeOffsetX.setValue(replay.relativeOffset.get().x);
                this.relativeOffsetY.setValue(replay.relativeOffset.get().y);
                this.relativeOffsetZ.setValue(replay.relativeOffset.get().z);
                this.updateRelativeAvailability(replay.actor.get());
                this.axesPreview.setValue(replay.axesPreview.get());
                this.dropItemsOnDeath.setValue(replay.dropItemsOnDeath.get());
                this.dropVelocityMinX.setValue(replay.dropVelocityMinX.get());
                this.dropVelocityMaxX.setValue(replay.dropVelocityMaxX.get());
                this.dropVelocityMinY.setValue(replay.dropVelocityMinY.get());
                this.dropVelocityMaxY.setValue(replay.dropVelocityMaxY.get());
                this.dropVelocityMinZ.setValue(replay.dropVelocityMinZ.get());
                this.dropVelocityMaxZ.setValue(replay.dropVelocityMaxZ.get());
                this.updateDropVelocityVisibility(replay.dropItemsOnDeath.get());
            }
        }
    }

    private void updateRelativeAvailability(boolean actorMode)
    {
        boolean relativeAllowed = !actorMode;

        this.relative.setEnabled(relativeAllowed);
        this.relativeOffsetX.setEnabled(relativeAllowed);
        this.relativeOffsetY.setEnabled(relativeAllowed);
        this.relativeOffsetZ.setEnabled(relativeAllowed);
        this.relative.tooltip(relativeAllowed
            ? UIKeys.FILM_REPLAY_RELATIVE_TOOLTIP
            : UIKeys.FILM_REPLAY_RELATIVE_ACTOR_DISABLED_TOOLTIP);

        if (actorMode)
        {
            this.relative.setValue(false);
        }
        else
        {
            Replay current = this.replays.getCurrentFirst();

            if (current != null)
            {
                this.relative.setValue(current.relative.get());
            }
        }
    }

    private void updateDropVelocityVisibility(boolean visible)
    {
        boolean present = this.itemDropsContent.getChildren().contains(this.dropVelocityGroup);

        if (visible && !present)
        {
            this.itemDropsContent.add(this.dropVelocityGroup);
            this.resize();
        }
        else if (!visible && present)
        {
            this.itemDropsContent.remove(this.dropVelocityGroup);
            this.resize();
        }
    }

    private void updateVanillaMobPlaybackVisibility(boolean visible)
    {
        boolean present = this.playbackContent.getChildren().contains(this.vanillaMobPlayback);

        if (visible && !present)
        {
            this.playbackContent.add(this.vanillaMobPlayback);
            this.resize();
        }
        else if (!visible && present)
        {
            this.playbackContent.remove(this.vanillaMobPlayback);
            this.resize();
        }
    }

    private void editShadow(Consumer<ShadowSettings> editor)
    {
        this.edit((replay) ->
        {
            ShadowSettings settings = BaseFilmController.resolveShadowSettings(replay, 0F).copy();

            editor.accept(settings);
            this.writeShadowSettings(replay, settings);
        });
    }

    private void writeShadowSettings(Replay replay, ShadowSettings settings)
    {
        replay.shadowOpacity.set(settings.opacity);
        replay.shadowSize.set(settings.widthX);
        replay.shadowSizeZ.set(settings.widthZ);
        replay.shadowOffsetX.set(settings.offsetX);
        replay.shadowOffsetY.set(settings.offsetY);
        replay.shadowOffsetZ.set(settings.offsetZ);

        ShadowSettings size = new ShadowSettings();

        size.widthX = settings.widthX;
        size.widthZ = settings.widthZ;
        size.offsetX = settings.offsetX;
        size.offsetY = settings.offsetY;
        size.offsetZ = settings.offsetZ;
        size.opacity = 1F;

        if (replay.keyframes.shadowSize.isEmpty())
        {
            replay.keyframes.shadowSize.insert(0, size.copy());
        }
        else
        {
            Keyframe<ShadowSettings> firstSize = replay.keyframes.shadowSize.get(0);

            if (firstSize != null && firstSize.getTick() == 0F)
            {
                firstSize.setValue(size.copy(), true);
            }
        }

        if (replay.keyframes.shadowOpacity.isEmpty())
        {
            replay.keyframes.shadowOpacity.insert(0, (double) settings.opacity);
        }
        else
        {
            Keyframe<Double> firstOpacity = replay.keyframes.shadowOpacity.get(0);

            if (firstOpacity != null && firstOpacity.getTick() == 0F)
            {
                firstOpacity.setValue((double) settings.opacity, true);
            }
        }
    }

    private void resetShadowAll()
    {
        this.editShadow((settings) ->
        {
            ShadowSettings defaults = new ShadowSettings();

            settings.opacity = defaults.opacity;
            settings.widthX = defaults.widthX;
            settings.widthZ = defaults.widthZ;
            settings.offsetX = defaults.offsetX;
            settings.offsetY = defaults.offsetY;
            settings.offsetZ = defaults.offsetZ;
        });
        this.refreshShadowFields();
    }

    private void resetShadowOpacity()
    {
        this.editShadow((settings) -> settings.opacity = 1F);
        this.refreshShadowFields();
    }

    private void resetShadowWidth()
    {
        this.editShadow((settings) ->
        {
            settings.widthX = 0.5F;
            settings.widthZ = 0.5F;
        });
        this.refreshShadowFields();
    }

    private void resetShadowOffset()
    {
        this.editShadow((settings) ->
        {
            settings.offsetX = 0F;
            settings.offsetY = 0F;
            settings.offsetZ = 0F;
        });
        this.refreshShadowFields();
    }

    private void refreshShadowFields()
    {
        Replay replay = this.replays.getCurrentFirst();

        if (replay == null)
        {
            return;
        }

        ShadowSettings shadow = BaseFilmController.resolveShadowSettings(replay, 0F);

        this.shadowSize.setValue(shadow.widthX);
        this.shadowSizeZ.setValue(shadow.widthZ);
        this.shadowOpacity.setValue(shadow.opacity);
        this.shadowOffsetX.setValue(shadow.offsetX);
        this.shadowOffsetY.setValue(shadow.offsetY);
        this.shadowOffsetZ.setValue(shadow.offsetZ);
    }

    private void setShadowSizeX(float value)
    {
        this.editShadow((settings) ->
        {
            settings.widthX = value;

            if (this.linkShadowSize)
            {
                settings.widthZ = value;
                this.shadowSizeZ.setValue(value);
            }
        });
    }

    private void setShadowSizeZ(float value)
    {
        this.editShadow((settings) ->
        {
            settings.widthZ = value;

            if (this.linkShadowSize)
            {
                settings.widthX = value;
                this.shadowSize.setValue(value);
            }
        });
    }

    private void toggleShadowSizeLink()
    {
        this.linkShadowSize = !this.linkShadowSize;
        this.updateShadowSizeLinkIcon();

        if (this.linkShadowSize)
        {
            float x = (float) this.shadowSize.getValue();

            this.editShadow((settings) ->
            {
                settings.widthZ = x;
                this.shadowSizeZ.setValue(x);
            });
        }
    }

    private void updateShadowSizeLinkIcon()
    {
        this.shadowSizeLink.active(this.linkShadowSize);
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        if (!this.docked)
        {
            super.renderBackground(context);
        }
        else
        {
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF141418);
            context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF2A2A35, 1);
        }

        this.content.area.render(context.batcher, 0xFF141418);

        if (this.replays.getList().isEmpty())
        {
            UIDataUtils.renderRightClickHere(context, this.replays.area, 0xFF141418);
        }
    }
}
