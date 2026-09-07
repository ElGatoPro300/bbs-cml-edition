package mchorse.bbs_mod.addons;

import mchorse.bbs_mod.events.BBSAddonMod;
import mchorse.bbs_mod.events.Subscribe;
import mchorse.bbs_mod.events.register.RegisterActionClipsEvent;
import mchorse.bbs_mod.events.register.RegisterActionConfigsEvent;
import mchorse.bbs_mod.events.register.RegisterAudioDecodersEvent;
import mchorse.bbs_mod.events.register.RegisterBBSSettingsEvent;
import mchorse.bbs_mod.events.register.RegisterCameraClipsEvent;
import mchorse.bbs_mod.events.register.RegisterEntityCaptureHandlersEvent;
import mchorse.bbs_mod.events.register.RegisterFormChannelsEvent;
import mchorse.bbs_mod.events.register.RegisterFormsEvent;
import mchorse.bbs_mod.events.register.RegisterKeyframeFactoriesEvent;
import mchorse.bbs_mod.events.register.RegisterMolangFunctionsEvent;
import mchorse.bbs_mod.events.register.RegisterParticleSimulationsEvent;
import mchorse.bbs_mod.events.register.RegisterReplayLifecycleEvent;
import mchorse.bbs_mod.events.register.RegisterSettingsEvent;
import mchorse.bbs_mod.events.register.RegisterSourcePacksEvent;
import mchorse.bbs_mod.events.register.RegisterUndoEvent;

/**
 * Base class for BBS addons.
 *
 * <p>Extend this class to create a BBS addon. This class provides convenient methods
 * to register content to the mod.</p>
 */
public abstract class BBSAddon implements BBSAddonMod
{
    @Subscribe
    public void onRegisterForms(RegisterFormsEvent event)
    {
        this.registerForms(event);
    }

    @Subscribe
    public void onRegisterCameraClips(RegisterCameraClipsEvent event)
    {
        this.registerCameraClips(event);
    }

    @Subscribe
    public void onRegisterActionClips(RegisterActionClipsEvent event)
    {
        this.registerActionClips(event);
    }

    @Subscribe
    public void onRegisterSettings(RegisterSettingsEvent event)
    {
        this.registerSettings(event);
    }

    @Subscribe
    public void onRegisterSourcePacks(RegisterSourcePacksEvent event)
    {
        this.registerSourcePacks(event);
    }

    @Subscribe
    public void onRegisterBBSSettings(RegisterBBSSettingsEvent event)
    {
        this.registerBBSSettings(event);
    }

    protected void registerForms(RegisterFormsEvent event)
    {}

    protected void registerCameraClips(RegisterCameraClipsEvent event)
    {}

    protected void registerActionClips(RegisterActionClipsEvent event)
    {}

    @Subscribe
    public void onRegisterEntityCaptureHandlers(RegisterEntityCaptureHandlersEvent event)
    {
        this.registerEntityCaptureHandlers(event);
    }

    protected void registerEntityCaptureHandlers(RegisterEntityCaptureHandlersEvent event)
    {}

    protected void registerSettings(RegisterSettingsEvent event)
    {}

    protected void registerSourcePacks(RegisterSourcePacksEvent event)
    {}

    protected void registerBBSSettings(RegisterBBSSettingsEvent event)
    {}

    @Subscribe
    public void onRegisterKeyframeFactories(RegisterKeyframeFactoriesEvent event)
    {
        this.registerKeyframeFactories(event);
    }

    protected void registerKeyframeFactories(RegisterKeyframeFactoriesEvent event)
    {}

    @Subscribe
    public void onRegisterMolangFunctions(RegisterMolangFunctionsEvent event)
    {
        this.registerMolangFunctions(event);
    }

    protected void registerMolangFunctions(RegisterMolangFunctionsEvent event)
    {}

    @Subscribe
    public void onRegisterActionConfigs(RegisterActionConfigsEvent event)
    {
        this.registerActionConfigs(event);
    }

    protected void registerActionConfigs(RegisterActionConfigsEvent event)
    {}

    @Subscribe
    public void onRegisterParticleSimulations(RegisterParticleSimulationsEvent event)
    {
        this.registerParticleSimulations(event);
    }

    protected void registerParticleSimulations(RegisterParticleSimulationsEvent event)
    {}

    @Subscribe
    public void onRegisterReplayLifecycle(RegisterReplayLifecycleEvent event)
    {
        this.registerReplayLifecycle(event);
    }

    protected void registerReplayLifecycle(RegisterReplayLifecycleEvent event)
    {}

    @Subscribe
    public void onRegisterFormChannels(RegisterFormChannelsEvent event)
    {
        this.registerFormChannels(event);
    }

    protected void registerFormChannels(RegisterFormChannelsEvent event)
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
