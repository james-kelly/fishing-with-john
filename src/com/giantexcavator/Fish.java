package com.giantexcavator;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;
import java.util.Random;

public class Fish {

    private static final int CAST_KEY = KeyEvent.VK_EQUALS;
    public static final int WAIT_MS = 15000;
    public static final double FRAME_DIFF_THRESHOLD = 0.045;
    public static final int START_DELAY_MS = 5000;
    public static final int POST_CAST_DELAY_MS = 4000;
    public static final int LOOP_DELAY_MIN_MS = 1000;
    public static final int LOOP_DELAY_MAX_MS = 2000;
    public static final int KEY_PRESS_DELAY_MIN_MS = 10;
    public static final int KEY_PRESS_DELAY_MAX_MS = 50;
    public static final String CASTS_DIR = "casts";
    public static final String BOBBERS_DIR = "bobbers";

    private Robot robot;

    private final Dimension screenSize;
    private final Rectangle castBoundingBox;

    private final Iterator<Integer> keyPressDelay = new Random().ints(KEY_PRESS_DELAY_MIN_MS, KEY_PRESS_DELAY_MAX_MS).iterator();
    private final Iterator<Integer> loopDelay = new Random().ints(LOOP_DELAY_MIN_MS, LOOP_DELAY_MAX_MS).iterator();

    private final File castsDir = new File(CASTS_DIR);
    private final File bobberDir = new File(BOBBERS_DIR);

    public Fish() {

        try {
            System.loadLibrary("opencv_java249");

            this.screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int x = (int) (screenSize.width * 0.25);
            int y = 50;
            int w = (int) (screenSize.width * 0.50);
            int h = (int) (screenSize.height * 0.75);

            this.castBoundingBox = new Rectangle(x, y, w, h);
            this.robot = new Robot();

        } catch (AWTException e) {
            throw new RuntimeException();
        }
    }


    public void fish() throws Exception {
        robot.delay(START_DELAY_MS);

        while (true) {
            // Move mouse out of the way
            robot.mouseMove(10, 10);

            // Cast
            robot.keyPress(CAST_KEY);
            robot.delay(keyPressDelay.next());
            robot.keyRelease(CAST_KEY);

            // Wait for ghost image from previous cast to fade
            robot.delay(POST_CAST_DELAY_MS);

            // Find the bobber
            BufferedImage capture = this.robot.createScreenCapture(castBoundingBox);
            File castSource = File.createTempFile("cast-", ".png", castsDir);
            ImageIO.write(capture, "png", castSource);
            Rectangle result = findBobber(castSource.getPath(), new File(bobberDir, "template7.png").getPath());

            // Translate the bobber rect to the screen coordinates
            result.translate(castBoundingBox.x, castBoundingBox.y);
            robot.mouseMove(result.x + result.width / 2 - 6, result.y + result.height / 2 - 6);

            // Wait for the "fish on hook" dip
            double maxP = 0;
            long start = System.currentTimeMillis();
            long now = System.currentTimeMillis();
            BufferedImage previousImage = this.robot.createScreenCapture(result);
            while (now - start < WAIT_MS) {
                BufferedImage currentImage = this.robot.createScreenCapture(result);
                double p = percentDiff(previousImage, currentImage);

                if (p > maxP) {
                    maxP = p;
                }

                if (p > FRAME_DIFF_THRESHOLD) {
                    break;
                }
                previousImage = currentImage;
                now = System.currentTimeMillis();
            }

            // Click the bobber
            robot.delay(100);
            robot.mousePress(InputEvent.BUTTON3_MASK);
            robot.delay(keyPressDelay.next());
            robot.mouseRelease(InputEvent.BUTTON3_MASK);

            robot.delay(loopDelay.next());
        }
    }

    /**
     * http://stackoverflow.com/questions/17001083/opencv-template-matching-example-in-android
     */
    public Rectangle findBobber(String sourceFile, String templateFile) {

        int match_method = Imgproc.TM_SQDIFF;
        Mat sourceMat = Highgui.imread(sourceFile);
        Mat templateMat = Highgui.imread(templateFile);

        int resultCols = sourceMat.cols() - templateMat.cols() + 1;
        int resultRows = sourceMat.rows() - templateMat.rows() + 1;
        Mat resultMat = new Mat(resultRows, resultCols, CvType.CV_32FC1);

        Imgproc.matchTemplate(sourceMat, templateMat, resultMat, match_method);
        Core.normalize(resultMat, resultMat, 0, 1, Core.NORM_MINMAX, -1, new Mat());
        Core.MinMaxLocResult mmr = Core.minMaxLoc(resultMat);

        org.opencv.core.Point matchLoc;
        if (match_method == Imgproc.TM_SQDIFF || match_method == Imgproc.TM_SQDIFF_NORMED) {
            matchLoc = mmr.minLoc;
        } else {
            matchLoc = mmr.maxLoc;
        }

        return new Rectangle((int) matchLoc.x, (int) matchLoc.y, templateMat.cols(), templateMat.rows());
    }


    /**
     * http://rosettacode.org/wiki/Percentage_difference_between_images#Java
     */
    private double percentDiff(BufferedImage img1, BufferedImage img2) {
        int width1 = img1.getWidth(null);
        int width2 = img2.getWidth(null);
        int height1 = img1.getHeight(null);
        int height2 = img2.getHeight(null);
        if ((width1 != width2) || (height1 != height2)) {
            System.err.println("Error: Images dimensions mismatch");
            System.exit(1);
        }
        long diff = 0;

        for (int i = 0; i < width1 - 1; i++) {
            for (int j = 0; j < height1 - 1; j++) {
                int rgb1 = img1.getRGB(i, j);
                int rgb2 = img2.getRGB(i, j);
                int r1 = (rgb1 >> 16) & 0xff;
                int g1 = (rgb1 >> 8) & 0xff;
                int b1 = (rgb1) & 0xff;
                int r2 = (rgb2 >> 16) & 0xff;
                int g2 = (rgb2 >> 8) & 0xff;
                int b2 = (rgb2) & 0xff;
                diff += Math.abs(r1 - r2);
                diff += Math.abs(g1 - g2);
                diff += Math.abs(b1 - b2);
            }
        }

        double n = width1 * height1 * 3;
        double p = diff / n / 255.0;
        return p;

    }

    public static void main(String[] args) throws Exception {
        Fish fish = new Fish();
        fish.fish();
    }

}
