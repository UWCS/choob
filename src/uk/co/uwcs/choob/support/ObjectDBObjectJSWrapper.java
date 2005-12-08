package uk.co.uwcs.choob.support;

import java.lang.reflect.*;
import java.util.*;
import uk.co.uwcs.choob.support.*;
import org.mozilla.javascript.*;

public final class ObjectDBObjectJSWrapper implements ObjectDBObject {
	private Scriptable obj;
	
	public int dummy() {
		return 0;
	}
	
	public ObjectDBObjectJSWrapper(Object obj) throws ChoobException, NoSuchFieldException {
		if (obj == null) {
			throw new ChoobException("Can't wrap a null object!");
		}
		if (!(obj instanceof Scriptable)) {
			throw new ChoobException("Get out of here, that's not a JavaScript Scriptable object!");
		}
		this.obj = (Scriptable)obj;
		if (!ScriptRuntime.typeof(this.obj).equals("object")) {
			throw new ChoobException("JavaScript object of the wrong type; only objects with typeof = 'object' allowed.");
		}
		// We ignore the return value, but it will throw an exception if the
		// property is missing, which we want.
		getFieldValue("id");
	}
	
	public String getClassName() {
		try {
			Object ctor = getFieldValue("constructor");
			if (ctor instanceof Function) {
				ObjectDBClass cls = new ObjectDBClassJSWrapper((Function)ctor);
				return cls.getName();
			}
		} catch (NoSuchFieldException e) {
			// Do nothing.
		}
		return "";
	}
	
	public int getId() {
		try {
			return ((Number)getFieldValue("id")).intValue();
		} catch (NoSuchFieldException e) {
			// Should not occur (we check for "id" on creation).
		}
		return 0;
	}
	
	public void setId(int id) {
		try {
			setFieldValue("id", new Integer(id));
		} catch (NoSuchFieldException e) {
			// Should not occur (we check for "id" on creation).
		}
	}
	
	public String[] getFields() {
		List<String> fields = new LinkedList<String>();
		
		Scriptable proto = obj;
		while (proto != null) {
			Object[] props = proto.getIds();
			
			for (int i = 0; i < props.length; i++) {
				if (props[i] instanceof String) {
					fields.add((String)props[i]);
				} else if (props[i] instanceof Number) {
					fields.add(props[i].toString());
				} else {
					System.err.println("WARNING: [ObjectDBObjectJSWrapper.getFields] Unexpected property type: " + props[i].getClass().getName());
				}
			}
			
			proto = proto.getPrototype();
		}
		
		String[] sFields = new String[0];
		return fields.toArray(sFields);
	}
	
	public Type getFieldType(String name) throws NoSuchFieldException {
		Object val = getFieldValue(name);
		if (val == null) {
			System.err.println("WARNING: [ObjectDBObjectJSWrapper.getFieldType] Property '" + name + "' does not exist.");
			return null;
		}
		
		String type = ScriptRuntime.typeof(val);
		
		if (type.equals("undefined") || type.equals("object") || type.equals("function") || type.equals("xml")) {
			System.err.println("WARNING: [ObjectDBObjectJSWrapper.getFieldType] Property '" + name + "' of type '" + type + "' cannot be saved to the ObjectDB.");
			return null;
		}
		if (type.equals("string")) {
			return String.class;
		}
		if (type.equals("number")) {
			return Double.TYPE;
		}
		if (type.equals("boolean")) {
			return Boolean.TYPE;
		}
		System.err.println("WARNING: [ObjectDBObjectJSWrapper.getFieldType] Property '" + name + "' had unknown 'typeof' result: " + type);
		return null;
	}
	
	public Object getFieldValue(String name) throws NoSuchFieldException {
		return JSUtils.mapJSToJava(JSUtils.getProperty(obj, name));
	}
	
	public void setFieldValue(String name, Object value) throws NoSuchFieldException {
		JSUtils.setProperty(obj, name, JSUtils.mapJavaToJS(value));
	}
}
