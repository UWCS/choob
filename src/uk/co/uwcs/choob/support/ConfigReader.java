package uk.co.uwcs.choob.support;

import java.util.*;
import java.io.*;

/**
 * Wrapper for configuration file reading.
 */
public final class ConfigReader {
	Properties botProps;

	/**
	 * Create a new ConfigReader.
	 * 
	 * @param configFile
	 *            The path of the filename to use for the config file.
	 * @throws ChoobException
	 *             Thrown if there was an error reading the config file.
	 */
	public ConfigReader(String configFile) throws IOException {
		botProps = new Properties();
		InputStream r = this.getClass().getClassLoader().getResourceAsStream(
				configFile);
		if (r == null)
			throw new IOException("Cannot find config file: " + configFile
					+ ". Have you created it from the example correctly?");
		botProps.load(r);
	}

	/**
	 * Attempts to get a setting from the config file, or, if it fails, returns
	 * the fallback that you provided.
	 * 
	 * @param setting
	 *            Setting you're attempting to get the value of.
	 * @param fallback
	 *            String to fallback to if it fails.
	 */
	public String getSettingFallback(String setting, String fallback) {
		try {
			return getSetting(setting);
		} catch (ChoobException e) {
		}

		return fallback;
	}

	/**
	 * Attempts to get a setting from the config file.
	 * 
	 * @param setting
	 *            Setting you're attempting to get the value of.
	 * @throws ChoobException
	 *             Thrown if the setting wasn't found.
	 */
	public String getSetting(String setting) throws ChoobException {
		String p = botProps.getProperty(setting);
		if (p != null)
			return p;
		throw new ChoobException("Setting doesn't exist in config file.");
	}
}
