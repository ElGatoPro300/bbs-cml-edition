package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSFeatures;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.SoundBuffer;
import mchorse.bbs_mod.audio.Waveform;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.PerLimbService;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.forms.TrailForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UIFilmPreview;
import mchorse.bbs_mod.ui.film.clips.renderer.IUIClipRenderer;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.film.replays.overlays.UIAnimationToPoseOverlayPanel;
import mchorse.bbs_mod.ui.film.replays.overlays.UIKeyframeSheetFilterOverlayPanel;
import mchorse.bbs_mod.ui.film.replays.overlays.UIRenameSheetOverlayPanel;
import mchorse.bbs_mod.ui.film.replays.overlays.UIReplaysOverlayPanel;
import mchorse.bbs_mod.ui.film.toolbar.KeyframeInsertInteractionState;
import mchorse.bbs_mod.ui.film.toolbar.TimelineInteractionSnapshot;
import mchorse.bbs_mod.ui.film.toolbar.TimelineToolbar;
import mchorse.bbs_mod.ui.film.toolbar.TimelineToolbarDock;
import mchorse.bbs_mod.ui.film.toolbar.TimelineToolbarDockLayout;
import mchorse.bbs_mod.ui.film.toolbar.TimelineToolbarRegistry;
import mchorse.bbs_mod.ui.film.toolbar.TimelineToolbarWiring;
import mchorse.bbs_mod.ui.film.toolbar.TimelineTrackEligibility;
import mchorse.bbs_mod.ui.film.toolbar.UIViewportInteraction;
import mchorse.bbs_mod.ui.film.toolbar.ViewportInteractionState;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.forms.editors.utils.UIBlockRepeatKeyframeUtils;
import mchorse.bbs_mod.ui.forms.editors.utils.UIFormPropertyTrackSheets;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIInverseKinematicsKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UILookAtKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIVisibleRenderKeyframeUtils;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.Scale;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.gizmo.GizmoController;
import mchorse.bbs_mod.ui.utils.gizmo.GizmoSurface;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

public class UIReplaysEditor extends UIElement implements GizmoSurface
{
    private static final Map<String, Integer> COLORS = new HashMap<>();
    private static final Map<String, Icon> ICONS = new HashMap<>();

    public static void registerColor(String id, int color)
    {
        COLORS.put(id, color);
    }

    public static void registerIcon(String id, Icon icon)
    {
        ICONS.put(id, icon);
    }

    private static String lastFilm = "";
    private static int lastReplay;

    public UIReplaysOverlayPanel replays;

    /* Keyframes */
    public UIKeyframeEditor keyframeEditor;

    /* Toolbar (added by the timeline toolbar feature; purely additive) */
    public TimelineToolbar toolbar;

    private final UIViewportInteraction viewportInteraction = new UIViewportInteraction();

    /* Clips */
    private UIFilmPanel filmPanel;

    private final GizmoController gizmoController = new GizmoController(this);
    private Area gizmoDragArea;
    private Film film;
    private Replay replay;
    private Set<String> keys = new LinkedHashSet<>();
    private Keyframe lastPickedKeyframe;
    private final Map<String, Boolean> collapsedModelTracks = new HashMap<>();

    static
    {
        COLORS.put("x", Colors.RED);
        COLORS.put("y", Colors.GREEN);
        COLORS.put("z", Colors.BLUE);
        COLORS.put("vX", Colors.RED);
        COLORS.put("vY", Colors.GREEN);
        COLORS.put("vZ", Colors.BLUE);
        COLORS.put("yaw", Colors.YELLOW);
        COLORS.put("pitch", Colors.CYAN);
        COLORS.put("headYaw", Colors.WHITE);
        COLORS.put("bodyYaw", Colors.MAGENTA);

        COLORS.put("stick_lx", Colors.RED);
        COLORS.put("stick_ly", Colors.GREEN);
        COLORS.put("stick_rx", Colors.RED);
        COLORS.put("stick_ry", Colors.GREEN);
        COLORS.put("trigger_l", Colors.RED);
        COLORS.put("trigger_r", Colors.GREEN);
        COLORS.put("extra1_x", Colors.RED);
        COLORS.put("extra1_y", Colors.GREEN);
        COLORS.put("extra2_x", Colors.RED);
        COLORS.put("extra2_y", Colors.GREEN);
        COLORS.put("shadow_size", Colors.MAGENTA);
        COLORS.put("shadow_opacity", Colors.ORANGE);
        COLORS.put("damage", Colors.RED);
        COLORS.put("invulnerable", Colors.YELLOW);
        COLORS.put("riding", Colors.ORANGE);
        COLORS.put("ridden", Colors.BLUE);

        COLORS.put("visible", Colors.WHITE & Colors.RGB);
        COLORS.put("render", Colors.WHITE & Colors.RGB);
        COLORS.put("pose", Colors.RED);
        COLORS.put("pose_overlay", Colors.ORANGE);
        COLORS.put("transform", Colors.GREEN);
        COLORS.put("transform_overlay", 0xaaff00);
        COLORS.put("color", Colors.INACTIVE);
        COLORS.put("color2", 0xff66ccff);
        COLORS.put("color_mode", 0xffaa66ff);
        COLORS.put("paint_color", Colors.INACTIVE);
        COLORS.put("paint", Colors.INACTIVE);
        COLORS.put("glow", Colors.YELLOW);
        COLORS.put("color_grade", Colors.PINK);
        COLORS.put("lighting", Colors.YELLOW);
        COLORS.put("look_at", 0x007f70);
        COLORS.put("inverse_kinematics", 0x6b4c9a);
        COLORS.put("illusion", Colors.DEEP_PINK);
        COLORS.put("illusion_overlay", 0xff66aa);
        COLORS.put("illusion_transform", 0xdd66ff);
        COLORS.put("illusion_transform_overlay", 0xcc88ff);
        COLORS.put("structure_light", Colors.YELLOW);
        COLORS.put("shape_keys", Colors.PINK);
        COLORS.put("actions", Colors.MAGENTA);

        COLORS.put("item_main_hand", Colors.ORANGE);
        COLORS.put("item_off_hand", Colors.ORANGE);
        COLORS.put("item_head", Colors.ORANGE);
        COLORS.put("item_chest", Colors.ORANGE);
        COLORS.put("item_legs", Colors.ORANGE);
        COLORS.put("item_feet", Colors.ORANGE);

        COLORS.put("user1", Colors.RED);
        COLORS.put("user2", Colors.ORANGE);
        COLORS.put("user3", Colors.GREEN);
        COLORS.put("user4", Colors.BLUE);
        COLORS.put("user5", Colors.RED);
        COLORS.put("user6", Colors.ORANGE);

        COLORS.put("frequency", Colors.RED);
        COLORS.put("count", Colors.GREEN);

        COLORS.put("settings", Colors.MAGENTA);
        COLORS.put("block_state", 0xffffda85);
        COLORS.put("breaking", 0xff90ffe3);
        COLORS.put("repeat_x", Colors.RED);
        COLORS.put("repeat_y", Colors.GREEN);
        COLORS.put("repeat_z", Colors.BLUE);
        COLORS.put("repeat_center_x", 0xffff6666);
        COLORS.put("repeat_center_y", 0xff66ff66);
        COLORS.put("repeat_center_z", 0xff6666ff);
        COLORS.put("item_stack", Colors.ORANGE);
        COLORS.put("modelTransform", Colors.YELLOW);
        COLORS.put("same_animation_when_dropped", Colors.MAGENTA);
        COLORS.put("enabled", Colors.WHITE & Colors.RGB);
        COLORS.put("level", Colors.YELLOW);
        COLORS.put("emit_light", Colors.YELLOW);
        COLORS.put("light_intensity", Colors.YELLOW);
        COLORS.put("structure_light", Colors.YELLOW);
        COLORS.put("biome_id", Colors.GREEN);
        COLORS.put("effect", Colors.MAGENTA);
        COLORS.put("offset_x", Colors.RED);
        COLORS.put("offset_y", Colors.GREEN);
        COLORS.put("offset_z", Colors.BLUE);

        ICONS.put("x", Icons.X);
        ICONS.put("y", Icons.Y);
        ICONS.put("z", Icons.Z);
        ICONS.put("yaw", Icons.Y);
        ICONS.put("pitch", Icons.X);
        ICONS.put("headYaw", Icons.Y);
        ICONS.put("bodyYaw", Icons.Y);

        ICONS.put("visible", Icons.VISIBLE);
        ICONS.put("render", Icons.PLAY);
        ICONS.put("texture", Icons.MATERIAL);
        ICONS.put("pose", Icons.POSE);
        ICONS.put("transform", Icons.ALL_DIRECTIONS);
        ICONS.put("color", Icons.BUCKET);
        ICONS.put("color2", Icons.BUCKET);
        ICONS.put("color_mode", Icons.PROPERTIES);
        ICONS.put("paint_color", Icons.BUCKET);
        ICONS.put("paint", Icons.BUCKET);
        ICONS.put("glow", Icons.LIGHT);
        ICONS.put("color_grade", Icons.FAVORITE);
        ICONS.put("lighting", Icons.LIGHT);
        ICONS.put("look_at", Icons.VISIBLE);
        ICONS.put("inverse_kinematics", Icons.IK);
        ICONS.put("illusion", Icons.POSE);
        ICONS.put("illusion_transform", Icons.ALL_DIRECTIONS);
        ICONS.put("structure_light", Icons.LIGHT);
        ICONS.put("actions", Icons.CONVERT);
        ICONS.put("shape_keys", Icons.HEART_ALT);
        ICONS.put("text", Icons.FONT);

        ICONS.put("stick_lx", Icons.LEFT_STICK);
        ICONS.put("stick_rx", Icons.RIGHT_STICK);
        ICONS.put("trigger_l", Icons.TRIGGER);
        ICONS.put("extra1_x", Icons.CURVES);
        ICONS.put("extra2_x", Icons.CURVES);
        ICONS.put("shadow_size", Icons.SCALE);
        ICONS.put("shadow_opacity", Icons.VISIBLE);
        ICONS.put("damage", Icons.SKULL);
        ICONS.put("invulnerable", Icons.LOCKED);
        ICONS.put("item_main_hand", Icons.LIMB);

        ICONS.put("user1", Icons.PARTICLE);

        ICONS.put("paused", Icons.TIME);
        ICONS.put("frequency", Icons.STOPWATCH);
        ICONS.put("count", Icons.BUCKET);

        ICONS.put("settings", Icons.GEAR);
        ICONS.put("block_state", Icons.BLOCK);
        ICONS.put("breaking", Icons.PICKAXE);
        ICONS.put("repeat_x", Icons.X);
        ICONS.put("repeat_y", Icons.Y);
        ICONS.put("repeat_z", Icons.Z);
        ICONS.put("repeat_center_x", Icons.X);
        ICONS.put("repeat_center_y", Icons.Y);
        ICONS.put("repeat_center_z", Icons.Z);
        ICONS.put("item_stack", Icons.LIMB);
        ICONS.put("modelTransform", Icons.ALL_DIRECTIONS);
        ICONS.put("same_animation_when_dropped", Icons.POSE);
        ICONS.put("enabled", Icons.VISIBLE);
        ICONS.put("level", Icons.LIGHT);
        ICONS.put("emit_light", Icons.LIGHT);
        ICONS.put("light_intensity", Icons.LIGHT);
        ICONS.put("structure_light", Icons.LIGHT);
        ICONS.put("biome_id", Icons.MATERIAL);
        ICONS.put("effect", Icons.PARTICLE);

        /* Structure selection icon for structure_file property */
        ICONS.put("structure_file", Icons.FILE);
    }

    private void collectLimbTracks(Form form, Set<String> propertyPaths)
    {
        if (form == null || !form.animatable.get())
        {
            return;
        }

        if (form instanceof ModelForm modelForm)
        {
            ModelInstance model = ModelFormRenderer.getModel(modelForm);

            if (model != null)
            {
                String path = FormUtils.getPath(modelForm);
                List<Pair<String, Integer>> orderedBones = this.collectBoneOrder(model.model);

                for (Pair<String, Integer> bone : orderedBones)
                {
                    if (bone.a.startsWith("armor_") || bone.a.endsWith("_item"))
                    {
                        continue;
                    }

                    propertyPaths.add(StringUtils.combinePaths(path, "pose") + ":" + bone.a);
                    propertyPaths.add(StringUtils.combinePaths(path, "pose_overlay") + ":" + bone.a);

                    for (int i = 0, c = modelForm.additionalOverlays.size(); i < c; i++)
                    {
                        propertyPaths.add(StringUtils.combinePaths(path, "pose_overlay" + i) + ":" + bone.a);
                    }
                }
            }
        }

        for (BodyPart part : form.parts.getAllTyped())
        {
            this.collectLimbTracks(part.getForm(), propertyPaths);
        }
    }

    private void orderLimbTracks(Form form, List<UIKeyframeSheet> limbs)
    {
        if (form == null || limbs.isEmpty())
        {
            return;
        }

        if (!(form instanceof ModelForm modelForm))
        {
            return;
        }

        ModelInstance model = ModelFormRenderer.getModel(modelForm);

        if (model == null)
        {
            return;
        }

        List<Pair<String, Integer>> orderedBones = this.collectBoneOrder(model.model);

        if (orderedBones.isEmpty())
        {
            return;
        }

        Map<String, UIKeyframeSheet> limbByBone = new HashMap<>();

        for (UIKeyframeSheet limb : limbs)
        {
            int colon = limb.id.indexOf(':');

            if (colon == -1)
            {
                continue;
            }

            limbByBone.put(limb.id.substring(colon + 1), limb);
        }

        Map<String, String> parentByBone = this.collectBoneParents(model.model);
        Map<String, List<String>> childrenByBone = this.collectChildren(parentByBone);

        int baseLevel = limbs.get(0).level;
        List<UIKeyframeSheet> reordered = new ArrayList<>();
        Set<UIKeyframeSheet> used = new HashSet<>();

        for (Pair<String, Integer> bone : orderedBones)
        {
            UIKeyframeSheet limb = limbByBone.get(bone.a);

            if (limb == null)
            {
                continue;
            }

            if (this.isAncestorCollapsed(limb, parentByBone))
            {
                /* Mark as handled so collapsed descendants don't get re-added by fallback pass. */
                used.add(limb);
                continue;
            }

            limb.level = baseLevel + bone.b;
            this.applyLimbExpandState(limb, bone.a, childrenByBone, limbByBone);
            reordered.add(limb);
            used.add(limb);
        }

        for (UIKeyframeSheet limb : limbs)
        {
            if (used.contains(limb))
            {
                continue;
            }

            limb.level = baseLevel;
            limb.toggleExpanded = null;
            reordered.add(limb);
        }

        limbs.clear();
        limbs.addAll(reordered);
    }

    private void applyLimbExpandState(UIKeyframeSheet limb, String boneName, Map<String, List<String>> childrenByBone, Map<String, UIKeyframeSheet> limbByBone)
    {
        if (!this.hasChildTrack(boneName, childrenByBone, limbByBone))
        {
            limb.toggleExpanded = null;
            return;
        }

        String key = this.replay.uuid.get() + ":" + limb.id;
        boolean expanded = !this.collapsedModelTracks.getOrDefault(key, false);

        limb.expanded = expanded;
        limb.toggleExpanded = () ->
        {
            this.collapsedModelTracks.put(key, !this.collapsedModelTracks.getOrDefault(key, false));
            this.updateChannelsList();
        };
    }

    private boolean hasChildTrack(String boneName, Map<String, List<String>> childrenByBone, Map<String, UIKeyframeSheet> limbByBone)
    {
        List<String> children = childrenByBone.get(boneName);

        if (children == null || children.isEmpty())
        {
            return false;
        }

        for (String child : children)
        {
            if (limbByBone.containsKey(child))
            {
                return true;
            }
        }

        return false;
    }

    private boolean isAncestorCollapsed(UIKeyframeSheet limb, Map<String, String> parentByBone)
    {
        int colon = limb.id.indexOf(':');

        if (colon == -1)
        {
            return false;
        }

        String poseTrackId = limb.id.substring(0, colon);
        String boneName = limb.id.substring(colon + 1);
        String parent = parentByBone.get(boneName);

        while (parent != null)
        {
            String key = this.replay.uuid.get() + ":" + poseTrackId + ":" + parent;

            if (this.collapsedModelTracks.getOrDefault(key, false))
            {
                return true;
            }

            parent = parentByBone.get(parent);
        }

        return false;
    }

    private Map<String, String> collectBoneParents(IModel model)
    {
        Map<String, String> parentByBone = new HashMap<>();

        if (model instanceof Model cubicModel)
        {
            this.collectBoneParentsFromGroups(cubicModel.topGroups, null, parentByBone);
        }
        else
        {
            Collection<BOBJBone> bones = model.getAllBOBJBones();

            if (bones != null && !bones.isEmpty())
            {
                for (BOBJBone bone : bones)
                {
                    if (bone.parentBone != null)
                    {
                        parentByBone.put(bone.name, bone.parentBone.name);
                    }
                }
            }
        }

        return parentByBone;
    }

    private void collectBoneParentsFromGroups(List<ModelGroup> groups, String parent, Map<String, String> parentByBone)
    {
        for (ModelGroup group : groups)
        {
            if (parent != null)
            {
                parentByBone.put(group.id, parent);
            }

            if (!group.children.isEmpty())
            {
                this.collectBoneParentsFromGroups(group.children, group.id, parentByBone);
            }
        }
    }

    private Map<String, List<String>> collectChildren(Map<String, String> parentByBone)
    {
        Map<String, List<String>> childrenByBone = new HashMap<>();

        for (Map.Entry<String, String> entry : parentByBone.entrySet())
        {
            childrenByBone.computeIfAbsent(entry.getValue(), (key) -> new ArrayList<>()).add(entry.getKey());
        }

        return childrenByBone;
    }

    private List<Pair<String, Integer>> collectBoneOrder(IModel model)
    {
        List<Pair<String, Integer>> orderedBones = new ArrayList<>();

        if (model instanceof Model cubicModel)
        {
            this.collectBonesFromGroups(cubicModel.topGroups, 0, orderedBones);
        }
        else
        {
            Collection<BOBJBone> bones = model.getAllBOBJBones();

            if (bones != null && !bones.isEmpty())
            {
                for (BOBJBone bone : bones)
                {
                    int depth = 0;
                    BOBJBone parent = bone.parentBone;

                    while (parent != null)
                    {
                        depth += 1;
                        parent = parent.parentBone;
                    }

                    orderedBones.add(new Pair<>(bone.name, depth));
                }
            }
            else
            {
                for (String bone : model.getAllGroupKeys())
                {
                    orderedBones.add(new Pair<>(bone, 0));
                }
            }
        }

        return orderedBones;
    }

