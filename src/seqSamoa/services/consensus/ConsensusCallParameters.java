/**
 *  SAMOA - PROTOCOL FRAMEWORK
 *  Copyright (C) 2005  Olivier Rütti (EPFL) (olivier.rutti@a3.epfl.ch)
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package seqSamoa.services.consensus;

import uka.transport.Transportable;

import framework.libraries.serialization.TList;

/**
 * The parameters of a call to Consensus
 */
public class ConsensusCallParameters {
    /**
     * <CODE>group</CODE> denotes the group of processes in which we execute
     * the consensus
     */
    public TList group;

    /**
     * <CODE>id</CODE> denotes the id of the consensus
     */
    public Transportable id;

    /**
     * Constructor
     * 
     * @param group
     *            the group of processes in which we execute the consensus
     * @param id
     *            the id of the consensus
     */
    public ConsensusCallParameters(TList group, Transportable id) {
        this.group = group;
        this.id = id;
    }
}
