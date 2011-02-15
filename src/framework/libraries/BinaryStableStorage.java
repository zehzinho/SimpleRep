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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;

import uka.transport.Transportable;
import framework.libraries.serialization.TByteArray;
import framework.libraries.serialization.TCollection;
import framework.libraries.serialization.THashMap;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TLinkedList;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TLong;
import framework.libraries.serialization.TMap;

/**
 * This class implements the stable storage using Standard Java Serialization, 
 * and writing to two binary files.
 * 
 * For more information, see interface <i>StableStorage</i>
 * 
 * @author smenadel
 */
public class BinaryStableStorage implements StableStorage {

	/** 1st log file */
	protected DataOutputStream o1 = null;
	protected File logFile1 = null;
	protected FileDescriptor fd1 = null;
	/** 2nd log file */
	protected DataOutputStream o2 = null;
	protected File logFile2 = null;
	protected FileDescriptor fd2 = null;

	protected TMap state = null;
	protected boolean closed = true;

	protected static final byte UNIQUE = 0;
	protected static final byte NORMAL = 1;
	protected static final byte DELETE = 2;

	protected static class Entry {
		public int k1;
		public long k2;
		public byte type;
		public byte[] value;

		public Entry(int k1, long k2, byte type, byte[] value) {
			this.k1 = k1;
			this.k2 = k2;
			this.type = type;
			this.value = value;
		}
	}

