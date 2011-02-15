/**
*  Fortika - Robust Group Communication
*  Copyright (C) 2002-2006  Sergio Mena de la Cruz (EPFL) (sergio.mena@epfl.ch)
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
package framework.libraries;

import uka.transport.Transportable;
import framework.libraries.serialization.TCollection;
import framework.libraries.serialization.TList;

/** 
 * Interface defining the methods to store Objects to stable storage. All 
 * <i>common code</i> protocols that need to use the stable storage 
 * (e.g. hard disk), need to accept a parameter of this type in their 
 * constructor.
 */
public interface StableStorage {

    /** 
     * This method stores an object in the stable storage. In order to retrieve it later on, 
     *  a <i>key</i> and a <i>protocol key</i> are passed as parameters. Both keys act 
     * as the "primary key" (in the databases sense)  of the object being stored.
     * 
     * If paramater <i>unique</i> is <i>true</i>, any previous value stored with the 
     * same <i>key</i> and <i>protocol key</i> will be lost. However, if
     * <i>unique</i> is <i>true</i>, all values are kept.
     *  
     * @param protKey The protocol key. Typically, every protocol uses the same key 
     * all the time. Different protocols should use different protocol keys to avoid 
     * side effects among them.  
     * @param  key The object's key. This key identifies the object being stored among 
     * other objects stored by the same protocol.
     * @param  log The object to store.
     * @param unique If true, all previous logged objects with the same values for 
     * both keys will be erased.
     */
    void store(int protKey, long key, Transportable log, boolean unique);

	/** 
	 * This method retrieves an object previously stored in the stable storage. The 
	 * <i>key</i> and <i>protocol key</i> necessary to identify the object 
	 * are passed as parameters. Both keys act as the "primary key" (in the 
	 * databases sense)  of the object to retrieve.
	 * 
	 * If there are several objects referenced by the pair (<i>key</i>, <i>protocol 
	 * key</i>) this method only retrieves the most recently stored object. This 
	 * situation is possible when those values were stored with parameter 
	 * <i>unique</i> set to <i>false</i>.
	 * 
     * @param protKey The protocol key. Typically, every protocol uses the same key 
     * all the time. Different protocols should use different protocol keys to avoid 
     * side effects among them.  
     * @param  key The object's key. This key identifies the object to retrieve among 
     * other objects stored by the same protocol.
     * @return null if key not found, otherwise the last object stored with these keys.
     */
    Transportable retrieve(int protKey, long key);

    /** 
	 * This method returns in a <i>List</i> all objects previously stored in the stable storage with  
	 * the <i>key</i> and <i>protocol key</i> passed as parameters. Both keys act as the 
	 * "primary key" (in the databases sense)  of the object to retrieve.
     * 
     * @param protKey The protocol key. Typically, every protocol uses the same key 
     * all the time. Different protocols should use different protocol keys to avoid 
     * side effects among them.  
     * @param  key The object's key. This key identifies the objects to retrieve among 
     * other objects stored by the same protocol.
     * @return empty List if key not found, otherwise a List with the values retrieved with the 
     * given keys (ordered from older to most recent log entry.)
     */
    TList retrieveAll(int protKey, long key);

    /** 
     * This method deletes all log entries identified by <i>key</i> and each of the <i>protocol key</i>s 
     * contained in the second parameter.
     * 
     * @param protKey The protocol key. Typically, every protocol uses the same key 
     * all the time. Different protocols should use different protocol keys to avoid 
     * side effects among them.  
     * @param  keys A collection containing all keys to delete. 
     */
    void delete(int protKey, TCollection keys);

	/** 
	 * This method deletes all log entries identified by <i>key</i> and <i>protocol key</i> passed 
	 * as parameters.
	 * 
     * @param protKey The protocol key. Typically, every protocol uses the same key 
     * all the time. Different protocols should use different protocol keys to avoid 
     * side effects among them.  
     * @param  key The key of the object to delete. This key identifies the object(s) to delete.
	 */
    void delete(int protKey, long key);

    /** 
     * This method erases all log entries. <b>It is very dangerous, so use it with care</b> 
     */
    void clear();

    /** 
     * Compact the stable storage. This operation costly but necessary. It garbage collects removed 
     * objects in the stable storage file.
     */
    void trim();

    /** 
     * To properly close the log file(s). In stacks with infinite runs only, this method is not very 
     * useful.
     */
    void close();

    /** 
     * For debug only -- Not documented 
     */
    void dump();
}
