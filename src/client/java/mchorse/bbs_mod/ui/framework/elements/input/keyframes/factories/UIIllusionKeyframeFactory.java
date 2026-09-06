package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.forms.forms.utils.Illusion;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIFileLinkList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class UIIllusionKeyframeFactory extends UIKeyframeFactory<Illusion>
{
    private UITrackpad count;
    private UITrackpad spread;
    private UIToggle front;
    private UIToggle back;
    private UIToggle left;
    private UIToggle right;
    private UIToggle up;
    private UIToggle down;
    private UIToggle uniform;
    private UITrackpad spacing;
    private UITrackpad offset;
    private UITrackpad opacity;
    private UIToggle opacityUniform;
    private UIToggle invert;
    private UIToggle gradual;
    private UIToggle gradualInvert;
    private UIPropTransform illusionTransform;
    private UIButton textures;
    private UIButton clearTextures;
    private UIToggle textureBend;
    private UIToggle randomTextures;
    private UIToggle real;
    private UITrackpad delay;
    private UITrackpad distort;
    private UIToggle distortUniform;
    private UIToggle distortInvert;
    private UITrackpad glow;
    private UIToggle glowUniform;
    private UIToggle glowInvert;
    private UIToggle distributeParticles;
    private UIToggle independentParticles;

    private UIElement opacityLabel;
    private UIElement opacityFlagsRow;
    private UIElement distortLabel;
    private UIElement distortFlagsRow;
    private UIElement delayLabel;
    private UIElement glowLabel;
    private UIElement glowFlagsRow;



    /**
     * Opens the texture picker with multi-selection support. A plain click picks a
     * single texture, Shift + click appends more textures (range when possible),
     * and Ctrl + click toggles a texture in the current selection.
     */
    public static void pickTextures(UIContext context, Supplier<List<Link>> getter, Consumer<List<Link>> setter)
    {
        List<Link> textures = new ArrayList<>(getter.get());
        Link initial = textures.isEmpty() ? null : textures.get(textures.size() - 1);
        Link[] lastNotified = {null};
        UITexturePicker[] ui = new UITexturePicker[1];

        ui[0] = UITexturePicker.open(context, initial, (link) ->
        {
            if (link == null)
            {
                return;
            }

            if (ui[0] != null && ui[0].notifyingCallbackClose)
            {
                return;
            }

            if (Window.isShiftPressed())
            {
                List<Link> range = getTextureRange(ui[0], textures.isEmpty() ? null : textures.get(textures.size() - 1), link);

                if (range != null)
                {
                    textures.addAll(range);
                }
                else
                {
                    textures.add(link);
                }
            }
            else if (Window.isCtrlPressed())
            {
                int index = indexOfTexture(textures, link);

                if (index >= 0)
                {
                    textures.remove(index);
                }
                else
                {
                    textures.add(link);
                }
            }
            else
            {
                /* The picker re-sends the current texture when it gets closed */
                if (link.equals(lastNotified[0]))
                {
                    return;
                }

                textures.clear();
                textures.add(link);
            }

            lastNotified[0] = link;
            setter.accept(new ArrayList<>(textures));
        });

        if (ui[0] != null)
        {
            ui[0].disableMultiSkin();

            /* Highlight all the picked textures in the file list (blue selection tint) */
            ui[0].highlighted(() -> textures);
        }
    }

    private static int indexOfTexture(List<Link> textures, Link link)
    {
        for (int i = 0; i < textures.size(); i++)
        {
            if (textures.get(i).equals(link))
            {
                return i;
            }
        }

        return -1;
    }



    /**

     * Textures between the last picked one and the newly Shift + clicked one (in

     * click direction, excluding the anchor, including the clicked one), taken from

     * the picker's current folder listing.

     */

    private static List<Link> getTextureRange(UITexturePicker ui, Link from, Link to)

    {

        if (ui == null || from == null || from.equals(to))

        {

            return null;

        }



        List<UIFileLinkList.FileLink> files = ui.picker.getList();

        int fromIndex = -1;

        int toIndex = -1;



        for (int i = 0; i < files.size(); i++)

        {

            UIFileLinkList.FileLink file = files.get(i);



            if (file.folder)

            {

                continue;

            }



            if (file.link.equals(from)) fromIndex = i;

            if (file.link.equals(to)) toIndex = i;

        }



        if (fromIndex < 0 || toIndex < 0)

        {

            return null;

        }



        List<Link> range = new ArrayList<>();

        int step = fromIndex < toIndex ? 1 : -1;



        for (int i = fromIndex + step; i != toIndex + step; i += step)

        {

            UIFileLinkList.FileLink file = files.get(i);



            if (!file.folder)

            {

                range.add(file.link);

            }

        }



        return range.isEmpty() ? null : range;

    }



    public static void applyIllusion(UIKeyframes editor, Keyframe keyframe, Consumer<Illusion> consumer)

    {

        boolean[] applied = {false};



        UIReplaysEditorUtils.forEachSelectedKeyframe(editor, keyframe, (selected) ->

        {

            applied[0] = true;



            if (selected.getValue() instanceof Illusion illusion)

            {

                Illusion copy = illusion.copy();

                consumer.accept(copy);

                selected.setValue(copy, true);

            }

        });



        if (!applied[0] && keyframe != null && keyframe.getValue() instanceof Illusion illusion)

        {

            Illusion copy = illusion.copy();

            consumer.accept(copy);

            keyframe.setValue(copy, true);

        }

    }



    public UIIllusionKeyframeFactory(Keyframe<Illusion> keyframe, UIKeyframes editor)

    {

        super(keyframe, editor);



        Illusion illusion = keyframe.getValue();



        this.count = new UITrackpad((v) -> this.editIllusion((i) -> i.count = v.intValue()));

        this.count.limit(0D).integer().setValue(illusion.count);

        this.spread = new UITrackpad((v) -> this.editIllusion((i) -> i.spread = v.floatValue()));

        this.spread.setValue(illusion.spread);

        this.spread.tooltip(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_SPREAD_TOOLTIP);



        this.front = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_FRONT, (b) -> this.toggleDirection(Illusion.FRONT, b.getValue()));

        this.back = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_BACK, (b) -> this.toggleDirection(Illusion.BACK, b.getValue()));

        this.left = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_LEFT, (b) -> this.toggleDirection(Illusion.LEFT, b.getValue()));

        this.right = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_RIGHT, (b) -> this.toggleDirection(Illusion.RIGHT, b.getValue()));

        this.up = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_UP, (b) -> this.toggleDirection(Illusion.UP, b.getValue()));

        this.down = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_DOWN, (b) -> this.toggleDirection(Illusion.DOWN, b.getValue()));



        this.uniform = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_UNIFORM, (b) -> this.editIllusion((i) -> i.uniform = b.getValue()));

        this.uniform.setValue(illusion.uniform);

        this.uniform.tooltip(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_UNIFORM_TOOLTIP);

        this.spacing = new UITrackpad((v) -> this.editIllusion((i) -> i.spacing = v.floatValue()));

        this.spacing.setValue(illusion.spacing);

        this.spacing.tooltip(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_SPACING_TOOLTIP);



        this.offset = new UITrackpad((v) -> this.editIllusion((i) -> i.offset = v.floatValue()));

        this.offset.setValue(illusion.offset);

        this.offset.tooltip(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_OFFSET_TOOLTIP);

        this.opacity = new UITrackpad((v) -> this.editIllusion((i) -> i.opacity = v.floatValue() / 100F));

        this.opacity.limit(0D).setValue(illusion.opacity * 100F);

        this.opacity.tooltip(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_OPACITY_TOOLTIP);

        this.opacityUniform = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_OPACITY_UNIFORM, (b) -> this.editIllusion((i) -> i.opacityUniform = b.getValue()));

        this.opacityUniform.setValue(illusion.opacityUniform);

        this.opacityUniform.tooltip(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_OPACITY_UNIFORM_TOOLTIP);

        this.invert = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_INVERT, (b) -> this.editIllusion((i) -> i.invert = b.getValue()));

        this.invert.setValue(illusion.invert);

        this.gradual = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_GRADUAL, (b) -> this.editIllusion((i) -> i.gradual = b.getValue()));

        this.gradual.setValue(illusion.gradual);

        this.gradual.tooltip(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_GRADUAL_TOOLTIP);

        this.gradualInvert = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_GRADUAL_INVERT, (b) -> this.editIllusion((i) -> i.gradualInvert = b.getValue()));

        this.gradualInvert.setValue(illusion.gradualInvert);

        this.gradualInvert.tooltip(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_GRADUAL_INVERT_TOOLTIP);

        this.illusionTransform = new UIPropTransform();

        this.illusionTransform.setTransform(illusion.transform);

        this.illusionTransform.callbacks(() -> this.keyframe.preNotify(), () ->

        {

            Transform current = this.illusionTransform.getTransform();

            this.editIllusion((i) -> i.transform.copy(current));

            this.keyframe.postNotify();

        });



        this.textures = new UIButton(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_TEXTURES, (b) ->

        {

            pickTextures(this.getContext(), () -> this.keyframe.getValue().textures, (list) -> this.editIllusion((i) ->

            {

                i.textures.clear();

                i.textures.addAll(list);

            }));

        });

        this.textures.tooltip(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_TEXTURES_TOOLTIP);

        this.clearTextures = new UIButton(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_TEXTURES_CLEAR, (b) -> this.editIllusion((i) -> i.textures.clear()));

        this.textureBend = new UIToggle(UIKeys.GENERIC_KEYFRAMES_LINK_BEND, (b) ->

        {

            for (UIKeyframeSheet sheet : this.editor.getGraph().getSheets())

            {

                for (Keyframe kf : sheet.selection.getSelected())

                {

                    kf.setBend(b.getValue());

                }

            }

        });

        this.textureBend.setValue(this.keyframe.isBend());

        this.textureBend.tooltip(UIKeys.GENERIC_KEYFRAMES_LINK_BEND_TOOLTIP);

        this.randomTextures = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_TEXTURES_RANDOM, (b) -> this.editIllusion((i) -> i.randomTextures = b.getValue()));

        this.randomTextures.setValue(illusion.randomTextures);

        this.randomTextures.tooltip(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_TEXTURES_RANDOM_TOOLTIP);



        this.real = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_REAL, (b) -> this.editIllusion((i) -> i.real = b.getValue()));

        this.real.setValue(illusion.real);

        this.real.tooltip(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_REAL_TOOLTIP);



        this.delay = new UITrackpad((v) -> this.editIllusion((i) -> i.delay = v.floatValue()));

        this.delay.limit(0D).setValue(illusion.delay);

        this.delay.tooltip(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_DELAY_TOOLTIP);



        this.distort = new UITrackpad((v) -> this.editIllusion((i) -> i.distort = v.floatValue()));

        this.distort.limit(0D).setValue(illusion.distort);

        this.distort.tooltip(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_DISTORT_TOOLTIP);

        this.distortUniform = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_DISTORT_UNIFORM, (b) -> this.editIllusion((i) -> i.distortUniform = b.getValue()));

        this.distortUniform.setValue(illusion.distortUniform);

        this.distortUniform.tooltip(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_DISTORT_UNIFORM_TOOLTIP);

        this.distortInvert = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_DISTORT_INVERT, (b) -> this.editIllusion((i) -> i.distortInvert = b.getValue()));

        this.distortInvert.setValue(illusion.distortInvert);

        this.distortInvert.tooltip(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_DISTORT_INVERT_TOOLTIP);



        this.glow = new UITrackpad((v) -> this.editIllusion((i) -> i.glow = v.floatValue()));

        this.glow.setValue(illusion.glow);

        this.glow.tooltip(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_GLOW_TOOLTIP);

        this.glowUniform = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_GLOW_UNIFORM, (b) -> this.editIllusion((i) -> i.glowUniform = b.getValue()));

        this.glowUniform.setValue(illusion.glowUniform);

        this.glowUniform.tooltip(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_GLOW_UNIFORM_TOOLTIP);

        this.glowInvert = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_GLOW_INVERT, (b) -> this.editIllusion((i) -> i.glowInvert = b.getValue()));

        this.glowInvert.setValue(illusion.glowInvert);

        this.glowInvert.tooltip(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_GLOW_INVERT_TOOLTIP);

        this.distributeParticles = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_DISTRIBUTE_PARTICLES, (b) -> this.editIllusion((i) -> i.distributeParticles = b.getValue()));

        this.distributeParticles.setValue(illusion.distributeParticles);

        this.distributeParticles.tooltip(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_DISTRIBUTE_PARTICLES_TOOLTIP);

        this.independentParticles = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_INDEPENDENT_PARTICLES, (b) -> this.editIllusion((i) -> i.independentParticles = b.getValue()));

        this.independentParticles.setValue(illusion.independentParticles);

        this.independentParticles.tooltip(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_INDEPENDENT_PARTICLES_TOOLTIP);



        this.opacityLabel = UI.label(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_OPACITY);
        this.opacityFlagsRow = UI.row(this.opacityUniform, this.invert);
        this.distortLabel = UI.label(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_DISTORT);
        this.distortFlagsRow = UI.row(this.distortUniform, this.distortInvert);
        this.delayLabel = UI.label(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_DELAY);
        this.glowLabel = UI.label(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_GLOW);
        this.glowFlagsRow = UI.row(this.glowUniform, this.glowInvert);

        this.updateDirections(illusion.directions);

        this.scroll.add(UI.label(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_COUNT), this.count);
        this.scroll.add(UI.label(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_SPREAD), this.spread);
        this.scroll.add(UI.row(this.front, this.back));
        this.scroll.add(UI.row(this.left, this.right));
        this.scroll.add(UI.row(this.up, this.down));
        this.scroll.add(this.uniform);
        this.scroll.add(this.spacing);
        this.scroll.add(UI.label(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_OFFSET), this.offset);
        this.scroll.add(this.independentParticles);
        this.scroll.add(this.distributeParticles);
        this.scroll.add(this.opacityLabel, this.opacity);
        this.scroll.add(this.opacityFlagsRow);
        this.scroll.add(UI.label(UIKeys.FORMS_EDITORS_GENERAL_ILLUSION_TRANSFORM));
        this.scroll.add(this.illusionTransform);
        this.scroll.add(UI.row(this.gradual, this.gradualInvert));
        this.scroll.add(this.distortLabel, this.distort);
        this.scroll.add(this.distortFlagsRow);
        this.scroll.add(this.delayLabel, this.delay);
        this.scroll.add(this.glowLabel, this.glow);
        this.scroll.add(this.glowFlagsRow);
        this.scroll.add(this.textures, this.clearTextures, this.textureBend, this.randomTextures);
        this.scroll.add(this.real);

        this.context((menu) -> menu.action(Icons.CLOSE, UIKeys.TRANSFORMS_CONTEXT_RESET, this::resetIllusion));

        this.updateIllusionOptions(this.getEditingForm());
    }

    private void editIllusion(Consumer<Illusion> consumer)
    {
        applyIllusion(this.editor, this.keyframe, consumer);
    }

    private void resetIllusion()
    {
        applyIllusion(this.editor, this.keyframe, (illusion) ->
        {
            Illusion defaults = new Illusion();

            illusion.count = defaults.count;
            illusion.spread = defaults.spread;
            illusion.directions = defaults.directions;
            illusion.offset = defaults.offset;
            illusion.opacity = defaults.opacity;
            illusion.opacityUniform = defaults.opacityUniform;
            illusion.invert = defaults.invert;
            illusion.uniform = defaults.uniform;
            illusion.spacing = defaults.spacing;
            illusion.textures.clear();
            illusion.randomTextures = defaults.randomTextures;
            illusion.real = defaults.real;
            illusion.gradual = defaults.gradual;
            illusion.gradualInvert = defaults.gradualInvert;
            illusion.transform.identity();
            illusion.delay = defaults.delay;
            illusion.distort = defaults.distort;
            illusion.distortUniform = defaults.distortUniform;
            illusion.distortInvert = defaults.distortInvert;
            illusion.glow = defaults.glow;
            illusion.glowUniform = defaults.glowUniform;
            illusion.glowInvert = defaults.glowInvert;
            illusion.distributeParticles = defaults.distributeParticles;
            illusion.independentParticles = defaults.independentParticles;
        });

        this.update();
    }

    private void toggleDirection(int bit, boolean enabled)
    {
        this.editIllusion((illusion) -> illusion.directions = enabled ? illusion.directions | bit : illusion.directions & ~bit);
    }

    private void updateDirections(int directions)
    {
        this.front.setValue((directions & Illusion.FRONT) != 0);
        this.back.setValue((directions & Illusion.BACK) != 0);
        this.left.setValue((directions & Illusion.LEFT) != 0);
        this.right.setValue((directions & Illusion.RIGHT) != 0);
        this.up.setValue((directions & Illusion.UP) != 0);
        this.down.setValue((directions & Illusion.DOWN) != 0);
    }

    @Override
    public void update()
    {
        super.update();

        this.updateIllusionOptions(this.getEditingForm());

        Illusion illusion = this.keyframe.getValue();

        this.count.setValue(illusion.count);
        this.spread.setValue(illusion.spread);
        this.uniform.setValue(illusion.uniform);
        this.spacing.setValue(illusion.spacing);
        this.offset.setValue(illusion.offset);
        this.opacity.setValue(illusion.opacity * 100F);
        this.opacityUniform.setValue(illusion.opacityUniform);
        this.invert.setValue(illusion.invert);
        this.gradual.setValue(illusion.gradual);
        this.gradualInvert.setValue(illusion.gradualInvert);
        this.illusionTransform.setTransform(illusion.transform);
        this.textureBend.setValue(this.keyframe.isBend());
        this.randomTextures.setValue(illusion.randomTextures);
        this.real.setValue(illusion.real);
        this.delay.setValue(illusion.delay);
        this.distort.setValue(illusion.distort);
        this.distortUniform.setValue(illusion.distortUniform);
        this.distortInvert.setValue(illusion.distortInvert);
        this.glow.setValue(illusion.glow);
        this.glowUniform.setValue(illusion.glowUniform);
        this.glowInvert.setValue(illusion.glowInvert);
        this.distributeParticles.setValue(illusion.distributeParticles);
        this.independentParticles.setValue(illusion.independentParticles);
        this.updateDirections(illusion.directions);
    }

    private void updateIllusionOptions(Form form)
    {
        boolean particleForm = form instanceof ParticleForm || form instanceof VanillaParticleForm;
        boolean customParticleForm = form instanceof ParticleForm;
        boolean meshIllusionOptions = !particleForm;

        this.independentParticles.setVisible(customParticleForm);
        this.distributeParticles.setVisible(particleForm);

        this.delayLabel.setVisible(particleForm || meshIllusionOptions);
        this.delay.setVisible(particleForm || meshIllusionOptions);

        this.opacityLabel.setVisible(meshIllusionOptions);
        this.opacity.setVisible(meshIllusionOptions);
        this.opacityFlagsRow.setVisible(meshIllusionOptions);

        this.distortLabel.setVisible(meshIllusionOptions);
        this.distort.setVisible(meshIllusionOptions);
        this.distortFlagsRow.setVisible(meshIllusionOptions);

        this.glowLabel.setVisible(meshIllusionOptions);
        this.glow.setVisible(meshIllusionOptions);
        this.glowFlagsRow.setVisible(meshIllusionOptions);

        this.textures.setVisible(meshIllusionOptions);
        this.clearTextures.setVisible(meshIllusionOptions);
        this.textureBend.setVisible(meshIllusionOptions);
        this.randomTextures.setVisible(meshIllusionOptions);

        this.real.setVisible(meshIllusionOptions);

        if (this.scroll != null)
        {
            this.scroll.resize();
        }
    }

    private Form getEditingForm()
    {
        if (this.editor == null)
        {
            return null;
        }

        UIKeyframeSheet sheet = this.editor.getGraph().getSheet(this.keyframe);

        if (sheet == null && this.keyframe != null && this.keyframe.getParent() != null)
        {
            for (UIKeyframeSheet candidate : this.editor.getGraph().getSheets())
            {
                if (candidate.channel == this.keyframe.getParent())
                {
                    sheet = candidate;
                    break;
                }
            }
        }

        if (sheet != null && sheet.property != null)
        {
            Form form = FormUtils.getForm(sheet.property);

            if (form != null)
            {
                return form;
            }
        }

        Form rootForm = null;
        UIReplaysEditor replays = this.editor.getParent(UIReplaysEditor.class);

        if (replays != null && replays.getReplay() != null)
        {
            rootForm = replays.getReplay().form.get();
        }
        else
        {
            UIFormEditor formEditor = this.editor.getParent(UIFormEditor.class);

            if (formEditor != null)
            {
                rootForm = formEditor.form;
            }
        }

        if (rootForm != null)
        {
            if (sheet != null && sheet.id != null)
            {
                int colon = sheet.id.indexOf(':');
                String propertyPath = colon != -1 ? sheet.id.substring(0, colon) : sheet.id;
                int lastSlash = propertyPath.lastIndexOf('/');

                if (lastSlash != -1)
                {
                    String path = propertyPath.substring(0, lastSlash);
                    Form subForm = FormUtils.getForm(rootForm, path);

                    if (subForm != null)
                    {
                        return subForm;
                    }
                }
            }

            return rootForm;
        }

        return null;
    }
}


