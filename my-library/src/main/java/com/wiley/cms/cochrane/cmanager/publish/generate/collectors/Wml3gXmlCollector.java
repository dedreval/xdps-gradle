package com.wiley.cms.cochrane.cmanager.publish.generate.collectors;

import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArticleContentCollector;
import com.wiley.cms.cochrane.utils.ContentLocation;
import org.apache.commons.lang.StringUtils;

import java.util.Arrays;
import java.util.List;

import static com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames.getCcaDbName;
import static com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames.getCentralDbName;
import static com.wiley.cms.cochrane.cmanager.FilePathBuilder.ML3G.getPathToEntireMl3gRecord;
import static com.wiley.cms.cochrane.cmanager.FilePathBuilder.ML3G.getPathToEntireMl3gRecordByPostfix;
import static com.wiley.cms.cochrane.cmanager.FilePathBuilder.ML3G.getPathToMl3gRecord;
import static com.wiley.cms.cochrane.cmanager.FilePathBuilder.ML3G.getPathToMl3gRecordByPostfix;
import static com.wiley.cms.cochrane.cmanager.FilePathBuilder.ML3G.getPathToPreviousMl3gRecord;
import static com.wiley.cms.cochrane.cmanager.FilePathBuilder.ML3G.getPathToPreviousMl3gRecordByPostfix;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 11.10.2018
 */
@Deprecated
public class Wml3gXmlCollector implements ArticleContentCollector<RecordWrapper> {
    private final ContentLocation contentLocation;
    private final String postfix;
    private final int issueId;

    public Wml3gXmlCollector(ContentLocation contentLocation, String postfix, int issueId) {
        this.contentLocation = contentLocation;
        this.postfix = postfix;
        this.issueId = issueId;
    }

    @Override
    public List<String> apply(RecordWrapper articleMd) {
        boolean centralArticle = getCentralDbName().equals(articleMd.getDbName());
        boolean ccaArticle = getCcaDbName().equals(articleMd.getDbName());
        boolean withPostfix = withPostfix();
        String path;
        if (contentLocation == ContentLocation.ISSUE) {
            if (ccaArticle) {
                path = articleMd.getRecordPath();
            } else {
                path = withPostfix
                        ? getPathToMl3gRecordByPostfix(issueId, articleMd.getDbName(), articleMd.getName(), postfix)
                        : getPathToMl3gRecord(issueId, articleMd.getDbName(), articleMd.getName(), centralArticle);
            }
        } else if (contentLocation == ContentLocation.PREVIOUS) {
            int articleVersion = articleMd.getVersions().getVersion();
            path = withPostfix
                    ? getPathToPreviousMl3gRecordByPostfix(articleVersion, articleMd.getName(), postfix)
                    : getPathToPreviousMl3gRecord(articleVersion, articleMd.getName());
        } else {
            if (ccaArticle) {
                path = articleMd.getRecordPath();
            } else {
                path = withPostfix
                        ? getPathToEntireMl3gRecordByPostfix(articleMd.getDbName(), articleMd.getName(), postfix)
                        : getPathToEntireMl3gRecord(articleMd.getDbName(), articleMd.getName(), centralArticle);
            }
        }
        return Arrays.asList(path);
    }

    private boolean withPostfix() {
        return StringUtils.isNotEmpty(postfix);
    }
}