    private void collectBonesFromGroups(List<ModelGroup> groups, int depth, List<Pair<String, Integer>> orderedBones)
    {
        for (ModelGroup group : groups)
        {
            orderedBones.add(new Pair<>(group.id, depth));

            if (!group.children.isEmpty())
            {
                this.collectBonesFromGroups(group.children, depth + 1, orderedBones);
            }
        }
    }

    public static Icon getIcon(String key)
    {
        if (key.indexOf(':') != -1)
        {
            return null;
        }

        String topLevel = StringUtils.fileName(key);

        if (topLevel.startsWith("pose_overlay"))
        {
            return Icons.POSE;
        }

        if (topLevel.startsWith("transform_overlay"))
        {
            return Icons.ALL_DIRECTIONS;
        }

        if (topLevel.startsWith("illusion_overlay"))
        {
            return Icons.POSE;
        }

        if (topLevel.startsWith("illusion_transform_overlay"))
        {
            return Icons.ALL_DIRECTIONS;
        }

        if (topLevel.equals("riding") || topLevel.equals("sitting") || topLevel.equals("ridden"))
        {
            return Icons.NONE;
        }

        return ICONS.getOrDefault(topLevel, Icons.NONE);
    }

    private static UIKeyframeSheet withTrackIcon(UIKeyframeSheet sheet, String key)
    {
        Icon icon = getIcon(key);

        if ((icon == null || icon == Icons.NONE) && key.indexOf(':') != -1)
        {
            String subKey = key.substring(key.indexOf(':') + 1);

            icon = getIcon(subKey);

            if (icon == null || icon == Icons.NONE)
            {
                String parentKey = key.substring(0, key.indexOf(':'));

                icon = getIcon(parentKey);
            }
        }

        if (icon == null || icon == Icons.NONE)
        {
            icon = Icons.PROPERTIES;
        }

        sheet.icon(icon);

        return sheet;
    }

    public static int getColor(String key)
    {
        int colon = key.indexOf(':');

        if (colon != -1)
        {
            String propertyPath = key.substring(0, colon);
            String propertyName = StringUtils.fileName(propertyPath);

            if (propertyName.equals("pose") || propertyName.startsWith("pose_overlay"))
            {
                return getPoseBoneColor(key.substring(colon + 1), propertyName.startsWith("pose_overlay"));
            }

            return 0xff3333;
        }

        String topLevel = StringUtils.fileName(key);

        if (topLevel.startsWith("pose_overlay")) return COLORS.get("pose_overlay");
        if (topLevel.startsWith("transform_overlay")) return COLORS.get("transform_overlay");
        if (topLevel.startsWith("illusion_overlay")) return COLORS.get("illusion_overlay");
        if (topLevel.startsWith("illusion_transform_overlay")) return COLORS.get("illusion_transform_overlay");

        return COLORS.getOrDefault(topLevel, Colors.ACTIVE);
    }

    private static int getPoseBoneColor(String boneName, boolean overlay)
    {
        int hash = Integer.rotateLeft(boneName.hashCode(), 13) ^ boneName.hashCode();
        float hue = (hash & 0xffff) / 65535F;
        float saturation = overlay ? 0.52F : 0.72F;
        float brightness = overlay ? 0.95F : 0.9F;
        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0x00ffffff;

        return 0xff000000 | rgb;
    }

    public static void offerAdjacent(UIContext context, Form form, String bone, Consumer<String> consumer)
    {
        if (!bone.isEmpty() && form instanceof ModelForm modelForm)
        {
            ModelInstance model = ModelFormRenderer.getModel(modelForm);

            if (model == null)
            {
                return;
            }

            context.replaceContextMenu((menu) ->
            {
                for (String modelGroup : model.model.getAdjacentGroups(bone))
                {
                    if (modelGroup.endsWith("_item"))
                    {
                        continue;
                    }

                    menu.action(Icons.LIMB, IKey.constant(modelGroup), () -> consumer.accept(modelGroup));
                }

                menu.autoKeys();
            });
        }
    }

    public static void offerHierarchy(UIContext context, Form form, String bone, Consumer<String> consumer)
    {
        if (!bone.isEmpty() && form instanceof ModelForm modelForm)
        {
            ModelInstance model = ModelFormRenderer.getModel(modelForm);

            if (model == null)
            {
                return;
            }

            context.replaceContextMenu((menu) ->
            {
                for (String modelGroup : model.model.getHierarchyGroups(bone))
                {
                    if (modelGroup.endsWith("_item"))
                    {
                        continue;
                    }

                    menu.action(Icons.LIMB, IKey.constant(modelGroup), () -> consumer.accept(modelGroup));
                }

                menu.autoKeys();
            });
        }
    }

    public static final Form DUMMY_FORM = new StructureForm();

    /**
     * Tracks exposed on replay folder/group timelines. Folders can contain any form type,
     * so this list is limited to properties that {@link BaseFilmController}
     * can apply to every form (visibility, color multiply, transform, paint, glow).
     * {@code render} is created for the visible/Enabled pairing but stays timeline-hidden.
     * Shadow size/opacity come from {@link ReplayKeyframes}
     * (same channels as normal replays) and are merged onto members at render time.
     */
    private static final List<String> GROUP_FORM_PROPERTIES = Arrays.asList(
        "visible",
        "render",
        "transform",
        "transform_overlay",
        "illusion",
        "color",
        "color_grade",
        "paint",
        "glow"
    );

    private static final List<String> GROUP_SHADOW_CHANNELS = Arrays.asList(
        "shadow_size",
        "shadow_opacity"
    );

    public static boolean renderBackground(UIContext context, UIKeyframes keyframes, Clips camera, int clipOffset, Clip selectedClip)
    {
        Scale scale = keyframes.getXAxis();
        boolean renderedOnce = false;
        Area area = new Area();
        area.copy(keyframes.area);
        int sidebarWidth = keyframes.getSidebarWidth();
        area.x += sidebarWidth;
        area.w -= sidebarWidth;

        context.batcher.clip(area, context);

        /* Selected clip: a color wash on the keyframe ruler, not a replica of the clip bar. */
        for (Clip clip : camera.get())
        {
            if (clip == selectedClip && !(clip instanceof AudioClip))
            {
                float offset = clip.tick.get() - clipOffset;
                int x1 = (int) scale.to(offset);
                int x2 = (int) scale.to(offset + clip.duration.get());

                if (x2 > keyframes.area.x && x1 < keyframes.area.ex())
                {
                    ClipFactoryData data = camera.getFactory().getData(clip);
                    int color = data.color;
                    int rulerBottom = keyframes.area.y + 16;
                    int top = Colors.setA(color, 0.5F);
                    int mid = Colors.setA(color, 0.16F);
                    int fade = Colors.setA(color, 0F);

                    x1 = Math.max(x1, area.x);
                    x2 = Math.min(x2, area.ex());

                    context.batcher.gradientVBox(x1, keyframes.area.y, x2, rulerBottom, top, mid);
                    context.batcher.gradientVBox(x1, rulerBottom, x2, keyframes.area.ey(), mid, fade);

                    renderedOnce = true;
                }
            }
        }

        /* Second pass: Render audio waveforms on top */
        for (Clip clip : camera.get())
        {
            if (clip instanceof AudioClip audioClip)
            {
                if (!BBSSettings.audioWaveformVisible.get())
                {
                    continue;
                }

                Link link = audioClip.audio.get();

                if (link == null)
                {
                    continue;
                }

                SoundBuffer buffer = BBSModClient.getSounds().get(link, true);

                if (buffer == null || buffer.getWaveform() == null)
                {
                    continue;
                }

                Waveform wave = buffer.getWaveform();

                if (wave != null)
                {
                    int audioOffset = audioClip.offset.get();
                    float offset = audioClip.tick.get() - clipOffset;
                    int duration = Math.min((int) (wave.getDuration() * 20), clip.duration.get());

                    int x1 = (int) scale.to(offset);
                    int x2 = (int) scale.to(offset + duration);

                    wave.render(context.batcher, Colors.WHITE, x1, keyframes.area.y + 15, x2 - x1, 20, TimeUtils.toSeconds(audioOffset), TimeUtils.toSeconds(audioOffset + duration));

                    renderedOnce = true;
                }
            }
        }

        context.batcher.unclip(context);

        return renderedOnce;
    }

    public UIReplaysEditor(UIFilmPanel filmPanel)
    {
        this.filmPanel = filmPanel;
        this.replays = new UIReplaysOverlayPanel(filmPanel, (replay) -> this.setReplay(replay, false, true));

        this.toolbar = new TimelineToolbar();
        this.toolbar.setSections(TimelineToolbarRegistry.forReplays(true));
        TimelineToolbarWiring.wireReplaysToolbar(this);
        this.toolbar.setInteractionCancelListener(this::cancelToolbarInteraction);
        this.add(this.toolbar);

        this.applyToolbarDockLayout();

        this.markContainer();
    }

    public void applyToolbarDockLayout()
    {
        TimelineToolbarDock dock = BBSSettings.timelineToolbarDocks.getDock(TimelineToolbarDockLayout.PANEL_REPLAY);

        this.toolbar.configureDockHost(this, TimelineToolbarDockLayout.PANEL_REPLAY, this::applyToolbarDockLayout);
        TimelineToolbarDockLayout.apply(this, this.toolbar, dock, this.keyframeEditor);
    }

    private void cancelToolbarInteraction()
    {
        this.viewportInteraction.cancel();

        if (this.keyframeEditor != null)
        {
            this.keyframeEditor.view.cancelTrackInteraction();
        }
    }

    public boolean isViewportInteractionActive()
    {
        return this.viewportInteraction.isActive();
    }

    public void renderViewportInteraction(UIContext context, Area viewport)
    {
        this.viewportInteraction.renderOverlay(context, viewport);
    }

    public void renderViewportInteractionHint(UIContext context, Area viewport, int bottomReserve)
    {
        this.viewportInteraction.renderHint(context, viewport, bottomReserve);
    }

    public boolean handleViewportInteractionMouse(UIContext context, Area viewport)
    {
        return this.viewportInteraction.handleMouseClicked(context, viewport,
            () -> this.rayTraceViewportBlock(context, viewport));
    }

    public boolean handleViewportInteractionKey(UIContext context)
    {
        return this.viewportInteraction.handleKeyPressed(context);
    }

    public void toolbarAddReplayAtViewport()
    {
        this.viewportInteraction.enter(new ViewportInteractionState(
            UIKeys.TIMELINE_INTERACTION_VIEWPORT_ADD,
            this::confirmAddReplayAtViewport));
    }

    public void toolbarMoveReplayAtViewport()
    {
        this.viewportInteraction.enter(new ViewportInteractionState(
            UIKeys.TIMELINE_INTERACTION_VIEWPORT_MOVE,
            this::confirmMoveReplayAtViewport));
    }

    private void confirmAddReplayAtViewport(Vector3d position)
    {
        Camera camera = this.filmPanel.getCamera();
        float pitch = 0F;
        float yaw = MathUtils.toDeg(camera.rotation.y);

        this.replays.replays.addReplay(position, pitch, yaw);
    }

    private void confirmMoveReplayAtViewport(Vector3d position)
    {
        this.moveReplay(position.x, position.y, position.z);
    }

    private Vector3d rayTraceViewportBlock(UIContext context, Area area)
    {
        World world = MinecraftClient.getInstance().world;
        UIFilmPreview preview = this.filmPanel.preview;

        if (world == null || preview == null || area == null || area.w <= 0 || area.h <= 0)
        {
            return null;
        }

        /* Use the world camera (the camera the viewport is actually rendered with) and
         * compute the ray analytically from its rotation + fov. The captured render
         * matrices can't be used here: without shaders the captured view matrix does
         * not include the camera rotation (see renderPickingPreview). */
        Camera camera = this.filmPanel.getWorldCamera();

        Area viewport = preview.getViewport();

        if (!viewport.isInside(context.mouseX(), context.mouseY()))
        {
            return null;
        }

        int renderW = BBSRendering.getVideoWidth();
        int renderH = BBSRendering.getVideoHeight();

        if (renderW <= 0 || renderH <= 0)
        {
            return null;
        }

        int px = (int) ((context.mouseX() - viewport.x) / (float) viewport.w * renderW);
        int py = (int) ((context.mouseY() - viewport.y) / (float) viewport.h * renderH);

        px = MathUtils.clamp(px, 0, renderW - 1);
        py = MathUtils.clamp(py, 0, renderH - 1);

        Vector3f direction = camera.getMouseDirectionFov(px, py, 0, 0, renderW, renderH);

        if (direction.lengthSquared() <= 1.0E-12F)
        {
            return null;
        }

        BlockHitResult blockHitResult = RayTracing.rayTrace(
            world,
            RayTracing.fromVector3d(camera.position),
            RayTracing.fromVector3f(direction),
            256F
        );

        Vector3d vec;

        if (blockHitResult.getType() == HitResult.Type.MISS)
        {
            /* No block under the cursor (looking at the sky or terrain out of range):
             * fall back to a point along the ray so the context menu still works. */
            double fallbackDistance = 32D;

            vec = new Vector3d(
                camera.position.x + direction.x * fallbackDistance,
                camera.position.y + direction.y * fallbackDistance,
                camera.position.z + direction.z * fallbackDistance
            );
        }
        else
        {
            vec = new Vector3d(
                blockHitResult.getPos().x,
                blockHitResult.getPos().y,
                blockHitResult.getPos().z
            );
        }

        if (Window.isShiftPressed())
        {
            vec = new Vector3d(
                Math.floor(vec.x) + 0.5D,
                Math.round(vec.y),
                Math.floor(vec.z) + 0.5D
            );
        }

        return vec;
    }

    public Vector3d rayTraceViewportFromContext(UIContext context, Area area)
    {
        return this.rayTraceViewportBlock(context, area);
    }

    private void openViewportReplayContextMenu(UIContext context, Vector3d position)
    {
        Camera camera = this.filmPanel.getCamera();

        if (camera == null)
        {
            return;
        }

        final Vector3d finalVec = position;
        float pitch = 0F;
        float yaw = MathUtils.toDeg(camera.rotation.y);
        boolean canMove = this.getReplay() != null;

        context.replaceContextMenu((menu) ->
        {
            menu.action(Icons.ADD, UIKeys.FILM_REPLAY_CONTEXT_ADD,
                () -> this.replays.replays.addReplay(finalVec, pitch, yaw));

            if (canMove)
            {
                menu.action(Icons.POINTER, UIKeys.FILM_REPLAY_CONTEXT_MOVE_HERE,
                    () -> this.moveReplay(finalVec.x, finalVec.y, finalVec.z));
            }
        });
    }

    public UIFilmPanel getFilmPanel()
    {
        return this.filmPanel;
    }

    public void toolbarRenameSheet()
    {
        if (this.keyframeEditor == null)
        {
            return;
        }

        this.keyframeEditor.view.enterTrackInteraction(
            UIKeys.TIMELINE_INTERACTION_PICK_TRACK,
            TimelineTrackEligibility::canRename,
            this::confirmRenameSheet);
    }

    private void confirmRenameSheet(UIKeyframeSheet clickedSheet)
    {
        UIRenameSheetOverlayPanel panel = new UIRenameSheetOverlayPanel(
            UIKeys.FILM_REPLAY_RENAME_SHEET_TITLE,
            UIKeys.FILM_REPLAY_RENAME_SHEET_MESSAGE,
            this.replay,
            clickedSheet.id,
            (str, color) ->
            {
                this.replay.setCustomSheetTitle(clickedSheet.id, str);
                this.replay.setSheetColor(clickedSheet.id, color);
                this.updateChannelsList();
            }
        );

        panel.text.setText(clickedSheet.title.get());
        UIOverlay.addOverlay(this.getContext(), panel, 300, 0.25F);
    }

    public void toolbarFilterSheets()
    {
        if (this.keyframeEditor == null)
        {
            return;
        }

        UIKeyframeSheetFilterOverlayPanel panel = new UIKeyframeSheetFilterOverlayPanel(BBSSettings.disabledSheets.get(), this.keys);

        UIOverlay.addOverlay(this.getContext(), panel, 240, 0.9F);

        panel.onClose((e) ->
        {
            this.updateChannelsList();
            BBSSettings.disabledSheets.set(BBSSettings.disabledSheets.get());
        });
    }

    public void toolbarAnimationToKeyframes()
    {
        if (this.keyframeEditor == null)
        {
            return;
        }

        this.keyframeEditor.view.enterTrackInteraction(
            UIKeys.TIMELINE_INTERACTION_PICK_TRACK,
            sheet -> TimelineTrackEligibility.canAnimationToPose(this, sheet),
            this::confirmAnimationToKeyframes);
    }

    private void confirmAnimationToKeyframes(UIKeyframeSheet sheet)
    {
        Form form = sheet.property != null ? FormUtils.getForm(sheet.property) : this.replay.form.get();

        if (form instanceof ModelForm modelForm)
        {
            this.animationToPoses(modelForm, sheet);
        }
    }

    public void toolbarPoseToLimbs()
    {
        if (this.keyframeEditor == null)
        {
            return;
        }

        this.keyframeEditor.view.enterTrackInteraction(
            UIKeys.TIMELINE_INTERACTION_PICK_TRACK,
            sheet -> TimelineTrackEligibility.canPoseToLimbs(this, sheet),
            this::confirmPoseToLimbs);
    }

    private void confirmPoseToLimbs(UIKeyframeSheet sheet)
    {
        this.convertToLimbs(sheet);
    }

    public void setFilm(Film film)
    {
        this.setFilm(film, true);
    }

    public void setFilm(Film film, boolean resetOrbit)
    {
        this.film = film;

        if (film != null)
        {
            List<Replay> replays = film.replays.getList();
            int index = film.getId().equals(lastFilm) ? lastReplay : 0;

            if (!CollectionUtils.inRange(replays, index))
            {
                index = 0;
            }

            this.replays.replays.setList(replays);
            this.setReplay(replays.isEmpty() ? null : replays.get(index), true, resetOrbit);
        }
        else
        {
            this.replays.replays.setList(new ArrayList<>());
            this.setReplay(null, false, false);
        }

        this.filmPanel.syncAnchoredReplaysPanelWithFilm();
    }

    /**
     * Light-weight film sync for undo/redo: updates the replay list widget without rebuilding
     * the keyframe editor or resetting orbit camera. {@link #applyUndoData} already restored
     * replay index and keyframe selection before {@link UIFilmPanel#refreshAfterUndo()} runs.
     */
    public void syncFilmForUndo(Film film)
    {
        this.film = film;

        if (film == null)
        {
            this.replays.replays.setList(new ArrayList<>());

            return;
        }

        this.replays.replays.setList(film.replays.getList());
        this.replays.replays.update();

        Replay replay = this.replay;

        if (replay != null && !film.replays.getList().contains(replay))
        {
            this.setReplay(film.replays.getList().isEmpty() ? null : film.replays.getList().get(0), true, false);
        }
        else if (replay != null)
        {
            this.filmPanel.actionEditor.setClips(replay.actions);
            BBSModClient.setSelectedReplay(replay);
            this.replays.syncReplaySelection(replay, true);
            this.filmPanel.syncAnchoredReplaysPanelSelection(replay, true);
        }
    }

