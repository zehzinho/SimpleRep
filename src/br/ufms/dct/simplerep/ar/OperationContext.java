package br.ufms.dct.simplerep.ar;

import java.util.concurrent.ConcurrentHashMap;

public class OperationContext {
	private ConcurrentHashMap<String, Object> properties;

	public OperationContext() {
		properties = new ConcurrentHashMap<String, Object>();
	}

	public void set(String key, Object value) {
		properties.put(key, value);
	}

	public Object get(String key) {
		return properties.get(key);
	}

	public Object remove(String key) {
		return properties.remove(key);
	}
}
