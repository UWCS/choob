package uk.co.uwcs.choob.support;

public final class ObjectDBClassJavaWrapper implements ObjectDBClass {
	private Class cls;

	public ObjectDBClassJavaWrapper(Object obj) {
		if (!(obj instanceof Class)) {
			throw new RuntimeException(
					"Trying to wrap a non-class type as a class!");
		}
		this.cls = (Class) obj;
	}

	public String getName() {
		return cls.getName();
	}

	public Object newInstance() throws InstantiationException,
			IllegalAccessException {
		return cls.newInstance();
	}
}
