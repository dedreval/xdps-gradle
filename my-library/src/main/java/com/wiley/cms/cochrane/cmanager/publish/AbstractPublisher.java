package com.wiley.cms.cochrane.cmanager.publish;

import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.HWFreq;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/22/2018
 */
public abstract class AbstractPublisher implements IPublish {

    protected IResultsStorage rs;
    protected IPublishStorage ps;
    protected IRepository rps;
    protected IFlowLogger flowLogger;

    protected String tag;
    private final String exportTypeName;


    protected AbstractPublisher(String tag, String exportTypeName) {
        this.tag = tag;
        this.exportTypeName = exportTypeName;
    }

    public abstract String getDbName();

    @Override
    public IPublish setContext(IResultsStorage rs, IPublishStorage ps, IFlowLogger flowLogger) {
        this.ps = ps;
        this.rs = rs;
        this.flowLogger = flowLogger;

        rps = RepositoryFactory.getRepository();

        return this;
    }

    protected static String buildTag(String dbName, String prefix, String exportTypeName) {
        return prefix.endsWith(":")
            ? String.format("%s%s:%s", prefix, BaseType.find(dbName).get().getPubInfo(exportTypeName).getTag(), dbName)
            : prefix;
    }

    protected String getExportTypeName() {
        return exportTypeName;
    }

    public static PublishEntity checkExportEntity(PublishWrapper pw, Integer dbId, String exportFolder, boolean onGen,
                                                  IResultsStorage rs, IPublishStorage ps, IFlowLogger fl) {
        PublishEntity pe;
        IRepository rp = RepositoryFactory.getRepository();
        if (pw.isNewPublish()) {
            if (onGen) {
                pw.getPublishEntity().setFileName(pw.getNewPackageName());
            }
            pw.setPublishEntity(ps.createPublish(dbId, pw.getPublishEntity().getPublishType(), pw.getPublishEntity(),
                    HWFreq.getHWFreq(pw.getHWFrequency()).ordinal(), onGen));
            pe = checkExportFileName(pw, pw.getPublishEntity(), exportFolder,  rp, rs, fl);
        } else {
            pe = checkExportFileName(pw, rs.findPublish(pw.getId()), exportFolder, rp, rs, fl);
        }
        pw.setPublishEntity(pe);
        return pe;
    }

    protected final PublishEntity checkExportEntityEntire(PublishWrapper pw, String dbName, String exportType,
                                                          String exportFolder, boolean onGen) {
        PublishEntity pe;
        if (pw.isNewPublish()) {
            if (onGen) {
                pw.getPublishEntity().setFileName(pw.getNewPackageName());
            }
            pw.setPublishEntity(ps.createPublishEntire(dbName, exportType, pw.getPublishEntity(),
                    HWFreq.getHWFreq(pw.getHWFrequency()).ordinal(), onGen));
            pe = checkExportFileName(pw, pw.getPublishEntity(), exportFolder, rps, rs, flowLogger);
        } else {
            pe = checkExportFileName(pw, rs.findPublish(pw.getId()), exportFolder, rps, rs, flowLogger);
        }

        pw.setPublishEntity(pe);
        return pe;
    }

    private static PublishEntity checkExportFileName(PublishWrapper pw, PublishEntity pe, String exportDir,
                                                     IRepository rp, IResultsStorage rs, IFlowLogger fl) {
        String oldName = pe.getFileName();
        String newName = oldName;
        if (oldName == null || oldName.isEmpty()) {
            newName = pw.getNewPackageName();
        }
        if (pw.isDS()) {
            newName = PublishHelper.checkUUID(ILogEvent.PRODUCT_SENT_DS, newName, exportDir, pe.sent(), pw, fl, rp);

        } else if (pw.isHW()) {
            PublishHelper.checkUUID(ILogEvent.PRODUCT_SENT_HW, pw, fl);

        } else if (pw.isWOLLIT()) {
            PublishHelper.checkUUID(ILogEvent.PRODUCT_SENT_WOLLIT, pw, fl);
        }
        if (oldName != newName) {
            return rs.updatePublish(pe.getId(), newName, pe.isGenerating(), pe.isGenerated(),
                    pe.getGenerationDate(), pe.isWaiting(), pe.getFileSize());
        }
        return pe;
    }
}
