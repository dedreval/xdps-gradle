package com.wiley.cms.cochrane.process;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import com.wiley.cms.process.IModelController;
import com.wiley.cms.process.entity.AbstractModelController;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 17.01.12
 */
@Stateless
@Local(IModelController.class)
public class ModelController extends AbstractModelController {
    protected static final Logger LOG = Logger.getLogger(ModelController.class);

    @PersistenceContext
    protected EntityManager em;

    @Override
    public EntityManager getManager() {
        return em;
    }
}
