package mchorse.bbs_mod.text;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.CustomFontManager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.imageio.ImageIO;

/**
 * Renders Persian/Arabic UI text with Java2D {@link TextLayout}, which applies
 * glyph shaping and kerning. Minecraft's {@code TextRenderer} cannot connect
 * Arabic-script letters because it advances every code point separately.
 */
public final class RtlAwtTextRenderer
{
    private static final Link RTL_FONT = Link.assets("fonts/vazirmatn-regular.ttf");

    private static final String CLASSPATH_FONT = "/assets/bbs/assets/fonts/vazirmatn-regular.ttf";

    private static final int CACHE_LIMIT = 512;

    private static final int[] FORMAT_COLORS = new int[32];

    private static final Map<CacheKey, CachedText> CACHE = new LinkedHashMap<>(CACHE_LIMIT, 0.75F, true)
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, CachedText> eldest)
        {
            return this.size() > CACHE_LIMIT;
        }
    };

    private static Font baseFont;

    private static FontRenderContext fontContext;

    private static float loadedFontSize = -1F;

    private static int textureSerial;

    private static boolean loadAttempted;

    static
    {
        for (int i = 0; i < 16; ++i)
        {
            int j = (i >> 3 & 1) * 85;
            int k = (i >> 2 & 1) * 170 + j;
            int l = (i >> 1 & 1) * 170 + j;
            int m = (i & 1) * 170 + j;

            if (i == 6)
            {
                k += 85;
            }

            if (i >= 16)
            {
                k /= 4;
                l /= 4;
                m /= 4;
            }

            FORMAT_COLORS[i] = (k & 255) << 16 | (l & 255) << 8 | (m & 255);
        }
    }

    private RtlAwtTextRenderer()
    {}

    public static boolean isReady()
    {
        ensureFont();

        return baseFont != null && fontContext != null;
    }

    public static void clearCache()
    {
        for (CachedText cached : CACHE.values())
        {
            cached.close();
        }

        CACHE.clear();
    }

    public static void invalidate()
    {
        clearCache();
        baseFont = null;
        fontContext = null;
        loadedFontSize = -1F;
        loadAttempted = false;
    }

    public static void loadFont(byte[] bytes)
    {
        if (bytes == null || bytes.length == 0)
        {
            return;
        }

        float size = getFontSize();

        try
        {
            Font font = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(bytes)).deriveFont(Font.PLAIN, size);

            baseFont = font;
            fontContext = new FontRenderContext(null, true, true);
            loadedFontSize = size;
            loadAttempted = true;
            clearCache();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void ensureFont()
    {
        float size = getFontSize();

        if (baseFont != null && loadedFontSize == size)
        {
            return;
        }

        if (baseFont != null)
        {
            clearCache();
            baseFont = null;
            fontContext = null;
            loadAttempted = false;
        }

        if (loadAttempted)
        {
            return;
        }

        loadAttempted = true;

        try (InputStream stream = openFontStream())
        {
            if (stream != null)
            {
                loadFont(stream.readAllBytes());
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private static InputStream openFontStream()
    {
        try
        {
            InputStream stream = BBSMod.getProvider().getAsset(RTL_FONT);

            if (stream != null)
            {
                return stream;
            }
        }
        catch (Exception e)
        {}

        InputStream stream = RtlAwtTextRenderer.class.getResourceAsStream(CLASSPATH_FONT);

        if (stream != null)
        {
            return stream;
        }

        return RtlAwtTextRenderer.class.getClassLoader().getResourceAsStream(CLASSPATH_FONT.substring(1));
    }

    public static int getWidth(String text)
    {
        if (!isReady() || text == null || text.isEmpty())
        {
            return 0;
        }

        return measure(text).width;
    }

    public static int getHeight()
    {
        if (!isReady())
        {
            return 9;
        }

        return Math.max(1, Math.round(baseFont.getLineMetrics("\u0633", fontContext).getAscent() + baseFont.getLineMetrics("\u0633", fontContext).getDescent()));
    }

    public static List<String> wrap(String text, int maxWidth)
    {
        List<String> lines = new ArrayList<>();

        if (text == null || text.isEmpty())
        {
            lines.add("");

            return lines;
        }

        if (!isReady() || maxWidth <= 0)
        {
            lines.add(text);

            return lines;
        }

        String[] parts = text.split("\n", -1);

        for (String part : parts)
        {
            if (part.isEmpty())
            {
                lines.add("");

                continue;
            }

            StringBuilder current = new StringBuilder();

            for (String word : part.split(" ", -1))
            {
                if (current.length() == 0)
                {
                    if (getWidth(word) <= maxWidth)
                    {
                        current.append(word);
                    }
                    else
                    {
                        lines.addAll(wrapLongToken(word, maxWidth));
                    }
                }
                else
                {
                    String candidate = current + " " + word;

                    if (getWidth(candidate) <= maxWidth)
                    {
                        current.append(' ').append(word);
                    }
                    else
                    {
                        lines.add(current.toString());
                        current = new StringBuilder();

                        if (getWidth(word) <= maxWidth)
                        {
                            current.append(word);
                        }
                        else
                        {
                            lines.addAll(wrapLongToken(word, maxWidth));
                        }
                    }
                }
            }

            if (current.length() > 0)
            {
                lines.add(current.toString());
            }
        }

        return lines;
    }

    public static boolean draw(Batcher2D batcher, String text, float x, float y, int color, boolean shadow)
    {
        if (!RtlTextEngine.isActive() || text == null || text.isEmpty())
        {
            return false;
        }

        if (!isReady())
        {
            return false;
        }

        CachedText cached = getCached(text, color);

        if (cached == null)
        {
            return false;
        }

        RenderSystem.enableBlend();

        int glId = MinecraftClient.getInstance().getTextureManager().getTexture(cached.textureId).getGlId();

        RenderSystem.bindTexture(glId);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        if (shadow)
        {
            batcher.texturedBox(glId, 0xA0000000, x + 1F, y + 1F, cached.displayWidth, cached.displayHeight, 0F, 0F, cached.textureWidth, cached.textureHeight, cached.textureWidth, cached.textureHeight);
        }

        batcher.texturedBox(glId, 0xFFFFFFFF, x, y, cached.displayWidth, cached.displayHeight, 0F, 0F, cached.textureWidth, cached.textureHeight, cached.textureWidth, cached.textureHeight);
        batcher.flush();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);

        return true;
    }

    private static List<String> wrapLongToken(String token, int maxWidth)
    {
        List<String> lines = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();

        for (int i = 0; i < token.length();)
        {
            int cp = token.codePointAt(i);
            chunk.appendCodePoint(cp);
            i += Character.charCount(cp);

            if (getWidth(chunk.toString()) > maxWidth)
            {
                if (chunk.length() > Character.charCount(cp))
                {
                    String built = chunk.toString();
                    int cut = built.offsetByCodePoints(built.length(), -1);

                    lines.add(built.substring(0, cut));
                    chunk = new StringBuilder(built.substring(cut));
                }
                else
                {
                    lines.add(chunk.toString());
                    chunk.setLength(0);
                }
            }
        }

        if (chunk.length() > 0)
        {
            lines.add(chunk.toString());
        }

        return lines;
    }

    private static Measure measure(String text)
    {
        TextLayout layout = createLayout(text, colorFromArgb(0xFFFFFFFF));

        return new Measure(Math.max(1, (int) Math.ceil(layout.getAdvance())), Math.max(1, (int) Math.ceil(layout.getAscent() + layout.getDescent())));
    }

    private static CachedText getCached(String text, int color)
    {
        CacheKey key = new CacheKey(text, color, loadedFontSize, getPixelScale());

        CachedText cached = CACHE.get(key);

        if (cached != null)
        {
            return cached;
        }

        cached = bake(text, color);

        if (cached != null)
        {
            CACHE.put(key, cached);
        }

        return cached;
    }

    private static CachedText bake(String text, int color)
    {
        try
        {
            Color defaultColor = colorFromArgb(color);
            TextLayout layout = createLayout(text, defaultColor);
            float pixelScale = getPixelScale();
            int displayWidth = Math.max(1, (int) Math.ceil(layout.getAdvance()));
            int displayHeight = Math.max(1, (int) Math.ceil(layout.getAscent() + layout.getDescent()));
            int textureWidth = Math.max(1, Math.round(displayWidth * pixelScale));
            int textureHeight = Math.max(1, Math.round(displayHeight * pixelScale));

            BufferedImage image = new BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();

            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            graphics.scale(pixelScale, pixelScale);

            layout.draw(graphics, 0F, layout.getAscent());
            graphics.dispose();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(output.toByteArray()));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);
            Identifier id = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("bbs_rtl_text_" + textureSerial++, texture);

            return new CachedText(id, displayWidth, displayHeight, textureWidth, textureHeight, texture);
        }
        catch (Exception e)
        {
            e.printStackTrace();

            return null;
        }
    }

    private static TextLayout createLayout(String text, Color defaultColor)
    {
        AttributedString attributed = buildAttributedString(text, defaultColor);

        return new TextLayout(attributed.getIterator(), fontContext);
    }

    private static AttributedString buildAttributedString(String text, Color defaultColor)
    {
        StringBuilder plain = new StringBuilder(text.length());
        List<ColorRange> ranges = new ArrayList<>();
        Color current = defaultColor;
        int rangeStart = 0;

        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);

            if (c == '\u00A7' && i + 1 < text.length())
            {
                if (plain.length() > rangeStart)
                {
                    ranges.add(new ColorRange(rangeStart, plain.length(), current));
                }

                char code = Character.toLowerCase(text.charAt(i + 1));

                if (code == 'r')
                {
                    current = defaultColor;
                }
                else
                {
                    int index = "0123456789abcdef".indexOf(code);

                    if (index >= 0)
                    {
                        current = colorFromRgb(FORMAT_COLORS[index], defaultColor.getAlpha());
                    }
                }

                rangeStart = plain.length();
                i += 1;

                continue;
            }

            plain.append(c);
        }

        if (plain.length() > rangeStart)
        {
            ranges.add(new ColorRange(rangeStart, plain.length(), current));
        }

        String value = plain.toString();
        AttributedString attributed = new AttributedString(value.isEmpty() ? " " : value);

        attributed.addAttribute(TextAttribute.FONT, baseFont);

        /* Mixed Persian/English strings often start with Latin tokens (e.g. "ffmpeg ...").
         * Without an explicit RTL paragraph direction, Java2D picks LTR from the first
         * strong character and the Arabic-script portion renders in the wrong visual order. */
        if (RtlTextEngine.isActive() && ArabicShaper.containsArabicScript(value))
        {
            attributed.addAttribute(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_RTL);
        }

        for (ColorRange range : ranges)
        {
            if (range.start < range.end)
            {
                attributed.addAttribute(TextAttribute.FOREGROUND, range.color, range.start, range.end);
            }
        }

        return attributed;
    }

    private static Color colorFromArgb(int argb)
    {
        int alpha = argb >> 24 & 0xFF;

        if (alpha == 0)
        {
            alpha = 255;
        }

        return new Color(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, alpha);
    }

    private static Color colorFromRgb(int rgb, int alpha)
    {
        return new Color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, alpha == 0 ? 255 : alpha);
    }

    private static float getFontSize()
    {
        return 11F * CustomFontManager.getFontScale();
    }

    /**
     * Rasterize text at a higher resolution than the logical UI size so it stays sharp when
     * Minecraft scales the GUI (UI scale 2, HiDPI, etc.).
     */
    private static float getPixelScale()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null)
        {
            return 1F;
        }

        float ui = (float) BBSModClient.getUIScaleFactor();

        if (ui <= 0F)
        {
            ui = BBSModClient.getGUIScale();
        }

        float window = (float) mc.getWindow().getScaleFactor();

        return Math.max(1F, Math.max(ui, window));
    }

    private static final class ColorRange
    {
        private final int start;

        private final int end;

        private final Color color;

        private ColorRange(int start, int end, Color color)
        {
            this.start = start;
            this.end = end;
            this.color = color;
        }
    }

    private static final class Measure
    {
        private final int width;

        private final int height;

        private Measure(int width, int height)
        {
            this.width = width;
            this.height = height;
        }
    }

    private static final class CacheKey
    {
        private final String text;

        private final int color;

        private final float fontSize;

        private final float pixelScale;

        private CacheKey(String text, int color, float fontSize, float pixelScale)
        {
            this.text = text;
            this.color = color;
            this.fontSize = fontSize;
            this.pixelScale = pixelScale;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
            {
                return true;
            }

            if (!(obj instanceof CacheKey other))
            {
                return false;
            }

            return this.color == other.color && Float.compare(this.fontSize, other.fontSize) == 0 && Float.compare(this.pixelScale, other.pixelScale) == 0 && Objects.equals(this.text, other.text);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(this.text, this.color, this.fontSize, this.pixelScale);
        }
    }

    private static final class CachedText
    {
        private final Identifier textureId;

        private final int displayWidth;

        private final int displayHeight;

        private final int textureWidth;

        private final int textureHeight;

        private final NativeImageBackedTexture texture;

        private CachedText(Identifier id, int displayWidth, int displayHeight, int textureWidth, int textureHeight, NativeImageBackedTexture texture)
        {
            this.textureId = id;
            this.displayWidth = displayWidth;
            this.displayHeight = displayHeight;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.texture = texture;
        }

        private void close()
        {
            try
            {
                this.texture.close();
            }
            catch (Exception e)
            {}
        }
    }
}
