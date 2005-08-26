/* This class would have comments, but, trust me, you don't want to know what it does. */

package org.uwcs.choob.support;

public class ChannelEvent extends anEvent
{

	// <Java> Noo, a c-style enum would be too easy!
	public static final int ce_Action = 1;
	public static final int ce_ChannelInfo = 2;
	public static final int ce_DeVoice = 3;
	public static final int ce_Deop = 4;
	public static final int ce_Finger = 5;
	public static final int ce_Invite = 6;
	public static final int ce_Join = 7;
	public static final int ce_Kick = 8;
	public static final int ce_Message = 9;
	public static final int ce_Mode = 10;
	public static final int ce_NickChange = 11;
	public static final int ce_Notice = 12;
	public static final int ce_Op = 13;
	public static final int ce_Part = 14;
	public static final int ce_Ping = 15;
	public static final int ce_PrivateMessage = 16;
	public static final int ce_Quit = 17;
	public static final int ce_RemoveChannelBan = 18;
	public static final int ce_RemoveChannelKey = 19;
	public static final int ce_RemoveChannelLimit = 20;
	public static final int ce_RemoveInviteOnly = 21;
	public static final int ce_RemoveModerated = 22;
	public static final int ce_RemoveNoExternalMessages = 23;
	public static final int ce_RemovePrivate = 24;
	public static final int ce_RemoveSecret = 25;
	public static final int ce_RemoveTopicProtection = 26;
	public static final int ce_ServerPing = 27;
	public static final int ce_ServerResponse = 28;
	public static final int ce_SetChannelBan = 29;
	public static final int ce_SetChannelKey = 30;
	public static final int ce_SetChannelLimit = 31;
	public static final int ce_SetInviteOnly = 32;
	public static final int ce_SetModerated = 33;
	public static final int ce_SetNoExternalMessages = 34;
	public static final int ce_SetPrivate = 35;
	public static final int ce_SetSecret = 36;
	public static final int ce_SetTopicProtection = 37;
	public static final int ce_Time = 38;
	public static final int ce_Topic = 39;
	public static final int ce_Unknown = 40;
	public static final int ce_UserMode = 41;
	public static final int ce_Version = 42;
	public static final int ce_Voice = 43;


	static final String methodName(int ce_code) throws Exception
	{
		switch (ce_code)
		{
			case ce_Action: return "onAction";
			case ce_ChannelInfo: return "onChannelInfo";
			case ce_DeVoice: return "onDeVoice";
			case ce_Deop: return "onDeop";
			case ce_Finger: return "onFinger";
			case ce_Invite: return "onInvite";
			case ce_Join: return "onJoin";
			case ce_Kick: return "onKick";
			case ce_Message: return "onMessage";
			case ce_Mode: return "onMode";
			case ce_NickChange: return "onNickChange";
			case ce_Notice: return "onNotice";
			case ce_Op: return "onOp";
			case ce_Part: return "onPart";
			case ce_Ping: return "onPing";
			case ce_PrivateMessage: return "onPrivateMessage";
			case ce_Quit: return "onQuit";
			case ce_RemoveChannelBan: return "onRemoveChannelBan";
			case ce_RemoveChannelKey: return "onRemoveChannelKey";
			case ce_RemoveChannelLimit: return "onRemoveChannelLimit";
			case ce_RemoveInviteOnly: return "onRemoveInviteOnly";
			case ce_RemoveModerated: return "onRemoveModerated";
			case ce_RemoveNoExternalMessages: return "onRemoveNoExternalMessages";
			case ce_RemovePrivate: return "onRemovePrivate";
			case ce_RemoveSecret: return "onRemoveSecret";
			case ce_RemoveTopicProtection: return "onRemoveTopicProtection";
			case ce_ServerPing: return "onServerPing";
			case ce_ServerResponse: return "onServerResponse";
			case ce_SetChannelBan: return "onSetChannelBan";
			case ce_SetChannelKey: return "onSetChannelKey";
			case ce_SetChannelLimit: return "onSetChannelLimit";
			case ce_SetInviteOnly: return "onSetInviteOnly";
			case ce_SetModerated: return "onSetModerated";
			case ce_SetNoExternalMessages: return "onSetNoExternalMessages";
			case ce_SetPrivate: return "onSetPrivate";
			case ce_SetSecret: return "onSetSecret";
			case ce_SetTopicProtection: return "onSetTopicProtection";
			case ce_Time: return "onTime";
			case ce_Topic: return "onTopic";
			case ce_Unknown: return "onUnknown";
			case ce_UserMode: return "onUserMode";
			case ce_Version: return "onVersion";
			case ce_Voice: return "onVoice";
		}

		// It's my party and I'll cry if I want to.
		throw new Exception ("Unrecognised event code.");
	}

