package io.github.harha.ircd.server;

import io.github.harha.ircd.util.CaseIMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.Map.Entry;

public class IRCServer implements Runnable
{
	private static final Logger logger = LoggerFactory.getLogger(IRCServer.class);

	private InetAddress m_host;
	private int m_port;
	private ServerSocket m_socket;
	private Map<String, List<Connection>> m_connections;
	private Map<String, Client> m_clients;
	private Map<String, Channel> m_channels;

	public IRCServer(int port) throws NumberFormatException, IOException
	{
		m_host = InetAddress.getLocalHost();
		m_socket = new ServerSocket(port);
		m_port = m_socket.getLocalPort();
		m_socket.setSoTimeout(0);
		m_connections = Collections.synchronizedMap(new CaseIMap<>());
		m_clients = Collections.synchronizedMap(new CaseIMap<>());
		m_channels = Collections.synchronizedMap(new CaseIMap<>());
	}

	@Override
	public void run()
	{
		while (m_socket.isBound() && !m_socket.isClosed()) {
			try {
				/* Accept connection, create i/o, create connection object */
				Socket socket;
				try {
					socket = m_socket.accept();
				} catch (SocketException acceptProblem) {
					if (acceptProblem.getMessage().equals("Socket closed")) {
						logger.info("clean shutdown >.<");
						return;
					}
					throw acceptProblem;
				}
				BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
				Connection connection = new Connection(this, socket, input, output);

                /* Look up the hostname or ip, depending which one is available */
				connection.sendMsgAndFlush(new ServMessage(this, "NOTICE", connection.getNick(), "*** Connection accepted. Looking up your hostname..."));
				String key = connection.getHost().getHostName();
				connection.sendMsgAndFlush(new ServMessage(this, "NOTICE", connection.getNick(), "*** Found your hostname."));

                /* Check if connections from same host already exist, check if max limit per hostname has been reached */
				if (!m_connections.containsKey(key)) {
					List<Connection> connections = new ArrayList<Connection>();
					m_connections.put(key, connections);
				}

                /* Add the accepted connection to the connections hashmap-list */
				m_connections.get(key).add(connection);
				logger.info("New incoming " + connection + ".");

			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void updateConnections()
	{
		Iterator<Entry<String, List<Connection>>> it_con_map = getConnections().entrySet().iterator();

		while (it_con_map.hasNext()) {
			Entry<String, List<Connection>> entry_map = it_con_map.next();
			List<Connection> con_list = (ArrayList<Connection>) entry_map.getValue();
			Iterator<Connection> it_con_list = con_list.iterator();

			while (it_con_list.hasNext()) {
				Connection c = it_con_list.next();

                /* First check, if the socket was closed for some reason */
				if (c.getSocket().isClosed() || c.getOutput().checkError()) {
					c.setState(ConnState.DISCONNECTED);
				}

                /* Handle unidentified connections */
				if (c.getState() == ConnState.UNIDENTIFIED) {
					if (c.getIdentTime() == -1)
						c.sendMsgAndFlush(new ServMessage(this, "NOTICE", c.getNick(), "*** Checking ident..."));

					c.updateUnidentified();

					c.setIdentTime(c.getIdentTime() + 1);
				}

                /* Handle identified client connections */
				if (c.getState() == ConnState.IDENTIFIED_AS_CLIENT) {
	                /* Add the connection as a client and inform them for the success */
					c.sendMsgAndFlush(new ServMessage(this, "NOTICE", c.getNick(), "*** Found your ident, identified as a client."));
					c.setState(ConnState.CONNECTED_AS_CLIENT);
					c.getUser().setHostName(c.getHost().getHostName());
					Client client = new Client(c);
					c.setParentClient(client);
					m_clients.put(c.getNick(), client);

                    /* Send some info about the server */
					c.sendMsg(new ServMessage(this, "001", c.getNick(), "Welcome to the " + "sName" + " IRC network, " + c.getNick()));
					c.sendMsg(new ServMessage(this, "002", c.getNick(), "Your host is " + getHost().getHostName() + ", running version mirage-ircd"));
					c.sendMsg(new ServMessage(this, "003", c.getNick(), "This server was created"));
					c.sendMsg(new ServMessage(this, CMDs.RPL_LUSERCLIENT, c.getNick(), "There are " + m_connections.size() + " users and 0 invisible on 1 server."));
					c.sendMsg(new ServMessage(this, CMDs.RPL_LUSEROP, c.getNick(), "0", "IRC Operators online."));
					c.sendMsg(new ServMessage(this, CMDs.RPL_LUSERUNKNOWN, c.getNick(), "0", "Unknown connections."));
					c.sendMsg(new ServMessage(this, CMDs.RPL_LUSERCHANNELS, c.getNick(), Integer.toString(m_channels.size()), "Channels formed."));
					c.sendMsg(new ServMessage(this, CMDs.RPL_LUSERME, c.getNick(), "I have " + m_clients.size() + " clients"));

                    /* Send MOTD to the client */
					c.sendMsg(new ServMessage(this, CMDs.RPL_MOTDSTART, c.getNick(), "- Message of the day -"));

					c.sendMsg(new ServMessage(this, CMDs.RPL_ENDOFMOTD, c.getNick(), "End of /MOTD command."));
					c.sendMsgAndFlush(new ServMessage(this, "004", c.getNick(), "some.thing UnrealIRCd-4.0.10 iowrsxzdHtIDRqpWGTSB lvhopsmntikraqbeIzMQNRTOVKDdGLPZSCcf."));
				}

                /* Handle connected client connections */
				if (c.getState() == ConnState.CONNECTED_AS_CLIENT) {
					Client client = c.getParentClient();

                    /* Send a PING request between intervals */
					int pingtime = 600;
					if (client.getPingTimer() >= pingtime && client.getPingTimer() % (pingtime / 10) == 0) {
						c.sendMsgAndFlush(new ServMessage("", "PING", c.getNick()));
					}

					client.updateIdentifiedClient();

                    /* Disconnect if it didn't respond to the PING request given enough time */
					if (client.getPingTimer() > (int) (pingtime * 1.5)) {
						c.setState(ConnState.DISCONNECTED);
					}

					client.setPingTimer(client.getPingTimer() + 1);
				}

                /* Unregister the connection if it was closed */
				if (c.getState() == ConnState.DISCONNECTED) {
					Client client = c.getParentClient();

                    /* Is it a client? */
					if (client != null) {
						client.quitChans("Connection reset by peer...");
						m_clients.remove(c.getNick());
					}

					c.kill();
					it_con_list.remove();
					logger.info(c + " Has disconnected.");
				}
			}

            /* Remove the key from connection list map if the list is empty */
			if (con_list.isEmpty()) {
				it_con_map.remove();
			}
		}
	}

	public void updateChannels()
	{
		Iterator<Entry<String, Channel>> it_chan_map = m_channels.entrySet().iterator();

		while (it_chan_map.hasNext()) {
			Entry<String, Channel> e = it_chan_map.next();
			Channel c = (Channel) e.getValue();

            /* Delete empty channels */
			if (c.getState() == ChanState.EMPTY) {
				it_chan_map.remove();
			}
		}
	}

	public void runUntilClosed() throws InterruptedException
	{
		while (!getSocket().isClosed()) {
			long time_s = System.nanoTime();

			updateConnections();
			updateChannels();

			long time_e = System.nanoTime();
			int time_d = (int) ((time_e - time_s) / 1000000);

			Thread.sleep(Math.max(100 - time_d, 10));
		}
	}

	public InetAddress getHost()
	{
		return m_host;
	}

	public int getPort()
	{
		return m_port;
	}

	public ServerSocket getSocket()
	{
		return m_socket;
	}

	public synchronized Map<String, List<Connection>> getConnections()
	{
		return m_connections;
	}

	public synchronized Map<String, Client> getClients()
	{
		return m_clients;
	}

	public synchronized Client getClient(String key)
	{
		return m_clients.get(key);
	}

	public synchronized Map<String, Channel> getChannels()
	{
		return m_channels;
	}

	public synchronized Channel getChannel(String key)
	{
		return m_channels.get(key);
	}

}
