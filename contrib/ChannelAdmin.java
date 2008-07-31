import java.util.List;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.ChoobPermission;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;
import uk.co.uwcs.choob.support.events.PrivateEvent;

/**
 * Channel administration plugin, allows suitably entitled users to perform
 * channel administration functions.
 * 
 * @author Blood God
 */

class ChannelUser {
    public int id;
    public String userName;
    public int accessLevel;
    public String channel;
}

enum UserAccess {
    OWNER, MASTER, OP, VOICE, NONE
}

public class ChannelAdmin {
    public String[] info() {
        return new String[] {
            "Plugin to allow administration of channels.", "The Choob Team",
            "choob@uwcs.co.uk",
            "$Rev$$Date$"
        };
    }

    Modules mods;
    IRCInterface irc;

    public ChannelAdmin(Modules mods, IRCInterface irc) {
        this.irc = irc;
        this.mods = mods;
    }

    public String[] helpOp = {
        "Give a user ops on a channel: ",
        "[<Channel>] [<Nick>]",
        "<Channel> optional channel to perform action on, this is required in a PM",
        "<Nick> optional nick to perform action on, if not provided will default to current user"
    };

    public void commandOp(Message mes, Modules mods, IRCInterface irc) {
        String[] params = mods.util.getParamArray(mes);
        String userName = mods.security.getUserAuthName(mes.getNick());
        String channel = mes.getContext();

        // Check that the user is authed
        if (!mods.security.hasAuth(userName)) {
            irc.sendContextReply(mes,
                "You must be authed to perform this command.");
            return;
        }

        // Check the user's access level
        if ((!mods.security.hasNickPerm(new ChoobPermission(
            "plugins.channeladmin.admin"), mes))
            && (UserAccess.OWNER != getUserAccess(userName, channel))
            && (UserAccess.MASTER != getUserAccess(userName, channel))
            && (UserAccess.OP != getUserAccess(userName, channel))) {
            irc
                .sendContextReply(mes,
                    "You do not have the required access on this channel to run this command.");
            return;
        }

        if (mes instanceof PrivateEvent) {
            if (3 != params.length) {
                irc.sendContextReply(mes,
                    "Syntax: ChannelAdmin.Op <Channel> <NickName>");
                return;
            }
        }

        try {
            switch (params.length) {
            case 1:
                irc.op(channel, userName);
                break;
            case 2:
                irc.op(channel, params[1]);
                break;
            case 3:
                irc.op(params[1], params[2]);
                if (mes instanceof PrivateEvent) {
                    irc.sendContextReply(mes, "Okay");
                }
                break;
            default:
                irc.sendContextReply(mes, "Syntax: ChannelAdmin.Op "
                    + helpOp[1]);
            }
        } catch (ChoobException e) {
            irc.sendContextReply(mes, "Could not op user: " + e.getMessage());
        }
    }

    public String[] helpDeop = {
        "Remove ops from a user",
        "[<Channel>] [<Nick>]",
        "<Channel> optional channel to perform action on, this is required in a PM",
        "<Nick> optional nick to perform action on, if not provided will default to current user"
    };

    public void commandDeop(Message mes, Modules mods, IRCInterface irc) {
        String[] params = mods.util.getParamArray(mes);
        String userName = mods.security.getUserAuthName(mes.getNick());
        String channel = mes.getTarget();

        // Check that the user is authed
        if (!mods.security.hasAuth(userName)) {
            irc.sendContextReply(mes,
                "You must be authed to perform this command.");
            return;
        }

        // Check the user's access level
        if ((!mods.security.hasNickPerm(new ChoobPermission(
            "plugins.channeladmin.admin"), mes))
            && (UserAccess.OWNER != getUserAccess(userName, channel))
            && (UserAccess.MASTER != getUserAccess(userName, channel))
            && (UserAccess.OP != getUserAccess(userName, channel))) {
            irc
                .sendContextReply(mes,
                    "You do not have the required access on this channel to run this command.");
            return;
        }

        if (mes instanceof PrivateEvent) {
            if (3 != params.length) {
                irc.sendContextReply(mes,
                    "Syntax: ChannelAdmin.Deop <Channel> <Nick>");
                return;
            }
        }

        try {
            switch (params.length) {
            case 1:
                irc.deOp(channel, userName);
                break;
            case 2:
            	if (irc.getNickname().equals(params[1])) {
            		irc.sendContextReply(mes, "Can't deop myself!");
            		break;
            	}
                irc.deOp(channel, params[1]);
                break;
            case 3:
            	if (irc.getNickname().equals(params[2])) {
            		irc.sendContextReply(mes, "Can't deop myself!");
            		break;
            	}
                irc.deOp(params[1], params[2]);
                if (mes instanceof PrivateEvent) {
                    irc.sendContextReply(mes, "Okay");
                }
                break;
            default:
                irc.sendContextReply(mes, "Syntax: ChannelAdmin.Deop "
                    + helpDeop[1]);
            }
        } catch (ChoobException e) {
            irc.sendContextReply(mes, "Could not perform action: "
                + e.getMessage());
        }
    }

