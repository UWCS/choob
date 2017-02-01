package io.github.harha.ircd.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Server {
	private static final Logger logger = LoggerFactory.getLogger(Server.class);

	private Connection m_connection;

	public Server(Connection connection) {
		m_connection = connection;
	}

	public void updateIdentifiedServer() {
		/* Read input from client */
		List<String> input_data = new ArrayList<String>();

		try {
			String line = null;
			while (m_connection.getInput().ready() && (line = m_connection.getInput().readLine()) != null && !line.isEmpty()) {
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
		logger.info("Input from {}: {}", m_connection, input_data);

        /* Parse input and handle it appropriately */
		for (String l : input_data) {
			CliMessage message = new CliMessage(l);

			switch (message.getCommand()) {
			}
		}
	}

	public Connection getConnection() {
		return m_connection;
	}

}
