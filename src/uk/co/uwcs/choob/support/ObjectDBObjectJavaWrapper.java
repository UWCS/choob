package uk.co.uwcs.choob.support;

import java.lang.reflect.*;
import java.util.*;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

public final class ObjectDBObjectJavaWrapper implements ObjectDBObject {
	private Object obj;
	
	public ObjectDBObjectJavaWrapper(Object obj) {
		this.obj = obj;
		getId();
	}
	
	public String getClassName() {
		return obj.getClass().getName();
	}

	public ObjectDBClass getODBClass() {
		return new ObjectDBClassJavaWrapper(getClass());
	}

	public int getId() {
		try {
			final Object obj2 = obj;
			return (Integer)AccessController.doPrivileged(new PrivilegedExceptionAction() {
					public Object run() throws NoSuchFieldException, IllegalAccessException {
						Field f = obj2.getClass().getField("id");
						return f.getInt(obj2);
					}
				});
		} catch (PrivilegedActionException e) {
			// Must be a NoSuchFieldException...
			throw new ObjectDBError("Class " + obj.getClass() + " does not have a unique 'id' property. Please add one.");
		}
	}
	
	public void setId(int id) {
		try {
			final Object obj2 = obj;
			final int val2 = id;
			AccessController.doPrivileged(new PrivilegedExceptionAction() {
					public Object run() throws NoSuchFieldException, IllegalAccessException {
						Field f = obj2.getClass().getField("id");
						f.setInt(obj2, val2);
						return null;
					}
				});
		} catch(PrivilegedActionException e) {
			// Must be a NoSuchFieldException...
			throw new ObjectDBError("Class " + obj.getClass() + " does not have a unique 'id' property. Please add one.");
		}
	}
	
	public String[] getFields() {
		List<String> fields = new LinkedList<String>();
		Field[] fieldObjs = obj.getClass().getFields();
		
		for (int i = 0; i < fieldObjs.length; i++) {
			fields.add(fieldObjs[i].getName());
		}
		
		String[] sFields = new String[0];
		return fields.toArray(sFields);
	}
	
	public Type getFieldType(String name) throws NoSuchFieldException {
		return obj.getClass().getField(name).getType();
	}
	
	public Object getFieldValue(String name) throws NoSuchFieldException, IllegalAccessException {
		return obj.getClass().getField(name).get(obj);
	}
	
	public void setFieldValue(String name, Object value) throws NoSuchFieldException, IllegalAccessException {
		obj.getClass().getField(name).set(obj, value);
	}
}
