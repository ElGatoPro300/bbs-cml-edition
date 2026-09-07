package mchorse.bbs_mod.ui.utils.pose;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormColorAdjustments;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormColorLayout;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UIEffectTransformCollapse;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UIDataContextMenu;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseManager;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import mchorse.bbs_mod.utils.resources.LinkUtils;

import com.mojang.blaze3d.systems.RenderSystem;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class UIPoseEditor extends UIElement
{
    public static final int TEXTURE_BEND_MIN_WIDTH = 72;

    private static final Map<String, String> LAST_LIMB_CACHE = new HashMap<>();
    private static final Map<String, Set<String>> MARKED_BONES_CACHE = new HashMap<>();
    private static final String MARKED_BONES_FILE = "marked_bones.json";
    private static boolean MARKED_BONES_LOADED = false;

    public UISearchList<String> groups;
    public UIElement extra;
    public UIStringList groupsList;
    public UIStringList categories;
    public UITrackpad fix;
    public UIButton pickTexture;
    public UIColor color;
    public UIEffectTransformCollapse colorTransform;
    public UIColor paintColor;
    public UITrackpad paintIntensity;
    public UIEffectTransformCollapse paintTransform;
    public UIFormColorAdjustments colorAdjustments;
    public UIColor glowingColor;
    public UITrackpad glowIntensity;
    public UIEffectTransformCollapse glowTransform;
    public UIToggle lighting;
    public UIToggle noShading;
    public UIElement paintSection;
    public UIElement glowSection;
    public UIPropTransform transform;
    public Runnable onChange;

    private String group = "";
    private Pose pose;
    protected IModel model;
    protected Map<String, String> flippedParts;
    /** Proveedor opcional para obtener la textura base del modelo cuando no hay override por hueso. */
    protected Supplier<Link> defaultTextureSupplier;
    /** Proveedor opcional del form a renderizar en la preview 3D del selector de texturas. */
    protected Supplier<Form> texturePreviewFormSupplier;
    /** Gestor de categorías de huesos (por grupo de pose). */
    protected BoneCategoriesManager boneCategories = new BoneCategoriesManager();
    private final List<String> allBones = new ArrayList<>();
    private final Set<String> markedBones = new HashSet<>();
    private boolean showOnlyMarked;
    private boolean invertLiveMirrorZ;
    private UIIcon invertLiveMirrorZButton;
    private UIIcon showOnlyMarkedButton;
    private String currentBone;
    /**
     * When true, pose footer shows Color extras (paint / glow / grade) and noshading.
     * Used for ModelForm pose and model Parts ({@code ModelConfig.parts}); both store the
     * same per-bone {@link PoseTransform} appearance fields.
     */
    private final boolean formAppearanceExtras;

    public UIPoseEditor()
    {
        this(true);
    }

    public UIPoseEditor(boolean formAppearanceExtras)
    {
        this.formAppearanceExtras = formAppearanceExtras;

        this.extra = new UIElement();
        this.extra.column().vertical().stretch();

        this.groupsList = new MarkableBoneList((l) ->
        {
            if (l != null && !l.isEmpty())
            {
                this.pickBone(l.get(0));
            }
        });
        this.groupsList.multi();
        this.groups = new UISearchList<>(this.groupsList);
        this.groups.label(UIKeys.GENERAL_SEARCH);
        /* Match Fix trackpad + transform block height so Bone list aligns with the numbers. */
        this.groups.h(UIStringList.DEFAULT_HEIGHT * 10 + 12);
        this.groups.list.background(0xFF141418);
        this.groups.list.scroll.cancelScrolling();
        this.groups.search.w(1F, -40);
        this.invertLiveMirrorZ = false;
        this.invertLiveMirrorZButton = new UIIcon(Icons.CONVERT, (b) -> this.toggleInvertLiveMirrorZ())
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                if (this.isActive())
                {
                    this.area.render(context.batcher, BBSSettings.primaryColor(Colors.A100));
                }

                super.renderSkin(context);
            }
        };
        this.invertLiveMirrorZButton.iconColor(Colors.LIGHTEST_GRAY);
        this.invertLiveMirrorZButton.hoverColor(Colors.WHITE);
        this.invertLiveMirrorZButton.activeColor(Colors.WHITE);
        this.invertLiveMirrorZButton.active(this.invertLiveMirrorZ);
        this.invertLiveMirrorZButton.tooltip(UIKeys.POSE_BONES_LIVE_MIRROR_INVERT_Z_TOOLTIP);
        this.invertLiveMirrorZButton.relative(this.groups).x(1F, -40).y(0).w(20).h(20);
        this.showOnlyMarkedButton = new UIIcon(() -> this.showOnlyMarked ? Icons.VISIBLE : Icons.FILTER, (b) -> this.toggleShowOnlyMarked());
        this.showOnlyMarked = BBSSettings.poseBonesFilterMarked != null && BBSSettings.poseBonesFilterMarked.get();
        this.showOnlyMarkedButton.active(this.showOnlyMarked);
        this.showOnlyMarkedButton.tooltip(UIKeys.POSE_BONES_FILTER_MARKED_TOOLTIP);
        this.showOnlyMarkedButton.relative(this.groups).x(1F, -20).y(0).w(20).h(20);
        this.groups.add(this.invertLiveMirrorZButton);
        this.groups.add(this.showOnlyMarkedButton);
        this.groups.list.context(() ->
        {
            UIDataContextMenu menu = new UIDataContextMenu(PoseManager.INSTANCE, this.group, () -> this.pose != null ? this.pose.toData() : new MapType(), this::pastePose);
            UIIcon flip = new UIIcon(Icons.CONVERT, (b) -> this.flipPose());

            flip.tooltip(UIKeys.POSE_CONTEXT_FLIP_POSE);
            menu.row.addBefore(menu.save, flip);

            return menu;
        });
        /* Lista de categorías a la derecha */
        this.categories = new UIStringList((l) -> {})
        {
            @Override
            public void render(UIContext context)
            {
                super.render(context);
                context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF3C3C3C);
            }
        };
        this.categories.background(0xFF141418).h(UIStringList.DEFAULT_HEIGHT * 10 - 8);
        this.categories.scroll.cancelScrolling();
        this.categories.context((menu) ->
        {
            String selectedCategory = this.categories.getCurrentFirst();

            menu.action(Icons.ADD, UIKeys.FORMS_CATEGORIES_CONTEXT_ADD_CATEGORY, () ->
            {
                UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                    UIKeys.POSE_CATEGORIES_MANAGE_TITLE,
                    UIKeys.POSE_CATEGORIES_MANAGE_CATEGORY_NAME,
                    (str) ->
                    {
                        if (str != null && !str.isEmpty())
                        {
                            this.boneCategories.addCategory(this.group, str);
                            this.refreshCategories();
                        }
                    }
                );
                UIOverlay.addOverlay(this.getContext(), panel);
            });

            if (selectedCategory != null && !selectedCategory.isEmpty())
            {
                menu.action(Icons.EDIT, UIKeys.FORMS_CATEGORIES_CONTEXT_RENAME_CATEGORY, () ->
                {
                    UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                        UIKeys.POSE_CATEGORIES_MANAGE_TITLE,
                        UIKeys.POSE_CATEGORIES_MANAGE_NEW_NAME,
                        (str) ->
                        {
                            if (str != null && !str.isEmpty())
                            {
                                this.boneCategories.renameCategory(this.group, selectedCategory, str);
                                this.refreshCategories();
                            }
                        }
                    );
                    UIOverlay.addOverlay(this.getContext(), panel);
                });

                menu.action(Icons.TRASH, UIKeys.FORMS_CATEGORIES_CONTEXT_REMOVE_CATEGORY, Colors.RED, () ->
                {
                    this.boneCategories.removeCategory(this.group, selectedCategory);
                    this.refreshCategories();
                });

                /* Ver huesos que pertenecen a la categoría seleccionada */
                menu.action(Icons.LIST, UIKeys.POSE_CATEGORIES_CONTEXT_VIEW_BONES, () ->
                {
                    String group = this.group;
                    List<String> bones = this.boneCategories.getBones(group, selectedCategory);

                    UISearchList<String> search = new UISearchList<>(new UIStringList(null));
                    UIList<String> list = search.list;

                    for (String g : bones) { list.add(g); }

                    UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(
                        UIKeys.POSE_CATEGORIES_VIEW_BONES_TITLE,
                        UIKeys.POSE_CATEGORIES_VIEW_BONES_DESCRIPTION,
                        (confirm) ->
                        {
                            if (confirm)
                            {
                                int index = list.getIndex();
                                String bone = CollectionUtils.getSafe(bones, index);
                                if (bone != null)
                                {
                                    this.selectBone(bone);
                                }
                            }
                        }
                    );

                    list.background();
                    /* Lista más alta y sin botones adicionales */
                    search.relative(panel.confirm).y(-5).w(1F).h(UIStringList.DEFAULT_HEIGHT * 12 + 20).anchor(0F, 1F);

                    /* Click derecho para eliminar el hueso de la categoría */
                    list.context((ctx) ->
                    {
                        ctx.action(Icons.TRASH, UIKeys.POSE_CATEGORIES_CONTEXT_REMOVE_BONE, Colors.RED, () ->
                        {
                            int idx = list.getIndex();
                            String bone = CollectionUtils.getSafe(bones, idx);
                            if (bone != null)
                            {
                                this.boneCategories.removeBone(group, selectedCategory, bone);
                                list.remove(bone);
                            }
                        });
                        ctx.autoKeys();
                    });

                    panel.content.add(search);
                    UIOverlay.addOverlay(this.getContext(), panel, 340, 360);
                });

                /* Separador visual no soportado por ContextMenuManager; omitido */

                String selectedBone = this.groups.list.getCurrentFirst();
                if (selectedBone != null && !selectedBone.isEmpty())
                {
                    menu.action(Icons.ADD, UIKeys.POSE_CATEGORIES_CONTEXT_ADD_SELECTED_BONE, () ->
                    {
                        this.boneCategories.addBone(this.group, selectedCategory, selectedBone);
                    });
                    menu.action(Icons.REMOVE, UIKeys.POSE_CATEGORIES_CONTEXT_REMOVE_SELECTED_BONE, () ->
                    {
                        this.boneCategories.removeBone(this.group, selectedCategory, selectedBone);
                    });
                }
            }

            menu.autoKeys();
        });
        this.fix = new UITrackpad((v) ->
        {
            String selectedCategory = this.categories != null ? this.categories.getCurrentFirst() : null;
            if (selectedCategory != null && !selectedCategory.isEmpty())
            {
                this.applyCategory((p) -> this.setFix(p, v.floatValue()));
            }
            else if (this.applyLiveMirror((p) -> this.setFix(p, v.floatValue())))
            {}
            else if (this.transform.getTransform() instanceof PoseTransform poseTransform)
            {
                this.setFix(poseTransform, v.floatValue());
            }

            if (this.onChange != null) this.onChange.run();
        });
        this.fix.limit(0D, 1D).increment(1D).values(0.1, 0.05D, 0.2D);
        this.fix.tooltip(UIKeys.POSE_CONTEXT_FIX_TOOLTIP);
        this.fix.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.applyChildren((p) -> this.setFix(p, (float) this.fix.getValue()));
                if (this.onChange != null) this.onChange.run();
            });

            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CATEGORIES_CONTEXT_APPLY_CATEGORY, () ->
            {
                this.applyCategory((p) -> this.setFix(p, (float) this.fix.getValue()));
                if (this.onChange != null) this.onChange.run();
            });
        });
        /* Botón para elegir textura de hueso (etiqueta fija ES/EN) */
        this.pickTexture = new UIButton(UIKeys.TEXTURE_PICK_BONE_TEXTURE, (b) ->
        {
            PoseTransform poseTransform = (PoseTransform) this.transform.getTransform();
            Link current = null;

            if (poseTransform != null && poseTransform.texture != null)
            {
                current = poseTransform.texture;
            }
            else if (this.defaultTextureSupplier != null)
            {
                current = this.defaultTextureSupplier.get();
            }

            UITexturePicker picker = UITexturePicker.open(this.getContext(), current, (l) ->
            {
                String selectedCategory = this.categories != null ? this.categories.getCurrentFirst() : null;
                if (selectedCategory != null && !selectedCategory.isEmpty())
                {
                    this.applyCategory((p) -> this.setTexture(p, l));
                }
                else if (this.applyLiveMirror((p) -> this.setTexture(p, l)))
                {}
                else if (this.transform.getTransform() instanceof PoseTransform pt)
                {
                    this.setTexture(pt, l);
                }

                if (this.onChange != null) this.onChange.run();
            });

            if (picker != null)
            {
                picker.withFormPreview(this.texturePreviewFormSupplier);
            }
        });
        this.pickTexture.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                PoseTransform t = (PoseTransform) this.transform.getTransform();
                Link chosen = t != null ? t.texture : null;
                this.applyChildren((p) -> this.setTexture(p, chosen));
                if (this.onChange != null) this.onChange.run();
            });
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CATEGORIES_CONTEXT_APPLY_CATEGORY, () ->
            {
                PoseTransform t = (PoseTransform) this.transform.getTransform();
                Link chosen = t != null ? t.texture : null;
                this.applyCategory((p) -> this.setTexture(p, chosen));
                if (this.onChange != null) this.onChange.run();
            });

            menu.action(Icons.CLOSE, UIKeys.GENERAL_NONE, () ->
            {
                if (this.applyLiveMirror((p) -> this.setTexture(p, null)))
                {
                    if (this.onChange != null) this.onChange.run();
                }
                else
                {
                    PoseTransform t = (PoseTransform) this.transform.getTransform();
                    if (t != null)
                    {
                        this.setTexture(t, null);
                        if (this.onChange != null) this.onChange.run();
                    }
                }
            });
        });
        this.color = new UIColor((c) ->
        {
            String selectedCategory = this.categories != null ? this.categories.getCurrentFirst() : null;
            if (selectedCategory != null && !selectedCategory.isEmpty())
            {
                this.applyCategory((p) -> this.setColor(p, c));
            }
            else if (this.applyLiveMirror((p) -> this.setColor(p, c)))
            {}
            else if (this.transform.getTransform() instanceof PoseTransform poseTransform)
            {
                this.setColor(poseTransform, c);
            }

            if (this.onChange != null) this.onChange.run();
        }).withAlpha();
        this.color.tooltip(UIKeys.FILM_REPLAY_TRACK_COLOR);
        this.color.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.applyChildren((p) -> this.setColor(p, this.color.picker.color.getARGBColor()));
                if (this.onChange != null) this.onChange.run();
            });
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CATEGORIES_CONTEXT_APPLY_CATEGORY, () ->
            {
                this.applyCategory((p) -> this.setColor(p, this.color.picker.color.getARGBColor()));
                if (this.onChange != null) this.onChange.run();
            });
        });
        this.colorTransform = new UIEffectTransformCollapse((apply) -> this.editPoseColor((color) ->
        {
            if (color.transform == null)
            {
                color.transform = new EffectTransform();
            }

            apply.accept(color.transform);
        }));
        this.paintColor = new UIColor((c) ->
        {
            String selectedCategory = this.categories != null ? this.categories.getCurrentFirst() : null;
            if (selectedCategory != null && !selectedCategory.isEmpty())
            {
                this.applyCategory((p) -> this.setPaintColor(p, c));
            }
            else if (this.applyLiveMirror((p) -> this.setPaintColor(p, c)))
            {}
            else if (this.transform.getTransform() instanceof PoseTransform poseTransform)
            {
                this.setPaintColor(poseTransform, c);
            }

            if (this.onChange != null) this.onChange.run();
        });
        this.paintColor.tooltip(UIKeys.FORMS_EDITORS_PAINT_COLOR);
        this.paintColor.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.applyChildren((p) -> this.setPaintColor(p, this.paintColor.picker.color.getRGBColor()));
                if (this.onChange != null) this.onChange.run();
            });
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CATEGORIES_CONTEXT_APPLY_CATEGORY, () ->
            {
                this.applyCategory((p) -> this.setPaintColor(p, this.paintColor.picker.color.getRGBColor()));
                if (this.onChange != null) this.onChange.run();
            });
        });
        this.paintIntensity = new UITrackpad((value) ->
        {
            String selectedCategory = this.categories != null ? this.categories.getCurrentFirst() : null;
            if (selectedCategory != null && !selectedCategory.isEmpty())
            {
                this.applyCategory((p) -> this.setPaintIntensity(p, value.floatValue()));
            }
            else if (this.applyLiveMirror((p) -> this.setPaintIntensity(p, value.floatValue())))
            {}
            else if (this.transform.getTransform() instanceof PoseTransform poseTransform)
            {
                this.setPaintIntensity(poseTransform, value.floatValue());
            }

            if (this.onChange != null) this.onChange.run();
        });
        this.paintIntensity.increment(0.05D).values(0.1D, 0.05D, 0.2D).limit(PaintSettings.MIN_INTENSITY, PaintSettings.MAX_INTENSITY);
        this.paintIntensity.tooltip(UIKeys.FORMS_EDITORS_PAINT_INTENSITY);
        this.paintIntensity.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.applyChildren((p) -> this.setPaintIntensity(p, (float) this.paintIntensity.getValue()));
                if (this.onChange != null) this.onChange.run();
            });
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CATEGORIES_CONTEXT_APPLY_CATEGORY, () ->
            {
                this.applyCategory((p) -> this.setPaintIntensity(p, (float) this.paintIntensity.getValue()));
                if (this.onChange != null) this.onChange.run();
            });
        });
        this.paintTransform = new UIEffectTransformCollapse((apply) -> this.editPosePaintColor((color) ->
        {
            if (color.transform == null)
            {
                color.transform = new EffectTransform();
            }

            apply.accept(color.transform);
        }));
        this.colorAdjustments = new UIFormColorAdjustments(
            () -> this.getEditingPoseColor(),
            (color) -> this.editPoseColor((target) ->
            {
                target.brightness = color.brightness;
                target.contrast = color.contrast;
                target.hue = color.hue;
                target.saturation = color.saturation;
                target.brightnessTransform = color.brightnessTransform == null ? new EffectTransform() : color.brightnessTransform.copy();
                target.contrastTransform = color.contrastTransform == null ? new EffectTransform() : color.contrastTransform.copy();
                target.hueTransform = color.hueTransform == null ? new EffectTransform() : color.hueTransform.copy();
                target.saturationTransform = color.saturationTransform == null ? new EffectTransform() : color.saturationTransform.copy();
            })
        );
        this.glowingColor = new UIColor((c) ->
        {
            String selectedCategory = this.categories != null ? this.categories.getCurrentFirst() : null;
            if (selectedCategory != null && !selectedCategory.isEmpty())
            {
                this.applyCategory((p) -> this.setGlowingColor(p, c));
            }
            else if (this.applyLiveMirror((p) -> this.setGlowingColor(p, c)))
            {}
            else if (this.transform.getTransform() instanceof PoseTransform poseTransform)
            {
                this.setGlowingColor(poseTransform, c);
            }

            if (this.onChange != null) this.onChange.run();
        });
        this.glowingColor.tooltip(UIKeys.FORMS_EDITORS_GLOWING_COLOR);
        this.glowingColor.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.applyChildren((p) -> this.setGlowingColor(p, this.glowingColor.picker.color.getRGBColor()));
                if (this.onChange != null) this.onChange.run();
            });
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CATEGORIES_CONTEXT_APPLY_CATEGORY, () ->
            {
                this.applyCategory((p) -> this.setGlowingColor(p, this.glowingColor.picker.color.getRGBColor()));
                if (this.onChange != null) this.onChange.run();
            });
        });
        this.glowIntensity = new UITrackpad((value) ->
        {
            String selectedCategory = this.categories != null ? this.categories.getCurrentFirst() : null;
            if (selectedCategory != null && !selectedCategory.isEmpty())
            {
                this.applyCategory((p) -> this.setGlowIntensity(p, value.floatValue()));
            }
            else if (this.applyLiveMirror((p) -> this.setGlowIntensity(p, value.floatValue())))
            {}
            else if (this.transform.getTransform() instanceof PoseTransform poseTransform)
            {
                this.setGlowIntensity(poseTransform, value.floatValue());
            }

            if (this.onChange != null) this.onChange.run();
        });
        this.glowIntensity.increment(0.05D).values(0.1D, 0.05D, 0.2D);
        this.glowIntensity.tooltip(UIKeys.FORMS_EDITORS_GLOW_INTENSITY);
        this.glowIntensity.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.applyChildren((p) -> this.setGlowIntensity(p, (float) this.glowIntensity.getValue()));
                if (this.onChange != null) this.onChange.run();
            });
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CATEGORIES_CONTEXT_APPLY_CATEGORY, () ->
            {
                this.applyCategory((p) -> this.setGlowIntensity(p, (float) this.glowIntensity.getValue()));
                if (this.onChange != null) this.onChange.run();
            });
        });
        this.glowTransform = new UIEffectTransformCollapse((apply) -> this.editPoseGlowColor((color) ->
        {
            if (color.transform == null)
            {
                color.transform = new EffectTransform();
            }

            apply.accept(color.transform);
        }));
        this.glowSection = UIFormColorLayout.createGlowSection(this.glowingColor, this.glowIntensity, this.glowTransform);
        this.paintSection = UIFormColorLayout.paintColorRowWithTransform(this.paintColor, this.paintIntensity, this.paintTransform);
        this.lighting = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_LIGHTING, (b) ->
        {
            String selectedCategory = this.categories != null ? this.categories.getCurrentFirst() : null;
            if (selectedCategory != null && !selectedCategory.isEmpty())
            {
                this.applyCategory((p) -> this.setLighting(p, b.getValue()));
            }
            else if (this.applyLiveMirror((p) -> this.setLighting(p, b.getValue())))
            {}
            else if (this.transform.getTransform() instanceof PoseTransform poseTransform)
            {
                this.setLighting(poseTransform, b.getValue());
            }

            if (this.onChange != null) this.onChange.run();
        });
        this.lighting.h(20);
        this.lighting.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.applyChildren((p) -> this.setLighting(p, this.lighting.getValue()));
                if (this.onChange != null) this.onChange.run();
            });
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CATEGORIES_CONTEXT_APPLY_CATEGORY, () ->
            {
                this.applyCategory((p) -> this.setLighting(p, this.lighting.getValue()));
                if (this.onChange != null) this.onChange.run();
            });
        });
        this.noShading = new UIToggle(UIKeys.FORMS_EDITORS_NOSHADING_SHADERS, (b) ->
        {
            String selectedCategory = this.categories != null ? this.categories.getCurrentFirst() : null;

            if (selectedCategory != null && !selectedCategory.isEmpty())
            {
                this.applyCategory((p) -> this.setNoshadingOpacity(p, b.getValue()));
            }
            else if (this.applyLiveMirror((p) -> this.setNoshadingOpacity(p, b.getValue())))
            {}
            else if (this.transform.getTransform() instanceof PoseTransform poseTransform)
            {
                this.setNoshadingOpacity(poseTransform, b.getValue());
            }

            if (this.onChange != null) this.onChange.run();
        });
        this.noShading.tooltip(UIKeys.FORMS_EDITORS_COLOR_NOSHADING_OPACITY_TOOLTIP);
        this.noShading.h(20);
        this.noShading.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.applyChildren((p) -> this.setNoshadingOpacity(p, this.noShading.getValue()));
                if (this.onChange != null) this.onChange.run();
            });
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CATEGORIES_CONTEXT_APPLY_CATEGORY, () ->
            {
                this.applyCategory((p) -> this.setNoshadingOpacity(p, this.noShading.getValue()));
                if (this.onChange != null) this.onChange.run();
            });
        });
        this.transform = this.createTransformEditor();

        if (this.useModelGizmoDrag())
        {
            this.transform.setModel();
            this.transform.invertModelPoseTrackballXYZ();
        }

        this.transform.callbacks(null, () ->
        {
            if (this.onChange != null)
            {
                this.onChange.run();
            }
        });

        this.column().vertical().stretch();
        boolean categoriesEnabled = BBSSettings.modelBlockCategoriesPanelEnabled != null && BBSSettings.modelBlockCategoriesPanelEnabled.get();

        if (categoriesEnabled)
        {
            this.add(UI.row(this.groups, this.categories));
        }
        else
        {
            this.add(this.groups);
        }

        this.add(this.extra);
        /* Classic order: bone texture / color / lighting above the transform grid. */
        this.add(this.createPoseFooter());
        this.add(UI.label(UIKeys.POSE_CONTEXT_FIX), this.fix, this.transform);
    }

    /**
     * Establece un proveedor de textura por defecto para usar cuando no exista
     * una textura específica del hueso. Devuelve this para permitir chaining.
     */
    public UIPoseEditor setDefaultTextureSupplier(Supplier<Link> supplier)
    {
        this.defaultTextureSupplier = supplier;

        return this;
    }

    public UIPoseEditor setTexturePreviewFormSupplier(Supplier<Form> supplier)
    {
        this.texturePreviewFormSupplier = supplier;

        return this;
    }

    private void applyChildren(Consumer<PoseTransform> consumer)
    {
        if (this.model == null || this.pose == null || !(this.transform.getTransform() instanceof PoseTransform))
        {
            return;
        }

        PoseTransform t = (PoseTransform) this.transform.getTransform();
        Collection<String> keys = this.model.getAllChildrenKeys(CollectionUtils.getKey(this.pose.transforms, t));

        for (String key : keys)
        {
            consumer.accept(this.pose.get(key));
        }
    }

    public Pose getPose()
    {
        return this.pose;
    }

    public String getGroup()
    {
        return this.groups.list.getCurrentFirst();
    }

    protected void pastePose(MapType data)
    {
        if (this.pose == null)
        {
            return;
        }

        String current = this.groups.list.getCurrentFirst();

        this.pose.fromData(data);
        this.pickBone(current);
        
        if (this.onChange != null)
        {
            this.onChange.run();
        }
    }

    protected void flipPose()
    {
        String current = this.groups.list.getCurrentFirst();

        this.pose.flip(this.flippedParts);
        this.pickBone(current);
        
        if (this.onChange != null)
        {
            this.onChange.run();
        }
    }

    public void setPose(Pose pose, String group)
    {
        this.pose = pose;
        this.group = group;
        this.loadMarkedBonesCache();
        this.refreshCategories();
    }

    /* Accesor público del grupo de pose (para fábricas y pistas) */
    public String getPoseGroupKey()
    {
        return this.group;
    }

    public void fillGroups(Collection<String> groups, boolean reset)
    {
        this.model = null;
        this.flippedParts = null;

        this.fillInGroups(groups, reset);
    }

    public void fillGroups(IModel model, Map<String, String> flippedParts, boolean reset)
    {
        this.model = model;
        this.flippedParts = flippedParts;

        this.fillInGroups(model == null ? Collections.emptyList() : model.getAllGroupKeys(), reset);
    }

    private void fillInGroups(Collection<String> groups, boolean reset)
    {
        double scroll = this.groups.list.scroll.getScroll();

        this.groups.list.clear();
        this.groups.list.add(groups);
        this.groups.list.sort();
        this.allBones.clear();
        this.allBones.addAll(this.groups.list.getList());
        if (!this.allBones.isEmpty())
        {
            this.markedBones.retainAll(this.allBones);
            this.saveMarkedBonesCache();
        }
        this.fix.setVisible(!groups.isEmpty());
        this.color.setVisible(!groups.isEmpty());
        this.paintColor.setVisible(!groups.isEmpty());
        this.paintIntensity.setVisible(!groups.isEmpty());
        this.glowingColor.setVisible(!groups.isEmpty());
        this.glowIntensity.setVisible(!groups.isEmpty());
        this.lighting.setVisible(!groups.isEmpty());
        if (this.noShading != null)
        {
            this.noShading.setVisible(!groups.isEmpty());
        }
        this.pickTexture.setVisible(!groups.isEmpty());
        this.paintSection.setVisible(!groups.isEmpty());
        this.colorAdjustments.setVisible(!groups.isEmpty());
        this.glowSection.setVisible(!groups.isEmpty());
        this.transform.setVisible(!groups.isEmpty());

        boolean persistedFilter = BBSSettings.poseBonesFilterMarked != null && BBSSettings.poseBonesFilterMarked.get();
        if (persistedFilter != this.showOnlyMarked)
        {
            this.showOnlyMarked = persistedFilter;
            this.showOnlyMarkedButton.active(this.showOnlyMarked);
        }

        String preferred = this.getLastSelectedBone();
        this.applyMarkedFilter(reset, preferred, scroll);
    }

    public void selectBone(String bone)
    {
        this.cacheLastSelectedBone(bone);

        if (this.showOnlyMarked && bone != null && !bone.isEmpty() && !this.markedBones.contains(bone))
        {
            this.showOnlyMarked = false;
            this.showOnlyMarkedButton.active(false);
            if (BBSSettings.poseBonesFilterMarked != null)
            {
                BBSSettings.poseBonesFilterMarked.set(false);
            }

            double scroll = this.groups.list.scroll.getScroll();
            this.applyMarkedFilter(false, bone, scroll);
        }
        else
        {
            this.groups.list.setCurrentScroll(bone);
            this.pickBone(bone);
        }

        this.selectCategoryForBone(bone);
    }

    public void addBoneToSelection(String bone)
    {
        if (bone == null || bone.isEmpty())
        {
            return;
        }

        int index = this.groups.list.getList().indexOf(bone);

        if (index < 0)
        {
            return;
        }

        this.groups.list.toggleIndex(index);

        List<String> current = this.groups.list.getCurrent();

        if (current.isEmpty())
        {
            this.currentBone = null;

            return;
        }

        this.pickBone(current.get(current.size() - 1));
        this.cacheLastSelectedBone(current.get(current.size() - 1));
        this.selectCategoryForBone(bone);
    }

    private void selectCategoryForBone(String bone)
    {
        if (this.categories != null && this.model != null)
        {
            List<String> cats = this.boneCategories.getCategories(this.group);
            for (String cat : cats)
            {
                List<String> bones = this.boneCategories.getBones(this.group, cat);
                if (bones.contains(bone))
                {
                    this.categories.setCurrentScroll(cat);
                    break;
                }
            }
        }
    }

    /* Subclass overridable methods */

  /** Film/replay pose editors keep the legacy model-space drag inversions; form model pose matches model block. */
    protected boolean useModelGizmoDrag()
    {
        return true;
    }

    protected float getGizmoTranslationScale()
    {
        return 16F;
    }

    protected UIPropTransform createTransformEditor()
    {
        return new CategoryPropTransform(this).enableHotkeys().translationScale(this.getGizmoTranslationScale());
    }

    /* Transformaciones aplicables por categoría */
    private static class CategoryPropTransform extends UIPropTransform
    {
        private final UIPoseEditor editor;

        private CategoryPropTransform(UIPoseEditor editor)
        {
            this.editor = editor;
        }

        private List<String> targets()
        {
            boolean categoriesEnabled = BBSSettings.modelBlockCategoriesPanelEnabled != null && BBSSettings.modelBlockCategoriesPanelEnabled.get();
            String selectedCategory = (categoriesEnabled && this.editor.categories != null) ? this.editor.categories.getCurrentFirst() : null;
            if (selectedCategory == null || selectedCategory.isEmpty())
            {
                List<String> liveMirror = this.editor.getLiveMirrorBones();
                if (!liveMirror.isEmpty())
                {
                    return liveMirror;
                }

                String current = this.editor.groups.list.getCurrentFirst();
                return current == null ? Collections.emptyList() : Collections.singletonList(current);
            }

            return this.editor.boneCategories.getBones(this.editor.group, selectedCategory);
        }

        @Override
        public void setT(Axis axis, double x, double y, double z)
        {
            if (!(this.getTransform() instanceof PoseTransform) || this.editor.pose == null || CollectionUtils.getKey(this.editor.pose.transforms, (PoseTransform) this.getTransform()) == null)
            {
                super.setT(axis, x, y, z);
                return;
            }

            this.preCallback();
            Transform transform = this.getTransform();
            float dx = (float) (x - transform.translate.x);
            float dy = (float) (y - transform.translate.y);
            float dz = (float) (z - transform.translate.z);

            for (String key : this.targets())
            {
                PoseTransform t = this.editor.pose.get(key);
                if (t != null)
                {
                    t.translate.x += dx;
                    t.translate.y += dy;
                    t.translate.z += dz;
                }
            }
            this.postCallback();
        }

        @Override
        public void setS(Axis axis, double x, double y, double z)
        {
            if (!(this.getTransform() instanceof PoseTransform) || this.editor.pose == null || CollectionUtils.getKey(this.editor.pose.transforms, (PoseTransform) this.getTransform()) == null)
            {
                super.setS(axis, x, y, z);
                return;
            }

            this.preCallback();
            Transform transform = this.getTransform();
            float dx = (float) (x - transform.scale.x);
            float dy = (float) (y - transform.scale.y);
            float dz = (float) (z - transform.scale.z);

            for (String key : this.targets())
            {
                PoseTransform t = this.editor.pose.get(key);
                if (t != null)
                {
                    t.scale.x += dx;
                    t.scale.y += dy;
                    t.scale.z += dz;
                }
            }
            this.postCallback();
        }

        @Override
        public void setR(Axis axis, double x, double y, double z)
        {
            if (!(this.getTransform() instanceof PoseTransform) || this.editor.pose == null || CollectionUtils.getKey(this.editor.pose.transforms, (PoseTransform) this.getTransform()) == null)
            {
                super.setR(axis, x, y, z);
                return;
            }

            this.preCallback();
            Transform transform = this.getTransform();
            float dx = MathUtils.toRad((float) x) - transform.rotate.x;
            float dy = MathUtils.toRad((float) y) - transform.rotate.y;
            float dz = MathUtils.toRad((float) z) - transform.rotate.z;
            List<String> targets = this.targets();
            boolean invertAxes = this.editor.shouldInvertLiveMirrorRotationZ(targets);
            String sourceBone = this.editor.getCurrentBone();

            for (String key : targets)
            {
                PoseTransform t = this.editor.pose.get(key);
                if (t != null)
                {
                    boolean mirroredBone = invertAxes && !key.equals(sourceBone);
                    t.rotate.x += mirroredBone ? -dx : dx;
                    t.rotate.y += mirroredBone ? -dy : dy;
                    t.rotate.z += mirroredBone ? -dz : dz;
                }
            }
            this.postCallback();
        }

        @Override
        public void setR2(Axis axis, double x, double y, double z)
        {
            if (!(this.getTransform() instanceof PoseTransform) || this.editor.pose == null || CollectionUtils.getKey(this.editor.pose.transforms, (PoseTransform) this.getTransform()) == null)
            {
                super.setR2(axis, x, y, z);
                return;
            }

            this.preCallback();
            Transform transform = this.getTransform();
            float dx = MathUtils.toRad((float) x) - transform.rotate2.x;
            float dy = MathUtils.toRad((float) y) - transform.rotate2.y;
            float dz = MathUtils.toRad((float) z) - transform.rotate2.z;
            List<String> targets = this.targets();
            boolean invertAxes = this.editor.shouldInvertLiveMirrorRotationZ(targets);
            String sourceBone = this.editor.getCurrentBone();

            for (String key : targets)
            {
                PoseTransform t = this.editor.pose.get(key);
                if (t != null)
                {
                    boolean mirroredBone = invertAxes && !key.equals(sourceBone);
                    t.rotate2.x += mirroredBone ? -dx : dx;
                    t.rotate2.y += mirroredBone ? -dy : dy;
                    t.rotate2.z += mirroredBone ? -dz : dz;
                }
            }
            this.postCallback();
        }

        @Override
        public void setP(Axis axis, double x, double y, double z)
        {
            if (!(this.getTransform() instanceof PoseTransform) || this.editor.pose == null || CollectionUtils.getKey(this.editor.pose.transforms, (PoseTransform) this.getTransform()) == null)
            {
                super.setP(axis, x, y, z);
                return;
            }

            this.preCallback();
            Transform transform = this.getTransform();
            float dx = (float) x - transform.pivot.x;
            float dy = (float) y - transform.pivot.y;
            float dz = (float) z - transform.pivot.z;

            for (String key : this.targets())
            {
                PoseTransform t = this.editor.pose.get(key);
                if (t != null)
                {
                    t.pivot.x += dx;
                    t.pivot.y += dy;
                    t.pivot.z += dz;
                }
            }
            this.postCallback();
        }
    }

    public void setGlobalTexture(UIElement element)
    {
        this.prepend(element);
        this.resize();
    }

    public void setTransform(Transform transform)
    {
        this.transform.setTransform(transform);

        boolean isPoseTransform = transform instanceof PoseTransform;

        this.fix.setVisible(true);
        this.color.setVisible(true);
        this.paintColor.setVisible(true);
        this.paintIntensity.setVisible(true);
        this.glowingColor.setVisible(true);
        this.glowIntensity.setVisible(true);
        this.paintSection.setVisible(true);
        this.colorAdjustments.setVisible(true);
        this.glowSection.setVisible(true);
        this.lighting.setVisible(true);
        if (this.noShading != null)
        {
            this.noShading.setVisible(true);
        }
        this.pickTexture.setVisible(BBSSettings.pickLimbTexture != null && BBSSettings.pickLimbTexture.get());

        this.fix.setEnabled(isPoseTransform);
        this.color.setEnabled(isPoseTransform);
        this.paintColor.setEnabled(isPoseTransform);
        this.paintIntensity.setEnabled(isPoseTransform);
        this.glowingColor.setEnabled(isPoseTransform);
        this.glowIntensity.setEnabled(isPoseTransform);
        this.lighting.setEnabled(isPoseTransform);
        if (this.noShading != null)
        {
            this.noShading.setEnabled(isPoseTransform);
        }
        this.pickTexture.setEnabled(isPoseTransform);

        if (!isPoseTransform || this.pose == null || CollectionUtils.getKey(this.pose.transforms, (PoseTransform) transform) == null)
        {
             this.groups.list.setIndex(-1);
        }
    }

    public Consumer<String> pickCallback;

    /**
     * Clear focus on numeric/color fields under this editor when the selected
     * bone changes. Focused {@link UITrackpad}s skip textbox refresh in
     * {@link UITrackpad#setValue(double)}, which left stale transform values
     * after switching limbs.
     */
    private void unfocusPoseInputs()
    {
        UIContext context = this.getContext();

        if (context == null || !(context.activeElement instanceof UIElement focused))
        {
            return;
        }

        UIElement element = focused;

        while (element != null)
        {
            if (element == this)
            {
                context.unfocus();

                return;
            }

            element = element.getParent();
        }
    }

    protected void pickBone(String bone)
    {
        boolean boneChanged = this.currentBone == null ? bone != null : !this.currentBone.equals(bone);

        if (boneChanged)
        {
            this.unfocusPoseInputs();
        }

        this.currentBone = bone;
        if (this.pickCallback != null)
        {
            this.pickCallback.accept(bone);
        }

        this.cacheLastSelectedBone(bone);

        this.fix.setVisible(true);
        this.color.setVisible(true);
        this.paintColor.setVisible(true);
        this.paintIntensity.setVisible(true);
        this.glowingColor.setVisible(true);
        this.glowIntensity.setVisible(true);
        this.paintSection.setVisible(true);
        this.colorAdjustments.setVisible(true);
        this.glowSection.setVisible(true);
        this.lighting.setVisible(true);
        if (this.noShading != null)
        {
            this.noShading.setVisible(true);
        }
        this.pickTexture.setVisible(BBSSettings.pickLimbTexture != null && BBSSettings.pickLimbTexture.get());

        this.fix.setEnabled(true);
        this.color.setEnabled(true);
        this.paintColor.setEnabled(true);
        this.paintIntensity.setEnabled(true);
        this.glowingColor.setEnabled(true);
        this.glowIntensity.setEnabled(true);
        this.lighting.setEnabled(true);
        if (this.noShading != null)
        {
            this.noShading.setEnabled(true);
        }
        this.pickTexture.setEnabled(true);

        PoseTransform poseTransform = this.pose != null ? this.pose.get(bone) : null;

        if (poseTransform != null)
        {
            this.fix.setValue(poseTransform.fix);
            this.color.setColor(poseTransform.color.getARGBColor());
            this.colorTransform.setEffectTransform(poseTransform.color.transform);
            this.paintColor.setColor(poseTransform.paintColor.getRGBColor());
            this.paintIntensity.setValue(poseTransform.paintColor.a);
            this.paintTransform.setEffectTransform(poseTransform.paintColor.transform);
            this.colorAdjustments.syncFromForm();
            this.glowingColor.setColor(poseTransform.glowingColor.getRGBColor());
            this.glowIntensity.setValue(poseTransform.glowIntensity);
            this.glowTransform.setEffectTransform(poseTransform.glowingColor.transform);
            this.lighting.setValue(poseTransform.lighting == 0F);
            if (this.noShading != null)
            {
                this.noShading.setValue(poseTransform.noshadingOpacity);
            }
            this.transform.setTransform(poseTransform);
        }
        else
        {
            this.fix.setValue(0F);
            this.color.setColor(Colors.WHITE);
            this.colorTransform.setEffectTransform(new EffectTransform());
            this.paintColor.setColor(0xFFFFFF);
            this.paintIntensity.setValue(0F);
            this.paintTransform.setEffectTransform(new EffectTransform());
            this.colorAdjustments.syncFromForm();
            this.glowingColor.setColor(0xFFFFFF);
            this.glowIntensity.setValue(0F);
            this.glowTransform.setEffectTransform(new EffectTransform());
            this.lighting.setValue(false);
            if (this.noShading != null)
            {
                this.noShading.setValue(false);
            }
            this.transform.setTransform(null);
        }
    }

    protected void setFix(PoseTransform transform, float value)
    {
        transform.fix = value;
    }

    protected void setColor(PoseTransform transform, int value)
    {
        Color rgba = Color.rgba(value);

        transform.color.set(rgba.r, rgba.g, rgba.b, rgba.a);
    }

    protected void setPaintColor(PoseTransform transform, int value)
    {
        float intensity = transform.paintColor.a;

        transform.paintColor.set(value);
        transform.paintColor.a = intensity;
        transform.shaderShadow = PaintSettings.resolveAutoShaderShadowForPoseAlpha(transform.paintColor.a);
    }

    protected void setPaintIntensity(PoseTransform transform, float value)
    {
        transform.paintColor.a = PaintSettings.clampIntensity(value);
        transform.shaderShadow = PaintSettings.resolveAutoShaderShadowForPoseAlpha(transform.paintColor.a);
    }

    protected void setGlowingColor(PoseTransform transform, int value)
    {
        Color rgb = new Color().set(value);

        transform.glowingColor.r = rgb.r;
        transform.glowingColor.g = rgb.g;
        transform.glowingColor.b = rgb.b;
        transform.glowingColor.a = 1F;
    }

    protected void setGlowIntensity(PoseTransform transform, float value)
    {
        transform.glowIntensity = value;
    }

    protected void setGlowRadius(PoseTransform transform, float value)
    {
        transform.glowRadius = value;
    }

    protected void setLighting(PoseTransform poseTransform, boolean value)
    {
        poseTransform.lighting = value ? 0F : 1F;
    }

    protected void setNoshadingOpacity(PoseTransform poseTransform, boolean value)
    {
        poseTransform.noshadingOpacity = value;
    }

    protected void setTexture(PoseTransform transform, Link value)
    {
        transform.texture = LinkUtils.copy(value);
        transform.textureBlend = 1F;
    }

    protected void setTextureBlend(PoseTransform transform, float value)
    {
        transform.textureBlend = 1F;
    }

    /**
     * Bone appearance controls above the transform grid:
     * section label, bone texture, color + lighting; Color extras (glow/paint/grade) when enabled.
     */
    public UIElement createPoseFooter()
    {
        boolean pickLimbTexture = BBSSettings.pickLimbTexture != null && BBSSettings.pickLimbTexture.get();
        UIElement footer = UI.column();

        footer.add(UIFormColorLayout.sectionLabel(UIKeys.FORMS_EDITOR_BONE));

        if (pickLimbTexture)
        {
            footer.add(this.pickTexture);
        }

        /* Color+icon cluster shares the row with Lighting; grid opens full-width below. */
        footer.add(UIFormColorLayout.colorWithTransformAndExtras(this.color, this.colorTransform, this.lighting));

        if (this.formAppearanceExtras)
        {
            footer.add(this.noShading);
            footer.add(UIFormColorLayout.createExtraSection(
                this.glowSection,
                this.paintSection,
                this.colorAdjustments.marginTop(4)
            ).marginTop(4));
        }

        return footer;
    }

    private Color getEditingPoseColor()
    {
        if (this.transform.getTransform() instanceof PoseTransform poseTransform)
        {
            return poseTransform.color;
        }

        return Color.white();
    }

    protected void editPoseColor(Consumer<Color> editor)
    {
        String selectedCategory = this.categories != null ? this.categories.getCurrentFirst() : null;

        if (selectedCategory != null && !selectedCategory.isEmpty())
        {
            this.applyCategory((p) -> editor.accept(p.color));
        }
        else if (this.applyLiveMirror((p) -> editor.accept(p.color)))
        {}
        else if (this.transform.getTransform() instanceof PoseTransform poseTransform)
        {
            editor.accept(poseTransform.color);
        }

        if (this.onChange != null)
        {
            this.onChange.run();
        }
    }

    protected void editPosePaintColor(Consumer<Color> editor)
    {
        String selectedCategory = this.categories != null ? this.categories.getCurrentFirst() : null;

        if (selectedCategory != null && !selectedCategory.isEmpty())
        {
            this.applyCategory((p) -> editor.accept(p.paintColor));
        }
        else if (this.applyLiveMirror((p) -> editor.accept(p.paintColor)))
        {}
        else if (this.transform.getTransform() instanceof PoseTransform poseTransform)
        {
            editor.accept(poseTransform.paintColor);
        }

        if (this.onChange != null)
        {
            this.onChange.run();
        }
    }

    protected void editPoseGlowColor(Consumer<Color> editor)
    {
        String selectedCategory = this.categories != null ? this.categories.getCurrentFirst() : null;

        if (selectedCategory != null && !selectedCategory.isEmpty())
        {
            this.applyCategory((p) -> editor.accept(p.glowingColor));
        }
        else if (this.applyLiveMirror((p) -> editor.accept(p.glowingColor)))
        {}
        else if (this.transform.getTransform() instanceof PoseTransform poseTransform)
        {
            editor.accept(poseTransform.glowingColor);
        }

        if (this.onChange != null)
        {
            this.onChange.run();
        }
    }

    /* Categorías */

    protected void refreshCategories()
    {
        if (this.categories == null)
        {
            return;
        }

        this.categories.clear();
        if (this.group != null)
        {
            this.categories.add(this.boneCategories.getCategories(this.group));
            this.categories.sort();
        }
    }

    protected void applyCategory(Consumer<PoseTransform> consumer)
    {
        boolean categoriesEnabled = BBSSettings.modelBlockCategoriesPanelEnabled != null && BBSSettings.modelBlockCategoriesPanelEnabled.get();
        String selectedCategory = categoriesEnabled ? this.categories.getCurrentFirst() : null;
        if (this.model == null || this.pose == null || selectedCategory == null || selectedCategory.isEmpty())
        {
            return;
        }

        List<String> bones = this.boneCategories.getBones(this.group, selectedCategory);
        for (String key : bones)
        {
            PoseTransform t = this.pose.get(key);
            if (t != null)
            {
                consumer.accept(t);
            }
        }
    }

    private void toggleShowOnlyMarked()
    {
        this.showOnlyMarked = !this.showOnlyMarked;
        this.showOnlyMarkedButton.active(this.showOnlyMarked);
        if (BBSSettings.poseBonesFilterMarked != null)
        {
            BBSSettings.poseBonesFilterMarked.set(this.showOnlyMarked);
        }

        String current = this.groups.list.getCurrentFirst();
        double scroll = this.groups.list.scroll.getScroll();
        this.applyMarkedFilter(false, current, scroll);
    }

    private void toggleInvertLiveMirrorZ()
    {
        this.invertLiveMirrorZ = !this.invertLiveMirrorZ;
        this.invertLiveMirrorZButton.active(this.invertLiveMirrorZ);
    }

    private void toggleBoneMarked(String bone)
    {
        if (bone == null || bone.isEmpty())
        {
            return;
        }

        if (this.markedBones.contains(bone))
        {
            this.markedBones.remove(bone);
        }
        else
        {
            this.markedBones.add(bone);
        }
        this.saveMarkedBonesCache();

        if (this.showOnlyMarked)
        {
            String current = this.groups.list.getCurrentFirst();
            double scroll = this.groups.list.scroll.getScroll();
            this.applyMarkedFilter(false, current, scroll);
        }
    }

    private void applyMarkedFilter(boolean reset, String preferredBone, double scroll)
    {
        List<String> source = this.showOnlyMarked ? this.getMarkedBonesInOrder() : this.allBones;
        this.groups.list.clear();
        this.groups.list.add(source);
        this.groups.list.sort();

        this.applySearchFilter();

        List<String> list = this.groups.list.getList();
        String element = preferredBone != null && list.contains(preferredBone) ? preferredBone : CollectionUtils.getSafe(list, 0);

        if (element != null)
        {
            if (reset)
            {
                this.groups.list.setCurrentScroll(element);
            }
            else
            {
                this.groups.list.setCurrent(element);
                this.groups.list.scroll.setScroll(scroll);
            }

            this.pickBone(element);
        }
        else
        {
            this.groups.list.setIndex(-1);
        }

        this.refreshCategories();
    }

    private void applySearchFilter()
    {
        String filter = this.groups.search.getText();

        this.groups.list.filter("");
        if (!filter.isEmpty())
        {
            this.groups.list.filter(filter);
        }
    }

    private List<String> getMarkedBonesInOrder()
    {
        List<String> marked = new ArrayList<>();

        for (String bone : this.allBones)
        {
            if (this.markedBones.contains(bone))
            {
                marked.add(bone);
            }
        }

        return marked;
    }

    protected List<String> getLiveMirrorBones()
    {
        if (this.groups == null)
        {
            return Collections.emptyList();
        }

        List<String> bones = this.groups.list.getCurrent();
        return bones.size() < 2 ? Collections.emptyList() : new ArrayList<>(bones);
    }

    protected boolean shouldInvertLiveMirrorRotationZ(List<String> targets)
    {
        return this.invertLiveMirrorZ && targets != null && targets.size() >= 2;
    }

    private boolean applyLiveMirror(Consumer<PoseTransform> consumer)
    {
        if (this.pose == null || consumer == null)
        {
            return false;
        }

        List<String> bones = this.getLiveMirrorBones();
        if (bones.isEmpty())
        {
            return false;
        }

        for (String bone : bones)
        {
            PoseTransform transform = this.pose.get(bone);
            if (transform != null)
            {
                consumer.accept(transform);
            }
        }

        return true;
    }

    private void loadMarkedBonesCache()
    {
        this.ensureMarkedBonesLoaded();
        this.markedBones.clear();

        Set<String> cached = MARKED_BONES_CACHE.get(this.getMarkedBonesCacheKey());
        if (cached != null)
        {
            this.markedBones.addAll(cached);
        }
    }

    private void saveMarkedBonesCache()
    {
        this.ensureMarkedBonesLoaded();

        String key = this.getMarkedBonesCacheKey();
        if (key.isEmpty())
        {
            return;
        }

        if (this.markedBones.isEmpty())
        {
            MARKED_BONES_CACHE.remove(key);
        }
        else
        {
            MARKED_BONES_CACHE.put(key, new HashSet<>(this.markedBones));
        }

        this.saveMarkedBonesToFile();
    }

    public static boolean hasMarkedBones(String groupKey)
    {
        if (groupKey == null || groupKey.isEmpty())
        {
            return false;
        }

        ensureMarkedBonesLoadedStatic();

        Set<String> cached = MARKED_BONES_CACHE.get(groupKey);
        return cached != null && !cached.isEmpty();
    }

    public static Set<String> getMarkedBones(String groupKey)
    {
        if (groupKey == null || groupKey.isEmpty())
        {
            return Collections.emptySet();
        }

        ensureMarkedBonesLoadedStatic();

        Set<String> cached = MARKED_BONES_CACHE.get(groupKey);
        return cached == null ? Collections.emptySet() : new HashSet<>(cached);
    }

    public String getCurrentBone()
    {
        return this.currentBone;
    }

    public static boolean isMarkedBone(String groupKey, String bone)
    {
        if (bone == null || bone.isEmpty())
        {
            return false;
        }

        ensureMarkedBonesLoadedStatic();

        Set<String> cached = groupKey == null ? null : MARKED_BONES_CACHE.get(groupKey);
        return cached != null && cached.contains(bone);
    }

    private String getMarkedBonesCacheKey()
    {
        return this.group == null ? "" : this.group;
    }

    private String getLastSelectedBone()
    {
        String key = this.getMarkedBonesCacheKey();
        String preferred = LAST_LIMB_CACHE.get(key);
        if (preferred == null || preferred.isEmpty())
        {
            preferred = LAST_LIMB_CACHE.get("");
        }

        if (preferred != null && !preferred.isEmpty())
        {
            return preferred;
        }

        return null;
    }

    private void cacheLastSelectedBone(String bone)
    {
        if (bone == null || bone.isEmpty())
        {
            return;
        }

        String key = this.getMarkedBonesCacheKey();
        if (!key.isEmpty())
        {
            LAST_LIMB_CACHE.put(key, bone);
        }
        LAST_LIMB_CACHE.put("", bone);
    }

    private void ensureMarkedBonesLoaded()
    {
        ensureMarkedBonesLoadedStatic();
    }

    private static void ensureMarkedBonesLoadedStatic()
    {
        if (MARKED_BONES_LOADED)
        {
            return;
        }

        MARKED_BONES_LOADED = true;

        try
        {
            BaseType type = DataToString.read(getMarkedBonesFileStatic());

            if (type != null && type.isMap())
            {
                MapType map = (MapType) type;

                for (String key : map.keys())
                {
                    ListType list = map.getList(key);
                    if (list == null)
                    {
                        continue;
                    }

                    Set<String> bones = new HashSet<>();
                    for (int i = 0; i < list.size(); i++)
                    {
                        bones.add(list.getString(i));
                    }

                    if (!bones.isEmpty())
                    {
                        MARKED_BONES_CACHE.put(key, bones);
                    }
                }
            }
        }
        catch (IOException e)
        {
        }
    }

    private void saveMarkedBonesToFile()
    {
        MapType root = new MapType();

        for (Map.Entry<String, Set<String>> entry : MARKED_BONES_CACHE.entrySet())
        {
            ListType list = new ListType();
            for (String bone : entry.getValue())
            {
                list.addString(bone);
            }
            root.put(entry.getKey(), list);
        }

        DataToString.writeSilently(this.getMarkedBonesFile(), root, true);
    }

    private File getMarkedBonesFile()
    {
        return getMarkedBonesFileStatic();
    }

    private static File getMarkedBonesFileStatic()
    {
        return BBSMod.getSettingsPath(MARKED_BONES_FILE);
    }

    private class MarkableBoneList extends UIStringList
    {
        public MarkableBoneList(Consumer<List<String>> callback)
        {
            super(callback);
        }

        @Override
        public void render(UIContext context)
        {
            super.render(context);
            context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF3C3C3C);

            if (!UIPoseEditor.this.showOnlyMarked || !UIPoseEditor.this.markedBones.isEmpty())
            {
                return;
            }

            String line1 = UIKeys.POSE_BONES_EMPTY_LINE1.get();
            String line2 = UIKeys.POSE_BONES_EMPTY_LINE2.get();
            int lineHeight = context.batcher.getFont().getHeight() + 4;
            int totalHeight = lineHeight * 2 - 4;
            int y = this.area.my() - totalHeight / 2;
            int color = Colors.setA(Colors.WHITE, 0.6F);

            context.batcher.clip(this.area, context);
            int x1 = this.area.mx() - context.batcher.getFont().getWidth(line1) / 2;
            context.batcher.textShadow(line1, x1, y, color);
            y += lineHeight;

            int iconSize = 16;
            int iconSpacing = 4;
            int line2TextWidth = context.batcher.getFont().getWidth(line2);
            int totalLine2Width = line2TextWidth + iconSpacing + iconSize;
            int x2 = this.area.mx() - totalLine2Width / 2;
            context.batcher.textShadow(line2, x2, y, color);
            int iconX = x2 + line2TextWidth + iconSpacing;
            int iconY = y + (context.batcher.getFont().getHeight() - iconSize) / 2;
            RenderSystem.enableBlend();
            context.batcher.icon(Icons.VISIBLE, color, iconX, iconY);
            context.batcher.unclip(context);
        }

        @Override
        protected void renderElementPart(UIContext context, String element, int i, int x, int y, boolean hover, boolean selected)
        {
            int iconX = x + 2;
            int iconY = y + (this.scroll.scrollItemSize - 16) / 2;
            boolean marked = UIPoseEditor.this.markedBones.contains(element);
            int iconColor = marked ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.35F);

            RenderSystem.enableBlend();
            context.batcher.icon(Icons.CHECKMARK, iconColor, iconX, iconY);

            int textX = x + 22;
            int maxWidth = this.area.w - 24;
            String displayText = element;
            int textWidth = context.batcher.getFont().getWidth(displayText);

            if (textWidth > maxWidth)
            {
                displayText = context.batcher.getFont().limitToWidth(displayText, maxWidth);
            }

            context.batcher.textShadow(displayText, textX, y + (this.scroll.scrollItemSize - context.batcher.getFont().getHeight()) / 2, hover ? Colors.HIGHLIGHT : Colors.WHITE);
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (!this.area.isInside(context) || context.mouseButton != 0)
            {
                return super.subMouseClicked(context);
            }

            int scrollIndex = this.scroll.getIndex(context.mouseX, context.mouseY);
            String element = this.getElementAt(scrollIndex);

            if (element == null)
            {
                return super.subMouseClicked(context);
            }

            int y = this.area.y + scrollIndex * this.scroll.scrollItemSize - (int) this.scroll.getScroll();
            int iconY = y + (this.scroll.scrollItemSize - 16) / 2;
            int iconX = this.area.x + 2;

            if (context.mouseX >= iconX && context.mouseX < iconX + 16 && context.mouseY >= iconY && context.mouseY < iconY + 16)
            {
                if (Window.isShiftPressed())
                {
                    UIPoseEditor.this.toggleBoneMarked(element);
                    return true;
                }
            }

            if (Window.isShiftPressed())
            {
                int index = this.list.indexOf(element);

                if (this.exists(index))
                {
                    if (this.multi && this.isSelected())
                    {
                        int first = this.current.get(0);
                        int increment = first > index ? -1 : 1;

                        for (int i = first + increment; i != index + increment; i += increment)
                        {
                            this.addIndex(i);
                        }
                    }
                    else
                    {
                        this.setIndex(index);
                    }

                    List<String> current = this.getCurrent();

                    if (this.callback != null && !current.isEmpty())
                    {
                        this.callback.accept(current);
                    }

                    return true;
                }
            }

            return super.subMouseClicked(context);
        }
    }
}
