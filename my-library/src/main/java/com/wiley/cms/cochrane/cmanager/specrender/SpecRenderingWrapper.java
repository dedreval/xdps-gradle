package com.wiley.cms.cochrane.cmanager.specrender;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.wiley.cms.cochrane.authentication.IVisit;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.entitywrapper.AbstractAction;
import com.wiley.cms.cochrane.cmanager.entitywrapper.Action;
import com.wiley.cms.cochrane.cmanager.specrender.data.ISpecRenderingStorage;
import com.wiley.cms.cochrane.cmanager.specrender.data.SpecRenderingFileVO;
import com.wiley.cms.cochrane.cmanager.specrender.data.SpecRenderingStorageFactory;
import com.wiley.cms.cochrane.cmanager.specrender.data.SpecRenderingVO;
import com.wiley.tes.util.Logger;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class SpecRenderingWrapper implements java.io.Serializable {
    private static final Logger LOG = Logger.getLogger(SpecRenderingWrapper.class);

    SpecRenderingVO vo;

    public SpecRenderingWrapper(int id) {
        vo = getResultStorage().findSpecRenderingVO(id);
        if (vo == null) {
            vo = new SpecRenderingVO();
        }
    }

    SpecRenderingWrapper(SpecRenderingVO vo) {
        this.vo = vo;
        if (vo == null) {
            this.vo = new SpecRenderingVO();
        }
    }

    public Integer getId() {
        if (vo != null) {
            return vo.getId();
        } else {
            return 0;
        }
    }

    public void setId(int id) {
        vo.setId(id);
    }

    public int getDbId() {
        if (vo != null) {
            return vo.getDbId();
        } else {
            return 0;
        }
    }

    public void setDbId(int id) {
        vo.setDbId(id);
    }

    public Date getDate() {
        if (vo != null) {
            return vo.getDate();
        } else {
            return null;
        }
    }

    public void setDate(Date date) {
        vo.setDate(date);
    }

    public boolean isSuccessful() {
        return vo != null && vo.isSuccessful();
    }

    public boolean isCompleted() {
        return vo != null && vo.isCompleted();
    }

    public List<SpecRenderingFileVO> getFiles() {
        if (vo != null) {
            return vo.getFiles();
        } else {
            return null;
        }
    }

    public Action[] getActions() {
        ArrayList<Action> actions = new ArrayList<Action>();

        actions.add(new RecreateAction());

        Action[] actionArray = new Action[actions.size()];
        actions.toArray(actionArray);

        return actionArray;
    }

    public void performAction(int action, IVisit visit) {
        switch (action) {
            case Action.CREATE_ACTION:
                new CreateAction().perform(visit);
                break;
            case Action.RECREATE_ACTION:
                new RecreateAction().perform(visit);
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private static ISpecRenderingStorage getResultStorage() {
        return SpecRenderingStorageFactory.getFactory().getInstance();
    }

    /**
     *
     */
    public class CreateAction extends AbstractAction {
        public int getId() {
            return Action.CREATE_ACTION;
        }

        public String getDisplayName() {
            return CochraneCMSProperties.getProperty("spec_rendering.action.create.name");
        }

        public void perform(IVisit visit) {
            LOG.debug("Special rendering creating started");
            SpecRenderingManager.recreateAll(vo.getDbId(), true);
        }
    }

    /**
     *
     */
    public class RecreateAction extends AbstractAction {
        public int getId() {
            return Action.RECREATE_ACTION;
        }

        public String getDisplayName() {
            return CochraneCMSProperties.getProperty("spec_rendering.action.recreate.name");
        }

        public void perform(IVisit visit) {
            LOG.debug("Special rendering recreating started");
            SpecRenderingManager.recreateAll(vo.getDbId(), true);
        }
    }

}
