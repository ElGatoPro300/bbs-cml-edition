package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.actions.types.MobDeathActionClip;
import mchorse.bbs_mod.actions.types.SwipeActionClip;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.values.ValuePoint;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.IntType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.utils.ShadowSettings;
import mchorse.bbs_mod.settings.values.base.BaseValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import net.minecraft.entity.LivingEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Replay extends ValueGroup
{
    public final ValueForm form = new ValueForm("form");
    public final ReplayKeyframes keyframes = new ReplayKeyframes("keyframes");
    public final FormProperties properties = new FormProperties("properties");
    public final Clips actions = new Clips("actions", BBSMod.getFactoryActionClips());
    public final Inventory inventory = new Inventory("inventory");

    public final ValueBoolean enabled = new ValueBoolean("enabled", true);
    public final ValueString label = new ValueString("label", "");
    public final ValueString nameTag = new ValueString("name_tag", "");
    public final ValueString group = new ValueString("group", "");
    public final ValueBoolean shadow = new ValueBoolean("shadow", true);
    public final ValueFloat shadowSize = new ValueFloat("shadow_size", 0.5F);
    public final ValueFloat shadowSizeZ = new ValueFloat("shadow_size_z", 0.5F);
    public final ValueFloat shadowOpacity = new ValueFloat("shadow_opacity", 1F, 0F, 1F);
    public final ValueFloat shadowOffsetX = new ValueFloat("shadow_offset_x", 0F);
    public final ValueFloat shadowOffsetY = new ValueFloat("shadow_offset_y", 0F);
    public final ValueFloat shadowOffsetZ = new ValueFloat("shadow_offset_z", 0F);
    public final ValueInt looping = new ValueInt("looping", 0);

    public final ValueBoolean actor = new ValueBoolean("actor", false);
    public final ValueBoolean fp = new ValueBoolean("fp", false);
    public final ValueBoolean vanillaMobPlayback = new ValueBoolean("vanilla_mob_playback", false);
    public final ValueBoolean relative = new ValueBoolean("relative", false);
    public final ValuePoint relativeOffset = new ValuePoint("relativeOffset", new Point(0, 0, 0));

    public transient boolean vanillaMobPlaybackSerialized = false;

    private final Map<String, String> customSheetTitles = new HashMap<>();
    private final Map<String, Integer> sheetColors = new HashMap<>();
    public final ValueBoolean axesPreview = new ValueBoolean("axes_preview", false);
    public final ValueString axesPreviewBone = new ValueString("axes_preview_bone", "");
    public final ValueBoolean isGroup = new ValueBoolean("is_group", false);
    public final ValueString uuid = new ValueString("uuid", "");

    /* Item drop velocity configuration */
    public final ValueBoolean dropItemsOnDeath = new ValueBoolean("drop_items_on_death", false);
    public final ValueFloat dropVelocityMinX = new ValueFloat("drop_velocity_min_x", -0.1F);
    public final ValueFloat dropVelocityMaxX = new ValueFloat("drop_velocity_max_x", 0.1F);
    public final ValueFloat dropVelocityMinY = new ValueFloat("drop_velocity_min_y", 0.1F);
    public final ValueFloat dropVelocityMaxY = new ValueFloat("drop_velocity_max_y", 0.25F);
    public final ValueFloat dropVelocityMinZ = new ValueFloat("drop_velocity_min_z", -0.1F);
    public final ValueFloat dropVelocityMaxZ = new ValueFloat("drop_velocity_max_z", 0.1F);

    public Replay(String id)
    {
        super(id);

        this.add(this.form);
        this.add(this.keyframes);
        this.add(this.properties);
        this.add(this.actions);
        this.add(this.inventory);

        this.add(this.enabled);
        this.add(this.label);
        this.add(this.nameTag);
        this.add(this.group);
        this.add(this.shadow);
        this.add(this.shadowSize);
        this.add(this.shadowSizeZ);
        this.add(this.shadowOpacity);
        this.add(this.shadowOffsetX);
        this.add(this.shadowOffsetY);
        this.add(this.shadowOffsetZ);
        this.add(this.looping);

        this.add(this.actor);
        this.add(this.fp);
        this.add(this.vanillaMobPlayback);
        this.add(this.relative);
        this.add(this.relativeOffset);

        this.add(this.axesPreview);
        this.add(this.axesPreviewBone);
        this.add(this.dropItemsOnDeath);
        this.add(this.dropVelocityMinX);
        this.add(this.dropVelocityMaxX);
        this.add(this.dropVelocityMinY);
        this.add(this.dropVelocityMaxY);
        this.add(this.dropVelocityMinZ);
        this.add(this.dropVelocityMaxZ);
        this.add(this.isGroup);
        this.add(this.uuid);
        
        this.uuid.set(UUID.randomUUID().toString());
    }

    public String getName()
    {
        if (this.isGroup.get())
        {
            return this.label.get().isEmpty() ? "New Group" : this.label.get();
        }

        String label = this.label.get();

        if (!label.isEmpty())
        {
            return label;
        }

        Form form = this.form.get();

        if (form == null)
        {
            return "-";
        }

        return form.getDisplayName();
    }

    /**
     * Camera-relative rendering is stub-only. Actor-mode replays are physical
     * entities teleported to absolute keyframe coordinates, so relative mode
     * has no effect while {@link #actor} is enabled.
     */
    public boolean isCameraRelative()
    {
        return this.relative.get() && !this.actor.get();
    }

    public void shift(float tick)
    {
        this.keyframes.shift(tick);
        this.properties.shift(tick);
        this.actions.shift(tick);
    }

    public void applyActions(LivingEntity actor, SuperFakePlayer fakePlayer, Film film, int tick)
    {
        this.applyActionsCrossing(actor, fakePlayer, film, tick - 1F, tick);
    }

    public int applyActionsCrossing(LivingEntity actor, SuperFakePlayer fakePlayer, Film film, float prevTime, float currTime)
    {
        if (actor != null && (actor.isDead() || actor.getHealth() <= 0F || actor.deathTime > 0))
        {
            return 0;
        }

        List<Clip> clips = this.actions.getClipsCrossing(prevTime, currTime);
        int fired = 0;

        for (Clip clip : clips)
        {
            fired += ((ActionClip) clip).applyCrossing(actor, fakePlayer, film, this, prevTime, currTime);
        }

        return fired;
    }

    public void applyClientActions(int tick, IEntity entity, Film film)
    {
        tick = this.getTick(tick);

        SwipeActionClip.noteClientFilmTick(entity, tick);

        boolean dead = entity != null && entity.getDeathTime() > 0;
        List<Clip> clips = this.actions.getClips(tick);

        for (Clip clip : clips)
        {
            if (clip instanceof ActionClip actionClip && actionClip.isClient())
            {
                if (dead && !(clip instanceof MobDeathActionClip))
                {
                    continue;
                }

                actionClip.applyClient(entity, film, this, tick);
            }
        }
    }

    public int getTick(int tick)
    {
        return this.looping.get() > 0 ? tick % this.looping.get() : tick;
    }

    public String getCustomSheetTitle(String id)
    {
        return this.customSheetTitles.get(id);
    }

    public void setCustomSheetTitle(String id, String title)
    {
        if (title == null || title.isBlank())
        {
            this.customSheetTitles.remove(id);
        }
        else
        {
            this.customSheetTitles.put(id, title);
        }
    }

    public Integer getSheetColor(String id)
    {
        return this.sheetColors.get(id);
    }

    public void setSheetColor(String id, Integer color)
    {
        if (color == null)
        {
            this.sheetColors.remove(id);
        }
        else
        {
            this.sheetColors.put(id, color);
        }
    }

    @Override
    public void copy(BaseValueGroup group)
    {
        super.copy(group);

        if (group instanceof Replay other)
        {
            this.customSheetTitles.clear();
            this.customSheetTitles.putAll(other.customSheetTitles);
            this.sheetColors.clear();
            this.sheetColors.putAll(other.sheetColors);
            this.vanillaMobPlaybackSerialized = other.vanillaMobPlaybackSerialized;
        }
    }

    @Override
    public BaseType toData()
    {
        MapType map = (MapType) super.toData();

        if (!this.customSheetTitles.isEmpty())
        {
            MapType titles = new MapType();

            for (Map.Entry<String, String> entry : this.customSheetTitles.entrySet())
            {
                titles.put(entry.getKey(), new StringType(entry.getValue()));
            }

            map.put("custom_sheet_titles", titles);
        }

        if (!this.sheetColors.isEmpty())
        {
            MapType colors = new MapType();

            for (Map.Entry<String, Integer> entry : this.sheetColors.entrySet())
            {
                colors.put(entry.getKey(), new IntType(entry.getValue()));
            }

            map.put("sheet_colors", colors);
        }

        return map;
    }

    @Override
    public void fromData(BaseType data)
    {
        super.fromData(data);

        if (data instanceof MapType map)
        {
            this.vanillaMobPlaybackSerialized = map.has("vanilla_mob_playback");

            BaseType titlesType = map.get("custom_sheet_titles");

            if (titlesType instanceof MapType titles)
            {
                for (String key : titles.keys())
                {
                    BaseType value = titles.get(key);

                    if (value != null && value.isString())
                    {
                        this.customSheetTitles.put(key, value.asString());
                    }
                }
            }

            BaseType colorsType = map.get("sheet_colors");

            if (colorsType instanceof MapType colors)
            {
                for (String key : colors.keys())
                {
                    BaseType value = colors.get(key);

                    if (value != null && value.isNumeric())
                    {
                        this.sheetColors.put(key, value.asNumeric().intValue());
                    }
                }
            }
        }

        /* Pre-XZ-shadow films only had shadow_size — mirror into Z so shape stays circular. */
        boolean migratedShadowZ = false;

        if (data instanceof MapType map && !map.has("shadow_size_z") && map.has("shadow_size"))
        {
            this.shadowSizeZ.set(this.shadowSize.get());
            migratedShadowZ = true;
        }

        this.ensureShadowKeyframes();
        this.applyVanillaPlaybackDefaults();
    }

    private void applyVanillaPlaybackDefaults()
    {}

    private void ensureShadowKeyframes()
    {
        /* Groups only contribute when the user keys shadow tracks. Auto-seeding would
         * overwrite every member's shadow until emptied. */
        if (this.isGroup.get())
        {
            this.clearIdentityGroupShadowSeeds();

            return;
        }

        if (this.keyframes.shadowSize.isEmpty())
        {
            ShadowSettings settings = new ShadowSettings(1F, this.shadowSize.get(), this.shadowSizeZ.get());

            settings.offsetX = this.shadowOffsetX.get();
            settings.offsetY = this.shadowOffsetY.get();
            settings.offsetZ = this.shadowOffsetZ.get();

            this.keyframes.shadowSize.insert(0, settings);
        }

        if (this.keyframes.shadowOpacity.isEmpty())
        {
            this.keyframes.shadowOpacity.insert(0, (double) this.shadowOpacity.get());
        }
    }

    /**
     * Remove auto-seeded identity shadow keys from groups (single default key at tick 0)
     * left by older builds so member form shadows work again without manual cleanup.
     */
    private void clearIdentityGroupShadowSeeds()
    {
        if (this.keyframes.shadowSize.getKeyframes().size() == 1)
        {
            Keyframe<ShadowSettings> keyframe = this.keyframes.shadowSize.get(0);
            ShadowSettings value = keyframe == null ? null : keyframe.getValue();

            if (keyframe != null && keyframe.getTick() == 0F && isIdentityGroupShadowSize(value))
            {
                this.keyframes.shadowSize.removeAll();
            }
        }

        if (this.keyframes.shadowOpacity.getKeyframes().size() == 1)
        {
            Keyframe<Double> keyframe = this.keyframes.shadowOpacity.get(0);
            Double value = keyframe == null ? null : keyframe.getValue();

            if (keyframe != null && keyframe.getTick() == 0F && value != null && Math.abs(value - 1D) < 0.0001D)
            {
                this.keyframes.shadowOpacity.removeAll();
            }
        }
    }

    private static boolean isIdentityGroupShadowSize(ShadowSettings value)
    {
        if (value == null)
        {
            return true;
        }

        return Math.abs(value.widthX - 0.5F) < 0.0001F
            && Math.abs(value.widthZ - 0.5F) < 0.0001F
            && Math.abs(value.offsetX) < 0.0001F
            && Math.abs(value.offsetY) < 0.0001F
            && Math.abs(value.offsetZ) < 0.0001F;
    }
}
