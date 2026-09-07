package mchorse.bbs_mod.ui.triggers;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.triggers.Trigger;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPickerOverlayPanel;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIEditorOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockEntityList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;

public class UITriggerOverlayPanel extends UIEditorOverlayPanel<Trigger>
{
    private ValueList<Trigger> triggers;
    private Runnable onClose;

    public UITriggerOverlayPanel(ValueList<Trigger> triggers, Runnable onClose)
    {
        this(triggers);
        this.onClose = onClose;
    }

    public UITriggerOverlayPanel(ValueList<Trigger> triggers)
    {
        super(TriggerKeys.TITLE);

        this.triggers = triggers;
        this.list.add(triggers.getAllTyped());
        this.pickItem(triggers.getAllTyped().isEmpty() ? null : triggers.getAllTyped().get(0), true);

        this.list.resetContext();
        this.list.context((menu) ->
        {
            menu.action(Icons.ADD, this.getAddLabel(), this::addItem);

            if (!this.list.getList().isEmpty())
            {
                menu.action(Icons.COPY, TriggerKeys.COPY_TRIGGER, () ->
                {
                    try
                    {
                        MinecraftClient.getInstance().keyboard.setClipboard(DataStorageUtils.toNbt(this.item.toData()).toString());
                    }
                    catch (Exception e)
                    {}
                });
            }

            try
            {
                String clipboard = MinecraftClient.getInstance().keyboard.getClipboard();
                NbtElement element = StringNbtReader.parse(clipboard);

                if (element instanceof NbtCompound)
                {
                    menu.action(Icons.PASTE, TriggerKeys.PASTE_TRIGGER, () ->
                    {
                        Trigger newTrigger = new Trigger("");
                        newTrigger.fromData(DataStorageUtils.fromNbt(element));
                        this.list.getList().add(newTrigger);
                        this.list.update();
                        this.pickItem(newTrigger, true);
                    });
                }
            }
            catch (Exception e)
            {}

            if (!this.list.getList().isEmpty())
            {
                menu.action(Icons.REMOVE, this.getRemoveLabel(), Colors.NEGATIVE, this::removeItem);
            }
        });
    }

    @Override
    public void close()
    {
        super.close();

        if (this.onClose != null)
        {
            this.onClose.run();
        }
    }

    @Override
    protected IKey getAddLabel()
    {
        return TriggerKeys.ADD_TRIGGER;
    }

    @Override
    protected IKey getRemoveLabel()
    {
        return TriggerKeys.REMOVE_TRIGGER;
    }

    @Override
    protected UIList<Trigger> createList()
    {
        return new UIList<Trigger>((l) -> this.pickItem(l.get(0), false))
        {
            @Override
            protected String elementToString(UIContext context, int i, Trigger element)
            {
                String type = element.type.get();

                if (type.equals("command"))
                {
                    String command = element.command.get();

                    return command.startsWith("/") ? command : "/" + command;
                }
                else if (type.equals("form"))
                {
                    Form form = element.form.get();

                    if (form == null)
                    {
                        return UIKeys.MORPHING_DEMORPH.get();
                    }

                    return TriggerKeys.MORPH_INTO.format(form.getDisplayName()).get();
                }
                else if (type.equals("block"))
                {
                    Form form = element.blockForm.get();

                    if (form == null)
                    {
                        return TriggerKeys.REMOVE_FORM.get();
                    }

                    return TriggerKeys.ADD_FORM_NAME.format(form.getDisplayName()).get();
                }
                else if (type.equals("film"))
                {
                    String filmName = element.film.get();

                    if (filmName.isEmpty())
                    {
                        return TriggerKeys.PLAY_FILM_EMPTY.get();
                    }

                    return TriggerKeys.PLAY_FILM_NAME.format(filmName).get();
                }

                return type;
            }

            @Override
            protected void renderElementPart(UIContext context, Trigger element, int i, int x, int y, boolean hover, boolean selected)
            {
                int color = Colors.WHITE & Colors.RGB;
                String type = element.type.get();

                if (type.equals("block"))
                {
                    color = Colors.ACTIVE;
                }
                else if (type.equals("command"))
                {
                    color = 0xA020F0;
                }
                else if (type.equals("form"))
                {
                    color = Colors.ORANGE;
                }
                else if (type.equals("film"))
                {
                    color = Colors.BLUE;
                }

                context.batcher.box(x, y, x + 2, y + this.scroll.scrollItemSize, Colors.A100 | color);
                context.batcher.gradientHBox(x + 2, y, x + 24, y + this.scroll.scrollItemSize, Colors.A25 | color, color);

                super.renderElementPart(context, element, i, x + 4, y, hover, selected);
            }
        };
    }

    @Override
    protected void addItem()
    {
        UIContext context = this.getContext();

        context.replaceContextMenu((menu) ->
        {
            menu.action(Icons.CONSOLE, TriggerKeys.ADD_COMMAND, 0xA020F0, () -> this.addTrigger("command"));
            menu.action(Icons.MORPH, TriggerKeys.ADD_FORM, Colors.ORANGE, () -> this.addTrigger("form"));
            menu.action(Icons.BLOCK, TriggerKeys.ADD_BLOCK, Colors.ACTIVE, () -> this.addTrigger("block"));
            menu.action(Icons.FILM, TriggerKeys.ADD_FILM, Colors.BLUE, () -> this.addTrigger("film"));
        });
    }

