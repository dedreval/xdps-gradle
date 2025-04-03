package com.wiley.cms.cochrane.cmanager.res;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.wiley.tes.util.res.JaxbResourceFactory;
import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.ResourceIntId;
import com.wiley.tes.util.res.ResourceManager;
import com.wiley.tes.util.res.SingletonRes;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 10.10.13
 */
@XmlRootElement(name = TransAbs.RES_NAME)
public class TransAbs extends ResourceIntId implements Serializable {

    static final String RES_NAME = "root";

    private static final long serialVersionUID = 1L;

    private static final SingletonRes<TransAbs> REF = new SingletonRes<>("transabs", null);

    private Map<String, String> langMap = new HashMap<>();
    private Set<String> fopSupported = new HashSet<>();

    public static void register(ResourceManager loader) {
        loader.register(RES_NAME, JaxbResourceFactory.create(TransAbs.class, REF));
    }

    public static Res<TransAbs> get() {

        Res<TransAbs> ret = REF.getResource();
        if (!Res.valid(ret)) {

            LOG.warn("translation codes mapping hasn't been initialized yet");
            ResourceManager.instance();
            ret = REF.getResource();
        }
        return ret;
    }

    public static String getMappedLanguage(String language) {
        return REF.getResource().get().getMappedCode(language.toLowerCase());
    }

    public static boolean isMappedLanguage4Fop(String language) {
        return REF.getResource().get().is4Fop(language);
    }

    @XmlElement(name = "value")
    public void setAbsValue(AbsValue[] values) {

        Map<String, String> tmpLangMap = new HashMap<>();
        Set<String> tmpFopSupported = new HashSet<>();

        for (AbsValue val: values) {
            if (val.lang == null || val.lang.length() == 0) {
                continue;
            }

            if (val.langOut == null || val.langOut.length() == 0) {
                String langName = val.lang.toLowerCase();
                tmpLangMap.put(langName, langName);
                checkSupportedFop(val, langName, tmpFopSupported);
                continue;
            }

            String langName = val.langOut;
            String[] keys = val.lang.split(" ");
            for (String key: keys) {
                tmpLangMap.put(key.toLowerCase(), langName);
                checkSupportedFop(val, langName, tmpFopSupported);
            }
        }

        langMap = tmpLangMap;
        fopSupported = tmpFopSupported;
    }

    private void checkSupportedFop(AbsValue val, String langName, Set<String> tmpFopSupported) {
        if (val.supportFop) {
            tmpFopSupported.add(langName);
        }
    }

    public String getMappedCode(String lang) {
        return langMap.get(lang);
    }

    public boolean is4Fop(String lang) {
        return fopSupported.contains(lang);
    }

    /**
     *
     */
    public static class AbsValue {

        @XmlAttribute(name = "lang")
        private String lang;

        @XmlAttribute(name = "lang_out")
        private String langOut;

        @XmlAttribute(name = "support-fop")
        private boolean supportFop = false;
    }
}
