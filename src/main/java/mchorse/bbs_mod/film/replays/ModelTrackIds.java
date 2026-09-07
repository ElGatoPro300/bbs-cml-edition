package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared ids / allocation for Minecut Model timeline track palette drops.
 * WORLD channels are intentionally excluded.
 */
public final class ModelTrackIds
{
    public static final String VISIBLE = "visible";
    public static final String RENDER = "render";
    public static final String LIGHTING = "lighting";
    public static final String RENDER_DEPTH = "render_depth";
    public static final String TRANSFORM = "transform";
    public static final String TRANSFORM_OVERLAY = "transform_overlay";
    public static final String SHAKE = "shake";
    public static final String POSE = "pose";
    public static final String POSE_OVERLAY = "pose_overlay";
    public static final String ANCHOR = "anchor";
    public static final String LOOK_AT = "look_at";
    public static final String INVERSE_KINEMATICS = "inverse_kinematics";
    public static final String ILLUSION = "illusion";
    public static final String ILLUSION_OVERLAY = "illusion_overlay";
    public static final String ILLUSION_TRANSFORM = "illusion_transform";
    public static final String ILLUSION_TRANSFORM_OVERLAY = "illusion_transform_overlay";
    public static final String COLOR = "color";
    public static final String COLOR_OVERLAY = "color_overlay";
    public static final String OPACITY = "opacity";
    public static final String GLOW = "glow";
    public static final String TEXTURE = "texture";
    public static final String BILLBOARD = "billboard";
    public static final String CROP = "crop";
    public static final String OFFSET_X = "offsetX";
    public static final String OFFSET_Y = "offsetY";
    public static final String ROTATION = "rotation";
    public static final String PAINT = "paint";
    public static final String PAINT_COLOR = "paint_color";
    public static final String PAINT_OVERLAY = "paint_overlay";
    public static final String ACTIONS = "actions";
    public static final String SHAPE_KEYS = "shape_keys";
    public static final String MODEL = "model";
    public static final String MODEL_TRANSFORM = "modelTransform";
    public static final String SAME_ANIMATION_WHEN_DROPPED = "same_animation_when_dropped";
    public static final String BLOCK_STATE = "block_state";
    public static final String ITEM_STACK = "item_stack";
    public static final String SETTINGS = "settings";
    public static final String PAUSED = "paused";
    public static final String FREQUENCY = "frequency";
    public static final String COUNT = "count";
    public static final String STRUCTURE_FILE = "structure_file";
    public static final String BIOME_ID = "biome_id";
    public static final String EMIT_LIGHT = "emit_light";
    public static final String LIGHT_INTENSITY = "light_intensity";
    public static final String STRUCTURE_LIGHT = "structure_light";
    public static final String ENABLED = "enabled";
    public static final String LEVEL = "level";
    public static final String EFFECT = "effect";
    public static final String COLOR_GRADE = "color_grade";

    /** Palette row types that allocate a new overlay instance on each drop. */
    public static final Set<String> STACKABLE_PALETTE_TYPES = Set.of(
        TRANSFORM_OVERLAY, POSE_OVERLAY, COLOR_OVERLAY, PAINT_OVERLAY, ILLUSION_OVERLAY
    );

    /**
     * Base palette ids that auto-stack: first drop = base track, later drops = overlays.
     * These rows stay visible in the Tracks menu so overlays can be added again.
     */
    public static final Set<String> AUTO_OVERLAY_BASES = Set.of(
        TRANSFORM, POSE, COLOR, PAINT, ILLUSION
    );

    /** Singleton palette rows — only one line per replay. */
    public static final List<String> SINGLETON_PALETTE_TYPES = Collections.unmodifiableList(Arrays.asList(
        VISIBLE, RENDER, TRANSFORM, POSE, LIGHTING, RENDER_DEPTH, SHAKE, ANCHOR, LOOK_AT, INVERSE_KINEMATICS,
        ILLUSION, COLOR, COLOR_GRADE, OPACITY, GLOW, TEXTURE, PAINT, ACTIONS,
        SHAPE_KEYS, MODEL, MODEL_TRANSFORM, SAME_ANIMATION_WHEN_DROPPED, BLOCK_STATE, ITEM_STACK,
        SETTINGS, PAUSED, FREQUENCY, COUNT, STRUCTURE_FILE, BIOME_ID, EMIT_LIGHT, LIGHT_INTENSITY,
        STRUCTURE_LIGHT, ENABLED, LEVEL, EFFECT, BILLBOARD, CROP, OFFSET_X, OFFSET_Y, ROTATION,
        "breaking", "repeat_x", "block_entity_nbt", "item_use_time", "length", "loop",
        "velocity", "scattering_yaw", "scattering_pitch", "offset_x", "offset_y", "offset_z", "local"
    ));

