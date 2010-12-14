import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketPermission;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.plugins.RequiresPermission;


@RequiresPermission(value=SocketPermission.class,permission="truck.gravitysensation.com",action="connect,resolve")
public class TrickyTrucks {

	private Modules mods;

	final OutputStream os;
	final InputStream is;
	final PrintStream failed;

	public TrickyTrucks(final Modules mods) throws UnknownHostException, IOException
	{
		this.mods = mods;
		for (Nick n : mods.odb.retrieve(Nick.class, "")) {
			trackedUsers.add(n.nick);
		}

		final Socket s = new Socket(InetAddress.getByName("truck.gravitysensation.com"), 23000);
		failed = new PrintStream(new FileOutputStream("wrong.rej"));

		os = s.getOutputStream();
		is = s.getInputStream();
		setup(os, is);

	}

	private final Set<String> trackedUsers = new HashSet<String>();

	public String commandAddPlayer(String msg) throws Exception {

		if (msg.length() > 16) {
			return "Too long!";
		}

		final List<Nick> values = mods.odb.retrieve(Nick.class, "where nick = '" + mods.odb.escapeString(msg) + "'");
		if(values.size() == 0) {
			trackedUsers.add(msg);
			mods.odb.save(new Nick(msg));
			return "Added " + msg;
		}

		return msg + " already existed";
	}

	public String commandTrackStatus(String id) throws Exception {
		return lookup(is, os, failed, trackedUsers, Integer.parseInt(id));
	}

	private static String lookup(final InputStream is, final OutputStream os, final PrintStream failed,
			final Set<String> trackedUsers, int track) throws IOException {
		final StringBuilder sb = new StringBuilder("For track " + track + ":");
		int pos = 0;
		int ourpos = 0;
		final List<Score> s = getScores(failed, os, is, track, 0);
		for (Score m : s) {
			++pos;
			if (trackedUsers.contains(m.name)) {
				sb.append("  ").append(++ourpos).append(") ")
				.append(m.name).append(", ")
				.append(new DecimalFormat("#.00").format(m.time)).append("s")
				.append(" (").append(pos).append(postfix(pos)).append(").");
			}
		}
		return sb.toString();
	}

	private static String postfix(final int n)
	{
		if (n % 100 >= 11 && n % 100 <= 13)
			return "th";

		switch (n % 10)
		{
			case 1: return "st";
			case 2: return "nd";
			case 3: return "rd";
		}
		return "th";
	}

	/** @param track As displayed; apart from home screen.
	 * @param truck 0 for free truck, upwards. */
	private static List<Score> getScores(final PrintStream failed, final OutputStream os,
			final InputStream is, int track, int truck) throws IOException {
		request(os, track, truck);
		char[] nc = readPacket(is);

		List<Score> pared;
		try {
			pared = parse(decode(nc));
		} catch (Exception e) {
			e.printStackTrace(failed);
			for (char c : nc)
				failed.print((int)c + ", ");
			failed.flush();
			throw new RuntimeException(e);
		}
		return pared;
	}

	private static void request(final OutputStream os, int track, int truck) throws IOException {
		byte[] req = new byte[] { 0x09, 0, 0x19, (byte) (track & 0xff), (byte) ((track >> 8) & 0xff),
				(byte) ((track >> 16) & 0xff), (byte) ((track >> 24) & 0xff), (byte) truck, 0, 0, 0 };
		os.write(req);
		os.flush();
	}

	private static void setup(final OutputStream os, final InputStream is) throws IOException {

		final byte[] initial = new byte[] {
				0x0d, 0x00, 0x04, 0x7e, (byte) 0xc1, 0x36, 0x65, 0x10,
				0x00, 0x00, 0x00, (byte) 0xfa, 0x0d, 0x08, 0x0a };
		final byte[] login = new byte[] { 0x09, 00, 0x1a, 00, 00, 00, 00, 00, 00, 00, 00, };
		final byte[] privacies = new byte[] { 0x54, 00, 0x05, 0x65, 00, 00, 00, 00, 00,
				00,
				00, // username
				00, 00, 00, 00, 00, 00, 00, 00, 65, 84, 73, 32, 82, 97, 100, 101, 111, 110,
				32,
				72, // system config
				68, 32, 53, 56, 48, 48, 32, 83, 101, 114, 105, 101, 115, 32, 32, 59, 97, 116, 105, 99, 102, 120, 51,
				50, 46, 100, 108, 108, 59, 99, 111, 114, 101, 115, 58, 52, 59, 49, 51, 50, 56, 120, 56, 52, 48, 59,
				100, 101, 116, 97, 105, 108, 58, 50, 59, };
		final byte[] login2 = new byte[] { 0x04, 00, 0x09, 00, 00, 00, };

		os.write(initial);
		os.flush();
		is.read(new byte[90000]);

		os.write(login);
		os.flush();
		is.read(new byte[90000]);
		os.write(privacies);
		os.flush();
		os.write(login2);
		os.flush();
		is.read(new byte[90000]);
	}

