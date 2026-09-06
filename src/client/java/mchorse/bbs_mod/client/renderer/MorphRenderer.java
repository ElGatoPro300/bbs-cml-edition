package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.selectors.ISelectorOwnerProvider;
import mchorse.bbs_mod.selectors.SelectorOwner;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.morphing.UIMorphingPanel;
import mchorse.bbs_mod.utils.interps.Lerps;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.RotationAxis;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

public class MorphRenderer
{
    public static boolean hidePlayer = false;

    public static boolean renderPlayer(AbstractClientPlayerEntity player, float bodyYaw, float g, MatrixStack matrixStack, OrderedRenderCommandQueue renderCommandQueue, int i)
    {
        Morph morph = Morph.getMorph(player);
        Form playerForm = morph != null ? morph.getForm() : null;

        UIBaseMenu menu = UIScreen.getCurrentMenu();
        if (menu instanceof UIDashboard dashboard)
        {
            UIDashboardPanel panel = dashboard.getPanels().panel;

            if (panel instanceof UIMorphingPanel morphingPanel && morphingPanel.palette.editor.isEditing())
            {
                Form editingForm = morphingPanel.palette.editor.form;

                if (!areFormsEquivalent(editingForm, playerForm))
                {
                    return true;
                }
            }
        }

        if (hidePlayer)
        {
            if (FormUtilsClient.getCurrentForm() instanceof MobForm form && !form.isPlayer())
            {
                return true;
            }
        }

        if (morph != null && morph.getForm() != null)
        {
            /* Spectator: vanilla only draws a translucent disembodied head. Rendering the
             * full morph cancels PlayerEntityRenderer and looks like survival. Fall through
             * so other spectators / F5 see the normal semi-transparent head. */
            if (player.isSpectator())
            {
                return false;
            }

            if (canRender(playerForm))
            {
                GlStateManager._enableDepthTest();

                boolean worldPass = BBSRendering.isRenderingWorld();

                /* InventoryScreen.drawEntity uses ENTITY_IN_UI for the player
                 * preview, then INVENTORY after. Forms must keep those same
                 * entity lights. World morphs keep level diffuse like model blocks. */
                if (worldPass)
                {
                    BBSRendering.setupWorldLevelDiffuseLighting();
                }
                else
                {
                    MinecraftClient.getInstance().gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.ENTITY_IN_UI);
                }

                int overlay = OverlayTexture.DEFAULT_UV;

                matrixStack.push();
                matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));

                FormRenderingContext morphContext = new FormRenderingContext()
                    .set(FormRenderType.ENTITY, morph.entity, matrixStack, i, overlay, g)
                    .camera(MinecraftClient.getInstance().gameRenderer.getCamera());

                /* Inventory / non-world drawEntity: soft must draw live (queues never flush). */
                if (!worldPass)
                {
                    morphContext.inUI();
                }

                FormUtilsClient.render(morph.getForm(), morphContext);

                if (morph.entity.getFireTicks() > 0)
                {
                    MorphFireRenderer.render(
                        matrixStack,
                        (VertexConsumerProvider) null,
                        morph.entity,
                        morph.getForm(),
                        g,
                        MinecraftClient.getInstance().gameRenderer.getCamera(),
                        false
                    );
                }

                matrixStack.pop();

                if (worldPass)
                {
                    BBSRendering.restoreWorldRenderState();
                }
                else
                {
                    MinecraftClient.getInstance().gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.ITEMS_3D);
                    BBSRendering.restoreWorldRenderState();
                }
            }

            return true;
        }

        return false;
    }

    private static boolean canRender(Form playerForm)
    {
        UIBaseMenu menu = UIScreen.getCurrentMenu();
        
        if (menu instanceof UIDashboard dashboard)
        {
            UIDashboardPanel panel = dashboard.getPanels().panel;

            if (panel instanceof UIMorphingPanel morphingPanel && morphingPanel.palette.editor.isEditing())
            {
                return areFormsEquivalent(morphingPanel.palette.editor.form, playerForm);
            }
        }

        return true;
    }

    private static boolean areFormsEquivalent(Form a, Form b)
    {
        if (a == b) return true;
        if (a == null || b == null) return false;

        MapType dataA = FormUtils.toData(a);
        MapType dataB = FormUtils.toData(b);

        return dataA != null && dataA.equals(dataB);
    }

    public static boolean renderLivingEntity(LivingEntity livingEntity, float bodyYaw, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, int o)
    {
        if (!(livingEntity instanceof ISelectorOwnerProvider))
        {
            return false;
        }

        SelectorOwner owner = ((ISelectorOwnerProvider) livingEntity).getOwner();

        owner.check();

        Form form = owner.getForm();

        if (form != null)
        {
            GlStateManager._enableDepthTest();

            matrixStack.push();
            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));

            FormUtilsClient.render(form, new FormRenderingContext()
                .set(FormRenderType.ENTITY, owner.entity, matrixStack, i, o, g)
                .camera(MinecraftClient.getInstance().gameRenderer.getCamera()));

            if (owner.entity.getFireTicks() > 0)
            {
                MorphFireRenderer.render(
                    matrixStack,
                    vertexConsumerProvider,
                    owner.entity,
                    form,
                    g,
                    MinecraftClient.getInstance().gameRenderer.getCamera(),
                    false
                );
            }

            matrixStack.pop();

            BBSRendering.restoreWorldRenderState();

            return true;
        }

        return false;
    }
}
