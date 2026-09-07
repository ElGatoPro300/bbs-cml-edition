package mchorse.bbs_mod.film;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.skin.SkinManager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.ErrorReporter;

import com.mojang.authlib.GameProfile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class PlayerCaptureForms
{
    private static final Executor EXECUTOR = Executors.newCachedThreadPool((r) ->
    {
        Thread thread = new Thread(r, "BBS-PlayerCaptureSkin");

        thread.setDaemon(true);

        return thread;
    });

    private PlayerCaptureForms()
    {}

    public static Form create(PlayerEntity target, boolean modelForm)
    {
        if (target == null)
        {
            return null;
        }

        return modelForm ? createModelForm(target) : createMobForm(target);
    }

    public static boolean isSlim(PlayerEntity target)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (target == null || mc.getNetworkHandler() == null)
        {
            return false;
        }

        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(target.getUuid());

        return entry != null && entry.getSkinTextures().model() == PlayerSkinType.SLIM;
    }

    private static MobForm createMobForm(PlayerEntity target)
    {
        MobForm form = new MobForm();
        NbtWriteView view = NbtWriteView.create(ErrorReporter.EMPTY);

        target.saveSelfData(view);

        NbtCompound compound = view.getNbt();

        for (String key : Arrays.asList(
            "Pos", "Motion", "Rotation", "FallDistance", "Fire", "Air", "OnGround",
            "Invulnerable", "PortalCooldown", "UUID",
            "HurtTime", "HurtByTimestamp", "DeathTime", "AbsorptionAmount",
            "FallFlying", "Brain", "Attributes", "ActiveEffects", "Passengers",
            "SleepingX", "SleepingY", "SleepingZ"
        ))
        {
            compound.remove(key);
        }

        GameProfile profile = target.getGameProfile();

        form.mobID.set("minecraft:player");
        form.mobNBT.set(compound.toString());
        form.playerName.set(profile.name() == null ? "" : profile.name());
        form.playerUuid.set(profile.id() == null ? "" : profile.id().toString());
        form.slim.set(isSlim(target));

        return form;
    }

    private static ModelForm createModelForm(PlayerEntity target)
    {
        ModelForm form = new ModelForm();
        boolean slim = isSlim(target);

        form.model.set(slim ? "player/alex" : "player/steve");
        applyExistingSkin(target.getGameProfile(), form);

        return form;
    }

    private static void applyExistingSkin(GameProfile profile, ModelForm form)
    {
        Link link = getSkinLink(profile);

        if (link != null)
        {
            form.texture.set(link);

            return;
        }

        downloadSkinAsync(profile, form);
    }

    private static Link getSkinLink(GameProfile profile)
    {
        if (profile == null || profile.name() == null || profile.name().isEmpty())
        {
            return null;
        }

        File file = SkinManager.getSkinFile(profile.name());

        if (!file.isFile())
        {
            return null;
        }

        return BBSMod.getProvider().getLink(file);
    }

    private static void downloadSkinAsync(GameProfile profile, ModelForm form)
    {
        if (profile == null || profile.id() == null || profile.name() == null || profile.name().isEmpty())
        {
            return;
        }

        CompletableFuture
            .supplyAsync(() ->
            {
                try
                {
                    return downloadSkin(profile);
                }
                catch (Exception e)
                {
                    return null;
                }
            }, EXECUTOR)
            .thenAccept((file) ->
            {
                if (file == null)
                {
                    return;
                }

                MinecraftClient.getInstance().execute(() ->
                {
                    Link link = BBSMod.getProvider().getLink(file);

                    if (link != null)
                    {
                        form.texture.set(link);
                    }
                });
            });
    }

    private static File downloadSkin(GameProfile profile) throws Exception
    {
        String skinUrl = getSkinUrl(profile.id());
        URL url = new URL(skinUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(15000);

        File tempFolder = new File("tmp_skins");

        if (!tempFolder.exists())
        {
            tempFolder.mkdirs();
        }

        File tempFile = new File(tempFolder, profile.name() + ".png");

        Files.copy(connection.getInputStream(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        SkinManager.saveSkin(profile.name(), tempFile);

        return SkinManager.getSkinFile(profile.name());
    }

    private static String getSkinUrl(UUID uuid) throws Exception
    {
        URL profileUrl = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", ""));
        HttpURLConnection connection = (HttpURLConnection) profileUrl.openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream())))
        {
            StringBuilder builder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null)
            {
                builder.append(line);
            }

            JsonObject profileJson = JsonParser.parseString(builder.toString()).getAsJsonObject();
            String encoded = profileJson.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
            String decoded = new String(Base64.getDecoder().decode(encoded));
            JsonObject texturesJson = JsonParser.parseString(decoded).getAsJsonObject();

            return texturesJson.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
        }
    }
}
