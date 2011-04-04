package uk.co.uwcs.choob.support;

final class ObjectDBClassJavaWrapper<T> implements ObjectDBClass<T> {
	private Class<T> cls;

	public ObjectDBClassJavaWrapper(Class<T> obj) {
		this.cls = obj;
	}

	@Override public String getName() {
		return cls.getName();
	}

	@Override public T newInstance() throws InstantiationException, IllegalAccessException {
		return cls.newInstance();
	}
}
