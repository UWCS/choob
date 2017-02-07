package uk.co.uwcs.choob;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.Writer;

class LogWriter extends Writer {
	private final Logger logger;

	LogWriter(Logger logger) {
		this.logger = logger;
	}

	@Override
	public void write(char[] cbuf, int off, int len) throws IOException {
		while (len > 0 && '\n' == cbuf[off + len - 1]) {
			--len;
		}

		final String asString = new String(cbuf, off, len);
		if (!asString.isEmpty()) {
			logger.info(asString);
		}
	}

	@Override
	public void flush() throws IOException {
	}

	@Override
	public void close() throws IOException {
	}
}
