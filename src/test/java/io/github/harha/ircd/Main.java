package io.github.harha.ircd;

import io.github.harha.ircd.server.IRCServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static uk.co.uwcs.choob.TestTemps.newThread;

public class Main
{
	private static final Logger logger = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) throws InterruptedException, IOException
	{
		int port = 0; // automatic
		if (1 == args.length) {
			port = Integer.parseInt(args[0]);
		}

		IRCServer server = new IRCServer(port);

		logger.info("Port: {}", server.getPort());

		newThread(server::run).start();
		server.runUntilClosed();
	}
}
