package com.anjia.unidbgserver.utils;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

/**
 * 评论数徽章 PNG 渲染器
 * <p>
 * 纯 JDK AWT 绘制（headless 安全），无 Spring 依赖。
 * 尺寸沿用原 SVG 徽章的度量：高 24、圆角 rx=12、SansSerif 加粗 12 号、
 * 宽度 = max(28, 位数 * 7 + 12)。
 */
public final class BadgeImageRenderer {

    private static final int HEIGHT = 24;
    private static final int RX = 12;
    private static final int FONT_SIZE = 12;
    /** 徽章背景色 #999 */
    private static final Color BACKGROUND_COLOR = new Color(0x99, 0x99, 0x99);

    private BadgeImageRenderer() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 渲染 count 的 PNG 徽章字节数组
     *
     * @param count 评论数（> 0）
     * @return PNG 字节数组
     */
    public static byte[] renderPng(int count) {
        String countStr = String.valueOf(count);
        int width = Math.max(28, countStr.length() * 7 + 12);

        BufferedImage image = new BufferedImage(width, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 圆角矩形背景
            g2d.setColor(BACKGROUND_COLOR);
            g2d.fillRoundRect(0, 0, width, HEIGHT, RX * 2, RX * 2);

            // 白色加粗数字，水平/垂直居中
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, FONT_SIZE));
            FontMetrics fontMetrics = g2d.getFontMetrics();
            int textX = (width - fontMetrics.stringWidth(countStr)) / 2;
            int baselineY = (HEIGHT - (fontMetrics.getAscent() + fontMetrics.getDescent())) / 2
                    + fontMetrics.getAscent();
            g2d.drawString(countStr, textX, baselineY);
        } finally {
            g2d.dispose();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", baos);
        } catch (Exception e) {
            throw new IllegalStateException("PNG 编码失败", e);
        }
        return baos.toByteArray();
    }
}