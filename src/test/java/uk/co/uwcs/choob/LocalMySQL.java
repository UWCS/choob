package uk.co.uwcs.choob;

import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

public class LocalMySQL implements Closeable
{
	private static final String SERVER_BINARY_PATH = findWorkingExecutable("/usr/sbin/mysqld");
	private static final String CLIENT_BINARY_PATH = findWorkingExecutable("/usr/bin/mysql");
	@Nullable
	private static final String EAT_MY_DATA_PATH;

	static {
		String path = null;
		try {
			path = findWorkingExecutable("/usr/bin/eatmydata");
		} catch (IllegalStateException notFound) {
			System.err.println("could be sped up by installing man:eatmydata(1)");
		}
		EAT_MY_DATA_PATH = path;
	}

	private static final String ROOT_PASSWORD = UUID.randomUUID().toString();

	public final File base = TestTemps.createTemporaryDirectory();
	public final int port = findAPort();

	private final Process server;

	public LocalMySQL(String dumpResource) throws IOException, InterruptedException, SQLException
	{
		final String basePath = base.getAbsolutePath();
		runAndAwait(SERVER_BINARY_PATH,
			"--no-defaults",
			"--skip-sync-frm",
			"--datadir=" + basePath,
			"--log_error=" + basePath + "/error.log",
			"--initialize-insecure");

		final String socketPath = basePath + "/sock.sock";

		server = buildProcess(SERVER_BINARY_PATH,
			"--no-defaults",
			"--skip-sync-frm",
			"--datadir=" + basePath,
			"--log_error=" + basePath + "/error.log",
			"--port=" + port,
			"--socket=" + socketPath,
			"--explicit_defaults_for_timestamp" // it complains if you don't specify this
		).start();
		server.getOutputStream().close();

		setRootPasswordOn(socketPath);

		runAsRoot(conn -> {
			try (final Statement stat = conn.createStatement()) {
				stat.execute("CREATE USER choob@localhost IDENTIFIED BY 'choob'");
				stat.execute("CREATE DATABASE choob");
				stat.execute("GRANT ALL PRIVILEGES ON choob.* TO choob@localhost");
			}
		});

		try (InputStream data = LocalMySQL.class.getResourceAsStream(dumpResource)) {
			final Process loader = buildProcess(CLIENT_BINARY_PATH,
				"--socket=" + socketPath,
				"--user=choob",
				"--password=choob",
				"--database=choob").start();
			ByteStreams.copy(data, loader.getOutputStream());
			loader.getOutputStream().close();
			if (0 != loader.waitFor()) {
				throw new IllegalStateException("loading dump failed");
			}
		}
	}

	private void setRootPasswordOn(String socketPath) throws IOException, InterruptedException
	{
		for (int retry = 0; retry < 5; retry++) {
			final Process client = buildProcess(CLIENT_BINARY_PATH,
				"--socket=" + socketPath,
				"--user=root").start();
			client.getOutputStream().write(("SET PASSWORD FOR 'root'@'localhost' = PASSWORD('"
				+ ROOT_PASSWORD +
				"');\n").getBytes());

			try {
				client.getOutputStream().close();
			} catch (IOException ignored) {
				System.err.println("couldn't close stdin, probably already closed");
			}
			if (0 == client.waitFor()) {
				return;
			}

			// don't believe you can check if the server has started...
			Thread.sleep(100);
		}
		throw new IllegalStateException("couldn't change root password");
	}

	interface SqlConsumer
	{
		void accept(Connection connection) throws SQLException;
	}

	public void runAsRoot(SqlConsumer code) throws SQLException
	{
		try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:" + port + "/",
			"root", ROOT_PASSWORD)) {
			code.accept(conn);
		}
	}

	@Override
	public void close() throws IOException
	{
		server.destroyForcibly();
		TestTemps.deleteInBackground(base);
	}

	private static String findWorkingExecutable(String... candidates)
	{
		for (String candidate : candidates) {
			if (new File(candidate).canExecute()) {
				return candidate;
			}
		}
		throw new IllegalStateException("Couldn't find a working binary in: " + Arrays.toString(candidates));
	}

	static int findAPort() throws IOException
	{
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	static void runAndAwait(String... args) throws InterruptedException, IOException
	{
		final Process proc = buildProcess(args).start();
		proc.getOutputStream().close();
		if (0 != proc.waitFor()) {
			throw new IllegalStateException("process failed");
		}
	}

	static ProcessBuilder buildProcess(String... args)
	{
		final ArrayList<String> newArgs = new ArrayList<>();
		if (null != EAT_MY_DATA_PATH) {
			newArgs.add(EAT_MY_DATA_PATH);
		}
		Collections.addAll(newArgs, args);
		ProcessBuilder builder = new ProcessBuilder(newArgs);
		builder.redirectError(ProcessBuilder.Redirect.INHERIT);
		builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);

		List<String> escaped = newArgs.stream()
			.map(s -> s.replaceAll("'", "'\\''")).collect(Collectors.toList());
		System.out.println("$ '" + Joiner.on("' '").join(escaped) + '\'');
		return builder;
	}
}
