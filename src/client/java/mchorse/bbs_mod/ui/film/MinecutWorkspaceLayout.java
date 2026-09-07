package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;

/**
 * Legacy Minecut column/size map. Kept only to migrate old {@code minecut_layout}
 * prefs into {@link EditorLayoutNode}; placement is owned by the classic dock engine.
 */
public final class MinecutWorkspaceLayout
{
    public enum Panel
    {
        MEDIA,
        PROPERTIES,
        PLAYER,
        TIMELINE;

        public static Panel fromId(String id)
        {
            if (id == null)
            {
                return null;
            }

            try
            {
                return Panel.valueOf(id);
            }
            catch (IllegalArgumentException ignored)
            {
                return null;
            }
        }
    }

    public final Panel[] columns = new Panel[] {Panel.MEDIA, Panel.PROPERTIES, Panel.PLAYER};
    public boolean timelineAtBottom = true;
    public int mediaW = 260;
    public int playerW = 400;
    public int timelineH = 260;

    public void reset()
    {
        this.columns[0] = Panel.MEDIA;
        this.columns[1] = Panel.PROPERTIES;
        this.columns[2] = Panel.PLAYER;
        this.timelineAtBottom = true;
        this.mediaW = 260;
        this.playerW = 400;
        this.timelineH = 260;
    }

    public int indexOf(Panel panel)
    {
        for (int i = 0; i < this.columns.length; i++)
        {
            if (this.columns[i] == panel)
            {
                return i;
            }
        }

        return -1;
    }

    public void swapColumns(Panel a, Panel b)
    {
        if (a == null || b == null || a == b || a == Panel.TIMELINE || b == Panel.TIMELINE)
        {
            return;
        }

        int ia = this.indexOf(a);
        int ib = this.indexOf(b);

        if (ia < 0 || ib < 0)
        {
            return;
        }

        Panel tmp = this.columns[ia];

        this.columns[ia] = this.columns[ib];
        this.columns[ib] = tmp;
    }

    public void setTimelineAtBottom(boolean bottom)
    {
        this.timelineAtBottom = bottom;
    }

    public int widthOf(Panel panel)
    {
        if (panel == Panel.MEDIA)
        {
            return this.mediaW;
        }

        if (panel == Panel.PLAYER)
        {
            return this.playerW;
        }

        return -1;
    }

    public void setWidthOf(Panel panel, int width)
    {
        if (panel == Panel.MEDIA)
        {
            this.mediaW = width;
        }
        else if (panel == Panel.PLAYER)
        {
            this.playerW = width;
        }
    }

    public MapType toData()
    {
        MapType data = new MapType();
        ListType order = new ListType();

        for (Panel panel : this.columns)
        {
            order.add(new StringType(panel.name()));
        }

        data.put("columns", order);
        data.putBool("timeline_bottom", this.timelineAtBottom);
        data.putInt("media_w", this.mediaW);
        data.putInt("player_w", this.playerW);
        data.putInt("timeline_h", this.timelineH);

        return data;
    }

    public void fromData(BaseType data)
    {
        if (data == null || !data.isMap())
        {
            return;
        }

        MapType map = data.asMap();

        if (map.has("columns", BaseType.TYPE_LIST))
        {
            ListType list = map.getList("columns");
            Panel[] next = new Panel[3];
            int n = 0;

            for (int i = 0; i < list.size() && n < 3; i++)
            {
                Panel panel = Panel.fromId(list.getString(i));

                if (panel == null || panel == Panel.TIMELINE)
                {
                    continue;
                }

                boolean dup = false;

                for (int j = 0; j < n; j++)
                {
                    if (next[j] == panel)
                    {
                        dup = true;
                        break;
                    }
                }

                if (!dup)
                {
                    next[n++] = panel;
                }
            }

            if (n == 3)
            {
                System.arraycopy(next, 0, this.columns, 0, 3);
            }
            else
            {
                this.columns[0] = Panel.MEDIA;
                this.columns[1] = Panel.PROPERTIES;
                this.columns[2] = Panel.PLAYER;
            }
        }

        if (map.has("timeline_bottom"))
        {
            this.timelineAtBottom = map.getBool("timeline_bottom");
        }

        if (map.has("media_w"))
        {
            this.mediaW = Math.max(120, map.getInt("media_w"));
        }

        if (map.has("player_w"))
        {
            this.playerW = Math.max(160, map.getInt("player_w"));
        }

        if (map.has("timeline_h"))
        {
            this.timelineH = Math.max(120, map.getInt("timeline_h"));
        }
    }

    /**
     * Approximate the previous fixed shell as a dock tree (ratios from pixel sizes).
     */
    public EditorLayoutNode toEditorLayoutNode()
    {
        return EditorLayoutNode.defaultMinecutLayout();
    }
}
