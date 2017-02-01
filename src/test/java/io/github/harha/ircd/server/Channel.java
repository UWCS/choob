package io.github.harha.ircd.server;

import io.github.harha.ircd.util.CaseIMap;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class Channel {

	private IRCServer m_ircserver;
	private String m_name;
	private String m_key;
	private String m_topic;
	private ChanState m_state;
	private Map<String, Client> m_clients;

	public Channel(IRCServer ircserver, String name, String key, String topic) {
		m_ircserver = ircserver;
		m_name = name;
		m_key = key;
		m_topic = "";
		m_state = ChanState.PUBLIC;
		m_clients = Collections.synchronizedMap(new CaseIMap<>());
	}

	public void sendMsg(ServMessage message) {
		Iterator<Entry<String, Client>> i = m_clients.entrySet().iterator();

		while (i.hasNext()) {
			Entry<String, Client> e = i.next();
			Client c = (Client) e.getValue();
			c.getConnection().sendMsg(message);
		}
	}

	public void sendMsgAndFlush(ServMessage message) {
		Iterator<Entry<String, Client>> i = m_clients.entrySet().iterator();

		while (i.hasNext()) {
			Entry<String, Client> e = i.next();
			Client c = (Client) e.getValue();
			c.getConnection().sendMsgAndFlush(message);
		}
	}

	public void sendMsg(Client client, ServMessage message) {
		Iterator<Entry<String, Client>> i = m_clients.entrySet().iterator();

		while (i.hasNext()) {
			Entry<String, Client> e = i.next();
			Client c = (Client) e.getValue();

			if (c.getConnection().getNick().equals(client.getConnection().getNick()))
				continue;

			c.getConnection().sendMsg(message);
		}
	}

	public void sendMsgAndFlush(Client client, ServMessage message) {
		Iterator<Entry<String, Client>> i = m_clients.entrySet().iterator();

		while (i.hasNext()) {
			Entry<String, Client> e = i.next();
			Client c = (Client) e.getValue();

			if (c.getConnection().getNick().equals(client.getConnection().getNick()))
				continue;

			c.getConnection().sendMsgAndFlush(message);
		}
	}

	public void clientJoin(Client client) {
		Connection connection = client.getConnection();

		if (client.getChannel(m_name) == null) {
			client.addChan(this);
			m_clients.put(connection.getNick(), client);
			sendMsgAndFlush(new ServMessage(connection, "JOIN", m_name));

			if (!m_topic.isEmpty()) {
				connection.sendMsgAndFlush(new ServMessage(m_ircserver, CMDs.RPL_TOPIC, connection.getNick(), m_name, m_topic));
			} else {
				connection.sendMsgAndFlush(new ServMessage(m_ircserver, CMDs.RPL_NOTOPIC, connection.getNick(), m_name, "No topic is set."));
			}

			connection.sendMsgAndFlush(new ServMessage(m_ircserver, CMDs.RPL_NAMREPLY, connection.getNick(), "@", m_name, getNames()));
			connection.sendMsgAndFlush(new ServMessage(m_ircserver, CMDs.RPL_ENDOFNAMES, connection.getNick(), m_name, "End of /NAMES list."));
		} else {
			clientPart(client, "Rejoining this channel...");

			if (client.getChannel(m_name) == null) {
				clientJoin(client);
			}
		}
	}

	public void clientPart(Client client, String reason) {
		Connection connection = client.getConnection();

		if (client.getChannel(m_name) != null) {
			sendMsgAndFlush(new ServMessage(connection, "PART", m_name, reason));
			client.removeChan(this);
			m_clients.remove(client.getConnection().getNick());
		} else {
			connection.sendMsgAndFlush(new ServMessage(connection, CMDs.ERR_USERNOTINCHANNEL, connection.getNick(), m_name, "You are not on that channel."));
		}

		if (m_clients.isEmpty()) {
			m_state = ChanState.EMPTY;
		}
	}

	public void clientQuit(Client client, String reason) {
		Connection connection = client.getConnection();

		if (client.getChannel(m_name) != null) {
			sendMsgAndFlush(new ServMessage(connection, "QUIT", reason));
			client.removeChan(this);
			m_clients.remove(client.getConnection().getNick());
		} else {
			connection.sendMsgAndFlush(new ServMessage(connection, CMDs.ERR_USERNOTINCHANNEL, connection.getNick(), m_name, "Is not on the channel."));
		}
	}

	public void setTopic(Client client, String topic) {
		m_topic = topic;

		if (!m_topic.trim().isEmpty()) {
			Iterator<Entry<String, Client>> i = m_clients.entrySet().iterator();

			while (i.hasNext()) {
				Entry<String, Client> e = i.next();
				Client c = (Client) e.getValue();
				c.getConnection().sendMsgAndFlush(new ServMessage(m_ircserver, CMDs.RPL_TOPIC, c.getConnection().getNick(), m_name, m_topic));
			}
		} else {
			m_topic = "";
		}
	}

	public void setState(ChanState state) {
		m_state = state;
	}

	public IRCServer getIRCServer() {
		return m_ircserver;
	}

	public String getName() {
		return m_name;
	}

	public String getKey() {
		return m_key;
	}

	public String getTopic() {
		return m_topic;
	}

	public ChanState getState() {
		return m_state;
	}

	public String getNames() {
		String names = "";

		Iterator<Entry<String, Client>> i = m_clients.entrySet().iterator();

		while (i.hasNext()) {
			Entry<String, Client> e = i.next();
			Client c = (Client) e.getValue();
			names += c.getConnection().getNick() + " ";
		}

		return names;
	}

	public Map<String, Client> getClients() {
		return m_clients;
	}

	public Client getClient(String key) {
		return m_clients.get(key);
	}

}
