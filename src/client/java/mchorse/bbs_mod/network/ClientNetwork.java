package mchorse.bbs_mod.network;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.blocks.entities.TriggerBlockEntity;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.entity.GunProjectileEntity;
import mchorse.bbs_mod.entity.IEntityFormProvider;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.film.RecorderMobActionCapture;
import mchorse.bbs_mod.film.RecorderMobCapture;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.items.GunProperties;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import mchorse.bbs_mod.ui.morphing.UIMorphingPanel;
import mchorse.bbs_mod.ui.triggers.UITriggerBlockPanel;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.repos.RepositoryOperation;
import mchorse.bbs_mod.utils.skin.SkinManager;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class ClientNetwork
{
    private static int ids = 0;
    private static Map<Integer, Consumer<BaseType>> callbacks = new HashMap<>();
    private static ClientPacketCrusher crusher = new ClientPacketCrusher();

    private static boolean isBBSModOnServer;

    public static void resetHandshake()
    {
        isBBSModOnServer = false;
        crusher.reset();
    }

    public static boolean isIsBBSModOnServer()
    {
        return isBBSModOnServer;
    }

    /* Network */

    public static void setup()
    {
        CustomPayload.Id<ServerNetwork.BufPayload> C_CLICKED_ID = ServerNetwork.idFor(ServerNetwork.CLIENT_CLICKED_MODEL_BLOCK_PACKET);
        CustomPayload.Id<ServerNetwork.BufPayload> C_PLAYER_FORM_ID = ServerNetwork.idFor(ServerNetwork.CLIENT_PLAYER_FORM_PACKET);
        CustomPayload.Id<ServerNetwork.BufPayload> C_BAY4LLY_SKIN = ServerNetwork.idFor(ServerNetwork.CLIENT_BAY4LLY_SKIN);
        CustomPayload.Id<ServerNetwork.BufPayload> C_PLAY_FILM_ID = ServerNetwork.idFor(ServerNetwork.CLIENT_PLAY_FILM_PACKET);
        CustomPayload.Id<ServerNetwork.BufPayload> C_MANAGER_DATA_ID = ServerNetwork.idFor(ServerNetwork.CLIENT_MANAGER_DATA_PACKET);
        CustomPayload.Id<ServerNetwork.BufPayload> C_STOP_FILM_ID = ServerNetwork.idFor(ServerNetwork.CLIENT_STOP_FILM_PACKET);
        CustomPayload.Id<ServerNetwork.BufPayload> C_HANDSHAKE_ID = ServerNetwork.idFor(ServerNetwork.CLIENT_HANDSHAKE);
        CustomPayload.Id<ServerNetwork.BufPayload> C_RECORDED_ACTIONS_ID = ServerNetwork.idFor(ServerNetwork.CLIENT_RECORDED_ACTIONS);
        CustomPayload.Id<ServerNetwork.BufPayload> C_ANIMATION_STATE_TRIGGER_ID = ServerNetwork.idFor(ServerNetwork.CLIENT_ANIMATION_STATE_TRIGGER);
        CustomPayload.Id<ServerNetwork.BufPayload> C_CHEATS_PERMISSION_ID = ServerNetwork.idFor(ServerNetwork.CLIENT_CHEATS_PERMISSION);
        CustomPayload.Id<ServerNetwork.BufPayload> C_SHARED_FORM_ID = ServerNetwork.idFor(ServerNetwork.CLIENT_SHARED_FORM);
        CustomPayload.Id<ServerNetwork.BufPayload> C_ENTITY_FORM_ID = ServerNetwork.idFor(ServerNetwork.CLIENT_ENTITY_FORM);
        CustomPayload.Id<ServerNetwork.BufPayload> C_ACTORS_ID = ServerNetwork.idFor(ServerNetwork.CLIENT_ACTORS);
        CustomPayload.Id<ServerNetwork.BufPayload> C_GUN_PROPERTIES_ID = ServerNetwork.idFor(ServerNetwork.CLIENT_GUN_PROPERTIES);
        CustomPayload.Id<ServerNetwork.BufPayload> C_PAUSE_FILM_ID = ServerNetwork.idFor(ServerNetwork.CLIENT_PAUSE_FILM);
        CustomPayload.Id<ServerNetwork.BufPayload> C_SELECTED_SLOT_ID = ServerNetwork.idFor(ServerNetwork.CLIENT_SELECTED_SLOT);
        CustomPayload.Id<ServerNetwork.BufPayload> C_ANIM_STATE_MB_TRIGGER_ID = ServerNetwork.idFor(ServerNetwork.CLIENT_ANIMATION_STATE_MODEL_BLOCK_TRIGGER);
        CustomPayload.Id<ServerNetwork.BufPayload> C_REFRESH_MODEL_BLOCKS_ID = ServerNetwork.idFor(ServerNetwork.CLIENT_REFRESH_MODEL_BLOCKS);
        CustomPayload.Id<ServerNetwork.BufPayload> C_CLICKED_TRIGGER_BLOCK_ID = ServerNetwork.idFor(ServerNetwork.CLIENT_CLICKED_TRIGGER_BLOCK_PACKET);
        CustomPayload.Id<ServerNetwork.BufPayload> C_MOB_COMBAT_ACTION_ID = ServerNetwork.idFor(ServerNetwork.CLIENT_MOB_COMBAT_ACTION);
        CustomPayload.Id<ServerNetwork.BufPayload> C_MOB_CONVERSION_ID = ServerNetwork.idFor(ServerNetwork.CLIENT_MOB_CONVERSION);

        PayloadTypeRegistry.playS2C().register(C_CLICKED_ID, ServerNetwork.BufPayload.codecFor(C_CLICKED_ID));
        PayloadTypeRegistry.playS2C().register(C_PLAYER_FORM_ID, ServerNetwork.BufPayload.codecFor(C_PLAYER_FORM_ID));
        PayloadTypeRegistry.playS2C().register(C_BAY4LLY_SKIN, ServerNetwork.BufPayload.codecFor(C_BAY4LLY_SKIN));
        PayloadTypeRegistry.playS2C().register(C_PLAY_FILM_ID, ServerNetwork.BufPayload.codecFor(C_PLAY_FILM_ID));
        PayloadTypeRegistry.playS2C().register(C_MANAGER_DATA_ID, ServerNetwork.BufPayload.codecFor(C_MANAGER_DATA_ID));
        PayloadTypeRegistry.playS2C().register(C_STOP_FILM_ID, ServerNetwork.BufPayload.codecFor(C_STOP_FILM_ID));
        PayloadTypeRegistry.playS2C().register(C_HANDSHAKE_ID, ServerNetwork.BufPayload.codecFor(C_HANDSHAKE_ID));
        PayloadTypeRegistry.playS2C().register(C_RECORDED_ACTIONS_ID, ServerNetwork.BufPayload.codecFor(C_RECORDED_ACTIONS_ID));
        PayloadTypeRegistry.playS2C().register(C_ANIMATION_STATE_TRIGGER_ID, ServerNetwork.BufPayload.codecFor(C_ANIMATION_STATE_TRIGGER_ID));
        PayloadTypeRegistry.playS2C().register(C_CHEATS_PERMISSION_ID, ServerNetwork.BufPayload.codecFor(C_CHEATS_PERMISSION_ID));
        PayloadTypeRegistry.playS2C().register(C_SHARED_FORM_ID, ServerNetwork.BufPayload.codecFor(C_SHARED_FORM_ID));
        PayloadTypeRegistry.playS2C().register(C_ENTITY_FORM_ID, ServerNetwork.BufPayload.codecFor(C_ENTITY_FORM_ID));
        PayloadTypeRegistry.playS2C().register(C_ACTORS_ID, ServerNetwork.BufPayload.codecFor(C_ACTORS_ID));
        PayloadTypeRegistry.playS2C().register(C_GUN_PROPERTIES_ID, ServerNetwork.BufPayload.codecFor(C_GUN_PROPERTIES_ID));
        PayloadTypeRegistry.playS2C().register(C_PAUSE_FILM_ID, ServerNetwork.BufPayload.codecFor(C_PAUSE_FILM_ID));
        PayloadTypeRegistry.playS2C().register(C_SELECTED_SLOT_ID, ServerNetwork.BufPayload.codecFor(C_SELECTED_SLOT_ID));
        PayloadTypeRegistry.playS2C().register(C_ANIM_STATE_MB_TRIGGER_ID, ServerNetwork.BufPayload.codecFor(C_ANIM_STATE_MB_TRIGGER_ID));
        PayloadTypeRegistry.playS2C().register(C_REFRESH_MODEL_BLOCKS_ID, ServerNetwork.BufPayload.codecFor(C_REFRESH_MODEL_BLOCKS_ID));
        PayloadTypeRegistry.playS2C().register(C_CLICKED_TRIGGER_BLOCK_ID, ServerNetwork.BufPayload.codecFor(C_CLICKED_TRIGGER_BLOCK_ID));
        PayloadTypeRegistry.playS2C().register(C_MOB_COMBAT_ACTION_ID, ServerNetwork.BufPayload.codecFor(C_MOB_COMBAT_ACTION_ID));
        PayloadTypeRegistry.playS2C().register(C_MOB_CONVERSION_ID, ServerNetwork.BufPayload.codecFor(C_MOB_CONVERSION_ID));

        ClientPlayNetworking.registerGlobalReceiver(C_CLICKED_ID, (payload, context) -> handleClientModelBlockPacket(context.client(), payload.asPacketByteBuf()));
        ClientPlayNetworking.registerGlobalReceiver(C_PLAYER_FORM_ID, (payload, context) -> handlePlayerFormPacket(context.client(), payload.asPacketByteBuf()));
        ClientPlayNetworking.registerGlobalReceiver(C_BAY4LLY_SKIN, (payload, context) -> handleBay4llySkinPacket(context.client(), payload.asPacketByteBuf()));
        ClientPlayNetworking.registerGlobalReceiver(C_PLAY_FILM_ID, (payload, context) -> handlePlayFilmPacket(context.client(), payload.asPacketByteBuf()));
        ClientPlayNetworking.registerGlobalReceiver(C_MANAGER_DATA_ID, (payload, context) -> handleManagerDataPacket(context.client(), payload.asPacketByteBuf()));
        ClientPlayNetworking.registerGlobalReceiver(C_STOP_FILM_ID, (payload, context) -> handleStopFilmPacket(context.client(), payload.asPacketByteBuf()));
        ClientPlayNetworking.registerGlobalReceiver(C_HANDSHAKE_ID, (payload, context) -> handleHandshakePacket(context.client(), payload.asPacketByteBuf()));
        ClientPlayNetworking.registerGlobalReceiver(C_RECORDED_ACTIONS_ID, (payload, context) -> handleRecordedActionsPacket(context.client(), payload.asPacketByteBuf()));
        ClientPlayNetworking.registerGlobalReceiver(C_ANIMATION_STATE_TRIGGER_ID, (payload, context) -> handleFormTriggerPacket(context.client(), payload.asPacketByteBuf()));
        ClientPlayNetworking.registerGlobalReceiver(C_CHEATS_PERMISSION_ID, (payload, context) -> handleCheatsPermissionPacket(context.client(), payload.asPacketByteBuf()));
        ClientPlayNetworking.registerGlobalReceiver(C_SHARED_FORM_ID, (payload, context) -> handleShareFormPacket(context.client(), payload.asPacketByteBuf()));
        ClientPlayNetworking.registerGlobalReceiver(C_ENTITY_FORM_ID, (payload, context) -> handleEntityFormPacket(context.client(), payload.asPacketByteBuf()));
        ClientPlayNetworking.registerGlobalReceiver(C_ACTORS_ID, (payload, context) -> handleActorsPacket(context.client(), payload.asPacketByteBuf()));
        ClientPlayNetworking.registerGlobalReceiver(C_GUN_PROPERTIES_ID, (payload, context) -> handleGunPropertiesPacket(context.client(), payload.asPacketByteBuf()));
        ClientPlayNetworking.registerGlobalReceiver(C_PAUSE_FILM_ID, (payload, context) -> handlePauseFilmPacket(context.client(), payload.asPacketByteBuf()));
        ClientPlayNetworking.registerGlobalReceiver(C_SELECTED_SLOT_ID, (payload, context) -> handleSelectedSlotPacket(context.client(), payload.asPacketByteBuf()));
        ClientPlayNetworking.registerGlobalReceiver(C_MOB_COMBAT_ACTION_ID, (payload, context) -> handleMobCombatActionPacket(context.client(), payload.asPacketByteBuf()));
        ClientPlayNetworking.registerGlobalReceiver(C_MOB_CONVERSION_ID, (payload, context) -> handleMobConversionPacket(context.client(), payload.asPacketByteBuf()));
        ClientPlayNetworking.registerGlobalReceiver(C_ANIM_STATE_MB_TRIGGER_ID, (payload, context) -> handleAnimationStateModelBlockPacket(context.client(), payload.asPacketByteBuf()));
        ClientPlayNetworking.registerGlobalReceiver(C_REFRESH_MODEL_BLOCKS_ID, (payload, context) -> handleRefreshModelBlocksPacket(context.client(), payload.asPacketByteBuf()));
        ClientPlayNetworking.registerGlobalReceiver(C_CLICKED_TRIGGER_BLOCK_ID, (payload, context) -> handleClickedTriggerBlockPacket(context.client(), payload.asPacketByteBuf()));
    }

    /* Handlers */

    private static void handleClickedTriggerBlockPacket(MinecraftClient client, PacketByteBuf buf)
    {
        BlockPos pos = buf.readBlockPos();

        client.execute(() ->
        {
            BlockEntity entity = client.world.getBlockEntity(pos);

            if (!(entity instanceof TriggerBlockEntity))
            {
                return;
            }

            UIDashboard dashboard = BBSModClient.getDashboard();
            UITriggerBlockPanel panel = dashboard.getPanel(UITriggerBlockPanel.class);

            /* Switch before opening so dashboard onOpen does not restore the last
             * film panel (which would restart paused actor replays). */
            dashboard.setPanel(panel);

            if (!(client.currentScreen instanceof UIScreen screen) || screen.getMenu() != dashboard)
            {
                UIScreen.open(dashboard);
            }

            panel.fill((TriggerBlockEntity) entity, true);
        });
    }

    private static void handleClientModelBlockPacket(MinecraftClient client, PacketByteBuf buf)
    {
        BlockPos pos = buf.readBlockPos();

        client.execute(() ->
        {
            BlockEntity entity = client.world.getBlockEntity(pos);

            if (!(entity instanceof ModelBlockEntity))
            {
                return;
            }

            UIBaseMenu menu = UIScreen.getCurrentMenu();
            UIDashboard dashboard = BBSModClient.getDashboard();
            UIModelBlockPanel panel = dashboard.getPanels().getPanel(UIModelBlockPanel.class);

            /* Switch before opening so dashboard onOpen does not restore the last
             * film panel (which would restart paused actor replays). */
            dashboard.setPanel(panel);

            if (menu != dashboard)
            {
                UIScreen.open(dashboard);
            }

            panel.fill((ModelBlockEntity) entity, true);
        });
    }

    private static void handlePlayerFormPacket(MinecraftClient client, PacketByteBuf buf)
    {
        crusher.receive(buf, (bytes, packetByteBuf) ->
        {
            int id = packetByteBuf.readInt();
            Form form = FormUtils.fromData(DataStorageUtils.readFromBytes(bytes));

            final Form finalForm = form;

            client.execute(() ->
            {
                Entity entity = client.world.getEntityById(id);
                Morph morph = Morph.getMorph(entity);

                if (morph != null)
                {
                    morph.setForm(finalForm);
                }
            });
        });
    }

    private static void handlePlayFilmPacket(MinecraftClient client, PacketByteBuf buf)
    {
        crusher.receive(buf, (bytes, packetByteBuf) ->
        {
            String filmId = packetByteBuf.readString();
            boolean withCamera = packetByteBuf.readBoolean();
            Film film = new Film();

            film.setId(filmId);
            film.fromData(DataStorageUtils.readFromBytes(bytes));

            client.execute(() -> Films.playFilm(film, withCamera));
        });
    }

    private static void handleManagerDataPacket(MinecraftClient client, PacketByteBuf buf)
    {
        crusher.receive(buf, (bytes, packetByteBuf) ->
        {
            int callbackId = packetByteBuf.readInt();
            RepositoryOperation op = RepositoryOperation.values()[packetByteBuf.readInt()];
            BaseType data = DataStorageUtils.readFromBytes(bytes);

            client.execute(() ->
            {
                Consumer<BaseType> callback = callbacks.remove(callbackId);

                if (callback != null)
                {
                    callback.accept(data);
                }
            });
        });
    }

    private static void handleStopFilmPacket(MinecraftClient client, PacketByteBuf buf)
    {
        String filmId = buf.readString();

        client.execute(() -> Films.stopFilm(filmId));
    }

    private static void handleHandshakePacket(MinecraftClient client, PacketByteBuf buf)
    {
        isBBSModOnServer = true;
    }

    private static void handleRecordedActionsPacket(MinecraftClient client, PacketByteBuf buf)
    {
        crusher.receive(buf, (bytes, packetByteBuf) ->
        {
            String filmId = packetByteBuf.readString();
            int replayId = packetByteBuf.readInt();
            int tick = packetByteBuf.readInt();
            BaseType data = DataStorageUtils.readFromBytes(bytes);

            client.execute(() ->
            {
                UIDashboard dashboard = BBSModClient.peekDashboard();

                if (dashboard == null)
                {
                    return;
                }

                UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);

                if (panel != null)
                {
                    panel.receiveActions(filmId, replayId, tick, data);
                }
            });
        });
    }

    private static void handleMobCombatActionPacket(MinecraftClient client, PacketByteBuf buf)
    {
        int victimEntityId = buf.readInt();
        int sourceEntityId = buf.readInt();
        float amount = buf.readFloat();
        byte kind = buf.readByte();

        client.execute(() ->
        {
            RecorderMobActionCapture.handleServerCombat(victimEntityId, sourceEntityId, amount, kind);
        });
    }

    private static void handleMobConversionPacket(MinecraftClient client, PacketByteBuf buf)
    {
        int oldEntityId = buf.readInt();
        int newEntityId = buf.readInt();

        client.execute(() ->
        {
            RecorderMobCapture.handleServerConversion(oldEntityId, newEntityId);
        });
    }

    private static void handleFormTriggerPacket(MinecraftClient client, PacketByteBuf buf)
    {
        int id = buf.readInt();
        String triggerId = buf.readString();
        int type = buf.readInt();

        client.execute(() ->
        {
            Entity entity = client.world.getEntityById(id);
            Morph morph = Morph.getMorph(entity);

            if (morph != null && morph.getForm() != null)
            {
                morph.getForm().playState(triggerId);
            }

            if (entity instanceof LivingEntity livingEntity && type > 0)
            {
                ItemStack stackInHand = livingEntity.getStackInHand(type == 1 ? Hand.MAIN_HAND : Hand.OFF_HAND);
                ModelProperties properties = BBSModClient.getItemStackProperties(stackInHand);

                if (properties != null && properties.getForm() != null)
                {
                    properties.getForm().playState(triggerId);
                }
            }
        });
    }

    private static void handleCheatsPermissionPacket(MinecraftClient client, PacketByteBuf buf)
    {
        boolean cheats = buf.readBoolean();

        client.execute(() ->
        {
            client.player.setClientPermissionLevel(cheats ? 4 : 0);
        });
    }

    private static void handleShareFormPacket(MinecraftClient client, PacketByteBuf buf)
    {
        crusher.receive(buf, (bytes, packetByteBuf) ->
        {
            final Form finalForm = FormUtils.fromData(DataStorageUtils.readFromBytes(bytes));

            if (finalForm == null)
            {
                return;
            }

            client.execute(() ->
            {
                UIBaseMenu menu = UIScreen.getCurrentMenu();
                UIDashboard dashboard = BBSModClient.getDashboard();

                if (menu == null)
                {
                    UIScreen.open(dashboard);
                }

                dashboard.setPanel(dashboard.getPanel(UIMorphingPanel.class));
                BBSModClient.getFormCategories().getRecentForms().getCategories().get(0).addForm(finalForm);
                dashboard.context.notifyInfo(UIKeys.FORMS_SHARED_NOTIFICATION.format(finalForm.getDisplayName()));
            });
        });
    }

    private static void handleEntityFormPacket(MinecraftClient client, PacketByteBuf buf)
    {
        crusher.receive(buf, (bytes, packetByteBuf) ->
        {
            final Form finalForm = FormUtils.fromData(DataStorageUtils.readFromBytes(bytes));

            if (finalForm == null)
            {
                return;
            }

            int entityId = buf.readInt();

            client.execute(() ->
            {
                Entity entity = client.world.getEntityById(entityId);

                if (entity instanceof IEntityFormProvider provider)
                {
                    provider.setForm(finalForm);
                }
            });
        });
    }

    private static void handleActorsPacket(MinecraftClient client, PacketByteBuf buf)
    {
        Map<String, Integer> actors = new HashMap<>();
        String filmId = buf.readString();

        for (int i = 0, c = buf.readInt(); i < c; i++)
        {
            String key = buf.readString();
            int entityId = buf.readInt();

            actors.put(key, entityId);
        }

        client.execute(() ->
        {
            UIDashboard dashboard = BBSModClient.peekDashboard();

            if (dashboard == null)
            {
                return;
            }

            UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);

            if (panel != null)
            {
                panel.updateActors(filmId, actors);
            }

            BBSModClient.getFilms().updateActors(filmId, actors);
        });
    }

    private static void handleGunPropertiesPacket(MinecraftClient client, PacketByteBuf buf)
    {
        GunProperties properties = new GunProperties();
        int entityId = buf.readInt();

        properties.fromNetwork(buf);

        client.execute(() ->
        {
            Entity entity = client.world.getEntityById(entityId);

            if (entity instanceof GunProjectileEntity projectile)
            {
                projectile.setProperties(properties);
                projectile.calculateDimensions();
            }
        });
    }

    private static void handlePauseFilmPacket(MinecraftClient client, PacketByteBuf buf)
    {
        String filmId = buf.readString();

        client.execute(() ->
        {
            Films.togglePauseFilm(filmId);
        });
    }
    
    private static void handleBay4llySkinPacket(MinecraftClient client, PacketByteBuf buf)
    {
        crusher.receive(buf, (bytes, packetByteBuf) ->
        {
            String playerName = packetByteBuf.readString();
            client.execute(() ->
            {
                try
                {
                    SkinManager.saveSkin(playerName, bytes);
                }
                catch (Exception e)
                {
                }
            });
        });
    }

    private static void handleSelectedSlotPacket(MinecraftClient client, PacketByteBuf buf)
    {
        int slot = buf.readInt();

        client.execute(() ->
        {
            client.player.getInventory().selectedSlot = slot;
        });
    }

    private static void handleAnimationStateModelBlockPacket(MinecraftClient client, PacketByteBuf buf)
    {
        BlockPos pos = buf.readBlockPos();
        String state = buf.readString();

        client.execute(() ->
        {
            BlockEntity blockEntity = client.world.getBlockEntity(pos);

            if (blockEntity instanceof ModelBlockEntity block)
            {
                if (block.getProperties().getForm() != null)
                {
                    block.getProperties().getForm().playState(state);
                }
            }
        });
    }

    private static void handleRefreshModelBlocksPacket(MinecraftClient client, PacketByteBuf buf)
    {
        int range = buf.readInt();

        client.execute(() ->
        {
            for (ModelBlockEntity mb : BBSRendering.capturedModelBlocks)
            {
                ModelProperties properties = mb.getProperties();
                int random = (int) (Math.random() * range);

                properties.setForm(FormUtils.copy(properties.getForm()));

                while (random > 0)
                {
                    properties.update(mb.getEntity());

                    random -= 1;
                }
            }
        });
    }

    /* API */
    
    public static void sendModelBlockForm(BlockPos pos, ModelBlockEntity modelBlock)
    {
        crusher.send(MinecraftClient.getInstance().player, ServerNetwork.SERVER_MODEL_BLOCK_FORM_PACKET, modelBlock.getProperties().toData(), (packetByteBuf) ->
        {
            packetByteBuf.writeBlockPos(pos);
        });
    }

    public static void sendTriggerBlockUpdate(BlockPos pos, TriggerBlockEntity entity)
    {
        MapType data = new MapType();

        data.put("left", entity.left.toData());
        data.put("right", entity.right.toData());
        data.put("enter", entity.enter.toData());
        data.put("exit", entity.exit.toData());
        data.put("whileIn", entity.whileIn.toData());
        data.putInt("regionDelay", entity.regionDelay.get());
        data.put("pos1", entity.pos1.toData());
        data.put("pos2", entity.pos2.toData());
        data.put("regionOffset", entity.regionOffset.toData());
        data.put("regionSize", entity.regionSize.toData());
        data.putBool("collidable", entity.collidable.get());
        data.putBool("region", entity.region.get());

        crusher.send(MinecraftClient.getInstance().player, ServerNetwork.SERVER_TRIGGER_BLOCK_UPDATE, data, (packetByteBuf) ->
        {
            packetByteBuf.writeBlockPos(pos);
        });
    }

    public static void sendPlayerForm(Form form)
    {
        MapType mapType = FormUtils.toData(form);

        crusher.send(MinecraftClient.getInstance().player, ServerNetwork.SERVER_PLAYER_FORM_PACKET, mapType == null ? new MapType() : mapType, (packetByteBuf) ->
        {});
    }

    /**
     * Ask the server to change this client's gamemode without chat feedback.
     */
    public static void sendSetGameMode(GameMode mode)
    {
        if (mode == null || MinecraftClient.getInstance().player == null)
        {
            return;
        }

        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeVarInt(mode.getId());
        ClientPlayNetworking.send(ServerNetwork.BufPayload.from(buf, ServerNetwork.idFor(ServerNetwork.SERVER_SET_GAME_MODE)));
    }

    public static void sendModelBlockTransforms(MapType data)
    {
        crusher.send(MinecraftClient.getInstance().player, ServerNetwork.SERVER_MODEL_BLOCK_TRANSFORMS_PACKET, data, (packetByteBuf) ->
        {});
    }

    public static void sendManagerDataLoad(String id, Consumer<BaseType> consumer)
    {
        MapType mapType = new MapType();

        mapType.putString("id", id);
        ClientNetwork.sendManagerData(RepositoryOperation.LOAD, mapType, consumer);
    }

    public static void sendManagerData(RepositoryOperation op, BaseType data, Consumer<BaseType> consumer)
    {
        int id = ids;

        callbacks.put(id, consumer);
        sendManagerData(id, op, data);

        ids += 1;
    }

    public static void sendManagerData(int callbackId, RepositoryOperation op, BaseType data)
    {
        crusher.send(MinecraftClient.getInstance().player, ServerNetwork.SERVER_MANAGER_DATA_PACKET, data, (packetByteBuf) ->
        {
            packetByteBuf.writeInt(callbackId);
            packetByteBuf.writeInt(op.ordinal());
        });
    }

    public static void sendActionRecording(String filmId, int replayId, int tick, int countdown, boolean state)
    {
        sendActionRecording(filmId, replayId, tick, countdown, state, false);
    }

    /**
     * @param recorderOnly true = film-editor viewport (keep FILM_EDITOR ActionPlayer);
     *                     false = Outside/world recording (spawn RECORDING ActionPlayer).
     */
    public static void sendActionRecording(String filmId, int replayId, int tick, int countdown, boolean state, boolean recorderOnly)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeString(filmId);
        buf.writeInt(replayId);
        buf.writeInt(tick);
        buf.writeInt(countdown);
        buf.writeBoolean(state);
        buf.writeBoolean(recorderOnly);

        ClientPlayNetworking.send(ServerNetwork.BufPayload.from(buf, ServerNetwork.idFor(ServerNetwork.SERVER_ACTION_RECORDING)));
    }

    public static void sendToggleFilm(String filmId, boolean withCamera)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeString(filmId);
        buf.writeBoolean(withCamera);

        ClientPlayNetworking.send(ServerNetwork.BufPayload.from(buf, ServerNetwork.idFor(ServerNetwork.SERVER_TOGGLE_FILM)));
    }

    public static void sendActionState(String filmId, ActionState state, int tick)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeString(filmId);
        buf.writeByte(state.ordinal());
        buf.writeInt(tick);

        ClientPlayNetworking.send(ServerNetwork.BufPayload.from(buf, ServerNetwork.idFor(ServerNetwork.SERVER_ACTION_CONTROL)));
    }

    public static void sendSyncData(String filmId, BaseValue data)
    {
        crusher.send(MinecraftClient.getInstance().player, ServerNetwork.SERVER_FILM_DATA_SYNC, data.toData(), (packetByteBuf) ->
        {
            DataPath path = data.getPath();

            packetByteBuf.writeString(filmId);
            packetByteBuf.writeInt(path.strings.size());

            for (String string : path.strings)
            {
                packetByteBuf.writeString(string);
            }
        });
    }

    public static void sendTeleport(PlayerEntity entity, double x, double y, double z)
    {
        sendTeleport(x, y, z, entity.getHeadYaw(), entity.getHeadYaw(), entity.getPitch());
    }

    public static void sendTeleport(double x, double y, double z, float yaw, float bodyYaw, float pitch)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeFloat(bodyYaw);
        buf.writeFloat(pitch);

        ClientPlayNetworking.send(ServerNetwork.BufPayload.from(buf, ServerNetwork.idFor(ServerNetwork.SERVER_PLAYER_TP)));
    }

    public static void sendFormTrigger(String triggerId, int type)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeString(triggerId);
        buf.writeInt(type);

        ClientPlayNetworking.send(ServerNetwork.BufPayload.from(buf, ServerNetwork.idFor(ServerNetwork.SERVER_ANIMATION_STATE_TRIGGER)));
    }

    public static void sendSharedForm(Form form, UUID uuid)
    {
        MapType mapType = FormUtils.toData(form);

        crusher.send(MinecraftClient.getInstance().player, ServerNetwork.SERVER_SHARED_FORM, mapType == null ? new MapType() : mapType, (packetByteBuf) ->
        {
            packetByteBuf.writeUuid(uuid);
        });
    }

    public static void sendZoom(boolean zoom)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeBoolean(zoom);

        ClientPlayNetworking.send(ServerNetwork.BufPayload.from(buf, ServerNetwork.idFor(ServerNetwork.SERVER_ZOOM)));
    }

    public static void sendPauseFilm(String filmId)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeString(filmId);

        ClientPlayNetworking.send(ServerNetwork.BufPayload.from(buf, ServerNetwork.idFor(ServerNetwork.SERVER_PAUSE_FILM)));
    }

    public static void sendTriggerBlockClick(BlockPos pos)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeBlockPos(pos);

        ClientPlayNetworking.send(ServerNetwork.BufPayload.from(buf, ServerNetwork.idFor(ServerNetwork.SERVER_TRIGGER_BLOCK_CLICK)));
    }
}
