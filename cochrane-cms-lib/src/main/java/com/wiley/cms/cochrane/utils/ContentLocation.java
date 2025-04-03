package com.wiley.cms.cochrane.utils;

import java.util.List;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 18.03.2014
 */
public enum ContentLocation {
    ISSUE {
        @Override
        public void addPdfs(Integer issueId, String dbName, Integer version, String recName, List<String> uris) {
            RepositoryUtils.addFolderPaths(getPathToPdf(issueId, dbName, version, recName), uris,
                    RepositoryUtils.PDF_FF);
        }

        @Override
        public String getPathToPdf(Integer issueId, String dbName, Integer version, String recName) {
            return FilePathBuilder.PDF.getPathToPdfFop(issueId, dbName, recName);
        }

        @Override
        public String getPathToPdfTex(Integer issueId, String dbName, Integer version, String recordName) {
            return FilePathBuilder.PDF.getPathToPdf(issueId, dbName, recordName);
        }

        @Override
        public String getPathToHtml(Integer issueId, String dbName, Integer version, String recName, boolean central) {
            return FilePathBuilder.getPathToHtml(issueId, dbName, recName, central);
        }

        @Override
        public String getPathToPdfFileWithSuffix(Integer issueId, String dbName, Integer version, String recordName,
                                                 String suffix) {
            return FilePathBuilder.PDF.getPathToIssuePdfFopWithSuffix(issueId, dbName, recordName, suffix);
        }

        @Override
        public String getPathToPdfTAFileWithSuffix(Integer issueId, String dbName, Integer version, String recName,
                                                   String lang, String suffix) {
            return FilePathBuilder.PDF.getPathToIssuePdfFopTAWithSuffix(issueId, dbName, recName, lang, suffix);
        }

        @Override
        public String getPathToMl3g(Integer issueId, String dbName, Integer version) {
            return FilePathBuilder.ML3G.getPathToMl3g(issueId, dbName);
        }

        @Override
        public String getPathToMl3g(Integer issueId, String dbName, Integer version, String recName, boolean central) {
            return FilePathBuilder.ML3G.getPathToMl3gRecord(issueId, dbName, recName, central);
        }

        @Override
        public String getPathToMl3gAssets(Integer issueId, String dbName, Integer version, String recName) {
            return FilePathBuilder.ML3G.getPathToMl3gRecordAssets(issueId, dbName, recName);
        }

        @Override
        public String getPathToMl3gTmpDir(Integer issueId, String dbName, Integer version, String recName) {
            return FilePathBuilder.ML3G.getPathToMl3gTmpDir(issueId, dbName, recName);
        }

        @Override
        public String getPathToJatsSrcDir(Integer issueId, String dbName, Integer packageId, Integer version,
                                          String recName) {
            return FilePathBuilder.JATS.getPathToSrcDir(issueId, packageId, recName);
        }

        @Override
        public String getPathToJatsTA(Integer issueId, Integer dfId, Integer version, String lang, String recName) {
            return FilePathBuilder.TR.getPathToJatsTADir(issueId, dfId, lang, recName) + FilePathCreator.SEPARATOR;
        }

        @Override
        public String getPathToMl3gTA(Integer issueId, Integer dfId, Integer version, String lang, String recName) {
            return FilePathBuilder.TR.getPathToIssueTARecord(issueId, dfId, lang, recName);
        }

        @Override
        public String getPathToRevmanSrc(Integer issueId, Integer version, String groupName, String recName) {
            return FilePathBuilder.RM.getPathToRevmanRecord(issueId, groupName, recName);
        }

        @Override
        public String getPathToRevmanMetadata(Integer issueId, Integer version, String groupName, String recName) {
            return FilePathBuilder.RM.getPathToRevmanMetadata(issueId, groupName, recName);
        }

        @Override
        public String getPathToTopics(Integer issueId, String groupName) {
            return FilePathBuilder.getPathToTopics(issueId, groupName);
        }

        @Override
        public String getPathToMl21SrcDir(Integer issueId, String dbName, Integer packageId, Integer version,
                                          String recName) {
            return FilePathBuilder.getPathToWML21RecordDir(issueId, dbName, packageId.toString(), recName);
        }

        @Override
        public String getPathToMl21SrcRecord(Integer issueId, String dbName, Integer packageId, Integer version,
                                             String recName) {
            return FilePathBuilder.getPathToWML21Record(issueId, dbName, packageId.toString(), recName);
        }

        @Override
        public String getShortString(int issueNumber, String dbName, Integer version) {
            return CmsUtils.isImportIssueNumber(issueNumber) ? ISSUE_PREVIOUS.getShortString(
                issueNumber, dbName, version) : (CmsUtils.isScheduledIssueNumber(issueNumber)
                    ? String.format("%s, Issue to SPD %d", dbName, version) : String.format("%s, Issue %d, %d", dbName,
                        CmsUtils.getIssueByIssueNumber(issueNumber), CmsUtils.getYearByIssueNumber(issueNumber)));
        }
    },
    ISSUE_COPY {
        @Override
        public String getPathToPdf(Integer issueId, String dbName, Integer version, String recordName) {
            return FilePathBuilder.PDF.getPathToBackupPdfFop(issueId, dbName, recordName);
        }

        @Override
        public String getPathToPdfTex(Integer issueId, String dbName, Integer version, String recordName) {
            return FilePathBuilder.PDF.getPathToBackupPdf(issueId, dbName, recordName);
        }

        @Override
        public String getPathToHtml(Integer issueId, String dbName, Integer version, String recName, boolean central) {
            return FilePathBuilder.getPathToBackupHtml(issueId, dbName, recName, central);
        }

        @Override
        public String getPathToMl3g(Integer issueId, String dbName, Integer version, String recName, boolean central) {
            return FilePathBuilder.ML3G.getPathToBackupMl3gRecord(issueId, dbName, recName, central);
        }

        @Override
        public String getPathToMl3gAssets(Integer issueId, String dbName, Integer version, String recName) {
            return FilePathBuilder.ML3G.getPathToBackupMl3gRecordAssets(issueId, dbName, recName);
        }

        @Override
        public String getPathToRevmanSrc(Integer issueId, Integer version, String groupName, String recName) {
            return FilePathBuilder.getPathToRevmanBackupRecord(issueId, groupName, recName);
        }

        @Override
        public String getPathToJatsSrcDir(Integer issueId, String dbName, Integer packageId, Integer version,
                                          String recName) {
            return FilePathBuilder.JATS.getPathToBackupDir(issueId, dbName, recName);
        }

        @Override
        public String getPathToMl3gTA(Integer issueId, Integer dfId, Integer version, String lang, String recName) {
            return FilePathBuilder.TR.getPathToBackupWML3GTARecord(issueId, lang, recName);
        }

        @Override
        public String getPathToMl21SrcDir(Integer issueId, String dbName, Integer packageId, Integer version,
                                          String recName) {
            return FilePathBuilder.getPathToBackupSrcRecordDir(issueId, dbName, recName);
        }

        @Override
        public String getPathToTA(Integer issueId, Integer version, String lang, String recName) {
            return FilePathBuilder.TR.getPathToBackupTARecord(issueId, lang, recName);
        }

        @Override
        public String getPathToMl21TA(Integer issueId, Integer dfId, Integer version, String lang, String recName) {
            return FilePathBuilder.TR.getPathToBackupWML21TARecord(issueId, lang, recName);
        }

        @Override
        public String getShortString(int issueNumber, String dbName, Integer version) {
            return String.format("%s, Issue backup %d, %d", dbName, CmsUtils.getIssueByIssueNumber(issueNumber),
                    CmsUtils.getYearByIssueNumber(issueNumber));
        }
    },
    ISSUE_PREVIOUS {

        @Override
        public String getPathToPdf(Integer issueId, String dbName, Integer version, String recName) {
            return FilePathBuilder.PDF.getPathToPreviousPdfFop(issueId, version, recName);
        }

        @Override
        public String getPathToPdfTex(Integer issueId, String dbName, Integer version, String recName) {
            return FilePathBuilder.PDF.getPathToPreviousPdf(issueId, version, recName);
        }

        @Override
        public String getPathToHtml(Integer issueId, String dbName, Integer version, String recName, boolean central) {
            return FilePathBuilder.getPathToPreviousHtml(issueId, version, recName);
        }

        @Override
        public String getPathToMl3g(Integer issueId, String dbName, Integer version) {
            return FilePathBuilder.ML3G.getPathToPreviousMl3g(issueId, version);
        }

        @Override
        public String getPathToMl3g(Integer issueId, String dbName, Integer version, String recName, boolean central) {
            return FilePathBuilder.ML3G.getPathToPreviousMl3gRecord(issueId, version, recName);
        }

        @Override
        public String getPathToMl3gTA(Integer issueId, Integer dfId, Integer version, String lang, String recName) {
            //return FilePathBuilder.TR.getPathToPreviousIssueTARecord(issueId, version, lang, recName);
            return FilePathBuilder.TR.getPathToIssueTARecord(issueId, dfId, lang, recName);
        }

        @Override
        public String getPathToMl3gAssets(Integer issueId, String dbName, Integer version, String recName) {
            return FilePathBuilder.ML3G.getPathToPreviousMl3gRecordAssets(issueId, version, recName);
        }

        @Override
        public String getPathToPdfFileWithSuffix(Integer issueId, String dbName, Integer version, String recName,
                                                 String suffix) {
            return FilePathBuilder.PDF.getPathToPreviousPdfFop(issueId, version, recName, suffix);
        }

        @Override
        public String getPathToPdfTAFileWithSuffix(Integer issueId, String dbName, Integer version, String recName,
                                                   String lang, String suffix) {
            return FilePathBuilder.PDF.getPathToPreviousPdfFopTA(issueId, version, recName, lang, suffix);
        }

        @Override
        public String getPathToMl3gTmpDir(Integer issueId, String dbName, Integer version, String recName) {
            return FilePathBuilder.ML3G.getPathToPreviousMl3gTmpDir(issueId, version, recName);
        }

        @Override
        public String getPathToRevmanSrc(Integer issueId, Integer version, String groupName, String recName) {
            return FilePathBuilder.getPathToPreviousRevmanRecord(issueId, version, groupName, recName);
        }

        @Override
        public String getPathToJatsSrcDir(Integer issueId, String dbName, Integer packageId, Integer version,
                                          String pubName) {
            return FilePathBuilder.JATS.getPathToSrcDir(issueId, packageId, pubName);
        }

        @Override
        public String getPathToJatsTA(Integer issueId, Integer dfId, Integer version, String lang, String recName) {
            return FilePathBuilder.TR.getPathToJatsTADir(issueId, dfId, lang, recName) + FilePathCreator.SEPARATOR;
        }

        @Override
        public String getShortString(int issueNumber, String dbName, Integer version) {
            return String.format("%s, Issue to import %d", dbName, issueNumber);
        }
    },
    ENTIRE {
        @Override
        public void addPdfs(Integer issueId, String dbName, Integer version, String recordName, List<String> uris) {
            RepositoryUtils.addFolderPaths(getPathToPdf(issueId, dbName, version, recordName), uris,
                    RepositoryUtils.PDF_FF);
        }

        @Override
        public String getPathToPdf(Integer issueId, String dbName, Integer version, String recordName) {
            return FilePathBuilder.PDF.getPathToEntirePdfFop(dbName, recordName);
        }

        @Override
        public String getPathToPdfTex(Integer issueId, String dbName, Integer version, String recName) {
            return FilePathBuilder.PDF.getPathToEntirePdf(dbName, recName);
        }

        @Override
        public String getPathToHtml(Integer issueId, String dbName, Integer version, String recName, boolean central) {
            return FilePathBuilder.getPathToEntireHtml(dbName, recName, central);
        }

        @Override
        public String getPathToPdfFileWithSuffix(Integer issueId, String dbName, Integer version, String recordName,
                                                 String suffix) {
            return FilePathBuilder.PDF.getPathToEntirePdfFopWithSuffix(dbName, recordName, suffix);
        }

        @Override
        public String getPathToPdfTAFileWithSuffix(Integer issueId, String dbName, Integer version, String recordName,
                                                   String lang, String suffix) {
            return FilePathBuilder.PDF.getPathToEntirePdfFopTAWithSuffix(dbName, recordName, lang, suffix);
        }

        @Override
        public String getPathToMl3g(Integer issueId, String dbName, Integer version) {
            return FilePathBuilder.ML3G.getPathToEntireMl3g(dbName);
        }

        @Override
        public String getPathToMl3g(Integer issueId, String dbName, Integer version, String recordName,
                                    boolean central) {
            return FilePathBuilder.ML3G.getPathToEntireMl3gRecord(dbName, recordName, central);
        }

        @Override
        public String getPathToMl3gAssets(Integer issueId, String dbName, Integer version, String recordName) {
            return FilePathBuilder.ML3G.getPathToEntireMl3gRecordAssets(dbName, recordName);
        }

        @Override
        public String getPathToMl3gTA(Integer issueId, Integer dfId, Integer version, String lang, String recName) {
            return FilePathBuilder.TR.getPathToWML3GTARecord(lang, recName);
        }

        @Override
        public String getPathToMl21TA(Integer issueId, Integer dfId, Integer version, String lang, String recName) {
            return FilePathBuilder.TR.getPathToWML21TARecord(lang, recName);
        }

        @Override
        public String getPathToMl3gTmpDir(Integer issueId, String dbName, Integer version, String recordName) {
            return FilePathBuilder.ML3G.getPathToEntireMl3gTmpDir(dbName, recordName);
        }

        @Override
        public String getPathToJatsSrcDir(Integer issueId, String dbName, Integer dfId, Integer version,
                                          String recName) {
            return FilePathBuilder.JATS.getPathToEntireDir(dbName, recName);
        }

        @Override
        public String getPathToJatsTA(Integer issueId, Integer dfId, Integer version, String lang, String recName) {
            return FilePathBuilder.TR.getPathToJatsTA(lang);
        }

        @Override
        public String getPathToRevmanSrc(Integer issueId, Integer version, String groupName, String recName) {
            return FilePathBuilder.getPathToEntireRevmanRecord(groupName, recName);
        }

        @Override
        public String getPathToRevmanMetadata(Integer issueId, Integer version, String groupName, String recName) {
            return FilePathBuilder.getPathToEntireRevmanMetadata(groupName, recName);
        }

        @Override
        public String getPathToTA(Integer issueId, Integer version, String lang, String recName) {
            return FilePathBuilder.TR.getPathToTARecord(lang, recName);
        }

        @Override
        public String getPathToMl21SrcDir(Integer issueId, String dbName, Integer packageId, Integer version,
                                           String recName) {
            return FilePathBuilder.getPathToEntireSrcDir(dbName, recName, false);
        }

        @Override
        public String getPathToMl21SrcRecord(Integer issueId, String dbName, Integer dfId, Integer version,
                                             String recName) {
            return FilePathBuilder.getPathToEntireSrcRecord(dbName, recName);
        }

        @Override
        public String getShortString(int issueNumber, String dbName, Integer version) {
            return "entire " + dbName;
        }
    },
    PREVIOUS {
        @Override
        public void addPdfs(Integer issueId, String dbName, Integer version, String recName, List<String> uris) {
            RepositoryUtils.addFolderPaths(getPathToPdf(issueId, dbName, version, recName), uris,
                    RepositoryUtils.PDF_FF);
        }

        @Override
        public String getPathToPdf(Integer issueId, String dbName, Integer version, String recName) {
            return FilePathBuilder.PDF.getPathToPreviousPdfFop(version, recName);
        }

        @Override
        public String getPathToHtml(Integer issueId, String dbName, Integer version, String recName, boolean central) {
            return FilePathBuilder.getPathToPreviousHtml(version, recName);
        }

        @Override
        public String getPathToPdfTex(Integer issueId, String dbName, Integer version, String recName) {
            return FilePathBuilder.PDF.getPathToPreviousPdf(version, recName);
        }

        @Override
        public String getPathToPdfFileWithSuffix(Integer issueId, String dbName, Integer version, String recordName,
                                                 String suffix) {
            return FilePathBuilder.PDF.getPathToPreviousPdfFopWithSuffix(version, recordName, suffix);
        }

        @Override
        public String getPathToPdfTAFileWithSuffix(Integer issueId, String dbName, Integer version, String recordName,
                                                   String lang, String suffix) {
            return FilePathBuilder.PDF.getPathToPreviousPdfFopTAWithSuffix(version, recordName, lang, suffix);
        }

        @Override
        public String getPathToMl3g(Integer issueId, String dbName, Integer version) {
            return FilePathBuilder.ML3G.getPathToPreviousMl3g(version);
        }

        @Override
        public String getPathToMl3g(Integer issueId, String dbName, Integer version, String recName, boolean central) {
            return FilePathBuilder.ML3G.getPathToPreviousMl3gRecord(version, recName);
        }

        @Override
        public String getPathToMl3gAssets(Integer issueId, String dbName, Integer version, String recName) {
            return FilePathBuilder.ML3G.getPathToPreviousMl3gRecordAssets(version, recName);
        }

        @Override
        public String getPathToMl3gTA(Integer issueId, Integer dfId, Integer version, String lang, String recName) {
            return FilePathBuilder.TR.getPathToPreviousWML3GTARecord(version, lang, recName);
        }

        @Override
        public String getPathToMl21TA(Integer issueId, Integer dfId, Integer version, String lang, String recName) {
            return FilePathBuilder.TR.getPathToPreviousWML21TARecord(version, lang, recName);
        }

        @Override
        public String getPathToMl3gTmpDir(Integer issueId, String dbName, Integer version, String recName) {
            return FilePathBuilder.ML3G.getPathToPreviousMl3gTmpDir(version, recName);
        }

        @Override
        public String getPathToJatsSrcDir(Integer issueId, String dbName, Integer dfId, Integer version,
                                          String recName) {
            return FilePathBuilder.JATS.getPathToPreviousDir(version, recName);
        }

        @Override
        public String getPathToJatsTA(Integer issueId, Integer dfId, Integer version, String lang, String recName) {
            return FilePathBuilder.TR.getPathToPreviousJatsTA(version, lang);
        }

        @Override
        public String getPathToRevmanSrc(Integer issueId, Integer version, String groupName, String recName) {
            return FilePathBuilder.getPathToPreviousRevmanRecord(version, groupName, recName);
        }

        @Override
        public String getPathToRevmanMetadata(Integer issueId, Integer version, String groupName, String recName) {
            return FilePathBuilder.getPathToPreviousRevmanMetadata(version, groupName, recName);
        }

        @Override
        public String getPathToTA(Integer issueId, Integer version, String lang, String recName) {
            return FilePathBuilder.TR.getPathToPreviousTARecord(version, lang, recName);
        }

        @Override
        public String getPathToMl21SrcDir(Integer issueId, String dbName, Integer dfId, Integer version,
                                          String recName) {
            return FilePathBuilder.getPathToPreviousSrcRecordDir(version, recName);
        }

        @Override
        public String getPathToMl21SrcRecord(Integer issueId, String dbName, Integer dfId, Integer version,
                                             String recName) {
            return FilePathBuilder.getPathToPreviousSrcRecord(version, recName);
        }

        @Override
        public String getShortString(int issueNumber, String dbName, Integer version) {
            return String.format("%s, history version %d", dbName, version);
        }
    };

