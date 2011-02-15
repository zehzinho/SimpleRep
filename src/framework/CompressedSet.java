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

import java.io.IOException;
import java.util.LinkedList;

import uka.transport.DeepClone;
import uka.transport.MarshalStream;
import uka.transport.Transportable;
import uka.transport.UnmarshalStream;

/**
 * Stores a set of compressable object
 * The set takes up only a small constant amount
 * of memory: if possible only lower and upper
 * limits are memorized. 
 */
public class CompressedSet implements Transportable {

    // Limit is used to store a set of objects
    // that fullfilled the space between the inf limit
    // and the sup limit
    private static class Limit {
        public Compressable inf;

        public Compressable sup;

        public Limit(Compressable inf, Compressable sup) {
            this.inf = inf;
            this.sup = sup;
        }

        public String toString() {
            return new String("[ " + inf.toString() + " - " + sup.toString()
                    + " ]");
        }
    }

    // The limits describing all the objects contained
    // in the compressed Set
    private LinkedList allLimitsByClass;

    public CompressedSet() {
        allLimitsByClass = new LinkedList();
    }

    /**
     * Add an element to the compressed Set.
     * 
     * @param comp the element to add
     */
    public void add(Compressable comp) {
        int sizeAllLimitsByClass = allLimitsByClass.size();
        for (int i = 0; i < sizeAllLimitsByClass; i++) {
            LinkedList allLimits = (LinkedList) allLimitsByClass.get(i);

            if (((Limit) allLimits.getFirst()).inf.compareToCompressable(comp) 
                    != Compressable.NOT_COMPARABLE) {
                int sizeAllLimits = allLimits.size();
                boolean lastLimitModified = false;

                // ASSERT: The Limits are sorted by type and from
                // the smallest to the biggest.
                for (int j = 0; j < sizeAllLimits; j++) {
                    Limit limit = (Limit) allLimits.get(j);
                    int resInf = comp.compareToCompressable(limit.inf);
                    int resSup = comp.compareToCompressable(limit.sup);

                    if (resInf == Compressable.SMALLER && !lastLimitModified) {
                        allLimits.add(j, new Limit(comp, comp));
                        return;
                    }

                    if (resInf == Compressable.PREDECESSOR) {
                        limit.inf = comp;
                        if (lastLimitModified) {
                            ((Limit) allLimits.get(j - 1)).sup = limit.sup;
                            allLimits.remove(j);
                        }
                        return;
                    }

                    if (lastLimitModified
                            || ((resInf >= Compressable.EQUAL) && (resSup <= Compressable.EQUAL)))
                        return;

                    if (resSup == Compressable.SUCCESSOR) {
                        limit.sup = comp;
                        lastLimitModified = true;
                    }

                    // If comp is bigger than superior limit of the last Limit
                    // add a new Limit
                    if ((resSup == Compressable.BIGGER) && (j+1 == sizeAllLimits))
                        allLimits.addLast(new Limit(comp, comp));
                }
                return;
            }
        }
        
        // If the following instructions are executed, this means
        // that there is no list with Limits for the type of Comp
        // Below, we create such a list
        LinkedList allLimits = new LinkedList();
        allLimits.addLast(new Limit(comp, comp));
        allLimitsByClass.addLast(allLimits);
    }

    public boolean contains(Compressable comp) {
        int sizeAllLimitsByClass = allLimitsByClass.size();
        for (int i = 0; i < sizeAllLimitsByClass; i++) {
            LinkedList allLimits = (LinkedList) allLimitsByClass.get(i);
            
            if (((Limit) allLimits.getFirst()).inf.compareToCompressable(comp) 
                    != Compressable.NOT_COMPARABLE) {
                
                int sizeAllLimits = allLimits.size();
                for (int j = 0; j < sizeAllLimits; j++) {
                    Limit limit = (Limit) allLimits.get(j);
                    int resInf = limit.inf.compareToCompressable(comp);
                    int resSup = limit.sup.compareToCompressable(comp);

                    if ((resInf <= Compressable.EQUAL) &&
                            (resSup >= Compressable.EQUAL))
                        return true;
                }
                return false;
            }            
        }
        return false;
    }
    
    public String toString(){
        String result = new String();
        int size = allLimitsByClass.size();
          
        for (int i=0;i<size;i++)
            result = new String(result + allLimitsByClass.get(i)+"\n");
            
        return result;
    }

    public void marshal(MarshalStream arg0) throws IOException {
       throw new RuntimeException("Not supported by CompressedSet");
    }

    public void unmarshalReferences(UnmarshalStream arg0) throws IOException, ClassNotFoundException {
        throw new RuntimeException("Not supported by CompressedSet");        
    }

    public Object deepClone(DeepClone arg0) throws CloneNotSupportedException {
        throw new RuntimeException("Not supported by CompressedSet");
    }
}
