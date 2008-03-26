/*
 * CallAPIResult.java
 *
 * Created on August 16, 2006, 1:06 AM
 */
package uk.co.uwcs.choob.support;

public final class CallAPIResult
{
	private String pluginName;
	private Object object;
	private Exception exception;
	
	public CallAPIResult(String pluginName, Object object, Exception exception) {
		this.pluginName = pluginName;
		this.object     = object;
		this.exception  = exception;
	}
	
	public String getPluginName() {
		return pluginName;
	}
	
	public Object getObject() {
		return object;
	}
	
	public Exception getException() {
		return exception;
	}
	
	public Boolean isSuccessful() {
		return (object != null) && (exception == null);
	}
	
	@Override
	public String toString() {
		if (exception != null) {
			return "{" + pluginName + "} " + exception.toString();
		}
		return "{" + pluginName + "} " + object.toString();
	}
}
