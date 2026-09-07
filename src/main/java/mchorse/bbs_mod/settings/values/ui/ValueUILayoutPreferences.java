package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.settings.values.base.BaseValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ValueUILayoutPreferences extends BaseValue
{
    public static final class FilmFloatingPanelState
    {
        public int x;
        public int y;
        public int w;
        public int h;
        public boolean collapsed;
    }

    private final List<String> filmHiddenPanels = new ArrayList<>();
    private final Map<String, FilmFloatingPanelState> filmFloatingPanels = new LinkedHashMap<>();
    private final List<String> filmCollapsedDocked = new ArrayList<>();
    private final List<String> filmCollapsedFloating = new ArrayList<>();
    private float formTreeWidth;
    private final Map<String, Float> formPanelWidths = new HashMap<>();
    private int keyframeSidebarWidth;

    public ValueUILayoutPreferences(String id)
    {
        super(id);
    }

    public List<String> getFilmHiddenPanels()
    {
        return new ArrayList<>(this.filmHiddenPanels);
    }

    public Map<String, FilmFloatingPanelState> getFilmFloatingPanels()
    {
        Map<String, FilmFloatingPanelState> copy = new LinkedHashMap<>();

        for (Map.Entry<String, FilmFloatingPanelState> entry : this.filmFloatingPanels.entrySet())
        {
            FilmFloatingPanelState state = entry.getValue();
            FilmFloatingPanelState clone = new FilmFloatingPanelState();

            clone.x = state.x;
            clone.y = state.y;
            clone.w = state.w;
            clone.h = state.h;
            clone.collapsed = state.collapsed;
            copy.put(entry.getKey(), clone);
        }

        return copy;
    }

    public List<String> getFilmCollapsedDocked()
    {
        return new ArrayList<>(this.filmCollapsedDocked);
    }

    public List<String> getFilmCollapsedFloating()
    {
        return new ArrayList<>(this.filmCollapsedFloating);
    }

    public float getFormTreeWidth()
    {
        return this.formTreeWidth;
    }

    public void setFormTreeWidth(float width)
    {
        BaseValue.edit(this, (v) -> this.formTreeWidth = width);
    }

    public float getFormPanelWidth(String panelClassName, float defaultWidth)
    {
        Float width = this.formPanelWidths.get(panelClassName);

        return width == null ? defaultWidth : width;
    }

    public void setFormPanelWidth(String panelClassName, float width)
    {
        BaseValue.edit(this, (v) -> this.formPanelWidths.put(panelClassName, width));
    }

    public int getKeyframeSidebarWidth(int defaultWidth)
    {
        return this.keyframeSidebarWidth > 0 ? this.keyframeSidebarWidth : defaultWidth;
    }

    public void setKeyframeSidebarWidth(int width)
    {
        BaseValue.edit(this, (v) -> this.keyframeSidebarWidth = Math.max(0, width));
    }

    public void setFilmSession(
        List<String> hiddenPanels,
        Map<String, FilmFloatingPanelState> floatingPanels,
        List<String> collapsedDocked,
        List<String> collapsedFloating
    )
    {
        BaseValue.edit(this, (v) ->
        {
            this.filmHiddenPanels.clear();
            this.filmFloatingPanels.clear();
            this.filmCollapsedDocked.clear();
            this.filmCollapsedFloating.clear();

            if (hiddenPanels != null)
            {
                this.filmHiddenPanels.addAll(hiddenPanels);
            }

            if (floatingPanels != null)
            {
                for (Map.Entry<String, FilmFloatingPanelState> entry : floatingPanels.entrySet())
                {
                    FilmFloatingPanelState source = entry.getValue();

                    if (source == null)
                    {
                        continue;
                    }

                    FilmFloatingPanelState state = new FilmFloatingPanelState();

                    state.x = source.x;
                    state.y = source.y;
                    state.w = source.w;
                    state.h = source.h;
                    state.collapsed = source.collapsed;
                    this.filmFloatingPanels.put(entry.getKey(), state);
                }
            }

            if (collapsedDocked != null)
            {
                this.filmCollapsedDocked.addAll(collapsedDocked);
            }

            if (collapsedFloating != null)
            {
                this.filmCollapsedFloating.addAll(collapsedFloating);
            }
        });
    }

    @Override
    public BaseType toData()
    {
        MapType data = new MapType();

        if (!this.filmHiddenPanels.isEmpty())
        {
            ListType hidden = new ListType();

            for (String panelId : this.filmHiddenPanels)
            {
                hidden.add(new StringType(panelId));
            }

            data.put("film_hidden_panels", hidden);
        }

        if (!this.filmFloatingPanels.isEmpty())
        {
            MapType floating = new MapType();

            for (Map.Entry<String, FilmFloatingPanelState> entry : this.filmFloatingPanels.entrySet())
            {
                FilmFloatingPanelState state = entry.getValue();
                MapType panel = new MapType();

                panel.putInt("x", state.x);
                panel.putInt("y", state.y);
                panel.putInt("w", state.w);
                panel.putInt("h", state.h);
                panel.putBool("collapsed", state.collapsed);
                floating.put(entry.getKey(), panel);
            }

            data.put("film_floating_panels", floating);
        }

        if (!this.filmCollapsedDocked.isEmpty())
        {
            ListType collapsed = new ListType();

            for (String panelId : this.filmCollapsedDocked)
            {
                collapsed.add(new StringType(panelId));
            }

            data.put("film_collapsed_docked", collapsed);
        }

        if (!this.filmCollapsedFloating.isEmpty())
        {
            ListType collapsed = new ListType();

            for (String panelId : this.filmCollapsedFloating)
            {
                collapsed.add(new StringType(panelId));
            }

            data.put("film_collapsed_floating", collapsed);
        }

        if (this.formTreeWidth > 0F)
        {
            data.putFloat("form_tree_width", this.formTreeWidth);
        }

        if (!this.formPanelWidths.isEmpty())
        {
            MapType widths = new MapType();

            for (Map.Entry<String, Float> entry : this.formPanelWidths.entrySet())
            {
                widths.putFloat(entry.getKey(), entry.getValue());
            }

            data.put("form_panel_widths", widths);
        }

        if (this.keyframeSidebarWidth > 0)
        {
            data.putInt("keyframe_sidebar_width", this.keyframeSidebarWidth);
        }

        return data;
    }

    @Override
    public void fromData(BaseType data)
    {
        if (!data.isMap())
        {
            return;
        }

        MapType map = data.asMap();

        this.filmHiddenPanels.clear();
        this.filmFloatingPanels.clear();
        this.filmCollapsedDocked.clear();
        this.filmCollapsedFloating.clear();
        this.formPanelWidths.clear();

        this.readStringList(map, "film_hidden_panels", this.filmHiddenPanels);
        this.readStringList(map, "film_collapsed_docked", this.filmCollapsedDocked);
        this.readStringList(map, "film_collapsed_floating", this.filmCollapsedFloating);
        this.readFilmFloatingPanels(map);
        this.formTreeWidth = map.getFloat("form_tree_width", 0F);
        this.readFormPanelWidths(map);
        this.keyframeSidebarWidth = map.getInt("keyframe_sidebar_width", 0);
    }

    private void readStringList(MapType map, String key, List<String> out)
    {
        if (!map.has(key))
        {
            return;
        }

        BaseType listData = map.get(key);

        if (listData == null || !listData.isList())
        {
            return;
        }

        for (BaseType item : listData.asList())
        {
            if (item != null && item.isString())
            {
                String value = item.asString();

                if (value != null && !value.isEmpty())
                {
                    out.add(value);
                }
            }
        }
    }

    private void readFilmFloatingPanels(MapType map)
    {
        if (!map.has("film_floating_panels"))
        {
            return;
        }

        BaseType floatingData = map.get("film_floating_panels");

        if (floatingData == null || !floatingData.isMap())
        {
            return;
        }

        MapType floating = floatingData.asMap();

        for (String panelId : floating.keys())
        {
            BaseType panelData = floating.get(panelId);

            if (panelData == null || !panelData.isMap())
            {
                continue;
            }

            MapType panel = panelData.asMap();
            FilmFloatingPanelState state = new FilmFloatingPanelState();

            state.x = panel.getInt("x", 0);
            state.y = panel.getInt("y", 0);
            state.w = panel.getInt("w", 0);
            state.h = panel.getInt("h", 0);
            state.collapsed = panel.getBool("collapsed", false);
            this.filmFloatingPanels.put(panelId, state);
        }
    }

    private void readFormPanelWidths(MapType map)
    {
        if (!map.has("form_panel_widths"))
        {
            return;
        }

        BaseType widthsData = map.get("form_panel_widths");

        if (widthsData == null || !widthsData.isMap())
        {
            return;
        }

        MapType widths = widthsData.asMap();

        for (String key : widths.keys())
        {
            this.formPanelWidths.put(key, widths.getFloat(key, 0F));
        }
    }
}
