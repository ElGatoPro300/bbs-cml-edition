package mchorse.bbs_mod.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

public class TextureFont
{
    private NativeImageBackedTexture texture;
    private Identifier textureId;
    private final Map<Character, Glyph> glyphs = new HashMap<>();
    private int height;
    private boolean initialized = false;
    private static final int[] COLORS = new int[32];

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

            COLORS[i] = (k & 255) << 16 | (l & 255) << 8 | (m & 255);
        }
    }

    public TextureFont(File fontFile, int style)
    {
        try
        {
            Font font = Font.createFont(Font.TRUETYPE_FONT, fontFile).deriveFont(style, 64f); /* High res for quality */
            generateTexture(font);
            this.initialized = true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public boolean isInitialized()
    {
        return initialized;
    }

    private void generateTexture(Font font) throws IOException
    {
        int imgSize = 2048; /* Increase size for more chars/quality */
        BufferedImage image = new BufferedImage(imgSize, imgSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        
        g2d.setFont(font);
        g2d.setColor(Color.WHITE);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        
        FontMetrics metrics = g2d.getFontMetrics();
        this.height = metrics.getHeight();
        
        int x = 0;
        int y = metrics.getAscent();
        
        /* Support Latin-1 Supplement */
        for (int i = 32; i < 256; i++)
        {
            char c = (char) i;
            if (!font.canDisplay(c)) continue;

            int w = metrics.charWidth(c);
            
            if (x + w >= imgSize)
            {
                x = 0;
                y += metrics.getHeight();
            }
            
            g2d.drawString(String.valueOf(c), x, y);
            
            /* Store glyph info */
            this.glyphs.put(c, new Glyph(x, y - metrics.getAscent(), w, metrics.getHeight(), imgSize));
            
            x += w + 4;
        }
        
        g2d.dispose();
        
        /* Upload to texture */
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(baos.toByteArray()));
        
        RenderSystem.recordRenderCall(() -> {
            this.texture = new NativeImageBackedTexture(nativeImage);
            String name = "bbs_font_" + font.hashCode();
            this.textureId = Identifier.of("bbs_mod", name.toLowerCase());
            MinecraftClient.getInstance().getTextureManager().registerTexture(this.textureId, this.texture);
        });
    }

    public int getWidth(String text)
    {
        return this.getWidth(text, 0);
    }

    public int getWidth(String text, float letterSpacing)
    {
        float w = 0;
        float scale = 0.25f;
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            
            if (c == '\u00A7' && i + 1 < text.length())
            {
                i++;
                continue;
            }

            Glyph g = glyphs.get(c);
            if (g != null) w += g.width * scale + letterSpacing;
        }
        return (int) w;
    }

    public List<String> wrap(String text, int width)
    {
        return this.wrap(text, width, 0);
    }

    public List<String> wrap(String text, int width, float letterSpacing)
    {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words)
        {
            String testLine = currentLine.length() > 0 ? currentLine + " " + word : word;
            if (this.getWidth(testLine, letterSpacing) <= width)
            {
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            }
            else
            {
                if (currentLine.length() > 0)
                {
                    lines.add(currentLine.toString());
                }
                currentLine = new StringBuilder(word);
            }
        }
        
        if (currentLine.length() > 0)
        {
            lines.add(currentLine.toString());
        }
        
        return lines;
    }

    public int getHeight()
    {
        return (int) (this.height * 0.25f);
    }

    public void draw(String text, float x, float y, int color, Matrix4f matrix, VertexConsumerProvider consumers, int light)
    {
        this.draw(text, x, y, color, color, 0, 0, matrix, consumers, light);
    }

    public void draw(String text, float x, float y, int color, int color2, float letterSpacing, float spaceWidth, Matrix4f matrix, VertexConsumerProvider consumers, int light)
    {
        this.draw(text, x, y, color, color2, letterSpacing, spaceWidth, matrix, consumers, light, 0.5F);
    }

    public void draw(String text, float x, float y, int color, int color2, float letterSpacing, float spaceWidth, Matrix4f matrix, VertexConsumerProvider consumers, int light, float gradientOffset)
    {
        if (this.textureId == null) return;

        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getText(this.textureId));
        float scale = 0.25f; /* Scale down because we generated at 64px */
        
        float r1 = (color >> 16 & 255) / 255.0F;
        float g1 = (color >> 8 & 255) / 255.0F;
        float b1 = (color & 255) / 255.0F;
        float a1 = (color >> 24 & 255) / 255.0F;

        float r2 = (color2 >> 16 & 255) / 255.0F;
        float g2 = (color2 >> 8 & 255) / 255.0F;
        float b2 = (color2 & 255) / 255.0F;
        float a2 = (color2 >> 24 & 255) / 255.0F;

        float cr1 = r1;
        float cg1 = g1;
        float cb1 = b1;

        float cr2 = r2;
        float cg2 = g2;
        float cb2 = b2;

        float cx = x;
        
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            
            if ((c == '\u00A7' || c == '&') && i + 1 < text.length())
            {
                int codeIndex = "0123456789abcdef".indexOf(Character.toLowerCase(text.charAt(i + 1)));
                if (codeIndex >= 0)
                {
                    int colorCode = COLORS[codeIndex];
                    float cr = (colorCode >> 16 & 255) / 255.0F;
                    float cg = (colorCode >> 8 & 255) / 255.0F;
                    float cb = (colorCode & 255) / 255.0F;
                    
                    /* Apply original tint if not white */
                    cr1 = cr * r1;
                    cg1 = cg * g1;
                    cb1 = cb * b1;
                    
                    cr2 = cr * r2;
                    cg2 = cg * g2;
                    cb2 = cb * b2;
                }
                else if (Character.toLowerCase(text.charAt(i + 1)) == 'r')
                {
                    cr1 = r1;
                    cg1 = g1;
                    cb1 = b1;
                    
                    cr2 = r2;
                    cg2 = g2;
                    cb2 = b2;
                }
                
                i++;
                continue;
            }

            Glyph glyph = glyphs.get(c);
            if (glyph == null) continue;
            
            float gw = glyph.width * scale;
            float gh = glyph.height * scale;
            
            /* Draw quad */
            if (Math.abs(gradientOffset - 0.5F) < 0.01F)
            {
                drawVertex(consumer, matrix, cx, y + gh, 0, glyph.u, glyph.v + glyph.vh, cr2, cg2, cb2, a2, light);
                drawVertex(consumer, matrix, cx + gw, y + gh, 0, glyph.u + glyph.uw, glyph.v + glyph.vh, cr2, cg2, cb2, a2, light);
                drawVertex(consumer, matrix, cx + gw, y, 0, glyph.u + glyph.uw, glyph.v, cr1, cg1, cb1, a1, light);
                drawVertex(consumer, matrix, cx, y, 0, glyph.u, glyph.v, cr1, cg1, cb1, a1, light);
            }
            else
            {
                float offset = Math.max(0.01F, Math.min(0.99F, gradientOffset));
                float mr = (cr1 + cr2) / 2F;
                float mg = (cg1 + cg2) / 2F;
                float mb = (cb1 + cb2) / 2F;
                float ma = (a1 + a2) / 2F;

                float splitY = y + gh * offset;
                float splitV = glyph.v + glyph.vh * offset;

                /* Top Quad */
                drawVertex(consumer, matrix, cx, splitY, 0, glyph.u, splitV, mr, mg, mb, ma, light);
                drawVertex(consumer, matrix, cx + gw, splitY, 0, glyph.u + glyph.uw, splitV, mr, mg, mb, ma, light);
                drawVertex(consumer, matrix, cx + gw, y, 0, glyph.u + glyph.uw, glyph.v, cr1, cg1, cb1, a1, light);
                drawVertex(consumer, matrix, cx, y, 0, glyph.u, glyph.v, cr1, cg1, cb1, a1, light);

                /* Bottom Quad */
                drawVertex(consumer, matrix, cx, y + gh, 0, glyph.u, glyph.v + glyph.vh, cr2, cg2, cb2, a2, light);
                drawVertex(consumer, matrix, cx + gw, y + gh, 0, glyph.u + glyph.uw, glyph.v + glyph.vh, cr2, cg2, cb2, a2, light);
                drawVertex(consumer, matrix, cx + gw, splitY, 0, glyph.u + glyph.uw, splitV, mr, mg, mb, ma, light);
                drawVertex(consumer, matrix, cx, splitY, 0, glyph.u, splitV, mr, mg, mb, ma, light);
            }
            
            cx += gw + letterSpacing;
            if (c == ' ') cx += spaceWidth;
        }
    }

    private void drawVertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float u, float v, float r, float g, float b, float a, int light)
    {
        consumer.vertex(matrix, x, y, z).color(r, g, b, a).texture(u, v).light(light);
    }

    private static class Glyph
    {
        float u, v, uw, vh;
        int width, height;

        public Glyph(int x, int y, int w, int h, int imgSize)
        {
            this.width = w;
            this.height = h;
            this.u = (float) x / imgSize;
            this.v = (float) y / imgSize;
            this.uw = (float) w / imgSize;
            this.vh = (float) h / imgSize;
        }
    }
}
