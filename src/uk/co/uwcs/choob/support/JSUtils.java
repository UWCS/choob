package uk.co.uwcs.choob.support;

import org.mozilla.javascript.*;

public final class JSUtils {
	public static Object mapJSToJava(Object jsObject) {
		// Most Native* types from Rhino are automatically converted, or
		// something. Arrays, however, definately are not. This code will map
		// JS arrays into Java ones. It tries to use sensible types, as well.
		
		if (jsObject instanceof NativeArray) {
			NativeArray ary = (NativeArray)jsObject;
			int aryLen = (int)ary.getLength();
			
			Object[]  aryO = new Object [aryLen];
			String[]  aryS = new String [aryLen];
			boolean[] aryB = new boolean[aryLen];
			double[]  aryN = new double [aryLen];
			boolean isStringArray  = true;
			boolean isBooleanArray = true;
			boolean isNumberArray  = true;
			
			for (int i = 0; i < aryLen; i++) {
				Object item = ary.get(i, ary);
				
				aryO[i] = mapJSToJava(item);
				
				if (isStringArray) {
					if (item instanceof String) {
						aryS[i] = (String)item;
					} else {
						isStringArray = false;
					}
				}
				if (isBooleanArray) {
					if (item instanceof Boolean) {
						aryB[i] = ((Boolean)item).booleanValue();
					} else {
						isBooleanArray = false;
					}
				}
				if (isNumberArray) {
					if (item instanceof Number) {
						aryN[i] = ((Number)item).doubleValue();
					} else {
						isNumberArray = false;
					}
				}
			}
			
			if (isStringArray) {
				return aryS;
			}
			if (isBooleanArray) {
				return aryB;
			}
			if (isNumberArray) {
				return aryN;
			}
			return aryO;
		}
		return jsObject;
	}
	
	public static Object mapJavaToJS(Object javaObject) {
		return javaObject;
	}
	
	public static Object getProperty(Scriptable obj, String prop) throws NoSuchFieldException {
		while (obj != null) {
			Object val = obj.get(prop, obj);
			if (val != Scriptable.NOT_FOUND) {
				return val;
			}
			obj = obj.getPrototype();
		}
		throw new NoSuchFieldException(prop);
	}
	
	public static void setProperty(Scriptable obj, String prop, Object value) {
		obj.put(prop, obj, value);
	}
}