    public String[] helpVoice = {
        "Give voice to a user",
        "[<Channel>] [<Nick>]",
        "<Channel> optional channel to perform action on, this is required in a PM",
        "<Nick> optional nick to perform action on, if not provided will default to current user"
    };

    public void commandVoice(Message mes, Modules mods, IRCInterface irc) {
        String[] params = mods.util.getParamArray(mes);
        String userName = mods.security.getUserAuthName(mes.getNick());
        String channel = mes.getTarget();

        // Check that the user is authed
        if (!mods.security.hasAuth(userName)) {
            irc.sendContextReply(mes,
                "You must be authed to perform this command.");
            return;
        }

        // Check the user's access level
        if ((!mods.security.hasNickPerm(new ChoobPermission(
            "plugins.channeladmin.admin"), mes))
            && (UserAccess.OWNER != getUserAccess(userName, channel))
            && (UserAccess.MASTER != getUserAccess(userName, channel))
            && (UserAccess.OP != getUserAccess(userName, channel))
            && (UserAccess.VOICE != getUserAccess(userName, channel))) {
            irc
                .sendContextReply(mes,
                    "You do not have the required access on this channel to run this command.");
            return;
        }

        if (mes instanceof PrivateEvent) {
            if (3 != params.length) {
                irc.sendContextReply(mes,
                    "Syntax: ChannelAdmin.Voice <Channel> <Nick>");
                return;
            }
        }

        try {
            switch (params.length) {
            case 1:
                irc.voice(channel, userName);
                break;
            case 2:
                irc.voice(channel, params[1]);
                break;
            case 3:
                irc.voice(params[1], params[2]);
                if (mes instanceof PrivateEvent) {
                    irc.sendContextReply(mes, "Okay");
                }
                break;
            default:
                irc.sendContextReply(mes, "Syntax: ChannelAdmin.Voice "
                    + helpVoice[1]);
            }
        } catch (ChoobException e) {
            irc.sendContextReply(mes, "Could not perform action: "
                + e.getMessage());
        }
    }

    public String[] helpDevoice = {
        "Remove voice from a user",
        "[<Channel>] [<Nick>]",
        "<Channel> optional channel to perform action on, this is required in a PM",
        "<Nick> optional nick to perform action on, if not provided will default to current user"
    };

    public void commandDevoice(Message mes, Modules mods, IRCInterface irc) {
        String[] params = mods.util.getParamArray(mes);
        String userName = mods.security.getUserAuthName(mes.getNick());
        String channel = mes.getTarget();

        // Check that the user is authed
        if (!mods.security.hasAuth(userName)) {
            irc.sendContextReply(mes,
                "You must be authed to perform this command.");
            return;
        }

        // Check the user's access level
        if ((!mods.security.hasNickPerm(new ChoobPermission(
            "plugins.channeladmin.admin"), mes))
            && (UserAccess.OWNER != getUserAccess(userName, channel))
            && (UserAccess.MASTER != getUserAccess(userName, channel))
            && (UserAccess.OP != getUserAccess(userName, channel))
            && (UserAccess.VOICE != getUserAccess(userName, channel))) {
            irc
                .sendContextReply(mes,
                    "You do not have the required access on this channel to run this command.");
            return;
        }

        if (mes instanceof PrivateEvent) {
            if (3 != params.length) {
                irc.sendContextReply(mes,
                    "Syntax: ChannelAdmin.Devoice <Channel> <Nick>");
                return;
            }
        }

        try {
            switch (params.length) {
            case 1:
                irc.deVoice(channel, userName);
                break;
            case 2:
            	if (irc.getNickname().equals(params[1])) {
            		irc.sendContextReply(mes, "Can't devoice myself!");
            		break;
            	}
                irc.deVoice(channel, params[1]);
                break;
            case 3:
            	if (irc.getNickname().equals(params[1])) {
            		irc.sendContextReply(mes, "Can't devoice myself!");
            		break;
            	}
                irc.deVoice(params[1], params[2]);
                if (mes instanceof PrivateEvent) {
                    irc.sendContextReply(mes, "Okay");
                }
                break;
            default:
                irc.sendContextReply(mes, "Syntax: ChannelAdmin.Devoice "
                    + helpDevoice[1]);
            }
        } catch (ChoobException e) {
            irc.sendContextReply(mes, "Could not perform action: "
                + e.getMessage());
        }
    }

