package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.ImageClip;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueDouble;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.forms.editors.utils.UICropOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import net.minecraft.util.math.MathHelper;

public class UIImageClip extends UIClip<ImageClip>
{
    public UIButton pickTexture;
    public UIToggle linear;
    public UIToggle mipmap;
    public UIButton openCrop;
    public UIToggle resizeCrop;
    public UIColor color;
    public UITrackpad offsetX;
    public UITrackpad offsetY;
    public UITrackpad rotationX;
    public UITrackpad rotationY;
    public UITrackpad rotation;
    public UIButton pickBlendFrom;
    public UIButton pickBlendTo;
    public UITrackpad blend;
    public UITrackpad x;
    public UITrackpad y;
    public UITrackpad width;
    public UIIcon uniformSize;
    public UIStringList blendModeList;
    public UITrackpad height;
    public UIButton resetNativeSize;
    public UITrackpad anchorX;
    public UITrackpad anchorY;
    public UITrackpad windowX;
    public UITrackpad windowY;
    public UITrackpad opacity;
    public UIToggle useKeyframes;
    public UIButton edit;
    public UIKeyframeEditor keyframes;

    private int lastSyncedCursor = Integer.MIN_VALUE;

    public UIImageClip(ImageClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.pickTexture = new UIButton(UIKeys.CAMERA_PANELS_IMAGE_PICK_TEXTURE, (b) ->
        {
            UITexturePicker.open(this.getContext(), this.clip.texture.get(), (l) ->
            {
                this.editor.editMultiple(this.clip.texture, (value) ->
                {
                    value.set(l);
                });

                if (this.clip.uniformSize.get())
                {
                    double width = this.getChannelValue(this.clip.width, this.clip.uniform.width, 100D);

                    this.writeDouble(this.clip.width, this.clip.uniform.width, width);
                    this.writeDouble(this.clip.height, this.clip.uniform.height, this.computeHeightForWidth(width, l));
                }

                this.fillData();
            });
        });

        this.linear = this.createBooleanField(this.clip.linear, this.clip.uniform.linear, UIKeys.TEXTURES_LINEAR);
        this.mipmap = this.createBooleanField(this.clip.mipmap, this.clip.uniform.mipmap, UIKeys.TEXTURES_MIPMAP);

        this.openCrop = new UIButton(UIKeys.FORMS_EDITORS_BILLBOARD_EDIT_CROP, (b) ->
        {
            UIOverlay.addOverlay(this.getContext(), new UICropOverlayPanel(this.clip.texture.get(), this.clip.crop.get()), 0.5F, 0.5F);
        });
        this.resizeCrop = this.createBooleanField(this.clip.resizeCrop, this.clip.uniform.resizeCrop, UIKeys.FORMS_EDITORS_BILLBOARD_RESIZE_CROP);

        this.color = new UIColor((c) ->
        {
            this.writeColor(this.clip.color, this.clip.uniform.color, Color.rgba(c));
            this.fillData();
        }).withAlpha();

        this.offsetX = this.createDoubleTrackpad(this.clip.offsetX, this.clip.uniform.offsetX, UIKeys.CAMERA_PANELS_IMAGE_UV_OFFSET_X, false, null, null);
        this.offsetY = this.createDoubleTrackpad(this.clip.offsetY, this.clip.uniform.offsetY, UIKeys.CAMERA_PANELS_IMAGE_UV_OFFSET_Y, false, null, null);
        this.rotationX = this.createDoubleTrackpad(this.clip.rotationX, this.clip.uniform.rotationX, UIKeys.CAMERA_PANELS_IMAGE_ROTATION_X, false, null, null);
        this.rotationY = this.createDoubleTrackpad(this.clip.rotationY, this.clip.uniform.rotationY, UIKeys.CAMERA_PANELS_IMAGE_ROTATION_Y, false, null, null);
        this.rotation = this.createDoubleTrackpad(this.clip.rotation, this.clip.uniform.rotation, UIKeys.CAMERA_PANELS_IMAGE_ROTATION_Z, false, null, null);

        this.pickBlendFrom = new UIButton(UIKeys.CAMERA_PANELS_IMAGE_BLEND_FROM, (b) ->
        {
            UITexturePicker.open(this.getContext(), this.clip.blendFrom.get(), (l) -> this.editor.editMultiple(this.clip.blendFrom, (value) ->
            {
                value.set(l);
            }));
        });
        this.pickBlendTo = new UIButton(UIKeys.CAMERA_PANELS_IMAGE_BLEND_TO, (b) ->
        {
            UITexturePicker.open(this.getContext(), this.clip.blendTo.get(), (l) -> this.editor.editMultiple(this.clip.blendTo, (value) ->
            {
                value.set(l);
            }));
        });
        this.blend = this.createDoubleTrackpad(this.clip.blend, this.clip.uniform.blend, UIKeys.CAMERA_PANELS_IMAGE_BLEND, false, 0F, 1F);
        this.blend.tooltip(UIKeys.CAMERA_PANELS_IMAGE_BLEND, Direction.BOTTOM);

        this.x = this.createDoubleTrackpad(this.clip.x, this.clip.uniform.x, UIKeys.CAMERA_PANELS_IMAGE_POSITION_X, false, null, null);
        this.y = this.createDoubleTrackpad(this.clip.y, this.clip.uniform.y, UIKeys.CAMERA_PANELS_IMAGE_POSITION_Y, false, null, null);

        this.width = new UITrackpad((v) -> this.setWidth(v.doubleValue()));
        this.width.tooltip(UIKeys.CAMERA_PANELS_IMAGE_WIDTH);

        this.height = new UITrackpad((v) -> this.setHeight(v.doubleValue()));
        this.height.tooltip(UIKeys.CAMERA_PANELS_IMAGE_HEIGHT);

        this.uniformSize = new UIIcon(Icons.LINK, (b) -> this.toggleUniformSize());
        this.uniformSize.tooltip(UIKeys.CAMERA_PANELS_IMAGE_UNIFORM_SIZE);
        this.uniformSize.iconColor(Colors.GRAY).activeColor(Colors.A100 + Colors.ACTIVE);
        this.uniformSize.marginTop(Batcher2D.getDefaultTextRenderer().getHeight() + 5);

        this.resetNativeSize = new UIButton(UIKeys.CAMERA_PANELS_IMAGE_RESET_NATIVE_SIZE, (b) -> this.applyNativeSize());

        this.anchorX = this.createDoubleTrackpad(this.clip.anchorX, this.clip.uniform.anchorX, UIKeys.CAMERA_PANELS_IMAGE_ANCHOR_X, false, null, null);
        this.anchorY = this.createDoubleTrackpad(this.clip.anchorY, this.clip.uniform.anchorY, UIKeys.CAMERA_PANELS_IMAGE_ANCHOR_Y, false, null, null);

        this.windowX = this.createDoubleTrackpad(this.clip.windowX, this.clip.uniform.windowX, UIKeys.CAMERA_PANELS_IMAGE_WINDOW_X, false, null, null);
        this.windowY = this.createDoubleTrackpad(this.clip.windowY, this.clip.uniform.windowY, UIKeys.CAMERA_PANELS_IMAGE_WINDOW_Y, false, null, null);

        this.opacity = new UITrackpad((v) ->
        {
            this.writeDouble(this.clip.opacity, this.clip.uniform.opacity, v.doubleValue() / 100D);
            this.fillData();
        });
        this.opacity.limit(0, 100);
        this.opacity.tooltip(UIKeys.CAMERA_PANELS_IMAGE_OPACITY);
        this.blendModeList = new UIStringList((items) ->
        {
            if (!items.isEmpty())
            {
                int index = this.blendModeList.getIndex();
                this.editor.editMultiple(this.clip.blendMode, (value) ->
                {
                    value.set(index);
                });
            }
        });
        this.blendModeList.background();
        this.blendModeList.add(UIKeys.CAMERA_PANELS_IMAGE_BLEND_MODE_NORMAL.get());
        this.blendModeList.add(UIKeys.CAMERA_PANELS_IMAGE_BLEND_MODE_MULTIPLY.get());
        this.blendModeList.add(UIKeys.CAMERA_PANELS_IMAGE_BLEND_MODE_SCREEN.get());
        this.blendModeList.add(UIKeys.CAMERA_PANELS_IMAGE_BLEND_MODE_ADD.get());
        this.blendModeList.add(UIKeys.CAMERA_PANELS_IMAGE_BLEND_MODE_SATURATION.get());
        this.blendModeList.add(UIKeys.CAMERA_PANELS_IMAGE_BLEND_MODE_INCRUSTATION.get());
        this.blendModeList.add(UIKeys.CAMERA_PANELS_IMAGE_BLEND_MODE_EXCLUSION.get());
        this.blendModeList.add(UIKeys.CAMERA_PANELS_IMAGE_BLEND_MODE_OVERLAY.get());
        this.blendModeList.add(UIKeys.CAMERA_PANELS_IMAGE_BLEND_MODE_COLOR_DODGE.get());
        this.blendModeList.tooltip(UIKeys.CAMERA_PANELS_IMAGE_OPACITY_STYLE);

        this.useKeyframes = new UIToggle(UIKeys.SCREEN_PANELS_USE_KEYFRAMES, (b) ->
        {
            boolean enabled = b.getValue();
            float tick = this.getClipTick();

            this.clip.useKeyframes.set(enabled);

            if (enabled)
            {
                this.clip.ensureChannelsSeeded(tick);
                this.keyframes.setChannels(this.clip.channels);
            }
            else
            {
                this.clip.ensureUniformSeeded(tick);

                if (this.keyframes.hasParent())
                {
                    this.editor.embedView(null);
                }
            }

            this.updateKeyframesControls();
            this.fillData();
        });
        this.useKeyframes.tooltip(UIKeys.SCREEN_PANELS_USE_KEYFRAMES_TOOLTIP);

        this.keyframes = this.createKeyframeEditor("image_keyframes");

        this.edit = new UIButton(UIKeys.GENERAL_EDIT, (b) ->
        {
            if (!this.clip.useKeyframes.get())
            {
                return;
            }

            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
            this.keyframes.view.getGraph().clearSelection();
        });
        this.edit.keys().register(Keys.FORMS_EDIT, () -> this.edit.clickItself());
    }

