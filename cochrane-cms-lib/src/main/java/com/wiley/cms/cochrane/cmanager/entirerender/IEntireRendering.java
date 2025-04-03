package com.wiley.cms.cochrane.cmanager.entirerender;

import java.util.List;

import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;

import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import org.jboss.ws.api.annotation.WebContext;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 15.03.11
 */
@Deprecated
@WebService(targetNamespace = "http://services.render.cms.wiley.com/jaws")
@SOAPBinding(style = SOAPBinding.Style.RPC)
@WebContext(contextRoot = "/CochraneCMS")
public interface IEntireRendering {
    boolean startRenderingEntireCDSR(RenderingPlan plan, List<String> recordNames) throws Exception;

    boolean startRenderingEntireCDSR(RenderingPlan plan, Integer[] recordIds, boolean withPrevious) throws Exception;

    boolean startRenderingEntireCentral(RenderingPlan plan, List<String> recordNames) throws Exception;

    boolean startRenderingEntireCentral(RenderingPlan plan, Integer[] recordIds) throws Exception;

    boolean startRenderingEntireCDSR(RenderingPlan plan) throws Exception;
}