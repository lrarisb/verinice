/*******************************************************************************
 * Copyright (c) 2014 Daniel Murygin.
 *
 * This program is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU Lesser General Public License 
 * as published by the Free Software Foundation, either version 3 
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,    
 * but WITHOUT ANY WARRANTY; without even the implied warranty 
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. 
 * If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Daniel Murygin <dm[at]sernet[dot]de> - initial API and implementation
 ******************************************************************************/
package sernet.verinice.service.test;

import static org.apache.commons.io.FilenameUtils.concat;
import static org.apache.commons.io.FilenameUtils.getFullPath;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import javax.annotation.Resource;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import sernet.gs.service.AbstractReportTemplateService;
import sernet.gs.service.MD5;
import sernet.verinice.interfaces.IReportDepositService;
import sernet.verinice.interfaces.IReportTemplateService;
import sernet.verinice.interfaces.IReportTemplateService.OutputFormat;
import sernet.verinice.interfaces.ReportDepositException;
import sernet.verinice.interfaces.ReportTemplateServiceException;
import sernet.verinice.model.report.FileMetaData;
import sernet.verinice.model.report.ReportTemplateMetaData;

/**
 * 
 * 
 * @author Daniel Murygin <dm[at]sernet[dot]de>
 */
public class ReportDepositTest extends CommandServiceProvider {

    private static final String REPORT_DIR = "/reports";
    private static final String RPTSUFFIX = ".rptdesign";
    private static final String DEPOSIT_DIR_PART_1 = "WEB-INF";
    private static final String DEPOSIT_DIR_PART_2 = "reportDeposit";

    private File deposit;

    @Resource(name = "reportdepositService")
    private IReportDepositService depositService;

    @Before
    public void setUp() throws Exception {

        deposit = createWEBINFFolder();

        assertTrue("Report deposit was not created", deposit.exists());
    }

    private File createWEBINFFolder() {

        String absolutePath = getTestBinariesRootDirectory();

        // concat root directory with the WEB-INF/reportDeposit
        File deposit = new File(
                concat(absolutePath, concat(DEPOSIT_DIR_PART_1, DEPOSIT_DIR_PART_2)));
        deposit.mkdirs();

        return deposit;
    }

    private String getTestBinariesRootDirectory() {

        // retrieve the current path to the actual report repository class file
        String className = depositService.getClass().getSimpleName() + ".class";
        URL resource = depositService.getClass().getResource(className);

        // cut off the base name (file name of the repository implementation)
        String absolutePath = getFullPath(resource.getPath());

        // convert the java package syntax (package1.package2. ...
        // .pacckagex.ClassName) to (package1/package2/ ... )
        // This way we get the root directory of the test classloader.
        String pathSuffix = getFullPath(
                depositService.getClass().getName().replace(".", File.separator));
        absolutePath = absolutePath.replaceAll(pathSuffix, "");
        return absolutePath;
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(deposit);
    }

    @Test
    public void testAddToServerDeposit() throws Exception {
        List<ReportTemplateMetaData> addedMetadataList = addAllFilesToDeposit();
        checkMetadataInDeposit(addedMetadataList, true);
    }

    @Test
    public void testRemoveFromServer() throws Exception {
        List<ReportTemplateMetaData> addedMetadataList = addAllFilesToDeposit();
        checkMetadataInDeposit(addedMetadataList, true);
        for (ReportTemplateMetaData metadata : addedMetadataList) {
            depositService.remove(metadata, Locale.ENGLISH);
        }
        checkMetadataInDeposit(addedMetadataList, false);
    }

    @Test
    public void testUpdateInServerDeposit() throws Exception {
        addAllFilesToDeposit();
        Set<ReportTemplateMetaData> metadataSet = depositService.getReportTemplates(Locale.ENGLISH);
        for (ReportTemplateMetaData metadata : metadataSet) {
            metadata.setOutputname(getOutputname());
            metadata.setOutputFormats(getOutputFormats());
            depositService.update(metadata, Locale.ENGLISH);
        }
        metadataSet = depositService.getReportTemplates(Locale.ENGLISH);
        for (ReportTemplateMetaData metadata : metadataSet) {
            assertEquals("Output name is not: " + getOutputname(), getOutputname(),
                    metadata.getOutputname());
            assertArrayEquals("Output formats name is not as expected.", getOutputFormats(),
                    metadata.getOutputFormats());
        }
    }

    @Test
    public void testUpdatingProperties() throws Exception {
        addAllFilesToDeposit();
        Set<ReportTemplateMetaData> metadataSet = depositService.getReportTemplates(Locale.ENGLISH);
        ReportTemplateMetaData randomTemplate = metadataSet
                .toArray(new ReportTemplateMetaData[metadataSet.size()])[new Random()
                        .nextInt(metadataSet.size())];
        OutputFormat[] toTest = new OutputFormat[] { IReportDepositService.OutputFormat.DOC,
                IReportDepositService.OutputFormat.XLS, IReportDepositService.OutputFormat.HTML };
        randomTemplate.setOutputFormats(toTest);

        URL reportDirectory = ReportDepositTest.class.getResource(REPORT_DIR);
        assertNotNull("Report directory not found: " + REPORT_DIR, reportDirectory);
        File dir = new File(reportDirectory.toURI());
        FileMetaData fileMetaData = new FileMetaData(
                checkServerLocation(randomTemplate.getFilename()),
                getCheckSum(randomTemplate.getFilename(), dir.getAbsolutePath()));

        ReportTemplateMetaData updatedData = new ReportTemplateMetaData(fileMetaData,
                randomTemplate.getOutputname(), toTest, randomTemplate.isServer(), false,
                randomTemplate.getContext());

        depositService.update(updatedData, Locale.ENGLISH);
        ReportTemplateMetaData storedData = getReportMetaDataFromDeposit(
                checkServerLocation(randomTemplate.getFilename()), depositService);
        assertArrayEquals(toTest, storedData.getOutputFormats());
        assertFalse(storedData.isMultipleRootObjects());

        updatedData = new ReportTemplateMetaData(fileMetaData, randomTemplate.getOutputname(),
                toTest, randomTemplate.isServer(), true, randomTemplate.getContext());
        depositService.update(updatedData, Locale.ENGLISH);
        storedData = getReportMetaDataFromDeposit(checkServerLocation(randomTemplate.getFilename()),
                depositService);
        assertTrue(storedData.isMultipleRootObjects());
    }

