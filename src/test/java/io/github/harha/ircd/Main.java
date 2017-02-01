package io.github.harha.ircd;

import io.github.harha.ircd.server.IRCServer;
import io.github.harha.ircd.util.Consts;
import io.github.harha.ircd.util.Macros;

import java.io.IOException;

public class Main {

	public static void main(String[] args) {
		Macros.LOG("mirage-ircd-%s initializing...", Consts.VERSION);

		// args = new String[2];
		// args[0] = "localhost";
		// args[1] = "6667";

		if (args.length < 2) {
			Macros.ERR("Please give the server ip and port as arguments. Example: java -jar program.jar 127.0.0.1 6667");
			System.exit(-1);
		}

		IRCServer server_object = null;
		Thread server_thread = null;

		Macros.LOG("Binding server on (%s:%s).", args[0], args[1]);

		try {
			server_object = new IRCServer(args[0], args[1]);
		} catch (NumberFormatException e) {
			Macros.ERR("Converting ip/port from string into another format failed.");

			server_object = null;
			e.printStackTrace();
		} catch (IOException e) {
			Macros.ERR("Cannot resolve the given host.");

			server_object = null;
			e.printStackTrace();
		}

		if (server_object == null) {
			Macros.ERR("Failed to initialize the server on given ip and port. Exiting...");
			System.exit(-1);
		}

		server_thread = new Thread(server_object);
		server_thread.start();

		Macros.LOG("The server has started listening for connections.");
		Macros.LOG("Hostname: %s", server_object.getHost());
		Macros.LOG("IP Address: %s", server_object.getIp());
		Macros.LOG("Port: %s", server_object.getPort());

		while (!server_object.getSocket().isClosed()) {
			long time_s = System.nanoTime();

			server_object.updateConnections();
			server_object.updateChannels();

			long time_e = System.nanoTime();
			int time_d = (int) ((time_e - time_s) / 1000000);

			server_object.setDeltaTime(time_d);

			try {
				Thread.sleep(Math.max(100 - time_d, 10));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

}
