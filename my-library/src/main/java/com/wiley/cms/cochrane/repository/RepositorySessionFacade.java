package com.wiley.cms.cochrane.repository;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.tes.util.Logger;

import javax.ejb.Local;
import javax.ejb.Stateless;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 */
@Stateless
@Local(IRepositorySessionFacade.class)
public class RepositorySessionFacade implements IRepositorySessionFacade {
    private static final int BUFFER_SIZE = 1024;

    private static final Logger LOG = Logger.getLogger(RepositorySessionFacade.class);

    /*public byte[] getManifest(String uri) throws IOException {
        IRepository rps = RepositoryFactory.getRepository();
        InputStream is = null;
        ByteArrayOutputStream baos = null;
        try {
            is = rps.getFile(uri);

            baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int num;
            while ((num = is.read(buffer)) > 0) {
                baos.write(buffer, 0, num);
            }
            baos.flush();
            baos.close();
        } catch (IOException e) {
            throw e;
        } finally {
            try {
                is.close();
            } catch (NullPointerException e) {
                LOG.error(e);
            }
        }

        return baos.toByteArray();
    }*/

    //public void removeRecordResources(int recId) {
    //    List<String> uris = RecordResourceWrapper.getUris(recId);
    //    if (uris.isEmpty()) {
    //        LOG.error("Failed to get resources uris of record " + recId);
    //    }

    //    CmsUtils.deleteSourceWithImages(uris);
    //}


    //public void removeRecordList(List<RecordEntity> issueRecordList) {
    //    try {
    //        for (RecordEntity record : issueRecordList) {
                //remove records xml shadow file
    //            RecordManager.removeXMLFromMLShadow(record.getRecordPath());
                //remove records files by manifest
    //            removeRecordResources(record.getId());
    //        }
    //    } catch (Exception e) {
    //        LOG.error(e, e);
    //    }
    //}

    // todo it's not used actually
    public void setAssets(String fileName) {
        URL url = this.getClass().getResource("/" + fileName);

        try {
            LOG.trace(url.toURI());
            String uri = url.getPath().replace("!/" + fileName, "");
            LOG.trace(uri);
            url = new URL(uri);

            ZipInputStream zis = new ZipInputStream(url.openStream());

            try {
                ZipEntry ze;
                while ((ze = zis.getNextEntry()) != null) {
                    if (!ze.isDirectory() && ze.getName().startsWith(fileName)) {
                        LOG.trace(ze.getName());
                        LOG.trace(ze.getSize());
                        LOG.trace(this.getClass().getResource("/" + ze.getName()).toURI());
                        IRepository rps = RepositoryFactory.getRepository();
                        rps.putFile("/" + ze.getName(), this.getClass().getResourceAsStream("/" + ze.getName()));
                    }
                }
                zis.close();

            } catch (Exception e) {
                LOG.error(e);
            }
        } catch (Exception e) {
            LOG.error(e);
        }
    }


    public String getArticleXmlFileAsString(String recordXMLPath) throws IOException {
        return getArticleXmlFileAsStringChangingDtd(recordXMLPath, false);
    }

    public String getArticleXmlFileAsString(String recordXMLPath, boolean correctDtdPath) throws IOException {
        return getArticleXmlFileAsStringChangingDtd(recordXMLPath, correctDtdPath);
    }

    private String getArticleXmlFileAsStringChangingDtd(String recordXMLPath, final boolean skipCorrectDtdPath)
        throws IOException {

        InputStream recordXMLIS = null;
        String data = null;
        try {
            IRepository rps = RepositoryFactory.getRepository();
            recordXMLIS = rps.getFile(recordXMLPath);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];

            int m;
            while ((m = recordXMLIS.read(buffer)) > 0) {
                out.write(buffer, 0, m);
            }


            data = new String(out.toByteArray());

            if (!skipCorrectDtdPath) {
                data = CmsUtils.correctDtdPath(data);
            }
        } finally {
            if (recordXMLIS != null) {
                recordXMLIS.close();
            }
        }
        return data;
    }

//    public void clearRecord(String uri) throws IOException
//    {
//        IRepository rps = RepositoryFactory.getRepository();
//        InputStream is = rps.getFile(uri);
//        try
//        {
//            RecordManifest rm = new RecordManifest(new InputSource(is));
//
//            List<String> delGroups = new ArrayList<String>();
//            for (Map.Entry<String, RecordManifest.FileGroup> fileGroup : rm.getGroups().entrySet())
//            {
//                LOG.debug("File group name: " + fileGroup.getKey());
//                if (!fileGroup.getKey().equals("temp"))
//                {
//                    ArrayList<String> fileList = new ArrayList<String>();
//                    for(RecordManifest.FileLink file : fileGroup.getValue().getFiles())
//                    {
//                        fileList.add(file.getUri());
//                    }
//                    String files[] = new String[fileList.size()];
//                    fileList.toArray(files);
//                    CmsUtils.deleteSourceWithImages(files);
//
//                    delGroups.add(fileGroup.getKey());
//                }
//            }
//            for (String groupName : delGroups)
//            {
//                rm.removeGroup(groupName);
//            }
//
//            rps.put(uri, new ByteArrayInputStream(rm.toXml().getBytes()));
//        }
//        catch (Exception e)
//        {
//            LOG.error(e, e);
//        }
//        finally
//        {
//            is.close();
//        }
//    }
}
