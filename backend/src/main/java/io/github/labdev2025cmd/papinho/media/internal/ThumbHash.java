package io.github.labdev2025cmd.papinho.media.internal;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Codificador ThumbHash (algoritmo de Evan Wallace, domínio público): resume uma imagem de até 100×100 em ~25
 * bytes, que o cliente decodifica num placeholder borrado enquanto a imagem carrega (MD-02).
 */
public final class ThumbHash {

    private ThumbHash() {
    }

    /** ThumbHash em Base64 de uma imagem já reduzida para no máximo 100×100. */
    public static String encode(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (w > 100 || h > 100) {
            throw new IllegalArgumentException("ThumbHash input must be at most 100x100");
        }
        int[] rgba = new int[w * h * 4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                int j = (x + y * w) * 4;
                rgba[j] = (argb >> 16) & 0xFF;
                rgba[j + 1] = (argb >> 8) & 0xFF;
                rgba[j + 2] = argb & 0xFF;
                rgba[j + 3] = image.getColorModel().hasAlpha() ? (argb >>> 24) & 0xFF : 255;
            }
        }
        return Base64.getEncoder().encodeToString(rgbaToThumbHash(w, h, rgba));
    }

    static byte[] rgbaToThumbHash(int w, int h, int[] rgba) {
        double avgR = 0;
        double avgG = 0;
        double avgB = 0;
        double avgA = 0;
        for (int i = 0, j = 0; i < w * h; i++, j += 4) {
            double alpha = rgba[j + 3] / 255.0;
            avgR += alpha / 255.0 * rgba[j];
            avgG += alpha / 255.0 * rgba[j + 1];
            avgB += alpha / 255.0 * rgba[j + 2];
            avgA += alpha;
        }
        if (avgA > 0) {
            avgR /= avgA;
            avgG /= avgA;
            avgB /= avgA;
        }
        boolean hasAlpha = avgA < w * h;
        int lLimit = hasAlpha ? 5 : 7;
        int lx = Math.max(1, (int) Math.round((double) lLimit * w / Math.max(w, h)));
        int ly = Math.max(1, (int) Math.round((double) lLimit * h / Math.max(w, h)));
        double[] l = new double[w * h];
        double[] p = new double[w * h];
        double[] q = new double[w * h];
        double[] a = new double[w * h];
        for (int i = 0, j = 0; i < w * h; i++, j += 4) {
            double alpha = rgba[j + 3] / 255.0;
            double r = avgR * (1 - alpha) + alpha / 255.0 * rgba[j];
            double g = avgG * (1 - alpha) + alpha / 255.0 * rgba[j + 1];
            double b = avgB * (1 - alpha) + alpha / 255.0 * rgba[j + 2];
            l[i] = (r + g + b) / 3;
            p[i] = (r + g) / 2 - b;
            q[i] = r - g;
            a[i] = alpha;
        }
        Channel lc = encodeChannel(l, w, h, Math.max(3, lx), Math.max(3, ly));
        Channel pc = encodeChannel(p, w, h, 3, 3);
        Channel qc = encodeChannel(q, w, h, 3, 3);
        Channel ac = hasAlpha ? encodeChannel(a, w, h, 5, 5) : null;
        boolean landscape = w > h;
        int header24 = (int) Math.round(63 * lc.dc) | ((int) Math.round(31.5 + 31.5 * pc.dc) << 6)
                | ((int) Math.round(31.5 + 31.5 * qc.dc) << 12) | ((int) Math.round(31 * lc.scale) << 18)
                | (hasAlpha ? 1 << 23 : 0);
        int header16 = (landscape ? ly : lx) | ((int) Math.round(63 * pc.scale) << 3)
                | ((int) Math.round(63 * qc.scale) << 9) | (landscape ? 1 << 15 : 0);
        List<double[]> acs = new ArrayList<>(List.of(lc.ac, pc.ac, qc.ac));
        if (ac != null) {
            acs.add(ac.ac);
        }
        int acStart = hasAlpha ? 6 : 5;
        int acCount = acs.stream().mapToInt(values -> values.length).sum();
        byte[] hash = new byte[acStart + (acCount + 1) / 2];
        hash[0] = (byte) (header24 & 255);
        hash[1] = (byte) ((header24 >> 8) & 255);
        hash[2] = (byte) (header24 >> 16);
        hash[3] = (byte) (header16 & 255);
        hash[4] = (byte) (header16 >> 8);
        if (ac != null) {
            hash[5] = (byte) ((int) Math.round(15 * ac.dc) | ((int) Math.round(15 * ac.scale) << 4));
        }
        int index = 0;
        for (double[] values : acs) {
            for (double f : values) {
                hash[acStart + (index >> 1)] |= (byte) ((int) Math.round(15 * f) << ((index & 1) << 2));
                index++;
            }
        }
        return hash;
    }

    private static Channel encodeChannel(double[] channel, int w, int h, int nx, int ny) {
        double dc = 0;
        double scale = 0;
        List<Double> ac = new ArrayList<>();
        double[] fx = new double[w];
        for (int cy = 0; cy < ny; cy++) {
            for (int cx = 0; cx * ny < nx * (ny - cy); cx++) {
                double f = 0;
                for (int x = 0; x < w; x++) {
                    fx[x] = Math.cos(Math.PI / w * cx * (x + 0.5));
                }
                for (int y = 0; y < h; y++) {
                    double fy = Math.cos(Math.PI / h * cy * (y + 0.5));
                    for (int x = 0; x < w; x++) {
                        f += channel[x + y * w] * fx[x] * fy;
                    }
                }
                f /= w * h;
                if (cx > 0 || cy > 0) {
                    ac.add(f);
                    scale = Math.max(scale, Math.abs(f));
                } else {
                    dc = f;
                }
            }
        }
        double[] values = new double[ac.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = scale > 0 ? 0.5 + 0.5 / scale * ac.get(i) : ac.get(i);
        }
        return new Channel(dc, values, scale);
    }

    private record Channel(double dc, double[] ac, double scale) {
    }
}
