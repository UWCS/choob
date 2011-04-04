package uk.co.uwcs.choob.support;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

public final class ObjectDBClassJSWrapper implements ObjectDBClass {
	private Function cls;

	public ObjectDBClassJSWrapper(Object obj) {
		if (!(obj instanceof Function)) {
			throw new RuntimeException("Trying to wrap a non-function type as a class!");
		}
		this.cls = (Function)obj;
	}

	@Override public String getName() {
		Context cx = Context.enter();
		try {
			try {
				String ctorName = (String)JSUtils.getProperty(cls, "name");
				Scriptable scope = ((Scriptable)cls).getParentScope();

				// Get plugin name from scope (HACK)!
				String plugName = "<error>";
				while (scope != null) {
					try {
						plugName = (String)JSUtils.getProperty(scope, "__jsplugman_pluginName");
						scope = null;
					} catch (NoSuchFieldException e) {
						scope = scope.getParentScope();
					}
				}

				return "plugins." + plugName + "." + ctorName;
			} catch (NoSuchFieldException e) {
				// Do nothing.
			}
		} finally {
			cx.exit();
		}
		return "";
	}

	@Override public Object newInstance() {
		Context cx = Context.enter();
		try {
			Scriptable scope = cls.getParentScope();
			return cls.construct(cx, scope, new Object[0]);
		} finally {
			cx.exit();
		}
	}

	@Override
	public Object getIdentity() {
		return cls;
	}
}
