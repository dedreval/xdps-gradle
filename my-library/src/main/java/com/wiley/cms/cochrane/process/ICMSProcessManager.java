package com.wiley.cms.cochrane.process;

import java.util.List;

import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.process.ExternalProcess;
import com.wiley.cms.process.IProcessManager;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.task.TaskVO;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 7/9/2018
 */
public interface ICMSProcessManager extends IProcessManager {
    int PROC_TYPE_RM_TO_WML21_SELECTIVE = 101;
    int PROC_TYPE_RENDER_SELECTIVE = 103;
    int PROC_TYPE_ML21_TO_ML3G_SELECTIVE = 104;

    @Deprecated
    int PROC_TYPE_COPY_TO_ML_SELECTIVE = 105;

    int PROC_TYPE_CLEAR_DB = 106;
    int PROC_TYPE_TERM2NUM1 = 107;
    int PROC_TYPE_ML3G_MESH_UPDATE_SELECTIVE = 110;
    int PROC_TYPE_RENDER_FOP = 112;
    int PROC_TYPE_UPLOAD_EDI = 113;
    int PROC_TYPE_UPLOAD_CDSR = 114;
    int PROC_TYPE_UPLOAD_CDSR_MESH = 115;
    int PROC_TYPE_IMPORT_JATS = 116;
    int PROC_TYPE_UPLOAD_CDSR_MLG = 117;
    int PROC_TYPE_SEND_TO_PUBLISH = 120;
    int PROC_TYPE_CONVERT_JATS = 121;
    int PROC_TYPE_UPLOAD_CCA = 122;
    Integer PROC_TYPE_SEND_TO_PUBLISH_DS = 123;
    Integer PROC_TYPE_UPLOAD_CENTRAL = 124;
    Integer PROC_TYPE_UPLOAD_CENTRAL_QAS = 1018;

    String LABEL_TERM2NUM = "Term2Num";
    String LABEL_WML3G_MESH_UPDATE = "Ml3gMeshUpdateSelective";

    TaskVO createProcessTask(int processId, String name, long delay) throws ProcessException;

    TaskVO findProcessTask(String taskName, String partUri) throws ProcessException;

    List<TaskVO> findProcessTasks(String taskName);

    ExternalProcess findFailedPackageProcess(int dfId);

    List<ProcessVO> findPackageProcesses(String processLabel, int dfId);

    List<ProcessVO> findActiveContentDbProcesses(int issue, int type);

    // this method is used only to support old publishing with a process
    void deletePublishProcess(int processId);


    void resendCentralQAService(int dfId, List<RecordEntity> records);
}
