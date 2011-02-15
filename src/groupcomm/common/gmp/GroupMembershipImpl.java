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
package groupcomm.common.gmp;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import framework.Constants;
import framework.GroupCommEventArgs;
import framework.GroupCommException;
import framework.GroupCommMessage;
import framework.PID;
import framework.libraries.Trigger;
import framework.libraries.serialization.TArrayList;
import framework.libraries.serialization.TBoolean;
import framework.libraries.serialization.THashSet;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TSet;

/**
 * Common code for the Group Membership Layer.
 * <br />
 * Events handled :
 * <ul>
 * <li>Init</li>
 * <li>Join</li>
 * <li>Remove</li>
 * <li>Pt2PtDeliver</li>
 * <li>GDeliver (or ADeliver)</li>
 * </ul>
 * Events provided :
 * <ul>
 * <li>Agcast (or Abcast)</li>
 * <li>JoinRemoveList</li>
 * <li>Pt2PtSend</li>
 * <li>NewView</li>
 * <li>GmpDeliver</li>
 * </ul>
 * @author Olivier FAURAX
 * @see groupcomm.appia.gmp
 * @see groupcomm.cactus.gmp
 */
public class GroupMembershipImpl {
	private PID myself;
	// initialized ?
	private boolean init = false;
	// do we have some processes when init ?
	private boolean initData = false;
	// Clause When of GDeliver
	private LinkedList whenADeliver = new LinkedList();
	// Logging
	private static final Logger logger =
		Logger.getLogger(GroupMembershipImpl.class.getName());
	// item for trigerring at the end of methods
	private static class TriggerItem {
		public int type;
		public GroupCommEventArgs args;

		public TriggerItem(int type, GroupCommEventArgs args) {
			this.type = type;
			this.args = args;
		}
	}

	/** List of known processes */
	protected TArrayList processes;

	/** Current view_id */
	protected int view_id = 0;

	/** The trigger used to launch events */
	protected Trigger gmp = null;

	/**
	 * Constructor
	 *
	 * @param trig the trigger used to launch events
	 */
	public GroupMembershipImpl(Trigger trig, PID myself) {
		logger.entering("GroupMembershipImpl", "<constr>");
        this.myself = myself;
		gmp = trig;
		logger.exiting("GroupMembershipImpl", "<constr>");
	}

        /**
	 * Return the current view
	 *
	 * @return    current view
	 */
         public TList getView(){
	        return processes;
	 }

	/**
	 * To handle init events
	 *
	 * @param ev the initial ensemble of processes.
	 * Must contains a {@link LinkedList} as first element.
	 * @throws InitException 
	 */
	public void handleInit(GroupCommEventArgs ev) throws GroupCommException {
		logger.entering("GroupMembershipImpl", "handleInit");
		// assert initialized != true
		if (init)
			throw new GroupCommException("GroupMembershipImpl already initialized.");
		init = true;
		view_id = 0;
		TList arg1 = (TList) ev.removeFirst();
		if (!arg1.isEmpty()) {
			initData = true;
			processes = new TArrayList(arg1);
			//Look for duplicate processes in the group
			for (int i = 0; i < processes.size(); i++)
				for (int j = i + 1; j < processes.size(); j++)
					if (processes.get(i).equals(processes.get(j)))
						throw new GroupCommException(
							"Process"
								+ processes.get(i)
								+ " appears more than once in the group.");
			// trigger NEW_VIEW
			GroupCommEventArgs argNV = new GroupCommEventArgs();
			argNV.addLast(new TInteger(view_id));
			argNV.addLast((TList) processes.clone());
			logger.log(
				Level.FINE,
				"Sending NewView with view_id {0} containing {1}",
				new Object[] { new Integer(view_id), processes });

            // join-remove
            GroupCommEventArgs jrl = new GroupCommEventArgs();
            jrl.addLast (new THashSet(processes)); // join
            jrl.addLast (new THashSet()); // remove
            gmp.trigger (Constants.JOINREMOVELIST, jrl);    

			gmp.trigger(Constants.NEW_VIEW, argNV);
		}
		logger.exiting("GroupMembershipImpl", "handleInit");
	}

	/**
	 * To handle join events
	 *
	 * @param ev the process that join. Must contain a {@link PID} as first element.
	 */
	public void handleJoin(GroupCommEventArgs ev) {
		logger.entering("GroupMembershipImpl", "handleJoin");
		PID p = (PID) ev.removeFirst();

		GroupCommMessage msg = new GroupCommMessage();
		GroupCommEventArgs arg = new GroupCommEventArgs();
		arg.addLast(new TInteger(Constants.ADD));
		arg.addLast(msg);
		arg.addLast(p);
		logger.log(Level.FINE, "Sending Join Broadcast for PID: {0}", p);
		// gmp.trigger(Constants.AGCAST, arg );
		gmp.trigger(Constants.ABCAST, arg);
		logger.exiting("GroupMembershipImpl", "handleJoin");
	}

