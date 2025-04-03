package com.wiley.cms.cochrane.cmanager.export.process;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.MessageParameters;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.db.DbStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.db.DbVO;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.ebch.process.GenProcessor;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.export.data.ExportEntity;
import com.wiley.cms.cochrane.cmanager.export.data.ExportVO;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;
import org.apache.commons.lang.StringUtils;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class ExportProcessor extends GenProcessor<ExportParameters> {

    protected Exporter exporter;
    protected Date date = new Date();
    protected String filePath;
    protected Set<Integer> items;
    protected ExportVO exportVO;
    protected Logger log;

    public ExportProcessor(ExportParameters params, Set<Integer> items) throws CmsException {
        super(params);
        this.params = params;
        this.items = items;

        setFilePath(generateFilePath());
        init();
    }

    protected void init() throws CmsException {
        exporter = new Exporter(params, items, filePath);
        log = Logger.getLogger(ExportProcessor.class);
    }

    public void run() {
        try {
            createDbTrack();
            exporter.run();
        } catch (CmsException e) {
            exporter.getErrors().get(StringUtils.EMPTY).append(e.getMessage());
        }
        complete(exporter.getErrors());
    }

    private void createDbTrack() {

        exportVO = new ExportVO();
        exportVO.setDate(date);
        exportVO.setFilePath(filePath);
        exportVO.setState(ExportEntity.IN_PROGRESS);
        exportVO.setItemAmount(items.size());
        exportVO.setUser(params.getUserName());
        exportVO.setClDbId(params.getDbId());
        exportVO.setDbName(params.getDbName());

        exportVO.setId(AbstractManager.getExportStorage().create(exportVO));
    }

    private void complete(Map<String, StringBuilder> errsByNotifId) {
        updateDb();
        completeLog(getAllErrors(errsByNotifId));
        sendNotification(errsByNotifId);
    }

    public static String getAllErrors(Map<String, StringBuilder> errsByNotifId) {
        StringBuilder errs = new StringBuilder();
        errsByNotifId.forEach((key, value) -> errs.append(value));
        return errs.toString();
    }

    protected void updateDb() {

        List<ExportVO> exports = exporter.getCompletedExports(exportVO);
        if (exports.isEmpty()) {
            log.warn("no exports have been completed...");
            return;
        }

        for (ExportVO vo: exports) {
            if (vo.isExists())    {
                AbstractManager.getExportStorage().setCompleted(vo.getId(), vo.getState(), vo.getFilePath());
            } else {
                AbstractManager.getExportStorage().create(vo);
            }
        }
    }

    private void completeLog(String errs) {
        boolean hasErrs = StringUtils.isNotEmpty(errs);
        String comments = (hasErrs
                ? "Export completed with errors: " + errs
                : "Export completed successfully for db [" + params.getDbName() + "]");

        AbstractManager.getActivityLogService().info(ActivityLogEntity.EntityLevel.EXPORT, ILogEvent.EXPORT_COMPLETED,
                exportVO.getId(), date.getTime() + Extensions.ZIP, params.getUserName(), comments);
        log.debug(comments);
    }

    private void sendNotification(Map<String, StringBuilder> errsByNotifId) {
        StringBuilder commonErrs = errsByNotifId.remove(StringUtils.EMPTY);
        StringBuilder commonExport = errsByNotifId.remove(MessageSender.EXPORT_COMPLETED_ID);
        StringBuilder ml3gExport = errsByNotifId.remove(MessageSender.EXPORT_3G_COMPLETED_ID);

        boolean hasErrs = commonErrs.length() > 0;

        Map<String, String> map = new HashMap<>();
        if (commonExport != null) {
            commonExport.append(commonErrs);
            errsByNotifId.forEach((key, value) -> commonExport.append(value));
            map.put(MessageParameters.MSG, getMessage(commonExport.toString(), hasErrs || !errsByNotifId.isEmpty()));
            MessageSender.sendMessage(MessageSender.EXPORT_COMPLETED_ID, map);
        }
        if (ml3gExport != null) {
            ml3gExport.append(commonErrs);
            if (commonExport == null) {
                errsByNotifId.forEach((key, value) -> ml3gExport.append(value));
            }
            map.clear();
            map.put(MessageParameters.MSG, getMessage(ml3gExport.toString(), hasErrs));
            MessageSender.sendMessage(MessageSender.EXPORT_3G_COMPLETED_ID, map);
        }
    }

    protected String getMessage(String errs, boolean hasErrs) {
        DbVO dbVO = AbstractManager.getResultDbStorage().getDbVO(params.getDbId());
        IssueVO issueVO = AbstractManager.getIssueStorage().getIssueVO(dbVO.getIssueId());

        String path = exporter.buildExportPaths();

        HashMap<String, String> map = new HashMap<>();
        map.put(MessageParameters.NUMB, String.valueOf(issueVO.getNumber()));
        map.put(MessageParameters.YEAR, String.valueOf(issueVO.getYear()));
        map.put(MessageParameters.NAME, params.getDbName());
        map.put(MessageParameters.PATH, path);
        map.put(MessageParameters.ERRS, errs);

        return (hasErrs
                ? CochraneCMSPropertyNames.getExportCompletedWithErrors(map)
                : CochraneCMSPropertyNames.getExportCompletedSuccessfully(map));
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    protected String generateFilePath() {
        DbVO db = DbStorageFactory.getFactory().getInstance().getDbVO(params.getDbId());
        return FilePathBuilder.getPathToIssueExport(db.getIssueId(), db.getTitle()) + date.getTime() + Extensions.ZIP;
    }
}
