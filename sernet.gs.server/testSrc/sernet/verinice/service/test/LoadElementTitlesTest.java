/*******************************************************************************
 * Copyright (c) 2016 Ruth Motza.
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
 *     Ruth Motza <rm[at]sernet[dot]de> - initial API and implementation
 ******************************************************************************/
package sernet.verinice.service.test;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.transaction.TransactionConfiguration;
import org.springframework.transaction.annotation.Transactional;

import sernet.gs.service.RetrieveInfo;
import sernet.hui.common.connect.HUITypeFactory;
import sernet.verinice.interfaces.CommandException;
import sernet.verinice.model.bsi.ITVerbund;
import sernet.verinice.model.bsi.PersonenKategorie;
import sernet.verinice.model.common.CnATreeElement;
import sernet.verinice.service.commands.LoadElementTitles;
import sernet.verinice.service.commands.LoadElementsByUuid;
import sernet.verinice.service.commands.SyncParameterException;
import sernet.verinice.service.test.helper.vnaimport.VNAImportHelper;

/**
 * 
 * Class to test {@link LoadElementTitles} To ensure the variable array-sizes to
 * be tested the loop (and with that the array-size) is incremented by a random
 * value between 1 and 5
 * 
 * In addition the typed to be tested in the arrays are selected via random to
 * ensure different combinations to be tested every time the test is run.
 * 
 * @author Ruth Motza <rm[at]sernet[dot]de>
 */
@Transactional
@TransactionConfiguration(transactionManager = "txManager")
public class LoadElementTitlesTest extends CommandServiceProvider {

    private static final Logger LOG = Logger.getLogger(LoadElementTitlesTest.class);
    private static final String VNA_FILENAME = "export_import_references_test.vna";
    private static final int INTERVAL_ARRAYSIZE = 5;
    private static final int MAX_NUM_TO_TEST = 5;
    private static HashSet<String> allTypeIds;

    private static final int HIGHER_INTERVAL = 10;
    private static final int LIMIT_TO_USE_SMALL_INTERVAL = 200;

    @Before
    public void importData() throws IOException, CommandException, SyncParameterException {
        VNAImportHelper.importFile(VNA_FILENAME);
    }

    @Test
    public void testExecute() throws CommandException {

        runTest(0);
        runTest(1);
        for (int i = 2; i < getAllTypeIds().size(); i += getRandomInInterval(1,
                INTERVAL_ARRAYSIZE)) {

            runTest(i);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Test finished");
        }
    }

    private HashSet<String> getAllTypeIds() {
        if (allTypeIds == null) {
            allTypeIds = new HashSet<>(HUITypeFactory.getInstance().getAllTypeIds());
            allTypeIds.remove(ITVerbund.TYPE_ID);
            allTypeIds.add(ITVerbund.TYPE_ID_HIBERNATE);
            allTypeIds.remove(PersonenKategorie.TYPE_ID);
            allTypeIds.add(PersonenKategorie.TYPE_ID_HIBERNATE);
        }
        return allTypeIds;
    }

    private int getRandomInInterval(int min, int max) {
        return (int) (min + Math.random() * max);
    }

    private void runTest(int k) throws CommandException {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Start Test with " + k + " entries.");
        }

        Set<String> typeIdsToTest;
        for (int i = 1; i <= getRandomInInterval(1, MAX_NUM_TO_TEST); i++) {

            typeIdsToTest = getRandomSubset(k);

            LoadElementTitles command = new LoadElementTitles(
                    typeIdsToTest.toArray(new String[typeIdsToTest.size()]));
            command = commandService.executeCommand(command);
            HashMap<String, String> map = command.getElementsUuid();

            reviewCommandResult(map);
        }
    }

    private HashSet<String> getRandomSubset(int num) {
        HashSet<String> subset = new HashSet<>();

        ArrayList<String> list = new ArrayList<>(getAllTypeIds());
        Collections.shuffle(list);

        if (list.size() <= num) {
            return new HashSet<>(list);
        }
        int i = 0;
        while (subset.size() != num) {
            i = (int) (Math.random() * (list.size() - 1));
            subset.add(list.get(i));
        }

        return subset;
    }

    private void reviewCommandResult(HashMap<String, String> map) throws CommandException {
        if (map != null && !map.isEmpty()) {

            ArrayList<String> uuids = new ArrayList<>(map.keySet());
            ArrayList<String> uuidsToCheck;

            if (map.size() > LIMIT_TO_USE_SMALL_INTERVAL) {
                uuidsToCheck = new ArrayList<>();
                for (int i = 0; i < uuids.size(); i += HIGHER_INTERVAL) {
                    uuidsToCheck.add(uuids.get(i));
                }
            } else {
                uuidsToCheck = uuids;
            }

            LoadElementsByUuid<CnATreeElement> loadElementsByUuid = new LoadElementsByUuid<>(
                    uuidsToCheck, RetrieveInfo.getPropertyInstance());
            loadElementsByUuid = commandService.executeCommand(loadElementsByUuid);
            Map<String, CnATreeElement> elementsFromDb = loadElementsByUuid.getElements().stream()
                    .collect(Collectors.toMap(CnATreeElement::getUuid, Function.identity()));

            for (String uuid : uuidsToCheck) {
                String title = map.get(uuid);
                String titleFromDB = elementsFromDb.get(uuid).getTitle();

                assertEquals(titleFromDB, title);
            }
        }
    }

}
