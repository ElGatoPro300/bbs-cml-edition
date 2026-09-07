package mchorse.bbs_mod.blocks.entities;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Transform;

import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;

public class ModelProperties implements IMapSerializable
{
    private String name = "";

    private Form form;
    private Form formThirdPerson;
    private Form formInventory;
    private Form formFirstPerson;

    private final Transform transform = new Transform();
    private final Transform transformThirdPerson = new Transform();
    private final Transform transformInventory = new Transform();
    private final Transform transformFirstPerson = new Transform();
    private ItemStack itemMainHand = ItemStack.EMPTY;
    private ItemStack itemOffHand = ItemStack.EMPTY;
    private ItemStack armorHead = ItemStack.EMPTY;
    private ItemStack armorChest = ItemStack.EMPTY;
    private ItemStack armorLegs = ItemStack.EMPTY;
    private ItemStack armorFeet = ItemStack.EMPTY;

    private boolean enabled = true;
    private boolean global;
    private boolean shadow;
    private boolean hitbox;
    private boolean lookAt;
    /* When chroma sky hides terrain, this block still renders if true (or if the global setting is on). */
    private boolean chromaSky;
    /**
     * When true (default), sample world light at the form's translated position (local).
     * When false, sample light at the model block itself (avoids dark forms pushed into solids).
     */
    private boolean localLighting = true;
    private int lightLevel = 0;
    private float hardness;

    public Form getForm()
    {
        return this.form;
    }

    public String getName()
    {
        return this.name;
    }

    public void setName(String name)
    {
        this.name = name == null ? "" : name.trim();
    }

    protected Form processForm(Form form)
    {
        if (form != null)
        {
            form.playMain();
        }

        return form;
    }

    public void setForm(Form form)
    {
        this.form = this.processForm(form);
    }

    public Form getFormThirdPerson()
    {
        return this.formThirdPerson;
    }

    public void setFormThirdPerson(Form form)
    {
        this.formThirdPerson = this.processForm(form);
    }

    public Form getFormInventory()
    {
        return this.formInventory;
    }

    public void setFormInventory(Form form)
    {
        this.formInventory = this.processForm(form);
    }

    public Form getFormFirstPerson()
    {
        return this.formFirstPerson;
    }

    public void setFormFirstPerson(Form form)
    {
        this.formFirstPerson = this.processForm(form);
    }

    public Transform getTransform()
    {
        return this.transform;
    }

    public Transform getTransformThirdPerson()
    {
        return this.transformThirdPerson;
    }

    public Transform getTransformInventory()
    {
        return this.transformInventory;
    }

    public Transform getTransformFirstPerson()
    {
        return this.transformFirstPerson;
    }

    public ItemStack getItemMainHand()
    {
        return this.itemMainHand;
    }

    public void setItemMainHand(ItemStack itemMainHand)
    {
        this.itemMainHand = itemMainHand == null ? ItemStack.EMPTY : itemMainHand.copy();
    }

    public ItemStack getItemOffHand()
    {
        return this.itemOffHand;
    }

    public void setItemOffHand(ItemStack itemOffHand)
    {
        this.itemOffHand = itemOffHand == null ? ItemStack.EMPTY : itemOffHand.copy();
    }

    public ItemStack getArmorHead()
    {
        return this.armorHead;
    }

    public void setArmorHead(ItemStack armorHead)
    {
        this.armorHead = armorHead == null ? ItemStack.EMPTY : armorHead.copy();
    }

    public ItemStack getArmorChest()
    {
        return this.armorChest;
    }

    public void setArmorChest(ItemStack armorChest)
    {
        this.armorChest = armorChest == null ? ItemStack.EMPTY : armorChest.copy();
    }

    public ItemStack getArmorLegs()
    {
        return this.armorLegs;
    }

    public void setArmorLegs(ItemStack armorLegs)
    {
        this.armorLegs = armorLegs == null ? ItemStack.EMPTY : armorLegs.copy();
    }

    public ItemStack getArmorFeet()
    {
        return this.armorFeet;
    }

    public void setArmorFeet(ItemStack armorFeet)
    {
        this.armorFeet = armorFeet == null ? ItemStack.EMPTY : armorFeet.copy();
    }

