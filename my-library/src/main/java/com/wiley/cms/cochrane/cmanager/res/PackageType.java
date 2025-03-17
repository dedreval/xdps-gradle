package com.wiley.cms.cochrane.cmanager.res;

import java.io.InputStream;
import java.io.Serializable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.contentworker.IPackageParser;
import com.wiley.cms.cochrane.cmanager.contentworker.PackageArticleResults;
import com.wiley.tes.util.res.DataTable;
import com.wiley.tes.util.res.JaxbResourceFactory;
import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.ResourceManager;
import com.wiley.tes.util.res.ResourceStrId;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 18.07.2019
 */
@XmlRootElement(name = PackageType.RES_NAME)
public class PackageType extends ResourceStrId {

    /**
     * an entry type
     */
    public enum EntryType {
        UNDEF {
            @Override
            public void parseZip(PackageType.Entry entry, Integer issueId, String group, String fileName,
                                 ZipInputStream zis, IPackageParser parser) throws Exception {
                parser.parseUndefinedEntry(entry, fileName, zis);
            }
        },
        ARTICLE {
            @Override
            public Object parseFinal(PackageType.Entry entry, String zeName, InputStream zis,
                                     IPackageParser parser, PackageArticleResults results) throws Exception {
                return addResult(parser.parseEmbeddedArticle(entry, zeName, zis, results), results);
            }

            @Override
            public void parseZip(PackageType.Entry entry, Integer issueId, String group, String fileName,
                                 ZipInputStream zis, IPackageParser parser) throws Exception {
                parser.parseArticleZip(entry, issueId, fileName, zis, false);
            }
        },
        TA {
            @Override
            public Object parseFinal(PackageType.Entry entry, String zeName, InputStream zis,
                                     IPackageParser parser, PackageArticleResults results) throws Exception {
                return addResult(parser.parseEmbeddedArticle(entry, zeName, zis, results), results);
            }

            @Override
            public void parseZip(PackageType.Entry entry, Integer issueId, String group, String fileName,
                                 ZipInputStream zis, IPackageParser parser) throws Exception {
                parser.parseArticleZip(entry, issueId, fileName, zis, true);
            }
        },
        TA_RETRACTED {
            @Override
            public void parseZip(PackageType.Entry entry, Integer issueId, String group, String fileName,
                                 ZipInputStream zis, IPackageParser parser) {
                parser.parseTranslationRetracted(issueId, fileName, zis);
            }
        },
        TOPIC {
            @Override
            public void parseZip(PackageType.Entry entry, Integer issueId, String group, String fileName,
                                 ZipInputStream zis, IPackageParser parser) throws Exception {
                parser.parseTopic(entry, issueId, group, fileName, zis);
            }
        },
        UCS {
            @Override
            public void parseZip(PackageType.Entry entry, Integer issueId, String group, String fileName,
                                 ZipInputStream zis, IPackageParser parser) throws Exception {
                parser.parseUSC(entry, fileName, zis);
            }
        },
        IMAGE {
            @Override
            public Object parseFinal(PackageType.Entry entry, String zeName, InputStream zis,
                                   IPackageParser parser, PackageArticleResults results) throws Exception {
                parser.parseImage(entry, results.getCdNumber(), zeName, zis);
                return Boolean.TRUE;
            }

            @Override
            public void parseZip(PackageType.Entry entry, Integer issueId, String group, String fileName,
                                 ZipInputStream zis, IPackageParser parser) throws Exception {
                parser.parseImage(entry, null, fileName, zis);
            }
        },
        STATS {
            @Override
            public Object parseFinal(PackageType.Entry entry, String zeName, InputStream zis,
                                   IPackageParser parser, PackageArticleResults results) throws Exception {
                parser.parseStatsFile(entry, results.getCdNumber(), zeName, zis);
                return addResult(Boolean.TRUE, results);
            }
        },
        MANIFEST {
            @Override
            public Object parseFinal(PackageType.Entry entry, String zeName, InputStream zis,
                                   IPackageParser parser, PackageArticleResults results) throws Exception {
                return addResult(parser.parseManifest(entry, results.getCurrentPath(), zis), results);
            }
        },
        METADATA {
            @Override
            public Object parseFinal(PackageType.Entry entry, String zeName, InputStream zis,
                                     IPackageParser parser, PackageArticleResults results) throws Exception {
                return parser.parseMetadata(entry, zeName, zis);
            }
        },
        IMPORT {
            @Override
            public Object parseFinal(PackageType.Entry entry, String zeName, InputStream zis,
                                    IPackageParser parser, PackageArticleResults results) throws Exception {
                return addResult(parser.parseImportFile(entry, zeName, zis), results);
            }
        },
        PDF {
            @Override
            public Object parseFinal(PackageType.Entry entry, String zeName, InputStream zis,
                                     IPackageParser parser, PackageArticleResults results) throws Exception {
                return addResult(parser.parsePDF(entry, zeName, zis), results);
            }
        };

        public Exception throwUnsupportedEntry(String path) {
            return new Exception(String.format("an unsupported entry for %s: %s", this, path));
        }

        public Object parseFinal(PackageType.Entry entry, String zeName, InputStream zis,
                                 IPackageParser parser, PackageArticleResults results) throws Exception {
            throw throwUnsupportedEntry(zeName);
        }

