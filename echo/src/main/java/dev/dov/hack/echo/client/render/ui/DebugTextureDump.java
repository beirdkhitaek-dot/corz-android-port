package dev.dov.hack.echo.client.render.ui;

import dev.dov.hack.echo.client.gfx.opengl.OpenGlTextureView;
import dev.dov.hack.echo.client.gfx.texture.TextureView;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

final class DebugTextureDump {
    private DebugTextureDump() {}

    static void dump(TextureView view, String name) {
        int width = view.width(0);
        int height = view.height(0);
        int id = ((OpenGlTextureView) view).textureId;
        int previousFramebuffer = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
        int previousTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, 0);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, id);
        GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, 1);
        ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
        try {
            GL11C.glGetTexImage(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixels);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            long r = 0, g = 0, b = 0, a = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int i = (y * width + x) * 4;
                    int red = pixels.get(i) & 0xFF;
                    int green = pixels.get(i + 1) & 0xFF;
                    int blue = pixels.get(i + 2) & 0xFF;
                    int alpha = pixels.get(i + 3) & 0xFF;
                    r += red; g += green; b += blue; a += alpha;
                    image.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
                }
            }
            long count = (long) width * height;
            System.out.println("[ECHODEBUG] " + name + " " + width + "x" + height + " glId=" + id
                    + " avg=(" + (r / count) + "," + (g / count) + "," + (b / count) + "," + (a / count) + ")"
                    + " corners=" + corner(pixels, width, height, 0, 0) + " " + corner(pixels, width, height, width - 1, 0)
                    + " " + corner(pixels, width, height, 0, height - 1) + " " + corner(pixels, width, height, width - 1, height - 1));
            ImageIO.write(image, "png", new File(System.getProperty("java.io.tmpdir"), "echo-" + name + ".png"));
        } catch (Exception failure) {
            System.out.println("[ECHODEBUG] dump failed " + name + ": " + failure);
        } finally {
            MemoryUtil.memFree((java.nio.Buffer) pixels);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previousTexture);
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, previousFramebuffer);
        }
    }

    private static String corner(ByteBuffer pixels, int width, int height, int x, int y) {
        int i = (y * width + x) * 4;
        return "(" + (pixels.get(i) & 0xFF) + "," + (pixels.get(i + 1) & 0xFF) + "," + (pixels.get(i + 2) & 0xFF) + ")";
    }
}