	/**
	 * To handle remove events
	 *
	 * @param P the process that is removed. Must contain a {@link PID} as first element.
	 */
	public void handleRemove(GroupCommEventArgs ev) {
		logger.entering("GroupMembershipImpl", "handleRemove");

		PID P = (PID) ev.removeFirst();

		GroupCommMessage msg = new GroupCommMessage();

		GroupCommEventArgs arg = new GroupCommEventArgs();
		arg.addLast(new TInteger(Constants.REM));
		arg.addLast(msg);
		arg.addLast(P);

		logger.log(Level.FINE, "Sending Remove Broadcast for PID: {0}", P);
		// gmp.trigger(Constants.AGCAST, arg );
		gmp.trigger(Constants.ABCAST, arg);
		logger.exiting("GroupMembershipImpl", "handleRemove");
	}

	/**
	 * To handle Pt2PtDeliver events
	 *
	 * @param ev the message. Must contains a {@link GroupCommMessage} of the form :
	 * {@link Integer}::{@link LinkedList}(processes)::{@link HashSet}(newProc)
	 * @throws GroupCommException if there are items of newProc that are not in processes
	 */
	public void handlePt2PtDeliver(GroupCommEventArgs ev)
		throws GroupCommException {
		logger.entering("GroupMembershipImpl", "handlePt2PtDeliver");
		//HashSet newProc = null;
		LinkedList toTrigger = new LinkedList();

		if (!initData) {
			GroupCommMessage m = (GroupCommMessage) ev.removeFirst();
			// m = view_id::processes::newProc

			GroupCommMessage m2 = m.cloneGroupCommMessage();
			// m2 = m = view_id::processes::newProc
			view_id = ((TInteger) m2.tunpack()).intValue();
			// m2 = processes::newProc
			processes = (TArrayList) m2.tunpack();
			//Look for duplicate processes in the group
			for (int i = 0; i < processes.size(); i++)
				for (int j = i + 1; j < processes.size(); j++)
					if (processes.get(i).equals(processes.get(j)))
						throw new GroupCommException(
							"Process"
								+ processes.get(i)
								+ " appears more than once in the group.");
			// m2 = newProc
			TSet newProc = (TSet) m2.tunpack();
			// m2 is empty

			// assert( newProc in processes )
			if (!processes.containsAll(newProc))
				throw new GroupCommException("GMP: No new process in NewProc list");

			initData = true;

			// trigger JOIN_REMOVE_LIST
			GroupCommEventArgs argJRL = new GroupCommEventArgs();
			argJRL.addLast(new THashSet(processes));
			argJRL.addLast(new THashSet());
			logger.log(
				Level.FINE,
				"Sending JoinRemoveList. Joining: ({0}), Leaving: (none)",
				processes);

			toTrigger.addLast(
				new TriggerItem(Constants.JOINREMOVELIST, argJRL));

            //I remove myself from the new processes
            newProc.remove(myself);
			// forall p in newProc
			GroupCommEventArgs arg;
			PID p;
			Iterator it = newProc.iterator();
			while (it.hasNext()) {
				p = (PID) it.next();
				arg = new GroupCommEventArgs();
				arg.addLast(m.cloneGroupCommMessage());
				arg.addLast(p);
				arg.addLast(new TBoolean(true)); // promisc

				Object[] temp = new Object[2];
				temp[0] = m;
				temp[1] = p;
				logger.log(
					Level.FINE,
					"Sending Pt2PtSend with message {0} to {1} in mode promisc",
					temp);

				// trigger PT2PTSEND
				toTrigger.addLast(new TriggerItem(Constants.PT2PTSEND, arg));
			}

			// trigger NEW_VIEW
			GroupCommEventArgs argNV = new GroupCommEventArgs();
			argNV.addLast(new TInteger(view_id));
			argNV.addLast((TList) processes.clone());

			Object[] temp = new Object[2];
			temp[0] = new Integer(view_id);
			temp[1] = processes;
			logger.log(
				Level.FINE,
				"Sending NewView with view_id {0} containing {1}",
				temp);

			toTrigger.addLast(new TriggerItem(Constants.NEW_VIEW, argNV));

			//Now we trigger all events scheduled
			while (!toTrigger.isEmpty()) {
				TriggerItem item = (TriggerItem) toTrigger.removeFirst();
				gmp.trigger(item.type, item.args);
			}

			// ADeliver WhenClause (as initData is true) (FLUSH !)
			while (!whenADeliver.isEmpty()) {
				handleADeliver((GroupCommEventArgs) whenADeliver.removeFirst());
			}
		}

		logger.exiting("GroupMembershipImpl", "handlePt2PtDeliver");
	}

