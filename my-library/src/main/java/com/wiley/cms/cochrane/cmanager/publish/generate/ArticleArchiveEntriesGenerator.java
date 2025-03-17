package com.wiley.cms.cochrane.cmanager.publish.generate;

import org.apache.commons.io.FilenameUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 02.10.2018
 *
 * @param <T> article metadata type
 */
public class ArticleArchiveEntriesGenerator<T> {
    private final List<ArticleContentCollector<T>> contentCollectors = new ArrayList<>();
    private final List<Function<String, String>> pathRelativizers = new ArrayList<>();

    private ArticleArchiveEntriesGenerator() {
    }

    public List<ArchiveEntry> generate(T articleMd) {
        List<ArchiveEntry> entries = new ArrayList<>();
        for (int i = 0; i < contentCollectors.size(); i++) {
            List<String> systemPaths = contentCollectors.get(i).apply(articleMd);
            Function<String, String> pathRelativizer = pathRelativizers.get(i);
            for (String systemPath : systemPaths) {
                entries.add(new ArchiveEntry(
                        FilenameUtils.separatorsToUnix(pathRelativizer.apply(systemPath)),
                        systemPath));
            }
        }
        return entries;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     *
     */
    public static class Builder<T> {
        private final ArticleArchiveEntriesGenerator<T> generator;

        private Builder() {
            this.generator = new ArticleArchiveEntriesGenerator<>();
        }

        public Builder<T> addInstruction(ArticleContentCollector<T> contentCollector,
                                         Function<String, String> pathRelativizer) {
            generator.contentCollectors.add(contentCollector);
            generator.pathRelativizers.add(pathRelativizer);
            return this;
        }

        public ArticleArchiveEntriesGenerator<T> build() {
            return generator;
        }
    }
}
