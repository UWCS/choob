package uk.co.uwcs.choob;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ThreadFactory;

public class TestTemps
{
	public static File createTemporaryDirectory() {
		return Files.createTempDir();
	}

	public static void deleteInBackground(File directory) {
		final Thread deleting = newThread(
			() -> {
				System.err.println("deleting " + directory);
				FileUtils.deleteDirectory(directory);
				System.err.println("deleted " + directory);
			});
		deleting.start();
		Runtime.getRuntime().addShutdownHook(newThread(deleting::join));
	}

	public static Thread newThread(final CallableVoid code) {
		return new Thread(() -> {
			try {
				code.run();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		});
	}

	public interface CallableVoid {
		void run() throws Exception;
	}
}
