package com.wiley.cms.cochrane.cmanager.publish.generate.literatum;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.publish.util.GenerationErrorCollector;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.ProcessVO;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 2/10/2019
 */
class HWDependency {

    private List<Integer> hwRecordIds;
    private ProcessVO hwProcess;
    private PublishWrapper hwPublish;

    HWDependency(PublishWrapper publish) {
        hwPublish = publish.getPublishToAwait();
        hwRecordIds = new ArrayList<>();
    }

    void addRecordToHWPublish(Integer recordId, String cdNumber, boolean wr) {
        if (wr) {
            if (!hwPublish.hasCdNumbers()) {
                hwPublish.setCdNumbers(new HashSet<>());
            }
            hwPublish.getCdNumbers().add(cdNumber);
        } else {
            hwRecordIds.add(recordId);
        }
    }

    void saveRecordToHWPublish(GenerationErrorCollector errorCollector, Logger log) {
        if (hwRecordIds.isEmpty()) {
            return;
        }
        try {
            if (hwProcess == null) {
                hwProcess = PublishHelper.createRecordPublishProcess(
                        PubType.TYPE_SEMANTICO, hwRecordIds.toArray(new Integer[hwRecordIds.size()]));
                hwPublish.setRecordsProcessId(hwProcess.getId());
            } else {
                ProcessHelper.createIdPartsProcess(hwProcess, hwRecordIds.toArray());
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            errorCollector.addError(GenerationErrorCollector.NotificationLevel.WARN, String.format(
                    "failed to add %d record(s) to HW publishing scope", hwRecordIds.size()));
        }
        hwRecordIds.clear();
    }

    void stopHWPublishAwait() {
        hwPublish.stopToAwait();
    }
}
