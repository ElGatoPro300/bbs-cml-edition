package mchorse.bbs_mod.addons;

import mchorse.bbs_mod.events.BBSAddonMod;
import mchorse.bbs_mod.events.Subscribe;
import mchorse.bbs_mod.events.register.RegisterAudioDecodersEvent;
import mchorse.bbs_mod.events.register.RegisterCameraControllersEvent;
import mchorse.bbs_mod.events.register.RegisterClientSettingsEvent;
import mchorse.bbs_mod.events.register.RegisterClipInteractionEvent;
import mchorse.bbs_mod.events.register.RegisterDashboardPanelsEvent;
import mchorse.bbs_mod.events.register.RegisterDockLayoutEvent;
import mchorse.bbs_mod.events.register.RegisterDopeSheetOverlayEvent;
import mchorse.bbs_mod.events.register.RegisterExtraFormsEvent;
import mchorse.bbs_mod.events.register.RegisterFilmControllerInteractionEvent;
import mchorse.bbs_mod.events.register.RegisterFilmEditorFactoriesEvent;
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
import mchorse.bbs_mod.events.register.RegisterGizmoEvent;
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
import mchorse.bbs_mod.events.register.RegisterReplayLifecycleEvent;
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
import mchorse.bbs_mod.events.register.RegisterUndoEvent;
import mchorse.bbs_mod.events.register.RegisterVideoRecordingEvent;

/**
 * Base class for BBS client addons.
 *
 * <p>Use this class for client-side only addons.
 * In fabric.mod.json, register this using "bbs-addon-client" entrypoint.</p>
 */
public abstract class BBSClientAddon implements BBSAddonMod
{
    @Subscribe
    public void onRegisterClientSettings(RegisterClientSettingsEvent event)
    {
        this.registerClientSettings(event);
    }

    @Subscribe
    public void onRegisterDashboardPanels(RegisterDashboardPanelsEvent event)
    {
        this.registerDashboardPanels(event);
    }

    @Subscribe
    public void onRegisterFormCategories(RegisterFormCategoriesEvent event)
    {
        this.registerFormCategories(event);
    }

    @Subscribe
    public void onRegisterL10n(RegisterL10nEvent event)
    {
        this.registerL10n(event);
    }

    @Subscribe
    public void onRegisterImporters(RegisterImportersEvent event)
    {
        this.registerImporters(event);
    }

    @Subscribe
    public void onRegisterParticleComponents(RegisterParticleComponentsEvent event)
    {
        this.registerParticleComponents(event);
    }

    @Subscribe
    public void onRegisterModelLoaders(RegisterModelLoadersEvent event)
    {
        this.registerModelLoaders(event);
    }

    protected void registerClientSettings(RegisterClientSettingsEvent event)
    {}

    protected void registerDashboardPanels(RegisterDashboardPanelsEvent event)
    {}

    protected void registerFormCategories(RegisterFormCategoriesEvent event)
    {}

    protected void registerL10n(RegisterL10nEvent event)
    {}

    protected void registerImporters(RegisterImportersEvent event)
    {}

    protected void registerParticleComponents(RegisterParticleComponentsEvent event)
    {}

    protected void registerModelLoaders(RegisterModelLoadersEvent event)
    {}

    @Subscribe
    public void onRegisterInterpolations(RegisterInterpolationsEvent event)
    {
        this.registerInterpolations(event);
    }

    @Subscribe
    public void onRegisterFilmEditorFactories(RegisterFilmEditorFactoriesEvent event)
    {
        this.registerFilmEditorFactories(event);
    }

    protected void registerFilmEditorFactories(RegisterFilmEditorFactoriesEvent event)
    {}

    @Subscribe
    public void onRegisterFilmUiAddon(RegisterFilmUiAddonEvent event)
    {
        this.registerFilmUiAddon(event);
    }

    protected void registerFilmUiAddon(RegisterFilmUiAddonEvent event)
    {}

    @Subscribe
    public void onRegisterDopeSheetOverlay(RegisterDopeSheetOverlayEvent event)
    {
        this.registerDopeSheetOverlay(event);
    }

    protected void registerDopeSheetOverlay(RegisterDopeSheetOverlayEvent event)
    {}

