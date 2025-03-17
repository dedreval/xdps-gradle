package com.wiley.tes.util.res;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;

import org.w3c.dom.Node;

import com.wiley.tes.util.Logger;

/**
 * @author Olga Soletskaya
 * Date: 03.06.12
 */
public class JAXBHelper {

    private static final Logger LOG = Logger.getLogger(JAXBHelper.class);

    private JAXBHelper() {
    }

    public static <T> T load(String fileName, Class<T> mainClass, Class... additionalClasses) {

        File file = new File(fileName);

        if (file.exists()) {
            try {
                Class[] jaxbClasses = new Class[additionalClasses.length + 1];

                jaxbClasses[0] = mainClass;
                System.arraycopy(additionalClasses, 0, jaxbClasses, 1, additionalClasses.length);

                JAXBContext jaxbContext = JAXBContext.newInstance(jaxbClasses);
                Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

                return mainClass.cast(unmarshaller.unmarshal(file));
            } catch (JAXBException e) {
                LOG.error(e);
            }
        } else {
            LOG.error("file doesn't exist: " + file.getAbsolutePath());
        }
        return null;
    }

    public static <T> T load(Node node, Class<T> mainClass, Unmarshaller unmarshaller) {
        try {
            return mainClass.cast(unmarshaller.unmarshal(node));

        } catch (JAXBException e) {
            LOG.error(e);
        }
        return null;
    }

    public static <T> T load(StringReader node, Class<T> mainClass, Unmarshaller unmarshaller) {
        try {
            return mainClass.cast(unmarshaller.unmarshal(node));

        } catch (JAXBException e) {
            LOG.error(e);
        }
        return null;
    }

    public static Unmarshaller getUnmarshaller(Class mainClass, Class... additionalClasses) {
        Class[] jaxbClasses = new Class[additionalClasses.length + 1];

        jaxbClasses[0] = mainClass;
        System.arraycopy(additionalClasses, 0, jaxbClasses, 1, additionalClasses.length);
        return getUnmarshaller(jaxbClasses);
    }

    private static Unmarshaller getUnmarshaller(Class... jaxbClasses) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(jaxbClasses);
            return jaxbContext.createUnmarshaller();

        } catch (JAXBException e) {
            LOG.error(e);
        }
        return null;
    }

    public static Marshaller getMarshaller(Class mainClass, Class... additionalClasses) {
        return getMarshaller(false, mainClass, additionalClasses);
    }

    public static Marshaller getMarshaller(boolean formattedOutput, Class mainClass, Class... additionalClasses) {

        Class[] jaxbClasses = new Class[additionalClasses.length + 1];
        jaxbClasses[0] = mainClass;
        System.arraycopy(additionalClasses, 0, jaxbClasses, 1, additionalClasses.length);
        return getMarshaller(formattedOutput, jaxbClasses);
    }

    public static Marshaller getMarshaller(boolean formattedOutput, Class... jaxbClasses) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(jaxbClasses);

            Marshaller marshaller = jaxbContext.createMarshaller();
            if (formattedOutput) {
                setFormattedOutput(marshaller, Boolean.TRUE);
            }
            return marshaller;

        } catch (JAXBException e) {
            LOG.error(e);
        }
        return null;
    }

    public static void setFormattedOutput(Marshaller marshaller, Boolean formattedOutput) {
        try {
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, formattedOutput);
        } catch (PropertyException pe) {
            LOG.error(pe);
        }
    }

    public static void save(String fileName, Object instance, Marshaller marshaller) {

        File file = new File(fileName);

        if (file.exists()) {
            if (!file.delete()) {
                LOG.error("problem with removing: " + file.getAbsolutePath());
            }
        }
        try {
            marshaller.marshal(instance, file);
        } catch (JAXBException e) {
            LOG.error(e);
        }
    }

    public static String convertToString(Object instance, boolean formattedOutput, Class... jaxbClasses) {

        Marshaller marshaller = getMarshaller(formattedOutput, jaxbClasses);
        return convertToString(instance, marshaller);
    }

    public static String convertToString(Object instance, Marshaller marshaller) {
        try {
            StringWriter writer = new StringWriter();
            marshaller.marshal(instance, writer);
            return writer.toString();
        } catch (JAXBException e) {
            LOG.error(e);
            return null;
        }
    }

    public static void save(String fileName, Object instance, Class... classes) {

        File file = new File(fileName);

        if (file.exists()) {
            if (!file.delete()) {
                LOG.error("problem with removing : " + file.getAbsolutePath());
            }
        }
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(classes);

            Marshaller marshaller = jaxbContext.createMarshaller();
            setFormattedOutput(marshaller, Boolean.TRUE);
            //marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, "");

            marshaller.marshal(instance, file);

        } catch (JAXBException e) {
            LOG.error(e);
        }
    }
}

