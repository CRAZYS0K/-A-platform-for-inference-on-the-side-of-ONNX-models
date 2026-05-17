package com.sokolov.labs.worker.inference;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public final class ImageLoader {

    public record Loaded(float[] tensor, int origWidth, int origHeight,
                         double scale, int padLeft, int padTop) {
    }

    private ImageLoader() {
    }

    /**
     * Reads JPG/PNG bytes, letterbox-resizes to {@code targetH x targetW}, normalizes to [0..1],
     * lays out as NCHW (channels=3, R/G/B) with values in row-major order.
     */
    public static Loaded loadLetterboxRgb(byte[] data, int targetH, int targetW) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
        if (src == null) {
            throw new IOException("Unsupported or corrupt image");
        }
        int origW = src.getWidth();
        int origH = src.getHeight();

        double scale = Math.min((double) targetW / origW, (double) targetH / origH);
        int resizedW = (int) Math.round(origW * scale);
        int resizedH = (int) Math.round(origH * scale);
        int padLeft = (targetW - resizedW) / 2;
        int padTop = (targetH - resizedH) / 2;

        BufferedImage canvas = new BufferedImage(targetW, targetH, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            // YOLO letterbox pads with gray (114)
            g.setColor(new java.awt.Color(114, 114, 114));
            g.fillRect(0, 0, targetW, targetH);
            g.drawImage(src, padLeft, padTop, resizedW, resizedH, null);
        } finally {
            g.dispose();
        }

        int hw = targetH * targetW;
        float[] tensor = new float[3 * hw];
        int[] rgb = canvas.getRGB(0, 0, targetW, targetH, null, 0, targetW);
        for (int i = 0; i < hw; i++) {
            int pixel = rgb[i];
            tensor[i] = ((pixel >> 16) & 0xFF) / 255f;             // R
            tensor[hw + i] = ((pixel >> 8) & 0xFF) / 255f;          // G
            tensor[2 * hw + i] = (pixel & 0xFF) / 255f;             // B
        }
        return new Loaded(tensor, origW, origH, scale, padLeft, padTop);
    }

    public static boolean looksLikeImageName(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".bmp");
    }
}
