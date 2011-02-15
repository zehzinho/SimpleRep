/*
 * Common code protocols for composition using Appia and Cactus
 * (c)  Distributed Systems Laboratory. EPFL. Switzerland
 */
// Transportable class for Boolean
package framework.libraries.serialization;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

import uka.transport.Transportable;

public class TGLinkedList<Type extends Transportable> implements Cloneable, Transportable {
	//TODO: To remove when extending LinkedList
	LinkedList<Type> contents;
	public TGLinkedList(){
		contents = new LinkedList<Type>();
	}
	
	public TGLinkedList(TList list){
		this();
		Iterator it = list.iterator();
		while(it.hasNext()){
			contents.add((Type) it.next());
		}
	}
	public void addFirst(Type o){
		contents.addFirst(o);
	}
	public void addLast(Type o){
		contents.addLast(o);
	}
	public Type removeFirst(){
		return contents.removeFirst();
	}
	public Type removeLast(){
		return contents.removeLast();
	}
    public Type getFirst(){
        return contents.getFirst();
    }
    public Type getLast(){
        return contents.getLast();
    }
    public void add(Type o) {
        addLast(o);
    }
    public void addAll(TCollection col) {
        Iterator it = col.iterator();
        while(it.hasNext()){
            addLast((Type) it.next());
        }
    }
    public void clear() {
        contents.clear();
    }
    public boolean contains(Type o) {
        return contents.contains(o);
    }
    public boolean containsAll(TCollection col) {
        Iterator it= col.iterator();
        boolean result = true;
        while(it.hasNext() && result){
            result = result && contents.contains(it.next());
        }
        return result;
    }
    public Type get(int index) {
        return contents.get(index);
    }
    public int indexOf(Type o) {
        return contents.indexOf(o);
    }
    public boolean isEmpty() {
        return contents.isEmpty();
    }
    public Iterator<Type> iterator() {
        return contents.iterator();
    }
    public boolean remove(Type o) {
        return contents.remove(o);
    }
    public Type remove(int idx) {
        return contents.remove(idx);
    }
    public void removeAll(TCollection col) {
        Iterator it= col.iterator();
        while(it.hasNext()){
            contents.remove(it.next());
        }
    }
    public Type set(int idx, Type o) {
        return contents.set(idx, o);
    }
    public int size() {
        return contents.size();
    }
    public String toString()
    {
        return ("TGLinkedList{" + contents + "}");
    }
    
    //End: to remove

    public boolean equals(Object o){
    	if (!(o instanceof TGLinkedList))
    		return false;
    	
    	TGLinkedList tgl = (TGLinkedList) o;
    	
    	return contents.equals(tgl.contents);
    }

    public int compareTo(Object o){
        throw new InternalError("TGLinkedList not implementing compareTo");
    }

    public Object clone(){
        TGLinkedList clone;
		try {
			clone = (TGLinkedList) super.clone();
		} catch (CloneNotSupportedException e) {
            throw new InternalError();
		}
        LinkedList<Type> clone_contents = new LinkedList<Type>(contents); 
        clone.contents = clone_contents;
        return clone;
    }

    /**
     *  Methods defined by the Transportable interface
     */
    
    // Size of primitive fields
    protected static final int _SIZE = uka.transport.BasicIO.SIZEOF_int;

    // Number of elements
    private int _nbelements;

   /** Used by uka.transport.UnmarshalStream to unmarshal the object */
   public  TGLinkedList(uka.transport.UnmarshalStream _stream)
	 throws java.io.IOException, ClassNotFoundException
   {
	 this(_stream, _SIZE);
	 _stream.accept(_SIZE);
   }

   protected TGLinkedList(uka.transport.UnmarshalStream  _stream, int _size)
	 throws java.io.IOException, ClassNotFoundException
   {
	 _stream.request(_size); 
	 byte[] _buffer = _stream.getBuffer();
	 int    _pos    = _stream.getPosition();
	 _nbelements = uka.transport.BasicIO.extractInt(_buffer, _pos);
	 _pos += uka.transport.BasicIO.SIZEOF_int;
   }

   /** Method of interface Transportable, it must be declared public.
	   It is called from within UnmarshalStream after creating the 
	   object and assigning a stream reference to it. */
   public void unmarshalReferences(uka.transport.UnmarshalStream _stream)
	 throws java.io.IOException, ClassNotFoundException
   {
       contents = new LinkedList<Type>();
	   for(int i = 0; i < _nbelements; i++){
		   contents.addLast((Type) _stream.readObject());
	   }
   }

   /** Called directly by uka.transport.MarshalStream */
   public void marshal(uka.transport.MarshalStream _stream)
	 throws java.io.IOException
   {
	 _stream.reserve(_SIZE);
	 byte[] _buffer = _stream.getBuffer();
	 int    _pos    = _stream.getPosition();
	 marshalPrimitives(_buffer, _pos);
	 _stream.deliver(_SIZE);
	 marshalReferences(_stream);
   }

   protected void marshalPrimitives(byte[] _buffer, int _pos)
	 throws java.io.IOException
   {
	 _nbelements = contents.size();
	 _pos = uka.transport.BasicIO.insert(_buffer, _pos, _nbelements);
   }

   protected void marshalReferences(uka.transport.MarshalStream _stream)
	 throws java.io.IOException
   {
	   ListIterator it = contents.listIterator();
	   for(int i = 0; i < _nbelements; i++){
		   _stream.writeObject(it.next());
	   }
   }

   public final Object deepClone(uka.transport.DeepClone _helper)
	 throws CloneNotSupportedException
   {
	 Object _copy = clone();
	 _helper.add(this, _copy);
	 ((TGLinkedList) _copy).deepCloneReferences(_helper);
	 return _copy;
   }

   /** Clone all references to other objects. Use the 
	   DeepClone to resolve cycles */
   protected void deepCloneReferences(uka.transport.DeepClone _helper)
	 throws CloneNotSupportedException
   {
	   LinkedList<Type> aux = new LinkedList<Type>();
	   ListIterator it = this.contents.listIterator();
	   while (it.hasNext()) {
		   aux.add((Type) _helper.doDeepClone(it.next()));
	   }
	   this.contents = aux;
   }
}
