package uk.co.uwcs.choob.support;

final class ObjectDBClassJavaWrapper implements ObjectDBClass {
	private Class<? extends ObjectDBClass> cls;
	
	@SuppressWarnings("unchecked")
	public ObjectDBClassJavaWrapper(Object obj) {
		if (!(obj instanceof Class)) {
			throw new RuntimeException("Trying to wrap a non-class type as a class: " + obj.getClass().getCanonicalName());
		}
		this.cls = (Class<? extends ObjectDBClass>)obj;
	}

	@Override public String getName() {
		return cls.getName();
	}

	@Override public Object newInstance() throws InstantiationException, IllegalAccessException {
		return cls.newInstance();
	}
}
