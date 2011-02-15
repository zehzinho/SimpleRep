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
package framework;

/**
 * Class defining the main constants of the GroupComm stack. </br>
 */
public class Constants{
    /**
    * Event identifiers
    */
    public static final int DUMP = 1;
    public static final int INIT = 2;
    public static final int SHUTDOWN = 3;

    public static final int ACCEPTED = 4;
    public static final int CONNECTED = 5;
    public static final int CLOSED = 6;
    public static final int BROKEN = 7;
    public static final int RECV = 8;
    public static final int READY4NEXTMESSAGE = 9;

    public static final int PT2PTSEND = 10;
    public static final int PT2PTDELIVER = 11;
    public static final int JOINREMOVELIST = 12;
    public static final int SUSPECT2 = 27;

    public static final int PROPOSE = 13;
    public static final int DECIDE = 14;

    public static final int STARTSTOPMONITOR = 15;
    public static final int SUSPECT = 16;
    public static final int ALIVE = 17;
    public static final int NEWLEADER = 28; // added by O. Rutti

    public static final int UDPRECEIVE = 18;
    public static final int UDPSEND = 19;

    public static final int ADELIVER = 20;
    public static final int ABCAST = 21;
    public static final int AGCAST = 22;
    public static final int NEW_VIEW = 23;
//    public static final int GMPDELIVER = 24; // added by ofaurax
    public static final int JOIN = 25; // added by ofaurax
    public static final int REMOVE = 26; // added by ofaurax
    
    public static final int FIFOSEND = 31; // added by O. Rutti
    public static final int FIFODELIVER = 32; // added by O Rutti
    
    public static final int COSEND = 33; // added by O. Rutti
    public static final int CODELIVER = 34; // added by O. Rutti

    /**
    * Identifiers of message types
    */
    public static final int AM = 1;
    public static final int ADD = 2;
    public static final int REM = 3;

   //TODO: is there a better way to share the threadgroup ?
   public static final ThreadGroup THREADGROUP = new CleanCrashThreadGroup("FortikaThGr");

    // ADDED FOR CRASH-RECOVERY
    /**
    * Event identifiers
    */
    //public static final int STARTSTOPMONITOR   = 1;
    public static final int RECOVERY           = 100;
    //public static final int UDPSEND            = 3;
    public static final int TRUST_SE           = 101;
    public static final int TRUST_SU           = 102;
    //public static final int PROPOSE            = 6;
    //public static final int ADELIVER           = 7;
    //public static final int DECIDE             = 8;
    //public static final int PT2PTSEND          = 9;
    //public static final int PT2PTDELIVER       = 10;
    //public static final int UDPRECEIVE         = 11;
    public static final int KILLMESSAGE        = 103;
    public static final int UPDATESTATE        = 104;
    public static final int NB_COMMITS         = 105;

    //public static final int GOSSIPR         = 5;
    //public static final int GOSSIPM         = 6;
    //public static final int STATE           = 7;

    // Key for scheduling the Timers
    public static final Integer GOSSIP      = new Integer(1);
    //TODO: Remove???

    /**
    * Constants for the Storage interface
    */
    public static final int LOG_ABCAST = 1;
    public static final int LOG_CONSENSUS = 2;
    public static final int LOG_FDSE = 3;
    public static final int LOG_FDSU = 4;
}
