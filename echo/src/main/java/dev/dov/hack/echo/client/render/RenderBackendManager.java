package dev.dov.hack.echo.client.render;

import dev.dov.hack.echo.client.EchoClient;
import dev.jadyen.hack.echo.natives.WindowHandle;
import dev.jadyen.hack.echo.natives.WindowBackend;
import dev.dov.hack.echo.client.gfx.command.CommandEncoder;
import dev.dov.hack.echo.client.gfx.opengl.OpenGlDevice;
import dev.dov.hack.echo.client.gfx.queue.CommandQueue;
import dev.dov.hack.echo.client.gfx.queue.QueueInfo;
import dev.dov.hack.echo.client.gfx.surface.SurfaceInfo.PresentMode;
import dev.dov.hack.echo.client.gfx.surface.Surface;
import dev.dov.hack.echo.client.gfx.surface.SurfaceInfo;
import dev.dov.hack.echo.client.gfx.texture.Texture;
import dev.dov.hack.echo.client.gfx.texture.TextureInfo;
import dev.dov.hack.echo.client.gfx.texture.TextureView;
import java.util.OptionalInt;
import net.minecraft.client.MinecraftClient;

public final class RenderBackendManager implements AutoCloseable {
    private static final int GRAPHICS_AND_PRESENT_QUEUE = 9;
    private static final int DEPTH_ATTACHMENT_USAGE = 8;
    private static final int DEPTH_MIP_LEVELS = 1;

    private final WindowBackend window;
    private final Surface surface;
    private final CommandQueue queue;
    private Texture depthTexture;
    private PresentMode presentMode;
    private int framebufferWidth;
    private int framebufferHeight;
    private final boolean openGl;
    private final FramebufferBlur framebufferBlur;

    public RenderBackendManager() {
        framebufferBlur = new FramebufferBlur();
        openGl = EchoClient.renderDevice instanceof OpenGlDevice;
        window = new WindowHandle(openGl);
        surface = EchoClient.renderDevice.createSurface(SurfaceInfo.builder().windowHandle(window.windowHandle()).build());
        queue = EchoClient.renderDevice.createQueue(QueueInfo.builder()
                .NativeCapabilities(GRAPHICS_AND_PRESENT_QUEUE).surface(surface).build());
    }

    public long windowHandle() {
        return window.windowHandle();
    }

    public void endFrame() {
        renderFrame();
        framebufferBlur.resetFrame();
        EchoClient.uiRenderer.clearFrameQueues();
    }

    public void renderFrame() {
        boolean visible = EchoClient.externalRendererActive
                && MinecraftClient.getInstance().world != null
                && !MinecraftClient.getInstance().getWindow().isMinimized();
        window.setVisible(visible);
        if (!visible) {
            return;
        }

        window.synchronizeWithMainWindow();
        int width = window.framebufferWidth();
        int height = window.framebufferHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        PresentMode requestedMode = MinecraftClient.getInstance().options.getEnableVsync().getValue()
                ? PresentMode.FIFO : PresentMode.IMMEDIATE;
        if (width != framebufferWidth || height != framebufferHeight
                || requestedMode != presentMode || surface.needsResize()) {
            surface.resize(width, height, requestedMode);
            framebufferWidth = width;
            framebufferHeight = height;
            presentMode = requestedMode;
        }

        TextureView target = surface.acquireTexture();
        if (target == null) {
            return;
        }
        ensureDepthTexture(target.width(0), target.height(0));
        CommandEncoder commands = queue.beginCommands();
        if (framebufferBlur.isActive()) {
            EchoClient.worldRenderer.compositeToTarget(commands, target, framebufferBlur.colorTexture());
        } else {
            EchoClient.worldRenderer.compositeToTarget(commands, target, EchoClient.worldRenderer.mainColorTexture());
            EchoClient.uiRenderer.beginFrame(commands, target, depthTexture.views[0], OptionalInt.empty());
            EchoClient.uiRenderer.endFrame();
        }
        surface.present(queue, commands);
    }

    @Override
    public void close() {
        queue.waitIdle();
        framebufferBlur.close();
        surface.destroy();
        if (depthTexture != null) {
            depthTexture.destroy();
        }
        queue.destroy();
        window.close();
    }

    private void ensureDepthTexture(int width, int height) {
        if (depthTexture != null && depthTexture.width == width && depthTexture.height == height) {
            return;
        }
        queue.waitIdle();
        if (depthTexture != null) {
            depthTexture.destroy();
        }
        depthTexture = EchoClient.renderDevice.createTexture(TextureInfo.builder()
                .width(width).height(height).format(EchoClient.renderDevice.depthFormat())
                .usage(DEPTH_ATTACHMENT_USAGE).mipCount(DEPTH_MIP_LEVELS).build());
    }

    public FramebufferBlur framebufferBlur() {
        return framebufferBlur;
    }
}