	public BinaryStableStorage(String path) {
		try {
			String path1 = path;
			String path2 = path + ".bak";
			logFile1 = generateLogFile(path1);
			logFile2 = generateLogFile(path2);

			state = recoverFiles();

			//Trim 1st file
			clearFile(logFile1);
			writeFile(logFile1);
			//Trim 2nd file 
			clearFile(logFile2);
			writeFile(logFile2);

			// Open the log files
			openStreams();
			closed = false;
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	protected void openStreams() throws IOException {
		FileOutputStream fo1 = new FileOutputStream(logFile1, true);
		FileOutputStream fo2 = new FileOutputStream(logFile2, true);
		o1 = new DataOutputStream(fo1);
		o2 = new DataOutputStream(fo2);
		fd1 = fo1.getFD();
		fd2 = fo2.getFD();
	}

	protected TMap recoverFiles() throws IOException {
		// parse logFile1...
		TMap st1 = parse(logFile1);
		//TODO: IMPORTANT If process crashed while rewriting log1, 
		// it may parse well while being incomplete. As a result, we may lose data.
		// SOLUTION: sequence number part of the state.
		if (st1 == null) {
			// logFile1 was not correctly written (crash).
			System.err.println("WARNING: 1st log file is corrupted!");

			// parse logFile2...
			TMap st2 = parse(logFile2);

			if (st2 == null) {
				System.err.println(
					"ERROR: Failed to parse both log files.\n"
						+ "There is no way to recover with damaged logs.\n"
						+ "Closing application now...");
				System.exit(1);
			} else {
				// fix logFile1...
				copy(logFile2, logFile1);
				st1 = st2;
			}
		} else {
			// logFile1 was correctly written to disk.
			if (logFile1.lastModified() > logFile2.lastModified()) {
				// logFile2 is not up-to-date!
				copy(logFile1, logFile2);
			} else {
				// Test logFile2 integrity
				TMap st2 = parse(logFile2);
				if (st2 == null) {
					// logFile2 was not correctly written (crash).
					System.err.println("WARNING: 2nd log file is corrupted!");
					// fix logFile2...
					copy(logFile1, logFile2);
				}
			}
		}
		return st1;
	}

	/** 
	 * Create or open the log file given by filePath.
	 * Parent directories are created if needed. 
	 */
	protected File generateLogFile(String filePath) throws IOException {
		File logFile = new File(filePath);
		if (!logFile.exists()) {
			if (logFile.getParentFile() != null) {
				logFile.getParentFile().mkdirs();
			}
			logFile.createNewFile();
		}
		if (!logFile.canRead()) {
			throw new IOException(
				"ERROR: Log file \"" + filePath + "\" is not readable...");
		}
		if (!logFile.canWrite()) {
			throw new IOException(
				"ERROR: Log file \"" + filePath + "\" is not writable...");
		}
		return logFile;
	}

	/**
	 * Copy a file to another.
	 */
	protected void copy(File src, File dest)
		throws IOException, FileNotFoundException {
		DataInputStream original =
			new DataInputStream(new FileInputStream(src));
		DataOutputStream copy =
			new DataOutputStream(new FileOutputStream(dest));
		try {
			while (true) {
				byte b = original.readByte();
				copy.writeByte(b);
			}
		} catch (EOFException e) {
			copy.flush();
		}
		original.close();
		copy.close();
	}

	protected void writeEntry(
		DataOutputStream os,
		int key1,
		long key2,
		byte[] value,
		byte type) {

		try {
			//key1::key2::type::length::value::checksum
			os.writeInt(key1);
			os.writeLong(key2);
			os.writeByte(type);
			if (type != DELETE) {
				os.writeInt(value.length);
				os.write(value);
			}
			os.flush();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	protected Entry readEntry(DataInputStream is) throws IOException {
		//key1::key2::type::length::value::checksum
		int key1 = is.readInt();
		long key2 = is.readLong();
		byte type = is.readByte();
		byte[] value = null;
		if (type != DELETE) {
			int length = is.readInt();
			value = new byte[length];
			readIS(is, value);
		}
		return new Entry(key1, key2, type, value);
	}

	public void store(
		int protocolKey,
		long key,
		Transportable value,
		boolean unique) {
		try {
			byte[] valueB;
			valueB = DefaultSerialization.marshall(value);
			byte uniqueB = unique ? UNIQUE : NORMAL;

			updateState(state, protocolKey, key, valueB, uniqueB);
			writeEntry(o1, protocolKey, key, valueB, uniqueB);
			fd1.sync();
			writeEntry(o2, protocolKey, key, valueB, uniqueB);
			fd2.sync();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public void delete(int protocolKey, long key) {
		try {
			//Delete from memory
			updateState(state, protocolKey, key, null, DELETE);
			//Delete from files
			writeEntry(o1, protocolKey, key, null, DELETE);
			fd1.sync();
			writeEntry(o2, protocolKey, key, null, DELETE);
			fd2.sync();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public void delete(int protocolKey, TCollection keys) {
		Iterator it = keys.iterator();
		while (it.hasNext()) {
			delete(protocolKey, ((Long) it.next()).longValue());
		}
	}

	protected static void updateState(
		TMap st,
		int protKey,
		long key,
		byte[] value,
		byte type) {

		TLinkedList s;
		TMap protMap;
        TByteArray tvalue = new TByteArray(value);
		switch (type) {
			case UNIQUE :
				protMap = (TMap) st.get(new TInteger(protKey));
				if (protMap == null) {
					protMap = new THashMap();
					st.put(new TInteger(protKey), protMap);
				}
				s = new TLinkedList();
				s.addFirst(tvalue);
				protMap.put(new TLong(key), s);
				break;
			case NORMAL :
				protMap = (TMap) st.get(new TInteger(protKey));
				if (protMap == null) {
					protMap = new THashMap();
					st.put(new TInteger(protKey), protMap);
				}
				s = (TLinkedList) protMap.get(new TLong(key));
				if (s == null) {
					s = new TLinkedList();
					protMap.put(new TLong(key), s);
				}
				s.addFirst(tvalue);
				break;
			case DELETE :
				protMap = (TMap) st.get(new TInteger(protKey));
				if (protMap != null)
					protMap.remove(new TLong(key));
				break;
			default :
				System.err.println("BinaryLogger. Weird entry type:" + type);
				System.exit(1);
		}
	}

	protected TMap parse(File f) throws IOException {
		DataInputStream dis = new DataInputStream(new FileInputStream(f));
		try {
			TMap st = new THashMap();
			while (dis.available() > 0) {
				Entry e = readEntry(dis);
				updateState(st, e.k1, e.k2, e.value, e.type);
			}
			dis.close();
			return st;
		} catch (EOFException eofe) {
			dis.close();
			return null;
		}

	}

	/**
	 * Reads <b>exactly</b> <i>length</i> bytes from the inputStream, stores it
	 * into <i>b</i>, and returns the number of read bytes.</br>
	 * If the method returns -1, it means that the inputstream has been closed.
	 */
	protected static int readIS(DataInputStream is, byte[] b)
		throws EOFException, IOException {
		int i = 0, n;
		for (n = 0; n < b.length && i != -1; n += i) {
			i = is.read(b, n, b.length - n);
		}
		if (i == -1)
			throw new EOFException();
		return n;
	}

	public Transportable retrieve(int protocolKey, long key) {
		try {
			TMap protMap = (TMap) state.get(new TInteger(protocolKey));
			if (protMap == null)
				return null;
			TLinkedList s = (TLinkedList) protMap.get(new TLong(key));
			if (s == null)
				return null;
			// Decode it
			// Transorm it into the object
			return DefaultSerialization.unmarshall( ((TByteArray)s.getFirst()).byteValue() );
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}
		return null; // never reached!
	}

	public TList retrieveAll(int protocolKey, long key) {
		try {
			TLinkedList all = new TLinkedList();
			TMap protMap = (TMap) state.get(new TInteger(protocolKey));
			if (protMap == null)
				return all;
			TLinkedList s = (TLinkedList) protMap.get(new TLong(key));
			if (s == null)
				return all;

			Iterator it = s.iterator();
			while (it.hasNext()) {
				Transportable o = DefaultSerialization.unmarshall( ((TByteArray) it.next()).byteValue() );
				all.addLast(o);
			}
			return all;
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		return null; // never reached!
	}

	/** 
	 * Erase all log entries. <b>Use with care</b> 
	 */
	public void clear() {
		try {
			closeStreams();
			// Clear 1st file
			clearFile(logFile1);
			// Clear 2nd file
			clearFile(logFile2);
			// Clear memory
			state.clear();
			openStreams();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Compact the stable storage. This operation costly but necessary. It garbage collects removed
	 * objects in the stable storage file.
	 */
	public void trim() {
		try {
			closeStreams();
			//Trim 1st file
			clearFile(logFile1);
			writeFile(logFile1);
			//Trim 2nd file 
			clearFile(logFile2);
			writeFile(logFile2);
			openStreams();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	protected void writeFile(File f) throws IOException {
		FileOutputStream fos = new FileOutputStream(f);
		//FileDescriptor fd = fos.getFD();
		DataOutputStream dos = new DataOutputStream(fos);
		TCollection prots = state.keySet();
		Iterator it = prots.iterator();
		while (it.hasNext()) {
			TInteger protKey = (TInteger) it.next();
			TMap protMap = (TMap) state.get(protKey);
			TCollection keys = protMap.keySet();
			Iterator it2 = keys.iterator();
			while (it2.hasNext()) {
				TLong key = (TLong) it2.next();
				TLinkedList l = (TLinkedList) protMap.get(key);
				Iterator it3 = l.iterator();
				while (it3.hasNext()) {
					byte[] b = ((TByteArray) it3.next()).byteValue();
					writeEntry(
						dos,
						protKey.intValue(),
						key.longValue(),
						b,
						NORMAL);
				}
			}
		}
		//dos.flush();
		//fd.sync();
		dos.close();
	}

	/**
	 * To properly exit the application. Log file(s) will never again be needed.
	 */
	public void close() {
		try {
			if (!closed) {
				//Close the output streams
				closeStreams();
				//Remove files
				logFile1.delete();
				logFile2.delete();
				closed = true;
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	protected void clearFile(File f) throws IOException {
		f.delete();
		f.createNewFile();
	}
	protected void closeStreams() throws IOException {
		o1.close();
		o2.close();
	}

	/** 
	 * For debug only -- Not documented
	 */
	public void dump() {
		System.err.println("-----DUMP Binary storage-----");
		TCollection prots = state.keySet();
		Iterator it = prots.iterator();
		while (it.hasNext()) {
			TInteger protKey = (TInteger) it.next();
			TMap protMap = (TMap) state.get(protKey);
			TCollection keys = protMap.keySet();
			Iterator it2 = keys.iterator();
			while (it2.hasNext()) {
				TLong key = (TLong) it2.next();
				TLinkedList l = (TLinkedList) protMap.get(key);
				System.err.println(
					"ProtKey:"
						+ protKey
						+ ", Key: "
						+ key
						+ ". Size: "
						+ l.size());
			}
		}
		System.err.println("-----------------------------");
	}
}
