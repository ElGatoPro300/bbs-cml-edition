package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.forms.forms.utils.ShadowSettings;
import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.utils.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Replays extends ValueList<Replay>
{
    public Replays(String id)
    {
        super(id);
    }

    @Override
    public void fromData(BaseType data)
    {
        super.fromData(data);

        List<Replay> allReplays = this.getList();
        Set<String> existingUUIDs = new HashSet<>();
        
        /* Collect existing UUIDs */
        for (Replay r : allReplays)
        {
            existingUUIDs.add(r.uuid.get());
        }

        /* Identify old groups and their members */
        Map<String, List<Replay>> oldGroups = new LinkedHashMap<>();
        Map<String, Replay> labelToGroup = new HashMap<>();

        for (Replay r : allReplays)
        {
            if (r.isGroup.get())
            {
                labelToGroup.put(r.label.get(), r);
            }
        }
        
        for (Replay replay : allReplays)
        {
            String groupName = replay.group.get();

            if (!groupName.isEmpty() && !existingUUIDs.contains(groupName))
            {
                /* If it's a label of an existing group, just link it instead of creating a new one */
                Replay existingGroup = labelToGroup.get(groupName);

                if (existingGroup != null)
                {
                    replay.group.set(existingGroup.uuid.get());
                    continue;
                }

                /* Don't create a new group if the name looks like a UUID that wasn't found,
                 * as it would result in a folder named after a UUID. */
                if (groupName.length() == 36 && groupName.contains("-"))
                {
                    continue;
                }

                oldGroups.computeIfAbsent(groupName, k -> new ArrayList<>()).add(replay);
            }
        }

        /* If no old groups found, exit early */
        if (oldGroups.isEmpty())
        {
            return;
        }

        /* Reconstruct list with new Group Replays and contiguous members */
        List<Replay> newList = new ArrayList<>();
        Set<Replay> processed = new HashSet<>();
        Map<String, Replay> createdGroups = new HashMap<>();

        for (Replay replay : allReplays)
        {
            if (processed.contains(replay))
            {
                continue;
            }

            String groupName = replay.group.get();
            boolean isOldGroupMember = !groupName.isEmpty() && !existingUUIDs.contains(groupName);

            if (isOldGroupMember)
            {
                /* We encountered the first member of an old group */
                if (!createdGroups.containsKey(groupName))
                {
                    /* Double check if it's really an old group member (not linked to label above) */
                    Replay existingGroup = labelToGroup.get(groupName);

                    if (existingGroup != null)
                    {
                        replay.group.set(existingGroup.uuid.get());
                        newList.add(replay);
                        processed.add(replay);
                        continue;
                    }

                    /* Create the Group Replay */
                    Replay groupReplay = new Replay(String.valueOf(allReplays.size() + createdGroups.size()));
                    groupReplay.isGroup.set(true);
                    groupReplay.label.set(groupName);
                    /* Ensure unique UUID if by chance it collides (unlikely but safe) */
                    while (existingUUIDs.contains(groupReplay.uuid.get()))
                    {
                        groupReplay.uuid.set(UUID.randomUUID().toString());
                    }
                    existingUUIDs.add(groupReplay.uuid.get());
                    
                    createdGroups.put(groupName, groupReplay);
                    newList.add(groupReplay);

                    /* Add all members of this group immediately */
                    List<Replay> members = oldGroups.get(groupName);
                    if (members != null)
                    {
                        for (Replay member : members)
                        {
                            member.group.set(groupReplay.uuid.get());
                            newList.add(member);
                            processed.add(member);
                        }
                    }
                }
            }
            else
            {
                /* Normal replay or already valid group member */
                newList.add(replay);
                processed.add(replay);
            }
        }

        /* Update the main list */
        this.list.clear();
        this.list.addAll(newList);
        this.sync();
    }

    public Replay addReplay()
    {
        Replay replay = new Replay(String.valueOf(this.list.size()));

        replay.keyframes.shadowSize.insert(0, new ShadowSettings());
        replay.keyframes.shadowOpacity.insert(0, 1D);

        this.preNotify();
        this.add(replay);
        this.postNotify();

        return replay;
    }

    public void remove(Replay replay)
    {
        int index = CollectionUtils.getIndex(this.list, replay);

        if (CollectionUtils.inRange(this.list, index))
        {
            this.preNotify();
            this.list.remove(index);
            this.sync();
            this.postNotify();
        }
    }

    @Override
    protected Replay create(String id)
    {
        return new Replay(id);
    }
}