    public String[] helpKick = {
        "Kick a user from a channel",
        "[<Channel>] <Nick> <Reason>",
        "<Channel> optional channel to perform action on, this is required in a PM",
        "<Nick> User to kick from channel", "<Reason> Reason for kick",
    };

    public void commandKick(Message mes, Modules mods, IRCInterface irc) {
        String[] params = mods.util.getParamArray(mes);
        String userName = mods.security.getUserAuthName(mes.getNick());
        String channel = mes.getTarget();

        // Check that the user is authed
        if (!mods.security.hasAuth(userName)) {
            irc.sendContextReply(mes,
                "You must be authed to perform this command.");
            return;
        }

        // Check the user's access level
        if ((!mods.security.hasNickPerm(new ChoobPermission(
            "plugins.channeladmin.admin"), mes))
            && (UserAccess.OWNER != getUserAccess(userName, channel))
            && (UserAccess.MASTER != getUserAccess(userName, channel))
            && (UserAccess.OP != getUserAccess(userName, channel))) {
            irc
                .sendContextReply(mes,
                    "You do not have the required access on this channel to run this command.");
            return;
        }

        if (mes instanceof PrivateEvent) {
            if (4 != params.length) {
                irc.sendContextReply(mes,
                    "Syntax: ChannelAdmin.Kick <Channel> <Nick> <Reason>");
                return;
            }
        }

        try {
            switch (params.length) {
            case 3:
            	if (irc.getNickname().equals(params[1])) {
            		irc.sendContextReply(mes, "Can't kick myself!");
            		break;
            	}
                irc.kick(channel, params[1], params[2]);
                break;
            case 4:
            	if (irc.getNickname().equals(params[2])) {
            		irc.sendContextReply(mes, "Can't kick myself!");
            		break;
            	}
                irc.kick(params[1], params[2], params[3]);
                if (mes instanceof PrivateEvent) {
                    irc.sendContextReply(mes, "Okay");
                }
                break;
            default:
                irc.sendContextReply(mes, "Syntax: ChannelAdmin.Kick "
                    + helpKick[1]);
            }
        } catch (ChoobException e) {
            irc.sendContextReply(mes, "Could not perform action: "
                + e.getMessage());
        }
    }

    public String[] helpBan = {
        "Ban a user from a channel",
        "[<Channel>] <Nick>",
        "<Channel> optional channel to perform action on, this is required in a PM",
        "<Nick> User to ban from the channel"
    };

    public void commandBan(Message mes, Modules mods, IRCInterface irc) {
        String[] params = mods.util.getParamArray(mes);
        String userName = mods.security.getUserAuthName(mes.getNick());
        String channel = mes.getTarget();

        // Check that the user is authed
        if (!mods.security.hasAuth(userName)) {
            irc.sendContextReply(mes,
                "You must be authed to perform this command.");
            return;
        }

        // Check the user's access level
        if ((!mods.security.hasNickPerm(new ChoobPermission(
            "plugins.channeladmin.admin"), mes))
            && (UserAccess.OWNER != getUserAccess(userName, channel))
            && (UserAccess.MASTER != getUserAccess(userName, channel))
            && (UserAccess.OP != getUserAccess(userName, channel))) {
            irc
                .sendContextReply(mes,
                    "You do not have the required access on this channel to run this command.");
            return;
        }

        if (mes instanceof PrivateEvent) {
            if (3 != params.length) {
                irc.sendContextReply(mes,
                    "Syntax: ChannelAdmin.Ban <Channel> <Nick>");
                return;
            }
        }

        try {
            switch (params.length) {
            case 2:
            	if (irc.getNickname().equals(params[1])) {
            		irc.sendContextReply(mes, "Can't ban myself!");
            		break;
            	}
                irc.ban(channel, params[1]);
                break;
            case 3:
            	if (irc.getNickname().equals(params[2])) {
            		irc.sendContextReply(mes, "Can't ban myself!");
            		break;
            	}
                irc.ban(params[1], params[2]);
                if (mes instanceof PrivateEvent) {
                    irc.sendContextReply(mes, "Okay");
                }
                break;
            default:
                irc.sendContextReply(mes, "Syntax: ChannelAdmin.Ban "
                    + helpBan[1]);
            }
        } catch (ChoobException e) {
            irc.sendContextReply(mes, "Could not perform action: "
                + e.getMessage());
        }
    }

