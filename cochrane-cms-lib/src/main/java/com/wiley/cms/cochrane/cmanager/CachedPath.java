package com.wiley.cms.cochrane.cmanager;

import com.wiley.cms.cochrane.cmanager.res.PathType;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 1/18/2020
 */
public enum CachedPath {

    ENTIRE_ROOT("entire-root"),
    ENTIRE_EXPORT("entire-export"),
    ENTIRE_REPORT("entire-report"),
    ENTIRE_PUBLISH("entire-publish"),
    ENTIRE_SRC("entire-src"),
    ENTIRE_SRC_TR("entire-src-tr"),
    ENTIRE_RECORD("entire-record"),
    ENTIRE_TA_ROOT("entire-ta-root"),
    ENTIRE_TA_LANG("entire-ta-lang"),
    ENTIRE_TA_JATS("entire-ta-jats"),
    ENTIRE_TA_ML21("entire-ta-wml21"),
    ENTIRE_TA_ML3G("entire-ta-wml3g"),
    ENTIRE_REVMAN("entire-revman"),
    ENTIRE_REVMAN_GROUP("entire-revman-group"),
    ENTIRE_REVMAN_SRC("entire-revman-src"),
    ENTIRE_REVMAN_TOPIC("entire-revman-topic"),
    ENTIRE_JATS("entire-jats"),
    ENTIRE_PDF("entire-pdf"),
    ENTIRE_PDF_FOP("entire-pdf-fop"),
    ENTIRE_HTML("entire-html"),
    ENTIRE_ML3G("entire-ml3g"),
    ENTIRE_ML3G_TMP("entire-ml3g-tmp"),

    ISSUE_ROOT("issue-root"),
    ISSUE_DB("issue-db"),
    ISSUE_EXPORT("issue-export"),
    ISSUE_PUBLISH("issue-publish"),
    ISSUE_PUBLISH_WHENREADY("issue-publish-wr"),
    ISSUE_ML21("issue-wml21"),
    ISSUE_ML3G("issue-ml3g"),
    ISSUE_ML3G_WOL("issue-ml3g-wol"),
    ISSUE_ML3G_TMP("issue-ml3g-tmp"),
    ISSUE_HTML("issue-html"),
    ISSUE_PDF("issue-pdf"),
    ISSUE_PDF_FOP("issue-pdf-fop"),
    ISSUE_COPY("issue-copy"),
    ISSUE_COPY_SRC("issue-copy-src"),
    ISSUE_COPY_PDF("issue-copy-pdf"),
    ISSUE_COPY_PDF_FOP("issue-copy-pdf-fop"),
    ISSUE_COPY_HTML("issue-copy-html"),
    ISSUE_COPY_ML3G("issue-copy-ml3g"),
    ISSUE_COPY_JATS("issue-copy-jats"),
    ISSUE_COPY_PREVIOUS("issue-copy-previous"),
    ISSUE_SRC_TR("issue-src-tr"),
    ISSUE_RECORD("issue-record"),
    ISSUE_TA_COPY("issue-ta-copy"),
    ISSUE_TA_JATS_COPY("issue-ta-jats-copy"),
    ISSUE_TA_ML3G_COPY("issue-ta-ml3g-copy"),
    ISSUE_PACKAGE("issue-package"),
    ISSUE_PACKAGE_TA("issue-package-ta"),
    ISSUE_REVMAN("issue-revman"),
    ISSUE_REVMAN_GROUP("issue-revman-group"),
    ISSUE_REVMAN_SRC("issue-revman-src"),
    ISSUE_REVMAN_TA("issue-revman-ta"),
    ISSUE_JATS("issue-jats"),
    ISSUE_JATS_GROUP("issue-jats-group"),
    ISSUE_JATS_TA("issue-jats-ta"),
    ISSUE_REVMAN_COPY("issue-revman-copy"),
    ISSUE_REVMAN_COPY_SRC("issue-revman-copy-src"),
    ISSUE_INPUT_GROUP("issue-input-group"),
    ISSUE_INPUT_SRC("issue-input-src"),
    ISSUE_PREVIOUS("issue-previous"),
    ISSUE_PREVIOUS_ML3G("issue-previous-ml3g"),
    ISSUE_PREVIOUS_ML3G_TMP("issue-previous-ml3g-tmp"),
    ISSUE_PREVIOUS_HTML("issue-previous-html"),
    ISSUE_PREVIOUS_PDF("issue-previous-pdf"),
    ISSUE_PREVIOUS_PDF_FOP("issue-previous-pdf-fop"),
    ISSUE_PREVIOUS_RECORD("issue-previous-record"),
    ISSUE_PREVIOUS_REVMAN_SRC("issue-previous-revman-src"),
    ISSUE_ARCHIVE("issue-archive"),
    //ISSUE_PREVIOUS_TA("issue-previous-ta"),
    PREVIOUS_CDSR_ROOT("previous-root"),
    PREVIOUS_CDSR("previous"),
    PREVIOUS_CDSR_SRC("previous-src"),
    PREVIOUS_CDSR_HTML("previous-html"),
    PREVIOUS_CDSR_PDF("previous-pdf"),
    PREVIOUS_CDSR_PDF_FOP("previous-pdf-fop"),
    PREVIOUS_CDSR_ML3G("previous-ml3g"),
    PREVIOUS_CDSR_ML3G_TMP("previous-ml3g-tmp"),
    PREVIOUS_RECORD("previous-record"),
    PREVIOUS_REVMAN("previous-revman"),
    PREVIOUS_REVMAN_SRC("previous-revman-src"),
    PREVIOUS_JATS("previous-jats"),
    PREVIOUS_TA_ROOT("previous-ta-root"),
    PREVIOUS_TA("previous-ta"),
    PREVIOUS_TA_LANG("previous-ta-lang"),
    PREVIOUS_TA_ML21("previous-ta-wml21"),
    PREVIOUS_TA_JATS("previous-ta-jats"),
    PREVIOUS_TA_ML3G("previous-ta-wml3g");

    final Res<PathType> path;

    CachedPath(String sid) {
        path = PathType.get(sid);
    }

    public boolean containsPaths(String pathValue) {
        return path.get().hasCachedPath(pathValue);
    }

    public void clear() {
        path.get().fresh(false);
    }

    public int getExpired() {
        return path.get().getExpiration();
    }

    public int estimateSize() {
        return path.get().countNodes();
    }

    int getPathPosition(String positionKey) {
        return path.get().getCalculatedPosition(positionKey);
    }

    String getLastPathKeyword() {
        return path.get().getLastCalculatedKeyword();
    }

    String getPath(String[] keySet, Object... elements) {
        return path.get().getCachedPath(keySet, elements);
    }

    void freshCachedPath(String[] keySet, Object... elements) {
        path.get().freshCachedPath(keySet, elements);
    }
}

