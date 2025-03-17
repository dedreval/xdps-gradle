package com.wiley.cms.qaservice.services;

import javax.xml.ws.Service;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.namespace.QName;

import com.wiley.cms.notification.service.INotificationWebService;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.render.services.IProvideRendering;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Pool;
import org.cochrane.archie.service.Publishing;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.render.services.IProvideSinglePdfRendering;
import com.wiley.cms.render.services.IRenderingProvider;

import static com.wiley.cms.cochrane.cmanager.contentworker.RevmanPackage.SEND_TO_COCHRANE_BY_SFTP;

/**
 * A web service proxies constructor.
 * Proxies instances are retrieved from a proxies pool. In case the pool is empty the new instance of
 * the required proxy will be obtained. All retrieved instances should be released bask to the pool using
 * the static method {@link #releaseServiceProxy(Object, Class)}.
 * The pool is used to avoid the memory leak occurs in case huge amount of service proxies were obtained.
 * The problem is reproduced under following JBoss versions: 4.2.1 GA, 4.2.2 GA, 4.2.3 GA.
 *
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 16-May-2008
 */
public class WebServiceUtils {

    public static final String PUBLISHING_SERVICE = "PublishingService";

    private static final Logger LOG = Logger.getLogger(WebServiceUtils.class);

    private static final Object LOCKER = new Object();
    private static final Object LOCKER_QA = new Object();
    private static final Object LOCKER_RND = new Object();
    private static final Object LOCKER_PDF_RND = new Object();
    private static final Object LOCKER_RENDER = new Object();
    private static final Object LOCKER_PUB = new Object();
    private static final Object LOCKER_NOIF = new Object();

    private static final String WSDL_POSTFIX = "?wsdl";

    private static final Map<Class<?>, Pool<?>> PROXIES_POOL = new ConcurrentHashMap<Class<?>, Pool<?>>();

    private static Service serviceQa;
    private static Service serviceProviderQa;
    private static Service serviceRnd;
    private static Service serviceProviderRender;
    private static Service serviceSinglePdfRnd;
    private static Service servicePublish;
    private static Service serviceNotification;

    private WebServiceUtils() {
    }

    public static String buildResponse(Object port) {
        java.util.Map<String, Object> responseContext = ((javax.xml.ws.BindingProvider) port).getResponseContext();
        if (responseContext == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String key: responseContext.keySet()) {
            sb.append(key).append("=").append(responseContext.get(key)).append("\n");
        }
        return sb.toString();
    }

    public static IProvideQa getProvideQa() throws MalformedURLException {
        synchronized (LOCKER) {
            if (serviceQa == null) {

                URL wsdlLocation = new URL(CochraneCMSProperties.getProperty(
                    CochraneCMSPropertyNames.QAS_SERVICE_URL) + "IProvideQa?wsdl");
                QName qname = new QName("http://services.qaservice.cms.wiley.com/jaws", "ProvideQaImplService");
                serviceQa = Service.create(wsdlLocation, qname);
            }

            return getServiceProxy(serviceQa, IProvideQa.class);
        }
    }

