package mira.platform.forge1710.client.ui;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

/** Bounded asynchronous image loading; texture allocation/release stays on the render thread. */
final class MarkdownImages {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final ExecutorService loader = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<Runnable>(32), action -> {
        Thread thread = new Thread(action, "Mira Markdown images"); thread.setDaemon(true); return thread;
    });
    private static final Map<String, Image> cache = new LinkedHashMap<>(32, 0.75F, true);
    static final class Image {
        volatile BufferedImage pixels;
        volatile boolean failed;
        boolean requiresConsent;
        ResourceLocation texture;
        int width = 160, height = 90;
        ResourceLocation texture(Minecraft minecraft) {
            if (texture == null && pixels != null) {
                BufferedImage ready = pixels;
                width = ready.getWidth(); height = ready.getHeight();
                texture = minecraft.getTextureManager().getDynamicTextureLocation("mira-markdown", new DynamicTexture(ready));
                pixels = null;
            }
            return texture;
        }
    }
    private MarkdownImages() {}
    static Image get(String source) {
        Image cached = cache.get(source);
        if (cached != null) return cached;
        Image image = new Image(); cache.put(source, image);
        if (cache.size() > 32) {
            Iterator<Image> entries = cache.values().iterator();
            Image oldest = entries.next(); entries.remove();
            if (oldest.texture != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(oldest.texture);
        }
        if (source.matches("[a-z0-9_.-]+:[a-zA-Z0-9_][a-zA-Z0-9_./-]*") && !source.startsWith("http:") && !source.startsWith("https:")) {
            image.texture = new ResourceLocation(source);
            try (InputStream resource = Minecraft.getMinecraft().getResourceManager().getResource(image.texture).getInputStream();
                    ImageInputStream stream = ImageIO.createImageInputStream(resource)) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw new IllegalArgumentException("Image format");
                ImageReader reader = readers.next();
                try {
                    reader.setInput(stream); image.width = reader.getWidth(0); image.height = reader.getHeight(0);
                    if (image.width < 1 || image.height < 1) throw new IllegalArgumentException("Image dimensions");
                } finally { reader.dispose(); }
            } catch (Exception ignored) { image.texture = null; image.failed = true; }
        } else if (source.startsWith("https://")) image.requiresConsent = true;
        else image.failed = true;
        return image;
    }
    static void request(String source) {
        Image image = get(source);
        if (!image.requiresConsent) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!minecraft.gameSettings.chatLinks) return;
        GuiScreen previous = minecraft.currentScreen;
        minecraft.displayGuiScreen(new GuiConfirmOpenLink((accepted, id) -> {
            minecraft.displayGuiScreen(previous);
            if (!accepted) return;
            image.requiresConsent = false;
            try {
                loader.execute(() -> {
                    try { image.pixels = download(source); }
                    catch (Exception ignored) { image.failed = true; }
                });
            } catch (RejectedExecutionException busy) { image.failed = true; }
        }, source, 0, true));
    }
    private static BufferedImage download(String source) throws Exception {
        URL url = new URL(source);
        for (int redirect = 0; redirect < 4; redirect++) {
            if (!url.getProtocol().equals("https") || url.getUserInfo() != null)
                throw new IllegalArgumentException("Image protocol");
            for (InetAddress address : InetAddress.getAllByName(url.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()
                    || (address.getAddress().length == 16 && (address.getAddress()[0] & 0xfe) == 0xfc))
                    throw new IllegalArgumentException("Private image destination");
            }
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(4000); connection.setReadTimeout(4000); connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Mira/0.1 Markdown");
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) { url = new URL(url, connection.getHeaderField("Location")); continue; }
                if (status != 200 || connection.getContentLengthLong() > MAX_BYTES) throw new IllegalArgumentException("Image response");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream input = connection.getInputStream()) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (bytes.size() + count > MAX_BYTES) throw new IllegalArgumentException("Image size");
                        bytes.write(buffer, 0, count);
                    }
                }
                try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                    Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
                    if (!readers.hasNext()) throw new IllegalArgumentException("Image format");
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(stream);
                        int width = reader.getWidth(0), height = reader.getHeight(0);
                        if (width < 1 || height < 1 || (long) width * height > 8000000) throw new IllegalArgumentException("Image dimensions");
                        BufferedImage decoded = reader.read(0);
                        double scale = Math.min(1, 1024.0 / Math.max(width, height));
                        if (scale == 1) return decoded;
                        BufferedImage resized = new BufferedImage(Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)), BufferedImage.TYPE_INT_ARGB);
                        Graphics2D graphics = resized.createGraphics();
                        try { graphics.drawImage(decoded, 0, 0, resized.getWidth(), resized.getHeight(), null); }
                        finally { graphics.dispose(); }
                        return resized;
                    } finally { reader.dispose(); }
                }
            } finally { connection.disconnect(); }
        }
        throw new IllegalArgumentException("Image redirects");
    }
}
