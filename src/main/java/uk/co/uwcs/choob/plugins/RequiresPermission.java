/**
 *
 */
package uk.co.uwcs.choob.plugins;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.security.Permission;

/**
 * @author richard
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RequiresPermission {
	Class<? extends Permission> value();
	String permission() default "";
	String action() default "";
}
