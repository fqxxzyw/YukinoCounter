package com.yukino.counter;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AppIcon {
    private AppIcon() {}

    public static BufferedImage image(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float s = size / 256f;
        g.setColor(new Color(225, 241, 255, 225));
        g.fillRoundRect(Math.round(12*s), Math.round(12*s), Math.round(232*s), Math.round(232*s), Math.round(58*s), Math.round(58*s));
        g.setColor(new Color(0, 103, 192));
        g.setStroke(new BasicStroke(Math.max(1.5f, 15*s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double cx=size/2d, cy=size/2d, radius=83*s;
        for (int arm=0; arm<6; arm++) {
            double a = Math.PI*arm/3d;
            double ex=cx+Math.cos(a)*radius, ey=cy+Math.sin(a)*radius;
            g.drawLine((int)cx,(int)cy,(int)ex,(int)ey);
            for (double f : new double[]{.58,.82}) {
                double bx=cx+Math.cos(a)*radius*f, by=cy+Math.sin(a)*radius*f;
                double branch=25*s;
                for (double da : new double[]{Math.PI*5/6, -Math.PI*5/6}) {
                    g.drawLine((int)bx,(int)by,(int)(bx+Math.cos(a+da)*branch),(int)(by+Math.sin(a+da)*branch));
                }
            }
        }
        g.dispose();
        return image;
    }

    /** Writes a Windows Vista+ PNG-compressed .ico containing a 256px icon. */
    public static void writeIco(Path path) throws IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image(256), "png", png);
        byte[] data = png.toByteArray();
        ByteBuffer header = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short)0).putShort((short)1).putShort((short)1);
        header.put((byte)0).put((byte)0).put((byte)0).put((byte)0);
        header.putShort((short)1).putShort((short)32).putInt(data.length).putInt(22);
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (var out = Files.newOutputStream(path)) { out.write(header.array()); out.write(data); }
    }

    public static void main(String[] args) throws IOException {
        Path ico = Path.of(args.length == 0 ? "assets/YukinoCounter.ico" : args[0]);
        writeIco(ico);
        ImageIO.write(image(256), "png", ico.resolveSibling("YukinoCounter.png").toFile());
    }
}