    public String[] helpUnban = {
        "Remove a ban for a user on a channel",
        "[<Channel>] <Nick>",
        "<Channel> optional channel to perform action on, this is required in a PM",
        "<Nick> name of the user to unban"
    };

    public void commandUnban(Message mes, Modules mods, IRCInterface irc) {
        String[] params = mods.util.getParamArray(mes);
        String userName = mods.security.getUserAuthName(mes.getNick());
        String channel = mes.getTarget();

        // Check that the user is authed
        if (!mods.security.hasAuth(userName)) {
            irc.sendContextReply(mes,
                "You must be authed to perform this command.");
            return;
        }

        // Check the user's access level
        if ((!mods.security.hasNickPerm(new ChoobPermission(
            "plugins.channeladmin.admin"), mes))
            && (UserAccess.OWNER != getUserAccess(userName, channel))
            && (UserAccess.MASTER != getUserAccess(userName, channel))
            && (UserAccess.OP != getUserAccess(userName, channel))) {
            irc
                .sendContextReply(mes,
                    "You do not have the required access on this channel to run this command.");
            return;
        }

        if (mes instanceof PrivateEvent) {
            if (3 != params.length) {
                irc.sendContextReply(mes,
                    "Syntax: ChannelAdmin.Unban <Channel> <Nick>");
                return;
            }
        }

        try {
            switch (params.length) {
            case 2:
                irc.unban(channel, params[1]);
                break;
            case 3:
                irc.unban(params[1], params[2]);
                if (mes instanceof PrivateEvent) {
                    irc.sendContextReply(mes, "Okay");
                }
                break;
            default:
                irc.sendContextReply(mes, "Syntax: ChannelAdmin.Unban "
                    + helpUnban[1]);
            }
        } catch (ChoobException e) {
            irc.sendContextReply(mes, "Could not perform action: "
                + e.getMessage());
        }
    }

    public String[] helpKickBan = {
        "Ban and kick a user from a channel",
        "[<Channel>] <Nick> <Reason>",
        "<Channel> optional channel to perform action on, this is required in a PM",
        "<Nick> User to kick ban from channel", "<Reason> Reason for kick"
    };

    public void commandKickBan(Message mes, Modules mods, IRCInterface irc) {
        String[] params = mods.util.getParamArray(mes);
        String userName = mods.security.getUserAuthName(mes.getNick());
        String channel = mes.getTarget();

        // Check that the user is authed
        if (!mods.security.hasAuth(userName)) {
            irc.sendContextReply(mes,
                "You must be authed to perform this command.");
            return;
        }

        // Check the user's access level
        if ((!mods.security.hasNickPerm(new ChoobPermission(
            "plugins.channeladmin.admin"), mes))
            && (UserAccess.OWNER != getUserAccess(userName, channel))
            && (UserAccess.MASTER != getUserAccess(userName, channel))
            && (UserAccess.OP != getUserAccess(userName, channel))) {
            irc
                .sendContextReply(mes,
                    "You do not have the required access on this channel to run this command.");
            return;
        }

        if (mes instanceof PrivateEvent) {
            if (4 != params.length) {
                irc.sendContextReply(mes,
                    "Syntax: ChannelAdmin.KickBan <Channel> <Nick> <Reason>");
                return;
            }
        }

        try {
            switch (params.length) {
            case 3:
            	if (irc.getNickname().equals(params[1])) {
            		irc.sendContextReply(mes, "Can't kickban myself!");
            		break;
            	}
                irc.ban(channel, params[1]);
                irc.kick(channel, params[1], userName + ": " + params[2]);
                break;
            case 4:
            	if (irc.getNickname().equals(params[2])) {
            		irc.sendContextReply(mes, "Can't kickban myself!");
            		break;
            	}
                irc.ban(params[1], params[2]);
                irc.kick(params[1], params[2], userName + ": " + params[3]);
                if (mes instanceof PrivateEvent) {
                    irc.sendContextReply(mes, "Okay");
                }
                break;
            default:
                irc.sendContextReply(mes, "Syntax: ChannelAdmin.KickBan "
                    + helpKickBan[1]);
            }
        } catch (ChoobException e) {
            irc.sendContextReply(mes, "Could not perform action: "
                + e.getMessage());
        }
    }

    public String[] helpSetAccess = {
        "Set the access for a user on a channel",
        "[<Channel>] <Nick> <Access>",
        "<Channel> optional channel to perform action on, this is required in a PM",
        "<Nick> User to set access for on the channel",
        "<Access> Level of access to grant the user, one of: Owner, Master, Op, Voice and None"
    };

