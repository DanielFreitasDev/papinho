package io.github.labdev2025cmd.papinho.support;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

/** PDFs de teste, gerados com o próprio PDFBox (só formas, sem fontes). */
public final class Pdfs {

    private Pdfs() {
    }

    /**
     * {@code pages} páginas: a primeira A4 em pé com a metade de cima preta (a prévia é dela), as outras A4 deitadas.
     */
    public static byte[] document(int pages) {
        try (PDDocument document = new PDDocument()) {
            PDRectangle a4 = PDRectangle.A4;
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(i == 0 ? a4 : new PDRectangle(a4.getHeight(), a4.getWidth()));
                document.addPage(page);
                if (i == 0) {
                    try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                        content.setNonStrokingColor(Color.BLACK);
                        content.addRect(0, a4.getHeight() / 2, a4.getWidth(), a4.getHeight() / 2);
                        content.fill();
                    }
                }
            }
            return save(document);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Uma página em branco do tamanho dado, em pontos. */
    public static byte[] page(float width, float height) {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(new PDRectangle(width, height)));
            return save(document);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** PDF que só abre com a senha (AES de 256 bits). */
    public static byte[] withPassword(String password) {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            StandardProtectionPolicy policy = new StandardProtectionPolicy("dono-" + password, password,
                    new AccessPermission());
            policy.setEncryptionKeyLength(256);
            document.protect(policy);
            return save(document);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * "Bomba de descompressão": uma página cujo conteúdo, comprimido em poucas centenas de KB, ocupa {@code bytes}
     * ao abrir. O filtro vai numa lista ({@code [/FlateDecode]}): com o nome solto, o PDFBox lê o conteúdo da página
     * aos poucos; com a lista, descomprime tudo na memória, como faz com fontes, imagens e formulários.
     */
    public static byte[] decompressionBomb(long bytes) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            COSStream contents = document.getDocument().createCOSStream();
            byte[] spaces = new byte[1 << 20];
            Arrays.fill(spaces, (byte) ' ');
            try (OutputStream deflated = new DeflaterOutputStream(contents.createRawOutputStream())) {
                for (long written = 0; written < bytes; written += spaces.length) {
                    deflated.write(spaces);
                }
            }
            COSArray filters = new COSArray();
            filters.add(COSName.FLATE_DECODE);
            contents.setItem(COSName.FILTER, filters);
            page.getCOSObject().setItem(COSName.CONTENTS, contents);
            return save(document);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static byte[] save(PDDocument document) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }
}
