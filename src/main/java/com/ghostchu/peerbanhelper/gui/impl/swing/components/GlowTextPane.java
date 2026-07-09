package com.ghostchu.peerbanhelper.gui.impl.swing.components;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class GlowTextPane extends JTextPane {
    private Color glowColor = new Color(255, 107, 143);
    private int glowRadius = 14;
    private float glowIntensity = 0.65f;
    private float glowBoost = 1.8f;

    // --- 性能优化 4.0：缓存友好型渲染 + 预乘 Alpha 加速 ---
    private BufferedImage textLayer;
    private BufferedImage glowLayer;

    private int[] textPixels;
    private int[] glowPixels;

    private int[] glowAlphaBuffer;
    private int[] blurTempBuffer;
    private int[] colSumsBuffer; // 新增：用于优化垂直模糊的列累加器
    private int cachedWidth = 0;
    private int cachedHeight = 0;

    private int[] divTable;
    private int currentKernelSize = -1;

    // 预计算的颜色混合表，干掉绘图时的 Alpha Blending 瓶颈
    private int[] preGlowPixels = new int[256];
    private int currentGlowRGB = -1;

    public void setGlow(Color color, int radius, float intensity, float boost) {
        this.glowColor = color;
        this.glowRadius = Math.max(0, radius);
        this.glowIntensity = Math.clamp(intensity, 0f, 1f);
        this.glowBoost = Math.clamp(boost, 0.5f, 4f);
        repaint();
    }

    private void checkBuffers(int width, int height) {
        if (width > cachedWidth || height > cachedHeight) {
            cachedWidth = Math.max(width, cachedWidth + 512);
            cachedHeight = Math.max(height, cachedHeight + 512);

            textLayer = new BufferedImage(cachedWidth, cachedHeight, BufferedImage.TYPE_INT_ARGB);
            // 核心黑魔法：使用 TYPE_INT_ARGB_PRE (预乘Alpha)，这会让 drawImage 的速度产生质的飞跃
            glowLayer = new BufferedImage(cachedWidth, cachedHeight, BufferedImage.TYPE_INT_ARGB_PRE);

            textPixels = ((java.awt.image.DataBufferInt) textLayer.getRaster().getDataBuffer()).getData();
            glowPixels = ((java.awt.image.DataBufferInt) glowLayer.getRaster().getDataBuffer()).getData();

            int length = cachedWidth * cachedHeight;
            glowAlphaBuffer = new int[length];
            blurTempBuffer = new int[length];
            colSumsBuffer = new int[cachedWidth];
        }
    }

    private void updateDivTable(int radius) {
        int kernelSize = radius * 2 + 1;
        if (currentKernelSize != kernelSize) {
            currentKernelSize = kernelSize;
            int maxSum = 255 * kernelSize;
            divTable = new int[maxSum + 1];
            for (int i = 0; i <= maxSum; i++) {
                divTable[i] = i / kernelSize;
            }
        }
    }

    private void updateGlowTable(Color glow) {
        int rgb = glow.getRGB() & 0xFFFFFF;
        if (currentGlowRGB != rgb) {
            currentGlowRGB = rgb;
            int r = glow.getRed();
            int g = glow.getGreen();
            int b = glow.getBlue();
            for (int a = 0; a <= 255; a++) {
                // 计算预乘 (Premultiplied) 的 ARGB
                int pr = (r * a) / 255;
                int pg = (g * a) / 255;
                int pb = (b * a) / 255;
                preGlowPixels[a] = (a << 24) | (pr << 16) | (pg << 8) | pb;
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (glowRadius <= 0 || glowIntensity <= 0f || getWidth() <= 0 || getHeight() <= 0) {
            super.paintComponent(g);
            return;
        }

        Rectangle clip = g.getClipBounds();
        if (clip == null) {
            clip = new Rectangle(0, 0, getWidth(), getHeight());
        }

        int pad = glowRadius * 2 + 2;
        Rectangle effectBounds = new Rectangle(
                Math.max(0, clip.x - pad),
                Math.max(0, clip.y - pad),
                Math.min(getWidth() - Math.max(0, clip.x - pad), clip.width + pad * 2),
                Math.min(getHeight() - Math.max(0, clip.y - pad), clip.height + pad * 2)
        );

        if (effectBounds.width <= 0 || effectBounds.height <= 0) {
            super.paintComponent(g);
            return;
        }

        checkBuffers(effectBounds.width, effectBounds.height);
        updateDivTable(glowRadius);
        updateGlowTable(glowColor);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setClip(clip);
        g2.setColor(getBackground());
        g2.fillRect(clip.x, clip.y, clip.width, clip.height);

        Graphics2D textGraphics = textLayer.createGraphics();
        textGraphics.setComposite(AlphaComposite.Clear);
        textGraphics.fillRect(0, 0, effectBounds.width, effectBounds.height);
        textGraphics.setComposite(AlphaComposite.SrcOver);
        textGraphics.setClip(0, 0, effectBounds.width, effectBounds.height);
        textGraphics.translate(-effectBounds.x, -effectBounds.y);
        renderTextLayer(textGraphics);
        textGraphics.dispose();

        extractGlowAlpha(effectBounds.width, effectBounds.height, cachedWidth, getBackground(), glowIntensity, glowBoost);
        boxBlurAlpha(effectBounds.width, effectBounds.height, cachedWidth, glowRadius);
        composeGlowImage(effectBounds.width, effectBounds.height, cachedWidth); // 纯查表合成，快到飞起

        g2.drawImage(glowLayer, effectBounds.x, effectBounds.y,
                effectBounds.x + effectBounds.width, effectBounds.y + effectBounds.height,
                0, 0, effectBounds.width, effectBounds.height, null);

        renderTextLayer(g2);
        g2.dispose();
    }

    private void renderTextLayer(Graphics2D graphics) {
        boolean oldOpaque = isOpaque();
        setOpaque(false);
        try {
            super.paintComponent(graphics);
        } finally {
            setOpaque(oldOpaque);
        }
    }

    private void extractGlowAlpha(int width, int height, int stride, Color background, float intensity, float boost) {
        int bgR = background.getRed();
        int bgG = background.getGreen();
        int bgB = background.getBlue();

        for (int y = 0; y < height; y++) {
            int rowOffset = y * stride;
            for (int x = 0; x < width; x++) {
                int index = rowOffset + x;
                int pixel = textPixels[index];
                int alpha = (pixel >>> 24) & 0xFF;
                if (alpha == 0) {
                    glowAlphaBuffer[index] = 0;
                    continue;
                }

                int r = (pixel >>> 16) & 0xFF;
                int g = (pixel >>> 8) & 0xFF;
                int b = pixel & 0xFF;

                int diffR = r > bgR ? r - bgR : bgR - r;
                int diffG = g > bgG ? g - bgG : bgG - g;
                int diffB = b > bgB ? b - bgB : bgB - b;
                int coverage = diffR > diffG ? (Math.max(diffR, diffB)) : (Math.max(diffG, diffB));

                if (coverage <= 1) {
                    glowAlphaBuffer[index] = 0;
                    continue;
                }

                int glowAlpha = (int) ((coverage / 255f) * alpha * intensity * boost);
                glowAlphaBuffer[index] = Math.min(glowAlpha, 255);
            }
        }
    }

    private void boxBlurAlpha(int width, int height, int stride, int radius) {
        if (radius <= 0) {
            for (int y = 0; y < height; y++) {
                System.arraycopy(glowAlphaBuffer, y * stride, glowPixels, y * stride, width);
            }
            return;
        }
        boxBlurHorizontalAlpha(glowAlphaBuffer, blurTempBuffer, width, height, stride, radius);
        boxBlurVerticalAlpha(blurTempBuffer, glowPixels, width, height, stride, radius);
    }

    private void boxBlurHorizontalAlpha(int[] src, int[] dst, int width, int height, int stride, int radius) {
        for (int y = 0; y < height; y++) {
            int row = y * stride;
            int sum = 0;

            for (int i = -radius; i <= radius; i++) {
                int xi = i < 0 ? 0 : (i >= width ? width - 1 : i);
                sum += src[row + xi];
            }

            for (int x = 0; x < width; x++) {
                dst[row + x] = divTable[sum];

                int removeX = x - radius;
                int addX = x + radius + 1;
                removeX = Math.max(removeX, 0);
                addX = addX >= width ? width - 1 : addX;

                sum += src[row + addX] - src[row + removeX];
            }
        }
    }

    // 重写垂直模糊：消除 CPU 缓存未命中（Cache Miss）
    private void boxBlurVerticalAlpha(int[] src, int[] dst, int width, int height, int stride, int radius) {
        java.util.Arrays.fill(colSumsBuffer, 0, width, 0);

        // 1. 初始化列的前 radius 行总和
        for (int y = -radius; y <= radius; y++) {
            int yi = y < 0 ? 0 : (y >= height ? height - 1 : y);
            int rowOffset = yi * stride;
            for (int x = 0; x < width; x++) {
                colSumsBuffer[x] += src[rowOffset + x];
            }
        }

        // 2. 逐行向下扫描：外层循环变成了 Y，内层是 X。内存访问变为完美连续访问！
        for (int y = 0; y < height; y++) {
            int dstRow = y * stride;

            int addY = y + radius + 1;
            addY = addY >= height ? height - 1 : addY;
            int addRowOffset = addY * stride;

            int remY = y - radius;
            remY = Math.max(remY, 0);
            int remRowOffset = remY * stride;

            for (int x = 0; x < width; x++) {
                dst[dstRow + x] = divTable[colSumsBuffer[x]];
                colSumsBuffer[x] += src[addRowOffset + x] - src[remRowOffset + x];
            }
        }
    }

    // 使用查表和预乘 Alpha，将合成速度拉满
    private void composeGlowImage(int width, int height, int stride) {
        for (int y = 0; y < height; y++) {
            int rowOffset = y * stride;
            for (int x = 0; x < width; x++) {
                int index = rowOffset + x;
                int alpha = glowPixels[index];
                // 一次查表直接拿走算好透明度混合的终极像素
                glowPixels[index] = preGlowPixels[alpha];
            }
        }
    }
}
