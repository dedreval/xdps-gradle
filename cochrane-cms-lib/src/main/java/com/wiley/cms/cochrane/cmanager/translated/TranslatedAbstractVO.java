package com.wiley.cms.cochrane.cmanager.translated;

import org.jdom.Element;

import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieResponseBuilder;

import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.TransAbs;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.process.ProcessHelper;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 31.07.12
 */
public class TranslatedAbstractVO extends ArchieEntry implements IRecord {
    private static final long serialVersionUID = 1L;

    private String sid;
    private String doi;
    private String title;
    private String englishTitle;
    private String language;
    private String originalLanguage;
    private String xmlVersion;

    public TranslatedAbstractVO(PublishedAbstractEntity pe) {
        super(pe);
        setSid(pe.getSid());
        language = pe.getLanguage();
    }

    public TranslatedAbstractVO(String name) {
        super(name);
    }

    public TranslatedAbstractVO(String name, String language) {
        super(name);
        this.language = language;
    }

    @Override
    public Element asErrorElement(ArchieResponseBuilder rb, ErrorInfo err, IFlowLogger logger) {
        return onEventLog(rb.asErrorElement(this, err), rb, logger);
    }

    @Override
    public Element asSuccessfulElement(ArchieResponseBuilder rb, IFlowLogger logger) {
        return onEventLog(rb.asSuccessfulElement(this), rb, logger);
    }

    @Override
    public PublishedAbstractEntity createWhenReadyEntity(BaseType baseType, Integer initialPackageId, Integer dbId,
                                                         Integer recId) {
        return new PublishedAbstractEntity(this, initialPackageId, dbId, recId);
    }

    @Override
    public boolean same(PublishedAbstractEntity pae) {
        return super.same(pae) && getLanguage().equals(pae.getLanguage());
    }

    public void setVersion3(boolean v3) {
        if (v3) {
            setXmlVersion("3");
        }
    }

    public boolean isVersion3() {
        return "3".equals(getXmlVersion());
    }

    public void setJats() {
        setXmlVersion("4");
    }

    public boolean isJats() {
        return "4".equals(getXmlVersion());
    }

    @Override
    public boolean isJatsTa() {
        return isJats();
    }

    @Override
    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    void setXmlVersion(String version) {
        this.xmlVersion = version;
    }

    private String getXmlVersion() {
        return this.xmlVersion;
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public String getEnglishTitle() {
        return englishTitle;
    }

    @Override
    public void setEnglishTitle(String title) {
        englishTitle = title;
    }

    @Override
    public String getLanguage() {
        return language;
    }

    public String getOriginalLanguage() {
        return originalLanguage;
    }

    public void setLanguage(String lang) {

        if (lang == null) {
            language = null;
            return;
        }
        originalLanguage = lang;
        language = getMappedLanguage(lang);
    }

    public boolean isDoiExist() {
        return doi != null;
    }

    public boolean isSidExist() {
        return sid != null && !sid.isEmpty();
    }

    public boolean isLanguageNotExist() {
        return language == null || language.isEmpty();
    }

    public static String getMappedLanguage(String language) {
        return TransAbs.get().get().getMappedCode(language.toLowerCase());
    }

    public static boolean isMappedLanguage4Fop(String language) {
        return TransAbs.get().get().is4Fop(language);
    }

    public static String getLanguages4FopAsStr(Iterable<String> languages) {
        if (languages == null) {
            return null;
        }
        TransAbs transAbs = TransAbs.get().get();
        StringBuilder retSb = null;
        String retStr = null;
        for (String lang: languages) {

            if (!transAbs.is4Fop(lang)) {
                continue;
            }

            if (retStr == null) {
                retStr = lang;

            } else if (retSb == null) {
                retSb = new StringBuilder(retStr);
                ProcessHelper.addSubParamString(lang, retSb);

            } else {
                ProcessHelper.addSubParamString(lang, retSb);
            }
        }
        return retStr == null ? null : (retSb == null ? retStr : retSb.toString());
    }

    @Override
    public String toString() {
        return super.toString() + "." + language;
    }
}
