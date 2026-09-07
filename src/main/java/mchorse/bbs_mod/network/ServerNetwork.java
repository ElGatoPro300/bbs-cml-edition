package mchorse.bbs_mod.network;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.ActionManager;
import mchorse.bbs_mod.actions.ActionPlayer;
import mchorse.bbs_mod.actions.ActionRecorder;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.actions.PlayerType;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.TriggerBlockEntity;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ByteType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.entity.GunProjectileEntity;
import mchorse.bbs_mod.entity.IEntityFormProvider;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmManager;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.items.GunProperties;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.EnumUtils;
import mchorse.bbs_mod.utils.PermissionUtils;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.repos.RepositoryOperation;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TypedEntityData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ServerNetwork
{
    public static final int STATE_TRIGGER_MORPH = 0;
    public static final int STATE_TRIGGER_MAIN_HAND_ITEM = 1;
    public static final int STATE_TRIGGER_OFF_HAND_ITEM = 2;

    public static final Identifier CLIENT_CLICKED_MODEL_BLOCK_PACKET = Identifier.of(BBSMod.MOD_ID, "c1");
    public static final Identifier CLIENT_PLAYER_FORM_PACKET = Identifier.of(BBSMod.MOD_ID, "c2");
    public static final Identifier CLIENT_PLAY_FILM_PACKET = Identifier.of(BBSMod.MOD_ID, "c3");
    public static final Identifier CLIENT_MANAGER_DATA_PACKET = Identifier.of(BBSMod.MOD_ID, "c4");
    public static final Identifier CLIENT_STOP_FILM_PACKET = Identifier.of(BBSMod.MOD_ID, "c5");
    public static final Identifier CLIENT_HANDSHAKE = Identifier.of(BBSMod.MOD_ID, "c6");
    public static final Identifier CLIENT_RECORDED_ACTIONS = Identifier.of(BBSMod.MOD_ID, "c7");
    public static final Identifier CLIENT_ANIMATION_STATE_TRIGGER = Identifier.of(BBSMod.MOD_ID, "c8");
    public static final Identifier CLIENT_CHEATS_PERMISSION = Identifier.of(BBSMod.MOD_ID, "c9");
    public static final Identifier CLIENT_SHARED_FORM = Identifier.of(BBSMod.MOD_ID, "c10");
    public static final Identifier CLIENT_ENTITY_FORM = Identifier.of(BBSMod.MOD_ID, "c11");
    public static final Identifier CLIENT_ACTORS = Identifier.of(BBSMod.MOD_ID, "c12");
    public static final Identifier CLIENT_GUN_PROPERTIES = Identifier.of(BBSMod.MOD_ID, "c13");
    public static final Identifier CLIENT_PAUSE_FILM = Identifier.of(BBSMod.MOD_ID, "c14");
    public static final Identifier CLIENT_SELECTED_SLOT = Identifier.of(BBSMod.MOD_ID, "c15");
    public static final Identifier CLIENT_ANIMATION_STATE_MODEL_BLOCK_TRIGGER = Identifier.of(BBSMod.MOD_ID, "c16");
    public static final Identifier CLIENT_REFRESH_MODEL_BLOCKS = Identifier.of(BBSMod.MOD_ID, "c17");
    public static final Identifier CLIENT_CLICKED_TRIGGER_BLOCK_PACKET = Identifier.of(BBSMod.MOD_ID, "c18");
    public static final Identifier CLIENT_BAY4LLY_SKIN = Identifier.of(BBSMod.MOD_ID, "c19");
    public static final Identifier CLIENT_MOB_COMBAT_ACTION = Identifier.of(BBSMod.MOD_ID, "c20");
    public static final Identifier CLIENT_MOB_CONVERSION = Identifier.of(BBSMod.MOD_ID, "c21");

    public static final byte MOB_COMBAT_KIND_MELEE = 0;
    public static final byte MOB_COMBAT_KIND_PROJECTILE = 1;
    public static final byte MOB_COMBAT_KIND_DAMAGE = 2;

    public static final Identifier SERVER_MODEL_BLOCK_FORM_PACKET = Identifier.of(BBSMod.MOD_ID, "s1");
    public static final Identifier SERVER_MODEL_BLOCK_TRANSFORMS_PACKET = Identifier.of(BBSMod.MOD_ID, "s2");
    public static final Identifier SERVER_PLAYER_FORM_PACKET = Identifier.of(BBSMod.MOD_ID, "s3");
    public static final Identifier SERVER_MANAGER_DATA_PACKET = Identifier.of(BBSMod.MOD_ID, "s4");
    public static final Identifier SERVER_ACTION_RECORDING = Identifier.of(BBSMod.MOD_ID, "s5");
    public static final Identifier SERVER_TOGGLE_FILM = Identifier.of(BBSMod.MOD_ID, "s6");
    public static final Identifier SERVER_ACTION_CONTROL = Identifier.of(BBSMod.MOD_ID, "s7");
    public static final Identifier SERVER_FILM_DATA_SYNC = Identifier.of(BBSMod.MOD_ID, "s8");
    public static final Identifier SERVER_PLAYER_TP = Identifier.of(BBSMod.MOD_ID, "s9");
    public static final Identifier SERVER_ANIMATION_STATE_TRIGGER = Identifier.of(BBSMod.MOD_ID, "s10");
    public static final Identifier SERVER_SHARED_FORM = Identifier.of(BBSMod.MOD_ID, "s11");
    public static final Identifier SERVER_ZOOM = Identifier.of(BBSMod.MOD_ID, "s12");
    public static final Identifier SERVER_PAUSE_FILM = Identifier.of(BBSMod.MOD_ID, "s13");
    public static final Identifier SERVER_TRIGGER_BLOCK_UPDATE = Identifier.of(BBSMod.MOD_ID, "s14");
    public static final Identifier SERVER_TRIGGER_BLOCK_CLICK = Identifier.of(BBSMod.MOD_ID, "s15");
    public static final Identifier SERVER_SET_GAME_MODE = Identifier.of(BBSMod.MOD_ID, "s16");

    private static ServerPacketCrusher crusher = new ServerPacketCrusher();

    public static CustomPayload.Id<BufPayload> idFor(Identifier identifier)
    {
        return new CustomPayload.Id<>(identifier);
    }

    public record BufPayload(byte[] data, CustomPayload.Id<BufPayload> id) implements CustomPayload
    {
        public static BufPayload from(PacketByteBuf buf, CustomPayload.Id<BufPayload> id)
        {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new BufPayload(bytes, id);
        }

        public PacketByteBuf asPacketByteBuf()
        {
            PacketByteBuf out = PacketByteBufs.create();
            out.writeBytes(this.data);
            return out;
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId()
        {
            return id;
        }

        public static PacketCodec<RegistryByteBuf, BufPayload> codecFor(CustomPayload.Id<BufPayload> id)
        {
            return new PacketCodec<RegistryByteBuf, BufPayload>()
            {
                @Override
                public BufPayload decode(RegistryByteBuf byteBuf)
                {
                    byte[] bytes = new byte[byteBuf.readableBytes()];
                    byteBuf.readBytes(bytes);
                    return new BufPayload(bytes, id);
                }

                @Override
                public void encode(RegistryByteBuf byteBuf, BufPayload payload)
                {
                    byteBuf.writeBytes(payload.data);
                }
            };
        }
    }

    public static void reset()
    {
        crusher.reset();
    }

    public static void setup()
    {
        PayloadTypeRegistry.playC2S().register(idFor(SERVER_MODEL_BLOCK_FORM_PACKET), BufPayload.codecFor(idFor(SERVER_MODEL_BLOCK_FORM_PACKET)));
        PayloadTypeRegistry.playC2S().register(idFor(SERVER_MODEL_BLOCK_TRANSFORMS_PACKET), BufPayload.codecFor(idFor(SERVER_MODEL_BLOCK_TRANSFORMS_PACKET)));
        PayloadTypeRegistry.playC2S().register(idFor(SERVER_PLAYER_FORM_PACKET), BufPayload.codecFor(idFor(SERVER_PLAYER_FORM_PACKET)));
        PayloadTypeRegistry.playC2S().register(idFor(SERVER_MANAGER_DATA_PACKET), BufPayload.codecFor(idFor(SERVER_MANAGER_DATA_PACKET)));
        PayloadTypeRegistry.playC2S().register(idFor(SERVER_ACTION_RECORDING), BufPayload.codecFor(idFor(SERVER_ACTION_RECORDING)));
        PayloadTypeRegistry.playC2S().register(idFor(SERVER_TOGGLE_FILM), BufPayload.codecFor(idFor(SERVER_TOGGLE_FILM)));
        PayloadTypeRegistry.playC2S().register(idFor(SERVER_ACTION_CONTROL), BufPayload.codecFor(idFor(SERVER_ACTION_CONTROL)));
        PayloadTypeRegistry.playC2S().register(idFor(SERVER_FILM_DATA_SYNC), BufPayload.codecFor(idFor(SERVER_FILM_DATA_SYNC)));
        PayloadTypeRegistry.playC2S().register(idFor(SERVER_PLAYER_TP), BufPayload.codecFor(idFor(SERVER_PLAYER_TP)));
        PayloadTypeRegistry.playC2S().register(idFor(SERVER_ANIMATION_STATE_TRIGGER), BufPayload.codecFor(idFor(SERVER_ANIMATION_STATE_TRIGGER)));
        PayloadTypeRegistry.playC2S().register(idFor(SERVER_SHARED_FORM), BufPayload.codecFor(idFor(SERVER_SHARED_FORM)));
        PayloadTypeRegistry.playC2S().register(idFor(SERVER_ZOOM), BufPayload.codecFor(idFor(SERVER_ZOOM)));
        PayloadTypeRegistry.playC2S().register(idFor(SERVER_PAUSE_FILM), BufPayload.codecFor(idFor(SERVER_PAUSE_FILM)));
        PayloadTypeRegistry.playC2S().register(idFor(SERVER_TRIGGER_BLOCK_UPDATE), BufPayload.codecFor(idFor(SERVER_TRIGGER_BLOCK_UPDATE)));
        PayloadTypeRegistry.playC2S().register(idFor(SERVER_TRIGGER_BLOCK_CLICK), BufPayload.codecFor(idFor(SERVER_TRIGGER_BLOCK_CLICK)));
        PayloadTypeRegistry.playC2S().register(idFor(SERVER_SET_GAME_MODE), BufPayload.codecFor(idFor(SERVER_SET_GAME_MODE)));

        try {
            Class<?> envTypeClass = Class.forName("net.fabricmc.api.EnvType");
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            Object envType = loaderClass.getMethod("getEnvironmentType").invoke(loader);
            Object serverEnum = envTypeClass.getField("SERVER").get(null);

            if (envType == serverEnum) {
                PayloadTypeRegistry.playS2C().register(idFor(CLIENT_CLICKED_MODEL_BLOCK_PACKET), BufPayload.codecFor(idFor(CLIENT_CLICKED_MODEL_BLOCK_PACKET)));
                PayloadTypeRegistry.playS2C().register(idFor(CLIENT_PLAYER_FORM_PACKET), BufPayload.codecFor(idFor(CLIENT_PLAYER_FORM_PACKET)));
                PayloadTypeRegistry.playS2C().register(idFor(CLIENT_PLAY_FILM_PACKET), BufPayload.codecFor(idFor(CLIENT_PLAY_FILM_PACKET)));
                PayloadTypeRegistry.playS2C().register(idFor(CLIENT_MANAGER_DATA_PACKET), BufPayload.codecFor(idFor(CLIENT_MANAGER_DATA_PACKET)));
                PayloadTypeRegistry.playS2C().register(idFor(CLIENT_STOP_FILM_PACKET), BufPayload.codecFor(idFor(CLIENT_STOP_FILM_PACKET)));
                PayloadTypeRegistry.playS2C().register(idFor(CLIENT_HANDSHAKE), BufPayload.codecFor(idFor(CLIENT_HANDSHAKE)));
                PayloadTypeRegistry.playS2C().register(idFor(CLIENT_RECORDED_ACTIONS), BufPayload.codecFor(idFor(CLIENT_RECORDED_ACTIONS)));
                PayloadTypeRegistry.playS2C().register(idFor(CLIENT_ANIMATION_STATE_TRIGGER), BufPayload.codecFor(idFor(CLIENT_ANIMATION_STATE_TRIGGER)));
                PayloadTypeRegistry.playS2C().register(idFor(CLIENT_CHEATS_PERMISSION), BufPayload.codecFor(idFor(CLIENT_CHEATS_PERMISSION)));
                PayloadTypeRegistry.playS2C().register(idFor(CLIENT_SHARED_FORM), BufPayload.codecFor(idFor(CLIENT_SHARED_FORM)));
                PayloadTypeRegistry.playS2C().register(idFor(CLIENT_ENTITY_FORM), BufPayload.codecFor(idFor(CLIENT_ENTITY_FORM)));
                PayloadTypeRegistry.playS2C().register(idFor(CLIENT_ACTORS), BufPayload.codecFor(idFor(CLIENT_ACTORS)));
                PayloadTypeRegistry.playS2C().register(idFor(CLIENT_GUN_PROPERTIES), BufPayload.codecFor(idFor(CLIENT_GUN_PROPERTIES)));
                PayloadTypeRegistry.playS2C().register(idFor(CLIENT_PAUSE_FILM), BufPayload.codecFor(idFor(CLIENT_PAUSE_FILM)));
                PayloadTypeRegistry.playS2C().register(idFor(CLIENT_SELECTED_SLOT), BufPayload.codecFor(idFor(CLIENT_SELECTED_SLOT)));
                PayloadTypeRegistry.playS2C().register(idFor(CLIENT_ANIMATION_STATE_MODEL_BLOCK_TRIGGER), BufPayload.codecFor(idFor(CLIENT_ANIMATION_STATE_MODEL_BLOCK_TRIGGER)));
                PayloadTypeRegistry.playS2C().register(idFor(CLIENT_REFRESH_MODEL_BLOCKS), BufPayload.codecFor(idFor(CLIENT_REFRESH_MODEL_BLOCKS)));
                PayloadTypeRegistry.playS2C().register(idFor(CLIENT_CLICKED_TRIGGER_BLOCK_PACKET), BufPayload.codecFor(idFor(CLIENT_CLICKED_TRIGGER_BLOCK_PACKET)));
                PayloadTypeRegistry.playS2C().register(idFor(CLIENT_MOB_COMBAT_ACTION), BufPayload.codecFor(idFor(CLIENT_MOB_COMBAT_ACTION)));
                PayloadTypeRegistry.playS2C().register(idFor(CLIENT_MOB_CONVERSION), BufPayload.codecFor(idFor(CLIENT_MOB_CONVERSION)));
            }
        } catch (Throwable t) {
        }

        ServerPlayNetworking.registerGlobalReceiver(idFor(SERVER_MODEL_BLOCK_FORM_PACKET), (payload, context) -> handleModelBlockFormPacket(context.server(), context.player(), payload.asPacketByteBuf()));
        ServerPlayNetworking.registerGlobalReceiver(idFor(SERVER_MODEL_BLOCK_TRANSFORMS_PACKET), (payload, context) -> handleModelBlockTransformsPacket(context.server(), context.player(), payload.asPacketByteBuf()));
        ServerPlayNetworking.registerGlobalReceiver(idFor(SERVER_PLAYER_FORM_PACKET), (payload, context) -> handlePlayerFormPacket(context.server(), context.player(), payload.asPacketByteBuf()));
        ServerPlayNetworking.registerGlobalReceiver(idFor(SERVER_MANAGER_DATA_PACKET), (payload, context) -> handleManagerDataPacket(context.server(), context.player(), payload.asPacketByteBuf()));
        ServerPlayNetworking.registerGlobalReceiver(idFor(SERVER_ACTION_RECORDING), (payload, context) -> handleActionRecording(context.server(), context.player(), payload.asPacketByteBuf()));
        ServerPlayNetworking.registerGlobalReceiver(idFor(SERVER_TOGGLE_FILM), (payload, context) -> handleToggleFilm(context.server(), context.player(), payload.asPacketByteBuf()));
        ServerPlayNetworking.registerGlobalReceiver(idFor(SERVER_ACTION_CONTROL), (payload, context) -> handleActionControl(context.server(), context.player(), payload.asPacketByteBuf()));
        ServerPlayNetworking.registerGlobalReceiver(idFor(SERVER_FILM_DATA_SYNC), (payload, context) -> handleSyncData(context.server(), context.player(), payload.asPacketByteBuf()));
        ServerPlayNetworking.registerGlobalReceiver(idFor(SERVER_PLAYER_TP), (payload, context) -> handleTeleportPlayer(context.server(), context.player(), payload.asPacketByteBuf()));
        ServerPlayNetworking.registerGlobalReceiver(idFor(SERVER_ANIMATION_STATE_TRIGGER), (payload, context) -> handleAnimationStateTriggerPacket(context.server(), context.player(), payload.asPacketByteBuf()));
        ServerPlayNetworking.registerGlobalReceiver(idFor(SERVER_SHARED_FORM), (payload, context) -> handleSharedFormPacket(context.server(), context.player(), payload.asPacketByteBuf()));
        ServerPlayNetworking.registerGlobalReceiver(idFor(SERVER_ZOOM), (payload, context) -> handleZoomPacket(context.server(), context.player(), payload.asPacketByteBuf()));
        ServerPlayNetworking.registerGlobalReceiver(idFor(SERVER_PAUSE_FILM), (payload, context) -> handlePauseFilmPacket(context.server(), context.player(), payload.asPacketByteBuf()));
        ServerPlayNetworking.registerGlobalReceiver(idFor(SERVER_TRIGGER_BLOCK_UPDATE), (payload, context) -> handleTriggerBlockUpdatePacket(context.server(), context.player(), payload.asPacketByteBuf()));
        ServerPlayNetworking.registerGlobalReceiver(idFor(SERVER_TRIGGER_BLOCK_CLICK), (payload, context) -> handleTriggerBlockClickPacket(context.server(), context.player(), payload.asPacketByteBuf()));
        ServerPlayNetworking.registerGlobalReceiver(idFor(SERVER_SET_GAME_MODE), (payload, context) -> handleSetGameModePacket(context.server(), context.player(), payload.asPacketByteBuf()));
    }

    /* Handlers */

    private static void handleModelBlockFormPacket(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        crusher.receive(buf, (bytes, packetByteBuf) ->
        {
            BlockPos pos = packetByteBuf.readBlockPos();

            try
            {
                MapType data = (MapType) DataStorageUtils.readFromBytes(bytes);

                server.execute(() ->
                {
                    World world = player.getEntityWorld();
                    BlockEntity be = world.getBlockEntity(pos);

                    if (be instanceof ModelBlockEntity modelBlock)
                    {
                        modelBlock.updateForm(data, world);
                    }
                });
            }
            catch (Exception e)
            {}
        });
    }

    private static void handleTriggerBlockUpdatePacket(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        crusher.receive(buf, (bytes, packetByteBuf) ->
        {
            BlockPos pos = packetByteBuf.readBlockPos();

            try
            {
                MapType data = (MapType) DataStorageUtils.readFromBytes(bytes);

                server.execute(() ->
                {
                    World world = player.getEntityWorld();
                    BlockEntity be = world.getBlockEntity(pos);

                    if (be instanceof TriggerBlockEntity trigger)
                    {
                        if (data.has("left")) trigger.left.fromData(data.getList("left"));
                        if (data.has("right")) trigger.right.fromData(data.getList("right"));
                        if (data.has("enter")) trigger.enter.fromData(data.getList("enter"));
                        if (data.has("exit")) trigger.exit.fromData(data.getList("exit"));
                        if (data.has("whileIn")) trigger.whileIn.fromData(data.getList("whileIn"));
                        if (data.has("regionDelay")) trigger.regionDelay.set(data.getInt("regionDelay"));
                        if (data.has("pos1")) trigger.pos1.fromData(data.getList("pos1"));
                        if (data.has("pos2")) trigger.pos2.fromData(data.getList("pos2"));
                        if (data.has("regionOffset")) trigger.regionOffset.fromData(data.getList("regionOffset"));
                        if (data.has("regionSize")) trigger.regionSize.fromData(data.getList("regionSize"));
                        if (data.has("collidable")) trigger.collidable.set(data.getBool("collidable"));
                        if (data.has("region")) trigger.region.set(data.getBool("region"));

                        trigger.markDirty();
                        world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
                    }
                });
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        });
    }

    private static void handleTriggerBlockClickPacket(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        BlockPos pos = buf.readBlockPos();

        server.execute(() ->
        {
            World world = player.getEntityWorld();
            BlockEntity be = world.getBlockEntity(pos);

            if (be instanceof TriggerBlockEntity trigger)
            {
                trigger.trigger(player, false);
            }
        });
    }

    /**
     * Silent gamemode change for Film / Model Block editors (no chat feedback).
     */
    private static void handleSetGameModePacket(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        int modeId = buf.readVarInt();

        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        GameMode mode = GameMode.byId(Integer.toString(modeId));

        if (mode == null)
        {
            return;
        }

        server.execute(() ->
        {
            if (player.interactionManager.getGameMode() != mode)
            {
                player.changeGameMode(mode);
            }
        });
    }

    private static void handleModelBlockTransformsPacket(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        crusher.receive(buf, (bytes, packetByteBuf) ->
        {
            try
            {
                MapType data = (MapType) DataStorageUtils.readFromBytes(bytes);

                server.execute(() ->
                {
                    ItemStack stack = player.getEquippedStack(EquipmentSlot.MAINHAND).copy();

                    if (stack.getItem() == BBSMod.MODEL_BLOCK_ITEM)
                    {
                        TypedEntityData<BlockEntityType<?>> beComponent = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
                        NbtCompound beNbt = beComponent != null ? beComponent.copyNbtWithoutId() : new NbtCompound();

                        beNbt.put("Properties", DataStorageUtils.toNbt(data));
                        stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, TypedEntityData.create(BBSMod.MODEL_BLOCK_ENTITY, beNbt));
                    }
                    else if (stack.getItem() == BBSMod.GUN_ITEM)
                    {
                        NbtComponent customComponent = stack.get(DataComponentTypes.CUSTOM_DATA);
                        NbtCompound customNbt = customComponent != null ? customComponent.copyNbt() : new NbtCompound();

                        customNbt.put("GunData", DataStorageUtils.toNbt(data));
                        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(customNbt));
                    }

                    player.equipStack(EquipmentSlot.MAINHAND, stack);
                });
            }
            catch (Exception e)
            {}
        });
    }

    private static void handlePlayerFormPacket(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        crusher.receive(buf, (bytes, packetByteBuf) ->
        {
            Form form = null;

            try
            {
                if (DataStorageUtils.readFromBytes(bytes) instanceof MapType data)
                {
                    form = BBSMod.getForms().fromData(data);
                }
            }
            catch (Exception e)
            {}

            final Form finalForm = form;

            server.execute(() ->
            {
                Morph.getMorph(player).setForm(FormUtils.copy(finalForm));

                sendMorphToTracked(player, finalForm);
            });
        });
    }

    private static void handleManagerDataPacket(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        crusher.receive(buf, (bytes, packetByteBuf) ->
        {
            MapType data = (MapType) DataStorageUtils.readFromBytes(bytes);
            int callbackId = packetByteBuf.readInt();
            RepositoryOperation op = RepositoryOperation.values()[packetByteBuf.readInt()];
            FilmManager films = BBSMod.getFilms();

            if (op == RepositoryOperation.LOAD)
            {
                String id = data.getString("id");
                Film film = films.load(id);

                 if (film != null)
                {
                    sendManagerData(player, callbackId, op, film.toData());
                }

            }
            else if (op == RepositoryOperation.SAVE)
            {
                films.save(data.getString("id"), data.getMap("data"));
            }
            else if (op == RepositoryOperation.RENAME)
            {
                films.rename(data.getString("from"), data.getString("to"));
            }
            else if (op == RepositoryOperation.DELETE)
            {
                films.delete(data.getString("id"));
            }
            else if (op == RepositoryOperation.KEYS)
            {
                ListType list = DataStorageUtils.stringListToData(films.getKeys());

                sendManagerData(player, callbackId, op, list);
            }
            else if (op == RepositoryOperation.ADD_FOLDER)
            {
                sendManagerData(player, callbackId, op, new ByteType(films.addFolder(data.getString("folder"))));
            }
            else if (op == RepositoryOperation.RENAME_FOLDER)
            {
                sendManagerData(player, callbackId, op, new ByteType(films.renameFolder(data.getString("from"), data.getString("to"))));
            }
            else if (op == RepositoryOperation.DELETE_FOLDER)
            {
                sendManagerData(player, callbackId, op, new ByteType(films.deleteFolder(data.getString("folder"))));
            }
        });
    }

    private static void handleActionRecording(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        String filmId = buf.readString();
        int replayId = buf.readInt();
        int tick = buf.readInt();
        int countdown = buf.readInt();
        boolean recording = buf.readBoolean();
        boolean recorderOnly = buf.isReadable() && buf.readBoolean();

        server.execute(() ->
        {
            if (recording)
            {
                Film film = BBSMod.getFilms().load(filmId);

                if (film != null)
                {
                    BBSMod.getActions().startRecording(film, player, tick, 0, countdown, replayId, recorderOnly);
                }
            }
            else
            {
                ActionRecorder recorder = BBSMod.getActions().stopRecording(player, recorderOnly);

                if (recorder == null)
                {
                    return;
                }

                Clips clips = recorder.composeClips();

                /* Send recorded clips to the client */
                sendRecordedActions(player, filmId, replayId, tick, clips);
            }
        });
    }

    private static void handleToggleFilm(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        String filmId = buf.readString();
        boolean withCamera = buf.readBoolean();

        server.execute(() ->
        {
            ActionPlayer actionPlayer = BBSMod.getActions().getPlayer(filmId);

            if (actionPlayer != null)
            {
                BBSMod.getActions().stop(filmId);

                for (ServerPlayerEntity otherPlayer : server.getPlayerManager().getPlayerList())
                {
                    sendStopFilm(otherPlayer, filmId);
                }
            }
            else
            {
                sendPlayFilm(player, player.getEntityWorld(), filmId, withCamera);
            }
        });
    }

    private static void handleActionControl(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        ActionManager actions = BBSMod.getActions();
        String filmId = buf.readString();
        ActionState state = EnumUtils.getValue(buf.readByte(), ActionState.values(), ActionState.STOP);
        int tick = buf.readInt();

        server.execute(() ->
        {
            if (state == ActionState.SEEK)
            {
                ActionPlayer actionPlayer = actions.getPlayer(filmId);

                if (actionPlayer != null)
                {
                    actionPlayer.goTo(tick);
                }
            }
            else if (state == ActionState.SYNC)
            {
                ActionPlayer actionPlayer = actions.getPlayer(filmId);

                if (actionPlayer != null)
                {
                    /* Soft tick move — same as Play/Pause; no world-clip walk. */
                    actionPlayer.syncPlaybackTick(tick);
                }
            }
            else if (state == ActionState.PLAY)
            {
                ActionPlayer actionPlayer = actions.getPlayer(filmId);

                if (actionPlayer != null)
                {
                    /* Soft sync — do not rebuild combat / re-fire clips on play. */
                    actionPlayer.syncPlaybackTick(tick);
                    actionPlayer.playing = true;
                }
            }
            else if (state == ActionState.PAUSE)
            {
                ActionPlayer actionPlayer = actions.getPlayer(filmId);

                if (actionPlayer != null)
                {
                    /* Soft sync — do not revive combat-dead actors on pause. */
                    actionPlayer.syncPlaybackTick(tick);
                    actionPlayer.playing = false;
                }
            }
            else if (state == ActionState.RESTART)
            {
                ActionPlayer actionPlayer = actions.getPlayer(filmId);

                if (actionPlayer == null)
                {
                    FilmManager films = BBSMod.getFilms();
                    Film film = (filmId != null && !filmId.isBlank() && films.exists(filmId)) ? films.load(filmId) : null;

                    if (film != null)
                    {
                        actionPlayer = actions.play(player, player.getEntityWorld(), film, tick, PlayerType.FILM_EDITOR);
                    }
                }
                else
                {
                    actions.stop(filmId);

                    actionPlayer = actions.play(player, player.getEntityWorld(), actionPlayer.film, tick, PlayerType.FILM_EDITOR);
                }

                if (actionPlayer != null)
                {
                    actionPlayer.syncing = true;
                    actionPlayer.playing = false;
                    /* Always sync HP + world clips from 0..cursor (incl. tick 0). */
                    actionPlayer.goTo(0, tick);
                }

                sendStopFilm(player, filmId);
            }
            else if (state == ActionState.RESTORE)
            {
                ActionPlayer actionPlayer = actions.getPlayer(filmId);
                ServerWorld world = actionPlayer != null ? actionPlayer.getWorld() : (ServerWorld) player.getEntityWorld();

                actions.restoreDamage(world);
            }
            else if (state == ActionState.PUPPET)
            {
                ActionPlayer actionPlayer = actions.getPlayer(filmId);

                if (actionPlayer != null)
                {
                    /* tick payload is the replay index, or -1 to release. */
                    actionPlayer.controlledReplay = tick;
                }
            }
            else if (state == ActionState.STOP)
            {
                actions.stop(filmId);
            }
        });
    }

    private static void handleSyncData(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        crusher.receive(buf, (bytes, packetByteBuf) ->
        {
            String filmId = packetByteBuf.readString();
            List<String> path = new ArrayList<>();

            for (int i = 0, c = buf.readInt(); i < c; i++)
            {
                path.add(buf.readString());
            }

            BaseType data = DataStorageUtils.readFromBytes(bytes);

            server.execute(() ->
            {
                BBSMod.getActions().syncData(filmId, new DataPath(path), data);
            });
        });
    }

    private static void handleTeleportPlayer(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float yaw = buf.readFloat();
        float bodyYaw = buf.readFloat();
        float pitch = buf.readFloat();

        server.execute(() ->
        {
            player.requestTeleport(x, y, z);

            player.setYaw(yaw);
            player.setHeadYaw(yaw);
            player.setBodyYaw(bodyYaw);
            player.setPitch(pitch);
        });
    }

    private static void handleAnimationStateTriggerPacket(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        String string = buf.readString();
        int type = buf.readInt();
        PacketByteBuf newBuf = PacketByteBufs.create();

        newBuf.writeInt(player.getId());
        newBuf.writeString(string);
        newBuf.writeInt(type);

        BufPayload payload = BufPayload.from(newBuf, idFor(CLIENT_ANIMATION_STATE_TRIGGER));

        for (ServerPlayerEntity otherPlayer : PlayerLookup.tracking(player))
        {
            ServerPlayNetworking.send(otherPlayer, payload);
        }

        server.execute(() ->
        {
            /* TODO: State Triggers */
        });
    }

    private static void handleSharedFormPacket(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        crusher.receive(buf, (bytes, packetByteBuf) ->
        {
            UUID playerUuid = packetByteBuf.readUuid();
            MapType data = (MapType) DataStorageUtils.readFromBytes(bytes);

            server.execute(() ->
            {
                ServerPlayerEntity otherPlayer = server.getPlayerManager().getPlayer(playerUuid);

                if (otherPlayer != null)
                {
                    sendSharedForm(otherPlayer, data);
                }
            });
        });
    }

    private static void handleZoomPacket(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        boolean zoom = buf.readBoolean();
        ItemStack main = player.getMainHandStack();

        if (main.getItem() == BBSMod.GUN_ITEM)
        {
            GunProperties properties = GunProperties.get(main);
            String command = zoom ? properties.cmdZoomOn : properties.cmdZoomOff;

            if (!command.isEmpty())
            {
                server.getCommandManager().parseAndExecute(player.getCommandSource(), command);
            }
        }
    }

    private static void handlePauseFilmPacket(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        String filmId = buf.readString();

        ActionPlayer actionPlayer = BBSMod.getActions().getPlayer(filmId);

        if (actionPlayer != null)
        {
            actionPlayer.toggle();
        }

        for (ServerPlayerEntity playerEntity : server.getPlayerManager().getPlayerList())
        {
            sendPauseFilm(playerEntity, filmId);
        }
    }

    /* API */

    public static void sendMorph(ServerPlayerEntity player, int playerId, Form form)
    {
        crusher.send(player, CLIENT_PLAYER_FORM_PACKET, FormUtils.toData(form), (packetByteBuf) ->
        {
            packetByteBuf.writeInt(playerId);
        });
    }

    public static void sendMorphToTracked(ServerPlayerEntity player, Form form)
    {
        sendMorph(player, player.getId(), form);

        for (ServerPlayerEntity otherPlayer : PlayerLookup.tracking(player))
        {
            sendMorph(otherPlayer, player.getId(), form);
        }
    }

    public static void sendClickedModelBlock(ServerPlayerEntity player, BlockPos pos)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeBlockPos(pos);

        ServerPlayNetworking.send(player, BufPayload.from(buf, idFor(CLIENT_CLICKED_MODEL_BLOCK_PACKET)));
    }

    public static void sendClickedTriggerBlock(ServerPlayerEntity player, BlockPos pos)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeBlockPos(pos);

        ServerPlayNetworking.send(player, BufPayload.from(buf, idFor(CLIENT_CLICKED_TRIGGER_BLOCK_PACKET)));
    }

    public static void sendPlayFilm(ServerPlayerEntity player, ServerWorld world, String filmId, boolean withCamera)
    {
        try
        {
            Film film = BBSMod.getFilms().load(filmId);

            if (film != null)
            {
                BBSMod.getActions().play(player, world, film, 0);

                BaseType data = film.toData();

                crusher.send(world.getPlayers().stream().map((p) -> (PlayerEntity) p).toList(), CLIENT_PLAY_FILM_PACKET, data, (packetByteBuf) ->
                {
                    packetByteBuf.writeString(filmId);
                    packetByteBuf.writeBoolean(withCamera);
                });
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void sendPlayFilm(ServerPlayerEntity player, String filmId, boolean withCamera)
    {
        try
        {
            Film film = BBSMod.getFilms().load(filmId);

            if (film != null)
            {
                BBSMod.getActions().play(player, player.getEntityWorld(), film, 0);

                crusher.send(player, CLIENT_PLAY_FILM_PACKET, film.toData(), (packetByteBuf) ->
                {
                    packetByteBuf.writeString(filmId);
                    packetByteBuf.writeBoolean(withCamera);
                });
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void sendStopFilm(ServerPlayerEntity player, String filmId)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeString(filmId);

        ServerPlayNetworking.send(player, BufPayload.from(buf, idFor(CLIENT_STOP_FILM_PACKET)));
    }

    public static void sendManagerData(ServerPlayerEntity player, int callbackId, RepositoryOperation op, BaseType data)
    {
        crusher.send(player, CLIENT_MANAGER_DATA_PACKET, data, (packetByteBuf) ->
        {
            packetByteBuf.writeInt(callbackId);
            packetByteBuf.writeInt(op.ordinal());
        });
    }

    public static void sendRecordedActions(ServerPlayerEntity player, String filmId, int replayId, int tick, Clips clips)
    {
        crusher.send(player, CLIENT_RECORDED_ACTIONS, clips.toData(), (packetByteBuf) ->
        {
            packetByteBuf.writeString(filmId);
            packetByteBuf.writeInt(replayId);
            packetByteBuf.writeInt(tick);
        });
    }

    public static void sendMobCombatAction(ServerPlayerEntity player, int victimEntityId, int sourceEntityId, float amount, byte kind)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeInt(victimEntityId);
        buf.writeInt(sourceEntityId);
        buf.writeFloat(amount);
        buf.writeByte(kind);

        ServerPlayNetworking.send(player, BufPayload.from(buf, idFor(CLIENT_MOB_COMBAT_ACTION)));
    }

    public static void sendMobConversion(ServerPlayerEntity player, int oldEntityId, int newEntityId)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeInt(oldEntityId);
        buf.writeInt(newEntityId);

        ServerPlayNetworking.send(player, BufPayload.from(buf, idFor(CLIENT_MOB_CONVERSION)));
    }

    public static void sendHandshake(MinecraftServer server, PacketSender packetSender)
    {
        packetSender.sendPacket(BufPayload.from(createHandshakeBuf(server), idFor(CLIENT_HANDSHAKE)));
    }

    public static void sendHandshake(MinecraftServer server, ServerPlayerEntity player)
    {
        ServerPlayNetworking.send(player, BufPayload.from(createHandshakeBuf(server), idFor(CLIENT_HANDSHAKE)));
    }

    private static PacketByteBuf createHandshakeBuf(MinecraftServer server)
    {
        PacketByteBuf buf = PacketByteBufs.create();
        String id = "";

        /* No need to do that in singleplayer */
        if (server.isSingleplayer())
        {
            id = "";
        }

        buf.writeString(id);

        return buf;
    }

    public static void sendCheatsPermission(ServerPlayerEntity player, boolean cheats)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeBoolean(cheats);

        ServerPlayNetworking.send(player, BufPayload.from(buf, idFor(CLIENT_CHEATS_PERMISSION)));
    }

    public static void sendSharedForm(ServerPlayerEntity player, MapType data)
    {
        crusher.send(player, CLIENT_SHARED_FORM, data, (packetByteBuf) ->
        {});
    }

    public static void sendEntityForm(ServerPlayerEntity player, IEntityFormProvider actor)
    {
        crusher.send(player, CLIENT_ENTITY_FORM, FormUtils.toData(actor.getForm()), (packetByteBuf) ->
        {
            packetByteBuf.writeInt(actor.getEntityId());
        });
    }

    public static void sendActors(ServerPlayerEntity player, String filmId, Map<String, LivingEntity> actors)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeString(filmId);
        buf.writeInt(actors.size());

        for (Map.Entry<String, LivingEntity> entry : actors.entrySet())
        {
            buf.writeString(entry.getKey());
            buf.writeInt(entry.getValue().getId());
        }

        ServerPlayNetworking.send(player, BufPayload.from(buf, idFor(CLIENT_ACTORS)));
    }

    public static void sendGunProperties(ServerPlayerEntity player, GunProjectileEntity projectile)
    {
        PacketByteBuf buf = PacketByteBufs.create();
        GunProperties properties = projectile.getProperties();

        buf.writeInt(projectile.getEntityId());
        properties.toNetwork(buf);

        ServerPlayNetworking.send(player, BufPayload.from(buf, idFor(CLIENT_GUN_PROPERTIES)));
    }

    public static void sendPauseFilm(ServerPlayerEntity player, String filmId)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeString(filmId);

        ServerPlayNetworking.send(player, BufPayload.from(buf, idFor(CLIENT_PAUSE_FILM)));
    }

    public static void sendSelectedSlot(ServerPlayerEntity player, int slot)
    {
        player.getInventory().setSelectedSlot(slot);

        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeInt(slot);

        ServerPlayNetworking.send(player, BufPayload.from(buf, idFor(CLIENT_SELECTED_SLOT)));
    }
    
    public static void sendBay4llySkinToAll(MinecraftServer server, byte[] bytes, String playerName)
    {
        List<PlayerEntity> list = new ArrayList<>();
        for (ServerPlayerEntity p : PlayerLookup.all(server))
        {
            list.add(p);
        }
        crusher.send(list, CLIENT_BAY4LLY_SKIN, bytes, (packetByteBuf) ->
        {
            packetByteBuf.writeString(playerName);
        });
    }

    public static void sendModelBlockState(ServerPlayerEntity player, BlockPos pos, String trigger)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeBlockPos(pos);
        buf.writeString(trigger);

        ServerPlayNetworking.send(player, BufPayload.from(buf, idFor(CLIENT_ANIMATION_STATE_MODEL_BLOCK_TRIGGER)));
    }

    public static void sendReloadModelBlocks(ServerPlayerEntity player, int tickRandom)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeInt(tickRandom);

        ServerPlayNetworking.send(player, BufPayload.from(buf, idFor(CLIENT_REFRESH_MODEL_BLOCKS)));
    }
}