    @Override
    protected void fillData(Trigger item)
    {
        this.editor.removeAll();

        String type = item.type.get();

        if (type.equals("command"))
        {
            UITextbox command = new UITextbox(1000, (t) -> item.command.set(t));
            command.setText(item.command.get());

            this.editor.add(UI.label(TriggerKeys.ACTION_COMMAND), command);
        }
        else if (type.equals("form"))
        {
            UIButton edit = new UIButton(UIKeys.GENERAL_EDIT, (b) ->
            {
                UIFormPalette.open(this.getParentContainer(), true, item.form.get(), (f) ->
                {
                    item.form.set(f);
                });
            });

            edit.setEnabled(item.form.get() != null);

            UIButton pick = new UIButton(UIKeys.GENERAL_PICK, (b) ->
            {
                UIFormPalette.open(this.getParentContainer(), false, item.form.get(), (f) ->
                {
                    item.form.set(f);
                    edit.setEnabled(f != null);

                    if (f == null)
                    {
                        PlayerEntity player = MinecraftClient.getInstance().player;

                        if (player != null)
                        {
                            Morph morph = Morph.getMorph(player);

                            if (morph != null)
                            {
                                morph.setForm(null);
                            }
                        }
                    }
                });
            });

            this.editor.add(UI.label(TriggerKeys.ACTION_FORM), UI.row(pick, edit));
        }
        else if (type.equals("block"))
        {
            UIButton edit = new UIButton(UIKeys.GENERAL_EDIT, (b) ->
            {
                UIFormPalette.open(this.getParentContainer(), true, item.blockForm.get(), (f) ->
                {
                    item.blockForm.set(f);
                });
            });

            edit.setEnabled(item.blockForm.get() != null);

            UIButton pick = new UIButton(UIKeys.GENERAL_PICK, (b) ->
            {
                UIFormPalette.open(this.getParentContainer(), false, item.blockForm.get(), (f) ->
                {
                    item.blockForm.set(f);
                    edit.setEnabled(f != null);
                });
            });

            UITrackpad x = new UITrackpad((v) -> item.x.set(v.intValue()));
            x.setValue(item.x.get());
            x.integer();

            UITrackpad y = new UITrackpad((v) -> item.y.set(v.intValue()));
            y.setValue(item.y.get());
            y.integer();

            UITrackpad z = new UITrackpad((v) -> item.z.set(v.intValue()));
            z.setValue(item.z.get());
            z.integer();

            UIButton pickBlock = new UIButton(TriggerKeys.PICK_BLOCK, (b) ->
            {
                UIOverlayPanel panel = new UIOverlayPanel(TriggerKeys.PICK_BLOCK);
                UIModelBlockEntityList modelBlocks = new UIModelBlockEntityList((l) ->
                {
                    ModelBlockEntity entity = l.get(0);

                    item.x.set(entity.getPos().getX());
                    item.y.set(entity.getPos().getY());
                    item.z.set(entity.getPos().getZ());

                    x.setValue(entity.getPos().getX());
                    y.setValue(entity.getPos().getY());
                    z.setValue(entity.getPos().getZ());

                    panel.close();
                });

                modelBlocks.background().add(BBSRendering.capturedModelBlocks);
                modelBlocks.relative(panel.content).w(1F).h(1F);
                panel.content.add(modelBlocks);

                UIOverlay.addOverlay(this.getContext(), panel, 200, 250);
            });

            this.editor.add(UI.label(TriggerKeys.ACTION_BLOCK_POS));
            this.editor.add(UI.row(x, y, z));
            this.editor.add(pickBlock);
            this.editor.add(UI.label(TriggerKeys.ACTION_BLOCK_FORM), UI.row(pick, edit));
        }
        else if (type.equals("film"))
        {
            UIButton pick = new UIButton(UIKeys.GENERAL_PICK, (b) ->
            {
                UIFilmPickerOverlayPanel panel = new UIFilmPickerOverlayPanel((f) ->
                {
                    item.film.set(f);
                });

                UIOverlay.addOverlay(this.getContext(), panel);
            });

            UIToggle playCamera = new UIToggle(TriggerKeys.PLAY_CAMERA, item.playCamera.get(), (b) -> item.playCamera.set(b.getValue()));

            this.editor.add(UI.label(TriggerKeys.ACTION_FILM), pick);
            this.editor.add(playCamera);
        }
    }

    private void addTrigger(String type)
    {
        Trigger trigger = new Trigger("");

        trigger.type.set(type);
        this.triggers.add(trigger);
        this.list.add(trigger);
        this.list.update();
        this.pickItem(trigger, true);
    }

    @Override
    protected void removeItem()
    {
        if (this.item != null)
        {
            this.triggers.getAllTyped().remove(this.item);
        }

        super.removeItem();
    }
}
