package mchorse.bbs_mod.cubic.model.loaders;

import mchorse.bbs_mod.bobj.BOBJAction;
import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJChannel;
import mchorse.bbs_mod.bobj.BOBJGroup;
import mchorse.bbs_mod.bobj.BOBJKeyframe;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.Constant;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.math.molang.expressions.MolangValue;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class BOBJModelLoader implements IModelLoader
{
    private Animations defaultAnimations;

    @Override
    public ModelInstance load(String id, ModelManager models, Link model, Collection<Link> links, MapType config)
    {
        List<Link> bobjLinks = new ArrayList<>();

        for (Link l : links)
        {
            if (l != null && l.path != null && l.path.endsWith(".bobj"))
            {
                bobjLinks.add(l);
            }
        }

        if (bobjLinks.isEmpty())
        {
            return null;
        }

        /* Prefer mesh-bearing files (model.bobj / default.bobj) over animation-only siblings. */
        bobjLinks.sort(Comparator.comparingInt(this::meshPreference).reversed());

        Link modelTexture = IModelLoader.getLink(model.combine("model.png"), links, ".png");
        Animations mergedAnimations = new Animations(models.parser);
        BOBJLoader.BOBJData meshData = null;
        Link meshLink = null;

        for (Link bobjLink : bobjLinks)
        {
            try (InputStream stream = models.provider.getAsset(bobjLink))
            {
                if (stream == null)
                {
                    System.err.println("BOBJ asset stream was null: " + bobjLink);

                    continue;
                }

                BOBJLoader.BOBJData bobjData = BOBJLoader.readData(stream);

                this.convertAnimations(bobjData, mergedAnimations);

                if (meshData == null && this.findSkinnedMesh(bobjData) != null)
                {
                    meshData = bobjData;
                    meshLink = bobjLink;
                }
            }
            catch (Exception e)
            {
                System.err.println("Failed to read BOBJ \"" + bobjLink + "\" for model \"" + model + "\"!");
                e.printStackTrace();
            }
        }

        if (meshData == null)
        {
            System.err.println("Model \"" + model + "\" doesn't have a mesh connected to one of the armatures!");

            return null;
        }

        try
        {
            BOBJArmature armature = meshData.armatures.values().iterator().next();
            List<BOBJLoader.CompiledData> compiledMeshes = new ArrayList<>();

            for (BOBJLoader.BOBJMesh mesh : meshData.meshes)
            {
                if (mesh.armature == armature)
                {
                    compiledMeshes.add(BOBJLoader.compileMesh(meshData, mesh));
                }
            }

            if (compiledMeshes.isEmpty())
            {
                System.err.println("Model \"" + model + "\" doesn't have a mesh connected to one of the armatures!");

                return null;
            }

            BOBJModel bobjModel = new BOBJModel(armature, compiledMeshes, id.startsWith("emoticons") && id.endsWith("_simple"));

            meshData.initiateArmatures();

            ModelInstance instance = new ModelInstance(id, bobjModel, mergedAnimations, modelTexture);

            /* Each BOBJ mesh is its own material (keyed by mesh name): load its default texture
             * from a folder named after the mesh; without one it falls back to the model texture. */
            for (BOBJLoader.CompiledData mesh : compiledMeshes)
            {
                String material = mesh.mesh.name;

                instance.materials.add(material);

                Link texture = IModelLoader.findMaterialTexture(links, model, material);

                if (texture != null)
                {
                    instance.materialTextures.put(material, texture);
                }
                else
                {
                    /* No texture yet: surface an empty folder for this mesh to drop one into. */
                    IModelLoader.ensureMaterialFolder(models.provider, model, material);
                }
            }

            if (id.startsWith("emoticons/"))
            {
                this.ensureDefaultAnimations(models.provider, models.parser);
                this.applyDefaultAnimations(instance);
            }

            /* Emoticons mesh files ship without clips; shared actions.bobj must be present. */
            if (instance.animations.animations.isEmpty() && id.startsWith("emoticons/"))
            {
                System.err.println("Emoticons model \"" + id + "\" has 0 animations after load (mesh=" + meshLink + "). Retrying actions.bobj...");
                this.defaultAnimations = null;
                this.ensureDefaultAnimations(models.provider, models.parser);
                this.applyDefaultAnimations(instance);
            }

            System.out.println("BOBJ model \"" + id + "\" loaded with " + instance.animations.animations.size() + " animation(s) from " + bobjLinks.size() + " file(s).");

            instance.applyConfig(config);

            return instance;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    private int meshPreference(Link link)
    {
        String path = link.path.toLowerCase();
        int score = 0;

        if (path.endsWith("/model.bobj") || path.endsWith("model.bobj"))
        {
            score += 100;
        }

        if (path.contains("/default") || path.contains("/slim"))
        {
            score += 50;
        }

        if (path.contains("/animations/") || path.contains("/emotes/") || path.endsWith("actions.bobj"))
        {
            score -= 100;
        }

        return score;
    }

    private BOBJLoader.BOBJMesh findSkinnedMesh(BOBJLoader.BOBJData bobjData)
    {
        if (bobjData == null || bobjData.armatures.isEmpty())
        {
            return null;
        }

        BOBJArmature armature = bobjData.armatures.values().iterator().next();

        for (BOBJLoader.BOBJMesh mesh : bobjData.meshes)
        {
            if (mesh.armature == armature)
            {
                return mesh;
            }
        }

        return null;
    }

    /**
     * Shared Emoticons clip library ({@code actions.bobj} + {@code emotes/*.bobj}).
     * Retries when a previous attempt left an empty cache (slow disks / low-memory IO).
     */
    public void ensureDefaultAnimations(AssetProvider provider, MolangParser parser)
    {
        if (this.defaultAnimations != null && !this.defaultAnimations.animations.isEmpty())
        {
            return;
        }

        this.loadDefaultAnimations(provider, parser);
    }

    private void applyDefaultAnimations(ModelInstance instance)
    {
        this.mergeDefaultAnimationsInto(instance);
    }

    public void mergeDefaultAnimationsInto(ModelInstance instance)
    {
        if (instance == null || instance.animations == null || this.defaultAnimations == null)
        {
            return;
        }

        for (Animation value : this.defaultAnimations.animations.values())
        {
            if (instance.animations.get(value.id) == null)
            {
                instance.animations.add(value);
            }
        }
    }

    public void loadDefaultAnimations(AssetProvider provider, MolangParser parser)
    {
        Animations loaded = new Animations(parser);
        List<Link> actionsList = new ArrayList<>();

        actionsList.add(Link.assets("actions.bobj"));

        for (Link link : provider.getLinksFromPath(Link.assets("emotes")))
        {
            if (link.path.endsWith(".bobj"))
            {
                actionsList.add(link);
            }
        }

        for (Link link : actionsList)
        {
            try (InputStream stream = provider.getAsset(link))
            {
                if (stream == null)
                {
                    System.err.println("Failed to load Emoticons " + link + " (stream null)!");

                    continue;
                }

                BOBJLoader.BOBJData bobjData = BOBJLoader.readData(stream);

                this.convertAnimations(bobjData, loaded);
                System.out.println("Loaded " + bobjData.actions.size() + " action(s) from " + link);
            }
            catch (Exception e)
            {
                System.err.println("Failed to load Emoticons " + link + "!");
                e.printStackTrace();
            }
        }

        /* Only cache a successful library. An empty cache would permanently starve
         * every emoticons/* model on the next load (common on flaky low-end IO). */
        if (!loaded.animations.isEmpty())
        {
            this.defaultAnimations = loaded;
        }
        else
        {
            this.defaultAnimations = null;
            System.err.println("Emoticons default animation library is empty; will retry on next load.");
        }
    }

    private Animations convertAnimations(BOBJLoader.BOBJData bobjData, Animations animations)
    {
        for (Map.Entry<String, BOBJAction> entry : bobjData.actions.entrySet())
        {
            if (animations.get(entry.getKey()) != null)
            {
                continue;
            }

            Animation animation = new Animation(entry.getKey(), animations.parser);

            this.fillAnimation(animation, entry.getValue());
            animations.add(animation);
        }

        return animations;
    }

    private void fillAnimation(Animation animation, BOBJAction value)
    {
        MolangParser parser = animation.parser;

        for (Map.Entry<String, BOBJGroup> entry : value.groups.entrySet())
        {
            AnimationPart part = new AnimationPart(parser);

            for (BOBJChannel channel : entry.getValue().channels)
            {
                if (channel.path.equals("location"))
                {
                    if (channel.index == 0) this.copyKeyframes(parser, part.x, channel);
                    else if (channel.index == 1) this.copyKeyframes(parser, part.y, channel);
                    else if (channel.index == 2) this.copyKeyframes(parser, part.z, channel);
                }
                else if (channel.path.equals("scale"))
                {
                    if (channel.index == 0) this.copyKeyframes(parser, part.sx, channel);
                    else if (channel.index == 1) this.copyKeyframes(parser, part.sy, channel);
                    else if (channel.index == 2) this.copyKeyframes(parser, part.sz, channel);
                }
                else
                {
                    if (channel.index == 0) this.copyKeyframes(parser, part.rx, channel);
                    else if (channel.index == 1) this.copyKeyframes(parser, part.ry, channel);
                    else if (channel.index == 2) this.copyKeyframes(parser, part.rz, channel);
                }
            }

            animation.parts.put(entry.getKey(), part);
        }

        /* Insert head keyframes */
        AnimationPart head = animation.parts.get("head");

        if (head == null)
        {
            head = new AnimationPart(parser);

            animation.parts.put("head", head);

            this.fillHeadVariables(parser, head);
        }
        else if (head.rx.isEmpty())
        {
            this.fillHeadVariables(parser, head);
        }

        animation.setLength(value.getDuration() / 20F);
    }

    private void copyKeyframes(MolangParser parser, KeyframeChannel<MolangExpression> keyframeChannel, BOBJChannel channel)
    {
        for (int i = 0, c = channel.keyframes.size(); i < c; i++)
        {
            BOBJKeyframe a = channel.keyframes.get(i);
            BOBJKeyframe b = a;

            if (i - 1 >= 0)
            {
                b = channel.keyframes.get(i - 1);
            }

            MolangValue value = new MolangValue(parser, new Constant(a.value));
            int index = keyframeChannel.insert(a.frame, value);

            Keyframe<MolangExpression> keyframe = keyframeChannel.get(index);

            /* Fill in interpolation and bezier handles */
            keyframe.getInterpolation().setInterp(b.interpolation.interp);
            keyframe.lx = a.frame - a.leftX;
            keyframe.ly = a.leftY - a.value;
            keyframe.rx = a.rightX - a.frame;
            keyframe.ry = a.rightY - a.value;
        }

        keyframeChannel.sort();
    }

    private void fillHeadVariables(MolangParser parser, AnimationPart head)
    {
        head.rx.insert(0F, parseExpression(parser, "query.head_pitch / 180 * " + Math.PI));
        head.ry.insert(0F, parseExpression(parser, "-query.head_yaw / 180 * " + Math.PI));
    }

    private static MolangExpression parseExpression(MolangParser parser, String expression)
    {
        try
        {
            return new MolangValue(parser, parser.parse(expression));
        }
        catch (Exception e)
        {}

        return MolangParser.ZERO;
    }
}
