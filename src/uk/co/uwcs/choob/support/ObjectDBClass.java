package uk.co.uwcs.choob.support;

import java.lang.reflect.*;

public interface ObjectDBClass {
	String getName();
	Object newInstance() throws InstantiationException, IllegalAccessException;
}
