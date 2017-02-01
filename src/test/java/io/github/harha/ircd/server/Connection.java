package io.github.harha.ircd.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Connection {
	private static final Logger logger = LoggerFactory.getLogger(Connection.class);

	private IRCServer m_ircserver;
	private Socket m_socket;
	private InetAddress m_host;
	private BufferedReader m_input;
	private PrintWriter m_output;
	private String m_nick;
	private UserInfo m_user;
	private ServerInfo m_server;
	private String m_pass;
	private ConnState m_state;
	private int m_identTime;
	private Client m_parent_client;

	public Connection(IRCServer ircserver, Socket socket, BufferedReader input, PrintWriter output) {
		/* Initialize all member variables and objects */
		m_ircserver = ircserver;
		m_socket = socket;
		m_host = socket.getInetAddress();
		m_input = input;
		m_output = output;
		m_nick = "*";
		m_user = new UserInfo("*", "0", "*", "");
		m_server = new ServerInfo("*", "0", "");
		m_pass = "";
		m_state = ConnState.UNIDENTIFIED;
		m_identTime = -1;
		m_parent_client = null;
	}

	@Override
	public String toString() {
		return String.format("Connection[%s, %s, %s]", m_nick, m_user, m_server);
	}

	public void updateUnidentified() {
		/* Read input from client */
		List<String> input_data = new ArrayList<String>();

		try {
			String line = null;
			while (m_input.ready() && (line = m_input.readLine()) != null && !line.isEmpty()) {
				input_data.add(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

        /* Return if input data was empty */
		if (input_data.isEmpty()) {
			return;
		}

        /* Log the input to console */
		logger.info("Input from {}: {}", this, input_data);

        /* Parse input and handle it appropriately */
		for (String l : input_data) {
			CliMessage message = new CliMessage(l);

			switch (message.getCommand()) {
				case "GET":
					sendMsgAndFlush(new ServMessage(m_ircserver, "NOTICE", m_nick, "*** Detected that the connection was made using a browser, disconnected."));
					setState(ConnState.DISCONNECTED);
					break;
				case "NICK":
					if (message.getParameters().size() > 0) {
						m_nick = message.getParameter(0);
					}
					break;
				case "USER":
					if (message.getParameters().size() > 0) {
						m_user = new UserInfo(message.getParameters());
					}
					break;
				case "SERVER":
					if (message.getParameters().size() > 0) {
						m_server = new ServerInfo(message.getParameters());
					}
					break;
				case "PASS":
					if (message.getParameters().size() > 0) {
						m_pass = message.getParameter(0);
					}
					break;
			}
		}

        /* Have we received enough info in order to try client identification? */
		if (!m_nick.equals("*")) {
            /* Nick length must be in-between 1 and 9 characters */
			if (m_nick.length() < 1 || m_nick.length() > 9) {
				sendMsgAndFlush(new ServMessage(m_ircserver, "NOTICE", m_nick, "*** NICK length must be in-between 1 and 9 characters. Disconnecting."));
				m_state = ConnState.DISCONNECTED;
				return;
			}

            /* Nick must not exist on the server */
			if (m_ircserver.getClients().containsKey(m_nick)) {
				sendMsgAndFlush(new ServMessage(m_ircserver, "NOTICE", m_nick, "*** NICK already exists on this server. Disconnecting."));
				m_state = ConnState.DISCONNECTED;
				return;
			}

            /* Accept the connection as identified client */
			m_state = ConnState.IDENTIFIED_AS_CLIENT;
		}
	}

	public void sendMsg(ServMessage message) {
		m_output.print(message);
	}

	public void sendMsgAndFlush(ServMessage message) {
		m_output.print(message);
		m_output.flush();
	}

	public void flush() {
		m_output.flush();
	}

	public void kill() {
		m_state = ConnState.DISCONNECTED;

		try {
			m_socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void setState(ConnState state) {
		m_state = state;
	}

	public void setIdentTime(int time) {
		m_identTime = time;
	}

	public void setParentClient(Client client) {
		m_parent_client = client;
	}

	public IRCServer getIRCServer() {
		return m_ircserver;
	}

	public InetAddress getHost() {
		return m_host;
	}

	public Socket getSocket() {
		return m_socket;
	}

	public BufferedReader getInput() {
		return m_input;
	}

	public PrintWriter getOutput() {
		return m_output;
	}

	public String getNick() {
		return m_nick;
	}

	public UserInfo getUser() {
		return m_user;
	}

	public ServerInfo getServer() {
		return m_server;
	}

	public ConnState getState() {
		return m_state;
	}

	public int getIdentTime() {
		return m_identTime;
	}

	public Client getParentClient() {
		return m_parent_client;
	}

}