    private OutputFormat[] getOutputFormats() {
        return new IReportDepositService.OutputFormat[] { IReportDepositService.OutputFormat.ODT };
    }

    private String getOutputname() {
        return ReportDepositTest.class.getSimpleName();
    }

    private void checkMetadataInDeposit(List<ReportTemplateMetaData> checkMetadataList,
            boolean expected) throws ReportTemplateServiceException {
        Set<ReportTemplateMetaData> metadataSet = depositService.getReportTemplates(Locale.ENGLISH);
        for (ReportTemplateMetaData metadata : checkMetadataList) {
            if (expected) {
                assertTrue(
                        "Report metadata not found in deposit, rpt file: " + metadata.getFilename(),
                        isInSet(metadataSet, metadata));
            } else {
                assertFalse("Report metadata found in deposit, rpt file: " + metadata.getFilename(),
                        isInSet(metadataSet, metadata));
            }
        }
    }

    private boolean isInSet(Set<ReportTemplateMetaData> metadataSet,
            ReportTemplateMetaData metadata) {
        for (ReportTemplateMetaData currentMetadata : metadataSet) {
            if (arePropertiesEqual(currentMetadata, metadata)) {
                return true;
            }
        }
        return false;
    }

    private boolean arePropertiesEqual(ReportTemplateMetaData metadata1,
            ReportTemplateMetaData metadata2) {
        return metadata1.getFilename().equals(metadata2.getFilename())
                && Arrays.equals(metadata1.getOutputFormats(), metadata2.getOutputFormats())
                && metadata1.getOutputname().equals(metadata2.getOutputname());
    }

    private List<ReportTemplateMetaData> addAllFilesToDeposit()
            throws ReportTemplateServiceException, URISyntaxException, ReportDepositException,
            IOException {
        URL reportDirectory = ReportDepositTest.class.getResource(REPORT_DIR);
        assertNotNull("Report directory not found: " + REPORT_DIR, reportDirectory);
        File dir = new File(reportDirectory.toURI());
        assertNotNull("Report directory not found: " + REPORT_DIR, dir);
        assertTrue("Report directory path is not a directory: " + REPORT_DIR, dir.isDirectory());
        List<String> rptFileNames = getRptfileList(dir);
        assertNotNull("No RPT files found in directory: " + REPORT_DIR, rptFileNames);
        assertFalse("No RPT files found in directory: " + REPORT_DIR, rptFileNames.isEmpty());
        List<ReportTemplateMetaData> metadataList = new ArrayList<ReportTemplateMetaData>(
                rptFileNames.size());
        for (String fileName : rptFileNames) {
            metadataList.add(addFileToDeposit(dir, fileName));
        }
        return metadataList;
    }

    private ReportTemplateMetaData addFileToDeposit(final File dir, String fileName)
            throws ReportTemplateServiceException, ReportDepositException, IOException {

        IReportTemplateService templateUtil = new AbstractReportTemplateService() {
            @Override
            protected boolean isHandeledByReportDeposit() {
                return false;
            }

            @Override
            protected File getTemplateDirectory() {
                return dir;
            }
        };

        File rptFile = new File(dir, fileName);
        byte[] fileData = Files.readAllBytes(rptFile.toPath());
        ReportTemplateMetaData metadata = getReportMetaDataFromDeposit(fileName, templateUtil);
        depositService.add(metadata, fileData, Locale.ENGLISH);
        return metadata;
    }

    private ReportTemplateMetaData getReportMetaDataFromDeposit(String filename,
            IReportTemplateService templateUtil) throws ReportTemplateServiceException {
        return templateUtil.getReportTemplates(Locale.ENGLISH).stream()
                .filter(rt -> rt.getFilename().equals(filename)).findFirst()
                .orElseThrow(() -> new RuntimeException("File not found: " + filename));
    }

    private List<String> getRptfileList(File dir) {
        String[] rptFileNames = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name) {
                return name.endsWith(RPTSUFFIX);
            }
        });
        return Arrays.asList(rptFileNames);
    }

    private String checkServerLocation(String path) {
        return path;
    }

    private String getCheckSum(String fileName, String dir) throws IOException {
        String filePath;
        if (!fileName.contains(dir)) {
            filePath = dir + File.separatorChar + fileName;
        } else {
            filePath = fileName;
        }

        return MD5.getMD5Checksum(FileUtils.readFileToByteArray(new File(filePath)));
    }
}
