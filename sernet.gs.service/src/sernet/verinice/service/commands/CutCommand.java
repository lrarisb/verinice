/*******************************************************************************
 * Copyright (c) 2011 Daniel Murygin.
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
package sernet.verinice.service.commands;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import sernet.gs.service.PermissionException;
import sernet.gs.service.RetrieveInfo;
import sernet.verinice.interfaces.CommandException;
import sernet.verinice.interfaces.ElementChange;
import sernet.verinice.interfaces.GenericCommand;
import sernet.verinice.interfaces.IBaseDao;
import sernet.verinice.interfaces.IChangeLoggingCommand;
import sernet.verinice.interfaces.IPostProcessor;
import sernet.verinice.model.common.ChangeLogEntry;
import sernet.verinice.model.common.CnATreeElement;
import sernet.verinice.model.iso27k.IISO27kGroup;

/**
 * @author Daniel Murygin <dm[at]sernet[dot]de>
 */
public class CutCommand extends GenericCommand implements IChangeLoggingCommand {

    private static final Logger log = Logger.getLogger(CutCommand.class);

    private String uuidGroup;

    private CnATreeElement selectedGroup;

    private List<String> uuidList;

    private int number = 0;

    private List<IPostProcessor> postProcessorList;

    private transient IBaseDao<CnATreeElement, Serializable> dao;

    // used on server side only !
    private transient Set<ElementChange> elementChanges;

    private String stationId;

    /**
     * @return A list of classes that can contain persons or personIsos as
     *         children
     */
    private static List<String> getPersonContainingTypeIDs() {
        ArrayList<String> list = new ArrayList<>();
        list.add(sernet.verinice.model.bsi.Person.TYPE_ID);
        list.add(sernet.verinice.model.bsi.PersonenKategorie.TYPE_ID);
        list.add(sernet.verinice.model.iso27k.Audit.TYPE_ID);
        list.add(sernet.verinice.model.iso27k.PersonGroup.TYPE_ID);
        list.add(sernet.verinice.model.iso27k.PersonIso.TYPE_ID);
        return list;
    }

    public CutCommand(String uuidGroup, List<String> uuidList) {
        this(uuidGroup, uuidList, new ArrayList<IPostProcessor>());
    }

    public CutCommand(String uuidGroup, List<String> uuidList,
            List<IPostProcessor> postProcessorList) {
        super();
        this.uuidGroup = uuidGroup;
        this.uuidList = uuidList;
        this.postProcessorList = postProcessorList;
        this.stationId = ChangeLogEntry.STATION_ID;
    }

    /*
     * @see sernet.verinice.interfaces.ICommand#execute()
     */
    @Override
    public void execute() {
        try {
            doExecute();
        } catch (PermissionException e) {
            if (log.isDebugEnabled()) {
                log.debug(e);
            }
            throw e;
        } catch (RuntimeException e) {
            log.error("RuntimeException while copying element", e);
            throw e;
        } catch (Exception e) {
            log.error("Error while copying element", e);
            throw new RuntimeException("Error while copying element", e);
        }

    }

    private void doExecute() throws CommandException {
        this.number = 0;
        elementChanges = new HashSet<>();
        List<CnATreeElement> elementList = createInsertList(uuidList);
        selectedGroup = getDao().findByUuid(uuidGroup,
                RetrieveInfo.getChildrenInstance().setParent(true).setProperties(true));
        Map<Integer, Integer> sourceDestMap = new Hashtable<>();
        boolean isPersonMoved = false;
        for (CnATreeElement element : elementList) {
            CnATreeElement movedElement = move(selectedGroup, element);
            // cut: source and dest is the same
            sourceDestMap.put(movedElement.getDbId(), movedElement.getDbId());
            for (String s : getPersonContainingTypeIDs()) {
                if (selectedGroup.getTypeId().equals(s)) {
                    isPersonMoved = true;
                    break;
                }
            }
        }
        if (isPersonMoved) {
            // TODO discardUserData() will remove all user data from memory, but
            // we only need to remove the user-specific data
            getCommandService().discardUserData();
        }
        updateScopeId(elementList);
        excecutePostProcessor(elementList, sourceDestMap);
    }

    private void excecutePostProcessor(List<CnATreeElement> elementList,
            Map<Integer, Integer> sourceDestMap) {
        if (getPostProcessorList() != null && !getPostProcessorList().isEmpty()) {
            List<Integer> copyElementIdList = new ArrayList<>(elementList.size());
            for (CnATreeElement element : elementList) {
                copyElementIdList.add(element.getDbId());
            }
            for (IPostProcessor postProcessor : getPostProcessorList()) {
                postProcessor.process(getCommandService(), copyElementIdList, sourceDestMap);
            }
        }
    }