    @Subscribe
    public void onRegisterExtraForms(RegisterExtraFormsEvent event)
    {
        this.registerExtraForms(event);
    }

    protected void registerExtraForms(RegisterExtraFormsEvent event)
    {}

    @Subscribe
    public void onRegisterShaderCurves(RegisterShaderCurvesEvent event)
    {
        this.registerShaderCurves(event);
    }

    protected void registerShaderCurves(RegisterShaderCurvesEvent event)
    {}

    @Subscribe
    public void onRegisterCameraControllers(RegisterCameraControllersEvent event)
    {
        this.registerCameraControllers(event);
    }

    protected void registerCameraControllers(RegisterCameraControllersEvent event)
    {}

    @Subscribe
    public void onRegisterFilmSimulation(RegisterFilmSimulationEvent event)
    {
        this.registerFilmSimulation(event);
    }

    protected void registerFilmSimulation(RegisterFilmSimulationEvent event)
    {}

    @Subscribe
    public void onRegisterVideoRecording(RegisterVideoRecordingEvent event)
    {
        this.registerVideoRecording(event);
    }

    protected void registerVideoRecording(RegisterVideoRecordingEvent event)
    {}

    @Subscribe
    public void onRegisterTextureInvalidation(RegisterTextureInvalidationEvent event)
    {
        this.registerTextureInvalidation(event);
    }

    protected void registerTextureInvalidation(RegisterTextureInvalidationEvent event)
    {}

    @Subscribe
    public void onRegisterFormPhysics(RegisterFormPhysicsEvent event)
    {
        this.registerFormPhysics(event);
    }

    protected void registerFormPhysics(RegisterFormPhysicsEvent event)
    {}

    @Subscribe
    public void onRegisterKeyframeFactoryUI(RegisterKeyframeFactoryUIEvent event)
    {
        this.registerKeyframeFactoryUI(event);
    }

    protected void registerKeyframeFactoryUI(RegisterKeyframeFactoryUIEvent event)
    {}

    @Subscribe
    public void onRegisterFormsRenderers(RegisterFormsRenderersEvent event)
    {
        this.registerFormsRenderers(event);
    }

    @Subscribe
    public void onRegisterGizmos(RegisterGizmoEvent event)
    {
        this.registerGizmos(event);
    }

    protected void registerGizmos(RegisterGizmoEvent event)
    {}

    @Subscribe
    public void onRegisterIcons(RegisterIconsEvent event)
    {
        this.registerIcons(event);
    }

    @Subscribe
    public void onRegisterUIKeyframeFactories(RegisterUIKeyframeFactoriesEvent event)
    {
        this.registerUIKeyframeFactories(event);
    }

    protected void registerInterpolations(RegisterInterpolationsEvent event)
    {}

    protected void registerFormsRenderers(RegisterFormsRenderersEvent event)
    {}

    protected void registerIcons(RegisterIconsEvent event)
    {}

    protected void registerUIKeyframeFactories(RegisterUIKeyframeFactoriesEvent event)
    {}

    @Subscribe
    public void onRegisterFormEditors(RegisterFormEditorsEvent event)
    {
        this.registerFormEditors(event);
    }

    protected void registerFormEditors(RegisterFormEditorsEvent event)
    {}

    @Subscribe
    public void onRegisterKeyframeShapes(RegisterKeyframeShapesEvent event)
    {
        this.registerKeyframeShapes(event);
    }

    protected void registerKeyframeShapes(RegisterKeyframeShapesEvent event)
    {}

    @Subscribe
    public void onRegisterUIValueFactories(RegisterUIValueFactoriesEvent event)
    {
        this.registerUIValueFactories(event);
    }

    protected void registerUIValueFactories(RegisterUIValueFactoriesEvent event)
    {}

    @Subscribe
    public void onRegisterPropTransforms(RegisterPropTransformEvent event)
    {
        this.registerPropTransforms(event);
    }

    protected void registerPropTransforms(RegisterPropTransformEvent event)
    {}

    @Subscribe
    public void onRegisterStencilMap(RegisterStencilMapEvent event)
    {
        this.registerStencilMap(event);
    }

