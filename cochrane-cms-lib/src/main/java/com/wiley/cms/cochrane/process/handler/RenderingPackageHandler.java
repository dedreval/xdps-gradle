package com.wiley.cms.cochrane.process.handler;

import java.io.Serializable;

import com.wiley.cms.cochrane.process.AcceptRenderQueue;
import com.wiley.cms.cochrane.process.BaseRenderingManager;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessVO;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 24.07.13
 */
public class RenderingPackageHandler extends PackageHandler<BaseRenderingManager, AcceptRenderQueue>
    implements Serializable {
    private static final long serialVersionUID = 1L;

    private int[] planIds;

    public RenderingPackageHandler() {
    }

    public RenderingPackageHandler(String label, int packageId, String packageName, String dbName, int[] planIds) {

        super(label, packageId, packageName, dbName);
        this.planIds = planIds;
    }

    @Override
    public void onExternalMessage(ProcessVO pvo, AcceptRenderQueue queue) throws ProcessException {
        queue.processMessage(this, pvo);
    }

    @Override
    protected int getParamCount() {
        return super.getParamCount() + (planIds == null ? 0 : planIds.length);
    }

    @Override
    protected void init(String... params) throws ProcessException {

        super.init(params);
        setPlanIds(super.getParamCount(), params.length - 1, params);
    }

    @Override
    protected void buildParams(StringBuilder sb) {
        super.buildParams(sb);
        for (int planId: getPlanIds()) {
            buildParams(sb, planId);
        }
    }

    private void setPlanIds(int start, int end, String... ids) {

        planIds = new int[end - start];
        for (int i = start; i < end; i++) {
            planIds[i - start] = Integer.valueOf(ids[i]);
        }
    }

    public int[] getPlanIds() {
        return planIds;
    }
}
