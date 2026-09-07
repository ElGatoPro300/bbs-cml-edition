package mchorse.bbs_mod.cubic.model;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.MolangHelper;
import mchorse.bbs_mod.cubic.animation.ProceduralDefaults;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.model.loaders.BOBJModelLoader;
import mchorse.bbs_mod.cubic.model.loaders.CubicModelLoader;
import mchorse.bbs_mod.cubic.model.loaders.FBXModelLoader;
import mchorse.bbs_mod.cubic.model.loaders.GLTFModelLoader;
import mchorse.bbs_mod.cubic.model.loaders.GeoCubicModelLoader;
import mchorse.bbs_mod.cubic.model.loaders.IModelLoader;
import mchorse.bbs_mod.cubic.model.loaders.MiModelLoader;
import mchorse.bbs_mod.cubic.model.loaders.VoxModelLoader;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.structure.ModelCollisionData;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.pose.PoseManager;
import mchorse.bbs_mod.utils.pose.ShapeKeysManager;
import mchorse.bbs_mod.utils.watchdog.IWatchDogListener;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;

import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ModelManager implements IWatchDogListener
{
    public static final String MODELS_PREFIX = "models/";
    public static final String CONFIG_FILE = "config.json";
    public static final String DYNAMIC_CONFIG_FILE = "dynamic_config.json";
    public static final String DYNAMIC_PHYS_BONES_KEY = "phys_bones";

    public final Map<String, ModelInstance> models = new ConcurrentHashMap<>();
    public final List<IModelLoader> loaders = new ArrayList<>();
    public final AssetProvider provider;
    public final MolangParser parser;
    private final Set<String> relodableSuffixes = new HashSet<>();
    /* ConcurrentHashMap forbids null values — track failed loads separately. */
    private final Set<String> failedModels = ConcurrentHashMap.newKeySet();

    private ModelLoader loader = new ModelLoader(this);
    private List<String> availableKeysCache;

    public ModelManager(AssetProvider provider)
    {
        this.provider = provider;
        this.parser = new MolangParser();

        MolangHelper.registerVars(this.parser);

        this.setupLoaders();
    }

    private void setupLoaders()
    {
        this.loaders.clear();
        this.relodableSuffixes.clear();
        this.loaders.add(new BOBJModelLoader());
        this.loaders.add(new FBXModelLoader());
        this.loaders.add(new CubicModelLoader());
        this.loaders.add(new GeoCubicModelLoader());
        this.loaders.add(new VoxModelLoader());
        this.loaders.add(new GLTFModelLoader());
        this.loaders.add(new MiModelLoader());

        this.registerRelodableSuffix(".bbs.json");
        this.registerRelodableSuffix(".geo.json");
        this.registerRelodableSuffix(".bobj");
        this.registerRelodableSuffix(".fbx");
        this.registerRelodableSuffix(".obj");
        this.registerRelodableSuffix(".gltf");
        this.registerRelodableSuffix(".glb");
        this.registerRelodableSuffix(".mimodel");
        this.registerRelodableSuffix(".animation.json");
        this.registerRelodableSuffix(".vox");
        this.registerRelodableSuffix("/" + CONFIG_FILE);
        this.registerRelodableSuffix("/" + DYNAMIC_CONFIG_FILE);
    }

    /**
     * Warm the shared Emoticons clip library so the first heavy {@code emoticons/alex}
     * / {@code steve} load on low-end machines does not contend with parsing actions.bobj.
     */
    public void ensureEmoticonDefaultAnimations()
    {
        for (IModelLoader loader : this.loaders)
        {
            if (loader instanceof BOBJModelLoader bobjLoader)
            {
                bobjLoader.ensureDefaultAnimations(this.provider, this.parser);
            }
            else if (loader instanceof FBXModelLoader fbxLoader)
            {
                fbxLoader.ensureDefaultAnimations(this.provider, this.parser);
            }
        }
    }

    /**
     * If an already-loaded emoticons model somehow has no clips (failed first-time
     * actions.bobj parse), merge the shared library in place.
     */
    public void ensureEmoticonAnimations(ModelInstance model)
    {
        if (model == null || model.id == null || !model.id.startsWith("emoticons/"))
        {
            return;
        }

        if (model.animations != null && !model.animations.animations.isEmpty())
        {
            return;
        }

        this.ensureEmoticonDefaultAnimations();

        for (IModelLoader loader : this.loaders)
        {
            if (loader instanceof BOBJModelLoader bobjLoader)
            {
                bobjLoader.ensureDefaultAnimations(this.provider, this.parser);
                bobjLoader.mergeDefaultAnimationsInto(model);

                break;
            }
        }
    }

    public void registerLoader(IModelLoader loader)
    {
        if (loader == null)
        {
            return;
        }

        this.loaders.add(loader);
    }

    public void registerRelodableSuffix(String suffix)
    {
        if (suffix == null || suffix.isEmpty())
        {
            return;
        }

        this.relodableSuffixes.add(suffix);
    }

    /**
     * Get all models that can be loaded by
     */
    public List<String> getAvailableKeys()
    {
        if (this.availableKeysCache != null)
        {
            return new ArrayList<>(this.availableKeysCache);
        }

        List<Link> models = new ArrayList<>(BBSMod.getProvider().getLinksFromPath(Link.assets("models"), true));
        Set<String> keys = new HashSet<>();

        models.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));

        for (Link link : models)
        {
            if (this.isRelodable(link))
            {
                String path = link.path;

                int slash = path.indexOf('/');
                int lastSlash = path.lastIndexOf('/');

                if (slash != lastSlash)
                {
                    path = path.substring(slash + 1, lastSlash);

                    keys.add(path);
                }
            }
        }

        this.availableKeysCache = new ArrayList<>(keys);

        return new ArrayList<>(this.availableKeysCache);
    }

    public ModelInstance getModel(String id)
    {
        return this.getModel(id, false);
    }

    /**
     * @param priority queue this id first when not yet loaded (visible morph thumbnails).
     */
    public ModelInstance getModel(String id, boolean priority)
    {
        if (id == null || id.isEmpty())
        {
            return null;
        }

        ModelInstance loaded = this.models.get(id);

        if (loaded != null)
        {
            return loaded;
        }

        if (this.failedModels.contains(id))
        {
            return null;
        }

        if (this.loader.isLoading(id))
        {
            if (priority)
            {
                this.loader.add(id, true);
            }

            return null;
        }

        this.loader.add(id, priority);

        return null;
    }

    public boolean isLoading(String id)
    {
        return this.loader.isLoading(id);
    }

    /**
     * Queue every known model for background load so morph thumbnails are warm
     * by the time the player opens the form list.
     */
    public void preloadAll()
    {
        for (String key : this.getAvailableKeys())
        {
            if (key == null || key.isEmpty() || this.models.containsKey(key) || this.failedModels.contains(key))
            {
                continue;
            }

            this.loader.add(key, false);
        }
    }

    public ModelInstance loadModel(String id)
    {
        /* MolangParser (and animation fromData) is not thread-safe. Parallel ModelLoader
         * workers must not parse models concurrently on the shared parser. */
        synchronized (this.parser)
        {
            return this.loadModelLocked(id);
        }
    }

    private ModelInstance loadModelLocked(String id)
    {
        ModelInstance model = null;
        Link modelLink = Link.assets(MODELS_PREFIX + id);
        Collection<Link> links = this.provider.getLinksFromPath(modelLink, true);
        MapType config = this.loadConfig(modelLink);

        for (IModelLoader loader : this.loaders)
        {
            model = loader.load(id, this, modelLink, links, config);

            if (model != null)
            {
                break;
            }
        }

        if (model == null)
        {
            System.err.println("Model \"" + id + "\" wasn't loaded properly, or was loaded with no top level groups!");
            this.failedModels.add(id);
            this.models.remove(id);
        }
        else
        {
            System.out.println("Model \"" + id + "\" was loaded!");

            ProceduralDefaults.ensureForModelInstance(model, this.provider, this.parser);
            this.ensureEmoticonAnimations(model);
            model.setup();

            ModelInstance existing = this.models.get(id);

            if (existing != null)
            {
                existing.delete();

                if (existing.model instanceof BOBJModel bobjModel)
                {
                    bobjModel.delete();
                }
            }

            this.failedModels.remove(id);
            this.models.put(id, model);
            ModelCollisionData.invalidate(id);
        }

        return model;
    }

    private MapType loadConfig(Link modelLink)
    {
        MapType config = this.loadConfigFile(modelLink, CONFIG_FILE);
        MapType dynamicConfig = this.loadConfigFile(modelLink, DYNAMIC_CONFIG_FILE);

        if (config == null && dynamicConfig == null)
        {
            return null;
        }

        if (config == null)
        {
            config = new MapType();
        }

        if (dynamicConfig != null && dynamicConfig.has(DYNAMIC_PHYS_BONES_KEY, BaseType.TYPE_LIST))
        {
            config.put(DYNAMIC_PHYS_BONES_KEY, dynamicConfig.get(DYNAMIC_PHYS_BONES_KEY).copy());
        }

        return config;
    }

    private MapType loadConfigFile(Link modelLink, String fileName)
    {
        try (InputStream asset = this.provider.getAsset(modelLink.combine(fileName)))
        {
            String string = IOUtils.readText(asset);
            BaseType base = DataToString.fromString(string);

            if (BaseType.isMap(base))
            {
                return base.asMap();
            }
        }
        catch (Exception e)
        {}

        return null;
    }

    public void reload()
    {
        this.availableKeysCache = null;

        for (ModelInstance model : this.models.values())
        {
            if (model != null)
            {
                model.delete();
            }
        }

        this.models.clear();
        this.failedModels.clear();
        ModelCollisionData.invalidateAll();
        PoseManager.INSTANCE.clear();
        ShapeKeysManager.INSTANCE.clear();
        this.setupLoaders();
    }

    public boolean isRelodable(Link link)
    {
        if (!link.path.startsWith(MODELS_PREFIX))
        {
            return false;
        }

        if (link.path.contains("/animations/") || link.path.contains("/shapes/"))
        {
            return false;
        }

        for (String suffix : this.relodableSuffixes)
        {
            if (link.path.endsWith(suffix))
            {
                return true;
            }
        }

        return false;
    }

    public void saveConfig(String id, MapType config)
    {
        if (config == null)
        {
            return;
        }

        Link modelLink = Link.assets(MODELS_PREFIX + id);
        MapType baseConfig = config.copy().asMap();

        this.writeConfigIfChanged(modelLink, CONFIG_FILE, baseConfig);
    }

    private void writeConfigIfChanged(Link modelLink, String fileName, MapType config)
    {
        File file = BBSMod.getProvider().getFile(modelLink.combine(fileName));

        if (file != null)
        {
            try
            {
                if (file.exists())
                {
                    BaseType existing = DataToString.fromString(IOUtils.readText(file));

                    if (BaseType.isMap(existing) && existing.asMap().equals(config))
                    {
                        return;
                    }
                }

                IOUtils.writeText(file, DataToString.toString(config, true));
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    public boolean renameModel(String from, String to)
    {
        Link fromLink = Link.assets(MODELS_PREFIX + from);
        Link toLink = Link.assets(MODELS_PREFIX + to);
        File fromFile = BBSMod.getProvider().getFile(fromLink);
        File toFile = BBSMod.getProvider().getFile(toLink);

        if (fromFile != null && fromFile.exists() && fromFile.isDirectory() && toFile != null && !toFile.exists())
        {
            return fromFile.renameTo(toFile);
        }

        return false;
    }

    /**
     * Watch dog listener implementation. This is a pretty bad hardcoded
     * solution that would only work for the cubic model loader.
     */
    @Override
    public void accept(Path path, WatchDogEvent event)
    {
        Link link = BBSMod.getProvider().getLink(path.toFile());

        if (link == null)
        {
            return;
        }

        if (this.isRelodable(link))
        {
            this.availableKeysCache = null;
            String key = StringUtils.parentPath(link.path.substring(MODELS_PREFIX.length()));

            if (link.path.endsWith("/" + CONFIG_FILE) || link.path.endsWith("/" + DYNAMIC_CONFIG_FILE))
            {
                this.failedModels.remove(key);
                return;
            }

            ModelInstance model = this.models.remove(key);

            this.failedModels.remove(key);

            if (model != null)
            {
                /* VAO deletion must happen on the render/GL thread — the watchdog runs
                 * on a file-system watcher thread that has no OpenGL context. */
                final ModelInstance toDelete = model;

                MinecraftClient.getInstance().execute(() ->
                {
                    toDelete.delete();

                    if (toDelete.model instanceof BOBJModel bobjModel)
                    {
                        bobjModel.delete();
                    }
                });
            }
        }
    }
}

