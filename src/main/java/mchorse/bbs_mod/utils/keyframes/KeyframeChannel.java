package mchorse.bbs_mod.utils.keyframes;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.KeyframeShape;
import mchorse.bbs_mod.utils.keyframes.factories.DoubleKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.FloatKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.IntegerKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Keyframe channel
 *
 * This class is responsible for storing individual keyframes and also
 * interpolating between them.
 */
public class KeyframeChannel <T> extends ValueList<Keyframe<T>>
{
    private IKeyframeFactory<T> factory;

    /* When true, newly created keyframes use the "default model interpolation" setting instead of the "default world interpolation" one */
    private boolean model;

    public KeyframeChannel(String id, IKeyframeFactory<T> factory)
    {
        super(id);

        this.factory = factory;
    }

    public IKeyframeFactory<T> getFactory()
    {
        return this.factory;
    }

    public void setFactory(IKeyframeFactory<T> factory)
    {
        this.factory = factory;
    }

    public boolean isModel()
    {
        return this.model;
    }

    public KeyframeChannel<T> setModel(boolean model)
    {
        this.model = model;

        return this;
    }

    private void applyDefaultShape(Keyframe<T> kf)
    {
        if (BBSSettings.defaultKeyframeShape == null)
        {
            return;
        }

        int idx = BBSSettings.defaultKeyframeShape.get();
        KeyframeShape[] shapes = KeyframeShape.values();

        if (idx >= 0 && idx < shapes.length)
        {
            kf.setShape(shapes[idx]);
        }
    }

    private void applyDefaultInterpolation(Keyframe<T> kf)
    {
        if (this.factory == KeyframeFactories.BOOLEAN)
        {
            kf.getInterpolation().setInterp(Interpolations.CONST);

            return;
        }

        String id = this.getId();

        if ("selected_slot".equals(id) || (id != null && id.endsWith("/selected_slot")))
        {
            kf.getInterpolation().setInterp(Interpolations.CONST);

            return;
        }

        if ((this.model ? BBSSettings.defaultModelInterpolation : BBSSettings.defaultInterpolation) == null)
        {
            return;
        }

        int idx = (this.model ? BBSSettings.defaultModelInterpolation : BBSSettings.defaultInterpolation).get();
        int i = 0;

        for (Map.Entry<String, IInterp> e : Interpolations.MAP.entrySet())
        {
            if (i == idx)
            {
                kf.getInterpolation().setInterp(e.getValue());
                break;
            }

            i++;
        }
    }

    /* Read only */

    public double getLength()
    {
        return this.list.isEmpty() ? 0 : (int) this.list.get(this.list.size() - 1).getTick();
    }

    public boolean isEmpty()
    {
        return this.list.isEmpty();
    }

    public List<Keyframe<T>> getKeyframes()
    {
        return Collections.unmodifiableList(this.list);
    }

    public boolean has(int index)
    {
        return index >= 0 && index < this.list.size();
    }

    public Keyframe<T> get(int index)
    {
        return this.has(index) ? this.list.get(index) : null;
    }

    public KeyframeSegment<T> find(float ticks)
    {
        KeyframeSegment<T> segment = this.findSegment(ticks);

        if (segment == null)
        {
            return null;
        }

        segment.setup(ticks);

        return segment;
    }

    public T interpolate(float ticks)
    {
        T orDefault = null;

        if (this.factory instanceof FloatKeyframeFactory)
        {
            orDefault = (T) Float.valueOf(0F);
        }
        else if (this.factory instanceof DoubleKeyframeFactory)
        {
            orDefault = (T) Double.valueOf(0D);
        }
        else if (this.factory instanceof IntegerKeyframeFactory)
        {
            orDefault = (T) Integer.valueOf(0);
        }

        return this.interpolate(ticks, orDefault);
    }

