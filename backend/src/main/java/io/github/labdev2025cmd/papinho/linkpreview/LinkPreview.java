package io.github.labdev2025cmd.papinho.linkpreview;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Prévia de um link em cache (MD-04). */
@Entity
@Table(name = "link_previews")
class LinkPreview {

    @Id
    private String urlHash;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private boolean available;

    private @Nullable String title;

    private @Nullable String description;

    private @Nullable String siteName;

    private byte @Nullable [] image;

    private @Nullable String embedProvider;

    private @Nullable String embedSrc;

    @Column(nullable = false)
    private Instant fetchedAt;

    protected LinkPreview() {
        this.urlHash = "";
        this.url = "";
        this.fetchedAt = Instant.EPOCH;
    }

    LinkPreview(String urlHash, String url, Instant now) {
        this.urlHash = urlHash;
        this.url = url;
        this.fetchedAt = now;
    }

    void fill(boolean available, @Nullable String title, @Nullable String description, @Nullable String siteName,
            byte @Nullable [] image, @Nullable String embedProvider, @Nullable String embedSrc, Instant now) {
        this.available = available;
        this.title = title;
        this.description = description;
        this.siteName = siteName;
        this.image = image;
        this.embedProvider = embedProvider;
        this.embedSrc = embedSrc;
        this.fetchedAt = now;
    }

    String urlHash() {
        return urlHash;
    }

    Instant fetchedAt() {
        return fetchedAt;
    }

    byte @Nullable [] image() {
        return image;
    }

    LinkPreviewService.PreviewView toView() {
        return new LinkPreviewService.PreviewView(url, available, title, description, siteName,
                image == null ? null : "/api/v1/link-previews/" + urlHash + "/image", embedProvider, embedSrc);
    }
}
