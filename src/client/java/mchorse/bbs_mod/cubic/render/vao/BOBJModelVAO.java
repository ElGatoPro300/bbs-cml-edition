package mchorse.bbs_mod.cubic.render.vao;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSUniform;
import mchorse.bbs_mod.cubic.render.CubicRenderer;
import mchorse.bbs_mod.forms.renderers.utils.BillboardRenderLayers;
import mchorse.bbs_mod.forms.renderers.utils.ModelEffectPass;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.iris.FormColorGradePatch;
import mchorse.bbs_mod.utils.iris.ShaderOpacityPatch;
import mchorse.bbs_mod.utils.joml.Matrices;

import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

public class BOBJModelVAO
{
    public BOBJLoader.CompiledData data;
    public BOBJArmature armature;

    protected int vao;
    protected int count;

    /* GL buffers */
    public int vertexBuffer;
    public int normalBuffer;
    public int lightBuffer;
    public int texCoordBuffer;
    public int tangentBuffer;
    public int midTextureBuffer;

    protected float[] tmpVertices;
    protected float[] tmpNormals;
    protected int[] tmpLight;
    protected float[] tmpTangents;
    protected int[] dominantBonePerTriangle;

    private final Map<Integer, Link> fullOverrides = new HashMap<>();
    private final Map<Integer, Float> partialOverrides = new HashMap<>();
    private final Set<Integer> colorOverrideBones = new HashSet<>();
    private final Set<Integer> overridden = new HashSet<>();
    private final Color scratchDrawColor = new Color();

    public BOBJModelVAO(BOBJLoader.CompiledData data, BOBJArmature armature)
    {
        this.data = data;
        this.armature = armature;

        this.initBuffers();
    }