    protected void registerStencilMap(RegisterStencilMapEvent event)
    {}

    @Subscribe
    public void onRegisterRayTracing(RegisterRayTracingEvent event)
    {
        this.registerRayTracing(event);
    }

    protected void registerRayTracing(RegisterRayTracingEvent event)
    {}

    @Subscribe
    public void onRegisterFilmPreview(RegisterFilmPreviewEvent event)
    {
        this.registerFilmPreview(event);
    }

    protected void registerFilmPreview(RegisterFilmPreviewEvent event)
    {}

    @Subscribe
    public void onRegisterReplayListContextMenu(RegisterReplayListContextMenuEvent event)
    {
        this.registerReplayListContextMenu(event);
    }

    protected void registerReplayListContextMenu(RegisterReplayListContextMenuEvent event)
    {}

    @Subscribe
    public void onRegisterReplayPanel(RegisterReplayPanelEvent event)
    {
        this.registerReplayPanel(event);
    }

    protected void registerReplayPanel(RegisterReplayPanelEvent event)
    {}

    @Subscribe
    public void onRegisterUITheme(RegisterUIThemeEvent event)
    {
        this.registerUITheme(event);
    }

    protected void registerUITheme(RegisterUIThemeEvent event)
    {}

    @Subscribe
    public void onRegisterFormEditorSection(RegisterFormEditorSectionEvent event)
    {
        this.registerFormEditorSection(event);
    }

    protected void registerFormEditorSection(RegisterFormEditorSectionEvent event)
    {}

    @Subscribe
    public void onRegisterFormRenderPhase(RegisterFormRenderPhaseEvent event)
    {
        this.registerFormRenderPhase(event);
    }

    protected void registerFormRenderPhase(RegisterFormRenderPhaseEvent event)
    {}

    @Subscribe
    public void onRegisterFormBlend(RegisterFormBlendEvent event)
    {
        this.registerFormBlend(event);
    }

    protected void registerFormBlend(RegisterFormBlendEvent event)
    {}

    @Subscribe
    public void onRegisterClipInteraction(RegisterClipInteractionEvent event)
    {
        this.registerClipInteraction(event);
    }

    protected void registerClipInteraction(RegisterClipInteractionEvent event)
    {}

    @Subscribe
    public void onRegisterDockLayout(RegisterDockLayoutEvent event)
    {
        this.registerDockLayout(event);
    }

    protected void registerDockLayout(RegisterDockLayoutEvent event)
    {}

    @Subscribe
    public void onRegisterParticleSchemeUI(RegisterParticleSchemeUIEvent event)
    {
        this.registerParticleSchemeUI(event);
    }

    protected void registerParticleSchemeUI(RegisterParticleSchemeUIEvent event)
    {}

    @Subscribe
    public void onRegisterFilmControllerInteraction(RegisterFilmControllerInteractionEvent event)
    {
        this.registerFilmControllerInteraction(event);
    }

    protected void registerFilmControllerInteraction(RegisterFilmControllerInteractionEvent event)
    {}

    @Subscribe
    public void onRegisterSettingsUISection(RegisterSettingsUISectionEvent event)
    {
        this.registerSettingsUISection(event);
    }

    protected void registerSettingsUISection(RegisterSettingsUISectionEvent event)
    {}

    @Subscribe
    public void onRegisterFilmSync(RegisterFilmSyncEvent event)
    {
        this.registerFilmSync(event);
    }

    protected void registerFilmSync(RegisterFilmSyncEvent event)
    {}

    @Subscribe
    public void onRegisterReplayLifecycle(RegisterReplayLifecycleEvent event)
    {
        this.registerReplayLifecycle(event);
    }

    protected void registerReplayLifecycle(RegisterReplayLifecycleEvent event)
    {}

    @Subscribe
    public void onRegisterUndo(RegisterUndoEvent event)
    {
        this.registerUndo(event);
    }

    protected void registerUndo(RegisterUndoEvent event)
    {}

    @Subscribe
    public void onRegisterAudioDecoders(RegisterAudioDecodersEvent event)
    {
        this.registerAudioDecoders(event);
    }

    protected void registerAudioDecoders(RegisterAudioDecodersEvent event)
    {}
}
