package com.wiley.tes.util.res;

//import org.codehaus.jackson.map.ObjectMapper;
//import org.codehaus.jackson.map.ObjectWriter;
//import org.codehaus.jackson.map.SerializationConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.w3c.dom.Node;


/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 29.03.13
 *
 * @param <R> Resource
 */
public class JacksonResourceFactory<R extends Resource> implements IResourceFactory {

    private final Class<R> mainClass;

    private ObjectWriter writer;
    private ObjectMapper mapper;

    private IResourceContainer<? super R> publisher;


    public JacksonResourceFactory(Class<R> resClass) {
        this(resClass, null, null);
    }

    public JacksonResourceFactory(Class<R> resClass, Class viewCLass, IResourceContainer<? super R> pub) {

        mainClass = resClass;
        publisher = pub;

        mapper = new ObjectMapper();
//        mapper.configure(SerializationConfig.Feature.DEFAULT_VIEW_INCLUSION, false);
//        mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        writer = mapper.writerWithView(viewCLass);
    }

    public static <R extends Resource> JacksonResourceFactory<R> create(Class<R> resClass, Class viewCLass) {
        return new JacksonResourceFactory<>(resClass, viewCLass, null);
    }


    public static <R extends Resource> JacksonResourceFactory<R> create(Class<R> resClass, Class viewCLass,
        IResourceContainer<? super R> publisher) {

        return new JacksonResourceFactory<>(resClass, viewCLass, publisher);
    }


    public IResourceContainer getResourceContainer() {
        return publisher;
    }

    public R createResource(Node node) throws Exception {
        throw new UnsupportedOperationException("creation JSON from XML is not supported for now");
    }

    public R createResource(String str) throws Exception {

        R res = mapper.readValue(str, mainClass);
        return publishResource(res);
    }

    private R publishResource(R res) throws Exception {
        if (res == null) {
            throw new Exception("JaxbResFactory: cannot create instance: " + mainClass.getName());
        }

        if (publisher != null) {
            publisher.publish(res);
        }

        return res;
    }

    public void saveResource(Resource res, String fileName) throws Exception {
        throw new UnsupportedOperationException("saving JSON is not supported for now");
    }

    public String convertResourceToString(Resource res) throws Exception {
        return writer.writeValueAsString(res);
    }
}

