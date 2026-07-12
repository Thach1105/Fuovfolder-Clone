package com.fuoverflow.exam.application;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlurImageGeneratorTest {
    private final BlurImageGenerator generator = new BlurImageGenerator();

    @Test
    void generateBlur_producesSmallJpeg() throws IOException {
        BufferedImage source = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", sourceBytes);

        byte[] blurBytes = generator.generateBlur(new ByteArrayInputStream(sourceBytes.toByteArray()));

        assertNotNull(blurBytes);
        assertTrue(blurBytes.length > 0);
        assertTrue(blurBytes.length < sourceBytes.size());

        BufferedImage blurImage = ImageIO.read(new ByteArrayInputStream(blurBytes));
        assertNotNull(blurImage);
        assertEquals(100, blurImage.getWidth());
        assertEquals(75, blurImage.getHeight());
    }

    @Test
    void generateBlur_handlesPortraitImage() throws IOException {
        BufferedImage source = new BufferedImage(400, 800, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", sourceBytes);

        byte[] blurBytes = generator.generateBlur(new ByteArrayInputStream(sourceBytes.toByteArray()));

        BufferedImage blurImage = ImageIO.read(new ByteArrayInputStream(blurBytes));
        assertEquals(100, blurImage.getWidth());
        assertEquals(200, blurImage.getHeight());
    }
}
