package com.wiley.cms.cochrane.medlinedownloader;

import java.time.LocalDate;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.Action;
import com.wiley.cms.cochrane.cmanager.entitywrapper.IssueWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.process.task.IScheduledTask;
import com.wiley.cms.process.task.ITaskExecutor;
import com.wiley.cms.process.task.TaskVO;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 2/6/2018
 */
public class MonthlyMeshtermExecutor implements ITaskExecutor, IScheduledTask {
    private static final Logger LOG = Logger.getLogger(MonthlyMeshtermExecutor.class);
    private static final Res<Property> MESH_UP_START = Property.get("cochrane.meshterm-updater.monthly-pattern");

    public boolean execute(TaskVO task) throws Exception {

        LocalDate ld = CmsUtils.getCochraneDownloaderDate();
        int year = ld.getYear();
        int month = ld.getMonthValue();
        IssueEntity ie;
        try {
            ie = ResultStorageFactory.getFactory().getInstance().findOpenIssueEntity(year, month);
        } catch (Exception e) {
            LOG.error(e.getMessage());
            return true;
        }

        if (ie.isMeshtermsDownloading()) {
            LOG.warn(String.format("MeSH terms is already downloading into issue %d-%d", year, month));

        } else if (!ie.isMeshtermsDownloaded()) {
            new IssueWrapper(ie.getId()).performAction(Action.DOWNLOAD_MESHTERMS_ACTION, null);

        } else {
            LOG.warn(String.format("MeSH terms is already downloaded into issue %d-%d", year, month));
        }
        return true;
    }

    @Override
    public String getScheduledTemplate() {
        return MESH_UP_START.get().getValue();
    }
}