	public String getMethodName() throws Exception
	{
		return methodName(ce_code);
	}

	// Simple ones..
	public String getChanged() { return getArg(5); }
	public String getCode() { return getArg(1); }
	public String getLimit() { return getArg(5); }
	public String getUserCount() { return getArg(2); }
	public String getDate() { return getArg(4); }
	public String getAction() { return getArg(5); }
	public String getHostmask() { return getArg(5); }
	public String getKey() { return getArg(0); }
	public String getKickerHostname() { return getArg(4); }
	public String getKickerLogin() { return getArg(3); }
	public String getKickerNick() { return getArg(2); }
	public String getLine() { return getArg(1); }
	public String getLogin() { return getArg(2); }
	public String getMode() { return getArg(5); }
	public String getNewNick() { return getArg(4); }
	public String getNotice() { return getArg(5); }
	public String getOldNick() { return getArg(1); }
	public String getPingValue() { return getArg(5); }
	public String getRecipient() { return getArg(5); }
	public String getRecipientNick() { return getArg(5); }
	public String getSetBy() { return getArg(3); }
	public String getTargetNick() { return getArg(1); }

	// Bwhwha.. wbaahwhawb.. bwa.. sob..
	public String getSourceNick()
	{
		switch (ce_code)
		{
			case ce_Finger:
			case ce_Notice:
			case ce_Ping:
			case ce_Quit:
			case ce_Time:
			case ce_Version:
			return getArg(1);
		}
		return getArg(2);
	}

	public String getChannel()
	{
		if (ce_code == ce_Invite)
			return getArg(5);
		return getArg(1);
	}

	public String getTopic()
	{
		if (ce_code == ce_ChannelInfo)
			return getArg(3);
		return getArg(2);
	}

	public String getSourceLogin()
	{
		switch (ce_code)
		{
			case ce_Finger:
			case ce_Notice:
			case ce_Ping:
			case ce_Quit:
			case ce_Time:
			case ce_Version:
			return getArg(2);
		}
		return getArg(3);
	}

	public String getSourceHostname()
	{
		switch (ce_code)
		{
			case ce_Finger:
			case ce_Notice:
			case ce_Ping:
			case ce_Quit:
			case ce_Time:
			case ce_Version:
			return getArg(2);
		}
		return getArg(3);
	}
	public String getMessage()
	{
		if (ce_code==ce_Message)
			return getArg(5);
		return getArg(4);
	}

	public String getReason()
	{
		if (ce_code==ce_Quit)
			return getArg(4);
		return getArg(6);
	}

	public String getResponse()
	{
		if (ce_code==ce_ServerPing)
			return getArg(1);
		return getArg(2);
	}

	public String getSender()
	{
		switch (ce_code)
		{
			case ce_Action:
			case ce_PrivateMessage:
			return getArg(1);
		}
		return getArg(2);
	}
	public String getTarget()
	{
		switch (ce_code)
		{
			case ce_Action:
			case ce_Finger:
			case ce_NickChange:
			return getArg(2);
		}
		return getArg(3);
	}

	public String getHostname()
	{
		switch (ce_code)
		{
			case ce_Action:
			case ce_Finger:
			case ce_NickChange:
			return getArg(3);
		}
		return getArg(4);
	}

	// Actual start of the code:

	private String[] args;
	private int ce_code;
	/**
	 * Getter helper for Args.
	 */
	public String getArg(int i)
	{
		return args[i-1];
	}

	public String[] getArgs()
	{
		return (String[])this.args.clone();
	}

	public void setArgs(String[] args)
	{
		this.args = (String[]) args.clone();
	}

	public int getce_code()
	{
		return ce_code;
	}

	public void setce_code(int ce_code)
	{
		this.ce_code=ce_code;
	}

	public ChannelEvent(int ce_code, String[] args)
	{
		this.ce_code=ce_code;
		setArgs(args);
		this.random = ((int)(Math.random()*127));
		this.millis = System.currentTimeMillis();
	}
}