    public boolean isEnabled()
    {
        return this.enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public boolean isGlobal()
    {
        return this.global;
    }

    public void setGlobal(boolean global)
    {
        this.global = global;
    }

    public boolean isShadow()
    {
        return this.shadow;
    }

    public void setShadow(boolean shadow)
    {
        this.shadow = shadow;
    }

    public boolean isHitbox()
    {
        return this.hitbox;
    }

    public void setHitbox(boolean hitbox)
    {
        this.hitbox = hitbox;
    }

    public boolean isLookAt()
    {
        return this.lookAt;
    }

    public void setLookAt(boolean lookAt)
    {
        this.lookAt = lookAt;
    }

    public boolean isChromaSky()
    {
        return this.chromaSky;
    }

    public void setChromaSky(boolean chromaSky)
    {
        this.chromaSky = chromaSky;
    }

    public boolean isLocalLighting()
    {
        return this.localLighting;
    }

    public void setLocalLighting(boolean localLighting)
    {
        this.localLighting = localLighting;
    }

    public int getLightLevel()
    {
        return this.lightLevel;
    }

    public void setLightLevel(int level)
    {
        this.lightLevel = Math.max(0, Math.min(15, level));
    }

    public float getHardness()
    {
        return this.hardness;
    }

    public void setHardness(float hardness)
    {
        if (hardness < 0F)
        {
            hardness = 0F;
        }
        else if (hardness > 50F)
        {
            hardness = 50F;
        }

        this.hardness = hardness;
    }

    public Form getForm(ModelTransformationMode mode)
    {
        Form form = this.form;

        if (mode == ModelTransformationMode.GUI && this.formInventory != null)
        {
            form = this.formInventory;
        }
        else if ((mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND || mode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND) && this.formThirdPerson != null)
        {
            form = this.formThirdPerson;
        }
        else if ((mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND || mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND) && this.formFirstPerson != null)
        {
            form = this.formFirstPerson;
        }

        return form;
    }

    public Transform getTransform(ModelTransformationMode mode)
    {
        Transform transform = this.transformThirdPerson;

        if (mode == ModelTransformationMode.GUI)
        {
            transform = this.transformInventory;
        }
        else if (mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND || mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND)
        {
            transform = this.transformFirstPerson;
        }
        else if (mode == ModelTransformationMode.GROUND)
        {
            transform = this.transform;
        }

        return transform;
    }

    @Override
    public void fromData(MapType data)
    {
        this.fromData(data, null);
    }

    public void fromData(MapType data, RegistryWrapper.WrapperLookup registries)
    {
        this.name = data.getString("name", "").trim();
        this.form = this.processForm(FormUtils.fromData(data.getMap("form")));
        this.formThirdPerson = this.processForm(FormUtils.fromData(data.getMap("formThirdPerson")));
        this.formInventory = this.processForm(FormUtils.fromData(data.getMap("formInventory")));
        this.formFirstPerson = this.processForm(FormUtils.fromData(data.getMap("formFirstPerson")));

        this.transform.fromData(data.getMap("transform"));
        this.transformThirdPerson.fromData(data.getMap("transformThirdPerson"));
        this.transformInventory.fromData(data.getMap("transformInventory"));
        this.transformFirstPerson.fromData(data.getMap("transformFirstPerson"));
        this.setItemMainHand(data.has("item_main_hand") ? KeyframeFactories.ITEM_STACK.fromData(data.get("item_main_hand"), registries) : ItemStack.EMPTY);
        this.setItemOffHand(data.has("item_off_hand") ? KeyframeFactories.ITEM_STACK.fromData(data.get("item_off_hand"), registries) : ItemStack.EMPTY);
        this.setArmorHead(data.has("item_head") ? KeyframeFactories.ITEM_STACK.fromData(data.get("item_head"), registries) : ItemStack.EMPTY);
        this.setArmorChest(data.has("item_chest") ? KeyframeFactories.ITEM_STACK.fromData(data.get("item_chest"), registries) : ItemStack.EMPTY);
        this.setArmorLegs(data.has("item_legs") ? KeyframeFactories.ITEM_STACK.fromData(data.get("item_legs"), registries) : ItemStack.EMPTY);
        this.setArmorFeet(data.has("item_feet") ? KeyframeFactories.ITEM_STACK.fromData(data.get("item_feet"), registries) : ItemStack.EMPTY);

        if (data.has("enabled")) this.enabled = data.getBool("enabled");
        this.shadow = data.getBool("shadow");
        this.global = data.getBool("global");
        this.lookAt = data.getBool("look_at");
        if (data.has("hitbox")) this.hitbox = data.getBool("hitbox");
        if (data.has("chroma_sky")) this.chromaSky = data.getBool("chroma_sky");
        if (data.has("local_lighting"))
        {
            this.localLighting = data.getBool("local_lighting");
        }
        else if (data.has("form_lighting"))
        {
            /* Legacy key from the first revision of this toggle. */
            this.localLighting = data.getBool("form_lighting");
        }
        if (data.has("light_level")) this.lightLevel = data.getInt("light_level");
        this.setHardness(data.getFloat("hardness", 0F));
    }

    @Override
    public void toData(MapType data)
    {
        this.toData(data, null);
    }

    public MapType toData(RegistryWrapper.WrapperLookup registries)
    {
        MapType map = new MapType();

        this.toData(map, registries);

        return map;
    }

    public void toData(MapType data, RegistryWrapper.WrapperLookup registries)
    {
        data.putString("name", this.name);
        data.put("form", FormUtils.toData(this.form));
        data.put("formThirdPerson", FormUtils.toData(this.formThirdPerson));
        data.put("formInventory", FormUtils.toData(this.formInventory));
        data.put("formFirstPerson", FormUtils.toData(this.formFirstPerson));

        data.put("transform", this.transform.toData());
        data.put("transformThirdPerson", this.transformThirdPerson.toData());
        data.put("transformInventory", this.transformInventory.toData());
        data.put("transformFirstPerson", this.transformFirstPerson.toData());
        data.put("item_main_hand", KeyframeFactories.ITEM_STACK.toData(this.itemMainHand, registries));
        data.put("item_off_hand", KeyframeFactories.ITEM_STACK.toData(this.itemOffHand, registries));
        data.put("item_head", KeyframeFactories.ITEM_STACK.toData(this.armorHead, registries));
        data.put("item_chest", KeyframeFactories.ITEM_STACK.toData(this.armorChest, registries));
        data.put("item_legs", KeyframeFactories.ITEM_STACK.toData(this.armorLegs, registries));
        data.put("item_feet", KeyframeFactories.ITEM_STACK.toData(this.armorFeet, registries));

        data.putBool("enabled", this.enabled);
        data.putBool("shadow", this.shadow);
        data.putBool("global", this.global);
        data.putBool("hitbox", this.hitbox);
        data.putBool("look_at", this.lookAt);
        data.putBool("chroma_sky", this.chromaSky);
        data.putBool("local_lighting", this.localLighting);
        data.putInt("light_level", this.lightLevel);
        data.putFloat("hardness", this.hardness);
    }

    public void update(IEntity entity)
    {
        entity.setEquipmentStack(EquipmentSlot.MAINHAND, this.itemMainHand.copy());
        entity.setEquipmentStack(EquipmentSlot.OFFHAND, this.itemOffHand.copy());
        entity.setEquipmentStack(EquipmentSlot.HEAD, this.armorHead.copy());
        entity.setEquipmentStack(EquipmentSlot.CHEST, this.armorChest.copy());
        entity.setEquipmentStack(EquipmentSlot.LEGS, this.armorLegs.copy());
        entity.setEquipmentStack(EquipmentSlot.FEET, this.armorFeet.copy());

        if (this.form != null)
        {
            this.form.update(entity);
        }

        if (this.formThirdPerson != null)
        {
            this.formThirdPerson.update(entity);
        }

        if (this.formInventory != null)
        {
            this.formInventory.update(entity);
        }

        if (this.formFirstPerson != null)
        {
            this.formFirstPerson.update(entity);
        }
    }
}
