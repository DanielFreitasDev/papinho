package io.github.labdev2025cmd.papinho.transcription;

/**
 * Provedores pré-configurados (AU-05). Todos falam a API de transcrição da OpenAI; mudam os padrões.
 */
public enum ProviderKind {
    GROQ("https://api.groq.com/openai", "whisper-large-v3-turbo", 25L * 1024 * 1024),
    WHISPER("http://localhost:8000", "whisper-1", 1024L * 1024 * 1024),
    OPENAI("https://api.openai.com", "whisper-1", 25L * 1024 * 1024),
    CUSTOM("", "whisper-1", 25L * 1024 * 1024);

    private final String defaultBaseUrl;
    private final String defaultModel;
    private final long defaultMaxBytes;

    ProviderKind(String defaultBaseUrl, String defaultModel, long defaultMaxBytes) {
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
        this.defaultMaxBytes = defaultMaxBytes;
    }

    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String defaultModel() {
        return defaultModel;
    }

    public long defaultMaxBytes() {
        return defaultMaxBytes;
    }
}