	/**
	 * To handle adeliver (and adeliver) events
	 *
	 * @param ev the message delivered. Must contain an {@link Integer},
	 * the {@link GroupCommMessage} delivered, a {@link PID}
	 */
	public void handleADeliver(GroupCommEventArgs ev) throws GroupCommException {
		logger.entering("GroupMembershipImpl", "handleADeliver");
		TList oldProcesses = null;
		THashSet newProc = new THashSet();
		THashSet argtemp = null;
		GroupCommEventArgs arg = null;
		Object[] temp = new Object[2];
		LinkedList toTrigger = new LinkedList();
		GroupCommEventArgs argNV = new GroupCommEventArgs();

		// When clause: if we can't treat the event now, we store it for later.
		if (!initData) {
			whenADeliver.addLast(ev);
		} else {
			int type = ((TInteger) ev.get(0)).intValue();
			//GroupCommMessage evmsg = (GroupCommMessage)ev.get(1);
			PID m = (PID) ev.get(2);

			switch (type) {
				case Constants.ADD :
					if (!processes.contains(m)) {
						oldProcesses = (TList) processes.clone();
						processes.add(m);
						view_id++;

						// newProc = processes - oldProcesses;
						TList newProcTemp = (TList) processes.clone();
						newProcTemp.removeAll(oldProcesses);

						newProc = new THashSet(newProcTemp);

						// argtemp = {m}
						argtemp = new THashSet();
						argtemp.add(m);

						arg = new GroupCommEventArgs();
						arg.addLast(argtemp);
						arg.addLast(new THashSet());

						logger.log(
							Level.FINE,
							"Sending JoinRemoveList. Joining: ({0}), Leaving: (none)",
							argtemp);

						toTrigger.addLast(
							new TriggerItem(Constants.JOINREMOVELIST, arg));

						// msg = view_id::processes::newProc
						GroupCommMessage msg = new GroupCommMessage();

						msg.tpack((TSet) newProc.clone());

						msg.tpack((TArrayList) processes.clone());
						msg.tpack(new TInteger(view_id));

						// forall p in newProc
						PID p;
						Iterator it = newProc.iterator();
						while (it.hasNext()) {
							p = (PID) it.next();
							arg = new GroupCommEventArgs();
							arg.addLast(msg.cloneGroupCommMessage());
							arg.addLast(p);
							arg.addLast(new TBoolean(true)); // promisc

							temp[0] = msg;
							temp[1] = p;
							logger.log(
								Level.FINE,
								"Sending Pt2PtSend with message {0} to {1} in mode promisc",
								temp);

							// trigger PT2PTSEND
							toTrigger.addLast(
								new TriggerItem(Constants.PT2PTSEND, arg));
						}

						// trigger NEW_VIEW
						argNV = new GroupCommEventArgs();
						argNV.addLast(new TInteger(view_id));
						argNV.addLast((TList) processes.clone());

						temp[0] = new Integer(view_id);
						temp[1] = processes;
						logger.log(
							Level.FINE,
							"Sending NewView with view_id {0} containing {1}",
							temp);

						toTrigger.addLast(
							new TriggerItem(Constants.NEW_VIEW, argNV));
					} else {
						System.err.println(
							"Warning: trying to join a process twice. "
								+ "Second <join> will be ignored.");
					}
					break;

				case Constants.REM :
					if (processes.contains(m)) {
						processes.remove(m);
						view_id++;

						// argtemp = {m}
						argtemp = new THashSet();
						argtemp.add(m);

						// trigger join_remove_list	
						arg = new GroupCommEventArgs();
						arg.addLast(new THashSet());
						arg.addLast(argtemp);

						toTrigger.addLast(
							new TriggerItem(Constants.JOINREMOVELIST, arg));

						// trigger NEW_VIEW
						argNV = new GroupCommEventArgs();
						argNV.addLast(new TInteger(view_id));
						argNV.addLast((TList) processes.clone());

						temp = new Object[2];
						temp[0] = new Integer(view_id);
						temp[1] = processes;
						logger.log(
							Level.FINE,
							"Sending NewView with view_id {0} containing {1}",
							temp);

						toTrigger.addLast(
							new TriggerItem(Constants.NEW_VIEW, argNV));
					}
					break;

				default :
					// 		arg = new GroupCommEventArgs();
					// 		arg.addLast(m);

					// 		logger.log(Level.FINE,
					// 			   "Sending GMPDeliver. Message: {0}",
					// 			   m);

					// 		toTrigger.addLast(new TriggerItem(Constants.GMPDELIVER, arg));
					throw new GroupCommException(
						"GroupMembershipImpl: Handle adeliver: Unknown message type");

			} // switch

			//Now we trigger all events scheduled
			while (!toTrigger.isEmpty()) {
				TriggerItem item = (TriggerItem) toTrigger.removeFirst();
				gmp.trigger(item.type, item.args);
			}
		} // if else (whenClause)

		logger.exiting("GroupMembershipImpl", "handleADeliver");

	}

	/**
	 * Print the current state of the layer.
	 *
	 * @param out The output stream used for showing infos
	 */
	public void dump(OutputStream out) {
		PrintStream err = new PrintStream(out);
		err.println("======== GroupMembershipImpl debug infos ============");
		err.println(" Initialized: " + String.valueOf(init));
		err.println(" Initdata: " + String.valueOf(initData));
		if (initData) {
			err.println(" View id: " + view_id);
			Iterator it = processes.iterator();
			PID pid;
			while (it.hasNext()) {
				pid = (PID) it.next();
				err.println("\t" + pid.toString());
			}
		}
		err.println("=====================================================");
	}
}