    public boolean isEntire() {
        return this == ENTIRE;
    }

    public final boolean hasPdf(Integer issueId, String dbName, Integer version, String recName) {
        return RepositoryFactory.getRepository().isFileExistsQuiet(getPathToPdf(issueId, dbName, version, recName));
    }

    public void addPdfs(Integer issueId, String dbName, Integer version, String recName, List<String> uris) {
    }

    public String getPathToMl3g(Integer issueId, String dbName, Integer version) {
        return null;
    }

    public String getPathToMl3g(Integer issueId, String dbName, Integer version, String recName, boolean central) {
        return null;
    }

    public String getPathToMl3gAssets(Integer issueId, String dbName, Integer version, String recName) {
        return null;
    }

    public String getPathToMl3gTA(Integer issueId, Integer dfId, Integer version, String lang, String recName) {
        return null;
    }

    public String getPathToPdfFileWithSuffix(Integer issueId, String dbName, Integer version, String recordName,
                                             String suffix) {
        return null;
    }

    public String getPathToPdfTAFileWithSuffix(Integer issueId, String dbName, Integer version, String recordName,
                                                  String lang, String suffix) {
        return null;
    }

    public String getPathToMl3gTmpDir(Integer issueId, String dbName, Integer version, String recName) {
        return null;
    }

