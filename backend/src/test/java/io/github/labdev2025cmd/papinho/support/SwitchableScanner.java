package io.github.labdev2025cmd.papinho.support;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.Path;

import io.github.labdev2025cmd.papinho.media.MalwareScanner;

/** Antivírus dos testes: aceita tudo, ou fica fora do ar como um clamd parado (MD-06). */
public class SwitchableScanner implements MalwareScanner {

    private volatile boolean down;

    public void down(boolean value) {
        this.down = value;
    }

    @Override
    public boolean isClean(Path file) throws IOException {
        if (down) {
            throw new ConnectException("Connection refused");
        }
        return true;
    }
}