    public void commandSetAccess(Message mes, Modules mods, IRCInterface irc) {
        String[] params = mods.util.getParamArray(mes);
        String userName = mods.security.getUserAuthName(mes.getNick());
        String channel = mes.getTarget();

        // Check that the user is authed
        if (!mods.security.hasAuth(userName)) {
            irc.sendContextReply(mes,
                "You must be authed to perform this command.");
            return;
        }

        // Check the user's access level
        if ((!mods.security.hasNickPerm(new ChoobPermission(
            "plugins.channeladmin.admin"), mes))
            && (UserAccess.OWNER != getUserAccess(userName, channel))
            && (UserAccess.MASTER != getUserAccess(userName, channel))) {
            irc
                .sendContextReply(mes,
                    "You do not have the required access on this channel to run this command.");
            return;
        }

        if (mes instanceof PrivateEvent) {
            if (4 != params.length) {
                irc.sendContextReply(mes,
                    "Syntax: ChannelAdmin.SetAccess <Channel> <Nick> <Access>");
                return;
            }
        }
        String accessUser = "NONE";
        String accessSetting = "NONE";

        switch (params.length) {
        case 3:
            accessUser = params[1];
            accessSetting = params[2];
            break;
        case 4:
            channel = params[1];
            accessUser = params[2];
            accessSetting = params[3];
            break;
        default:
            irc.sendContextReply(mes, "Syntax: ChannelAdmin.SetAccess "
                + helpSetAccess[1]);
        }

        if (!mods.security.hasAuth(accessUser)) {
            irc.sendContextReply(mes,
                "Target user must be authed in order to have access granted");
            return;
        }

        int accessLevel = UserAccess.valueOf(accessSetting.toUpperCase())
            .ordinal();
        if ((!mods.security.hasNickPerm(new ChoobPermission(
            "plugins.channeladmin.admin"), mes))
            && (accessLevel < getUserAccess(userName, channel).ordinal())) {
            irc.sendContextReply(mes,
                "You do not have the access to set this access level.");
            return;
        }

        List<ChannelUser> results = mods.odb.retrieve(ChannelUser.class,
            "WHERE userName = '" + accessUser + "' AND channel = '" + channel
                + "'");
        ChannelUser user;
        if (0 == results.size()) {
            user = new ChannelUser();
            user.userName = accessUser;
            user.channel = channel;
            user.accessLevel = accessLevel;
        } else {
            user = results.get(0);
            user.accessLevel = accessLevel;
        }

        mods.odb.save(user);
        irc.sendContextReply(mes, "Successfully updated user permissions.");
    }

    public String[] helpShowAccess = {
        "Display the access for a channel",
        "[<Channel>] [<Nick>]",
        "<Channel> Optional channel to get the access for, either this or Nick is required in a PM",
        "<Nick> Optional nick to get access for, either this or Channel is required in a PM"
    };

    public void commandShowAccess(Message mes, Modules mods, IRCInterface irc) {
        String[] params = mods.util.getParamArray(mes);
        String channel = mes.getTarget();

        List<ChannelUser> users;
        String query = "WHERE channel = '" + channel + "'";
        
        switch (params.length) {
        default:
        case 1:
            if (mes instanceof PrivateEvent) {
                irc.sendContextReply(mes,
                    "Either Channel or Nick must be provided");
                return;
            }
            break;
        case 2:
            if (('#' == params[1].charAt(0)) || ('&' == params[1].charAt(0))) {
                query = "WHERE channel = '" + params[1] + "'";
            } else {
                query = "WHERE userName = '" + params[1] + "'"; 
            }
            break;
        case 3:
            query = "WHERE channel = '" + params[1] + "' AND userName = '" + params[2] + "'";
        }
        
        users = mods.odb.retrieve(ChannelUser.class, query);
        if (0 == users.size()) {
            irc.sendContextReply(mes,
                "There are no registered users matching this query.");
            return;
        }
        String[] userInfo = new String[users.size()];
        for (int i = 0; i < users.size(); i++) {
            ChannelUser user = users.get(i);
            userInfo[i] = user.userName + " (" + user.channel + ") :"
                + UserAccess.values()[user.accessLevel].toString().toLowerCase();
        }
        irc.sendContextReply(mes, userInfo);
    }

    private UserAccess getUserAccess(String userName, String channel) {
        // Retrieve access from ODB.
        List<ChannelUser> results = mods.odb.retrieve(ChannelUser.class,
            "WHERE userName = '" + userName + "' AND channel = '" + channel
                + "'");
        if (0 == results.size()) {
            return UserAccess.NONE;
        } else {
            return UserAccess.values()[results.get(0).accessLevel];
        }
    }
}
