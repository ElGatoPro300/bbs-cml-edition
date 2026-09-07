package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.utils.OS;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.swing.JFileChooser;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

public class UIUtils
{
    private static final AtomicBoolean AWT_READY = new AtomicBoolean(false);

    /**
     * Open web link (in default web browser)
     */
    public static boolean openWebLink(String address)
    {
        if (OS.CURRENT == OS.WINDOWS)
        {
            return runSysCommand("rundll32", "url.dll,FileProtocolHandler", address);
        }
        else if (OS.CURRENT == OS.MACOS)
        {
            return runSysCommand("open", address);
        }

        return runSysCommand("kde-open", address)
            || runSysCommand("gnome-open", address)
            || runSysCommand("xdg-open", address);
    }

    /**
     * Open a folder (in default file browser)
     */
    public static boolean openFolder(File folder)
    {
        try
        {
            String path = folder.getAbsolutePath();

            if (OS.CURRENT == OS.WINDOWS)
            {
                return runSysCommand("explorer", path);
            }
            else if (OS.CURRENT == OS.MACOS)
            {
                return runSysCommand("open", path);
            }

            return runSysCommand("kde-open", path)
                || runSysCommand("gnome-open", path)
                || runSysCommand("xdg-open", path);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Native OS "Open" dialog. On Windows prefers the real WinForms picker (same look as
     * Explorer). Falls back to Swing / AWT. Runs off the render thread; {@code onPicked}
     * runs on the Minecraft client thread.
     */
    public static void pickOpenFile(String title, String windowsFilter, String[] extensions, Consumer<File> onPicked)
    {
        if (onPicked == null)
        {
            return;
        }

        String dialogTitle = title == null || title.isEmpty() ? "Open" : title;
        String[] exts = extensions == null ? new String[0] : extensions;

        Thread thread = new Thread(() ->
        {
            File selected = null;

            try
            {
                /* Windows Forms first (real Explorer dialog, appears above Minecraft). */
                if (OS.CURRENT == OS.WINDOWS)
                {
                    selected = pickOpenFileWindowsForms(dialogTitle, exts);
                }

                if (selected == null || !selected.isFile())
                {
                    selected = pickOpenFileSwing(dialogTitle, exts);
                }

                if (selected == null || !selected.isFile())
                {
                    selected = pickOpenFileAwt(dialogTitle, windowsFilter, exts);
                }
            }
            catch (Throwable t)
            {
                t.printStackTrace();
            }

            if (selected == null || !selected.isFile())
            {
                return;
            }

            File picked = selected;
            MinecraftClient client = MinecraftClient.getInstance();

            if (client != null)
            {
                client.execute(() -> onPicked.accept(picked));
            }
            else
            {
                onPicked.accept(picked);
            }
        }, "bbs-native-file-dialog");

        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Real Windows OpenFileDialog via PowerShell STA — matches Explorer UI.
     */
    private static File pickOpenFileWindowsForms(String title, String[] extensions)
    {
        String filter = buildWindowsFormsFilter(extensions);
        String safeTitle = escapePowerShellSingleQuoted(title);
        String safeFilter = escapePowerShellSingleQuoted(filter);
        String script =
            "Add-Type -AssemblyName System.Windows.Forms; "
                + "$d = New-Object System.Windows.Forms.OpenFileDialog; "
                + "$d.Title = '" + safeTitle + "'; "
                + "$d.Filter = '" + safeFilter + "'; "
                + "$d.FilterIndex = 1; "
                + "$d.Multiselect = $false; "
                + "$d.CheckFileExists = $true; "
                + "if ($d.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) { "
                + "[Console]::Out.Write($d.FileName) "
                + "}";

        try
        {
            ProcessBuilder builder = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy", "Bypass",
                "-STA",
                "-Command",
                script
            );
            builder.redirectErrorStream(true);
            Process process = builder.start();
            StringBuilder out = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
            {
                String line;

                while ((line = reader.readLine()) != null)
                {
                    if (out.length() > 0)
                    {
                        out.append('\n');
                    }

                    out.append(line);
                }
            }

            int code = process.waitFor();
            String path = out.toString().trim();

            if (code == 0 && !path.isEmpty())
            {
                File file = new File(path);

                if (file.isFile())
                {
                    return file;
                }
            }
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }

        return null;
    }

    private static String buildWindowsFormsFilter(String[] extensions)
    {
        if (extensions == null || extensions.length == 0)
        {
            return "All files (*.*)|*.*";
        }

        StringBuilder patterns = new StringBuilder();
        StringBuilder label = new StringBuilder("Video (");

        for (int i = 0; i < extensions.length; i++)
        {
            String extension = extensions[i] == null ? "" : extensions[i].toLowerCase(Locale.ROOT);

            if (extension.isEmpty())
            {
                continue;
            }

            if (patterns.length() > 0)
            {
                patterns.append(';');
                label.append(", ");
            }

            patterns.append("*.").append(extension);
            label.append(extension);
        }

        label.append(')');

        return label + "|" + patterns + "|MP4 (*.mp4)|*.mp4|All files (*.*)|*.*";
    }

    private static String escapePowerShellSingleQuoted(String value)
    {
        if (value == null)
        {
            return "";
        }

        return value.replace("'", "''");
    }

    private static File pickOpenFileSwing(String title, String[] extensions)
    {
        ensureAwt();

        try
        {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Throwable ignored)
        {}

        Frame owner = null;

        try
        {
            owner = new Frame();
            owner.setAlwaysOnTop(true);
            owner.setLocationRelativeTo(null);
            owner.setVisible(true);

            JFileChooser chooser = new JFileChooser();

            chooser.setDialogTitle(title);
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setMultiSelectionEnabled(false);
            chooser.setAcceptAllFileFilterUsed(true);

            File videoDir = new File(BBSMod.getAssetsFolder(), "video");

            if (videoDir.isDirectory())
            {
                chooser.setCurrentDirectory(videoDir.getParentFile());
            }

            if (extensions != null && extensions.length > 0)
            {
                chooser.setFileFilter(new FileNameExtensionFilter("Video files", extensions));
            }

            int result = chooser.showOpenDialog(owner);

            if (result == JFileChooser.APPROVE_OPTION)
            {
                return chooser.getSelectedFile();
            }
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }
        finally
        {
            if (owner != null)
            {
                owner.dispose();
            }
        }

        return null;
    }

    private static File pickOpenFileAwt(String title, String windowsFilter, String[] extensions)
    {
        ensureAwt();

        try
        {
            FileDialog dialog = new FileDialog((Frame) null, title, FileDialog.LOAD);

            if (windowsFilter != null && !windowsFilter.isEmpty())
            {
                dialog.setFile(windowsFilter);
            }

            if (extensions != null && extensions.length > 0)
            {
                dialog.setFilenameFilter((dir, name) ->
                {
                    if (name == null)
                    {
                        return false;
                    }

                    String lower = name.toLowerCase(Locale.ROOT);

                    for (String extension : extensions)
                    {
                        if (extension != null && lower.endsWith("." + extension.toLowerCase(Locale.ROOT)))
                        {
                            return true;
                        }
                    }

                    return false;
                });
            }

            dialog.setMultipleMode(false);
            dialog.setVisible(true);

            String file = dialog.getFile();
            String directory = dialog.getDirectory();

            if (file != null && directory != null)
            {
                return new File(directory, file);
            }
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }

        return null;
    }

    private static void ensureAwt()
    {
        if (AWT_READY.get())
        {
            return;
        }

        try
        {
            System.setProperty("java.awt.headless", "false");
            Toolkit.getDefaultToolkit();
            AWT_READY.set(true);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }
    }

    private static boolean runSysCommand(String... command)
    {
        try
        {
            Process p = Runtime.getRuntime().exec(command);

            if (p == null)
            {
                return false;
            }

            try
            {
                return p.exitValue() == 0;
            }
            catch (IllegalThreadStateException e)
            {
                return true;
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();

            return false;
        }
    }

    public static void playClick()
    {
        playClick(1F);
    }

    public static void playClick(float pitch)
    {
        if (BBSSettings.clickSound.get())
        {
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(BBSMod.CLICK, pitch));
        }
        else
        {
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }
}
