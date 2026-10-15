package io.github.labdev2025cmd.papinho.shared.qr;

import java.util.EnumMap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/** Gera QR Codes em SVG puro (sem estilos inline, compatível com a CSP estrita). */
public final class QrCodes {

    private QrCodes() {
    }

    /**
     * QR Code em SVG para o conteúdo informado.
     *
     * @param content texto ou URL codificado
     * @param label nome acessível do SVG
     */
    public static String svg(String content, String label) {
        try {
            var hints = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 2);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints);
            StringBuilder path = new StringBuilder();
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    if (matrix.get(x, y)) {
                        path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                    }
                }
            }
            String escapedLabel = org.springframework.web.util.HtmlUtils.htmlEscape(label);
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + matrix.getWidth() + " "
                    + matrix.getHeight() + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\""
                    + escapedLabel + "\"><rect width=\"100%\" height=\"100%\" fill=\"#fff\"/><path fill=\"#000\" d=\""
                    + path + "\"/></svg>";
        } catch (WriterException exception) {
            throw new IllegalStateException("Unable to render QR code", exception);
        }
    }
}
