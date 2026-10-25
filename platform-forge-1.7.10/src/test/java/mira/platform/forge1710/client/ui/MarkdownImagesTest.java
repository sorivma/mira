package mira.platform.forge1710.client.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class MarkdownImagesTest {
    @Test public void remoteImagesWaitForConsentWithoutPerformingIo() {
        MarkdownImages.Image image = MarkdownImages.get("https://127.0.0.1:1/private-image.png");
        assertTrue(image.requiresConsent);
        assertFalse(image.failed);
        assertNull(image.pixels);
        assertNull(image.texture);
        assertSame(image, MarkdownImages.get("https://127.0.0.1:1/private-image.png"));
    }

    @Test public void insecureAndLocalFileSchemesCannotLoad() {
        for (String source : new String[] {"http://localhost/image.png", "file:///private/image.png", "data:image/png;base64,AA=="}) {
            MarkdownImages.Image image = MarkdownImages.get(source);
            assertTrue(image.failed);
            assertFalse(image.requiresConsent);
            assertNull(image.pixels);
        }
    }
}