    private UITrackpad createDoubleTrackpad(KeyframeChannel<Double> channel, ValueDouble uniform, IKey tooltip, boolean integer, Float min, Float max)
    {
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            this.writeDouble(channel, uniform, v.doubleValue());
            this.fillData();
        });

        if (integer)
        {
            trackpad.integer();
        }

        if (min != null && max != null)
        {
            trackpad.limit(min, max);
        }

        if (tooltip != null)
        {
            trackpad.tooltip(tooltip);
        }

        return trackpad;
    }

    private UIToggle createBooleanField(KeyframeChannel<Boolean> channel, ValueBoolean uniform, IKey label)
    {
        return new UIToggle(label, (b) ->
        {
            this.writeBoolean(channel, uniform, b.getValue());
            this.fillData();
        });
    }

    private void writeDouble(KeyframeChannel<Double> channel, ValueDouble uniform, double value)
    {
        if (channel == this.clip.blend)
        {
            value = MathHelper.clamp(value, ImageClip.BLEND_MIN, ImageClip.BLEND_MAX);
        }
        else if (channel == this.clip.opacity)
        {
            value = MathHelper.clamp(value, ImageClip.OPACITY_MIN, ImageClip.OPACITY_MAX);
        }

        if (this.clip.useKeyframes.get())
        {
            channel.insert(this.getClipTick(), value);
        }
        else
        {
            this.clip.uniformSeeded.set(true);
            uniform.set(value);
        }
    }

    private void writeBoolean(KeyframeChannel<Boolean> channel, ValueBoolean uniform, boolean value)
    {
        if (this.clip.useKeyframes.get())
        {
            channel.insert(this.getClipTick(), value);
        }
        else
        {
            this.clip.uniformSeeded.set(true);
            uniform.set(value);
        }
    }

    private void writeColor(KeyframeChannel<Color> channel, ValueColor uniform, Color value)
    {
        if (this.clip.useKeyframes.get())
        {
            channel.insert(this.getClipTick(), value);
        }
        else
        {
            this.clip.uniformSeeded.set(true);
            uniform.set(value);
        }
    }

    private void updateKeyframesControls()
    {
        this.edit.setEnabled(this.clip.useKeyframes.get());
    }

    private UIKeyframeEditor createKeyframeEditor(String undoId)
    {
        UIKeyframeEditor editor = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));

        editor.view.backgroundRenderer((context) ->
        {
            UIReplaysEditor.renderBackground(context, editor.view, (Clips) this.clip.getParent(), this.clip.tick.get(), this.clip);
        });
        editor.view.duration(() -> this.clip.duration.get());
        editor.view.changed(() -> this.fillData());
        editor.setUndoId(undoId);

        return editor;
    }

    private int getClipTick()
    {
        return MathHelper.clamp(this.editor.getCursor() - this.clip.tick.get(), 0, this.clip.duration.get());
    }

    /**
     * Keep property inputs in sync if the film cursor moves without going through
     * the normal scrub refresh path (e.g. after a prior fillData failure).
     */
    @Override
    public void render(UIContext context)
    {
        int cursor = this.editor.getCursor();

        if (cursor != this.lastSyncedCursor)
        {
            this.fillData();
        }

        super.render(context);
    }

    private void applySheetLimits()
    {
        for (UIKeyframeSheet sheet : this.keyframes.view.getGraph().getSheets())
        {
            if ("blend".equals(sheet.id) || "opacity".equals(sheet.id))
            {
                sheet.limit(0D, 1D);
            }
            else
            {
                /* Clear any stale bounds so percent-sized tracks (width/height ≈ 100)
                 * are not left clamped from another clip's sheet reuse path. */
                sheet.limit(null, null);
            }
        }
    }

    private void toggleUniformSize()
    {
        boolean enabling = !this.clip.uniformSize.get();

        this.editor.editMultiple(this.clip.uniformSize, (value) ->
        {
            value.set(enabling);
        });

        if (enabling)
        {
            double width = this.getChannelValue(this.clip.width, this.clip.uniform.width, 100D);

            this.writeDouble(this.clip.width, this.clip.uniform.width, width);
            this.writeDouble(this.clip.height, this.clip.uniform.height, this.computeHeightForWidth(width));
        }

        this.fillData();
    }

    private void setWidth(double width)
    {
        this.writeDouble(this.clip.width, this.clip.uniform.width, width);

        if (this.clip.uniformSize.get())
        {
            this.writeDouble(this.clip.height, this.clip.uniform.height, this.computeHeightForWidth(width));
        }

        this.fillData();
    }

    private void setHeight(double height)
    {
        this.writeDouble(this.clip.height, this.clip.uniform.height, height);

        if (this.clip.uniformSize.get())
        {
            this.writeDouble(this.clip.width, this.clip.uniform.width, this.computeWidthForHeight(height));
        }

        this.fillData();
    }

    private void applyNativeSize()
    {
        double width = 100D;
        double height = this.computeHeightForWidth(width);

        this.writeDouble(this.clip.width, this.clip.uniform.width, width);
        this.writeDouble(this.clip.height, this.clip.uniform.height, height);
        this.fillData();
    }

    private double computeHeightForWidth(double widthPercent)
    {
        return this.computeHeightForWidth(widthPercent, this.clip.texture.get());
    }

    private double computeHeightForWidth(double widthPercent, Link link)
    {
        int[] dimensions = this.getTextureDimensions(link);
        int screenW = BBSRendering.getVideoWidth();
        int screenH = BBSRendering.getVideoHeight();

        if (dimensions == null || dimensions[0] <= 0 || dimensions[1] <= 0 || screenW <= 0 || screenH <= 0)
        {
            return widthPercent;
        }

        return widthPercent * screenW * dimensions[1] / ((double) dimensions[0] * screenH);
    }

    private double computeWidthForHeight(double heightPercent)
    {
        return this.computeWidthForHeight(heightPercent, this.clip.texture.get());
    }

    private double computeWidthForHeight(double heightPercent, Link link)
    {
        int[] dimensions = this.getTextureDimensions(link);
        int screenW = BBSRendering.getVideoWidth();
        int screenH = BBSRendering.getVideoHeight();

        if (dimensions == null || dimensions[0] <= 0 || dimensions[1] <= 0 || screenW <= 0 || screenH <= 0)
        {
            return heightPercent;
        }

        return heightPercent * dimensions[0] * screenH / ((double) dimensions[1] * screenW);
    }

    private int[] getTextureDimensions()
    {
        return this.getTextureDimensions(this.clip.texture.get());
    }

    private int[] getTextureDimensions(Link link)
    {
        if (link == null)
        {
            return null;
        }

        Texture texture = BBSModClient.getTextures().getTexture(link);

        if (texture == null || texture.width <= 0 || texture.height <= 0)
        {
            return null;
        }

        return new int[] {texture.width, texture.height};
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_IMAGE_TEXTURE, this.pickTexture, this.linear, this.mipmap, this.color));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_IMAGE_CROP, this.openCrop, this.resizeCrop));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_IMAGE_UV_SHIFT, UI.row(this.offsetX, this.offsetY)));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_IMAGE_ROTATION, UI.row(this.rotationX, this.rotationY, this.rotation)));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_IMAGE_BLEND, UI.row(this.pickBlendFrom, this.pickBlendTo), this.blend));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_IMAGE_OFFSET, UI.row(this.x, this.y)));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_IMAGE_SIZE, UI.row(this.width, this.uniformSize, this.height), this.resetNativeSize));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_IMAGE_ANCHOR, UI.row(this.anchorX, this.anchorY)));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_IMAGE_WINDOW, UI.row(this.windowX, this.windowY)));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_IMAGE_OPACITY, this.opacity));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_IMAGE_OPACITY_STYLE, this.blendModeList.h(128)));
        this.panels.add(this.section(UIKeys.SCREEN_PANELS_KEYFRAMES, this.useKeyframes, this.edit));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.linear.setValue(this.getBooleanValue(this.clip.linear, this.clip.uniform.linear, false));
        this.mipmap.setValue(this.getBooleanValue(this.clip.mipmap, this.clip.uniform.mipmap, false));
        this.resizeCrop.setValue(this.getBooleanValue(this.clip.resizeCrop, this.clip.uniform.resizeCrop, false));
        this.color.setColor(this.getColorValue(this.clip.color, this.clip.uniform.color, Color.white()).getARGBColor());
        this.offsetX.setValue(this.getChannelValue(this.clip.offsetX, this.clip.uniform.offsetX, 0D));
        this.offsetY.setValue(this.getChannelValue(this.clip.offsetY, this.clip.uniform.offsetY, 0D));
        this.rotationX.setValue(this.getChannelValue(this.clip.rotationX, this.clip.uniform.rotationX, 0D));
        this.rotationY.setValue(this.getChannelValue(this.clip.rotationY, this.clip.uniform.rotationY, 0D));
        this.rotation.setValue(this.getChannelValue(this.clip.rotation, this.clip.uniform.rotation, 0D));
        this.blend.setValue(this.getChannelValue(this.clip.blend, this.clip.uniform.blend, 0D));
        this.x.setValue(this.getChannelValue(this.clip.x, this.clip.uniform.x, 0D));
        this.y.setValue(this.getChannelValue(this.clip.y, this.clip.uniform.y, 0D));
        this.width.setValue(this.getChannelValue(this.clip.width, this.clip.uniform.width, 100D));
        this.height.setValue(this.getChannelValue(this.clip.height, this.clip.uniform.height, 100D));
        this.anchorX.setValue(this.getChannelValue(this.clip.anchorX, this.clip.uniform.anchorX, 0.5D));
        this.anchorY.setValue(this.getChannelValue(this.clip.anchorY, this.clip.uniform.anchorY, 0.5D));
        this.windowX.setValue(this.getChannelValue(this.clip.windowX, this.clip.uniform.windowX, 0.5D));
        this.windowY.setValue(this.getChannelValue(this.clip.windowY, this.clip.uniform.windowY, 0.5D));
        this.opacity.setValue(this.getChannelValue(this.clip.opacity, this.clip.uniform.opacity, 1D) * 100F);
        this.blendModeList.setIndex(this.clip.blendMode.get());
        this.uniformSize.active(this.clip.uniformSize.get());
        this.useKeyframes.setValue(this.clip.useKeyframes.get());
        this.updateKeyframesControls();

        /* Avoid rebuilding keyframe sheets on every cursor scrub — only when empty
         * or when channels were extended (e.g. color track added). */
        if (this.keyframes.view.getGraph().getSheets().isEmpty()
            || this.keyframes.view.getGraph().getSheets().size() != this.clip.channels.length)
        {
            this.keyframes.setChannels(this.clip.channels);
        }

        this.applySheetLimits();
        this.updateTrackTitles();
        this.lastSyncedCursor = this.editor.getCursor();
    }

    private double getChannelValue(KeyframeChannel<Double> channel, ValueDouble uniform, double fallback)
    {
        if (!this.clip.useKeyframes.get())
        {
            return uniform.get();
        }

        if (channel.isEmpty())
        {
            return this.clip.uniformSeeded.get() ? uniform.get() : fallback;
        }

        return this.readDouble(channel, this.getClipTick(), fallback);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private double readDouble(KeyframeChannel<Double> channel, float tick, double fallback)
    {
        Object value = ((KeyframeChannel) channel).interpolate(tick);

        if (value instanceof Number)
        {
            return ((Number) value).doubleValue();
        }

        return fallback;
    }

    private boolean getBooleanValue(KeyframeChannel<Boolean> channel, ValueBoolean uniform, boolean fallback)
    {
        if (!this.clip.useKeyframes.get())
        {
            return uniform.get();
        }

        if (channel.isEmpty())
        {
            return this.clip.uniformSeeded.get() ? uniform.get() : fallback;
        }

        return channel.interpolate(this.getClipTick(), fallback);
    }

    private Color getColorValue(KeyframeChannel<Color> channel, ValueColor uniform, Color fallback)
    {
        if (!this.clip.useKeyframes.get())
        {
            return uniform.get();
        }

        if (channel.isEmpty())
        {
            return this.clip.uniformSeeded.get() ? uniform.get() : fallback;
        }

        return channel.interpolate(this.getClipTick(), fallback);
    }

    private void updateTrackTitles()
    {
        for (UIKeyframeSheet sheet : this.keyframes.view.getGraph().getSheets())
        {
            sheet.title = this.getTrackTitle(sheet.id);
        }
    }

    private IKey getTrackTitle(String id)
    {
        return switch (id)
        {
            case "texture_track" -> UIKeys.CAMERA_PANELS_IMAGE_TEXTURE;
            case "linear" -> UIKeys.TEXTURES_LINEAR;
            case "mipmap" -> UIKeys.TEXTURES_MIPMAP;
            case "resizeCrop" -> UIKeys.FORMS_EDITORS_BILLBOARD_RESIZE_CROP;
            case "offsetX" -> UIKeys.CAMERA_PANELS_IMAGE_UV_OFFSET_X;
            case "offsetY" -> UIKeys.CAMERA_PANELS_IMAGE_UV_OFFSET_Y;
            case "rotationX" -> UIKeys.CAMERA_PANELS_IMAGE_ROTATION_X;
            case "rotationY" -> UIKeys.CAMERA_PANELS_IMAGE_ROTATION_Y;
            case "rotation" -> UIKeys.CAMERA_PANELS_IMAGE_ROTATION_Z;
            case "x" -> UIKeys.CAMERA_PANELS_IMAGE_POSITION_X;
            case "y" -> UIKeys.CAMERA_PANELS_IMAGE_POSITION_Y;
            case "width" -> UIKeys.CAMERA_PANELS_IMAGE_WIDTH;
            case "height" -> UIKeys.CAMERA_PANELS_IMAGE_HEIGHT;
            case "anchorX" -> UIKeys.CAMERA_PANELS_IMAGE_ANCHOR_X;
            case "anchorY" -> UIKeys.CAMERA_PANELS_IMAGE_ANCHOR_Y;
            case "windowX" -> UIKeys.CAMERA_PANELS_IMAGE_WINDOW_X;
            case "windowY" -> UIKeys.CAMERA_PANELS_IMAGE_WINDOW_Y;
            case "opacity" -> UIKeys.CAMERA_PANELS_IMAGE_OPACITY;
            case "color" -> UIKeys.CAMERA_PANELS_IMAGE_COLOR;
            case "blend" -> UIKeys.CAMERA_PANELS_IMAGE_BLEND;
            default -> IKey.constant(id);
        };
    }

    @Override
    protected UIKeyframeEditor resolveClipEmbeddableView(String undoId)
    {
        return undoId.equals(this.keyframes.getUndoId()) ? this.keyframes : null;
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        if (data.getString("embed").equals("image_keyframes") && this.clip.useKeyframes.get())
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
        }
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        if (this.keyframes.hasParent())
        {
            data.putString("embed", "image_keyframes");
        }
    }
}
