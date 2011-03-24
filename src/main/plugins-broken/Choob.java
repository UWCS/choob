
public class Choob
{
	public String commandStart(String s)
	{
		try
		{
			// This constructor is default by default; not yours.
			new uk.co.uwcs.choob.Choob();
		}
		catch (Throwable t)
		{
			return "Died: " + t + " caused by " + t.getCause();
		}
	}
}