        public void parseZip(PackageType.Entry entry, Integer issueId, String group, String fileName,
                             ZipInputStream zis, IPackageParser parser) throws Exception {
            throw throwUnsupportedEntry(fileName);
        }

        public static int size() {
            return IMPORT.ordinal() + 1;
        }

        Object addResult(Object result, PackageArticleResults results) {
            if (results != null) {
                results.addResult(result, this);
            }
            return result;
        }
    }

    static final String RES_NAME = "packagetype";

    private static final long serialVersionUID = 1L;

    private static final DataTable<String, PackageType> DT = new DataTable<>(RES_NAME);

    @XmlElement(name = "template")
    private List<Template> templates;

    @XmlElement(name = "entry")
    private List<Entry> rootEntries;

    private Pattern articlePattern;

    public static void register(ResourceManager loader) {
        loader.register(RES_NAME, JaxbResourceFactory.create(PackageType.class, Entry.class));
    }

    public static Res<PackageType> find(String sid) {
        return DT.findResource(sid);
    }

    public static Res<PackageType> findCDSRJats() {
        return find("cdsr-jats");
    }

    public static Res<PackageType> findCDSRJatsAries() {
        return find("cdsr-jats-aries");
    }

    public static Res<PackageType> findCDSRJatsArchieAries() {
        return find("cdsr-jats-archie-aries");
    }

    public static Res<PackageType> findCDSRRevman() {
        return find("cdsr-revman");
    }

    public static Res<PackageType> findEditorialMl3g() {
        return find("editorial-ml3g");
    }

    public static Res<PackageType> findEditorialMl3gAries() {
        return find("editorial-ml3g-aries");
    }

    public static Res<PackageType> findEditorialMl3gFlat() {
        return find("editorial-ml3g-flat");
    }

    public static Res<PackageType> findCcaMl3g() {
        return find("cca-ml3g");
    }

    public static Res<PackageType> findCcaMl3gAries() {
        return find("cca-ml3g-aries");
    }

    public static Res<PackageType> findBaseAries() {
        return find("base-aries");
    }

    static Res<PackageType> get(String sid) {
        return DT.get(sid);
    }

    @Override
    protected void populate() {
        DT.publish(this);
    }

    @Override
    protected void resolve() {
        if (templates == null) {
            return;
        }
        resolve(rootEntries, templates);

        String articleTemplate = getArticleTemplate();
        if (articleTemplate != null) {
            articlePattern = Pattern.compile(articleTemplate);
        }
    }

    public Entry match(String path) {
        return match(path, rootEntries);
    }

    public Pattern getArticlePattern() {
        return articlePattern;
    }

    public String parseArticleName(String path) {
        if (articlePattern != null) {
            Matcher matcher = articlePattern.matcher(path);
            if (matcher.find()) {
                return path.substring(matcher.start(), matcher.end());
            }
        }
        return null;
    }

    public boolean isEditorialFlat() {
        return getId().equals(findEditorialMl3gFlat().get().getId());
    }

    public static CmsException throwUnknownEntry(String path) {
        return new CmsException(String.format("an entry is unsupported %s", path));
    }

    private String getArticleTemplate() {
        return getTemplateValue("A");
    }

    private String getTemplateValue(String name) {
        if (templates != null) {
            for (Template t : templates) {
                if (t.name.equals(name)) {
                    return t.value;
                }
            }
        }
        return null;
    }

    private static Entry match(String path, List<Entry> entries) {
        if (entries != null) {
            for (Entry entry : entries) {
                if (entry.pattern != null && entry.pattern.matcher(path).matches()) {
                    return entry;
                }
            }
        }
        return null;
    }

    private static void resolve(List<Entry> entries, List<Template> templates) {
        if (entries == null) {
            return;
        }
        for (Entry entry : entries) {
            if (entry.template != null) {
                entry.template = applyTemplates(entry.template, templates);
                entry.pattern = Pattern.compile(entry.template);
            }
            if (entry.entries != null) {
                resolve(entry.entries, templates);
            }
        }
    }

    private static String applyTemplates(String regexp, List<Template> templates) {
        String ret = regexp;
        for (Template template: templates) {
            ret = ret.replaceAll("\\$\\{" + template.name.trim() + "}", template.value);
        }
        return ret.trim();
    }

    /**
     * An initial package entry
     */
    public static class Entry implements Serializable {
        private static final long serialVersionUID = 1L;

        @XmlAttribute(name = "template")
        private String template;

        @XmlAttribute(name = "dest")
        private String dest;

        @XmlAttribute(name = "skip")
        private boolean skip;

        private EntryType type = EntryType.UNDEF;

        @XmlElement(name = "entry")
        private List<Entry> entries;

        private Pattern pattern;

        @XmlAttribute(name = "type")
        public String getEntryTypeXml() {
            return type.name();
        }

        public void setEntryTypeXml(String typeName) {
            type = EntryType.valueOf(typeName);
        }

        public EntryType getType() {
            return type;
        }

        public String getDestinationFolder() {
            return dest;
        }

        public Entry match(String path) {
            return PackageType.match(path, entries);
        }

        public boolean toSkip() {
            return skip;
        }
    }

    /**
     * A template for a better package entries visibility
     */
    public static class Template implements Serializable {
        private static final long serialVersionUID = 1L;

        @XmlAttribute(name = "name")
        private String name;

        @XmlAttribute(name = "value")
        private String value;
    }
}