    /**
     * Value of the last keyframe at or before {@code ticks}, ignoring interpolation.
     * Discrete tracks (hotbar slot) must not blend across keys.
     */
    public T interpolateHeld(float ticks)
    {
        if (this.list.isEmpty())
        {
            return this.factory.createEmpty();
        }

        Keyframe<T> held = this.list.get(0);

        for (Keyframe<T> frame : this.list)
        {
            if (frame.getTick() > ticks)
            {
                break;
            }

            held = frame;
        }

        return this.factory.copy(held.getValue());
    }

    public T interpolate(float ticks, T orDefault)
    {
        KeyframeSegment<T> segment = this.findSegment(ticks);

        if (segment == null)
        {
            return orDefault;
        }

        segment.setup(ticks);

        return segment.createInterpolated();
    }

    /**
     * Find a keyframe segment at given ticks
     */
    public KeyframeSegment<T> findSegment(float ticks)
    {
        /* No keyframes, no values */
        if (this.list.isEmpty())
        {
            return null;
        }

        /* Check whether given ticks are outside keyframe channel's range */
        Keyframe<T> prev = this.list.get(0);
        int size = this.list.size();

        if (size == 1 || ticks < prev.getTick())
        {
            return new KeyframeSegment<>(prev, prev);
        }

        Keyframe<T> last = this.list.get(size - 1);

        if (ticks >= last.getTick())
        {
            return new KeyframeSegment<>(last, last);
        }

        /* Use binary search to find the proper segment */
        int low = 0;
        int high = size - 1;

        while (low <= high)
        {
            int mid = low + (high - low) / 2;

            if (this.list.get(mid).getTick() < ticks)
            {
                low = mid + 1;
            }
            else
            {
                high = mid - 1;
            }
        }

        Keyframe<T> b = this.list.get(low);

        if (b.getTick() == Math.floor(ticks) && low < size - 1)
        {
            low += 1;
            b = this.list.get(low);
        }

        Keyframe<T> a = low - 1 >= 0 ? this.list.get(low - 1) : b;
        KeyframeSegment<T> segment = new KeyframeSegment<>(a, b);

        segment.setup(ticks);

        return segment;
    }

    /* Write only */

    public void removeAll()
    {
        this.preNotify();
        this.list.clear();
        this.postNotify();
    }

    public void remove(int index)
    {
        if (index < 0 || index > this.list.size() - 1)
        {
            return;
        }

        this.preNotify();
        this.list.remove(index);
        this.sync();
        this.postNotify();
    }

    /**
     * Drop every keyframe at {@code tick} or later (used when viewport "All groups"
     * recording starts so old future poses do not lerp into the capture).
     */
    public void removeFrom(float tick)
    {
        this.preNotify();
        this.list.removeIf((next) -> next.getTick() >= tick);
        this.sync();
        this.postNotify();
    }

    public boolean removeSilently(Keyframe<T> keyframe)
    {
        int index = this.list.indexOf(keyframe);

        if (index >= 0)
        {
            this.list.remove(index);
            this.sync();
            return true;
        }

        return false;
    }

    public void insertSpace(int where, int ticks)
    {
        KeyframeSegment<T> segment = this.findSegment(where);

        if (segment == null || where > segment.b.getTick())
        {
            return;
        }

        if (where < segment.a.getTick())
        {
            this.moveX(ticks);
        }
        else
        {
            BaseValue.edit(this, (__) ->
            {
                T copy = this.factory.copy(segment.createInterpolated());
                List<Keyframe<T>> keyframes = this.getKeyframes();

                for (int i = keyframes.indexOf(segment.b); i < keyframes.size(); i++)
                {
                    Keyframe<T> kf = keyframes.get(i);

                    kf.setTick(kf.getTick() + ticks);
                }

                Keyframe<T> kfA = keyframes.get(this.insert(where, copy));
                Keyframe<T> kfB = keyframes.get(this.insert(where + ticks, this.factory.copy(copy)));

                kfA.getInterpolation().setInterp(Interpolations.CONST);
                kfB.getInterpolation().copy(segment.a.getInterpolation());
            });
        }
    }