    public Replay getReplay()
    {
        return this.replay;
    }

    public void setReplay(Replay replay)
    {
        this.setReplay(replay, true, true);
    }

    public void setReplay(Replay replay, boolean select, boolean resetOrbit)
    {
        Camera fromCamera = null;
        Vector3f oldAnchor = null;
        boolean orbitPick = resetOrbit
            && replay != null
            && this.filmPanel.getController().getPovMode() == UIFilmController.CAMERA_MODE_ORBIT
            && this.filmPanel.getController().orbit.enabled;

        if (orbitPick)
        {
            fromCamera = new Camera();
            fromCamera.copy(this.filmPanel.getCamera());
            oldAnchor = this.filmPanel.getController().orbit.getAnchorPoint(0F);
        }

        this.replay = replay;

        BBSModClient.setSelectedReplay(replay);

        if (orbitPick)
        {
            this.filmPanel.getController().orbit.pickTarget(replay, fromCamera, oldAnchor, 0F);
        }
        else if (resetOrbit)
        {
            this.filmPanel.getController().orbit.reset();
        }

        this.replays.syncReplaySelection(replay, select);
        this.filmPanel.syncAnchoredReplaysPanelSelection(replay, select);
        this.filmPanel.actionEditor.setClips(replay == null ? null : replay.actions);
        this.initializeCollapsedGroupsForReplay(replay);
        this.updateChannelsList();
    }

    private void initializeCollapsedGroupsForReplay(Replay replay)
    {
        if (replay == null || replay.uuid == null)
        {
            return;
        }

        String replayId = replay.uuid.get();
        replayId = replayId == null ? "" : replayId;

        String initKey = replayId + ":__collapsed_init__";

        /* Initialize only once per replay, then preserve user folding choices. */
        if (this.collapsedModelTracks.containsKey(initKey))
        {
            return;
        }

        Form form = replay.form.get();

        if (form == null)
        {
            return;
        }

        Form rootForm = FormUtils.getRoot(form);
        String rootPath = FormUtils.getPath(rootForm);

        this.collapsedModelTracks.put(replayId + ":" + rootPath, false);
        this.collapsedModelTracks.put(replayId + ":__model__", false);
        this.collapsedModelTracks.put(replayId + ":__world__", true);
        this.collapsedModelTracks.put(replayId + ":__vanilla_poses__", true);
        this.collapsedModelTracks.put(replayId + ":__vanilla_actions__", true);
        this.collapsedModelTracks.put(replayId + ":__model__:color", true);
        this.collapsedModelTracks.put(replayId + ":damage", true);

        List<String> childPaths = new ArrayList<>();

        this.collectChildFormPaths(rootForm, "", childPaths);

        for (String path : childPaths)
        {
            this.collapsedModelTracks.put(replayId + ":" + path, true);
        }

        this.collapsedModelTracks.put(initKey, true);
    }

    private void collectChildFormPaths(Form form, String parentPath, List<String> out)
    {
        List<BodyPart> parts = form.parts.getAllTyped();

        for (int i = 0; i < parts.size(); i++)
        {
            Form child = parts.get(i).getForm();

            if (child == null)
            {
                continue;
            }

            String path = parentPath.isEmpty() ? String.valueOf(i) : parentPath + "/" + i;

            out.add(path);
            this.collectChildFormPaths(child, path, out);
        }
    }

    /**
     * Switch to the replay timeline tab (and its linked properties) so viewport
     * bone picks can drive pose gizmos instead of staying on the camera timeline.
     */
    private void focusReplayTimelineForPick()
    {
        if (this.filmPanel == null)
        {
            return;
        }

        this.filmPanel.focusPanelTab("replayTimeline");
        this.filmPanel.focusLinkedPropertiesTab("replayTimeline");
        this.filmPanel.showPanel(this);
    }

    /**
     * Expand collapsed nested-form track groups so bone picks on body parts can
     * reach that form's pose sheet. Does <b>not</b> expand the pose/limb group
     * itself — limb sheets stay hidden until the user opens pose (default). With
     * an empty pose track, bone picks insert a provisional keyframe on the main
     * pose sheet instead of expanding limbs.
     */
    private void ensureTracksVisibleForFormBone(Form form, String bone)
    {
        if (this.replay == null || form == null)
        {
            return;
        }

        String replayId = this.replay.uuid.get();

        replayId = replayId == null ? "" : replayId;

        boolean changed = false;
        String formPath = FormUtils.getPath(form);
        Form rootForm = FormUtils.getRoot(form);
        String rootPath = FormUtils.getPath(rootForm);

        changed |= this.expandTrackGroup(replayId + ":" + rootPath, false);

        if (!formPath.isEmpty())
        {
            String[] segments = formPath.split("/");
            StringBuilder built = new StringBuilder();

            for (int i = 0; i < segments.length; i++)
            {
                if (i > 0)
                {
                    built.append('/');
                }

                built.append(segments[i]);
                changed |= this.expandTrackGroup(replayId + ":" + built, false);
            }
        }

        /* Only expand nested limb subgroups when pose limbs are already visible. */
        String poseId = StringUtils.combinePaths(formPath, "pose");
        String poseKey = replayId + ":" + poseId;
        boolean poseExpanded = !this.collapsedModelTracks.getOrDefault(poseKey, true);

        if (poseExpanded && bone != null && !bone.isEmpty() && form instanceof ModelForm modelForm)
        {
            ModelInstance model = ModelFormRenderer.getModel(modelForm);

            if (model != null && model.model != null)
            {
                Map<String, String> parentByBone = this.collectBoneParents(model.model);
                String parent = parentByBone.get(bone);

                while (parent != null)
                {
                    changed |= this.expandTrackGroup(replayId + ":" + poseId + ":" + parent, false);
                    parent = parentByBone.get(parent);
                }
            }
        }

        if (changed)
        {
            this.updateChannelsList();
        }
    }

    private boolean expandTrackGroup(String key, boolean collapsedByDefault)
    {
        if (this.collapsedModelTracks.getOrDefault(key, collapsedByDefault))
        {
            this.collapsedModelTracks.put(key, false);

            return true;
        }

        return false;
    }

    public void moveReplay(double x, double y, double z)
    {
        if (this.replay != null)
        {
            int cursor = this.filmPanel.getCursor();

            this.replay.keyframes.x.insert(cursor, x);
            this.replay.keyframes.y.insert(cursor, y);
            this.replay.keyframes.z.insert(cursor, z);
        }
    }

    public void insertKeyframeColumn(float tick)
    {
        Replay replay = this.getReplay();
        IEntity entity = this.filmPanel.getController().getCurrentEntity();

        if (replay == null || entity == null)
        {
            return;
        }

        List<String> groups = Arrays.asList(ReplayKeyframes.GROUP_POSITION, ReplayKeyframes.GROUP_ROTATION);

        BaseValue.edit(replay.keyframes, (keyframes) ->
        {
            keyframes.record(tick, entity, groups);
        });
    }

    public void insertKeyframeColumnInterpolated(float tick)
    {
        Replay replay = this.getReplay();

        if (replay == null)
        {
            return;
        }

        List<String> groups = Arrays.asList(ReplayKeyframes.GROUP_POSITION, ReplayKeyframes.GROUP_ROTATION);

        BaseValue.edit(replay.keyframes, (keyframes) ->
        {
            keyframes.insertInterpolated(tick, groups);
        });
    }

    public void toolbarInsertKeyframeAtTimeline()
    {
        if (this.keyframeEditor == null)
        {
            return;
        }

        this.insertKeyframeColumn(this.filmPanel.getCursor());
        UIUtils.playClick();
    }

    public void toolbarEnterInsertColumnAtCursor()
    {
        if (this.keyframeEditor == null)
        {
            return;
        }

        this.keyframeEditor.view.enterKeyframeInsert(KeyframeInsertInteractionState.columnAtCursor(
            UIKeys.TIMELINE_INTERACTION_INSERT_KEYFRAME_COLUMN_CURSOR,
            this::insertKeyframeColumnInterpolated));
    }

    public void toolbarEnterInsertSingleAtTimeline()
    {
        if (this.keyframeEditor == null)
        {
            return;
        }

        int tick = this.filmPanel.getCursor();

        this.keyframeEditor.view.enterKeyframeInsert(KeyframeInsertInteractionState.individualAtPlayhead(
            UIKeys.TIMELINE_INTERACTION_INSERT_KEYFRAME_SINGLE_TIMELINE,
            tick,
            (sheet, keyframeTick) -> this.keyframeEditor.view.toolbarInsertIndividual(sheet, keyframeTick)));
    }

    public void toolbarEnterInsertSingleAtCursor()
    {
        if (this.keyframeEditor == null)
        {
            return;
        }

        this.keyframeEditor.view.enterKeyframeInsert(KeyframeInsertInteractionState.individualAtCursor(
            UIKeys.TIMELINE_INTERACTION_INSERT_KEYFRAME_SINGLE_CURSOR,
            (sheet, keyframeTick) -> this.keyframeEditor.view.toolbarInsertIndividual(sheet, keyframeTick)));
    }

    public boolean canToolbarInsertKeyframeColumn()
    {
        return this.keyframeEditor != null
            && this.keyframeEditor.view.isModifyingKeyframes()
            && this.keyframeEditor.view instanceof UIFilmKeyframes filmKeyframes
            && filmKeyframes.isReplayWorldEditor()
            && this.getReplay() != null
            && this.filmPanel.getController().getCurrentEntity() != null;
    }

    private static final List<String> WORLD_CHANNELS = Arrays.asList("x", "y", "z", "vX", "vY", "vZ", "yaw", "pitch", "headYaw", "bodyYaw", "grounded", "damage", "invulnerable", "death_time", "using_item", "item_use_time", "fire", "particles", "active_hand", "fall", "sneaking", "riding", "sprinting", "swimming", "flying", "fall_flying", "crawling", "climbing", "blocking", "sleeping", "riptide", "item_main_hand", "item_off_hand", "item_head", "item_chest", "item_legs", "item_feet", "selected_slot", "stick_lx", "stick_ly", "stick_rx", "stick_ry", "trigger_l", "trigger_r", "extra1_x", "extra1_y", "extra2_x", "extra2_y", "shadow_size", "shadow_opacity");
    private static final Set<String> VANILLA_POSE_CHANNELS = Set.of(
        "riding", "swimming", "flying", "fall_flying",
        "crawling", "climbing", "blocking", "sleeping", "riptide"
    );
    private static final Set<String> VANILLA_ACTION_CHANNELS = Set.of(
        "death_time", "using_item", "item_use_time", "fire", "particles", "active_hand"
    );
    private static final List<String> MODEL_PROPERTIES = Arrays.asList("visible", "render", "lighting", "transform", "transform_overlay", "pose", "pose_overlay", "anchor", "look_at", "inverse_kinematics", "illusion", "illusion_transform", "color", "color2", "color_mode", "color_grade", "paint", "paint_color", "glow", "texture", "pbr_normal_intensity", "pbr_specular_intensity", "model", "actions", "shape_keys", "block_state", "item_stack", "modelTransform", "same_animation_when_dropped", "settings", "paused", "frequency", "count", "structure_file", "biome_id", "emit_light", "light_intensity", "structure_light", "enabled", "level", "effect");

    private static boolean isFormItemUseTimeTrack(UIKeyframeSheet sheet)
    {
        return sheet != null && "item_use_time".equals(sheet.id) && sheet.property != null;
    }

    /**
     * Human-readable timeline track names for internal property ids (overlays, paint color, etc.).
     */
    private static IKey resolveWorldChannelTrackTitle(String trackName)
    {
        if (trackName.equals("x"))
        {
            return UIKeys.FILM_REPLAY_TRACK_X;
        }

        if (trackName.equals("y"))
        {
            return UIKeys.FILM_REPLAY_TRACK_Y;
        }

        if (trackName.equals("z"))
        {
            return UIKeys.FILM_REPLAY_TRACK_Z;
        }

        if (trackName.equals("vX"))
        {
            return UIKeys.FILM_REPLAY_TRACK_VX;
        }

        if (trackName.equals("vY"))
        {
            return UIKeys.FILM_REPLAY_TRACK_VY;
        }

        if (trackName.equals("vZ"))
        {
            return UIKeys.FILM_REPLAY_TRACK_VZ;
        }

        if (trackName.equals("yaw"))
        {
            return UIKeys.FILM_REPLAY_TRACK_YAW;
        }

        if (trackName.equals("pitch"))
        {
            return UIKeys.FILM_REPLAY_TRACK_PITCH;
        }

        if (trackName.equals("headYaw"))
        {
            return UIKeys.FILM_REPLAY_TRACK_HEAD_YAW;
        }

        if (trackName.equals("bodyYaw"))
        {
            return UIKeys.FILM_REPLAY_TRACK_BODY_YAW;
        }

        if (trackName.equals("grounded"))
        {
            return UIKeys.FILM_REPLAY_TRACK_GROUNDED;
        }

        if (trackName.equals("damage"))
        {
            return UIKeys.FILM_REPLAY_TRACK_DAMAGE;
        }

        if (trackName.equals("invulnerable"))
        {
            return UIKeys.FILM_REPLAY_TRACK_INVULNERABLE;
        }

        if (trackName.equals("death_time"))
        {
            return UIKeys.FILM_REPLAY_TRACK_DEATH_TIME;
        }

        if (trackName.equals("using_item"))
        {
            return UIKeys.FILM_REPLAY_TRACK_USING_ITEM;
        }

        if (trackName.equals("item_use_time"))
        {
            return UIKeys.FILM_REPLAY_TRACK_ITEM_USE_TIME;
        }

        if (trackName.equals("fire"))
        {
            return UIKeys.FILM_REPLAY_TRACK_FIRE;
        }

        if (trackName.equals("particles"))
        {
            return UIKeys.FILM_REPLAY_TRACK_PARTICLES;
        }

        if (trackName.equals("active_hand"))
        {
            return UIKeys.FILM_REPLAY_TRACK_ACTIVE_HAND;
        }

        if (trackName.equals("fall"))
        {
            return UIKeys.FILM_REPLAY_TRACK_FALL;
        }

        if (trackName.equals("sneaking"))
        {
            return UIKeys.FILM_REPLAY_TRACK_SNEAKING;
        }

        if (trackName.equals("sprinting"))
        {
            return UIKeys.FILM_REPLAY_TRACK_SPRINTING;
        }

        if (trackName.equals("swimming"))
        {
            return UIKeys.FILM_REPLAY_TRACK_SWIMMING;
        }

        if (trackName.equals("flying"))
        {
            return UIKeys.FILM_REPLAY_TRACK_FLYING;
        }

        if (trackName.equals("fall_flying"))
        {
            return UIKeys.FILM_REPLAY_TRACK_FALL_FLYING;
        }

        if (trackName.equals("crawling"))
        {
            return UIKeys.FILM_REPLAY_TRACK_CRAWLING;
        }

        if (trackName.equals("climbing"))
        {
            return UIKeys.FILM_REPLAY_TRACK_CLIMBING;
        }

        if (trackName.equals("blocking"))
        {
            return UIKeys.FILM_REPLAY_TRACK_BLOCKING;
        }

        if (trackName.equals("sleeping"))
        {
            return UIKeys.FILM_REPLAY_TRACK_SLEEPING;
        }

        if (trackName.equals("riptide"))
        {
            return UIKeys.FILM_REPLAY_TRACK_RIPTIDE;
        }

        if (trackName.equals("item_main_hand"))
        {
            return UIKeys.FILM_REPLAY_TRACK_ITEM_MAIN_HAND;
        }

        if (trackName.equals("item_off_hand"))
        {
            return UIKeys.FILM_REPLAY_TRACK_ITEM_OFF_HAND;
        }

        if (trackName.equals("item_head"))
        {
            return UIKeys.FILM_REPLAY_TRACK_ITEM_HEAD;
        }

        if (trackName.equals("item_chest"))
        {
            return UIKeys.FILM_REPLAY_TRACK_ITEM_CHEST;
        }

        if (trackName.equals("item_legs"))
        {
            return UIKeys.FILM_REPLAY_TRACK_ITEM_LEGS;
        }

        if (trackName.equals("item_feet"))
        {
            return UIKeys.FILM_REPLAY_TRACK_ITEM_FEET;
        }

        if (trackName.equals("selected_slot"))
        {
            return UIKeys.FILM_REPLAY_TRACK_SELECTED_SLOT;
        }

        if (trackName.equals("riding"))
        {
            return UIKeys.FILM_REPLAY_TRACK_RIDING;
        }

        if (trackName.equals("ridden"))
        {
            return UIKeys.FILM_REPLAY_TRACK_RIDDEN;
        }

        if (trackName.equals("stick_lx"))
        {
            return UIKeys.FILM_REPLAY_TRACK_STICK_LX;
        }

        if (trackName.equals("stick_ly"))
        {
            return UIKeys.FILM_REPLAY_TRACK_STICK_LY;
        }

        if (trackName.equals("stick_rx"))
        {
            return UIKeys.FILM_REPLAY_TRACK_STICK_RX;
        }

        if (trackName.equals("stick_ry"))
        {
            return UIKeys.FILM_REPLAY_TRACK_STICK_RY;
        }

        if (trackName.equals("trigger_l"))
        {
            return UIKeys.FILM_REPLAY_TRACK_TRIGGER_L;
        }

        if (trackName.equals("trigger_r"))
        {
            return UIKeys.FILM_REPLAY_TRACK_TRIGGER_R;
        }

        if (trackName.equals("extra1_x"))
        {
            return UIKeys.FILM_REPLAY_TRACK_EXTRA1_X;
        }

        if (trackName.equals("extra1_y"))
        {
            return UIKeys.FILM_REPLAY_TRACK_EXTRA1_Y;
        }

        if (trackName.equals("extra2_x"))
        {
            return UIKeys.FILM_REPLAY_TRACK_EXTRA2_X;
        }

        if (trackName.equals("extra2_y"))
        {
            return UIKeys.FILM_REPLAY_TRACK_EXTRA2_Y;
        }

        if (trackName.equals("shadow_size"))
        {
            return UIKeys.FILM_REPLAY_SHADOW_SIZE;
        }

        if (trackName.equals("shadow_opacity"))
        {
            return UIKeys.FILM_REPLAY_SHADOW_OPACITY;
        }

        return null;
    }

