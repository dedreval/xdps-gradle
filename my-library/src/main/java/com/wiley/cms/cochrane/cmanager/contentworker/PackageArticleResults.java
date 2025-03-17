package com.wiley.cms.cochrane.cmanager.contentworker;

import com.wiley.cms.cochrane.cmanager.res.PackageType;
import com.wiley.cms.cochrane.utils.Constants;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 8/22/2021
 *
 * a object type of results for main article's content type
 * @param <A>
 */
public class PackageArticleResults<A> {

    private String basePath;
    private String path;
    private String publisherId;
    private String cdNumber;
    private int pubNumber = Constants.FIRST_PUB;

    private Object[] results = new Object[PackageType.EntryType.size()];

    public PackageArticleResults() {
    }

    public PackageArticleResults(String cdNumber) {
        this.cdNumber = cdNumber;
    }

    public String getManifestResult() {
        return (String) results[PackageType.EntryType.MANIFEST.ordinal()];
    }

    public A getArticleResult() {
        return (A) results[PackageType.EntryType.ARTICLE.ordinal()];
    }

    public A getTranslationResult() {
        return (A) results[PackageType.EntryType.TA.ordinal()];
    }

    public boolean getStatsResult() {
        return results[PackageType.EntryType.STATS.ordinal()] != null;
    }

    public void addResult(Object result, PackageType.EntryType type) {
        if (results != null && results.length > type.ordinal()) {
            results[type.ordinal()] = result;
        }
    }

    public String getCdNumber() {
        return cdNumber;
    }

    public PackageArticleResults<A> setCdNumber(String cdNumber) {
        this.cdNumber = cdNumber;
        return this;
    }

    public PackageArticleResults<A> setPubNumber(int pubNumber) {
        this.pubNumber = pubNumber;
        return this;
    }

    public String getPublisherId() {
        return publisherId;
    }

    public PackageArticleResults<A> setPublisherId(String publisherId) {
        this.publisherId = publisherId;
        return this;
    }

    public String getCurrentPath() {
        return path;
    }

    public PackageArticleResults<A> setCurrentPath(String path) {
        this.path = path;
        return this;
    }

    public String getBasePath() {
        return basePath;
    }

    public PackageArticleResults<A> setBasePath(String path) {
        this.basePath = path;
        return  this;
    }
}
