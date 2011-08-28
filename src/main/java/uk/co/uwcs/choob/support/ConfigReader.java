package uk.co.uwcs.choob.support;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper for configuration file reading.
 */
public final class ConfigReader
{
	private static final Logger logger = LoggerFactory.getLogger(ConfigReader.class);

	Properties botProps;

	/**
	 * Create a new ConfigReader.
	 * @param configFile The path of the filename to use for the config file.
	 * @throws ChoobException Thrown if there was an error reading the config file.
	 */
	public ConfigReader(final String configFile) throws IOException
	{
		botProps = new Properties();
		final FileInputStream r = new FileInputStream(configFile);
		botProps.load(r);
	}

	/**
	 * Attempts to get a setting from the config file, or, if it fails, returns the fallback that you provided.
	 * @param setting Setting you're attempting to get the value of.
	 * @param fallback String to fallback to if it fails.
	 */
	public String getSettingFallback( final String setting, final String fallback)
	{
		try
		{
			return getSetting(setting);
		}
		catch (final ChoobException e) {
			logger.warn("Warning: Setting " + setting + " was not set, falling back to " + fallback, e);
		}

		return fallback;
	}

	/**
	 * Attempts to get a setting from the config file.
	 * @param setting Setting you're attempting to get the value of.
	 * @throws ChoobException Thrown if the setting wasn't found.
	 */
	private String getSetting( final String setting ) throws ChoobException
	{
		final String p=botProps.getProperty(setting);
		if (p!=null)
			return p;

		final String prop = System.getProperty("choob" + setting);
		if (null != prop)
			return prop;

		throw new ChoobException("Setting doesn't exist in config file" +
				" or system properties (with 'choob' prefix): " + setting);
	}
}
