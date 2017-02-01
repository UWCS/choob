package io.github.harha.ircd.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ServMessage {

	private String m_prefix;
	private String m_command;
	private List<String> m_parameters;

	public ServMessage(String prefix, String command, String... parameters) {
		m_prefix = prefix;
		m_command = command;
		m_parameters = new ArrayList<String>(Arrays.asList(parameters));
	}

	public ServMessage(IRCServer server, String command, String... parameters) {
		m_prefix = server.getHost().getHostName();
		m_command = command;
		m_parameters = new ArrayList<String>(Arrays.asList(parameters));
	}

	public ServMessage(Connection connection, String command, String... parameters) {
		m_prefix = connection.getNick() + "!" + connection.getUser().getUserName() + "@" + connection.getHost().getHostName();
		m_command = command;
		m_parameters = new ArrayList<String>(Arrays.asList(parameters));
	}

	@Override
	public String toString() {
		return ((m_prefix.isEmpty() ? "" : ":") + m_prefix + " " + m_command + " " + getParametersAsString()).trim() + "\r\n";
	}

	public String getPrefix() {
		return m_prefix;
	}

	public String getCommand() {
		return m_command;
	}

	public List<String> getParameters() {
		return m_parameters;
	}

	public String getParametersAsString() {
		String parameters = "";

		for (String p : m_parameters) {
			parameters += (p.contains(" ") && !p.startsWith(":") ? ":" : "") + p + " ";
		}

		return parameters.trim();
	}

}
