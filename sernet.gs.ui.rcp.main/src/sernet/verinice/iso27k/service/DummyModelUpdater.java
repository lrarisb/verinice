/*******************************************************************************
 * Copyright (c) 2010 Daniel Murygin.
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
package sernet.verinice.iso27k.service;

import sernet.verinice.model.common.CnATreeElement;

/**
 * @author Daniel Murygin <dm@sernet.de>
 *
 */
public class DummyModelUpdater implements IModelUpdater {

    /* (non-Javadoc)
     * @see sernet.verinice.iso27k.service.IModelUpdater#childAdded(sernet.verinice.iso27k.model.Group, sernet.gs.ui.rcp.main.common.model.CnATreeElement)
     */
    public void childAdded(CnATreeElement group, CnATreeElement element) {
    }

    /* (non-Javadoc)
     * @see sernet.verinice.iso27k.service.IModelUpdater#reload()
     */
    @Override
    public void reload() {
    }

}