    public String getPathToPdf(Integer issueId, String dbName, Integer version, String recName) {
        return null;
    }

    public String getPathToPdfTex(Integer issueId, String dbName, Integer version, String recName) {
        return null;
    }

    public String getPathToHtml(Integer issueId, String dbName, Integer version, String recName, boolean central) {
        return null;
    }

    public String getPathToJatsSrcDir(Integer issueId, String dbName, Integer dfId, Integer version, String recName) {
        return null;
    }

    public String getPathToJatsTA(Integer issueId, Integer dfId, Integer version, String lang, String recName) {
        return null;
    }

    public String getPathToTopics(Integer issueId, String groupName) {
        return FilePathBuilder.getPathToTopics(groupName);
    }

    public String getPathToRevmanSrc(Integer issueId, Integer version, String groupName, String recName) {
        return null;
    }

    public String getPathToRevmanMetadata(Integer issueId, Integer version, String groupName, String recName) {
        return null;
    }

    public String getPathToTA(Integer issueId, Integer version, String lang, String recName) {
        return null;
    }

    public String getPathToMl21TA(Integer issueId, Integer dfId, Integer version, String lang, String recName) {
        return null;
    }

    public String getPathToMl21SrcDir(Integer issueId, String dbName, Integer dfId, Integer version, String recName) {
        return null;
    }

    public String getPathToMl21SrcRecord(Integer issueId, String dbName, Integer dfId, Integer version,
                                         String recName) {
        return null;
    }

    public String getShortString(int issueNumber, String dbName, Integer version) {
        return dbName;
    }
}
