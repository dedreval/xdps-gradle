package com.wiley.cms.process.handler;

import java.io.Serializable;
import java.net.URI;

import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessManager;
import com.wiley.cms.process.ProcessPartVO;
import com.wiley.cms.process.ProcessVO;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 05.09.13
 * @param <T> Manager
 */
public class PlanProfileHandler<T extends ProcessManager> extends CallbackHandler<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int COUNT_PARAM = 3;

    private String profile;
    private String plan;
    private String database;

    public PlanProfileHandler() {
    }

    public PlanProfileHandler(String label, String plan, String profile, URI callbackURI) {
        this(label, plan, profile, "", callbackURI);
    }

    public PlanProfileHandler(String label, String plan, String profile, String database, URI callbackURI) {
        super(label, callbackURI);
        setPlan(plan);
        setProfile(profile);
        setDatabase(database);
    }

    public PlanProfileHandler copy(String plan) {
        return new PlanProfileHandler(getName(), plan, getProfile(), getDatabase(), getCallbackURI());
    }

    public void onProcessPartMessage(ProcessPartVO processPart) {
    }

    @Override
    protected void init(String... params) throws ProcessException {

        super.init(params);

        setPlan(params[super.getParamCount()]);
        setProfile(params[super.getParamCount() + 1]);
        setDatabase(params[super.getParamCount() + 2]);
    }

    @Override
    protected int getParamCount() {
        return super.getParamCount() + COUNT_PARAM;
    }

    @Override
    protected void buildParams(StringBuilder sb) {
        super.buildParams(sb);
        buildParams(sb, getPlan(), getProfile(), getDatabase());
    }

    @Override
    protected void validate(ProcessVO pvo) throws ProcessException {
        if (plan == null) {
            throw createNullMandatoryParamError(pvo, "plan");
        }
        if (profile == null) {
            throw createNullMandatoryParamError(pvo, "profile");
        }

        if (database == null) {
            throw createNullMandatoryParamError(pvo, "database");
        }
    }

    //@Override
    //protected void onStart(ProcessVO pvo, T manager) throws ProcessException {
    //    if (plan == null) {
    //        throw createNullMandatoryParamError(pvo, "plan");
    //    }
    //    if (profile == null) {
    //        throw createNullMandatoryParamError(pvo, "profile");
    //    }
    //    super.onStart(pvo, manager);
    //}

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getDatabase() {
        return database;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getProfile() {
        return profile;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public String getPlan() {
        return plan;
    }

    public static PlanProfileHandler getProcessHandler(ProcessVO pvo) throws ProcessException {
        return ProcessHandler.castProcessHandler(pvo.getHandler(), PlanProfileHandler.class);
    }
}

