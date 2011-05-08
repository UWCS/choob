import java.security.Permission;
import java.util.List;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobPermission;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

class StoredSetting {
	public int id;
	public String method;
}

public class AuthSelector {

	private final Modules mods;
	private final IRCInterface irc;

	public AuthSelector(final Modules mods, final IRCInterface irc) {
		this.mods = mods;
		this.irc = irc;
	}

	public String[] info()	{
		return new String[] {
			"Auth method selection plugin.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev: 946 $$Date: 2007-07-14 10:58:37 +0100 (Sat, 14 Jul 2007) $"
		};
	}

	public String[] helpApi = {
			"Using the GetAuthMethod api call the currently selected method " +
			"of authentication can be determined."
	};

	public String apiGetAuthMethod() {
		final List<StoredSetting> results = mods.odb.retrieve(StoredSetting.class, "WHERE id = 1");
		if (results.size() != 1) {
			return "unknown";
		}
		final StoredSetting authMethod = results.get(0);
		return authMethod.method;

	}

	public String[] helpSetAuthMethod = {
			"Specify the authentication method to use.",
			"<AuthType>",
			"<AuthType> is the type of authentication to use, either" +
			" \"nickserv\" or \"quakenet\" as appropriate."
	};

	public void commandSetAuthMethod(final Message mes) {
		// Check security
		final Permission permission = new ChoobPermission("plugin.authselector.setmethod");
		if (!mods.security.hasNickPerm(permission, mes)) {
			irc.sendContextReply(mes, "Permission denied. You require Choob " +
					"permssion \"plugin.authselector.setmethod\" in order to " +
					"do this.");
			return;
		}

		final String authType = mods.util.getParamString(mes).toLowerCase();
		if (authType.length() == 0) {
			irc.sendContextReply(mes, "Please provide an authentication method to use.");
		}

		StoredSetting authMethod;
		final List<StoredSetting> results = mods.odb.retrieve(StoredSetting.class, "WHERE id = 1");
		if (results.size() != 1) {
			authMethod = new StoredSetting();
			authMethod.id = 1;
			authMethod.method = "unknown";
			mods.odb.save(authMethod);
		} else {
			authMethod = results.get(0);
		}

		if (authType.equals("nickserv")) {
			authMethod.method = authType;
			mods.odb.update(authMethod);

			irc.sendContextReply(mes, "Enabled NickServ authentication.");
			return;
		}

		if (authType.equals("quakenet")) {
			authMethod.method = authType;
			mods.odb.update(authMethod);
			irc.sendContextReply(mes, "Enabled Quakenet authentication.");
			return;
		}

		irc.sendContextReply(mes, "Invalid authentication type. Please use" +
				" either \"nickserv\" or \"quakenet\" as appropriate.");
	}
}