    /**
     * Insert a keyframe at given tick with given value
     *
     * This method is useful as it's not creating keyframes every time you
     * need to add some value, but rather inserts in correct order or
     * overwrites existing keyframe.
     *
     * Also, it returns index at which it was inserted.
     */
    public int insert(float tick, T value)
    {
        this.preNotify();

        Keyframe<T> prev;

        if (!this.list.isEmpty())
        {
            prev = this.list.get(0);

            if (tick < prev.getTick())
            {
                Keyframe<T> kf = new Keyframe<>("", this.factory, tick, value);
                this.applyDefaultInterpolation(kf);
                this.applyDefaultShape(kf);

                this.add(0, kf);
                this.sort();

                this.postNotify();

                return 0;
            }
        }

        prev = null;
        int index = 0;

        for (Keyframe<T> frame : this.list)
        {
            if (frame.getTick() == tick)
            {
                frame.setValue(value);
                this.postNotify();

                return index;
            }

            if (prev != null && tick > prev.getTick() && tick < frame.getTick())
            {
                break;
            }

            index++;
            prev = frame;
        }

        Keyframe<T> kf = new Keyframe<T>("", this.factory, tick, value);

        this.applyDefaultInterpolation(kf);
        this.applyDefaultShape(kf);

        this.add(index, kf);
        this.sort();
        this.postNotify();

        return index;
    }

    /**
     * Insert a keyframe only when {@code value} differs from the keyframe at or
     * before {@code tick}. When the value changes after a gap longer than one tick,
     * also inserts a hold keyframe at {@code tick - 1} so linear interpolation matches
     * what {@link #simplify()} would keep after per-tick recording.
     *
     * @return index of the inserted/updated keyframe, or {@code -1} if skipped
     */
    public int insertIfChanged(float tick, T value)
    {
        Keyframe<T> previous = null;

        for (Keyframe<T> frame : this.list)
        {
            if (frame.getTick() > tick)
            {
                break;
            }

            previous = frame;
        }

        if (previous == null)
        {
            return this.insert(tick, value);
        }

        if (this.factory.compare(previous.getValue(), value))
        {
            if (previous.getTick() == tick)
            {
                return this.insert(tick, value);
            }

            return -1;
        }

        if (tick > previous.getTick() + 1F)
        {
            this.insert(tick - 1F, this.factory.copy(previous.getValue()));
        }

        return this.insert(tick, value);
    }

    /**
     * Insert a keyframe at {@code tick} with the channel value interpolated at
     * that tick (not the current runtime / entity state).
     */
    public int insertInterpolated(float tick)
    {
        KeyframeSegment<T> segment = this.find(tick);
        T value;

        if (segment != null)
        {
            value = this.factory.copy(segment.createInterpolated());
        }
        else
        {
            T interpolated = this.interpolate(tick);

            value = interpolated != null ? this.factory.copy(interpolated) : this.factory.createEmpty();
        }

        int index = this.insert(tick, value);

        if (segment != null)
        {
            Keyframe<T> keyframe = this.get(index);

            if (keyframe != null)
            {
                keyframe.getInterpolation().copy(segment.a.getInterpolation());
                keyframe.copyOverExtra(segment.a);
            }
        }

        return index;
    }

    public void sort()
    {
        this.list.sort((a, b) -> (int) (a.getTick() - b.getTick()));

        this.sync();
    }

    public void simplify()
    {
        if (this.list.size() <= 2)
        {
            return;
        }

        this.preNotify();

        for (int i = 1; i < this.list.size() - 1; i++)
        {
            Keyframe<T> prev = this.list.get(i - 1);
            Keyframe<T> current = this.list.get(i);
            Keyframe<T> next = this.list.get(i + 1);

            if (this.factory.compare(current.getValue(), prev.getValue()) && this.factory.compare(current.getValue(), next.getValue()))
            {
                this.list.remove(i);

                i -= 1;
            }
        }

        int size = this.list.size();

        if (this.factory.compare(this.list.get(size - 1).getValue(), this.list.get(size - 2).getValue()))
        {
            this.list.remove(size - 1);
        }

        this.sync();
        this.postNotify();
    }

