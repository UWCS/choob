package uk.co.uwcs.choob.support;

import java.lang.reflect.*;

public interface ObjectDBObject {
	// This is used to store the right class name in the DB and to validate loading objects.
	String getClassName();
	
	// The object must have an "ID" to be stored in the ObjectDB.
	int getId();
	void setId(int id);
	
	// Gets and sets the information relating to the object's fields as stored in the ObjectDB.
	String[] getFields();
	Type getFieldType(String name) throws NoSuchFieldException;
	Object getFieldValue(String name) throws NoSuchFieldException, IllegalAccessException;
	void setFieldValue(String name, Object value) throws NoSuchFieldException, IllegalAccessException;
}
