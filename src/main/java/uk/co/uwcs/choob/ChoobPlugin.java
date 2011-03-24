package uk.co.uwcs.choob;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates a method is a choob command
 * @author benji
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ChoobPlugin 
{
	String description();
	String author();
	String email();
	String revision();
	String date();
}
