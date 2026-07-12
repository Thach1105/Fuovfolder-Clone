package com.fuoverflow.exam.application;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Component
class BlurImageGenerator {
    private static final int TARGET_WIDTH = 100;
    private static final int BLUR_RADIUS = 10;

    byte[] generateBlur(InputStream originalImage) throws IOException {
        BufferedImage source = ImageIO.read(originalImage);
        if (source == null) {
            throw new IOException("Unable to decode image");
        }

        int targetHeight = Math.max(1,
                (int) Math.round((double) source.getHeight() / source.getWidth() * TARGET_WIDTH));
        BufferedImage resized = new BufferedImage(TARGET_WIDTH, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, TARGET_WIDTH, targetHeight, null);
        g.dispose();

        BufferedImage blurred = applyGaussianBlur(resized, BLUR_RADIUS);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(blurred, "jpeg", out);
        return out.toByteArray();
    }

    private static BufferedImage applyGaussianBlur(BufferedImage image, int radius) {
        int size = radius * 2 + 1;
        float[] data = new float[size * size];
        float sigma = radius / 3.0f;
        float sum = 0;
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                float value = (float) Math.exp(-(x * x + y * y) / (2 * sigma * sigma));
                data[(y + radius) * size + (x + radius)] = value;
                sum += value;
            }
        }
        for (int i = 0; i < data.length; i++) {
            data[i] /= sum;
        }
        Kernel kernel = new Kernel(size, size, data);
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        return op.filter(image, null);
    }
}