    private void updateScopeId(List<CnATreeElement> elementList) throws CommandException {
        // set scope id of all elements and it's subtrees
        for (CnATreeElement element : elementList) {
            if (selectedGroup.getScopeId() != null) {
                UpdateScopeId updateScopeId = new UpdateScopeId(element.getDbId(),
                        selectedGroup.getScopeId());
                getCommandService().executeCommand(updateScopeId);
            } else if (!selectedGroup.isScope()) {
                log.warn("cut&paste target has no scopeID");
            }
        }
    }

    private CnATreeElement move(CnATreeElement group, CnATreeElement element)
            throws CommandException {
        CnATreeElement parentOld = element.getParent();
        parentOld.removeChild(element);

        // save old parent (switch to dao from command call because of Bug 918)
        getDao().merge(parentOld, false);

        ElementChange delete = new ElementChange(element, ChangeLogEntry.TYPE_DELETE);
        elementChanges.add(delete);

        element.setParentAndScope(group);

        group.addChild(element);

        if (element.getIconPath() == null) {
            element.setIconPath(group.getIconPath());
        }

        // save element (switch to dao from command call because of Bug 918)
        getDao().merge(element, false);
        getDao().flush();
        getDao().clear();

        ElementChange insert = new ElementChange(element, ChangeLogEntry.TYPE_INSERT);
        if (insert.getTime().equals(delete.getTime())) {
            Calendar plus1Second = Calendar.getInstance();
            plus1Second.add(Calendar.SECOND, 1);
            insert.setTime(plus1Second.getTime());
        }
        elementChanges.add(insert);

        number++;
        return element;
    }

    /**
     * Creates a list of elements. First all elements are loaded by UUID. A
     * child will be removed from the list if it's parent is already a member.
     * 
     * @param uuidList
     *            A list of element UUID
     * @return List of elements
     */
    protected List<CnATreeElement> createInsertList(List<String> uuidList) {
        List<CnATreeElement> tempList = new ArrayList<>();
        List<CnATreeElement> insertList = new ArrayList<>();
        int depth = 0;
        for (String uuid : uuidList) {
            CnATreeElement element = getDao().findByUuid(uuid,
                    RetrieveInfo.getChildrenInstance().setParent(true));
            createInsertList(element, tempList, insertList, depth);
        }
        return insertList;
    }

    private void createInsertList(CnATreeElement element, List<CnATreeElement> tempList,
            List<CnATreeElement> insertList, int depth) {
        if (!tempList.contains(element)) {
            tempList.add(element);
            if (depth == 0) {
                insertList.add(element);
            }
            if (element instanceof IISO27kGroup && element.getChildren() != null) {
                depth++;
                for (CnATreeElement child : element.getChildren()) {
                    createInsertList(child, tempList, insertList, depth);
                }
            }
        } else {
            insertList.remove(element);
        }
    }

    public String getUuidGroup() {
        return uuidGroup;
    }

    public void setUuidGroup(String uuidGroup) {
        this.uuidGroup = uuidGroup;
    }

    public List<String> getUuidList() {
        return uuidList;
    }

    public void setUuidList(List<String> uuidList) {
        this.uuidList = uuidList;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public List<IPostProcessor> getPostProcessorList() {
        return postProcessorList;
    }

    public void addPostProcessor(IPostProcessor task) {
        if (postProcessorList == null) {
            postProcessorList = new LinkedList<>();
        }
        postProcessorList.add(task);
    }

    private IBaseDao<CnATreeElement, Serializable> getDao() {
        if (dao == null) {
            dao = getDaoFactory().getDAO(CnATreeElement.class);
        }
        return dao;
    }

    @Override
    public void clear() {
        // changedElements are used on server side only !
        if (elementChanges != null) {
            elementChanges.clear();
        }
    }

    /*
     * @see sernet.verinice.interfaces.IChangeLoggingCommand#getStationId()
     */
    @Override
    public String getStationId() {
        return this.stationId;
    }

    /*
     * @see
     * sernet.verinice.interfaces.IChangeLoggingCommand#getChangedElements()
     */
    @Override
    public List<ElementChange> getChanges() {
        ArrayList<ElementChange> changes = new ArrayList<>(0);
        if (elementChanges != null && !elementChanges.isEmpty()) {
            changes.addAll(elementChanges);
        }
        return changes;
    }

    /*
     * @see sernet.verinice.interfaces.IChangeLoggingCommand#getChangeType()
     */
    @Override
    public int getChangeType() {
        return ChangeLogEntry.TYPE_UPDATE;
    }

    public static class InheritPermissions extends OverwritePermissions {

        /**
         * @param element
         * @param permissions
         */
        public InheritPermissions(CnATreeElement element) {
            super(element.getUuid());
        }

    }

}
