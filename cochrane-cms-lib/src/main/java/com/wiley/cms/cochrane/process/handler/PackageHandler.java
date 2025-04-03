package com.wiley.cms.cochrane.process.handler;

import java.io.Serializable;

import com.wiley.cms.cochrane.process.BaseAcceptQueue;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessManager;
import com.wiley.cms.process.handler.NamedHandler;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 24.07.13
 * @param <M> Manager
 * @param <Q> Queue
 */
public class PackageHandler<M extends ProcessManager, Q extends BaseAcceptQueue> extends NamedHandler<M, Q>
    implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int COUNT_PARAM = 3;

    private int packId;
    private String packName;
    private String dbName;

    public PackageHandler() {
    }

    public PackageHandler(String label, int packageId, String packageName, String dbName) {

        super(label);

        setPackageId(packageId);
        setPackageName(packageName);
        setDbName(dbName);
    }

    @Override
    protected void init(String... params) throws ProcessException {

        super.init(params);

        setPackageId(params[super.getParamCount()]);
        setPackageName(params[super.getParamCount() + 1]);
        setDbName(params[super.getParamCount() + 2]);
    }

    @Override
    protected int getParamCount() {
        return super.getParamCount() + COUNT_PARAM;
    }

    @Override
    protected void buildParams(StringBuilder sb) {
        super.buildParams(sb);
        buildParams(sb, getPackageId(), getPackageName(), getDbName());
    }

    public void setPackageId(int packageId) {
        packId = packageId;
    }

    public void setPackageId(String packageId) throws ProcessException {
        setPackageId(getIntegerParam(packageId));
    }

    public int getPackageId() {
        return packId;
    }

    public void setPackageName(String packageName) {
        packName = packageName;
    }

    public String getPackageName() {
        return packName;
    }

    public void setDbName(String name) {
        dbName = name;
    }

    public String getDbName() {
        return dbName;
    }
}
