package mchorse.bbs_mod;

import mchorse.bbs_mod.addons.AddonInfo;
import mchorse.bbs_mod.audio.SoundManager;
import mchorse.bbs_mod.blocks.ModelBlock;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.blocks.entities.TriggerBlockEntity;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.camera.clips.misc.CurveClientClip;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.client.PendingFilmLaunch;
import mchorse.bbs_mod.client.StructurePickerClient;
import mchorse.bbs_mod.client.StructurePickerRenderer;
import mchorse.bbs_mod.client.WorldLaunchHelper;
import mchorse.bbs_mod.client.renderer.ModelBlockEntityRenderer;
import mchorse.bbs_mod.client.renderer.TriggerBlockEntityRenderer;
import mchorse.bbs_mod.client.renderer.entity.ActorEntityRenderer;
import mchorse.bbs_mod.client.renderer.entity.GunProjectileEntityRenderer;
import mchorse.bbs_mod.client.renderer.item.GunItemRenderer;
import mchorse.bbs_mod.client.renderer.item.ModelBlockItemRenderer;
import mchorse.bbs_mod.client.video.VideoFormEngine;
import mchorse.bbs_mod.client.video.VideoRenderer;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.discord.DiscordPresenceManager;
import mchorse.bbs_mod.events.BBSAddonMod;
import mchorse.bbs_mod.events.register.RegisterCameraControllersEvent;
import mchorse.bbs_mod.events.register.RegisterClientSettingsEvent;
import mchorse.bbs_mod.events.register.RegisterClipInteractionEvent;
import mchorse.bbs_mod.events.register.RegisterDashboardPanelsEvent;
import mchorse.bbs_mod.events.register.RegisterDockLayoutEvent;
import mchorse.bbs_mod.events.register.RegisterDopeSheetOverlayEvent;
import mchorse.bbs_mod.events.register.RegisterExtraFormsEvent;
import mchorse.bbs_mod.events.register.RegisterFilmControllerInteractionEvent;
import mchorse.bbs_mod.events.register.RegisterFilmPreviewEvent;
import mchorse.bbs_mod.events.register.RegisterFilmSimulationEvent;
import mchorse.bbs_mod.events.register.RegisterFilmSyncEvent;
import mchorse.bbs_mod.events.register.RegisterFilmUiAddonEvent;
import mchorse.bbs_mod.events.register.RegisterFormBlendEvent;
import mchorse.bbs_mod.events.register.RegisterFormCategoriesEvent;
import mchorse.bbs_mod.events.register.RegisterFormEditorSectionEvent;
import mchorse.bbs_mod.events.register.RegisterFormEditorsEvent;
import mchorse.bbs_mod.events.register.RegisterFormPhysicsEvent;
import mchorse.bbs_mod.events.register.RegisterFormRenderPhaseEvent;
import mchorse.bbs_mod.events.register.RegisterFormsRenderersEvent;
import mchorse.bbs_mod.events.register.RegisterIconsEvent;
import mchorse.bbs_mod.events.register.RegisterImportersEvent;
import mchorse.bbs_mod.events.register.RegisterInterpolationsEvent;
import mchorse.bbs_mod.events.register.RegisterKeyframeFactoryUIEvent;
import mchorse.bbs_mod.events.register.RegisterKeyframeShapesEvent;
import mchorse.bbs_mod.events.register.RegisterL10nEvent;
import mchorse.bbs_mod.events.register.RegisterModelLoadersEvent;
import mchorse.bbs_mod.events.register.RegisterParticleComponentsEvent;
import mchorse.bbs_mod.events.register.RegisterParticleSchemeUIEvent;
import mchorse.bbs_mod.events.register.RegisterPropTransformEvent;
import mchorse.bbs_mod.events.register.RegisterRayTracingEvent;
import mchorse.bbs_mod.events.register.RegisterReplayListContextMenuEvent;
import mchorse.bbs_mod.events.register.RegisterReplayPanelEvent;
import mchorse.bbs_mod.events.register.RegisterSettingsUISectionEvent;
import mchorse.bbs_mod.events.register.RegisterShaderCurvesEvent;
import mchorse.bbs_mod.events.register.RegisterShadersEvent;
import mchorse.bbs_mod.events.register.RegisterSourcePacksEvent;
import mchorse.bbs_mod.events.register.RegisterStencilMapEvent;
import mchorse.bbs_mod.events.register.RegisterTextureInvalidationEvent;
import mchorse.bbs_mod.events.register.RegisterUIKeyframeFactoriesEvent;
import mchorse.bbs_mod.events.register.RegisterUIThemeEvent;
import mchorse.bbs_mod.events.register.RegisterUIValueFactoriesEvent;
import mchorse.bbs_mod.events.register.RegisterVideoRecordingEvent;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.FormUIPreviewCache;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.categories.UserFormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.structure.ModelCollisionLiveBake;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.FramebufferManager;
import mchorse.bbs_mod.graphics.texture.TextureManager;
import mchorse.bbs_mod.items.GunProperties;
import mchorse.bbs_mod.items.GunZoom;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.network.ServerNetwork;
import mchorse.bbs_mod.particles.ParticleManager;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.resources.packs.URLError;
import mchorse.bbs_mod.resources.packs.URLRepository;
import mchorse.bbs_mod.resources.packs.URLSourcePack;
import mchorse.bbs_mod.resources.packs.URLTextureErrorCallback;
import mchorse.bbs_mod.selectors.EntitySelectors;
import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.settings.UiStyleCapabilities;
import mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel;
import mchorse.bbs_mod.settings.ui.UIValueMap;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.text.RtlFontManager;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.WorldPropertiesHelper;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.film.FilmUiCapabilities;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIMobCaptureRecordOverlayPanel;
import mchorse.bbs_mod.ui.film.replays.overlays.UIQuickReplayOverlayPanel;
import mchorse.bbs_mod.ui.film.toolbar.TimelineToolbarDockSync;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.framework.BbsGuiScale;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.KeyframeShapeRenderers;
import mchorse.bbs_mod.ui.framework.elements.utils.CustomFontManager;
import mchorse.bbs_mod.ui.framework.styles.UIStyle;
import mchorse.bbs_mod.ui.model.UIModelPanel;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockEditorMenu;
import mchorse.bbs_mod.ui.morphing.UIMorphingPanel;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.keys.KeybindSettings;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.RecentAssetsTracker;
import mchorse.bbs_mod.utils.ScreenshotRecorder;
import mchorse.bbs_mod.utils.VideoRecorder;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.iris.IrisUtils;
import mchorse.bbs_mod.utils.iris.ShaderOpacityPatch;
import mchorse.bbs_mod.utils.resources.MinecraftSourcePack;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.impl.client.rendering.BlockEntityRendererRegistryImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import org.joml.Matrix4fStack;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class BBSModClient implements ClientModInitializer
{
    public static final List<AddonInfo> registeredAddons = new ArrayList<>();

    public static void registerAddon(AddonInfo info)
    {
        registeredAddons.add(info);
    }
    private static TextureManager textures;
    private static FramebufferManager framebuffers;
    private static SoundManager sounds;
    private static L10n l10n;

    private static ModelManager models;
    private static FormCategories formCategories;
    private static ScreenshotRecorder screenshotRecorder;
    private static VideoRecorder videoRecorder;
    private static EntitySelectors selectors;

    private static ParticleManager particles;

    private static KeyBinding keyDashboard;
    private static KeyBinding keyItemEditor;
    private static KeyBinding keyPlayFilm;
    private static KeyBinding keyPauseFilm;
    private static KeyBinding keyRecordReplay;
    private static KeyBinding keyRecordVideo;
    private static KeyBinding keyOpenReplays;
    private static KeyBinding keyOpenQuickReplays;
    private static KeyBinding keyOpenMorphing;
    private static KeyBinding keyDemorph;
    private static KeyBinding keyTeleport;
    private static KeyBinding keyZoom;
    private static KeyBinding keyToggleReplayHud;

    private static UIDashboard dashboard;

    private static CameraController cameraController = new CameraController();
    private static ModelBlockItemRenderer modelBlockItemRenderer = new ModelBlockItemRenderer();
    private static GunItemRenderer gunItemRenderer = new GunItemRenderer();
    private static Films films;
    private static GunZoom gunZoom;

    private static Replay selectedReplay;

    private static float originalFramebufferScale;

    public static TextureManager getTextures()
    {
        return textures;
    }

    public static FramebufferManager getFramebuffers()
    {
        return framebuffers;
    }

    public static SoundManager getSounds()
    {
        return sounds;
    }

    public static L10n getL10n()
    {
        return l10n;
    }

    public static ModelManager getModels()
    {
        return models;
    }

    public static FormCategories getFormCategories()
    {
        return formCategories;
    }

    public static ScreenshotRecorder getScreenshotRecorder()
    {
        return screenshotRecorder;
    }

    public static VideoRecorder getVideoRecorder()
    {
        return videoRecorder;
    }

    /**
     * Apply action/command clips up to the current export film clock before a frame is captured.
     */
    private static void syncExportActions(VideoRecorder recorder)
    {
        /* HQ already syncs (and may settle) in RenderTickCounterMixin before the world draws. */
        if (recorder.isHighQualityRender())
        {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        MinecraftServer server = client.getServer();

        if (server == null)
        {
            return;
        }

        float filmTime = recorder.getFilmTime();

        try
        {
            server.submit(() -> BBSMod.getActions().syncActionsTo(filmTime)).get(100L, TimeUnit.MILLISECONDS);
        }
        catch (Exception e)
        {
            /* Export continues even if a sync times out; next frame retries. */
        }
    }

    public static EntitySelectors getSelectors()
    {
        return selectors;
    }

    public static ParticleManager getParticles()
    {
        return particles;
    }

    public static CameraController getCameraController()
    {
        return cameraController;
    }

    public static Films getFilms()
    {
        return films;
    }

     public static void setSelectedReplay(Replay replay)
    {
        selectedReplay = replay;
    }

    public static Replay getSelectedReplay()
    {
        return selectedReplay;
    }


    public static GunZoom getGunZoom()
    {
        return gunZoom;
    }

    public static KeyBinding getKeyZoom()
    {
        return keyZoom;
    }

    public static KeyBinding getKeyRecordVideo()
    {
        return keyRecordVideo;
    }

    public static KeyBinding getKeyOpenQuickReplays()
    {
        return keyOpenQuickReplays;
    }

    public static UIDashboard getDashboard()
    {
        if (dashboard == null)
        {
            dashboard = new UIDashboard();
        }

        return dashboard;
    }

    public static UIDashboard peekDashboard()
    {
        return dashboard;
    }

    public static int getGUIScale()
    {
        float scale = BBSSettings.getUIScaleFactor();

        if (scale <= 0F)
        {
            return MinecraftClient.getInstance().options.getGuiScale().getValue();
        }

        return Math.max(1, Math.round(scale));
    }

    /**
     * The exact (possibly fractional) BBS UI scale, e.g. 1.6. Returns 0 when set to "auto" so the
     * window keeps Minecraft's computed integer scale.
     */
    public static double getUIScaleFactor()
    {
        return BBSSettings.getUIScaleFactor();
    }

    public static float getOriginalFramebufferScale()
    {
        return Math.max(originalFramebufferScale, 1);
    }

    public static ModelProperties getItemStackProperties(ItemStack stack)
    {
        ModelBlockItemRenderer.Item item = modelBlockItemRenderer.get(stack);

        if (item != null)
        {
            return item.entity.getProperties();
        }

        GunItemRenderer.Item gunItem = gunItemRenderer.get(stack);

        if (gunItem != null)
        {
            return gunItem.properties;
        }

        return null;
    }

    public static void onEndKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo info)
    {
        if (action != GLFW.GLFW_PRESS)
        {
            return;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player == null || MinecraftClient.getInstance().currentScreen != null)
        {
            return;
        }

        Morph morph = Morph.getMorph(player);

        /* Animation state trigger */
        if (morph != null && morph.getForm() != null && morph.getForm().findState(key, (form, state) ->
        {
            ClientNetwork.sendFormTrigger(state.id.get(), ServerNetwork.STATE_TRIGGER_MORPH);
            form.playState(state);
        }))
            return;

        /* Animation state trigger for items*/
        ModelProperties main = getItemStackProperties(player.getStackInHand(Hand.MAIN_HAND));
        ModelProperties offhand = getItemStackProperties(player.getStackInHand(Hand.OFF_HAND));

        if (main != null && main.getForm() != null && main.getForm().findState(key, (form, state) ->
        {
            ClientNetwork.sendFormTrigger(state.id.get(), ServerNetwork.STATE_TRIGGER_MAIN_HAND_ITEM);
            form.playState(state);
        }))
            return;

        if (offhand != null && offhand.getForm() != null && offhand.getForm().findState(key, (form, state) ->
        {
            ClientNetwork.sendFormTrigger(state.id.get(), ServerNetwork.STATE_TRIGGER_OFF_HAND_ITEM);
            form.playState(state);
        }))
            return;

        /* Change form based on the hotkey */
        for (Form form : BBSModClient.getFormCategories().getRecentForms().getCategories().get(0).getForms())
        {
            if (form.hotkey.get() == key)
            {
                ClientNetwork.sendPlayerForm(form);

                return;
            }
        }

        for (UserFormCategory category : BBSModClient.getFormCategories().getUserForms().categories)
        {
            for (Form form : category.getForms())
            {
                if (form.hotkey.get() == key)
                {
                    ClientNetwork.sendPlayerForm(form);

                    return;
                }
            }
        }
    }

    @Override
    public void onInitializeClient()
    {
        ModelCollisionLiveBake.register();

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
        {
            if (world.getBlockEntity(pos) instanceof TriggerBlockEntity)
            {
                if (player.isCreative())
                {
                    return ActionResult.PASS;
                }

                ClientNetwork.sendTriggerBlockClick(pos);

                return ActionResult.SUCCESS;
            }

            if (player.getStackInHand(hand).getItem() == BBSMod.STRUCTURE_PICKER_ITEM)
            {
                if (world.isClient)
                {
                    return StructurePickerClient.onAttackBlock();
                }

                return ActionResult.SUCCESS;
            }

            return ActionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
        {
            if (!world.isClient)
            {
                if (player.getStackInHand(hand).getItem() == BBSMod.STRUCTURE_PICKER_ITEM)
                {
                    /* Allow opening Model Block UI while holding Structure Picker. */
                    if (hitResult != null && world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof ModelBlock)
                    {
                        return ActionResult.PASS;
                    }

                    return ActionResult.SUCCESS;
                }

                return ActionResult.PASS;
            }

            return StructurePickerClient.onUseBlock(hitResult, player.isSneaking());
        });

        FabricLoader.getInstance()
            .getEntrypointContainers("bbs-addon-client", BBSAddonMod.class)
            .forEach((container) ->
            {
                BBSMod.events.register(container.getEntrypoint());
            });

        AssetProvider provider = BBSMod.getProvider();

        textures = new TextureManager(provider);
        framebuffers = new FramebufferManager();
        sounds = new SoundManager(provider);
        l10n = new L10n();
        l10n.register((lang) -> Collections.singletonList(Link.assets("strings/" + lang + ".json")));
        l10n.reload();

        BBSMod.events.post(new RegisterL10nEvent(l10n));

        File parentFile = BBSMod.getSettingsFolder().getParentFile();

        particles = new ParticleManager(() -> new File(BBSMod.getAssetsFolder(), "particles"));

        models = new ModelManager(provider);
        BBSMod.events.post(new RegisterModelLoadersEvent(models));
        formCategories = new FormCategories();
        BBSMod.events.post(new RegisterFormCategoriesEvent(formCategories));
        BBSMod.events.post(new RegisterImportersEvent());
        BBSMod.events.post(new RegisterParticleComponentsEvent(ParticleScheme.PARSER.components));
        BBSMod.events.post(new RegisterInterpolationsEvent(Interpolations.MAP));
        BBSMod.events.post(new RegisterFormsRenderersEvent());
        BBSMod.events.post(new RegisterFormEditorsEvent(UIFormEditor.panels));
        BBSMod.events.post(new RegisterIconsEvent());
        BBSMod.events.post(new RegisterUIValueFactoriesEvent(UIValueMap.factories));
        BBSMod.events.post(new RegisterUIKeyframeFactoriesEvent(UIKeyframeFactory.FACTORIES));
        BBSMod.events.post(new RegisterKeyframeShapesEvent(KeyframeShapeRenderers.SHAPES));
        BBSMod.events.post(new RegisterPropTransformEvent());
        BBSMod.events.post(new RegisterStencilMapEvent());
        BBSMod.events.post(new RegisterRayTracingEvent());
        BBSMod.events.post(new RegisterFilmPreviewEvent());
        BBSMod.events.post(new RegisterFilmUiAddonEvent());
        BBSMod.events.post(new RegisterReplayListContextMenuEvent());
        BBSMod.events.post(new RegisterReplayPanelEvent());
        BBSMod.events.post(new RegisterUIThemeEvent());
        BBSMod.events.post(new RegisterFormEditorSectionEvent());
        BBSMod.events.post(new RegisterFormRenderPhaseEvent());
        BBSMod.events.post(new RegisterFormBlendEvent());
        BBSMod.events.post(new RegisterClipInteractionEvent());
        BBSMod.events.post(new RegisterDockLayoutEvent(BBSModClient::getDashboard));
        BBSMod.events.post(new RegisterParticleSchemeUIEvent());
        BBSMod.events.post(new RegisterFilmControllerInteractionEvent());
        BBSMod.events.post(new RegisterSettingsUISectionEvent());
        BBSMod.events.post(new RegisterFilmSyncEvent());
        BBSMod.events.post(new RegisterDopeSheetOverlayEvent());
        BBSMod.events.post(new RegisterExtraFormsEvent());
        BBSMod.events.post(new RegisterShaderCurvesEvent());
        BBSMod.events.post(new RegisterCameraControllersEvent(cameraController));
        BBSMod.events.post(new RegisterFilmSimulationEvent());
        BBSMod.events.post(new RegisterVideoRecordingEvent());
        BBSMod.events.post(new RegisterTextureInvalidationEvent());
        BBSMod.events.post(new RegisterFormPhysicsEvent());
        BBSMod.events.post(new RegisterKeyframeFactoryUIEvent());
        screenshotRecorder = new ScreenshotRecorder(new File(parentFile, "screenshots"));
        videoRecorder = new VideoRecorder();
        selectors = new EntitySelectors();
        selectors.read();
        films = new Films();

        RecentAssetsTracker.load();

        BBSResources.init();

        URLRepository repository = new URLRepository(new File(parentFile, "url_cache"));

        provider.register(new URLSourcePack("http", repository));
        provider.register(new URLSourcePack("https", repository));

        KeybindSettings.registerClasses();

        BBSMod.setupConfig(Icons.KEY_CAP, "keybinds", new File(BBSMod.getSettingsFolder(), "keybinds.json"), KeybindSettings::register);

        BBSMod.events.post(new RegisterClientSettingsEvent());

        BBSSettings.language.postCallback((v, f) ->
        {
            RtlFontManager.invalidate();
            reloadLanguage(getLanguageKey());
            RtlFontManager.ensureLoaded();
        });

        BBSSettings.editorTimeMode.postCallback((v, f) ->
        {
            if (dashboard != null && dashboard.getPanels().panel instanceof UIFilmPanel panel)
            {
                panel.fillData();
            }
        });

        BBSSettings.discordPresence.postCallback((v, f) -> DiscordPresenceManager.INSTANCE.onSettingsChanged());
        BBSSettings.discordApplicationId.postCallback((v, f) -> DiscordPresenceManager.INSTANCE.onSettingsChanged());
        BBSSettings.optimizedMorphMenu.postCallback((v, f) ->
        {
            FormUIPreviewCache.clear();

            if (BBSSettings.optimizedMorphMenu.get())
            {
                getModels().preloadAll();
            }
        });

        if (BBSSettings.irisOpacityFix != null)
        {
            BBSSettings.irisOpacityFix.postCallback((v, f) ->
            {
                if (BBSRendering.isIrisLoaded())
                {
                    IrisUtils.reloadShaders();
                }
            });
        }

        if (BBSSettings.irisFormFluidPatch != null)
        {
            BBSSettings.irisFormFluidPatch.postCallback((v, f) ->
            {
                if (BBSRendering.isIrisLoaded())
                {
                    IrisUtils.reloadShaders();
                }
            });
        }

        if (BBSSettings.irisFormGlowBloomPatch != null)
        {
            BBSSettings.irisFormGlowBloomPatch.postCallback((v, f) ->
            {
                if (BBSRendering.isIrisLoaded())
                {
                    IrisUtils.reloadShaders();
                }
            });
        }

        if (BBSSettings.shaderShadowOpacity != null)
        {
            BBSSettings.shaderShadowOpacity.postCallback((v, f) ->
            {
                if (BBSRendering.isIrisLoaded())
                {
                    ShaderOpacityPatch.syncShadowOpacityDefault();
                }
            });
        }

        if (BBSSettings.shaderShadowDither != null)
        {
            BBSSettings.shaderShadowDither.postCallback((v, f) ->
            {
                if (BBSRendering.isIrisLoaded())
                {
                    IrisUtils.reloadShaders();
                }
            });
        }

        if (BBSSettings.worldGammaOverride != null && BBSSettings.worldGammaOverride.get() && BBSSettings.worldGammaPercent != null)
        {
            WorldPropertiesHelper.setGammaPercent(BBSSettings.worldGammaPercent.get());
        }
        else
        {
            WorldPropertiesHelper.clearGammaOverride();
        }

        IValueListener refreshModelHover = (v, f) ->
        {
            if (!UISettingsOverlayPanel.isDeferringLiveSettings())
            {
                BBSSettings.syncAppliedAppearance();
                refreshModelEditorHover();
            }
        };
        BBSSettings.modelEditorHoverColor.postCallback(refreshModelHover);
        BBSSettings.modelEditorHoverOpacity.postCallback(refreshModelHover);
        BBSSettings.modelEditorAltHoverColor.postCallback(refreshModelHover);
        BBSSettings.modelEditorAltHoverOpacity.postCallback(refreshModelHover);
        BBSSettings.modelEditorAltHoverMultipleColors.postCallback(refreshModelHover);
        BBSSettings.favoriteColors.postCallback(refreshModelHover);

        BBSSettings.editorTimelineToolbar.postCallback((v, f) -> TimelineToolbarDockSync.applySettingsChange());

        BBSSettings.editorSeparateReplayPropertiesPanel.postCallback((v, f) ->
        {
            if (dashboard != null && dashboard.getPanels().panel instanceof UIFilmPanel panel)
            {
                panel.applySeparateReplayPropertiesPanelSetting();
            }
        });
        BBSSettings.editorEmbeddedKeyframeSidePanel.postCallback((v, f) ->
        {
            if (dashboard != null && dashboard.getPanels().panel instanceof UIFilmPanel panel)
            {
                panel.applyEmbeddedKeyframeSidePanelSetting();
            }
        });
        BBSSettings.tooltipStyle.modes(
            UIKeys.ENGINE_TOOLTIP_STYLE_LIGHT,
            UIKeys.ENGINE_TOOLTIP_STYLE_DARK
        );
        BBSSettings.uiStyle.modes(UIKeys.ENGINE_UI_STYLE_CLASSIC);

        if (UiStyleCapabilities.isAddonStyleAvailable()
            || FilmUiCapabilities.hasAddon())
        {
            BBSSettings.uiStyle.modes(
                UIKeys.ENGINE_UI_STYLE_CLASSIC,
                UIKeys.ENGINE_UI_STYLE_ADDON
            );
        }
        else if (BBSSettings.uiStyle != null && BBSSettings.uiStyle.get() == 1)
        {
            BBSSettings.uiStyle.set(0);
        }

        BBSSettings.uiStyle.postCallback((v, f) ->
        {
            UIStyle.invalidateAddonCache();

            if (dashboard != null)
            {
                UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);

                if (panel != null)
                {
                    panel.remountForUiStyle();
                }
            }
        });

        BBSSettings.replayContextOptions.modes(
            UIKeys.CONFIG_GENERAL_COMPACTED_OPTIONS_DEFAULT,
            UIKeys.CONFIG_GENERAL_COMPACTED_OPTIONS_SEPARATED,
            UIKeys.CONFIG_GENERAL_COMPACTED_OPTIONS_COMPACTED
        );

        BBSSettings.gizmoStyle.modes(
            UIKeys.CONFIG_AXES_GIZMO_STYLE_1,
            UIKeys.CONFIG_AXES_GIZMO_STYLE_2,
            UIKeys.CONFIG_AXES_GIZMO_STYLE_3
        );

        BBSSettings.editorTimeMode.modes(
            UIKeys.CONFIG_EDITOR_TICKS_MODE,
            UIKeys.CONFIG_EDITOR_SECONDS_MODE,
            UIKeys.CONFIG_EDITOR_FRAMES_MODE
        );

        BBSSettings.keystrokeMode.modes(
            UIKeys.ENGINE_KEYSTROKES_POSITION_AUTO,
            UIKeys.ENGINE_KEYSTROKES_POSITION_BOTTOM_LEFT,
            UIKeys.ENGINE_KEYSTROKES_POSITION_BOTTOM_RIGHT,
            UIKeys.ENGINE_KEYSTROKES_POSITION_TOP_RIGHT,
            UIKeys.ENGINE_KEYSTROKES_POSITION_TOP_LEFT
        );

        UIKeys.C_KEYBIND_CATGORIES.load(KeyCombo.getCategoryKeys());
        UIKeys.C_KEYBIND_CATGORIES_TOOLTIP.load(KeyCombo.getCategoryKeys());

        /* Replace audio clip with client version that plays audio */
        BBSMod.getFactoryCameraClips()
            .register(Link.bbs("audio"), AudioClientClip.class, new ClipFactoryData(Icons.SOUND, 0xffc825))
            .register(Link.bbs("tracker"), TrackerClientClip.class, new ClipFactoryData(Icons.USER, 0x4cedfc))
            .register(Link.bbs("curve"), CurveClientClip.class, new ClipFactoryData(Icons.ARC, 0xff775f));

        /* Keybinds */
        keyDashboard = this.createKey("dashboard", GLFW.GLFW_KEY_0);
        keyItemEditor = this.createKey("item_editor", GLFW.GLFW_KEY_HOME);
        keyPlayFilm = this.createKey("play_film", GLFW.GLFW_KEY_RIGHT_CONTROL);
        keyPauseFilm = this.createKey("pause_film", GLFW.GLFW_KEY_BACKSLASH);
        keyRecordReplay = this.createKey("record_replay", GLFW.GLFW_KEY_RIGHT_ALT);
        keyRecordVideo = this.createKey("record_video", GLFW.GLFW_KEY_F4);
        keyOpenReplays = this.createKey("open_replays", GLFW.GLFW_KEY_RIGHT_SHIFT);
        keyOpenQuickReplays = this.createKey("open_quick_replays", GLFW.GLFW_KEY_RIGHT_BRACKET);
        keyOpenMorphing = this.createKey("open_morphing", GLFW.GLFW_KEY_B);
        keyDemorph = this.createKey("demorph", GLFW.GLFW_KEY_PERIOD);
        keyTeleport = this.createKey("teleport", GLFW.GLFW_KEY_Y);
        keyZoom = this.createKeyMouse("zoom", 2);
        keyToggleReplayHud = this.createKey("toggle_replay_hud", GLFW.GLFW_KEY_P);

        WorldRenderEvents.AFTER_ENTITIES.register((context) ->
        {
            BBSRendering.renderCoolStuff(context);

            if (BBSRendering.isChromaSkyEnabled())
            {
                float d = BBSRendering.getChromaSkyBillboard();

                if (d > 0)
                {
                    MatrixStack stack = context.matrixStack();
                    Color color = Colors.COLOR.set(BBSRendering.getChromaSkyColor());

                    stack.push();

                    MatrixStack.Entry peek = stack.peek();

                    peek.getPositionMatrix().identity();
                    peek.getNormalMatrix().identity();
                    stack.translate(0F, 0F, -d);

                    RenderSystem.enableDepthTest();
                    BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

                    float fov = MinecraftClient.getInstance().options.getFov().getValue();
                    float dd = d * (float) Math.pow(fov / 40F, 2F);

                    Draw.fillQuad(builder, stack,
                        -dd, -dd, 0,
                        dd, -dd, 0,
                        dd, dd, 0,
                        -dd, dd, 0,
                        color.r, color.g, color.b, 1F
                    );

                    RenderSystem.setShader(GameRenderer::getPositionColorProgram);

                    Matrix4fStack mvStack = RenderSystem.getModelViewStack();
                    mvStack.pushMatrix();
                    mvStack.identity();
                    RenderSystem.applyModelViewMatrix();

                    BufferRenderer.drawWithGlobalProgram(builder.end());

                    mvStack.popMatrix();
                    RenderSystem.applyModelViewMatrix();

                    RenderSystem.disableDepthTest();

                    stack.pop();
                }
            }
        });

        /* Soft-opacity: Iris flushes here. Vanilla Fabulous also flushes into the translucent
         * FB before combine (otherwise soft vanishes). Vanilla Fancy waits until LAST.
         * Fabulous soft-through-soft wash is an accepted limit — docs/SOFT_OPACITY_FABULOUS.md. */
        WorldRenderEvents.AFTER_TRANSLUCENT.register((context) ->
        {
            ShaderOpacityPatch.onAfterTranslucentTerrain();
        });

        WorldRenderEvents.LAST.register((context) ->
        {
            /* Fancy: primary soft flush after clouds. Fabulous: leftovers on main FB. */
            ShaderOpacityPatch.onAfterVanillaClouds();

            Draw.flushIrisBoxes();

            /* After clouds / translucents / model blocks so selection+gizmos stay on top. */
            StructurePickerRenderer.render(context);
            Draw.flushIrisBoxes();

            if (Gizmo.INSTANCE.hasDeferred())
            {
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(false);
                Gizmo.INSTANCE.renderDeferred(context.matrixStack());
                RenderSystem.depthMask(true);
            }

            if (videoRecorder.isRecording() && BBSRendering.canRender)
            {
                syncExportActions(videoRecorder);
                videoRecorder.recordFrame();
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
        {
            /* Required for ItemStack.CODEC (enchantments / components) on film
             * keyframes, undo snapshots, and inventory slots. Without this the
             * client falls back to plain NbtOps and enchanted stacks vanish. */
            BBSMod.setRegistryManager(handler.getRegistryManager());
            BBSMod.setClientRegistryManager(handler.getRegistryManager());
            RecentAssetsTracker.load();
            PendingFilmLaunch.onJoin();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
        {
            dashboard = null;
            films = new Films();
            setSelectedReplay(null);

            ClientNetwork.resetHandshake();
            films.reset();
            cameraController.reset();
            BBSMod.setRegistryManager(null);
            BBSMod.setClientRegistryManager(null);
            WorldLaunchHelper.onClientDisconnected(client);
        });

        ClientTickEvents.START_CLIENT_TICK.register((client) ->
        {
            BBSRendering.startTick();

            /* JOIN can be missed after resource reload / dimension changes; keep
             * the client registry lookup alive for enchanted ItemStack codecs. */
            if (BBSMod.getRegistryManager() == null && client.world != null)
            {
                BBSMod.setRegistryManager(client.world.getRegistryManager());
                BBSMod.setClientRegistryManager(client.world.getRegistryManager());
            }

            if (!client.isPaused())
            {
                TriggerBlockEntityRenderer.capturedTriggerBlocks.clear();
            }
        });

        ClientTickEvents.END_WORLD_TICK.register((client) ->
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (!mc.isPaused())
            {
                films.updateEndWorld();
            }

            BBSResources.tick();
        });

        ClientTickEvents.END_CLIENT_TICK.register((client) ->
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc.currentScreen instanceof UIScreen screen)
            {
                screen.update();
            }

            DiscordPresenceManager.INSTANCE.tick();

            PendingFilmLaunch.tick(mc);
            WorldLaunchHelper.tick(mc);

            cameraController.update();

            if (!mc.isPaused())
            {
                films.update();
                modelBlockItemRenderer.update();
                gunItemRenderer.update();
                textures.update();
                VideoFormEngine.tickCleanup();
                VideoRenderer.update();
            }

            StructurePickerClient.tick(mc);

            while (keyDashboard.wasPressed()) UIScreen.open(getDashboard());
            while (keyItemEditor.wasPressed()) this.keyOpenModelBlockEditor(mc);
            while (keyPlayFilm.wasPressed()) this.keyPlayFilm();
            while (keyPauseFilm.wasPressed()) this.keyPauseFilm();
            while (keyRecordReplay.wasPressed()) this.keyRecordReplay();
            while (keyRecordVideo.wasPressed())
            {
                Window window = mc.getWindow();
                int width = Math.max(window.getWidth(), 2);
                int height = Math.max(window.getHeight(), 2);

                if (width % 2 == 1) width -= width % 2;
                if (height % 2 == 1) height -= height % 2;

                videoRecorder.toggleRecording(BBSRendering.getTexture().id, width, height);
                BBSRendering.setCustomSize(videoRecorder.isRecording(), width, height);
            }
            while (keyOpenReplays.wasPressed()) this.keyOpenReplays();
            while (keyOpenQuickReplays.wasPressed())
            {
                if (!UIQuickReplayOverlayPanel.isOpened())
                {
                    this.keyOpenQuickReplays();
                }
            }
            while (keyOpenMorphing.wasPressed())
            {
                UIDashboard dashboard = getDashboard();

                UIScreen.open(dashboard);
                dashboard.setPanel(dashboard.getPanel(UIMorphingPanel.class));
            }
            while (keyDemorph.wasPressed()) ClientNetwork.sendPlayerForm(null);
            while (keyTeleport.wasPressed()) this.keyTeleport();
            while (keyToggleReplayHud.wasPressed()) BBSSettings.editorReplayHud.set(!BBSSettings.editorReplayHud.get());

            if (mc.player != null)
            {
                boolean zoom = keyZoom.isPressed();
                ItemStack stack = mc.player.getMainHandStack();

                if (gunZoom == null && zoom && stack.getItem() == BBSMod.GUN_ITEM)
                {
                    GunProperties properties = GunProperties.get(stack);

                    ClientNetwork.sendZoom(true);
                    gunZoom = new GunZoom(properties.fovTarget, properties.fovInterp, properties.fovDuration);
                }
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) ->
        {
            BBSRendering.renderHud(drawContext, tickCounter.getTickDelta(false));

            if (gunZoom != null)
            {
                gunZoom.update(keyZoom.isPressed(), tickCounter.getLastFrameDuration());

                if (gunZoom.canBeRemoved())
                {
                    ClientNetwork.sendZoom(false);
                    gunZoom = null;
                }
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register((e) ->
        {
            DiscordPresenceManager.INSTANCE.shutdown();
            BBSResources.stopWatchdog();
        });
        ClientLifecycleEvents.CLIENT_STARTED.register((e) ->
        {
            DiscordPresenceManager.INSTANCE.init();
            DiscordPresenceManager.INSTANCE.onClientStarted();
            BBSRendering.setupFramebuffer();
            provider.register(new MinecraftSourcePack());
            RtlFontManager.ensureLoaded();

            Window window = MinecraftClient.getInstance().getWindow();

            originalFramebufferScale = window.getFramebufferWidth() / window.getWidth();
        });

        URLTextureErrorCallback.EVENT.register((url, error) ->
        {
            UIBaseMenu menu = UIScreen.getCurrentMenu();

            if (menu != null)
            {
                url = url.substring(0, MathUtils.clamp(url.length(), 0, 40));

                if (error == URLError.FFMPEG)
                {
                    menu.context.notifyError(UIKeys.TEXTURE_URL_ERROR_FFMPEG.format(url));
                }
                else if (error == URLError.HTTP_ERROR)
                {
                    menu.context.notifyError(UIKeys.TEXTURE_URL_ERROR_HTTP.format(url));
                }
            }
        });

        BBSRendering.setup();

        /* Network */
        ClientNetwork.setup();

        /* Register addons from FabricLoader (common + client-only entrypoints). */
        Set<String> registeredAddonIds = new HashSet<>();

        Consumer<EntrypointContainer<BBSAddonMod>> registerCatalog =
            (container) ->
            {
                ModMetadata meta = container.getProvider().getMetadata();
                String id = meta.getId();

                if (!registeredAddonIds.add(id))
                {
                    return;
                }

                String name = meta.getName();
                String version = meta.getVersion().getFriendlyString();
                String description = meta.getDescription();
                List<String> authors = meta.getAuthors().stream().map(Person::getName).toList();

                Link icon = null;
                Optional<String> iconPath = meta.getIconPath(64);

                if (iconPath.isPresent())
                {
                    String path = iconPath.get();

                    if (path.startsWith("assets/"))
                    {
                        String relative = path.substring("assets/".length());

                        icon = new Link("mod_icons", relative);
                    }
                }

                ContactInformation contact = meta.getContact();
                String website = contact.get("homepage").orElse("");
                String issues = contact.get("issues").orElse("");
                String source = contact.get("sources").orElse("");

                registerAddon(new AddonInfo(id, name, version, description, authors, icon, website, issues, source));
            };

        FabricLoader.getInstance()
            .getEntrypointContainers("bbs-addon", BBSAddonMod.class)
            .forEach(registerCatalog);
        FabricLoader.getInstance()
            .getEntrypointContainers("bbs-addon-client", BBSAddonMod.class)
            .forEach(registerCatalog);

        /* Entity renderers */
        EntityRendererRegistry.register(BBSMod.ACTOR_ENTITY, ActorEntityRenderer::new);
        EntityRendererRegistry.register(BBSMod.GUN_PROJECTILE_ENTITY, GunProjectileEntityRenderer::new);

        BlockEntityRendererRegistry.register(BBSMod.MODEL_BLOCK_ENTITY, ModelBlockEntityRenderer::new);
        BlockEntityRendererRegistry.register(BBSMod.TRIGGER_BLOCK_ENTITY, TriggerBlockEntityRenderer::new);

        BuiltinItemRendererRegistry.INSTANCE.register(BBSMod.MODEL_BLOCK_ITEM, modelBlockItemRenderer);
        BuiltinItemRendererRegistry.INSTANCE.register(BBSMod.GUN_ITEM, gunItemRenderer);

        /* Create folders */
        BBSMod.getAudioFolder().mkdirs();
        BBSMod.getAssetsPath("textures").mkdirs();

        for (String path : List.of("alex", "alex_simple", "steve", "steve_simple"))
        {
            BBSMod.getAssetsPath("models/emoticons/" + path + "/").mkdirs();
        }

        for (String path : List.of("alex", "alex_bends", "eyes", "eyes_1px", "steve", "steve_bends"))
        {
            BBSMod.getAssetsPath("models/player/" + path + "/").mkdirs();
        }
    }

    private KeyBinding createKey(String id, int key)
    {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + BBSMod.MOD_ID + "." + id,
            InputUtil.Type.KEYSYM,
            key,
            "category." + BBSMod.MOD_ID + ".main"
        ));
    }

    private KeyBinding createKeyMouse(String id, int button)
    {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + BBSMod.MOD_ID + "." + id,
            InputUtil.Type.MOUSE,
            button,
            "category." + BBSMod.MOD_ID + ".main"
        ));
    }

    private void keyOpenModelBlockEditor(MinecraftClient mc)
    {
        ItemStack stack = mc.player.getEquippedStack(EquipmentSlot.MAINHAND);
        ModelBlockItemRenderer.Item item = modelBlockItemRenderer.get(stack);
        GunItemRenderer.Item gunItem = gunItemRenderer.get(stack);

        if (item != null)
        {
            UIScreen.open(new UIModelBlockEditorMenu(item.entity.getProperties()));
        }
        else if (gunItem != null)
        {
            UIScreen.open(new UIModelBlockEditorMenu(gunItem.properties));
        }
    }

    private void keyPlayFilm()
    {
        UIFilmPanel panel = getDashboard().getPanel(UIFilmPanel.class);

        if (panel != null && panel.hasActiveFilmSession())
        {
            Films.playFilm(panel.getData().getId(), false);
        }
    }

    private void keyPauseFilm()
    {
        UIFilmPanel panel = getDashboard().getPanel(UIFilmPanel.class);

        if (panel != null && panel.hasActiveFilmSession())
        {
            Films.pauseFilm(panel.getData().getId());
        }
    }

    private void keyRecordReplay()
    {
        UIDashboard dashboard = getDashboard();
        UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);

        if (panel != null && panel.hasActiveFilmSession())
        {
            Recorder recorder = getFilms().getRecorder();

            if (recorder != null)
            {
                recorder = BBSModClient.getFilms().stopRecording();

                if (recorder == null || recorder.hasNotStarted() || !panel.hasActiveFilmSession())
                {
                    return;
                }

                panel.applyRecordedKeyframes(recorder, panel.getData());
                panel.replayEditor.replays.replays.buildVisualList();
                panel.replayEditor.updateChannelsList();
                panel.getController().refreshEntities();
                panel.save();
            }
            else
            {
                if (UIMobCaptureRecordOverlayPanel.isOpened())
                {
                    return;
                }

                UIFilmPanel filmPanel = dashboard.getPanel(UIFilmPanel.class);

                if (filmPanel == null || !filmPanel.hasActiveFilmSession())
                {
                    return;
                }

                if (BBSSettings.recordingMobCaptureOnAlt.get())
                {
                    int cursorTick = filmPanel.getCursor();

                    UIMobCaptureRecordOverlayPanel.openInGame((setup) ->
                    {
                        if (!filmPanel.hasActiveFilmSession())
                        {
                            return;
                        }

                        Replay replay = filmPanel.replayEditor.getReplay();

                        if (replay == null)
                        {
                            replay = getSelectedReplay();
                        }

                        int index = filmPanel.getData().replays.getList().indexOf(replay);

                        if (index >= 0)
                        {
                            getFilms().startRecording(filmPanel.getData(), index, cursorTick);
                        }
                    });
                }
                else
                {
                    Replay replay = filmPanel.replayEditor.getReplay();

                    if (replay == null)
                    {
                        replay = getSelectedReplay();
                    }

                    int index = filmPanel.getData().replays.getList().indexOf(replay);

                    if (index >= 0)
                    {
                        getFilms().startRecording(filmPanel.getData(), index, filmPanel.getCursor());
                    }
                }
            }
        }
    }

    private void keyOpenReplays()
    {
        UIScreen.open(getDashboard());
    }

    private void keyOpenQuickReplays()
    {
        UIDashboard dashboard = getDashboard();

        Film quickReplayFilm = this.getQuickReplayFilm(dashboard);

        if (quickReplayFilm != null && !quickReplayFilm.replays.getList().isEmpty())
        {
            UIQuickReplayOverlayPanel.open(
                new UIQuickReplayOverlayPanel(
                    quickReplayFilm.replays.getList(),
                    getSelectedReplay(),
                    this::setQuickReplaySelection
                )
            );

            return;
        }
    }

    private void setQuickReplaySelection(Replay replay)
    {
        setSelectedReplay(replay);

        UIDashboard dashboard = getDashboard();
        UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);

        if (panel != null && panel.getData() != null && panel.getData().replays.getList().contains(replay))
        {
            panel.replayEditor.setReplay(replay);
        }
    }

    private Film getQuickReplayFilm(UIDashboard dashboard)
    {
        Replay selected = getSelectedReplay();
        UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);
        Film film = panel != null && panel.hasActiveFilmSession() ? panel.getData() : null;

        if (this.isFilmUsableForQuickSelection(film, selected))
        {
            return film;
        }

        Recorder recorder = getFilms().getRecorder();

        if (recorder != null && this.isFilmUsableForQuickSelection(recorder.film, selected))
        {
            return recorder.film;
        }

        /* Only fall back to playing controllers when a film session is still active. */
        if (panel == null || !panel.hasActiveFilmSession())
        {
            return null;
        }

        for (BaseFilmController controller : getFilms().getControllers())
        {
            if (this.isFilmUsableForQuickSelection(controller.film, selected))
            {
                return controller.film;
            }
        }

        return null;
    }

    private boolean isFilmUsableForQuickSelection(Film film, Replay selected)
    {
        if (film == null || film.replays.getList().isEmpty())
        {
            return false;
        }

        return selected == null || film.replays.getList().contains(selected);
    }

    private void keyTeleport()
    {
        UIDashboard dashboard = getDashboard();
        UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);

        if (panel != null)
        {
            panel.replayEditor.teleport();
        }
    }

    public static void reloadFromSettings()
    {
        BBSSettings.syncAppliedAppearance();
        refreshModelEditorHover();
        CustomFontManager.invalidate();
        RtlFontManager.invalidate();

        for (Settings settings : BBSMod.getSettings().modules.values())
        {
            settings.save();
        }

        reloadLanguage(getLanguageKey());

        UIDashboard dashboard = getDashboard();

        if (dashboard != null)
        {
            UIFilmPanel filmPanel = dashboard.getPanel(UIFilmPanel.class);

            if (filmPanel != null)
            {
                filmPanel.fillData();
            }
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (menu != null && mc != null)
        {
            Screen screen = mc.currentScreen;

            if (screen instanceof UIScreen uiScreen)
            {
                uiScreen.reapplyScale();
            }
            else if (BbsGuiScale.isLinkedToGame())
            {
                mc.options.getGuiScale().setValue(getGUIScale());
                mc.onResolutionChanged();
                menu.resize(mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
            }
            else
            {
                BbsGuiScale.resizeMenu(menu);
            }
        }
    }

    /** Reapplies the BBS UI scale to the currently open menu immediately (e.g. while a settings
     *  slider is being dragged), without the heavier work {@link #reloadFromSettings()} does
     *  (saving settings to disk, reloading language, etc). */
    public static void applyUIScaleLive()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (menu != null && mc != null)
        {
            Screen screen = mc.currentScreen;

            if (screen instanceof UIScreen uiScreen)
            {
                uiScreen.reapplyScale();
            }
            else if (BbsGuiScale.isLinkedToGame())
            {
                mc.options.getGuiScale().setValue(getGUIScale());
                mc.onResolutionChanged();
                menu.resize(mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
            }
            else
            {
                BbsGuiScale.resizeMenu(menu);
            }
        }
    }

    /** Applies the model editor hover color/opacity immediately (settings live-preview),
     *  refreshing both the applied snapshot the renderers read and the model editor's
     *  cached geometry highlight. */
    public static void applyModelEditorHoverLive()
    {
        BBSSettings.syncAppliedAppearance();
        refreshModelEditorHover();
    }

    private static void refreshModelEditorHover()
    {
        UIDashboard dashboard = getDashboard();

        if (dashboard == null)
        {
            return;
        }

        UIDashboardPanel panel = dashboard.getPanels().panel;

        if (panel instanceof UIModelPanel modelPanel)
        {
            modelPanel.renderer.dirty();
        }
    }

    public static String getLanguageKey()
    {
        return getLanguageKey(BBSSettings.language.get());
    }

    public static String getLanguageKey(String key)
    {
        if (key == null || key.isEmpty())
        {
            MinecraftClient client = MinecraftClient.getInstance();

            if (client == null || client.options == null)
            {
                return "";
            }

            key = client.options.language;
        }

        return key;
    }

    public static void reloadLanguage(String language)
    {
        l10n.reload(language, BBSMod.getProvider());
        RtlFontManager.ensureLoaded();
    }
}
