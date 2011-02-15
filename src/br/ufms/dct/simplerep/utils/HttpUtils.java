package br.ufms.dct.simplerep.utils;

import java.io.ByteArrayInputStream;

import org.apache.http.entity.BasicHttpEntity;

public class HttpUtils {
	public static BasicHttpEntity string2BasicEntity (String str) {
		BasicHttpEntity entity = new BasicHttpEntity();
		entity.setContent(new ByteArrayInputStream(str.getBytes()));
		return entity;
	}
}