	private static char[] readPacket(final InputStream is) throws IOException {
		byte[] n = new byte[900000];
		final int found = is.read(n);
		char[] nc = new char[found];
		for (int i = 63; i < found; ++i)
			nc[i - 63] = (char) (n[i] < 0 ? 256 + n[i] : n[i]);
		return nc;
	}

	static class Score {
        final String name;
        private final double time;
        private final boolean hard;
        final boolean online;

        public Score(String name, double time, boolean hard, boolean online) {
            this.name = name;
            this.time = time;
            this.hard = hard;
            this.online = online;
        }

        @Override
        public String toString() {
            return "Score [name=" + name + ", time=" + time + ","
            + (hard ? " (hard)" : "")
            + (online ? " (online)" : "") + "]";
        }
	}

	static List<Score> parse(char[] b) {
		final List<Score> ret = new ArrayList<Score>(b.length / 32);
		int ptr = 0;
		while (ptr < b.length - 30) {
			final String name = cstring(b, ptr, 16);
			ptr += 16;
			final double time = dword(b, ptr) / 120.;
			ptr += 4;
			final boolean hard = b[ptr + 6] != 0;
			final boolean online = b[ptr + 7] != 0;
			ptr += 12;
			ret.add(new Score(name, time, hard, online));
		}
		return ret;
	}

	private static long dword(char[] b, int ptr) {
		return (b[ptr] + (b[ptr + 1] << 8));
	}

	private static String cstring(char[] b, int ptr, int i) {
		final String s = new String(b, ptr, i);
		final int ind = s.indexOf(0);
		if (-1 == ind)
			return s;
		return s.substring(0, ind);
	}

	static char[] decode(char[] in) {
		char[] out = new char[in.length * 5];

		int outptr = 0;
		int inptr = 0;
		char flag = in[inptr++];
		flag &= 0x1f;
		char esp10 = flag;

		while (true) {
			if (flag >= 0x20) {

				char highflag = (char) (flag >> 5);
				int lowflag = -((flag & 0x1f) << 8);

				--highflag;

				if (6 == highflag) {
					highflag = (char) (in[inptr++] + 6);
				}

				lowflag -= in[inptr++];

				int sourceptr = outptr + lowflag;

				if (inptr < in.length)
					esp10 = flag = in[inptr++];
				else
					throw new AssertionError();

				if (outptr == sourceptr) {

					char thing = out[outptr - 1];

					out[outptr++] = thing;
					out[outptr++] = thing;
					out[outptr++] = thing;

					if (highflag != 0) {

						flag = esp10;

						for (int i = 0; i < highflag; ++i)
							out[outptr++] = thing;
					}
				} else {

					--sourceptr;

					out[outptr++] = out[sourceptr++];
					out[outptr++] = out[sourceptr++];
					out[outptr++] = out[sourceptr++];

					if ((highflag & 1) == 1) {

						out[outptr++] = out[sourceptr++];

						--highflag;
					}

					int tooutptr = outptr;
					outptr += highflag;
					highflag >>= 1;

					while (highflag != 0) {
						out[tooutptr++] = out[sourceptr++];
						out[tooutptr++] = out[sourceptr++];

						--highflag;
					}
				}
			} else {
				++flag;
				int inend = inptr + flag;
				if (inend >= in.length)
					return Arrays.copyOfRange(out, 0, outptr);

				for (int i = 0; i < flag; ++i)
					out[outptr++] = in[inptr++];

				flag = in[inptr++];
				esp10 = flag;
			}
		}

	}
}

class Nick {
	public int id;
	public String nick;

	public Nick() {
	}

	public Nick(final String nick) {
		this.nick = nick;
	}

}