    /**
     * Full Tracks-menu order (grouped for the Minecut palette UI).
     * Overlay rows are omitted — Pose / Transform / Color / Paint / Illusion auto-stack.
     * Billboard / crop / offset / rotation appear only when the form exposes them
     * (Billboard + Extruded forms via {@link #isApplicableToForm}).
     */
    public static final List<String> PALETTE_CORE = Collections.unmodifiableList(Arrays.asList(
        VISIBLE, TRANSFORM, POSE, SHAPE_KEYS, ACTIONS, MODEL, TEXTURE,
        BILLBOARD, CROP, OFFSET_X, OFFSET_Y, ROTATION
    ));

    public static final List<String> PALETTE_APPEARANCE = Collections.unmodifiableList(Arrays.asList(
        LIGHTING, COLOR, PAINT, GLOW, "pbr_normal_intensity", "pbr_specular_intensity"
    ));

    public static final List<String> PALETTE_MOTION = Collections.unmodifiableList(Arrays.asList(
        SHAKE, ANCHOR, INVERSE_KINEMATICS, LOOK_AT, ILLUSION
    ));

    /** @deprecated Kept for callers; Minecut Tracks UI no longer uses Animation/Form tabs. */
    public static final List<String> PALETTE_ANIMATION = Collections.unmodifiableList(Arrays.asList(
        ACTIONS, SHAPE_KEYS, MODEL, MODEL_TRANSFORM, SAME_ANIMATION_WHEN_DROPPED, PAUSED, SETTINGS
    ));

    /**
     * Form-specific Core rows (filtered by {@link #isApplicableToForm}). Also the seed list
     * for discovering addon tracks (irl light, …) that are not in Core/Appearance/Motion.
     */
    public static final List<String> PALETTE_FORM = Collections.unmodifiableList(Arrays.asList(
        /* Block — Repeat is a single family row (repeat_x); axes/centers are not palette rows. */
        BLOCK_STATE, "block_entity_nbt", "breaking", "repeat_x",
        /* Item */
        ITEM_STACK, "item_use_time", SAME_ANIMATION_WHEN_DROPPED,
        /* Particle */
        SETTINGS, PAUSED, "velocity", COUNT, FREQUENCY,
        "scattering_yaw", "scattering_pitch", "offset_x", "offset_y", "offset_z",
        /* Trail / video */
        "length", "loop",
        /* Structure */
        STRUCTURE_FILE, BIOME_ID, STRUCTURE_LIGHT,
        /* Light */
        ENABLED, LEVEL, EFFECT,
        /* Misc form extras */
        COLOR_GRADE, "local"
    ));

    /** Actor / world channels that must never be treated as Model/Core form tracks. */
    private static final Set<String> WORLD_ONLY_LEAVES = Set.of(
        "x", "y", "z", "vx", "vy", "vz", "yaw", "pitch", "headyaw", "bodyyaw",
        "grounded", "damage", "death_time", "active_hand", "fall", "sneaking",
        "riding", "sprinting", "swimming", "flying", "fall_flying", "crawling",
        "climbing", "blocking", "sleeping", "riptide",
        "item_main_hand", "item_off_hand", "item_head", "item_chest", "item_legs", "item_feet",
        "selected_slot", "stick_lx", "stick_ly", "stick_rx", "stick_ry",
        "trigger_l", "trigger_r", "extra1_x", "extra1_y", "extra2_x", "extra2_y",
        "shadow_size", "shadow_opacity", "fire"
    );

    private ModelTrackIds()
    {
    }

    /** Overlay family base for a palette/base id, or {@code null} if not auto-stackable. */
    public static String overlayFamilyOf(String paletteType)
    {
        if (paletteType == null)
        {
            return null;
        }

        if (paletteType.equals(POSE) || paletteType.equals(POSE_OVERLAY) || paletteType.startsWith(POSE_OVERLAY))
        {
            return POSE_OVERLAY;
        }

        if (paletteType.equals(TRANSFORM) || paletteType.equals(TRANSFORM_OVERLAY) || paletteType.startsWith(TRANSFORM_OVERLAY))
        {
            return TRANSFORM_OVERLAY;
        }

        if (paletteType.equals(COLOR) || paletteType.equals(COLOR_OVERLAY) || paletteType.startsWith(COLOR_OVERLAY))
        {
            return COLOR_OVERLAY;
        }

        if (paletteType.equals(PAINT) || paletteType.equals(PAINT_OVERLAY) || paletteType.startsWith(PAINT_OVERLAY)
            || paletteType.equals(PAINT_COLOR))
        {
            return PAINT_OVERLAY;
        }

        if (paletteType.equals(ILLUSION) || paletteType.equals(ILLUSION_OVERLAY) || paletteType.startsWith(ILLUSION_OVERLAY))
        {
            return ILLUSION_OVERLAY;
        }

        return null;
    }

