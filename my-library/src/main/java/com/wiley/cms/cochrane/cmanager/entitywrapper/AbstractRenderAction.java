package com.wiley.cms.cochrane.cmanager.entitywrapper;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 13.06.2013
 */
public abstract class AbstractRenderAction extends AbstractAction {

    protected int action = Action.RENDER_HTML_ACTION;

    public int getId() {
        return action;
    }

    public String getDisplayName() {
        return action == Action.RENDER_HTML_ACTION ? "Render HTML" : "Render PDF";
    }

}
