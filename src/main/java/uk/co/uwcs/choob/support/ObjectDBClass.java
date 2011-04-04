package uk.co.uwcs.choob.support;

public interface ObjectDBClass<T> {
	/**
	 * Gets the name of the class, in an ObjectDB compatible format.
	 * @return The full class name, e.g. "plugin.MyPlugin.SomeClass".
	 */
	String getName();

	/**
	 * Constructs a new instance of the represented class, and returns it.
	 */
	T newInstance() throws InstantiationException, IllegalAccessException;
}
