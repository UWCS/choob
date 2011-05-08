package uk.co.uwcs.choob.support;

import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;

import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;

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

	@Override public String getClassName() {
		return makeObjetDbClass().getName();
	}

	private ObjectDBClass makeObjetDbClass() {
		try {
			Object ctor = getFieldValue("constructor");
			if (ctor instanceof Function) {
				return new ObjectDBClassJSWrapper(ctor);
			}
		} catch (NoSuchFieldException e) {
			throw new RuntimeException("This used to just return the empty string", e);
		}
		throw new RuntimeException("This used to just return the empty string");
	}

	@Override public int getId() {
		try {
			return ((Number)getFieldValue("id")).intValue();
		} catch (NoSuchFieldException e) {
			// Should not occur (we check for "id" on creation).
		}
		return 0; // XXX ?!
	}

	@Override public void setId(int id) {
		try {
			setFieldValue("id", new Integer(id));
		} catch (NoSuchFieldException e) {
			// Should not occur (we check for "id" on creation).
		}
	}

	@Override public String[] getFields() {
		List<String> fields = new LinkedList<String>();

		Scriptable proto = obj;
		while (proto != null) {
			Object[] props = proto.getIds();

			for (int i = 0; i < props.length; i++) {
				if (props[i] instanceof String) {
					String name = (String)props[i];
					// Don't save props starting "_".
					if (!name.startsWith("_")) {
						// Check we like the type of the prop...
						try {
							Type valType = getFieldType(name);
							if (valType != null) {
								fields.add(name);
							}
						} catch (NoSuchFieldException e) {
							// Should never happen here!
						}
					}
				} else if (props[i] instanceof Number) {
					// Don't do anything for numeric properties.
				} else {
					System.err.println("WARNING: [ObjectDBObjectJSWrapper.getFields] Unexpected property type: " + props[i].getClass().getName());
				}
			}

			proto = proto.getPrototype();
		}

		String[] sFields = new String[0];
		return fields.toArray(sFields);
	}

	@Override public Type getFieldType(String name) throws NoSuchFieldException {
		Object val = getFieldValue(name);
		if (val == null) {
			return null;
		}

		String type = ScriptRuntime.typeof(val);

		if (type.equals("undefined") || type.equals("object") || type.equals("function") || type.equals("xml")) {
			return null;
		}
		if (type.equals("string")) {
			return String.class;
		}
		if (type.equals("number")) {
			if (val instanceof Integer)
				return Integer.TYPE;
			if (val instanceof Long)
				return Long.TYPE;
			if (val instanceof Float)
				return Float.TYPE;
			return Double.TYPE;
		}
		if (type.equals("boolean")) {
			return Boolean.TYPE;
		}
		return null;
	}

	@Override public Object getFieldValue(String name) throws NoSuchFieldException {
		return JSUtils.mapJSToJava(JSUtils.getProperty(obj, name));
	}

	@Override public void setFieldValue(String name, Object value) throws NoSuchFieldException {
		JSUtils.setProperty(obj, name, JSUtils.mapJavaToJS(value));
	}

	@Override
	public Object getIdentity() {
		return makeObjetDbClass().getIdentity();
	}

	@Override
	public String getPackageName() {
		return getClassName().replaceAll("\\.?[^.]*$", "");
	}

	@Override
	public String getSimpleName() {
		return getClassName().replaceFirst(".*\\.", "");
	}
}
