package io.github.labdev2025cmd.papinho.media.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.labdev2025cmd.papinho.media.MalwareScanner;
import io.github.labdev2025cmd.papinho.media.MediaProperties;

/**
 * Cliente do clamd pelo comando {@code zINSTREAM} (MD-06): envia o arquivo em blocos com tamanho de 4 bytes e
 * lê a resposta {@code stream: OK} ou {@code stream: <assinatura> FOUND}. Qualquer outra resposta é erro, e o
 * upload é recusado (falha fechada).
 */
public class ClamAvScanner implements MalwareScanner {

    private static final Logger log = LoggerFactory.getLogger(ClamAvScanner.class);
    private static final int CHUNK = 64 * 1024;

    private final MediaProperties.ClamAv properties;

    public ClamAvScanner(MediaProperties.ClamAv properties) {
        this.properties = properties;
    }

    @Override
    public boolean isClean(Path file) throws IOException {
        int timeout = (int) properties.timeout().toMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(properties.host(), properties.port()), timeout);
            socket.setSoTimeout(timeout);
            OutputStream out = socket.getOutputStream();
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            byte[] buffer = new byte[CHUNK];
            try (InputStream in = Files.newInputStream(file)) {
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(new byte[] {(byte) (read >>> 24), (byte) (read >>> 16), (byte) (read >>> 8),
                        (byte) read});
                    out.write(buffer, 0, read);
                }
            }
            out.write(new byte[4]);
            out.flush();
            String reply = readReply(socket.getInputStream());
            if (reply.endsWith("OK") && !reply.contains("FOUND")) {
                return true;
            }
            if (reply.endsWith("FOUND")) {
                // Só a assinatura vai para o log; nada do arquivo nem de quem enviou.
                log.warn("Upload rejected by ClamAV: {}", reply.replace("stream: ", ""));
                return false;
            }
            throw new IOException("Unexpected clamd reply");
        }
    }

    private static String readReply(InputStream in) throws IOException {
        ByteArrayOutputStream reply = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) > 0 && reply.size() < 1024) {
            reply.write(b);
        }
        return reply.toString(StandardCharsets.US_ASCII).strip();
    }
}
