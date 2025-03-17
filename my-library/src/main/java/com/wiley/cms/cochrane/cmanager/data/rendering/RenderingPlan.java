package com.wiley.cms.cochrane.cmanager.data.rendering;

import java.util.HashMap;
import java.util.Map;

import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.utils.ContentLocation;

import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 8/15/2019
 */
public enum RenderingPlan {

    // do not change ordering because the ordinal is a db key
    UNKNOWN("unknown"),
    PDF_TEX("pdf_tex"),
    CD("html_cd"),
    HTML("html_diamond"),
    PDF_FOP("pdf_fop") {

        @Override
        public String getDirPath(Integer issueId, String dbName, Integer version, String cdNumber, ContentLocation cl) {
            return cl.getPathToPdf(issueId, dbName, version, cdNumber);
        }

        @Override
        public String getFilePath(Integer issueId, String dbName, Integer version, String cdNumber, String url,
                                  ContentLocation cl) {
            return getDirPath(issueId, dbName, version, cdNumber, cl) + RepositoryUtils.getLastNameByPath(url);
        }
    };

    private static final Logger LOG = Logger.getLogger(RenderingPlan.class);
    private static final Map<String, RenderingPlan> PLANS = new HashMap<>();

    public final String planName;

    static {
        RenderingPlan[] values = RenderingPlan.values();
        for (RenderingPlan plan: values) {
            PLANS.put(plan.planName, plan);
        }
    }

    RenderingPlan(String name) {
        planName = name;
    }

    public static RenderingPlan get(String planName) {
        RenderingPlan ret = PLANS.get(planName);
        return ret != null ? ret : UNKNOWN;
    }

    public static RenderingPlan get(int id) {
        try {
            return values()[id];
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return UNKNOWN;
        }
    }

    public static boolean isPdf(int planId) {
        return isPdfFOP(planId) || planId == RenderingPlan.PDF_TEX.ordinal();
    }

    public static boolean isPdfFOP(int planId) {
        return planId == RenderingPlan.PDF_FOP.ordinal();
    }

    public static boolean isHtml(int planId) {
        return planId == RenderingPlan.HTML.ordinal();
    }

    public int id() {
        return ordinal();
    }

    public String getDirPath(Integer issueId, String dbName, Integer version, String cdNumber, ContentLocation cl) {
        return FilePathCreator.getRenderedDirPath(issueId.toString(), dbName, cdNumber, this);
    }

    public String getFilePath(Integer issueId, String dbName, Integer version, String cdNumber, String url,
                              ContentLocation cl) {
        return FilePathCreator.getRenderedFilePath(issueId.toString(), dbName, cdNumber, this, url);
    }

    @Override
    public String toString() {
        return String.format("%s [%d]", planName, ordinal());
    }
}

