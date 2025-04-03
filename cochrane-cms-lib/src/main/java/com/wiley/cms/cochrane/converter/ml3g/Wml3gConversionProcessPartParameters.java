package com.wiley.cms.cochrane.converter.ml3g;

import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.process.ProcessHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 08.09.2014
 */
public class Wml3gConversionProcessPartParameters {

    private final IssueVO issueVO;
    private final ContentLocation contLoc;
    private final int version;
    private final String[] procEntities;

    public Wml3gConversionProcessPartParameters(IssueVO issueVO,
                                                ContentLocation contLoc,
                                                int version,
                                                String[] procEntities) {
        this.issueVO = issueVO;
        this.contLoc = contLoc;
        this.version = version;
        this.procEntities = procEntities;
    }

    public Wml3gConversionProcessPartParameters(IssueVO issueVO,
                                                ContentLocation contLoc,
                                                int version,
                                                List<?> procEntities) {
        this.issueVO = issueVO;
        this.contLoc = contLoc;
        this.version = version;
        this.procEntities = getProcessedEntities(procEntities);
    }

    private String[]  getProcessedEntities(List<?> procEntitiesList) {
        String[] procEntityArr = new String[procEntitiesList.size()];
        for (int i = 0; i < procEntitiesList.size(); i++) {
            procEntityArr[i] = procEntitiesList.get(i).toString();
        }
        return procEntityArr;
    }

    public String getParameters() {
        HashMap<String, String> params = new HashMap<String, String>(Parameter.values().length);
        params.put(Parameter.ISSUE_ID.name(), String.valueOf(issueVO.getId()));
        params.put(Parameter.ISSUE_YEAR.name(), String.valueOf(issueVO.getYear()));
        params.put(Parameter.ISSUE_NUMB.name(), String.valueOf(issueVO.getNumber()));
        params.put(Parameter.CONT_LOC.name(), contLoc.name());
        params.put(Parameter.VERSION.name(), String.valueOf(version));
        params.put(Parameter.PROC_ENTTIES.name(), ProcessHelper.buildSubParametersString(procEntities));

        return ProcessHelper.buildParametersString(params);
    }

    public IssueVO getIssueVO() {
        return issueVO;
    }

    public ContentLocation getContentLocation() {
        return contLoc;
    }

    public int getVersion() {
        return version;
    }

    public String[] getProcEntities() {
        return procEntities;
    }

    public static Wml3gConversionProcessPartParameters parseParameters(
            Map<String, String> params) throws Exception {
        try {
            IssueVO issueVO = new IssueVO(Integer.parseInt(params.get(Parameter.ISSUE_ID.name())),
                    Integer.parseInt(params.get(Parameter.ISSUE_YEAR.name())),
                    Integer.parseInt(params.get(Parameter.ISSUE_NUMB.name())),
                    null);
            ContentLocation contLoc = ContentLocation.valueOf(params.get(Parameter.CONT_LOC.name()));
            int version = Integer.parseInt(params.get(Parameter.VERSION.name()));
            String[] procEntities = ProcessHelper.parseSubParamString(params.get(Parameter.PROC_ENTTIES.name()));

            return new Wml3gConversionProcessPartParameters(issueVO, contLoc, version, procEntities);
        } catch (Exception e) {
            String paramStr = ProcessHelper.buildParametersString(params);
            throw new Exception(String.format("Failed to parse process part parameters {%s}, %s", paramStr, e));
        }
    }

    /**
     *
     */
    enum Parameter {
        ISSUE_ID, ISSUE_YEAR, ISSUE_NUMB, CONT_LOC, VERSION, PROC_ENTTIES
    }
}