    public void moveX(float offset)
    {
        this.preNotify();

        for (Keyframe<T> keyframe : this.list)
        {
            keyframe.setTick(keyframe.getTick() + offset);
        }

        this.postNotify();
    }

    @Override
    protected Keyframe<T> create(String id)
    {
        Keyframe<T> kf = new Keyframe<>(id, this.factory);

        this.applyDefaultInterpolation(kf);
        this.applyDefaultShape(kf);

        return kf;
    }

    @Override
    public BaseType toData()
    {
        MapType data = new MapType();

        data.put("keyframes", super.toData());
        data.putString("type", CollectionUtils.getKey(KeyframeFactories.FACTORIES, this.factory));

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
        IKeyframeFactory<T> constructed = this.factory;
        IKeyframeFactory fromFile = KeyframeFactories.FACTORIES.get(map.getString("type"));

        /* Keep the constructor factory if the saved type is missing/unknown. */
        if (fromFile != null)
        {
            this.factory = fromFile;
        }

        super.fromData(map.getList("keyframes"));

        /* Channels constructed as DOUBLE must stay DOUBLE. Older films may have
         * saved the same id as integer/float; keeping that factory causes
         * ClassCastException when UI reads interpolated values as Double. */
        if (constructed == KeyframeFactories.DOUBLE && this.factory != KeyframeFactories.DOUBLE
            && (this.factory == KeyframeFactories.INTEGER || this.factory == KeyframeFactories.FLOAT))
        {
            this.promoteNumericKeyframesToDouble();
        }

        this.sort();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void promoteNumericKeyframesToDouble()
    {
        ListType keyframesData = new ListType();

        for (Object object : this.list)
        {
            Keyframe keyframe = (Keyframe) object;
            BaseType raw = keyframe.toData();

            if (raw == null || !raw.isMap())
            {
                continue;
            }

            MapType data = raw.asMap();
            Object value = keyframe.getValue();
            double number = value instanceof Number ? ((Number) value).doubleValue() : 0D;

            data.putDouble("value", number);
            keyframesData.add(data);
        }

        this.factory = (IKeyframeFactory<T>) KeyframeFactories.DOUBLE;
        this.list.clear();

        for (int i = 0; i < keyframesData.size(); i++)
        {
            Keyframe<T> keyframe = this.create(String.valueOf(i));

            this.list.add(keyframe);
            keyframe.setParent(this);
            keyframe.fromData(keyframesData.get(i));
        }

        this.sync();
    }

    public void copyKeyframes(KeyframeChannel<T> channel)
    {
        this.list.clear();

        for (Keyframe<T> keyframe : channel.getKeyframes())
        {
            Keyframe<T> value = new Keyframe<>(keyframe.getId(), keyframe.getFactory());

            value.copy(keyframe);
            this.add(value);
        }

        this.sort();
    }

    public void copyOver(KeyframeChannel channel, int tick)
    {
        if (this.factory != channel.factory || channel.isEmpty())
        {
            return;
        }

        this.preNotify();

        double start = tick + ((Keyframe) channel.getKeyframes().get(0)).getTick();

        this.list.removeIf((next) -> next.getTick() >= start);

        for (Object o : channel.getKeyframes())
        {
            Keyframe keyframe = (Keyframe) o;
            Keyframe value = new Keyframe<>(keyframe.getId(), keyframe.getFactory());

            value.fromData(keyframe.toData());
            value.setTick(tick + value.getTick());
            this.list.add(value);
        }

        this.sync();
        this.postNotify();
    }
}