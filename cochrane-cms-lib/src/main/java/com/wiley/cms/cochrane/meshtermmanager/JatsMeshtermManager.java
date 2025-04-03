package com.wiley.cms.cochrane.meshtermmanager;

import com.wiley.cms.cochrane.cmanager.data.meshterm.DescriptorEntity;
import com.wiley.cms.cochrane.cmanager.data.meshterm.IMeshtermStorage;
import com.wiley.cms.cochrane.cmanager.data.meshterm.QualifierEntity;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.XmlUtils;

import java.util.List;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * @version 19.12.2019
 */
public class JatsMeshtermManager {
    private static final Logger LOG = Logger.getLogger(JatsMeshtermManager.class);
    private static final String MESH_HEADING_LIST_NS = "<MeshHeadingList xmlns=\"http://ct.wiley.com/ns/xdps/mesh\">";
    private static final String MESH_HEADING_LIST_CLOSE = "</MeshHeadingList>";
    private static final String MESH_HEADING = "<MeshHeading>";
    private static final String MESH_HEADING_CLOSE = "</MeshHeading>";

    private JatsMeshtermManager() {
    }

    public static String generateMeshTerms(IRecord record, IMeshtermStorage ms) {
        String recordName = record.getName();
        if (ms.recordNameExists(recordName)) {
            List<DescriptorEntity> descriptorEntities = ms.getDescriptors(recordName);

            if (!descriptorEntities.isEmpty()) {
                StringBuilder builder = new StringBuilder(XmlUtils.XML_HEAD);
                buildHeader(builder);
                buildMeshHeadings(recordName, descriptorEntities, ms, builder);
                buildCloseTag(builder);
                LOG.debug("inserting MeSH terms into WML3G for record "
                                  + RevmanMetadataHelper.buildPubName(recordName, record.getPubNumber()));

                return builder.toString();
            }
        }
        return null;
    }

    private static void buildHeader(StringBuilder builder) {
        builder.append("\n").append(MESH_HEADING_LIST_NS);
    }

    private static void buildMeshHeadings(String recordName, List<DescriptorEntity> entities, IMeshtermStorage ms,
                                          StringBuilder builder) {
        entities.forEach(descriptorEntity -> {
                builder.append("\n").append(MESH_HEADING).append("\n")
                        .append(descriptorEntity.toString()).append("\n");
                buildQualifiers(recordName, builder, ms, descriptorEntity);
                builder.append(MESH_HEADING_CLOSE);
            });
    }

    private static void buildQualifiers(String recordName, StringBuilder builder, IMeshtermStorage ms,
                                        DescriptorEntity entity) {
        List<QualifierEntity> qualifierEntities = ms.getQualifiers(recordName, entity);
        if (!qualifierEntities.isEmpty()) {
            qualifierEntities.forEach(qualifierEntity -> {
                    String qualifier = qualifierEntity.getQualifier();
                    if (qualifier != null && !qualifier.isEmpty()) {
                        builder.append(qualifierEntity.toString()).append("\n");
                    }
                });
        }
    }

    private static void buildCloseTag(StringBuilder builder) {
        builder.append("\n").append(MESH_HEADING_LIST_CLOSE);
    }
}