package com.wiley.cms.cochrane.cmanager.publish.generate;

import java.util.Comparator;
import java.util.Objects;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 01.11.11
 */
public class ArchiveEntry implements Comparable<ArchiveEntry>{
    private static final Comparator<ArchiveEntry> COMPARATOR = Comparator
            .comparing(ArchiveEntry::getPathToContent, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(ArchiveEntry::getPathInArchive, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(ArchiveEntry::getContent, Comparator.nullsFirst(Comparator.naturalOrder()));
    private String pathInArchive;
    private String pathToContent;
    private String content;

    public ArchiveEntry(String pathInArchive, String pathToContent) {
        this(pathInArchive, pathToContent, null);
    }

    public ArchiveEntry(String pathInArchive, String pathToContent, String content) {
        this.pathInArchive = pathInArchive;
        this.pathToContent = pathToContent;
        this.content = content;
    }

    public String getPathInArchive() {
        return pathInArchive;
    }

    public String getPathToContent() {
        return pathToContent;
    }

    public String getContent() {
        return content;
    }

    @Override
    public int compareTo(ArchiveEntry other) {
        return COMPARATOR.compare(this, other);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ArchiveEntry) {
            return compareTo((ArchiveEntry) o) == 0;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pathInArchive, pathToContent, content);
    }

    @Override
    public String toString() {
        return "ArchiveEntry{"
                + "pathInArchive='" + pathInArchive + '\''
                + ", pathToContent='" + pathToContent + '\''
                + ", content.length()='" + (content == null ? null : content.length()) + '\''
                + '}';
    }
}
