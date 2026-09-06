package mchorse.bbs_mod.cubic.model.loaders;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.bobj.BOBJAction;
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
import mchorse.bbs_mod.cubic.model.gltf.GLTFConverter;
import mchorse.bbs_mod.cubic.model.gltf.GLTFParser;
import mchorse.bbs_mod.cubic.model.gltf.data.GLTF;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.math.Constant;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.math.molang.expressions.MolangValue;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.resources.Pixels;

import net.minecraft.client.MinecraftClient;

import org.lwjgl.opengl.GL11;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class GLTFModelLoader implements IModelLoader
{
    @Override
    public ModelInstance load(String id, ModelManager models, Link model, Collection<Link> links, MapType config)
    {
        Link gltfLink = null;
        
        for (Link l : links)
        {
            if (l.path.endsWith(".gltf") || l.path.endsWith(".glb"))
            {
                gltfLink = l;
                break;
            }
        }
        
        if (gltfLink == null) return null;
        
        // Try to find an external texture first
        Link textureLink = null;
        for (Link l : links)
        {
            if (l.path.endsWith(".png"))
            {
                textureLink = l;
                break;
            }
        }
        
        try (InputStream stream = models.provider.getAsset(gltfLink))
        {
            GLTF gltf = GLTFParser.parse(stream);
            
            // Load Buffers
            if (gltf.buffers != null)
            {
                for (GLTF.GLTFBuffer buffer : gltf.buffers)
                {
                    if (buffer.data == null && buffer.uri != null)
                    {
                        if (buffer.uri.startsWith("data:"))
                        {
                            String b64 = buffer.uri.substring(buffer.uri.indexOf(",") + 1);
                            buffer.data = Base64.getDecoder().decode(b64);
                        }
                        else
                        {
                             Link bufferLink = new Link(gltfLink.source, gltfLink.path.substring(0, gltfLink.path.lastIndexOf('/')+1) + buffer.uri);
                             try (InputStream bufStream = models.provider.getAsset(bufferLink))
                             {
                                 buffer.data = bufStream.readAllBytes();
                             }
                        }
                    }
                }
            }
            
            // Extract embedded texture if no external texture found
            if (textureLink == null && gltf.images != null && !gltf.images.isEmpty())
            {
                GLTF.GLTFImage image = gltf.images.get(0);
                byte[] imageData = null;
                
                if (image.bufferView >= 0 && gltf.bufferViews != null && image.bufferView < gltf.bufferViews.size())
                {
                    GLTF.GLTFBufferView view = gltf.bufferViews.get(image.bufferView);
                    GLTF.GLTFBuffer buffer = gltf.buffers.get(view.buffer);
                    if (buffer.data != null)
                    {
                        imageData = new byte[view.byteLength];
                        System.arraycopy(buffer.data, view.byteOffset, imageData, 0, view.byteLength);
                    }
                }
                else if (image.uri != null && image.uri.startsWith("data:"))
                {
                    String b64 = image.uri.substring(image.uri.indexOf(",") + 1);
                    imageData = Base64.getDecoder().decode(b64);
                }
                
                if (imageData != null)
                {
                    try
                    {
                        Pixels pixels = Pixels.fromPNGStream(new ByteArrayInputStream(imageData));
                        if (pixels != null)
                        {
                            Link embeddedLink = new Link(gltfLink.source, gltfLink.path + "/embedded_texture.png");
                            
                            /* RenderSystem.recordRenderCall(...) was removed in 1.21.11; MinecraftClient.execute(...)
                             * schedules this on the client/render thread the same way. */
                            MinecraftClient.getInstance().execute(() ->
                            {
                                try
                                {
                                    Texture texture = Texture.textureFromPixels(pixels, GL11.GL_NEAREST);
                                    
                                    // Register to TextureManager manually
                                    BBSModClient.getTextures().textures.put(embeddedLink, texture);
                                    System.out.println("GLTFModelLoader: Loaded embedded texture for " + id);
                                }
                                catch (Exception e)
                                {
                                    e.printStackTrace();
                                }
                            });
                            
                            textureLink = embeddedLink;
                        }
                    }
                    catch (Exception e)
                    {
                        System.err.println("GLTFModelLoader: Failed to load embedded texture");
                        e.printStackTrace();
                    }
                }
            }
            
            BOBJLoader.BOBJData data = GLTFConverter.convert(gltf);
            
            if (!data.meshes.isEmpty())
            {
                BOBJLoader.BOBJMesh mesh = data.meshes.get(0);
                BOBJLoader.CompiledData compiled = BOBJLoader.compileMesh(data, mesh);
                
                data.initiateArmatures();
                
                BOBJModel bobjModel = new BOBJModel(mesh.armature, List.of(compiled), false);
                
                Animations animations = this.convertAnimations(data, new Animations(models.parser));
                
                ModelInstance instance = new ModelInstance(id, bobjModel, animations, textureLink);
                instance.applyConfig(config);
                
                return instance;
            }
        }
        catch (Exception e)
        {
            System.err.println("Failed to load GLTF model '" + gltfLink + "': " + e.getMessage());
        }
        
        return null;
    }
    
    private Animations convertAnimations(BOBJLoader.BOBJData bobjData, Animations animations)
    {
        for (Map.Entry<String, BOBJAction> entry : bobjData.actions.entrySet())
        {
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
