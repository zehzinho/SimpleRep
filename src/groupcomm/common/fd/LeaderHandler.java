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
package groupcomm.common.fd;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.logging.Logger;

import framework.Constants;
import framework.GroupCommEventArgs;
import framework.GroupCommException;
import framework.PID;
import framework.libraries.Trigger;
import framework.libraries.serialization.TCollection;
import framework.libraries.serialization.THashMap;
import framework.libraries.serialization.THashSet;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TLinkedList;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TSet;

public class LeaderHandler {

    private PID myself;

    // Set of monitored group of processes
    private THashMap processes = new THashMap();

    private THashMap cProcesses = new THashMap();

    // Set of suspected processe
    private TSet suspected = new THashSet();

    // Object that routes outgoing events
    private Trigger trigger = null;

    private static final Logger logger = Logger.getLogger(LeaderHandler.class
            .getName());

    public LeaderHandler(Trigger trigger, PID myself) {

        logger.entering("LeaderHandler", "<constr> 4 parameters");
        this.trigger = trigger;
        this.myself = myself;
        logger.exiting("LeaderHandler", "<constr> 4 parameters");
    }

    private TLinkedList getAllProcesses() {
        TLinkedList result = new TLinkedList();

        TCollection allGroups = processes.keySet();
        Iterator it = allGroups.iterator();

        while (it.hasNext()) {
            TList l = (TList) it.next();
            int sizeL = l.size();

            for (int i = 0; i < sizeL; i++)
                if (!result.contains(l.get(i)))
                    result.add(l.get(i));
        }

        return result;
    }

    /**
     * Le handler de l'�v�nement <i>StartStopMonitor</i>. <br>
     * Cet �v�nement permet de d�buter ou d'arreter le failure detector pour
     * certains processus.
     * 
     * @param e
     *            <dl>
     *            <dt> start : Set </dt>
     *            <dd> L'ensemble de processus que nous devons commencer �
     *            monitorer. </dd>
     *            <dt> stop : Set </dt>
     *            <dd> L'ensemble de processus que nous devons arr�ter de
     *            monitorer.</dd>
     *            </dl>
     */
    public void handleStartStopMonitor(GroupCommEventArgs e)
            throws GroupCommException {

        logger.entering("LeaderHandler", "handleStartStopMonitor");
        TList start = (TList) e.removeFirst();
        TList stop = (TList) e.removeFirst();

        // Compute the list of processes that FD must start to monitor
        // and that FD must stop to monitor
        TList before = getAllProcesses();
        int beforeSize = before.size();

        if (!stop.isEmpty())
            stop(stop);

        if (!start.isEmpty())
            start(start);

        TList after = getAllProcesses();
        int afterSize = after.size();

        TSet startMonitor = new THashSet();
        TSet stopMonitor = new THashSet();
        for (int i = 0; i < beforeSize; i++)
            if (!after.contains(before.get(i)))
                stopMonitor.add(before.get(i));
        for (int i = 0; i < afterSize; i++)
            if (!before.contains(after.get(i)))
                startMonitor.add(after.get(i));

        triggerStartStop(startMonitor, stopMonitor);
        logger.exiting("LeaderHandler", "handleStartStopMonitor");
    }

    /**
     * Handler for event <i>Suspect</i>. Every time the Failure Detector
     * changes its suspect list, it triggers an event that this handler is bound
     * to.
     * 
     * @param e
     *            <dl>
     *            <dt> arg1 : Set[PID] </dt>
     *            <dd> The updated suspect list. </dd>
     *            </dl>
     */
    public void handleSuspect(GroupCommEventArgs e) {
        logger.entering("LeaderHandler", "handleSuspect");

        suspected = (TSet) e.removeFirst();
        TCollection allGroups = processes.keySet();
        Iterator it = allGroups.iterator();

        while (it.hasNext()) {
            TList l = (TList) it.next();
            PID leader = (PID) l.get(0);
            int sizeL = l.size();

            // Cherche le le nouveau leader du groupe
            // Le leader est le premier de la liste non suspect�
            for (int i = 0; i < sizeL && suspected.contains(leader); i++)
                leader = (PID) l.get(i);

            // Si le leader a chang� -> trigger l'�v�nement
            if (!leader.equals((PID) processes.get(l))) {
                processes.put(l, leader);
                triggerNewLeader(l, leader);
            }
        }

        logger.exiting("LeaderHandler", "handleSuspect");
    }

    /**
     * Commence � monitorer les processus dans <i>s</i>, si ils ne l'�taient
     * pas d�j�.
     */
    private void start(TList s) { // throws AlreadySchedulingException {
        logger.entering("LeaderHandler", "start");

        if (!cProcesses.containsKey(s)) {
            processes.put(s, s.get(0));
            cProcesses.put(s, new TInteger(1));
        } else {
            int n = ((TInteger) cProcesses.get(s)).intValue();
            cProcesses.put(s, new TInteger(n + 1));
        }

        triggerNewLeader(s, (PID) processes.get(s));

        logger.exiting("LeaderHandler", "start");
    }

    /**
     * Arr�te de monitorer les processus contenus dans <i>s</i>. Tous les
     * processus dans <i>s</i> doivent �tre monitor�s.
     */
    private void stop(TList s) { // throws NoSuchTaskException {
        logger.entering("LeaderHandler", "stop");
        int n = ((TInteger) cProcesses.get(s)).intValue();

        if (n == 1) {
            processes.remove(s);
            cProcesses.remove(s);
        } else
            cProcesses.put(s, new TInteger(n - 1));

        logger.exiting("LeaderHandler", "stop");
    }

    private void triggerNewLeader(TList group, PID leader) {
        logger.entering("LeaderHandler", "triggerNewLeader");
        GroupCommEventArgs e = new GroupCommEventArgs();
        e.addLast(leader);
        e.addLast(group);
        trigger.trigger(Constants.NEWLEADER, e);
        logger.exiting("LeaderHandler", "triggerNewLeader");
    }

    private void triggerStartStop(TSet start, TSet stop) {
        logger.entering("LeaderHandler", "triggerStartStopMonitor");
        GroupCommEventArgs e = new GroupCommEventArgs();
        e.addLast(start);
        e.addLast(stop);
        trigger.trigger(Constants.STARTSTOPMONITOR, e);
        logger.exiting("LeaderHandler", "triggerStartStopMonitor");
    }

    /**
     * Used for debugging. </br> Undocumented.
     */
    public void dump(OutputStream out) {
        PrintStream err = new PrintStream(out);
        err.println("========= LeaderHandler: dump =========");
        err.println(" Monitored Processes: " + processes);
        err.println("===================================");
    }
}