    /** Form-path prefix of a track id ({@code "0/pose"} → {@code "0"}), or empty for root. */
    public static String formPathOf(String trackId)
    {
        if (trackId == null || trackId.isEmpty())
        {
            return "";
        }

        String path = trackId.contains(":") ? trackId.substring(0, trackId.indexOf(':')) : trackId;
        int slash = path.lastIndexOf('/');

        return slash >= 0 ? path.substring(0, slash) : "";
    }

    /** Leaf channel id ({@code "0/pose_overlay"} → {@code "pose_overlay"}). */
    public static String leafTrackId(String trackId)
    {
        if (trackId == null || trackId.isEmpty())
        {
            return trackId;
        }

        String path = trackId.contains(":") ? trackId.substring(0, trackId.indexOf(':')) : trackId;
        int slash = path.lastIndexOf('/');

        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    /** Qualify a leaf palette/channel id under a form path ({@code "0"} + {@code "pose"} → {@code "0/pose"}). */
    public static String qualifyTrackId(String formPath, String leafId)
    {
        if (leafId == null)
        {
            return null;
        }

        if (formPath == null || formPath.isEmpty())
        {
            return leafId;
        }

        return StringUtils.combinePaths(formPath, leafId);
    }

    /**
     * Leaves scoped to a form path: root = ids without {@code /};
     * body part {@code "0"} = {@code "0/pose"}, {@code "0/transform"}, …
     */
    public static List<String> leavesUnder(List<String> order, String formPath)
    {
        List<String> leaves = new ArrayList<>();

        if (order == null)
        {
            return leaves;
        }

        String normalized = formPath == null ? "" : formPath;

        for (String id : order)
        {
            if (id == null || id.contains(":"))
            {
                continue;
            }

            if (normalized.isEmpty())
            {
                if (!id.contains("/"))
                {
                    leaves.add(id);
                }
            }
            else
            {
                String prefix = normalized + "/";

                if (id.startsWith(prefix))
                {
                    String rest = id.substring(prefix.length());

                    if (!rest.isEmpty() && !rest.contains("/"))
                    {
                        leaves.add(rest);
                    }
                }
            }
        }

        return leaves;
    }

    /**
     * Resolve a Tracks-palette id into the concrete channel to insert.
     * Auto-overlay bases stack into overlays when the base is already present;
     * true singletons return {@code null} when already present.
     */
    public static String resolveFromPalette(List<String> order, String paletteType)
    {
        return resolveFromPalette(order, paletteType, "");
    }

    public static String resolveFromPalette(List<String> order, String paletteType, String formPath)
    {
        if (paletteType == null)
        {
            return null;
        }

        List<String> scoped = leavesUnder(order, formPath);
        String leaf;

        if (isStackablePaletteType(paletteType))
        {
            leaf = allocateNextOverlayId(scoped, paletteType);
        }
        else if (AUTO_OVERLAY_BASES.contains(paletteType) && isPaletteTypeAlreadyPresent(order, paletteType, formPath))
        {
            String overlayBase = overlayFamilyOf(paletteType);

            leaf = overlayBase == null ? null : allocateNextOverlayId(scoped, overlayBase);
        }
        else if (isPaletteTypeAlreadyPresent(order, paletteType, formPath))
        {
            return null;
        }
        else
        {
            leaf = paletteType;
        }

        return leaf == null ? null : qualifyTrackId(formPath, leaf);
    }

    /**
     * True when the selected replay already has this palette row's base track
     * (further drops would only create overlays / duplicates).
     */
    public static boolean isPaletteTypeAlreadyPresent(List<String> order, String paletteType)
    {
        return isPaletteTypeAlreadyPresent(order, paletteType, "");
    }

    public static boolean isPaletteTypeAlreadyPresent(List<String> order, String paletteType, String formPath)
    {
        if (order == null || paletteType == null)
        {
            return false;
        }

        return leavesUnder(order, formPath).contains(paletteType);
    }

    /** True when the form exposes this property (Pose only on models, Color only if registered, …). */
    public static boolean isApplicableToForm(Form form, String trackId)
    {
        if (form == null || trackId == null || trackId.isEmpty())
        {
            return false;
        }

        return FormUtils.getProperty(form, leafTrackId(trackId)) != null;
    }

    /**
     * Palette row is shown when the form supports it. True singletons hide once
     * present; Pose / Transform / Color / Paint / Illusion stay visible for overlays.
     */
    public static boolean canAddFromPalette(Replay replay, String paletteType)
    {
        return canAddFromPalette(replay, paletteType, "");
    }

    public static boolean canAddFromPalette(Replay replay, String paletteType, String formPath)
    {
        if (replay == null || paletteType == null || replay.isGroup.get())
        {
            return false;
        }

        Form root = replay.form.get();
        Form form = (formPath == null || formPath.isEmpty()) ? root : FormUtils.getForm(root, formPath);

        if (!isApplicableToForm(form, paletteType))
        {
            return false;
        }

        if (AUTO_OVERLAY_BASES.contains(paletteType) || isStackablePaletteType(paletteType))
        {
            return true;
        }

        replay.ensureModelTrackOrder();

        if ("repeat_x".equals(paletteType))
        {
            List<String> leaves = leavesUnder(replay.getModelTrackOrder(), formPath);

            return !leaves.contains("repeat_x") && !leaves.contains("repeat_y") && !leaves.contains("repeat_z");
        }

        return !isPaletteTypeAlreadyPresent(replay.getModelTrackOrder(), paletteType, formPath);
    }

    /** True for transform_overlay / pose_overlayN / color_overlay / … (not the base track). */
    public static boolean isOverlayTrackId(String trackId)
    {
        if (trackId == null || trackId.isEmpty())
        {
            return false;
        }

        String leaf = leafTrackId(trackId);
        String overlayBase = overlayFamilyOf(leaf);

        if (overlayBase == null)
        {
            return false;
        }

        return leaf.equals(overlayBase) || leaf.startsWith(overlayBase);
    }

    /**
     * Logical default insert index for a click-add (near related family / MODEL property order).
     */
    public static int defaultInsertIndex(List<String> order, String trackId)
    {
        return defaultInsertIndex(order, trackId, formPathOf(trackId));
    }

    public static int defaultInsertIndex(List<String> order, String trackId, String formPath)
    {
        if (order == null || trackId == null)
        {
            return 0;
        }

        String leaf = leafTrackId(trackId);
        List<String> scoped = leavesUnder(order, formPath);
        int scopedAt = defaultInsertIndexScoped(scoped, leaf);

        return mapScopedInsertToOrder(order, formPath, scopedAt);
    }

    private static int defaultInsertIndexScoped(List<String> order, String trackId)
    {
        if (order == null || trackId == null)
        {
            return 0;
        }

        String overlayBase = overlayFamilyOf(trackId);

        if (overlayBase != null && (trackId.equals(overlayBase) || trackId.startsWith(overlayBase)))
        {
            String base = overlayBase.equals(POSE_OVERLAY) ? POSE
                : overlayBase.equals(TRANSFORM_OVERLAY) ? TRANSFORM
                : overlayBase.equals(COLOR_OVERLAY) ? COLOR
                : overlayBase.equals(PAINT_OVERLAY) ? PAINT
                : ILLUSION;
            int last = -1;

            for (int i = 0; i < order.size(); i++)
            {
                String id = order.get(i);

                if (id.equals(base) || id.equals(overlayBase) || id.startsWith(overlayBase))
                {
                    last = i;
                }
            }

            return last < 0 ? order.size() : last + 1;
        }

        /* Prefer MODEL_PROPERTIES-like order: visible → transform → pose → rest */
        List<String> preferred = Arrays.asList(
            VISIBLE, RENDER, LIGHTING, RENDER_DEPTH, TRANSFORM, POSE, SHAKE, COLOR, COLOR_GRADE, OPACITY,
            PAINT, GLOW, TEXTURE, ANCHOR, LOOK_AT, INVERSE_KINEMATICS, ILLUSION, ILLUSION_TRANSFORM,
            ACTIONS, SHAPE_KEYS, MODEL, MODEL_TRANSFORM, SAME_ANIMATION_WHEN_DROPPED, PAUSED, SETTINGS,
            BLOCK_STATE, ITEM_STACK, FREQUENCY, COUNT, STRUCTURE_FILE, BIOME_ID, EMIT_LIGHT,
            LIGHT_INTENSITY, STRUCTURE_LIGHT, ENABLED, LEVEL, EFFECT
        );
        int pref = preferred.indexOf(trackId);

        if (pref < 0)
        {
            return order.size();
        }

        for (int i = 0; i < order.size(); i++)
        {
            int other = preferred.indexOf(order.get(i));

            if (other > pref)
            {
                return i;
            }
        }

        return order.size();
    }

    private static int mapScopedInsertToOrder(List<String> order, String formPath, int scopedAt)
    {
        String normalized = formPath == null ? "" : formPath;
        int seen = 0;
        int lastInScope = -1;

        for (int i = 0; i < order.size(); i++)
        {
            if (!isUnderFormPath(order.get(i), normalized))
            {
                continue;
            }

            if (seen == scopedAt)
            {
                return i;
            }

            lastInScope = i;
            seen++;
        }

        return lastInScope < 0 ? order.size() : lastInScope + 1;
    }

    private static boolean isUnderFormPath(String trackId, String formPath)
    {
        if (trackId == null || trackId.contains(":"))
        {
            return false;
        }

        if (formPath == null || formPath.isEmpty())
        {
            return !trackId.contains("/");
        }

        String prefix = formPath + "/";

        if (!trackId.startsWith(prefix))
        {
            return false;
        }

        String rest = trackId.substring(prefix.length());

        return !rest.isEmpty() && !rest.contains("/");
    }

    public static List<String> buildDefaultsFromSettings()
    {
        LinkedHashSet<String> order = new LinkedHashSet<>();

        /* Transform above Pose — matches classic MODEL property order. */
        if (BBSSettings.recordingDefaultTrackTransform != null && BBSSettings.recordingDefaultTrackTransform.get())
        {
            order.add(TRANSFORM);
        }

        if (BBSSettings.recordingDefaultTrackPose != null && BBSSettings.recordingDefaultTrackPose.get())
        {
            order.add(POSE);
        }

        if (BBSSettings.recordingDefaultTrackVisible != null && BBSSettings.recordingDefaultTrackVisible.get())
        {
            order.add(VISIBLE);
        }

        if (BBSSettings.recordingDefaultTrackColor != null && BBSSettings.recordingDefaultTrackColor.get())
        {
            order.add(COLOR);
        }

        if (BBSSettings.recordingDefaultTrackOpacity != null && BBSSettings.recordingDefaultTrackOpacity.get())
        {
            order.add(OPACITY);
        }

        if (order.isEmpty())
        {
            order.add(TRANSFORM);
            order.add(POSE);
        }

        int transformOverlays = BBSSettings.getTransformOverlaysCount();
        int poseOverlays = BBSSettings.getPoseOverlaysCount();
        int colorOverlays = BBSSettings.getColorOverlaysCount();
        int illusionOverlays = BBSSettings.getIllusionOverlaysCount();

        for (int i = 0; i < transformOverlays; i++)
        {
            order.add(overlayId(TRANSFORM_OVERLAY, i));
        }

        for (int i = 0; i < poseOverlays; i++)
        {
            order.add(overlayId(POSE_OVERLAY, i));
        }

        for (int i = 0; i < colorOverlays; i++)
        {
            order.add(overlayId(COLOR_OVERLAY, i));
        }

        for (int i = 0; i < illusionOverlays; i++)
        {
            order.add(overlayId(ILLUSION_OVERLAY, i));
        }

        return new ArrayList<>(order);
    }

    /**
     * Overlay instance ids: first is bare {@code base}, then {@code base0}, {@code base1}, …
     */
    public static String overlayId(String base, int instanceIndex)
    {
        if (instanceIndex <= 0)
        {
            return base;
        }

        return base + (instanceIndex - 1);
    }

    public static int overlayInstanceIndex(String trackId, String base)
    {
        if (trackId == null || base == null)
        {
            return -1;
        }

        if (trackId.equals(base))
        {
            return 0;
        }

        if (!trackId.startsWith(base))
        {
            return -1;
        }

        String suffix = trackId.substring(base.length());

        if (suffix.isEmpty())
        {
            return 0;
        }

        try
        {
            return Integer.parseInt(suffix) + 1;
        }
        catch (NumberFormatException ignored)
        {
            return -1;
        }
    }

    /** Numbered form field index: bare → -1, {@code base0} → 0, … */
    public static int overlayNumberedIndex(String trackId, String base)
    {
        int instance = overlayInstanceIndex(trackId, base);

        if (instance < 0)
        {
            return -2;
        }

        return instance - 1;
    }

    public static String allocateNextOverlayId(List<String> order, String base)
    {
        if (order == null || !order.contains(base))
        {
            return base;
        }

        for (int i = 0; ; i++)
        {
            String id = base + i;

            if (!order.contains(id))
            {
                return id;
            }
        }
    }

    public static boolean isStackablePaletteType(String paletteType)
    {
        return paletteType != null && STACKABLE_PALETTE_TYPES.contains(paletteType);
    }

    public static boolean isModelTrackId(String id)
    {
        if (id == null || id.isEmpty())
        {
            return false;
        }

        String path = id.contains(":") ? id.substring(0, id.indexOf(':')) : id;
        String leaf = leafTrackId(path);
        String lower = leaf.toLowerCase(Locale.ROOT);

        if (id.contains(":"))
        {
            return lower.startsWith("pose");
        }

        if (WORLD_ONLY_LEAVES.contains(lower))
        {
            return false;
        }

        /* Body-part paths and any remaining form / addon leaf (irl light, …). */
        return true;
    }

    public static boolean isLimbChildOfOrdered(String path, List<String> order)
    {
        if (path == null || !path.contains(":") || order == null)
        {
            return false;
        }

        /* Require the full parent path (e.g. "0/pose"), not a bare root "pose". */
        String root = path.substring(0, path.indexOf(':'));

        return order.contains(root);
    }

    /** PBR intensity tracks that nest under Texture in the timeline. */
    public static boolean isTextureChildTrack(String path)
    {
        if (path == null || path.isEmpty())
        {
            return false;
        }

        return leafTrackId(path).equals("pbr_normal_intensity")
            || leafTrackId(path).equals("pbr_specular_intensity");
    }

    /**
     * Ensure the form exposes the property for {@code trackId}, then create the keyframe channel.
     * {@code trackId} may be path-qualified ({@code 0/pose}); {@code rootForm} is the replay root.
     */
    public static KeyframeChannel ensureChannel(Replay replay, Form rootForm, String trackId)
    {
        if (replay == null || rootForm == null || trackId == null)
        {
            return null;
        }

        String formPath = formPathOf(trackId);
        String leaf = leafTrackId(trackId);
        Form target = formPath.isEmpty() ? rootForm : FormUtils.getForm(rootForm, formPath);

        if (target == null)
        {
            target = rootForm;
        }

        ensureFormProperty(target, leaf);

        return replay.properties.getOrCreate(rootForm, trackId);
    }

    public static void ensureFormProperty(Form form, String trackId)
    {
        if (form == null || trackId == null)
        {
            return;
        }

        String leaf = leafTrackId(trackId);
        int transformN = overlayNumberedIndex(leaf, TRANSFORM_OVERLAY);

        if (transformN >= -1)
        {
            form.ensureTransformOverlay(transformN);

            return;
        }

        int colorN = overlayNumberedIndex(leaf, COLOR_OVERLAY);

        if (colorN >= -1)
        {
            form.ensureColorOverlay(colorN);

            return;
        }

        int paintN = overlayNumberedIndex(leaf, PAINT_OVERLAY);

        if (paintN >= -1)
        {
            form.ensurePaintOverlay(paintN);

            return;
        }

        int illusionN = overlayNumberedIndex(leaf, ILLUSION_OVERLAY);

        if (illusionN >= -1)
        {
            form.ensureIllusionOverlay(illusionN);

            return;
        }

        int illusionTransformN = overlayNumberedIndex(leaf, ILLUSION_TRANSFORM_OVERLAY);

        if (illusionTransformN >= -1)
        {
            form.ensureIllusionTransformOverlay(illusionTransformN);

            return;
        }

        int poseN = overlayNumberedIndex(leaf, POSE_OVERLAY);

        if (poseN >= -1 && form instanceof ModelForm modelForm)
        {
            modelForm.ensurePoseOverlay(poseN);
        }
    }

    public static List<String> migrateOrderFromProperties(Replay replay)
    {
        LinkedHashSet<String> order = new LinkedHashSet<>();

        if (replay != null && replay.properties != null)
        {
            for (String key : replay.properties.properties.keySet())
            {
                if (isModelTrackId(key) && !key.contains(":"))
                {
                    order.add(key);
                }
            }
        }

        if (order.isEmpty())
        {
            return buildDefaultsFromSettings();
        }

        return new ArrayList<>(order);
    }
}