    public static IKey resolvePropertyTrackTitle(String trackName)
    {
        IKey worldTitle = resolveWorldChannelTrackTitle(trackName);

        if (worldTitle != null)
        {
            return worldTitle;
        }

        if (trackName.equals("paint_color") || trackName.equals("paint"))
        {
            return UIKeys.FORMS_EDITORS_PAINT_COLOR;
        }

        if (trackName.equals("glow") || trackName.equals("glow_settings"))
        {
            return UIKeys.FORMS_EDITORS_GLOW;
        }

        if (trackName.equals("color_grade"))
        {
            return UIKeys.FORMS_EDITORS_COLOR_GRADE;
        }

        if (trackName.equals("color2"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_COLOR2;
        }

        if (trackName.equals("color_mode"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_COLOR_MODE;
        }

        if (trackName.equals("color"))
        {
            return UIKeys.FILM_REPLAY_TRACK_COLOR;
        }

        if (trackName.equals("repeat_x"))
        {
            return UIKeys.FORMS_EDITORS_BLOCK_REPEAT_X;
        }

        if (trackName.equals("repeat_y"))
        {
            return UIKeys.FORMS_EDITORS_BLOCK_REPEAT_Y;
        }

        if (trackName.equals("repeat_z"))
        {
            return UIKeys.FORMS_EDITORS_BLOCK_REPEAT_Z;
        }

        if (trackName.equals("repeat_center_x"))
        {
            return UIKeys.FORMS_EDITORS_BLOCK_REPEAT_CENTER_X;
        }

        if (trackName.equals("repeat_center_y"))
        {
            return UIKeys.FORMS_EDITORS_BLOCK_REPEAT_CENTER_Y;
        }

        if (trackName.equals("repeat_center_z"))
        {
            return UIKeys.FORMS_EDITORS_BLOCK_REPEAT_CENTER_Z;
        }

        if (trackName.equals("look_at"))
        {
            return UIKeys.FORMS_EDITORS_GENERAL_LOOK_AT;
        }

        if (trackName.equals("inverse_kinematics"))
        {
            return UIKeys.FORMS_EDITORS_GENERAL_INVERSE_KINEMATICS;
        }

        if (trackName.equals("illusion"))
        {
            return UIKeys.FORMS_EDITORS_GENERAL_ILLUSION;
        }

        if (trackName.equals("illusion_overlay"))
        {
            return UIKeys.FILM_REPLAY_TRACK_ILLUSION_OVERLAY;
        }

        if (trackName.startsWith("illusion_overlay"))
        {
            String suffix = trackName.substring("illusion_overlay".length());

            if (suffix.isEmpty())
            {
                return UIKeys.FILM_REPLAY_TRACK_ILLUSION_OVERLAY;
            }

            try
            {
                return UIKeys.FILM_REPLAY_TRACK_ILLUSION_OVERLAY_N.format(Integer.parseInt(suffix) + 1);
            }
            catch (Exception e)
            {
                return UIKeys.FILM_REPLAY_TRACK_ILLUSION_OVERLAY;
            }
        }

        if (trackName.equals("pose_overlay"))
        {
            return UIKeys.FILM_REPLAY_TRACK_POSE_OVERLAY;
        }

        if (trackName.startsWith("pose_overlay"))
        {
            String suffix = trackName.substring("pose_overlay".length());

            if (suffix.isEmpty())
            {
                return UIKeys.FILM_REPLAY_TRACK_POSE_OVERLAY;
            }

            try
            {
                return UIKeys.FILM_REPLAY_TRACK_POSE_OVERLAY_N.format(Integer.parseInt(suffix) + 1);
            }
            catch (Exception e)
            {
                return UIKeys.FILM_REPLAY_TRACK_POSE_OVERLAY;
            }
        }

        if (trackName.equals("transform_overlay"))
        {
            return UIKeys.FILM_REPLAY_TRACK_TRANSFORM_OVERLAY;
        }

        if (trackName.startsWith("transform_overlay"))
        {
            String suffix = trackName.substring("transform_overlay".length());

            if (suffix.isEmpty())
            {
                return UIKeys.FILM_REPLAY_TRACK_TRANSFORM_OVERLAY;
            }

            try
            {
                return UIKeys.FILM_REPLAY_TRACK_TRANSFORM_OVERLAY_N.format(Integer.parseInt(suffix) + 1);
            }
            catch (Exception e)
            {
                return UIKeys.FILM_REPLAY_TRACK_TRANSFORM_OVERLAY;
            }
        }

        if (trackName.equals("visible"))
        {
            return UIKeys.FILM_REPLAY_TRACK_VISIBLE;
        }

        if (trackName.equals("render"))
        {
            return UIKeys.FILM_REPLAY_TRACK_RENDER;
        }

        if (trackName.equals("lighting"))
        {
            return UIKeys.FILM_REPLAY_TRACK_LIGHTING;
        }

        if (trackName.equals("transform"))
        {
            return UIKeys.FILM_REPLAY_TRACK_TRANSFORM;
        }

        if (trackName.equals("anchor"))
        {
            return UIKeys.FILM_REPLAY_TRACK_ANCHOR;
        }

        if (trackName.equals("texture"))
        {
            return UIKeys.FILM_REPLAY_TRACK_TEXTURE;
        }

        if (trackName.equals("model"))
        {
            return UIKeys.FILM_REPLAY_TRACK_MODEL;
        }

        if (trackName.equals("actions"))
        {
            return UIKeys.FILM_REPLAY_TRACK_ACTIONS;
        }

        if (trackName.equals("shape_keys"))
        {
            return UIKeys.FILM_REPLAY_TRACK_SHAPE_KEYS;
        }

        if (trackName.equals("item_stack"))
        {
            return UIKeys.FILM_REPLAY_TRACK_ITEM_STACK;
        }

        if (trackName.equals("same_animation_when_dropped"))
        {
            return UIKeys.FILM_REPLAY_TRACK_SAME_ANIMATION_WHEN_DROPPED;
        }

        if (trackName.equals("pose"))
        {
            return UIKeys.FILM_REPLAY_TRACK_POSE;
        }

        return null;
    }

    public static IKey resolveTrackTitle(String trackName)
    {
        IKey title = resolvePropertyTrackTitle(trackName);

        return title != null ? title : IKey.constant(trackName);
    }

    public void updateChannelsList()
    {
        this.updateChannelsList(false);
    }

    /**
     * @param preserveKeyframeSelection when true, restore sheet / keyframe / limb after
     *                                  rebuild (Actor toggle). Other rebuilds keep the
     *                                  previous clear-selection behavior.
     */
    public void updateChannelsList(boolean preserveKeyframeSelection)
    {
        String keepActiveTab = null;

        if (preserveKeyframeSelection && this.filmPanel != null)
        {
            keepActiveTab = this.captureActiveTabForChannelsRebuild();
            this.filmPanel.beginSuppressLinkedPropertiesTabFocus();
        }

        try
        {
            this.rebuildChannelsList(preserveKeyframeSelection);
        }
        finally
        {
            if (preserveKeyframeSelection && this.filmPanel != null)
            {
                this.filmPanel.endSuppressLinkedPropertiesTabFocus();

                if (keepActiveTab != null)
                {
                    this.filmPanel.focusPanelTab(keepActiveTab);
                }
            }
        }
    }

    /**
     * Prefer the tab group shared by Properties / Replays / General so Actor toggle
     * cannot leave the user on Propiedades generales after a keyframe remount.
     */
    private String captureActiveTabForChannelsRebuild()
    {
        String propertiesId = this.filmPanel.shouldRedirectProperties() ? "unifiedEditArea" : "editArea";
        String active = this.filmPanel.getActiveTabPanelId(propertiesId);

        if (active != null)
        {
            return active;
        }

        active = this.filmPanel.getActiveTabPanelId("replaysPanel");

        if (active != null)
        {
            return active;
        }

        return this.filmPanel.getActiveTabPanelId("replaysPropertiesPanel");
    }

    private void rebuildChannelsList(boolean preserveKeyframeSelection)
    {
        UIKeyframes lastEditor = null;
        TimelineInteractionSnapshot interactionSnapshot = null;
        KeyframeEditorSelectionSnapshot selectionSnapshot = null;

        if (this.keyframeEditor != null)
        {
            lastEditor = this.keyframeEditor.view;
            interactionSnapshot = TimelineInteractionSnapshot.capture(lastEditor);

            if (preserveKeyframeSelection)
            {
                selectionSnapshot = KeyframeEditorSelectionSnapshot.capture(this.keyframeEditor);
            }

            this.keyframeEditor.removeFromParent();
        }

        if (this.replay == null)
        {
            return;
        }

        if (!this.replay.isGroup.get() && this.replay.form.get() == null)
        {
            return;
        }

        this.initializeCollapsedGroupsForReplay(this.replay);

        /* Replay keyframes */
        List<UIKeyframeSheet> sheets = new ArrayList<>();

        if (this.replay.isGroup.get())
        {
            List<String> properties = new ArrayList<>(GROUP_FORM_PROPERTIES);

            for (int i = 0; i < BBSSettings.recordingPoseTransformOverlays.get(); i++)
            {
                properties.add("transform_overlay" + i);
            }

            this.replay.properties.getOrCreate(DUMMY_FORM, "render");

            for (String key : properties)
            {
                if (UIVisibleRenderKeyframeUtils.isRenderTimelineHidden(key))
                {
                    continue;
                }

                KeyframeChannel property = this.replay.properties.getOrCreate(DUMMY_FORM, key);

                if (property != null)
                {
                    BaseValueBasic formProperty = FormUtils.getProperty(DUMMY_FORM, key);

                    if (formProperty == null && FormProperties.isColorGradeChannelKey(key))
                    {
                        formProperty = FormUtils.getProperty(DUMMY_FORM, FormProperties.colorPropertyPathForGrade(key));
                    }

                    String customTitle = this.replay.getCustomSheetTitle(key);
                    Integer customColor = this.replay.getSheetColor(key);
                    int sheetColor = customColor != null ? customColor : getColor(key);
                    IKey resolvedTitle = resolvePropertyTrackTitle(key);

                    UIKeyframeSheet sheet = customTitle != null && !customTitle.isEmpty()
                        ? new UIKeyframeSheet(key, IKey.constant(customTitle), sheetColor, false, property, formProperty)
                        : resolvedTitle != null
                            ? new UIKeyframeSheet(key, resolvedTitle, sheetColor, false, property, formProperty)
                            : new UIKeyframeSheet(sheetColor, false, property, formProperty);

                    sheets.add(withTrackIcon(sheet, key));
                }
            }

            for (String key : GROUP_SHADOW_CHANNELS)
            {
                BaseValue value = this.replay.keyframes.get(key);

                if (!(value instanceof KeyframeChannel channel))
                {
                    continue;
                }

                String customTitle = this.replay.getCustomSheetTitle(key);
                Integer customColor = this.replay.getSheetColor(key);
                int sheetColor = customColor != null ? customColor : getColor(key);
                IKey resolvedTitle = resolvePropertyTrackTitle(key);

                UIKeyframeSheet sheet = customTitle != null && !customTitle.isEmpty()
                    ? new UIKeyframeSheet(key, IKey.constant(customTitle), sheetColor, false, channel, null)
                    : resolvedTitle != null
                        ? new UIKeyframeSheet(key, resolvedTitle, sheetColor, false, channel, null)
                        : new UIKeyframeSheet(sheetColor, false, channel, null);

                sheets.add(withTrackIcon(sheet, key));
            }
        }
        else
        {
            for (String key : ReplayKeyframes.CURATED_CHANNELS)
            {
                /* Actor-only nested Damage child — omit for non-actor replays. */
                if (key.equals("invulnerable") && !this.replay.actor.get())
                {
                    continue;
                }

                BaseValue value = this.replay.keyframes.get(key);
                KeyframeChannel channel = (KeyframeChannel) value;

                String customTitle = this.replay.getCustomSheetTitle(key);
                Integer customColor = this.replay.getSheetColor(key);
                int baseColor = getColor(key);
                int sheetColor = customColor != null ? customColor : baseColor;

                IKey resolvedTitle = resolvePropertyTrackTitle(key);

                UIKeyframeSheet sheet = customTitle != null && !customTitle.isEmpty()
                    ? new UIKeyframeSheet(key, IKey.constant(customTitle), sheetColor, false, channel, null)
                    : resolvedTitle != null
                        ? new UIKeyframeSheet(key, resolvedTitle, sheetColor, false, channel, null)
                        : new UIKeyframeSheet(sheetColor, false, channel, null);

                sheets.add(withTrackIcon(sheet, key));
            }

            /* Form properties */
            Set<String> propertyPaths = new LinkedHashSet<>(FormUtils.collectPropertyPaths(this.replay.form.get()));

            /* render stays keyframable but is edited from the visible track properties */
            FormUtils.addPairedRenderPropertyPaths(propertyPaths, propertyPaths);

            for (String key : this.replay.properties.properties.keySet())
            {
                if (this.isCompatiblePropertyPath(this.replay.form.get(), key))
                {
                    propertyPaths.add(key);
                }
            }

            this.collectLimbTracks(this.replay.form.get(), propertyPaths);

            for (String key : propertyPaths)
            {
                /* Ocultar/omitir la pista tint_block_entities y huesos de item */
                if (this.isHiddenModelProperty(key))
                {
                    continue;
                }

                if (UIVisibleRenderKeyframeUtils.isRenderTimelineHidden(key))
                {
                    continue;
                }

                KeyframeChannel property = this.replay.properties.getOrCreate(this.replay.form.get(), key);

                if (property != null)
                {
                    BaseValueBasic formProperty = FormUtils.getProperty(this.replay.form.get(), key);

                    if (formProperty == null && FormProperties.isColorGradeChannelKey(key))
                    {
                        formProperty = FormUtils.getProperty(this.replay.form.get(), FormProperties.colorPropertyPathForGrade(key));
                    }

                    String customTitle = this.replay.getCustomSheetTitle(key);
                    Integer customColor = this.replay.getSheetColor(key);
                    int baseColor = getColor(key);
                    int sheetColor = customColor != null ? customColor : baseColor;

                    String title = key;
                    int colon = key.indexOf(':');

                    if (colon != -1)
                    {
                        String boneName = key.substring(colon + 1);

                        title = boneName;
                    }

                    UIKeyframeSheet sheet = customTitle != null && !customTitle.isEmpty()
                        ? new UIKeyframeSheet(key, IKey.constant(customTitle), sheetColor, false, property, formProperty)
                        : new UIKeyframeSheet(key, IKey.constant(title), sheetColor, false, property, formProperty);

                    sheets.add(withTrackIcon(sheet, key));
                }
            }

            if (this.replay.form.get() instanceof ModelForm modelForm)
            {
                List<UIKeyframeSheet> materialSheets = new ArrayList<>();

                UIReplaysEditorUtils.addMaterialTextureSheets(modelForm, this.replay.properties, materialSheets);
                sheets.addAll(materialSheets);
            }
        }

        /* Sort sheets by form path and priority */
        sheets.sort((a, b) ->
        {
            Form formA = a.property == null ? null : FormUtils.getForm(a.property);
            Form formB = b.property == null ? null : FormUtils.getForm(b.property);
            String pathA = formA == null ? "" : FormUtils.getPath(formA);
            String pathB = formB == null ? "" : FormUtils.getPath(formB);

            int pathComp = comparePathsNaturally(pathA, pathB);

            if (pathComp != 0)
            {
                return pathComp;
            }

            ToIntFunction<UIKeyframeSheet> getPriority = (sheet) ->
            {
                String id = sheet.id;
                String name = StringUtils.fileName(id);

                int curatedIndex = ReplayKeyframes.CURATED_CHANNELS.indexOf(id);

                if (curatedIndex != -1)
                {
                    /* Group shadow tracks belong at the bottom, not with world channels. */
                    if (UIReplaysEditor.this.replay != null && UIReplaysEditor.this.replay.isGroup.get()
                        && (id.equals("shadow_size") || id.equals("shadow_opacity")))
                    {
                        return id.equals("shadow_size") ? 900 : 901;
                    }

                    return -100 + curatedIndex;
                }

                if (name.equals("visible")) return 0;
                if (name.equals("render")) return 1;
                if (name.equals("lighting")) return 2;

                if (name.equals("transform")) return 10;
                if (name.startsWith("transform_overlay"))
                {
                    String suffix = name.substring("transform_overlay".length());
                    if (suffix.isEmpty()) return 11;
                    try { return 12 + Integer.parseInt(suffix); } catch (Exception e) { return 19; }
                }

                if (name.equals("pose")) return 20;
                if (name.startsWith("pose_overlay"))
                {
                    if (name.indexOf(':') != -1) return 29;
                    String suffix = name.substring("pose_overlay".length());
                    if (suffix.isEmpty()) return 21;
                    try { return 22 + Integer.parseInt(suffix); } catch (Exception e) { return 28; }
                }

                if (name.indexOf(':') != -1) return 29;

                if (name.equals("anchor")) return 30;
                if (name.equals("look_at")) return 31;
                if (name.equals("inverse_kinematics")) return 32;
                if (name.equals("illusion")) return 33;
                if (name.equals("illusion_overlay")) return 34;
                if (name.startsWith("illusion_overlay") && name.length() > "illusion_overlay".length())
                {
                    String suffix = name.substring("illusion_overlay".length());

                    try { return 35 + Integer.parseInt(suffix); } catch (Exception e) { return 50; }
                }
                if (name.equals("structure_file")) return 60;
                if (name.equals("pivot")) return 61;
                if (name.equals("biome_id")) return 62;
                if (name.equals("structure_light")) return 63;
                if (name.equals("color")) return 64;
                if (name.equals("color_grade")) return 65;
                if (name.equals("paint_color") || name.equals("paint")) return 66;
                if (name.equals("glow") || name.equals("glow_settings")) return 67;
                if (name.equals("texture")) return 68;
                if (name.equals("pbr_normal_intensity")) return 69;
                if (name.equals("pbr_specular_intensity")) return 70;
                if (name.equals("model")) return 71;
                if (name.equals("item_stack")) return 72;
                if (name.equals("block_state")) return 73;
                if (name.equals("breaking")) return 74;
                if (name.equals("repeat_x") || name.equals("repeat_y") || name.equals("repeat_z")
                    || name.equals("repeat_center_x") || name.equals("repeat_center_y") || name.equals("repeat_center_z")) return 75;

                if (name.equals("item_use_time") && sheet.property != null)
                {
                    return 76;
                }

                return 500;
            };

            int priorityA = getPriority.applyAsInt(a);
            int priorityB = getPriority.applyAsInt(b);

            if (priorityA != priorityB)
            {
                return Integer.compare(priorityA, priorityB);
            }

            if (priorityA == 29)
            {
                String boneA = a.id.substring(a.id.indexOf(':') + 1);
                String boneB = b.id.substring(b.id.indexOf(':') + 1);

                return compareNaturally(boneA, boneB);
            }

            return 0;
        });

        UIBlockRepeatKeyframeUtils.groupRepeatSheets(sheets, this.collapsedModelTracks, this::updateChannelsList);

        this.keys.clear();

        for (UIKeyframeSheet sheet : sheets)
        {
            this.keys.add(StringUtils.fileName(sheet.id));
        }

        sheets.removeIf((v) ->
        {
            for (String s : BBSSettings.disabledSheets.get())
            {
                if (v.id.equals(s) || v.id.endsWith("/" + s))
                {
                    return true;
                }
            }

            return false;
        });

        List<UIKeyframeSheet> grouped = new ArrayList<>();
        Set<String> addedGroups = new HashSet<>();

        final boolean legacyOriginalLayout = false;

        if (legacyOriginalLayout && !this.replay.isGroup.get())
        {
            Form formObj = this.replay.form.get();

            if (formObj == null)
            {
                sheets = grouped;

                return;
            }

            Form rootForm = FormUtils.getRoot(formObj);
            String rootPath = FormUtils.getPath(rootForm);
            String rootKey = this.replay.uuid.get() + ":" + rootPath;
            boolean rootExpanded = !this.collapsedModelTracks.getOrDefault(rootKey, false);

            UIKeyframeSheet rootHeader = UIKeyframeSheet.groupHeader(
                "__group__" + rootKey,
                IKey.constant(rootForm.getDisplayName()),
                Colors.LIGHTEST_GRAY & Colors.RGB,
                rootKey,
                rootExpanded,
                () ->
                {
                    this.collapsedModelTracks.put(rootKey, !this.collapsedModelTracks.getOrDefault(rootKey, false));
                    this.updateChannelsList();
                }
            );

            rootHeader.level = 0;
            grouped.add(rootHeader);

            if (rootExpanded)
            {
                FormTracks rootTracks = new FormTracks(rootForm);
                Map<String, FormTracks> subForms = new LinkedHashMap<>();
                List<UIKeyframeSheet> otherTracks = new ArrayList<>();

                for (UIKeyframeSheet sheet : sheets)
                {
                    Form form = null;

                    if (sheet.property != null)
                    {
                        form = FormUtils.getForm(sheet.property);
                    }
                    else
                    {
                        int colon = sheet.id.indexOf(':');
                        String path = "";

                        if (colon != -1)
                        {
                            String propertyPath = sheet.id.substring(0, colon);
                            int lastSlash = propertyPath.lastIndexOf('/');

                            if (lastSlash != -1)
                            {
                                path = propertyPath.substring(0, lastSlash);
                            }
                        }

                        form = FormUtils.getForm(this.replay.form.get(), path);
                    }

                    if (form != null)
                    {
                        String path = FormUtils.getPath(form);

                        if (path.equals(rootPath))
                        {
                            this.processTrack(sheet, "", 1, rootTracks.before, rootTracks.pose, rootTracks.limbs, rootTracks.overlayRoots, rootTracks.overlayLimbs, rootTracks.after);
                        }
                        else
                        {
                            if (!subForms.containsKey(path))
                            {
                                subForms.put(path, new FormTracks(form));
                            }

                            this.processTrack(sheet, "", 1, subForms.get(path).before, subForms.get(path).pose, subForms.get(path).limbs, subForms.get(path).overlayRoots, subForms.get(path).overlayLimbs, subForms.get(path).after);
                        }
                    }
                    else
                    {
                        otherTracks.add(sheet);
                    }
                }

                this.orderLimbTracks(rootTracks.form, rootTracks.limbs);
                List<UIKeyframeSheet> orderedRootOverlays = this.orderOverlayTracks(rootTracks.form, rootTracks.overlayRoots, rootTracks.overlayLimbs);

                /* Add root tracks first */
                grouped.addAll(rootTracks.before);
                grouped.addAll(rootTracks.pose);
                grouped.addAll(orderedRootOverlays);
                grouped.addAll(rootTracks.limbs);
                grouped.addAll(rootTracks.after);

                /* Add sub-form tracks next */
                for (FormTracks subForm : subForms.values())
                {
                    /* Add sub-form pose tracks renamed to form name */
                    for (UIKeyframeSheet poseSheet : subForm.pose)
                    {
                        poseSheet.title = IKey.constant(subForm.form.getDisplayName());
                        grouped.add(poseSheet);
                    }

                    this.orderLimbTracks(subForm.form, subForm.limbs);
                    List<UIKeyframeSheet> orderedSubOverlays = this.orderOverlayTracks(subForm.form, subForm.overlayRoots, subForm.overlayLimbs);

                    grouped.addAll(subForm.before);
                    grouped.addAll(orderedSubOverlays);
                    grouped.addAll(subForm.limbs);
                    grouped.addAll(subForm.after);
                }

                grouped.addAll(otherTracks);

                /* Flatten levels and disable toggles */
                for (UIKeyframeSheet sheet : grouped)
                {
                    if (sheet != rootHeader)
                    {
                        sheet.level = 1;
                        sheet.toggleExpanded = null;
                    }
                }
            }

            sheets = grouped;
        }
        else if (!this.replay.isGroup.get())
        {
            Form rootForm = FormUtils.getRoot(this.replay.form.get());
            String rootPath = FormUtils.getPath(rootForm);
            String rootKey = this.replay.uuid.get() + ":" + rootPath;
            boolean rootExpanded = !this.collapsedModelTracks.getOrDefault(rootKey, false);

            UIKeyframeSheet rootHeader = UIKeyframeSheet.groupHeader(
                "__group__" + rootKey,
                IKey.constant(rootForm.getDisplayName()),
                Colors.LIGHTEST_GRAY & Colors.RGB,
                rootKey,
                rootExpanded,
                () ->
                {
                    this.collapsedModelTracks.put(rootKey, !this.collapsedModelTracks.getOrDefault(rootKey, false));
                    this.updateChannelsList();
                }
            );

            rootHeader.level = 0;
            grouped.add(rootHeader);

            if (rootExpanded)
            {
                String worldKey = this.replay.uuid.get() + ":__world__";
                boolean worldExpanded = !this.collapsedModelTracks.getOrDefault(worldKey, false);
                UIKeyframeSheet worldHeader = UIKeyframeSheet.groupHeader(
                    "__group__" + worldKey,
                    UIKeys.FILM_REPLAY_WORLD,
                    Colors.LIGHTEST_GRAY & Colors.RGB,
                    worldKey,
                    worldExpanded,
                    () ->
                    {
                        this.collapsedModelTracks.put(worldKey, !this.collapsedModelTracks.getOrDefault(worldKey, false));
                        this.updateChannelsList();
                    }
                );

                worldHeader.level = 1;

                String vanillaPosesKey = this.replay.uuid.get() + ":__vanilla_poses__";
                boolean vanillaPosesExpanded = !this.collapsedModelTracks.getOrDefault(vanillaPosesKey, true);
                UIKeyframeSheet vanillaPosesHeader = UIKeyframeSheet.groupHeader(
                    "__group__" + vanillaPosesKey,
                    UIKeys.FILM_REPLAY_VANILLA_POSES,
                    Colors.LIGHTEST_GRAY & Colors.RGB,
                    vanillaPosesKey,
                    vanillaPosesExpanded,
                    () ->
                    {
                        this.collapsedModelTracks.put(vanillaPosesKey, !this.collapsedModelTracks.getOrDefault(vanillaPosesKey, true));
                        this.updateChannelsList();
                    }
                );

                vanillaPosesHeader.level = 2;

                String vanillaActionsKey = this.replay.uuid.get() + ":__vanilla_actions__";
                boolean vanillaActionsExpanded = !this.collapsedModelTracks.getOrDefault(vanillaActionsKey, true);
                UIKeyframeSheet vanillaActionsHeader = UIKeyframeSheet.groupHeader(
                    "__group__" + vanillaActionsKey,
                    UIKeys.FILM_REPLAY_VANILLA_ACTIONS,
                    Colors.LIGHTEST_GRAY & Colors.RGB,
                    vanillaActionsKey,
                    vanillaActionsExpanded,
                    () ->
                    {
                        this.collapsedModelTracks.put(vanillaActionsKey, !this.collapsedModelTracks.getOrDefault(vanillaActionsKey, true));
                        this.updateChannelsList();
                    }
                );

                vanillaActionsHeader.level = 2;

                String modelPropsKey = this.replay.uuid.get() + ":__model__";
                boolean modelPropsExpanded = !this.collapsedModelTracks.getOrDefault(modelPropsKey, false);
                UIKeyframeSheet modelPropsHeader = UIKeyframeSheet.groupHeader(
                    "__group__" + modelPropsKey,
                    UIKeys.FILM_REPLAY_MODEL,
                    Colors.LIGHTEST_GRAY & Colors.RGB,
                    modelPropsKey,
                    modelPropsExpanded,
                    () ->
                    {
                        this.collapsedModelTracks.put(modelPropsKey, !this.collapsedModelTracks.getOrDefault(modelPropsKey, false));
                        this.updateChannelsList();
                    }
                );

                modelPropsHeader.level = 1;

                grouped.add(worldHeader);

                List<UIKeyframeSheet> worldTracks = new ArrayList<>();
                List<UIKeyframeSheet> vanillaPoseTracks = new ArrayList<>();
                List<UIKeyframeSheet> vanillaActionTracks = new ArrayList<>();
                List<UIKeyframeSheet> modelTracksBeforePose = new ArrayList<>();
                List<UIKeyframeSheet> poseTrack = new ArrayList<>();
                List<UIKeyframeSheet> poseLimbTracks = new ArrayList<>();
                List<UIKeyframeSheet> overlayTracks = new ArrayList<>();
                List<UIKeyframeSheet> overlayLimbTracks = new ArrayList<>();
                List<UIKeyframeSheet> modelTracksAfterPose = new ArrayList<>();
                boolean hasVanillaPoses = false;
                boolean hasVanillaActions = false;
                UIKeyframeSheet invulnerableSheet = null;
                boolean actorReplay = this.replay.actor.get();

                Map<String, FormTracks> subForms = new LinkedHashMap<>();

                for (UIKeyframeSheet sheet : sheets)
                {
                    if (WORLD_CHANNELS.contains(sheet.id) && !isFormItemUseTimeTrack(sheet))
                    {
                        if (sheet.id.equals("invulnerable"))
                        {
                            if (actorReplay)
                            {
                                invulnerableSheet = sheet;
                            }

                            continue;
                        }

                        if (!this.collapsedModelTracks.getOrDefault(worldKey, false))
                        {
                            if (VANILLA_POSE_CHANNELS.contains(sheet.id))
                            {
                                hasVanillaPoses = true;

                                if (vanillaPosesExpanded)
                                {
                                    sheet.level = 3;
                                    vanillaPoseTracks.add(sheet);
                                }
                            }
                            else if (VANILLA_ACTION_CHANNELS.contains(sheet.id))
                            {
                                hasVanillaActions = true;

                                if (vanillaActionsExpanded)
                                {
                                    sheet.level = 3;
                                    vanillaActionTracks.add(sheet);
                                }
                            }
                            else
                            {
                                sheet.level = 2;

                                if (sheet.id.equals("damage") && actorReplay)
                                {
                                    String damageKey = this.replay.uuid.get() + ":damage";
                                    boolean damageExpanded = !this.collapsedModelTracks.getOrDefault(damageKey, true);

                                    sheet.expanded = damageExpanded;
                                    sheet.toggleExpanded = () ->
                                    {
                                        this.collapsedModelTracks.put(damageKey, !this.collapsedModelTracks.getOrDefault(damageKey, true));
                                        this.updateChannelsList();
                                    };
                                }

                                worldTracks.add(sheet);
                            }
                        }
                    }
                    else if (MODEL_PROPERTIES.contains(sheet.id) || isFormItemUseTimeTrack(sheet) || sheet.id.startsWith("pose") || sheet.id.startsWith("transform_overlay") || sheet.id.startsWith("illusion_overlay"))
                    {
                        if (!this.collapsedModelTracks.getOrDefault(modelPropsKey, false))
                        {
                            this.processTrack(sheet, modelPropsKey, 2, modelTracksBeforePose, poseTrack, poseLimbTracks, overlayTracks, overlayLimbTracks, modelTracksAfterPose);
                        }
                    }
                    else
                    {
                        Form form = null;

                        if (sheet.property != null)
                        {
                            form = FormUtils.getForm(sheet.property);
                        }
                        else
                        {
                            int colon = sheet.id.indexOf(':');
                            String path = "";

                            if (colon != -1)
                            {
                                String propertyPath = sheet.id.substring(0, colon);
                                int lastSlash = propertyPath.lastIndexOf('/');

                                if (lastSlash != -1)
                                {
                                    path = propertyPath.substring(0, lastSlash);
                                }
                            }

                            form = FormUtils.getForm(this.replay.form.get(), path);
                        }

                        if (form != null)
                        {
                            String path = FormUtils.getPath(form);

                            if (path.equals(rootPath))
                            {
                                if (!this.collapsedModelTracks.getOrDefault(modelPropsKey, false))
                                {
                                    this.processTrack(sheet, modelPropsKey, 2, modelTracksBeforePose, poseTrack, poseLimbTracks, overlayTracks, overlayLimbTracks, modelTracksAfterPose);
                                }

                                continue;
                            }

                            if (!subForms.containsKey(path))
                            {
                                subForms.put(path, new FormTracks(form));
                            }

                            String groupKey = this.replay.uuid.get() + ":" + path;

                            this.processTrack(sheet, groupKey, path.split("/").length, subForms.get(path).before, subForms.get(path).pose, subForms.get(path).limbs, subForms.get(path).overlayRoots, subForms.get(path).overlayLimbs, subForms.get(path).after);
                        }
                    }
                }

                if (actorReplay && invulnerableSheet != null)
                {
                    String damageKey = this.replay.uuid.get() + ":damage";

                    if (!this.collapsedModelTracks.getOrDefault(damageKey, true))
                    {
                        for (int i = 0; i < worldTracks.size(); i++)
                        {
                            if (worldTracks.get(i).id.equals("damage"))
                            {
                                invulnerableSheet.level = worldTracks.get(i).level + 1;
                                worldTracks.add(i + 1, invulnerableSheet);
                                break;
                            }
                        }
                    }
                }

                this.orderLimbTracks(rootForm, poseLimbTracks);
                List<UIKeyframeSheet> orderedOverlayTracks = this.orderOverlayTracks(rootForm, overlayTracks, overlayLimbTracks);

                this.injectColorGradeSheets(modelTracksBeforePose);
                this.injectColorGradeSheets(modelTracksAfterPose);

                if (!this.collapsedModelTracks.getOrDefault(worldKey, false))
                {
                    grouped.addAll(worldTracks);

                    if (hasVanillaPoses)
                    {
                        grouped.add(vanillaPosesHeader);
                        grouped.addAll(vanillaPoseTracks);
                    }

                    if (hasVanillaActions)
                    {
                        grouped.add(vanillaActionsHeader);
                        grouped.addAll(vanillaActionTracks);
                    }
                }

                grouped.add(modelPropsHeader);
                grouped.addAll(modelTracksBeforePose);
                grouped.addAll(poseTrack);
                grouped.addAll(poseLimbTracks);
                grouped.addAll(orderedOverlayTracks);
                grouped.addAll(modelTracksAfterPose);

                List<String> subFormPaths = new ArrayList<>(subForms.keySet());
                subFormPaths.sort(UIReplaysEditor::comparePathsNaturally);

                for (String path : subFormPaths)
                {
                    FormTracks subForm = subForms.get(path);
                    String groupKey = this.replay.uuid.get() + ":" + path;
                    int level = path.split("/").length;

                    if (addedGroups.add(groupKey))
                    {
                        boolean expanded = !this.collapsedModelTracks.getOrDefault(groupKey, false);
                        UIKeyframeSheet header = UIKeyframeSheet.groupHeader(
                            "__group__" + groupKey,
                            IKey.constant(subForm.form.getDisplayName()),
                            Colors.LIGHTEST_GRAY & Colors.RGB,
                            groupKey,
                            expanded,
                            () ->
                            {
                                this.collapsedModelTracks.put(groupKey, !this.collapsedModelTracks.getOrDefault(groupKey, false));
                                this.updateChannelsList();
                            }
                        );

                        header.level = level;
                        grouped.add(header);
                    }

                    if (!this.collapsedModelTracks.getOrDefault(groupKey, false))
                    {
                        this.orderLimbTracks(subForm.form, subForm.limbs);
                        List<UIKeyframeSheet> orderedSubOverlays = this.orderOverlayTracks(subForm.form, subForm.overlayRoots, subForm.overlayLimbs);

                        this.injectColorGradeSheets(subForm.before);
                        this.injectColorGradeSheets(subForm.after);

                        grouped.addAll(subForm.before);
                        grouped.addAll(subForm.pose);
                        grouped.addAll(subForm.limbs);
                        grouped.addAll(orderedSubOverlays);
                        grouped.addAll(subForm.after);
                    }
                }
            }

            sheets = grouped;
        }
        else if (this.replay.isGroup.get())
        {
            /* Nest paint / glow / color_grade under color (same as replay form timeline). */
            List<UIKeyframeSheet> before = new ArrayList<>();
            List<UIKeyframeSheet> pose = new ArrayList<>();
            List<UIKeyframeSheet> limbs = new ArrayList<>();
            List<UIKeyframeSheet> overlayRoots = new ArrayList<>();
            List<UIKeyframeSheet> overlayLimbs = new ArrayList<>();
            List<UIKeyframeSheet> after = new ArrayList<>();

            for (UIKeyframeSheet sheet : sheets)
            {
                this.processTrack(sheet, "", 0, before, pose, limbs, overlayRoots, overlayLimbs, after);
            }

            this.injectColorGradeSheets(before);
            this.injectColorGradeSheets(after);

            sheets = new ArrayList<>();
            sheets.addAll(before);
            sheets.addAll(pose);
            sheets.addAll(limbs);
            sheets.addAll(overlayRoots);
            sheets.addAll(overlayLimbs);
            sheets.addAll(after);
        }

        Object lastForm = null;

        for (UIKeyframeSheet sheet : sheets)
        {
            if (sheet.groupHeader)
            {
                sheet.separator = false;
                lastForm = null;
                continue;
            }

            Object form = sheet.property == null ? null : FormUtils.getForm(sheet.property);

            if (!Objects.equals(lastForm, form))
            {
                sheet.separator = true;
            }

            lastForm = form;
        }

        for (UIKeyframeSheet sheet : sheets)
        {
            if (sheet.property == null && ("shadow_size".equals(sheet.id) || "shadow_opacity".equals(sheet.id)))
            {
                sheet.separator = false;
            }
        }

        if (!sheets.isEmpty())
        {
            /* Rebuilding drops UI selection. Untouched provisional (ghost) keyframes are
             * normally removed here so they do not become permanent after collapse/reopen.
             * Actor toggle preserves selection — keep the ghost so restore can re-select it. */
            if (!preserveKeyframeSelection)
            {
                this.cleanupUntouchedAutomaticKeyframe(this.lastPickedKeyframe, null);
            }

            this.lastPickedKeyframe = null;
            this.keyframeEditor = new UIKeyframeEditor((consumer) ->
            {
                UIFilmKeyframes keyframes = new UIFilmKeyframes(this.filmPanel.cameraEditor, (keyframe) ->
                {
                    this.cleanupUntouchedAutomaticKeyframe(this.lastPickedKeyframe, keyframe);
                    this.lastPickedKeyframe = keyframe;
                    this.filmPanel.focusLinkedPropertiesTab("replayTimeline");
                    consumer.accept(keyframe);
                }).absolute();

                keyframes.setPresetsPreview(new UIReplayPresetPreview(this::getReplay));

                return keyframes;
            }).target(this.filmPanel.getReplayKeyframePropertiesHost());
            this.keyframeEditor.setUndoId("replay_keyframe_editor");
            /* Retarget after rebuild in case layout redirect (unified vs editArea) changed. */
            this.filmPanel.updateTargets();

            /* Reset */
            if (lastEditor != null)
            {
                this.keyframeEditor.view.copyViewport(lastEditor);
            }

            this.keyframeEditor.view.backgroundRenderer((context) ->
            {
                UIKeyframes view = this.keyframeEditor.view;

                context.batcher.flush();
                renderBackground(context, view, this.film.camera, 0, this.filmPanel.cameraEditor.getClip());
            });
            this.keyframeEditor.view.duration(() -> this.film.camera.calculateDuration());
            this.keyframeEditor.view.context((menu) ->
            {
                int mouseY = this.getContext().mouseY;
                UIKeyframeSheet sheet = this.keyframeEditor.view.getGraph().getSheet(mouseY);

                if (sheet != null && sheet.channel.getFactory() == KeyframeFactories.POSE)
                {
                    String trackName = StringUtils.fileName(sheet.id);

                    if (trackName.equals("pose") || trackName.startsWith("pose_overlay"))
                    {
                        Form form = sheet.property != null ? FormUtils.getForm(sheet.property) : this.replay.form.get();

                        if (form instanceof ModelForm modelForm)
                        {
                            menu.action(Icons.POSE, UIKeys.FILM_REPLAY_CONTEXT_ANIMATION_TO_KEYFRAMES, () -> this.animationToPoses(modelForm, sheet));
                        }
                        menu.action(Icons.CONVERT, UIKeys.FILM_REPLAY_CONTEXT_POSE_TO_LIMBS, () -> this.convertToLimbs(sheet));
                    }
                }

                int mouseY2 = this.getContext().mouseY;
                UIKeyframeSheet clickedSheet = this.keyframeEditor.view.getGraph().getSheet(mouseY2);
                if (clickedSheet != null && !clickedSheet.groupHeader)
                {
                    menu.action(Icons.FONT, UIKeys.FILM_REPLAY_RENAME_SHEET, () ->
                    {
                        UIRenameSheetOverlayPanel panel = new UIRenameSheetOverlayPanel(
                            UIKeys.FILM_REPLAY_RENAME_SHEET_TITLE,
                            UIKeys.FILM_REPLAY_RENAME_SHEET_MESSAGE,
                            this.replay,
                            clickedSheet.id,
                            (str, color) ->
                            {
                                this.replay.setCustomSheetTitle(clickedSheet.id, str);
                                this.replay.setSheetColor(clickedSheet.id, color);
                                this.updateChannelsList();
                            }
                        );

                        panel.text.setText(clickedSheet.title.get());
                        UIOverlay.addOverlay(this.getContext(), panel, 300, 0.25F);
                    });
                }

                if (this.keyframeEditor.view.getGraph() instanceof UIKeyframeDopeSheet && (clickedSheet == null || !clickedSheet.groupHeader))
                {
                    menu.action(Icons.FILTER, UIKeys.FILM_REPLAY_FILTER_SHEETS, () ->
                    {
                        UIKeyframeSheetFilterOverlayPanel panel = new UIKeyframeSheetFilterOverlayPanel(BBSSettings.disabledSheets.get(), this.keys);

                        UIOverlay.addOverlay(this.getContext(), panel, 240, 0.9F);

                        panel.onClose((e) ->
                        {
                            this.updateChannelsList();
                            BBSSettings.disabledSheets.set(BBSSettings.disabledSheets.get());
                        });
                    });
                }
            });

            for (UIKeyframeSheet sheet : sheets)
            {
                this.keyframeEditor.view.addSheet(sheet);
            }

            this.add(this.keyframeEditor);
            this.applyToolbarDockLayout();

            if (interactionSnapshot != null && !interactionSnapshot.isEmpty())
            {
                interactionSnapshot.restore(this.keyframeEditor.view);
            }

            if (selectionSnapshot != null && !selectionSnapshot.isEmpty())
            {
                this.restoreKeyframeSelection(selectionSnapshot);
            }
        }

        this.resize();

        if (this.keyframeEditor != null)
        {
            this.keyframeEditor.view.getDopeSheet().setTrackHeight(this.keyframeEditor.view.getDopeSheet().getTrackHeight());
        }

        if (this.keyframeEditor != null && lastEditor == null)
        {
            this.keyframeEditor.view.resetView();
        }
    }

    /**
     * Re-select sheet / keyframe / limb after {@link #updateChannelsList(boolean)}.
     * Prefers the same keyframe instance when it survived provisional cleanup.
     */
    private void restoreKeyframeSelection(KeyframeEditorSelectionSnapshot snapshot)
    {
        if (this.keyframeEditor == null || snapshot == null || snapshot.isEmpty())
        {
            return;
        }

        IUIKeyframeGraph graph = this.keyframeEditor.view.getGraph();
        UIKeyframeSheet sheet = graph.getSheet(snapshot.getSheetId());

        if (sheet == null)
        {
            return;
        }

        String bone = snapshot.getBone();
        Keyframe keyframe = snapshot.findKeyframe(sheet);

        if (keyframe != null)
        {
            graph.selectKeyframe(keyframe);
            this.applyRestoredBoneSelection(bone);

            return;
        }

        /* Untouched provisional limb keyframes are removed during rebuild — re-pick
         * the limb on the same sheet without inserting a permanent keyframe. */
        if (bone != null && !bone.isEmpty())
        {
            this.pickProperty(bone, sheet, false);
        }
    }

    private void applyRestoredBoneSelection(String bone)
    {
        if (bone == null || bone.isEmpty() || this.keyframeEditor == null)
        {
            return;
        }

        if (this.keyframeEditor.editor instanceof UIPoseKeyframeFactory poseFactory)
        {
            poseFactory.poseEditor.selectBone(bone);
        }
        else if (this.keyframeEditor.editor instanceof UILookAtKeyframeFactory lookAtFactory)
        {
            lookAtFactory.lookAtEditor.selectBone(bone);
        }
        else if (this.keyframeEditor.editor instanceof UIInverseKinematicsKeyframeFactory ikFactory)
        {
            ikFactory.ikEditor.selectBone(bone);
        }
    }

    private static boolean isHiddenModelProperty(String key)
    {
        return UIFormPropertyTrackSheets.isHiddenModelProperty(key);
    }

    private boolean isCompatiblePropertyPath(Form rootForm, String key)
    {
        if (rootForm == null || key == null || key.isEmpty())
        {
            return false;
        }

        int colon = key.indexOf(':');
        String path = colon == -1 ? key : key.substring(0, colon);

        if (FormProperties.isColorGradeChannelKey(path))
        {
            String colorPath = FormProperties.colorPropertyPathForGrade(path);
            BaseValueBasic colorProperty = FormUtils.getProperty(rootForm, colorPath);

            return colorProperty instanceof ValueColor;
        }

        BaseValueBasic property = FormUtils.getProperty(rootForm, path);

        if (property == null)
        {
            return false;
        }

        if (colon == -1)
        {
            return true;
        }

        String boneName = key.substring(colon + 1);

        return this.isCompatibleBoneProperty(property, boneName);
    }

    private boolean isCompatibleBoneProperty(BaseValueBasic property, String boneName)
    {
        if (boneName == null || boneName.isEmpty() || !(property.getParent() instanceof Form parentForm))
        {
            return false;
        }

        if (!(parentForm instanceof ModelForm modelForm))
        {
            return false;
        }

        ModelInstance model = ModelFormRenderer.getModel(modelForm);

        if (model == null)
        {
            return false;
        }

        IModel modelDef = model.model;

        if (modelDef instanceof Model cubicModel)
        {
            return cubicModel.getAllGroupKeys().contains(boneName);
        }

        Collection<BOBJBone> bones = modelDef.getAllBOBJBones();

        if (bones != null)
        {
            for (BOBJBone bone : bones)
            {
                if (boneName.equals(bone.name))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private void processTrack(UIKeyframeSheet sheet, String groupKey, int level, List<UIKeyframeSheet> before, List<UIKeyframeSheet> pose, List<UIKeyframeSheet> limbs, List<UIKeyframeSheet> overlayRoots, List<UIKeyframeSheet> overlayLimbs, List<UIKeyframeSheet> after)
    {
        sheet.level = level;
        String customTitle = this.replay.getCustomSheetTitle(sheet.id);
        int colon = sheet.id.indexOf(':');
        String trackName = StringUtils.fileName(sheet.id);

        /* Reset title in case it was changed by originalKeyframeUI mode */
        if ((customTitle == null || customTitle.isEmpty()))
        {
            if (colon != -1)
            {
                /* Bone/part limb tracks: show the real bone name (Head, Body, …), not a generic overlay label. */
                sheet.title = IKey.constant(sheet.id.substring(colon + 1));
            }
            else
            {
                IKey propertyTitle = resolvePropertyTrackTitle(trackName);

                if (propertyTitle != null)
                {
                    sheet.title = propertyTitle;
                }
                else
                {
                    Form trackForm = FormUtils.getForm(sheet.property);

                    if (trackForm != null)
                    {
                        sheet.title = IKey.constant(trackForm.getTrackName(sheet.channel.getId()));
                    }
                }
            }
        }

        String scopeKey = groupKey == null || groupKey.isEmpty() ? this.replay.uuid.get() + ":__model__" : groupKey;
        String textureParentKey = scopeKey + ":texture";
        String itemStackParentKey = scopeKey + ":item_stack";
        String illusionParentKey = scopeKey + ":illusion";
        String colorParentKey = scopeKey + ":color";
        boolean isPbrTrack = trackName.equals("pbr_normal_intensity") || trackName.equals("pbr_specular_intensity");
        boolean isColorChildTrack = trackName.equals("paint") || trackName.equals("paint_color")
            || trackName.equals("glow") || trackName.equals("glow_settings")
            || trackName.equals("color_grade")
            || trackName.equals("color2") || trackName.equals("color_mode");

        boolean isMaterialTextureTrack = PerLimbService.isMaterialTextureChannel(sheet.id);

        if (isMaterialTextureTrack)
        {
            if (this.collapsedModelTracks.getOrDefault(textureParentKey, true))
            {
                return;
            }

            sheet.level += 1;

            if (customTitle == null || customTitle.isEmpty())
            {
                PerLimbService.MaterialTexturePath matPath = PerLimbService.parseMaterialTexturePath(sheet.id);

                if (matPath != null)
                {
                    sheet.title = IKey.constant(matPath.material());
                }
            }
        }

        if (isPbrTrack)
        {
            if (this.collapsedModelTracks.getOrDefault(textureParentKey, true))
            {
                return;
            }

            sheet.level += 1;

            if ((customTitle == null || customTitle.isEmpty()) && trackName.equals("pbr_normal_intensity"))
            {
                sheet.title = UIKeys.FILM_REPLAY_TRACK_PBR_NORMAL_INTENSITY;
            }
            else if ((customTitle == null || customTitle.isEmpty()) && trackName.equals("pbr_specular_intensity"))
            {
                sheet.title = UIKeys.FILM_REPLAY_TRACK_PBR_SPECULAR_INTENSITY;
            }
        }

        if (isColorChildTrack)
        {
            if (this.collapsedModelTracks.getOrDefault(colorParentKey, true))
            {
                return;
            }

            sheet.level += 1;

            if ((customTitle == null || customTitle.isEmpty()) && trackName.equals("color_grade"))
            {
                sheet.title = UIKeys.FORMS_EDITORS_COLOR_GRADE;
            }
            else if ((customTitle == null || customTitle.isEmpty()) && trackName.equals("color2"))
            {
                sheet.title = UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_COLOR2;
            }
            else if ((customTitle == null || customTitle.isEmpty()) && trackName.equals("color_mode"))
            {
                sheet.title = UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_COLOR_MODE;
            }
        }

        if (colon != -1)
        {
            String parentId = sheet.id.substring(0, colon);
            String parentKey = this.replay.uuid.get() + ":" + parentId;

            if (this.collapsedModelTracks.getOrDefault(parentKey, true))
            {
                return;
            }

            sheet.level += 1;

            String parentTrackName = StringUtils.fileName(parentId);

            if (parentTrackName.startsWith("pose_overlay"))
            {
                overlayLimbs.add(sheet);
            }
            else
            {
                limbs.add(sheet);
            }
        }
        else if (trackName.equals("pose"))
        {
            String parentKey = this.replay.uuid.get() + ":" + sheet.id;
            boolean expanded = !this.collapsedModelTracks.getOrDefault(parentKey, true);

            sheet.expanded = expanded;
            sheet.toggleExpanded = () ->
            {
                boolean collapsing = !this.collapsedModelTracks.getOrDefault(parentKey, true);

                this.collapsedModelTracks.put(parentKey, collapsing);

                /* Closing pose limbs should discard untouched preview keyframes. */
                if (collapsing)
                {
                    this.cleanupUntouchedAutomaticKeyframe(this.lastPickedKeyframe, null);
                    this.lastPickedKeyframe = null;
                }

                this.updateChannelsList();
            };

            pose.add(sheet);
        }
        else if (trackName.startsWith("pose_overlay"))
        {
            String parentKey = this.replay.uuid.get() + ":" + sheet.id;
            boolean expanded = !this.collapsedModelTracks.getOrDefault(parentKey, true);

            sheet.expanded = expanded;
            sheet.toggleExpanded = () ->
            {
                boolean collapsing = !this.collapsedModelTracks.getOrDefault(parentKey, true);

                this.collapsedModelTracks.put(parentKey, collapsing);

                if (collapsing)
                {
                    this.cleanupUntouchedAutomaticKeyframe(this.lastPickedKeyframe, null);
                    this.lastPickedKeyframe = null;
                }

                this.updateChannelsList();
            };

            overlayRoots.add(sheet);
        }
        else if (trackName.equals("texture"))
        {
            boolean expanded = !this.collapsedModelTracks.getOrDefault(textureParentKey, true);

            sheet.expanded = expanded;
            sheet.toggleExpanded = () ->
            {
                this.collapsedModelTracks.put(textureParentKey, !this.collapsedModelTracks.getOrDefault(textureParentKey, true));
                this.updateChannelsList();
            };

            this.addTrackByPriority(trackName, before, after, sheet);
        }
        else if (trackName.equals("color"))
        {
            boolean expanded = !this.collapsedModelTracks.getOrDefault(colorParentKey, true);

            sheet.expanded = expanded;
            sheet.toggleExpanded = () ->
            {
                this.collapsedModelTracks.put(colorParentKey, !this.collapsedModelTracks.getOrDefault(colorParentKey, true));
                this.updateChannelsList();
            };

            this.addTrackByPriority(trackName, before, after, sheet);
        }
        else if (trackName.equals("item_stack"))
        {
            boolean expanded = !this.collapsedModelTracks.getOrDefault(itemStackParentKey, true);

            sheet.expanded = expanded;
            sheet.toggleExpanded = () ->
            {
                this.collapsedModelTracks.put(itemStackParentKey, !this.collapsedModelTracks.getOrDefault(itemStackParentKey, true));
                this.updateChannelsList();
            };

            this.addTrackByPriority(trackName, before, after, sheet);
        }
        else if (isFormItemUseTimeTrack(sheet))
        {
            if (this.collapsedModelTracks.getOrDefault(itemStackParentKey, true))
            {
                return;
            }

            sheet.level += 1;

            if (customTitle == null || customTitle.isEmpty())
            {
                sheet.title = UIKeys.FILM_REPLAY_TRACK_ITEM_USE_TIME_LABEL;
            }

            after.add(sheet);
        }
        else if (trackName.startsWith("transform_overlay") || trackName.equals("transform"))
        {
            before.add(sheet);
        }
        else if (trackName.equals("illusion"))
        {
            boolean expanded = !this.collapsedModelTracks.getOrDefault(illusionParentKey, true);

            sheet.expanded = expanded;
            sheet.toggleExpanded = () ->
            {
                this.collapsedModelTracks.put(illusionParentKey, !this.collapsedModelTracks.getOrDefault(illusionParentKey, true));
                this.updateChannelsList();
            };

            after.add(sheet);
        }
        else if (this.isIllusionOverlayTrack(trackName))
        {
            if (this.collapsedModelTracks.getOrDefault(illusionParentKey, true))
            {
                return;
            }

            sheet.level += 1;
            after.add(sheet);
        }
        else
        {
            this.addTrackByPriority(trackName, before, after, sheet);
        }

        if (customTitle != null && !customTitle.isEmpty())
        {
            sheet.title = IKey.constant(customTitle);
        }
    }

    private void injectColorGradeSheets(List<UIKeyframeSheet> tracks)
    {
        for (int i = 0; i < tracks.size(); i++)
        {
            UIKeyframeSheet sheet = tracks.get(i);
            String name = StringUtils.fileName(sheet.id);

            if (!name.equals("color") || sheet.groupHeader || !sheet.expanded)
            {
                continue;
            }

            Form form = sheet.property == null ? null : FormUtils.getForm(sheet.property);

            if (form instanceof LabelForm || form instanceof TrailForm)
            {
                continue;
            }

            int insertAt = i + 1;
            boolean hasGrade = false;

            while (insertAt < tracks.size())
            {
                String child = StringUtils.fileName(tracks.get(insertAt).id);

                if (child.equals("paint") || child.equals("paint_color") || child.equals("glow") || child.equals("glow_settings"))
                {
                    insertAt++;
                }
                else if (child.equals("color_grade"))
                {
                    hasGrade = true;
                    insertAt++;
                }
                else
                {
                    break;
                }
            }

            if (!hasGrade)
            {
                tracks.add(insertAt, this.createColorGradeSheet(sheet));
                insertAt++;
            }

            String scopePrefix = sheet.id.equals("color") ? "" : sheet.id.substring(0, sheet.id.length() - "color".length());

            for (int j = insertAt; j < tracks.size(); j++)
            {
                UIKeyframeSheet candidate = tracks.get(j);
                String childName = StringUtils.fileName(candidate.id);

                if (childName.equals("color2") || childName.equals("color_mode"))
                {
                    String candidatePrefix = candidate.id.equals(childName) ? "" : candidate.id.substring(0, candidate.id.length() - childName.length());

                    if (scopePrefix.equals(candidatePrefix))
                    {
                        tracks.remove(j);
                        tracks.add(insertAt, candidate);
                        insertAt++;
                        j--;
                    }
                }
            }

            i = insertAt - 1;
        }
    }

    private UIKeyframeSheet createColorGradeSheet(UIKeyframeSheet colorSheet)
    {
        String gradeId;

        if (colorSheet.id.equals("color"))
        {
            gradeId = "color_grade";
        }
        else if (colorSheet.id.endsWith("/color"))
        {
            gradeId = colorSheet.id.substring(0, colorSheet.id.length() - "color".length()) + "color_grade";
        }
        else
        {
            gradeId = colorSheet.id + "/color_grade";
        }

        Form form = this.replay == null ? null : this.replay.form.get();
        KeyframeChannel gradeChannel = this.replay == null
            ? null
            : this.replay.properties.getOrCreate(form != null ? form : DUMMY_FORM, gradeId);

        if (gradeChannel == null)
        {
            gradeChannel = colorSheet.channel;
        }

        UIKeyframeSheet grade = new UIKeyframeSheet(
            gradeId,
            UIKeys.FORMS_EDITORS_COLOR_GRADE,
            getColor("color_grade"),
            false,
            gradeChannel,
            colorSheet.property
        );

        grade.level = colorSheet.level + 1;
        grade.icon(Icons.FAVORITE);

        return grade;
    }

    private boolean isIllusionOverlayTrack(String trackName)
    {
        if (trackName.equals("illusion_overlay"))
        {
            return true;
        }

        return trackName.startsWith("illusion_overlay") && trackName.length() > "illusion_overlay".length();
    }

    private void addTrackByPriority(String trackName, List<UIKeyframeSheet> before, List<UIKeyframeSheet> after, UIKeyframeSheet sheet)
    {
        int poseIndex = MODEL_PROPERTIES.indexOf("pose");
        int currentIndex = MODEL_PROPERTIES.indexOf(trackName);

        if (WORLD_CHANNELS.contains(trackName) && !isFormItemUseTimeTrack(sheet))
        {
            /* Folder shadow tracks stay at the end of the group timeline. */
            if (this.replay != null && this.replay.isGroup.get()
                && (trackName.equals("shadow_size") || trackName.equals("shadow_opacity")))
            {
                after.add(sheet);

                return;
            }

            before.add(sheet);

            return;
        }

        if (currentIndex != -1 && currentIndex < poseIndex)
        {
            before.add(sheet);
        }
        else
        {
            after.add(sheet);
        }
    }

    private List<UIKeyframeSheet> orderOverlayTracks(Form form, List<UIKeyframeSheet> overlayRoots, List<UIKeyframeSheet> overlayLimbs)
    {
        List<UIKeyframeSheet> ordered = new ArrayList<>();

        if (overlayRoots.isEmpty() && overlayLimbs.isEmpty())
        {
            return ordered;
        }

        Set<UIKeyframeSheet> used = new HashSet<>();

        for (UIKeyframeSheet root : overlayRoots)
        {
            ordered.add(root);
            used.add(root);

            List<UIKeyframeSheet> limbs = new ArrayList<>();
            String prefix = root.id + ":";

            for (UIKeyframeSheet limb : overlayLimbs)
            {
                if (limb.id.startsWith(prefix))
                {
                    limbs.add(limb);
                    used.add(limb);
                }
            }

            this.orderLimbTracks(form, limbs);
            ordered.addAll(limbs);
        }

        for (UIKeyframeSheet limb : overlayLimbs)
        {
            if (!used.contains(limb))
            {
                ordered.add(limb);
            }
        }

        return ordered;
    }

    private static class FormTracks
    {
        public final Form form;
        public final List<UIKeyframeSheet> before = new ArrayList<>();
        public final List<UIKeyframeSheet> pose = new ArrayList<>();
        public final List<UIKeyframeSheet> limbs = new ArrayList<>();
        public final List<UIKeyframeSheet> overlayRoots = new ArrayList<>();
        public final List<UIKeyframeSheet> overlayLimbs = new ArrayList<>();
        public final List<UIKeyframeSheet> after = new ArrayList<>();

        public FormTracks(Form form)
        {
            this.form = form;
        }
    }

    private static int comparePathsNaturally(String a, String b)
    {
        if (a.equals(b))
        {
            return 0;
        }

        String[] left = a.split("/");
        String[] right = b.split("/");
        int min = Math.min(left.length, right.length);

        for (int i = 0; i < min; i++)
        {
            int cmp = compareNaturally(left[i], right[i]);

            if (cmp != 0)
            {
                return cmp;
            }
        }

        return Integer.compare(left.length, right.length);
    }

    private static int compareNaturally(String a, String b)
    {
        int i = 0;
        int j = 0;

        while (i < a.length() && j < b.length())
        {
            char ca = a.charAt(i);
            char cb = b.charAt(j);

            if (Character.isDigit(ca) && Character.isDigit(cb))
            {
                int startI = i;
                int startJ = j;

                while (i < a.length() && Character.isDigit(a.charAt(i)))
                {
                    i++;
                }

                while (j < b.length() && Character.isDigit(b.charAt(j)))
                {
                    j++;
                }

                String numberA = a.substring(startI, i);
                String numberB = b.substring(startJ, j);
                int numericCmp = compareNumericStrings(numberA, numberB);

                if (numericCmp != 0)
                {
                    return numericCmp;
                }

                continue;
            }

            int charCmp = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));

            if (charCmp != 0)
            {
                return charCmp;
            }

            i++;
            j++;
        }

        return Integer.compare(a.length(), b.length());
    }

    private static int compareNumericStrings(String a, String b)
    {
        int ai = 0;
        int bi = 0;

        while (ai < a.length() && a.charAt(ai) == '0')
        {
            ai++;
        }

        while (bi < b.length() && b.charAt(bi) == '0')
        {
            bi++;
        }

        String trimmedA = a.substring(ai);
        String trimmedB = b.substring(bi);

        if (trimmedA.length() != trimmedB.length())
        {
            return Integer.compare(trimmedA.length(), trimmedB.length());
        }

        int cmp = trimmedA.compareTo(trimmedB);

        if (cmp != 0)
        {
            return cmp;
        }

        return Integer.compare(a.length(), b.length());
    }

    private void animationToPoses(ModelForm modelForm, UIKeyframeSheet sheet)
    {
        ModelInstance model = ModelFormRenderer.getModel(modelForm);

        if (model != null)
        {
            UIAnimationToPoseOverlayPanel.IUIAnimationPoseCallback cb = (animationKey, onlyKeyframes, length, step) ->
                this.animationToPoseKeyframes(modelForm, sheet, animationKey, onlyKeyframes, length, step);

            UIOverlay.addOverlay(this.getContext(), new UIAnimationToPoseOverlayPanel(cb, modelForm, sheet), 260, 260);
        }
    }

    public void animationToPoseKeyframes(ModelForm modelForm, UIKeyframeSheet sheet, String animationKey, boolean onlyKeyframes, int length, int step)
    {
        ModelInstance model = ModelFormRenderer.getModel(modelForm);
        Animation animation = model.animations.get(animationKey);

        if (animation != null)
        {
            int current = this.filmPanel.getCursor();
            IEntity entity = this.filmPanel.getController().getCurrentEntity();

            this.keyframeEditor.view.getDopeSheet().clearSelection();

            if (onlyKeyframes)
            {
                List<Float> list = this.getTicks(animation);

                for (float i : list)
                {
                    this.fillAnimationPose(sheet, i, model, entity, animation, current);
                }
            }
            else
            {
                for (int i = 0; i < length; i += step)
                {
                    this.fillAnimationPose(sheet, i, model, entity, animation, current);
                }
            }

            this.keyframeEditor.view.getDopeSheet().pickSelected();
        }
    }

    private List<Float> getTicks(Animation animation)
    {
        Set<Float> integers = new HashSet<>();

        for (AnimationPart value : animation.parts.values())
        {
            for (KeyframeChannel<MolangExpression> channel : value.channels)
            {
                for (Keyframe<MolangExpression> keyframe : channel.getKeyframes())
                {
                    integers.add(keyframe.getTick());
                }
            }
        }

        ArrayList<Float> ticks = new ArrayList<>(integers);

        Collections.sort(ticks);

        return ticks;
    }

    private void convertToLimbs(UIKeyframeSheet sheet)
    {
        List<Keyframe> selected = new ArrayList<>(sheet.selection.getSelected());

        if (selected.isEmpty())
        {
            return;
        }

        Form form = this.replay.form.get();

        if (form == null)
        {
            return;
        }

        Form rootForm = FormUtils.getRoot(form);

        Set<String> boneNames = new HashSet<>();

        for (Keyframe kf : selected)
        {
            Pose pose = (Pose) kf.getValue();

            if (pose != null)
            {
                boneNames.addAll(pose.transforms.keySet());
            }
        }

        BaseValue.edit(this.replay, IValueListener.FLAG_UNMERGEABLE, (r) ->
        {
            Set<Float> convertedTicks = new HashSet<>();

            for (Keyframe kf : selected)
            {
                Pose pose = (Pose) sheet.channel.interpolate(kf.getTick());

                if (pose == null)
                {
                    continue;
                }

                convertedTicks.add(kf.getTick());

                for (String boneName : boneNames)
                {
                    PoseTransform transform = pose.transforms.get(boneName);

                    if (transform == null)
                    {
                        transform = new PoseTransform();
                    }

                    String key = sheet.id + ":" + boneName;

                    KeyframeChannel<Transform> channel = this.replay.properties.getOrCreate(rootForm, key);

                    if (channel != null)
                    {
                        int index = channel.insert(kf.getTick(), transform.copy());
                        Keyframe<Transform> newKf = channel.get(index);

                        newKf.copyOverExtra(kf);
                    }
                }
            }

            if (!convertedTicks.isEmpty())
            {
                for (int i = sheet.channel.getList().size() - 1; i >= 0; i--)
                {
                    Keyframe existing = (Keyframe) sheet.channel.getList().get(i);

                    for (Float tick : convertedTicks)
                    {
                        if (Math.abs(existing.getTick() - tick) < 0.0001F)
                        {
                            sheet.channel.remove(i);
                            break;
                        }
                    }
                }
            }

            this.replay.properties.cleanUp();
        });

        this.replay.properties.resetProperties(this.replay.form.get());

        if (this.filmPanel.getUndoHandler() != null)
        {
            this.filmPanel.getUndoHandler().submitUndo();
        }

        this.updateChannelsList();
    }

    private void fillAnimationPose(UIKeyframeSheet sheet, float i, ModelInstance model, IEntity entity, Animation animation, int current)
    {
        model.model.resetPose();
        model.model.apply(entity, animation, i, 1F, 0F, false);

        int insert = sheet.channel.insert(current + i, model.model.createPose());

        sheet.selection.add(insert);
    }

    private boolean isBonePickProperty(String propertyId)
    {
        return propertyId.equals("pose") || propertyId.startsWith("pose_overlay")
            || (BBSFeatures.isFormIkLookAtUiEnabled() && BBSFeatures.isFormIkLookAtProperty(propertyId));
    }

    /**
     * Bone stencil can hit any visible replay. Switch to the owning replay first so
     * pose/look-at sheets and the gizmo target the correct actor.
     */
    private boolean ensureReplayForForm(Form form)
    {
        Replay owner = this.findReplayForForm(form);

        if (owner == null)
        {
            return this.replay != null;
        }

        if (owner != this.replay)
        {
            this.setReplay(owner, true, false);
        }

        return this.keyframeEditor != null;
    }

    private Replay findReplayForForm(Form form)
    {
        if (form == null || this.filmPanel.getData() == null)
        {
            return null;
        }

        Form root = FormUtils.getRoot(form);

        for (Replay replay : this.filmPanel.getData().replays.getList())
        {
            Form replayForm = replay.form.get();

            if (replayForm != null && FormUtils.getRoot(replayForm) == root)
            {
                return replay;
            }
        }

        return null;
    }

    public void pickForm(Form form, String bone)
    {
        if (form == null || bone.isEmpty())
        {
            return;
        }

        this.focusReplayTimelineForPick();

        if (!this.ensureReplayForForm(form))
        {
            return;
        }

        this.ensureTracksVisibleForFormBone(form, bone);

        if (this.keyframeEditor == null)
        {
            return;
        }

        String formPath = FormUtils.getPath(form);
        String propertyPath = null;
        IUIKeyframeGraph graph = this.keyframeEditor.view.getGraph();
        Keyframe selected = graph.getSelected();

        if (selected != null)
        {
            UIKeyframeSheet sheet = graph.getSheet(selected);

            if (sheet != null)
            {
                String sheetId = sheet.id;
                int colon = sheetId.indexOf(':');
                String pathWithProperty = colon != -1 ? sheetId.substring(0, colon) : sheetId;
                String propertyId = StringUtils.fileName(pathWithProperty);

                if (StringUtils.parentPath(pathWithProperty).equals(formPath) && this.isBonePickProperty(propertyId))
                {
                    propertyPath = pathWithProperty;
                }
            }
        }

        if (propertyPath == null)
        {
            UIKeyframeSheet lastSheet = graph.getLastSheet();

            if (lastSheet != null)
            {
                String sheetId = lastSheet.id;
                int colon = sheetId.indexOf(':');
                String pathWithProperty = colon != -1 ? sheetId.substring(0, colon) : sheetId;
                String propertyId = StringUtils.fileName(pathWithProperty);

                if (StringUtils.parentPath(pathWithProperty).equals(formPath) && this.isBonePickProperty(propertyId))
                {
                    propertyPath = pathWithProperty;
                }
            }
        }

        if (propertyPath == null)
        {
            String activeOverlayPath = null;
            String posePath = null;
            String lookAtPath = null;
            String inverseKinematicsPath = null;
            double minPoseDist = Double.MAX_VALUE;
            double minOverlayDist = Double.MAX_VALUE;
            double minLookAtDist = Double.MAX_VALUE;
            double minInverseKinematicsDist = Double.MAX_VALUE;
            int currentTick = this.filmPanel.getCursor();

            for (UIKeyframeSheet sheet : graph.getSheets())
            {
                String sheetId = sheet.id;
                int colon = sheetId.indexOf(':');
                String pathWithProperty = colon != -1 ? sheetId.substring(0, colon) : sheetId;

                if (StringUtils.parentPath(pathWithProperty).equals(formPath))
                {
                    String propertyId = StringUtils.fileName(pathWithProperty);

                    if (propertyId.equals("pose"))
                    {
                        posePath = pathWithProperty;

                        if (!sheet.channel.isEmpty())
                        {
                            KeyframeSegment segment = sheet.channel.find(currentTick);

                            if (segment != null)
                            {
                                minPoseDist = Math.abs(segment.getClosest().getTick() - currentTick);
                            }
                        }
                    }
                    else if (propertyId.startsWith("pose_overlay"))
                    {
                        if (!sheet.channel.isEmpty())
                        {
                            KeyframeSegment segment = sheet.channel.find(currentTick);

                            if (segment != null)
                            {
                                double dist = Math.abs(segment.getClosest().getTick() - currentTick);

                                if (activeOverlayPath == null)
                                {
                                    activeOverlayPath = pathWithProperty;
                                    minOverlayDist = dist;
                                }
                            }
                        }
                    }
                    else if (propertyId.equals("look_at"))
                    {
                        lookAtPath = pathWithProperty;

                        if (!sheet.channel.isEmpty())
                        {
                            KeyframeSegment segment = sheet.channel.find(currentTick);

                            if (segment != null)
                            {
                                minLookAtDist = Math.abs(segment.getClosest().getTick() - currentTick);
                            }
                        }
                    }
                    else if (propertyId.equals("inverse_kinematics"))
                    {
                        inverseKinematicsPath = pathWithProperty;

                        if (!sheet.channel.isEmpty())
                        {
                            KeyframeSegment segment = sheet.channel.find(currentTick);

                            if (segment != null)
                            {
                                minInverseKinematicsDist = Math.abs(segment.getClosest().getTick() - currentTick);
                            }
                        }
                    }
                }
            }

            if (activeOverlayPath != null && minOverlayDist < minPoseDist)
            {
                propertyPath = activeOverlayPath;
            }
            else if (inverseKinematicsPath != null && minInverseKinematicsDist <= minPoseDist && this.keyframeEditor.editor instanceof UIInverseKinematicsKeyframeFactory)
            {
                propertyPath = inverseKinematicsPath;
            }
            else if (lookAtPath != null && minLookAtDist <= minPoseDist && this.keyframeEditor.editor instanceof UILookAtKeyframeFactory)
            {
                propertyPath = lookAtPath;
            }
            else if (posePath != null)
            {
                propertyPath = posePath;
            }
            else if (BBSFeatures.isFormIkLookAtUiEnabled() && lookAtPath != null)
            {
                propertyPath = lookAtPath;
            }
            else if (BBSFeatures.isFormIkLookAtUiEnabled() && inverseKinematicsPath != null)
            {
                propertyPath = inverseKinematicsPath;
            }
        }

        if (propertyPath == null)
        {
            if (BBSFeatures.isFormIkLookAtUiEnabled() && this.keyframeEditor.editor instanceof UIInverseKinematicsKeyframeFactory)
            {
                propertyPath = StringUtils.combinePaths(formPath, "inverse_kinematics");
            }
            else if (BBSFeatures.isFormIkLookAtUiEnabled() && this.keyframeEditor.editor instanceof UILookAtKeyframeFactory)
            {
                propertyPath = StringUtils.combinePaths(formPath, "look_at");
            }
            else
            {
                propertyPath = StringUtils.combinePaths(formPath, "pose");
            }
        }

        this.pickProperty(bone, propertyPath, false);
    }

    public void pickFormProperty(Form form, String bone)
    {
        if (form == null)
        {
            return;
        }

        this.focusReplayTimelineForPick();

        if (!this.ensureReplayForForm(form))
        {
            return;
        }

        this.ensureTracksVisibleForFormBone(form, bone);

        String path = FormUtils.getPath(form);
        boolean shift = Window.isShiftPressed();
        ContextMenuManager manager = new ContextMenuManager();

        manager.autoKeys();

        for (BaseValueBasic formProperty : form.getAllMap().values())
        {
            if (!formProperty.isVisible())
            {
                continue;
            }

            manager.action(getIcon(formProperty.getId()), IKey.constant(formProperty.getId()), () ->
            {
                this.pickProperty(bone, StringUtils.combinePaths(path, formProperty.getId()), shift);
            });
        }

        this.getContext().replaceContextMenu(manager.create());
    }

    private void pickProperty(String bone, String key, boolean insert)
    {
        IUIKeyframeGraph graph = this.keyframeEditor.view.getGraph();
        Keyframe selected = graph.getSelected();
        UIKeyframeSheet activeSheet = selected != null ? graph.getSheet(selected) : null;

        if (activeSheet != null)
        {
            String id = activeSheet.id;
            int colon = id.indexOf(':');
            String baseId = colon != -1 ? id.substring(0, colon) : id;
            String boneId = colon != -1 ? id.substring(colon + 1) : null;

            if (baseId.equals(key))
            {
                if (boneId == null || boneId.equals(bone))
                {
                    this.pickProperty(bone, activeSheet, insert);

                    return;
                }
            }
        }

        /* Limb track only when pose is already expanded (limbs visible). */
        if (bone != null && !bone.isEmpty())
        {
            String limbTrackId = key + ":" + bone;

            for (UIKeyframeSheet sheet : this.keyframeEditor.view.getGraph().getSheets())
            {
                if (sheet.id.equals(limbTrackId))
                {
                    this.pickProperty(bone, sheet, insert);

                    return;
                }
            }
        }

        for (UIKeyframeSheet sheet : this.keyframeEditor.view.getGraph().getSheets())
        {
            if (sheet.id.equals(key))
            {
                this.pickProperty(bone, sheet, insert);

                return;
            }
        }

        if (bone != null && !bone.isEmpty() && this.keyframeEditor != null)
        {
            if (this.keyframeEditor.editor instanceof UIPoseKeyframeFactory poseFactory)
            {
                if (Window.isCtrlPressed())
                {
                    poseFactory.poseEditor.addBoneToSelection(bone);
                }
                else
                {
                    poseFactory.poseEditor.selectBone(bone);
                }
            }
            else if (this.keyframeEditor.editor instanceof UILookAtKeyframeFactory lookAtFactory)
            {
                lookAtFactory.lookAtEditor.selectBone(bone);
            }
            else if (this.keyframeEditor.editor instanceof UIInverseKinematicsKeyframeFactory ikFactory)
            {
                ikFactory.ikEditor.selectBone(bone);
            }
        }
    }

    private void pickProperty(String bone, UIKeyframeSheet sheet, boolean insert)
    {
        int tick = this.filmPanel.getRunner().ticks;

        if (insert)
        {
            Keyframe keyframe = this.keyframeEditor.view.getGraph().addKeyframe(sheet, tick, null);

            this.keyframeEditor.view.getGraph().selectKeyframe(keyframe);

            return;
        }

        Keyframe provisionalKeyframe = this.ensureAutomaticLimbKeyframe(sheet, bone, tick);

        if (provisionalKeyframe == null)
        {
            provisionalKeyframe = this.ensureAutomaticPoseKeyframe(sheet, bone, tick);
        }

        KeyframeSegment segment = sheet.channel.find(tick);
        Keyframe closest = null;

        if (segment != null)
        {
            closest = segment.getClosest();
        }
        else if (!sheet.channel.isEmpty())
        {
            closest = sheet.channel.get(0);
        }

        if (closest == null)
        {
            closest = provisionalKeyframe;
        }

        if (closest != null)
        {
            if (this.keyframeEditor.view.getGraph().getSelected() != closest)
            {
                this.keyframeEditor.view.getGraph().selectKeyframe(closest);
            }

            if (this.keyframeEditor.editor instanceof UIPoseKeyframeFactory poseFactory)
            {
                if (Window.isCtrlPressed())
                {
                    poseFactory.poseEditor.addBoneToSelection(bone);
                }
                else
                {
                    poseFactory.poseEditor.selectBone(bone);
                }
            }
            else if (this.keyframeEditor.editor instanceof UILookAtKeyframeFactory lookAtFactory)
            {
                lookAtFactory.lookAtEditor.selectBone(bone);
            }
            else if (this.keyframeEditor.editor instanceof UIInverseKinematicsKeyframeFactory ikFactory)
            {
                ikFactory.ikEditor.selectBone(bone);
            }

            this.filmPanel.setCursor((int) closest.getTick());
        }
        else if (this.keyframeEditor.editor instanceof UIPoseKeyframeFactory poseFactory)
        {
            if (Window.isCtrlPressed())
            {
                poseFactory.poseEditor.addBoneToSelection(bone);
            }
            else
            {
                poseFactory.poseEditor.selectBone(bone);
            }
        }
        else if (this.keyframeEditor.editor instanceof UILookAtKeyframeFactory lookAtFactory)
        {
            lookAtFactory.lookAtEditor.selectBone(bone);
        }
        else if (this.keyframeEditor.editor instanceof UIInverseKinematicsKeyframeFactory ikFactory)
        {
            ikFactory.ikEditor.selectBone(bone);
        }
    }

    private boolean isPoseTrackId(String id)
    {
        String property = StringUtils.fileName(id);

        return property.equals("pose") || property.startsWith("pose_overlay");
    }

    /** Main pose / pose_overlay tracks and their {@code pose:bone} limb channels. */
    private boolean isProvisionalPoseChannelId(String id)
    {
        if (id == null || id.isEmpty())
        {
            return false;
        }

        int colon = id.indexOf(':');
        String propertyId = colon == -1 ? id : id.substring(0, colon);

        return this.isPoseTrackId(propertyId);
    }

    private boolean isProvisionalAutomaticKeyframe(Keyframe keyframe, String channelId)
    {
        if (keyframe == null || keyframe.getColor() == null)
        {
            return false;
        }

        if (keyframe.getColor().a >= 0.99F)
        {
            return false;
        }

        return this.isProvisionalPoseChannelId(channelId);
    }

    private boolean isProvisionalAutomaticKeyframe(Keyframe keyframe, UIKeyframeSheet sheet)
    {
        return sheet != null && this.isProvisionalAutomaticKeyframe(keyframe, sheet.id);
    }

    private void cleanupUntouchedAutomaticKeyframe(Keyframe previous, Keyframe current)
    {
        if (previous == null || previous == current || this.keyframeEditor == null)
        {
            return;
        }

        IUIKeyframeGraph graph = this.keyframeEditor.view.getGraph();
        UIKeyframeSheet sheet = graph.getSheet(previous);

        if (!this.isProvisionalAutomaticKeyframe(previous, sheet))
        {
            return;
        }

        if (sheet.channel.removeSilently(previous))
        {
            graph.pickSelected();
        }
    }

    /**
     * Drop untouched auto-inserted pose/limb previews so they are never written to disk.
     * Semi-transparent color is the provisional marker; serialization stores RGB only and
     * reloads opaque, which made previews look like confirmed keyframes after reopen.
     * <p>
     * Only call when leaving the film context (close / switch film). Running this during
     * periodic autosave removed the live channel from {@link FormProperties} via
     * {@code cleanUp()} while timeline sheets still referenced it, so later pose edits
     * mutated an orphaned channel and the form stopped updating until world reload.
     */
    public void discardUntouchedAutomaticKeyframes()
    {
        if (this.film == null)
        {
            return;
        }

        boolean removed = false;

        for (Replay replay : this.film.replays.getList())
        {
            removed |= this.discardUntouchedAutomaticKeyframes(replay);
        }

        this.lastPickedKeyframe = null;

        if (removed && this.keyframeEditor != null)
        {
            for (UIKeyframeSheet sheet : this.keyframeEditor.view.getGraph().getSheets())
            {
                sheet.selection.clear();
            }

            this.keyframeEditor.view.getGraph().pickKeyframe(null);
        }
    }

    private boolean discardUntouchedAutomaticKeyframes(Replay replay)
    {
        if (replay == null)
        {
            return false;
        }

        boolean removed = false;
        List<Keyframe> pending = new ArrayList<>();

        for (KeyframeChannel<?> channel : replay.properties.properties.values())
        {
            if (!this.isProvisionalPoseChannelId(channel.getId()))
            {
                continue;
            }

            pending.clear();

            for (Object entry : channel.getList())
            {
                Keyframe keyframe = (Keyframe) entry;

                if (this.isProvisionalAutomaticKeyframe(keyframe, channel.getId()))
                {
                    pending.add(keyframe);
                }
            }

            for (Keyframe keyframe : pending)
            {
                removed |= channel.removeSilently(keyframe);
            }
        }

        /* Skip cleanUp while this replay's sheets are still mounted — removing an empty
         * channel from the map orphans the sheet's KeyframeChannel reference. */
        if (removed && !(this.keyframeEditor != null && this.replay == replay))
        {
            replay.properties.cleanUp();
        }

        return removed;
    }

    /**
     * When the pose group is already expanded, bone picks use {@code pose:bone}
     * limb sheets. Insert a transparent Transform keyframe at the playhead so the
     * property panel can open; first edit promotes it, leaving it untouched removes it.
     */
    private Keyframe ensureAutomaticLimbKeyframe(UIKeyframeSheet sheet, String bone, int tick)
    {
        if (sheet == null || !BBSSettings.autoKeyframes.get())
        {
            return null;
        }

        if (bone == null || bone.isEmpty())
        {
            return null;
        }

        int colon = sheet.id.indexOf(':');

        if (colon == -1)
        {
            return null;
        }

        String propertyId = sheet.id.substring(0, colon);
        String boneId = sheet.id.substring(colon + 1);

        if (!this.isPoseTrackId(propertyId) || !bone.equals(boneId))
        {
            return null;
        }

        for (Object o : sheet.channel.getList())
        {
            Keyframe keyframe = (Keyframe) o;

            if (Math.abs(keyframe.getTick() - tick) < 0.0001F)
            {
                return keyframe;
            }
        }

        KeyframeSegment segment = sheet.channel.find(tick);
        Keyframe source = null;
        Transform transform = null;

        if (segment != null)
        {
            source = segment.a;
            Object interpolated = segment.createInterpolated();

            if (interpolated instanceof Transform value)
            {
                transform = (Transform) sheet.channel.getFactory().copy(value);
            }
        }

        if (transform == null)
        {
            transform = (Transform) sheet.channel.getFactory().createEmpty();
        }

        int index = sheet.channel.insert(tick, transform);
        Keyframe keyframe = (Keyframe) sheet.channel.get(index);

        if (keyframe != null)
        {
            if (source != null)
            {
                keyframe.getInterpolation().copy(source.getInterpolation());
            }

            keyframe.setColor(Color.rgba(Colors.setA(sheet.color, 0.35F)));
        }

        return keyframe;
    }

    /**
     * When the pose group is collapsed (limb sheets hidden) and the main pose
     * track has no keyframes yet, insert a transparent Pose keyframe so limb
     * picks can open the pose property panel without expanding limbs.
     */
    private Keyframe ensureAutomaticPoseKeyframe(UIKeyframeSheet sheet, String bone, int tick)
    {
        if (sheet == null || !BBSSettings.autoKeyframes.get())
        {
            return null;
        }

        if (bone == null || bone.isEmpty())
        {
            return null;
        }

        if (sheet.id.indexOf(':') != -1 || !this.isPoseTrackId(sheet.id))
        {
            return null;
        }

        if (!sheet.channel.isEmpty())
        {
            return null;
        }

        Object poseValue = sheet.channel.getFactory().createEmpty();

        if (sheet.property != null && sheet.property.get() instanceof Pose formPose)
        {
            poseValue = sheet.channel.getFactory().copy(formPose);
        }

        int index = sheet.channel.insert(tick, poseValue);
        Keyframe keyframe = (Keyframe) sheet.channel.get(index);

        if (keyframe != null)
        {
            keyframe.setColor(Color.rgba(Colors.setA(sheet.color, 0.35F)));
        }

        return keyframe;
    }

    public boolean clickViewport(UIContext context, Area area)
    {
        if (this.filmPanel.isFlying())
        {
            return false;
        }

        if (this.filmPanel.getController().tryPickHoveredReplay(context))
        {
            return true;
        }

        if (Window.isAltPressed() && context.mouseButton == 0)
        {
            return false;
        }

        StencilFormFramebuffer stencil = this.filmPanel.getController().getStencil();
        UIPropTransform editableTransform = UIReplaysEditorUtils.getEditableTransform(this.keyframeEditor);

        this.gizmoDragArea = area;

        if (context.mouseButton == 0 && this.gizmoController.tryStartHandleDrag(context, editableTransform))
        {
            /* Gizmo drag advances from UIPropTransform.render(); show the linked
             * Properties tab so that widget is mounted/visible while dragging. */
            this.filmPanel.focusLinkedPropertiesTab("replayTimeline");

            return true;
        }

        if (stencil.hasPicked())
        {
            Pair<Form, String> pair = stencil.getPicked();

            if (pair != null && context.mouseButton < 2)
            {
                boolean allowPick = true;

                if (BBSSettings.replayMarkedBonesOnly.get() && !Window.isShiftPressed() && pair.a instanceof ModelForm modelForm)
                {
                    ModelInstance model = ModelFormRenderer.getModel(modelForm);
                    String poseGroup = model == null ? modelForm.model.get() : model.poseGroup;

                    if (poseGroup == null || poseGroup.isEmpty())
                    {
                        poseGroup = model == null ? modelForm.model.get() : model.id;
                    }

                    if (UIPoseEditor.hasMarkedBones(poseGroup) && !UIPoseEditor.isMarkedBone(poseGroup, pair.b))
                    {
                        allowPick = false;
                    }
                }

                if (allowPick)
                {
                    if (pair.a == null)
                    {
                        return false;
                    }

                    if (context.mouseButton == 0)
                    {
                        if (Window.isShiftPressed()) offerHierarchy(this.getContext(), pair.a, pair.b, (bone) -> this.pickForm(pair.a, bone));
                        else this.pickForm(pair.a, pair.b);

                        return true;
                    }
                    else if (context.mouseButton == 1)
                    {
                        this.pickFormProperty(pair.a, pair.b);

                        return true;
                    }
                }
            }
        }
        else if (context.mouseButton == 1 && this.filmPanel.getData() != null && !this.viewportInteraction.isActive())
        {
            Vector3d vec = this.rayTraceViewportBlock(context, area);

            if (vec != null)
            {
                this.openViewportReplayContextMenu(context, vec);

                return true;
            }
        }

        if (stencil.hasPicked() && context.mouseButton == 2 && Window.isAltPressed()
            && this.filmPanel.getController().getPovMode() == UIFilmController.CAMERA_MODE_ORBIT
            && this.filmPanel.getController().orbit.enabled
            && !this.filmPanel.getController().orbit.isAnimating()
            && !this.viewportInteraction.isActive())
        {
            int stencilIndex = stencil.getIndex() - Gizmo.STENCIL_HANDLE_MAX - 1;
            List<Replay> replays = this.filmPanel.getData().replays.getList();

            if (stencilIndex >= 0 && stencilIndex < replays.size())
            {
                Replay replay = replays.get(stencilIndex);

                this.setReplay(replay, true, true);
                this.filmPanel.focusAfterAltReplayPick();

                return true;
            }
        }

        if (area.isInside(context)
            && this.filmPanel.isFlying()
            && this.filmPanel.getController().getPovMode() == UIFilmController.CAMERA_MODE_ORBIT
            && this.filmPanel.getController().orbit.enabled
            && !this.filmPanel.getController().orbit.isAnimating())
        {
            int button = this.filmPanel.getController().orbit.canStart(context);

            if (button >= 0)
            {
                this.filmPanel.getController().orbit.start(context);

                return true;
            }
        }

        return false;
    }

    @Override
    public StencilFormFramebuffer getGizmoStencil()
    {
        return this.filmPanel.getController().getStencil();
    }

    public void updateGizmoHover()
    {
        this.gizmoController.updateHover();
    }

    @Override
    public void prepareGizmoDrag(UIPropTransform transform)
    {
        if (transform == null || this.gizmoDragArea == null)
        {
            return;
        }

        FilmPoseGizmoDrag.prepare(this.filmPanel, this.gizmoDragArea, transform);
    }

    public void stopGizmoDrag()
    {
        Pair<Form, String> pendingPick = this.gizmoController.consumePendingTrackballClick();

        if (pendingPick != null && pendingPick.a != null)
        {
            if (Window.isShiftPressed())
            {
                offerHierarchy(this.getContext(), pendingPick.a, pendingPick.b, (bone) -> this.pickForm(pendingPick.a, bone));
            }
            else
            {
                this.pickForm(pendingPick.a, pendingPick.b);
            }
        }

        this.gizmoController.stop();
    }

    public void close()
    {
        this.discardUntouchedAutomaticKeyframes();

        if (this.film != null)
        {
            lastFilm = this.film.getId();
            lastReplay = this.replays.replays.getIndex();
        }
    }

    public void teleport()
    {
        if (this.filmPanel.getData() == null)
        {
            return;
        }

        Replay replay = this.getReplay();

        if (replay != null)
        {
            int tick = this.filmPanel.getCursor();
            double x = replay.keyframes.x.interpolate(tick);
            double y = replay.keyframes.y.interpolate(tick);
            double z = replay.keyframes.z.interpolate(tick);
            float yaw = replay.keyframes.yaw.interpolate(tick).floatValue();
            float headYaw = replay.keyframes.headYaw.interpolate(tick).floatValue();
            float bodyYaw = replay.keyframes.bodyYaw.interpolate(tick).floatValue();
            float pitch = replay.keyframes.pitch.interpolate(tick).floatValue();
            ClientPlayerEntity player = MinecraftClient.getInstance().player;

            PlayerUtils.teleport(x, y, z, headYaw, pitch);
            player.setYaw(yaw);
            player.setHeadYaw(headYaw);
            player.setBodyYaw(bodyYaw);
            player.setPitch(pitch);
        }
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        List<Integer> selection = DataStorageUtils.intListFromData(data.getList("selection"));
        List<Integer> currentIndices = this.replays.replays.getCurrentIndices();
        Replay replay = CollectionUtils.getSafe(this.film.replays.getList(), data.getInt("replay"));

        if (this.replay != replay)
        {
            this.setReplay(replay, true, false);
        }

        currentIndices.clear();
        currentIndices.addAll(selection);
        this.replays.replays.update();
    }

    @Override
    public void setVisible(boolean visible)
    {
        if (!visible)
        {
            this.toolbar.cancelDockDrag();
        }

        super.setVisible(visible);

        if (this.keyframeEditor != null)
        {
            this.keyframeEditor.setVisible(visible);
        }
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        int index = this.film.replays.getList().indexOf(this.getReplay());

        data.putInt("replay", index);
        data.put("selection", DataStorageUtils.intListToData(this.replays.replays.getCurrentIndices()));
    }

    public void clearSelection()
    {
        if (this.keyframeEditor != null && this.keyframeEditor.view != null)
        {
            this.keyframeEditor.view.getGraph().clearSelection();
            this.keyframeEditor.view.getGraph().pickSelected();
        }
    }
}
