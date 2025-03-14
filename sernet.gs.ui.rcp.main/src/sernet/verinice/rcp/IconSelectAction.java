/*******************************************************************************
 * Copyright (c) 2012 Daniel Murygin.
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
package sernet.verinice.rcp;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

import sernet.gs.ui.rcp.main.Activator;
import sernet.gs.ui.rcp.main.bsi.editors.EditorUtil;
import sernet.gs.ui.rcp.main.common.model.CnAElementFactory;
import sernet.hui.common.VeriniceContext;
import sernet.springclient.RightsServiceClient;
import sernet.verinice.interfaces.ActionRightIDs;
import sernet.verinice.interfaces.ICommandService;
import sernet.verinice.iso27k.rcp.JobScheduler;
import sernet.verinice.iso27k.rcp.Mutex;
import sernet.verinice.model.common.ChangeLogEntry;
import sernet.verinice.model.common.CnATreeElement;
import sernet.verinice.service.commands.UpdateIcon;

/**
 * @author Daniel Murygin <dm[at]sernet[dot]de>
 */
public class IconSelectAction
        implements IWorkbenchWindowActionDelegate, RightEnabledUserInteraction {

    private static final Logger LOG = Logger.getLogger(IconSelectAction.class);

    private Shell shell;

    private List<CnATreeElement> selectedElments;

    private static ISchedulingRule iSchedulingRule = new Mutex();

    /*
     * @see org.eclipse.ui.IWorkbenchWindowActionDelegate#init(org.eclipse.ui.
     * IWorkbenchWindow)
     */
    @Override
    public void init(IWorkbenchWindow window) {
        this.shell = window.getShell();
    }

    /*
     * @see org.eclipse.ui.IActionDelegate#run(org.eclipse.jface.action.IAction)
     */
    @Override
    public void run(IAction arg0) {
        try {
            final IconSelectDialog dialog = new IconSelectDialog(shell);
            if (Window.OK == dialog.open() && dialog.isSomethingSelected()) {
                WorkspaceJob updateIconJob = new WorkspaceJob(Messages.IconSelectAction_0) {
                    @Override
                    public IStatus runInWorkspace(final IProgressMonitor monitor) {
                        IStatus status = Status.OK_STATUS;
                        try {
                            monitor.setTaskName(Messages.IconSelectAction_1);
                            String iconPath = dialog.isDefaultIcon() ? null
                                    : dialog.getSelectedPath();

                            Activator.inheritVeriniceContextState();
                            // update our local copies
                            selectedElments.forEach(el -> el.setIconPath(iconPath));
                            UpdateIcon updateIcon = new UpdateIcon(
                                    selectedElments.stream().map(CnATreeElement::getUuid)
                                            .collect(Collectors.toSet()),
                                    iconPath, ChangeLogEntry.STATION_ID);
                            updateIcon = getCommandService().executeCommand(updateIcon);

                            // notify all views of change:
                            for (CnATreeElement element : updateIcon.getChangedElements()) {
                                CnAElementFactory.getModel(element).childChanged(element);
                                EditorUtil.changeEditorImage(element);
                            }
                        } catch (Exception e) {
                            LOG.error("Error while changing icons.", e); //$NON-NLS-1$
                            status = new Status(IStatus.ERROR, "sernet.verinice.rcp", //$NON-NLS-1$
                                    Messages.IconSelectAction_3, e);
                        }
                        return status;
                    }
                };
                JobScheduler.scheduleJob(updateIconJob, iSchedulingRule);
            }
        } catch (Exception e) {
            LOG.error(Messages.IconSelectAction_4, e);
        }
    }

    /*
     * @see
     * org.eclipse.ui.IActionDelegate#selectionChanged(org.eclipse.jface.action.
     * IAction, org.eclipse.jface.viewers.ISelection)
     */
    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        if (action.isEnabled()) {
            action.setEnabled(checkRights());
        }

        if (selection instanceof ITreeSelection) {
            ITreeSelection treeSelection = (ITreeSelection) selection;
            List<?> selectionList = treeSelection.toList();
            selectedElments = new ArrayList<>(selectionList.size());
            for (Object object : selectionList) {
                if (object instanceof CnATreeElement) {
                    selectedElments.add((CnATreeElement) object);
                }
            }
        }
    }

    /*
     * @see org.eclipse.ui.IWorkbenchWindowActionDelegate#dispose()
     */
    @Override
    public void dispose() {
        // nothing to do here
    }

    /*
     * @see sernet.verinice.interfaces.RightEnabledUserInteraction#getRightID()
     */
    @Override
    public String getRightID() {
        return ActionRightIDs.CHANGEICON;
    }

    private ICommandService getCommandService() {
        return (ICommandService) VeriniceContext.get(VeriniceContext.COMMAND_SERVICE);
    }
}
