package framework.libraries.serialization;

import org.jdom.Element;

import uka.transport.Transportable;

@SuppressWarnings("serial")
public class TElement implements Transportable {
	private Element content;
	
	public TElement(Element element) {
		content = element;
	}
	
	public Element getElement() {
		return this.content;
	}
	
    /**
     *  Methods defined by the Transportable interface
     */
    
    // Size of primitive fields
    protected static final int _SIZE = uka.transport.BasicIO.SIZEOF_int;

    // Number of elements
    private int _nbelements;

   /** Used by uka.transport.UnmarshalStream to unmarshal the object */
   public  TElement(uka.transport.UnmarshalStream _stream)
	 throws java.io.IOException, ClassNotFoundException
   {
	 this(_stream, _SIZE);
	 _stream.accept(_SIZE);
   }

   protected TElement(uka.transport.UnmarshalStream  _stream, int _size)
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
	   String name = ((TString) _stream.readObject()).toString();
	   String value = ((TString) _stream.readObject()).toString();

	   this.content = new Element(name);
	   this.content.setText(value);
	   
	   for (int i=0; i<_nbelements; i++){
		   TElement el = (TElement) _stream.readObject(); 
		   this.content.addContent(el.getElement());
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
		 _nbelements = content.getChildren().size();
		 _pos = uka.transport.BasicIO.insert(_buffer, _pos, _nbelements);
   }

   protected void marshalReferences(uka.transport.MarshalStream _stream)
	 throws java.io.IOException
   {
	   TString name = new TString(content.getName());
	   TString value = new TString(content.getText());
	   
	   _stream.writeObject(name);
	   _stream.writeObject(value);
	   for (int i=0; i<_nbelements; i++)
		   _stream.writeObject(new TElement((Element) content.getChildren().get(i)));
   }

   public final Object deepClone(uka.transport.DeepClone _helper)
	 throws CloneNotSupportedException
   {
	   throw new CloneNotSupportedException();
   }

   /** Clone all references to other objects. Use the 
	   DeepClone to resolve cycles */
   protected void deepCloneReferences(uka.transport.DeepClone _helper)
	 throws CloneNotSupportedException
   {
	   throw new CloneNotSupportedException();
   }
}
