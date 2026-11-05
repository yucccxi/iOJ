package me.iynotuo.judge.captcha;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Renders a small PNG CAPTCHA with Java AWT. No external imaging library is used, which keeps the
 * deployment dependency-free; the generator is intentionally isolated so a hosted CAPTCHA can be
 * substituted later.
 */
@Component
public class CaptchaImageGenerator {

    private static final int WIDTH = 140;
    private static final int HEIGHT = 48;
    private static final String[] FONT_FAMILIES = {Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED};

    private final SecureRandom random = new SecureRandom();

    /**
     * @param code the answer to render; callers keep this value server-side only
     * @return a complete {@code data:image/png;base64,...} URL
     */
    public String generate(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            graphics.setColor(new Color(246, 248, 252));
            graphics.fillRect(0, 0, WIDTH, HEIGHT);

            drawNoise(graphics);

            int step = (WIDTH - 24) / Math.max(1, code.length());
            for (int i = 0; i < code.length(); i++) {
                drawCharacter(graphics, code.charAt(i), 14 + i * step);
            }

            drawInterferenceLines(graphics);
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("PNG image writer is not available");
            }
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("无法生成图形验证码", ex);
        }
    }

    private void drawCharacter(Graphics2D graphics, char value, int x) {
        Font font = new Font(FONT_FAMILIES[random.nextInt(FONT_FAMILIES.length)],
                Font.BOLD, 27 + random.nextInt(4));
        double angle = Math.toRadians(random.nextInt(25) - 12);
        int y = 33 + random.nextInt(7) - 3;

        AffineTransform original = graphics.getTransform();
        graphics.rotate(angle, x, y);
        graphics.setFont(font);
        graphics.setColor(new Color(30 + random.nextInt(70), 30 + random.nextInt(70),
                40 + random.nextInt(70)));
        graphics.drawString(String.valueOf(value), x, y);
        graphics.setTransform(original);
    }

    private void drawNoise(Graphics2D graphics) {
        for (int i = 0; i < 45; i++) {
            graphics.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256),
                    70 + random.nextInt(80)));
            int x = random.nextInt(WIDTH);
            int y = random.nextInt(HEIGHT);
            graphics.fillOval(x, y, 2, 2);
        }
    }

    private void drawInterferenceLines(Graphics2D graphics) {
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        graphics.setStroke(new BasicStroke(1.2f));
        for (int i = 0; i < 6; i++) {
            graphics.setColor(new Color(80 + random.nextInt(120), 80 + random.nextInt(120),
                    80 + random.nextInt(120)));
            graphics.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                    random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }
        graphics.setComposite(AlphaComposite.SrcOver);
    }
}