    public static IQaProvider getQaProvider() throws MalformedURLException {
        synchronized (LOCKER_QA) {
            if (serviceProviderQa == null) {
                URL wsdlLocation = new URL(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.QAS_SERVICE_URL)
                    + "IQaProvider?wsdl");
                QName qname = new QName(IQaProvider.TARGET_NAMESPACE, "QaProviderService");
                serviceProviderQa = Service.create(wsdlLocation, qname);
            }
            return getServiceProxy(serviceProviderQa, IQaProvider.class);
        }
    }

    public static IRenderingProvider getRenderingProvider() throws MalformedURLException {
        synchronized (LOCKER_RENDER) {
            if (serviceProviderRender == null) {
                URL wsdlLocation = new URL(CochraneCMSProperties.getProperty(
                    CochraneCMSPropertyNames.RENDERER_SERVICE_URL) + "IRenderingProvider?wsdl");
                QName qname = new QName(IRenderingProvider.TARGET_NAMESPACE, "RenderingProviderService");
                serviceProviderRender = Service.create(wsdlLocation, qname);
            }
            return getServiceProxy(serviceProviderRender, IRenderingProvider.class);
        }
    }

    public static IProvideRendering getProvideRendering() throws MalformedURLException {
        synchronized (LOCKER_RND) {
            if (serviceRnd == null) {

                URL wsdlLocation = new URL(
                        CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.RENDERER_SERVICE_URL)
                                + "IProvideRendering?wsdl");
                QName qname = new QName("http://services.render.cms.wiley.com/jaws", "ProvideRenderingImplService");
                serviceRnd = Service.create(wsdlLocation, qname);
            }
            return getServiceProxy(serviceRnd, IProvideRendering.class);
        }
    }

    public static IProvideSinglePdfRendering getProvideSinglePdfRendering() throws MalformedURLException {
        synchronized (LOCKER_PDF_RND) {
            if (serviceSinglePdfRnd == null) {
                URL wsdlLocation = new URL(CochraneCMSProperties.getProperty(
                        CochraneCMSPropertyNames.RENDERER_SERVICE_URL)
                        + "IProvideSinglePdfRendering?wsdl");
                QName qname = new QName("http://services.singlepdfrender.cms.wiley.com/jaws",
                        "ProvideSinglePdfRenderingImplService");
                serviceSinglePdfRnd = Service.create(wsdlLocation, qname);
            }
            return getServiceProxy(serviceSinglePdfRnd, IProvideSinglePdfRendering.class);
        }
    }

    public static Publishing getPublishing() throws MalformedURLException {
        synchronized (LOCKER_PUB) {
            if (servicePublish == null) {
                URL wsdlLocation = new URL(CochraneCMSPropertyNames.getArchieDownloadService() + WSDL_POSTFIX);
                QName qname = new QName(Publishing.TARGET_NAMESPACE, PUBLISHING_SERVICE);
                servicePublish = Service.create(wsdlLocation, qname);
            }
            return getServiceProxy(servicePublish, Publishing.class);
        }
    }

    public static INotificationWebService getNotification() throws MalformedURLException {
        synchronized (LOCKER_NOIF) {
            if (serviceNotification == null) {
                QName qname = new QName(INotificationWebService.TARGET_NS, "newNotification");
                serviceNotification = createService(CochraneCMSPropertyNames.getNotificationServiceUrl(), qname);
            }
            return getServiceProxy(serviceNotification, INotificationWebService.class);
        }
    }

    public static String getLinkByServiceName(String name, String operation) {
        String ret = "undefined";

        if (SEND_TO_COCHRANE_BY_SFTP.equalsIgnoreCase(operation)) {
            ret = "";
        } else if (PUBLISHING_SERVICE.equals(name)) {
            ret = CochraneCMSPropertyNames.getArchieDownloadService() + WSDL_POSTFIX;
        }

        return ret;
    }

    /**
     * Creates Service object using specified url to wsdl file and service name.
     * If the url doesn't point to the resources directory, the '?wsdl' string
     * will be added to the end of the url string.
     * @param urlStr url to wsdl file represented as string
     * @param qname service name
     * @return Service object
     * @throws MalformedURLException if the url string is invalid
     */
    private static Service createService(String urlStr, QName qname) throws MalformedURLException {
        String tmpUrlStr = (urlStr.contains(CochraneCMSPropertyNames.getCochraneResourcesRoot()))
                ? urlStr
                : urlStr + WSDL_POSTFIX;
        URL wsdlLocation = new URL(tmpUrlStr);
        return Service.create(wsdlLocation, qname);
    }

    /**
     * Retrieves from the pool or obtained the new instance of the service proxy in case the pool is empty.
     * @param service service provider
     * @param clazz web service interface class
     * @param <T> web service interface
     * @return
     */
    @SuppressWarnings("unchecked")
    private static <T> T getServiceProxy(Service service, Class<T> clazz) {
        Pool<?> pool = PROXIES_POOL.get(clazz);
        if (pool == null) {
            pool = new Pool<T>(getPoolFactory(service, clazz));
            PROXIES_POOL.put(clazz, pool);
        }

        T ret = (T) pool.get();
        //T ret = service.getPort(clazz);
        ProcessHelper.setWSTimeout(ret);
        return ret;
    }

    private static <T> Pool.Factory<T> getPoolFactory(final Service service,
                                                      final Class<T> clazz) {
        return new Pool.Factory<T>() {

            @Override
            public T create() {
                return service.getPort(clazz);
            }
        };
    }

    /**
     * Put the proxy object back to the pool for later usage.
     * @param proxy proxy object
     * @param clazz web service interface class related to the proxy
     */
    @SuppressWarnings("unchecked")
    public static void releaseServiceProxy(Object proxy, Class<?> clazz) {
        if (proxy == null) {
            return;
        }

        Pool<Object> pool = (Pool<Object>) PROXIES_POOL.get(clazz);
        boolean matches = false;
        Class[] interfaces = proxy.getClass().getInterfaces();
        for (Class interfac : interfaces) {
            if (interfac.equals(clazz)) {
                matches = true;
                break;
            }
        }

        if (matches && pool != null) {
            pool.release(proxy);
        } else {
            LOG.error("Failed to release service proxy for class " + clazz.toString()
                    + ", the proxy wasn't obtained via service proxies pool");
        }
    }

    public static void resetNotification() {
        synchronized (LOCKER_NOIF) {
            if (serviceNotification != null) {
                PROXIES_POOL.remove(INotificationWebService.class);
                serviceNotification = null;
            }
        }
    }

    public static void reset() {
        resetNotification();

        synchronized (LOCKER_PUB) {
            if (servicePublish != null) {
                PROXIES_POOL.remove(Publishing.class);
                servicePublish = null;
            }
        }
    }
}
