
import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.ChoobNoSuchPluginException;
import uk.co.uwcs.choob.support.ChoobPermission;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

/**
 * Plugin to make choob functionality accessible over HTTP.
 * Uses Java's built in webserver and Jersey.
 * 
 * @author benji
 */
public class Jersey
{

	private HttpServer server;
	private final List<URL> pluginURLs = new ArrayList<URL>();

	public String[] info()
	{
		return new String[]
			{
				"Plugin that allows exposure choob functionality over HTTP using the Jersey implementation of the JSR-311 API.",
				"The Choob Team",
				"choob@uwcs.co.uk",
				""
			};
	}
	private final Modules mods;
	private final IRCInterface irc;
	public String[] optionsGeneral =
	{
		"PortNumber"
	};
	public String[] optionsGeneralDefaults =
	{
		"8023"
	};
	public String[] helpOptionPortNumber =
	{
		"Port number to operate the web server on."
	};
	private int portNumber = 8023;

	public Jersey(final Modules mods, final IRCInterface irc) throws ChoobException
	{
		this.mods = mods;
		this.irc = irc;
		for (File file : (new File("pluginData" + File.separator).listFiles()))
		{
			try
			{
				pluginURLs.add(file.toURI().toURL());
			} catch (MalformedURLException ex)
			{
				throw new ChoobException("Unable to create URL for plugin location " + file);
			}
		}
		startServer();

	}
	public String[] helpCommandStopServer =
	{
		"Stop the HTTP Server."
	};

	public void commandStopServer(Message mes)
	{
		mods.security.checkNickPerm(new ChoobPermission("plugins.jersey.stopserver"), mes);
		irc.sendContextReply(mes,"Requesting server stop...");
		server.stop(10);
		irc.sendContextReply(mes, "Server now stopped.");
	}

	private void startServer() throws ChoobException
	{
		updateOptions();
		try
		{

			Thread.currentThread().setContextClassLoader(
				//HaxHaxSunPluginClassLoader ¬_¬
				new URLClassLoader(pluginURLs.toArray(new URL[]
				{
				})));
			server = HttpServerFactory.create("http://localhost:" + portNumber + "/");
			server.start();
		} catch (IOException ex)
		{
			throw new ChoobException(ex.getMessage());
		}
	}
	
	private void updateOptions() throws ChoobException
	{
		try
		{
			String portOption = (String) mods.plugin.callAPI("Options", "GetGeneralOption", "PortNumber");
			if (portOption != null)
			{
				portNumber = Integer.parseInt(portOption);
			} else
			{
				portOption = optionsGeneralDefaults[0];
			}
		} catch (ChoobNoSuchPluginException e)
		{
		} catch (NumberFormatException e)
		{
		}
	}
	
	public String[] helpCommandStartServer =
	{
		"(Re)Start the HTTP Server, it is running by default after plugin load."
	};

	public void commandStartServer(Message mes) throws ChoobException
	{
		mods.security.checkNickPerm(new ChoobPermission("plugins.jersey.startserver"), mes);
		startServer();
		irc.sendContextReply(mes, "Restarted Server.");
	}
}
