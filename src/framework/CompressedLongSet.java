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

import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Stores a set of non-negative long integers.
 * The subset of type 0, 1, 2, ..., k takes up only a small constant amount
 * of memory.
 */
public class CompressedLongSet
    //implements Serializable
{
    transient SortedSet set;
    long filled;

    // Does not implement the SortedSet interface.
    // Provides similar type safe methods instead.
    // XXX: provide a full set of methods.

    public CompressedLongSet() {
	set = new TreeSet();
	filled = 0;
    }

    public CompressedLongSet(CompressedLongSet right) {
	set = new TreeSet(right.set);
	filled = right.filled;
    }

    public void add(long i) {
	if (i < 0) {
	    throw new IllegalArgumentException();
	}
	if (i < filled) {
	    return;
	} else if (i == filled) {
	    do {
		filled++;
	    } while (set.remove(new Long(filled)));
	} else {
	    // assert i > filled;
	    set.add(new Long(i));
	}
    }

    public boolean contains(long i) {
	if (i < 0) {
	    throw new IllegalArgumentException();
	}
	return (i < filled) ? true : set.contains(new Long(i));
    }

    /** 
     * Length of full sequence starting from 0. In other words,
     * if this method returns k, then the set contains 0, 1, 2, ..., k-1.
     */
    public long getFilled() {
	return filled;
    }

    public String toString() {
	StringBuffer sb = new StringBuffer();
	sb.append("[");

	boolean first = true;
	if (filled > 0) {
	    if (filled == 1) {
		sb.append("0");
	    } else {
		sb.append("0-"+(filled-1));
	    }
	    first = false;
	}

	Iterator it = set.iterator();
	while (it.hasNext()) {
	    if (!first) {
		sb.append(",");
	    } else {
		first = false;
	    }
	    sb.append(it.next());
	}

	sb.append("]");
	return sb.toString();
    }

    public int hashCode() {
        return ( (int)((31*filled) & Integer.MAX_VALUE) + set.hashCode() );
    }

    public boolean equals(Object o) {
	if (!(o instanceof CompressedLongSet)) {
	    return false;
	}
	CompressedLongSet right = (CompressedLongSet)o;
	return filled == right.filled && set.equals(right.set);
    }

    private synchronized void writeObject(java.io.ObjectOutputStream s)
	throws java.io.IOException
    {
	s.defaultWriteObject();

	try {
	    s.writeLong(set.size());
	} catch (RuntimeException ex) {
	    try {
		System.out.println("set "+set);
		System.out.println("set.size() "+set.size());
	    } catch (Exception ex2) {
	    }
	    throw ex;
	}

	Iterator it = set.iterator();
	while (it.hasNext()) {
	    long next = ((Long)it.next()).longValue();
	    s.writeLong(next);
	}
    }

    private synchronized void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException
    {
	s.defaultReadObject();
	
	// XXX: add sanity checks
	long size = s.readLong();
	set = new TreeSet();
	for (long i=0; i<size; i++) {
	    long next = s.readLong();
	    set.add(new Long(next));
	}
    }

    /**
     * For testing.
     */
    public static void main(String[] args) {
	CompressedLongSet set = new CompressedLongSet();
	check(set.toString(), "[]");
	set.add(3);
	check(set.toString(), "[3]");
	set.add(0);
	check(set.toString(), "[0,3]");
	set.add(1);
	check(set.toString(), "[0-1,3]");
	set.add(2);
	check(set.toString(), "[0-3]");
	set.add(6);
	check(set.toString(), "[0-3,6]");
	check(""+set.contains(0), "true");
	check(""+set.contains(1), "true");
	check(""+set.contains(2), "true");
	check(""+set.contains(3), "true");
	check(""+set.contains(4), "false");
	check(""+set.contains(5), "false");
	check(""+set.contains(6), "true");
	check(""+set.contains(7), "false");
    }

    private static void check(String s1, String s2) {
	if (!s1.equals(s2)) {
	    System.out.println("The result of an operation should be\n  "
			       + s2 +"\nnot\n  "+ s1 + " !");
	    System.exit(1);
	}
    }

}
