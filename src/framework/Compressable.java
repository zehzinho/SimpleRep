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

public interface Compressable {
    static final int NOT_COMPARABLE = 0;
    static final int SMALLER = 1;
    static final int PREDECESSOR = 2;
    static final int EQUAL = 3;
    static final int SUCCESSOR = 4;
    static final int BIGGER = 5;
    
    /**
     * Compare two objects. Consider the call o1.comparetTo(o2),  
     * the result can be the following:
     * 
     *         0: the two objects can not be compared
     *         1: o1 is smaller than o2 (and not the immediate predecessor of o2)
     *         2: o1 is the immediate predecessor of o2
     *         3: o1 is equal to  o2 
     *         4: o1 is the successor of o2
     *         5: o1 is bigger than o2 (and not the immediate successor of o2)
     * 
     * @param o
     * @return
     */
    public int compareToCompressable (Object o);
}