    /**
     * Initiate buffers. This method is responsible for allocating 
     * buffers for the data to be passed to VBOs and also generating the 
     * VBOs themselves. 
     */
    protected void initBuffers()
    {
        int previousVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

        this.vao = GL30.glGenVertexArrays();

        GL30.glBindVertexArray(this.vao);

        this.vertexBuffer = GL30.glGenBuffers();
        this.normalBuffer = GL30.glGenBuffers();
        this.lightBuffer = GL30.glGenBuffers();
        this.texCoordBuffer = GL30.glGenBuffers();
        this.tangentBuffer = GL30.glGenBuffers();
        this.midTextureBuffer = GL30.glGenBuffers();

        this.count = this.data.normData.length / 3;
        this.tmpVertices = new float[this.data.posData.length];
        this.tmpNormals = new float[this.data.normData.length];
        this.tmpLight = new int[this.data.posData.length];
        this.tmpTangents = new float[this.count * 4];
        this.dominantBonePerTriangle = new int[this.count / 3];
        this.buildDominantBones();

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, this.vertexBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, this.data.posData, GL30.GL_DYNAMIC_DRAW);
        GL30.glVertexAttribPointer(Attributes.POSITION, 3, GL30.GL_FLOAT, false, 0, 0);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, this.normalBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, this.data.normData, GL30.GL_DYNAMIC_DRAW);
        GL30.glVertexAttribPointer(Attributes.NORMAL, 3, GL30.GL_FLOAT, false, 0, 0);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, this.lightBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, this.tmpLight, GL30.GL_DYNAMIC_DRAW);
        GL30.glVertexAttribIPointer(Attributes.LIGHTMAP_UV, 2, GL30.GL_INT, 0, 0);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, this.texCoordBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, this.data.texData, GL30.GL_STATIC_DRAW);
        GL30.glVertexAttribPointer(Attributes.TEXTURE_UV, 2, GL30.GL_FLOAT, false, 0, 0);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, this.tangentBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, this.tmpTangents, GL30.GL_STATIC_DRAW);
        GL30.glVertexAttribPointer(Attributes.TANGENTS, 4, GL30.GL_FLOAT, false, 0, 0);

        float[] midTexCoords = ModelVAOData.calculateMidTexCoords(this.data.texData);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, this.midTextureBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, midTexCoords, GL30.GL_STATIC_DRAW);
        GL30.glVertexAttribPointer(Attributes.MID_TEXTURE_UV, 2, GL30.GL_FLOAT, false, 0, 0);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(previousVAO);
    }

    /**
     * Clean up resources which were used by this
     */
    public void delete()
    {
        GL30.glDeleteVertexArrays(this.vao);

        GL15.glDeleteBuffers(this.vertexBuffer);
        GL15.glDeleteBuffers(this.normalBuffer);
        GL15.glDeleteBuffers(this.lightBuffer);
        GL15.glDeleteBuffers(this.texCoordBuffer);
        GL15.glDeleteBuffers(this.tangentBuffer);
        GL15.glDeleteBuffers(this.midTextureBuffer);
    }

    /**
     * Update this mesh. This method is responsible for applying 
     * matrix transformations to vertices and normals according to its 
     * bone owners and these bone influences.
     */
    public void updateMesh(StencilMap stencilMap)
    {
        this.updateCpuMesh(stencilMap, true);
    }

    private void updateCpuMesh(StencilMap stencilMap, boolean upload)
    {
        Vector4f sum = new Vector4f();
        Vector4f result = new Vector4f(0F, 0F, 0F, 0F);
        Vector3f sumNormal = new Vector3f();
        Vector3f resultNormal = new Vector3f();

        float[] oldVertices = this.data.posData;
        float[] newVertices = this.tmpVertices;
        float[] oldNormals = this.data.normData;
        float[] newNormals = this.tmpNormals;

        Matrix4f[] matrices = this.armature.matrices;

        for (int i = 0, c = this.count; i < c; i++)
        {
            int count = 0;
            float maxWeight = -1;
            int lightBone = -1;

            for (int w = 0; w < 4; w++)
            {
                float weight = this.data.weightData[i * 4 + w];

                if (weight > 0)
                {
                    int index = this.data.boneIndexData[i * 4 + w];

                    sum.set(oldVertices[i * 3], oldVertices[i * 3 + 1], oldVertices[i * 3 + 2], 1F);
                    matrices[index].transform(sum);
                    result.add(sum.mul(weight));

                    sumNormal.set(oldNormals[i * 3], oldNormals[i * 3 + 1], oldNormals[i * 3 + 2]);
                    Matrices.TEMP_3F.set(matrices[index]).transform(sumNormal);
                    resultNormal.add(sumNormal.mul(weight));

                    count++;

                    if (weight > maxWeight)
                    {
                        lightBone = index;
                        maxWeight = weight;
                    }
                }
            }

            if (count == 0)
            {
                result.set(oldVertices[i * 3], oldVertices[i * 3 + 1], oldVertices[i * 3 + 2], 1F);
                resultNormal.set(oldNormals[i * 3], oldNormals[i * 3 + 1], oldNormals[i * 3 + 2]);
            }

            result.x /= result.w;
            result.y /= result.w;
            result.z /= result.w;

            newVertices[i * 3] = result.x;
            newVertices[i * 3 + 1] = result.y;
            newVertices[i * 3 + 2] = result.z;

            newNormals[i * 3] = resultNormal.x;
            newNormals[i * 3 + 1] = resultNormal.y;
            newNormals[i * 3 + 2] = resultNormal.z;

            result.set(0F, 0F, 0F, 0F);
            resultNormal.set(0F, 0F, 0F);

            boolean allowBone = true;
            if (stencilMap != null && stencilMap.allowedBones != null && lightBone >= 0)
            {
                BOBJBone bone = this.getBoneByIndex(lightBone);
                allowBone = bone != null && stencilMap.allowedBones.contains(bone.name);
            }

            if (stencilMap != null)
            {
                this.tmpLight[i * 2] = Math.max(0, stencilMap.increment ? (allowBone ? lightBone : 0) : 0);
                this.tmpLight[i * 2 + 1] = 0;
            }
        }

        this.processData(newVertices, newNormals);

        if (!upload)
        {
            return;
        }

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vertexBuffer);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, newVertices);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.normalBuffer);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, newNormals);

        if (BBSRendering.isIrisShadersEnabled())
        {
            BBSRendering.calculateTangents(this.tmpTangents, newVertices, newNormals, this.data.texData);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.tangentBuffer);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, this.tmpTangents);
        }

        if (stencilMap != null)
        {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.lightBuffer);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, this.tmpLight);
        }

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    public void renderLayer(MatrixStack stack, Color color, int light, int overlay, Link defaultTexture, boolean cull)
    {
        this.renderLayer(stack, color, light, overlay, defaultTexture, cull, null, null);
    }

    public void renderLayer(MatrixStack stack, Color color, int light, int overlay, Link defaultTexture, boolean cull, ShaderProgram shader, StencilMap stencilMap)
    {
        /* Reuse weighted skinning and the simple-player joint deformation without
         * requiring the legacy transform-feedback program or raw VAO draw. */
        this.updateCpuMesh(stencilMap, false);

        if (shader != null)
        {
            ModelVAORenderer.beginCpuGeometry(shader);
        }

        for (int first = 0; first < this.dominantBonePerTriangle.length;)
        {
            int boneIndex = this.dominantBonePerTriangle[first];
            int end = first + 1;

            while (end < this.dominantBonePerTriangle.length && this.dominantBonePerTriangle[end] == boneIndex)
            {
                end++;
            }

            BOBJBone bone = this.getBoneByIndex(boneIndex);
            if (stencilMap != null && bone != null && !stencilMap.isBoneAllowed(bone.name))
            {
                first = end;

                continue;
            }

            if (shader != null)
            {
                if (bone != null)
                {
                    BobjBoneDrawEffects.applyGroupUniforms(bone);
                }
                else
                {
                    BobjBoneDrawEffects.restoreGroupUniforms();
                }
            }

            Color tint = color.copy();

            if (bone != null && (shader == null || !bone.color.hasActiveTransform() || stencilMap != null))
            {
                tint.mul(bone.color);
            }

            int boneLight = bone == null ? light : BobjBoneDrawEffects.computeDrawLight(bone, light, stencilMap);
            if (stencilMap != null)
            {
                boneLight = stencilMap.increment ? Math.max(0, boneIndex) : 0;
            }

            float blend = bone == null || bone.texture == null ? 0F : Math.max(0F, Math.min(1F, bone.textureBlend));

            if (blend < 1F)
            {
                this.drawLayerRange(stack, tint, boneLight, overlay, defaultTexture, cull, first * 3, end * 3, 1F - blend, shader, stencilMap);
            }

            if (blend > 0F)
            {
                this.drawLayerRange(stack, tint, boneLight, overlay, bone.texture, cull, first * 3, end * 3, blend, shader, stencilMap);
            }

            first = end;
        }
    }

    private void drawLayerRange(MatrixStack stack, Color color, int light, int overlay, Link link, boolean cull, int first, int end, float factor, ShaderProgram shader, StencilMap stencilMap)
    {
        Texture texture = BBSModClient.getTextures().getTexture(link);
        float alpha = color.a * factor;

        if (texture == null || alpha <= 0.001F)
        {
            return;
        }

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES,
            VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
        MatrixStack.Entry entry = stack.peek();

        for (int i = first; i < end; i++)
        {
            int xyz = i * 3;
            int uv = i * 2;

            builder.vertex(entry.getPositionMatrix(), this.tmpVertices[xyz], this.tmpVertices[xyz + 1], this.tmpVertices[xyz + 2])
                .color(color.r, color.g, color.b, alpha).texture(this.data.texData[uv], this.data.texData[uv + 1])
                .overlay(overlay).light(light)
                .normal(entry, this.tmpNormals[xyz], this.tmpNormals[xyz + 1], this.tmpNormals[xyz + 2]);
        }

        if (shader != null)
        {
            ModelVAORenderer.setupUniformsCpuPretransformed(shader, new Matrix4f(stack.peek().getPositionMatrix()).invert());
            BBSUniform.set(shader, "TextureBlendActive", 0F);

            if (stencilMap != null)
            {
                BBSUniform.set(shader, "Target", stencilMap.objectIndex);
            }

            boolean overlayPass = ModelVAORenderer.isPaintOverlayPass() || ModelVAORenderer.isColorTintOverlayPass() || ModelVAORenderer.isColorGradeOverlayPass();
            ModelEffectPass.draw(builder.end(), texture, shader, stencilMap != null,
                stencilMap != null || (!overlayPass && alpha >= ShaderOpacityPatch.LIVE_DEPTH_WRITE_ALPHA), cull, overlayPass);
        }
        else
        {
            BillboardRenderLayers.draw(builder.end(), texture, texture.isLinear(), false,
                alpha >= ShaderOpacityPatch.LIVE_DEPTH_WRITE_ALPHA, cull);
        }
    }

    protected void processData(float[] newVertices, float[] newNormals)
    {}

    protected void buildDominantBones()
    {
        for (int triangle = 0, triCount = this.dominantBonePerTriangle.length; triangle < triCount; triangle++)
        {
            int base = triangle * 3;
            int a = this.getDominantBoneForVertex(base);
            int b = this.getDominantBoneForVertex(base + 1);
            int c = this.getDominantBoneForVertex(base + 2);

            if (a == b || a == c)
            {
                this.dominantBonePerTriangle[triangle] = a;
            }
            else if (b == c)
            {
                this.dominantBonePerTriangle[triangle] = b;
            }
            else
            {
                this.dominantBonePerTriangle[triangle] = a;
            }
        }
    }

    protected int getDominantBoneForVertex(int vertex)
    {
        int base = vertex * 4;
        float max = -1F;
        int bone = -1;

        for (int i = 0; i < 4; i++)
        {
            float weight = this.data.weightData[base + i];
            int boneIndex = this.data.boneIndexData[base + i];

            if (boneIndex >= 0 && weight > max)
            {
                max = weight;
                bone = boneIndex;
            }
        }

        return bone;
    }

    protected BOBJBone getBoneByIndex(int index)
    {
        for (BOBJBone bone : this.armature.orderedBones)
        {
            if (bone.index == index)
            {
                return bone;
            }
        }

        return null;
    }

    protected BOBJBone getBoneByName(String name)
    {
        for (BOBJBone bone : this.armature.orderedBones)
        {
            if (bone.name.equals(name))
            {
                return bone;
            }
        }

        return null;
    }

    protected void renderStencilPickPriority(StencilMap stencilMap)
    {
        if (stencilMap == null || !stencilMap.increment)
        {
            return;
        }

        /* Keep depth on so nearer limbs (head in front of torso) stay pickable. Priority
         * bones only win z-ties / coplanar overlaps against parents drawn earlier. */
        BBSRendering.enableDepthTest();
        BBSRendering.depthMask(true);

        for (String boneId : CubicRenderer.STENCIL_PICK_PRIORITY_BONES)
        {
            BOBJBone bone = this.getBoneByName(boneId);

            if (bone != null)
            {
                this.drawTriangles((boneIndex) -> boneIndex == bone.index);
            }
        }
    }

    protected void drawTriangles(IntPredicate predicate)
    {
        int start = -1;

        for (int i = 0; i < this.dominantBonePerTriangle.length; i++)
        {
            boolean draw = predicate.test(this.dominantBonePerTriangle[i]);

            if (draw && start == -1)
            {
                start = i;
            }
            else if (!draw && start != -1)
            {
                GL30.glDrawArrays(GL30.GL_TRIANGLES, start * 3, (i - start) * 3);
                start = -1;
            }
        }

        if (start != -1)
        {
            GL30.glDrawArrays(GL30.GL_TRIANGLES, start * 3, (this.dominantBonePerTriangle.length - start) * 3);
        }
    }

    protected void collectBoneOverrides()
    {
        this.fullOverrides.clear();
        this.partialOverrides.clear();
        this.colorOverrideBones.clear();
        this.overridden.clear();

        for (BOBJBone bone : this.armature.orderedBones)
        {
            if (bone.texture != null)
            {
                float blend = bone.textureBlend;

                if (blend >= 1F)
                {
                    this.fullOverrides.put(bone.index, bone.texture);
                }
                else if (blend > 0F)
                {
                    this.partialOverrides.put(bone.index, blend);
                }
            }

            if (BobjBoneDrawEffects.hasCustomColorEffects(bone))
            {
                this.colorOverrideBones.add(bone.index);
            }
        }

        this.overridden.addAll(this.fullOverrides.keySet());
        this.overridden.addAll(this.partialOverrides.keySet());
        this.overridden.addAll(this.colorOverrideBones);
    }

    protected void drawBoneOverride(ShaderProgram shader, MatrixStack stack, float r, float g, float b, float a, int light, int overlay, Link defaultTexture, BOBJBone bone)
    {
        Link fullTexture = this.fullOverrides.get(bone.index);
        Float blend = this.partialOverrides.get(bone.index);
        boolean hasColor = this.colorOverrideBones.contains(bone.index);

        if (fullTexture != null)
        {
            this.bindDrawTexture(fullTexture);
            ModelVAORenderer.clearTextureBlend();
        }
        else if (blend != null)
        {
            if (defaultTexture != null)
            {
                this.bindDrawTexture(defaultTexture);
            }

            ModelVAORenderer.setTextureBlend(bone.texture, blend);
        }
        else if (defaultTexture != null)
        {
            this.bindDrawTexture(defaultTexture);
            ModelVAORenderer.clearTextureBlend();
        }

        float drawR = r;
        float drawG = g;
        float drawB = b;
        float drawA = a;
        int drawLight = light;

        if (hasColor)
        {
            BobjBoneDrawEffects.applyGroupUniforms(bone);
            BobjBoneDrawEffects.computeDrawColor(bone, r, g, b, a, this.scratchDrawColor);
            drawR = this.scratchDrawColor.r;
            drawG = this.scratchDrawColor.g;
            drawB = this.scratchDrawColor.b;
            drawA = this.scratchDrawColor.a;
            drawLight = BobjBoneDrawEffects.computeDrawLight(bone, light, null);
        }

        try
        {
            this.rebindShaderSamplers(shader, stack, drawR, drawG, drawB, drawA, drawLight, overlay);
            this.drawTriangles((boneIndex) -> boneIndex == bone.index);
        }
        finally
        {
            ModelVAORenderer.clearTextureBlend();

            if (hasColor)
            {
                BobjBoneDrawEffects.restoreGroupUniforms();
            }
        }
    }

    /**
     * BBS {@link ShaderProgram#bind()} snapshots Sampler* from {@link RenderSystem} at
     * {@link ModelVAORenderer#setupUniforms}. Skin must be bound before that — binding after
     * leaves Sampler0 on whatever Iris left (featureless tinted silhouette, no skin).
     */
    protected void bindDrawTexture(Link texture)
    {
        if (texture != null)
        {
            BBSModClient.getTextures().bindTexture(texture);
        }
    }

    protected void rebindShaderSamplers(ShaderProgram shader, MatrixStack stack, float r, float g, float b, float a, int light, int overlay)
    {
        BBSRendering.bindProgram(shader);
        ModelVAORenderer.setupUniforms(stack, shader);
        GL30.glBindVertexArray(this.vao);

        GL30.glDisableVertexAttribArray(Attributes.COLOR);
        GL30.glDisableVertexAttribArray(Attributes.OVERLAY_UV);
        GL30.glDisableVertexAttribArray(Attributes.LIGHTMAP_UV);

        GL30.glVertexAttrib4f(Attributes.COLOR, r, g, b, a);
        GL30.glVertexAttribI2i(Attributes.OVERLAY_UV, overlay & '\uffff', overlay >> 16 & '\uffff');
        GL30.glVertexAttribI2i(Attributes.LIGHTMAP_UV, light & '\uffff', light >> 16 & '\uffff');
    }

    public void render(ShaderProgram shader, MatrixStack stack, float r, float g, float b, float a, StencilMap stencilMap, int light, int overlay, Link defaultTexture)
    {
        boolean hasShaders = BBSRendering.isIrisShadersEnabled();

        int currentVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int currentElementArrayBuffer = GL30.glGetInteger(GL30.GL_ELEMENT_ARRAY_BUFFER_BINDING);

        if (defaultTexture != null)
        {
            this.bindDrawTexture(defaultTexture);
        }

        BBSRendering.bindProgram(shader);
        ModelVAORenderer.setupUniforms(stack, shader);

        ShaderOpacityPatch.uploadShadowFormUniform();
        FormColorGradePatch.uploadToCurrentProgram();

        int textureID = BBSRendering.getBoundTexture();
        GlStateManager._activeTexture(GL30.GL_TEXTURE0);
        GlStateManager._bindTexture(textureID);

        GL30.glBindVertexArray(this.vao);

        /* Constant color/light/overlay must be set after VAO bind (same as ModelVAO). Setting
         * them before bind loses form alpha under Iris deferred redraws — opaque silhouette. */
        GL30.glDisableVertexAttribArray(Attributes.COLOR);
        GL30.glDisableVertexAttribArray(Attributes.OVERLAY_UV);
        GL30.glDisableVertexAttribArray(Attributes.LIGHTMAP_UV);

        GL30.glVertexAttrib4f(Attributes.COLOR, r, g, b, a);
        GL30.glVertexAttribI2i(Attributes.OVERLAY_UV, overlay & '\uffff', overlay >> 16 & '\uffff');
        GL30.glVertexAttribI2i(Attributes.LIGHTMAP_UV, light & '\uffff', light >> 16 & '\uffff');

        GL30.glEnableVertexAttribArray(Attributes.POSITION);
        GL30.glEnableVertexAttribArray(Attributes.TEXTURE_UV);
        GL30.glEnableVertexAttribArray(Attributes.NORMAL);

        if (stencilMap != null) GL30.glEnableVertexAttribArray(Attributes.LIGHTMAP_UV);
        if (hasShaders) GL30.glEnableVertexAttribArray(Attributes.TANGENTS);
        if (hasShaders) GL30.glEnableVertexAttribArray(Attributes.MID_TEXTURE_UV);

        if (stencilMap == null)
        {
            this.collectBoneOverrides();

            if (this.overridden.isEmpty())
            {
                GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, this.count);
            }
            else
            {
                this.drawTriangles((bone) -> bone < 0 || !this.overridden.contains(bone));

                for (BOBJBone bone : this.armature.orderedBones)
                {
                    if (!this.overridden.contains(bone.index))
                    {
                        continue;
                    }

                    this.drawBoneOverride(shader, stack, r, g, b, a, light, overlay, defaultTexture, bone);
                }
            }
        }
        else
        {
            GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, this.count);
            this.renderStencilPickPriority(stencilMap);
        }

        GL30.glDisableVertexAttribArray(Attributes.POSITION);
        GL30.glDisableVertexAttribArray(Attributes.TEXTURE_UV);
        GL30.glDisableVertexAttribArray(Attributes.NORMAL);

        if (stencilMap != null) GL30.glDisableVertexAttribArray(Attributes.LIGHTMAP_UV);
        if (hasShaders) GL30.glDisableVertexAttribArray(Attributes.TANGENTS);
        if (hasShaders) GL30.glDisableVertexAttribArray(Attributes.MID_TEXTURE_UV);

        BBSRendering.unbindProgram();

        GL30.glBindVertexArray(currentVAO);

        /* ELEMENT_ARRAY_BUFFER binding is VAO state; binding it with VAO 0 raises
         * GL_INVALID_OPERATION ("Array object is not active") under OpenGL debug. */
        if (currentVAO != 0)
        {
            GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, currentElementArrayBuffer);
        }
    }
}
