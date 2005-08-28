#!/usr/bin/perl

use strict;
use warnings;

#public void onSyntheticMessage(SyntheticMessage mes)

# interfaces:
# MessageEvent (getText)
# ContextEvent (getReplyContext)
# UserEvent (getNick, getHostname, getUserName)
# MultiModeEvent (getModes)
# AimedEvent (getTarget)

# Inheritance map
our %inheritance = (
	IRCEvent => undef,

#	ChannelEvent => [qw/IRCEvent ContextEvent UserEvent/],
#	PrivateEvent => [qw/IRCEvent ContextEvent UserEvent/],

	Message => [qw/IRCEvent MessageEvent ContextEvent UserEvent AimedEvent/],

	PrivateMessage => [qw/Message PrivateEvent CommandEvent/],
	ChannelMessage => [qw/Message ChannelEvent CommandEvent/],
	Action => "__HORRORMUNGER__",
	PrivateAction => [qw/Message PrivateEvent/],
	ChannelAction => [qw/Message ChannelEvent/],

	ChannelInfo => [qw/IRCEvent ChannelEvent/],

	ChannelMode => [qw/IRCEvent ChannelEvent ModeEvent/],
	ChannelParamMode => [qw/ChannelMode ParamEvent/],
	ChannelUserMode => [qw/ChannelMode AimedEvent/],

	ChannelInvite => [qw/IRCEvent ChannelEvent UserEvent AimedEvent/],
	ChannelJoin => [qw/IRCEvent ChannelEvent ContextEvent UserEvent/],
	ChannelPart => [qw/IRCEvent ChannelEvent ContextEvent UserEvent/],
	ChannelKick => [qw/IRCEvent ChannelEvent ContextEvent UserEvent AimedEvent/],
	ChannelTopic => [qw/IRCEvent ChannelEvent ContextEvent MessageEvent/], # !!! (extras)

	QuitEvent => [qw/IRCEvent MessageEvent UserEvent/],

	NickChange => [qw/IRCEvent UserEvent NickChangeEvent/],

	UnknownEvent => [qw/IRCEvent/],

	ChannelModes => [qw/IRCEvent ChannelEvent MultiModeEvent/],
	UserModes => [qw/IRCEvent MultiModeEvent/],
);

# Parameters for stuff
our %params = (
	IRCEvent => [qw/methodName/],
	ChannelEvent => [qw/channel/],
	PrivateEvent => [qw//],
	CommandEvent => [qw//],
	UserEvent => [qw/nick login hostname/],
	MessageEvent => [qw/message/],
	ModeEvent => [qw/mode (boolean)set/],
	AimedEvent => [qw/target/],
	MultiModeEvent => [qw/modes/],
	NickChangeEvent => [qw/newNick/],
	ContextEvent => [qw//],
	ParamEvent => [qw/param/],
);

# Stuff that can be overridden in created of a synthetic event
our %overrides = (
	MessageEvent => [qw/message/],
);

my $eventHandlers = '';
while ($_ = <DATA>) {
	if (/^(\w+)\(([\w, ]+)\)(?:\[([\w, "=(]+)\])? (\w+), (.*)/) {
		my ($name, $params, $extraparams, $class, $desc) = ($1, $2, $3||"", $4, $5);

		my $evtName = "on$name";
		my @params = split /, /, $params;
		@params = (@params, (split /, /, $extraparams),
			"methodName = \"$evtName\"");
		my @paramProtos;
		my %paramConst;
		my %paramType;
		foreach (@params) {
			if (/^(?:(\w+) )?(\w+)(?: = ([("\w]+))?$/) {
				my ($type, $name, $def) = ($1, $2, $3);
				$type ||= "String";
				$paramType{$name} = $type;
				if (!$def) {
					push @paramProtos, "$type $name";
					$paramConst{$name} = $name;
				} else {
					$paramConst{$name} = "$def";
				}
			}
		}
		my $paramProtos = join ', ', @paramProtos;

		my $eventHandler = qq|\tprotected void $evtName($paramProtos) {\n|;
		if ($inheritance{$class} eq "__HORRORMUNGER__") {
			# Need to horrormunge!
			my %handlers;
			$paramConst{channel} = "target";
			foreach ('Private', 'Channel') {
				my @inherit = &getInherit($_.$class);

				my @constOrder = map { my @a=(); if ($params{$_}) { @a = @{$params{$_}} } (@a) } @inherit;
				map { s/\(\w+\)// } @constOrder;

				my $constParams = join ', ', map { $paramConst{$_} } @constOrder;

				$handlers{$_} = qq|spinThread(new $_$class($constParams))|;
			}
			$eventHandler .= <<END;
		if (target.indexOf('#') == 0)
			$handlers{Private};
		else
			$handlers{Channel};
	}

END
		} else {
			my @inherit = &getInherit($class);

			my @constOrder = map { my @a=(); if ($params{$_}) { @a = @{$params{$_}} } (@a) } @inherit;
			map { s/\(\w+\)// } @constOrder;

			my $constParams = join ', ', map { $paramConst{$_} } @constOrder;

			$eventHandler .= qq|\t\tspinThread(new $class($constParams));\n\t}\n\n|;
		}
		$eventHandlers .= $eventHandler;
	}
}

open CHOOB, "org/uwcs/choob/Choob.java";
my $choob = do { local $/; <CHOOB> };
close CHOOB;
$choob =~ s[(?<=// BEGIN PASTE!).*?(?=// END PASTE!)][\n\n$eventHandlers\t]s;
open CHOOB, ">org/uwcs/choob/Choob.java";
print CHOOB $choob;
close CHOOB;

foreach my $class (keys %inheritance) {
	next if $inheritance{$class} && $inheritance{$class} =~ /^_/;
	my @inherit = &getInherit($class);
	my @constOrder = map { my @a=(); if ($params{$_}) { @a = @{$params{$_}} } (@a) } @inherit;
	my %paramType;
	foreach(@constOrder) {
		if (s/\((\w+)\)//) {
			$paramType{$_} = $1;
		} else {
			$paramType{$_} = "String";
		}
	}
	my $constProto = join ', ', map { "$paramType{$_} $_" } @constOrder;

	my $extends = "";
	my $implements = "";
	my $super1 = "\t\tsuper();";
	my $super2 = "\t\tsuper(old);";
	my $overrideProto = "$class old";
	my (@myOverrides, @underOverrides, @allOverrides);
	my @superOrder;
	my $cloneProto = "";
	my $cloneCall = "this";
	my $extra = "";
	if ($inheritance{$class}) {
		my @i = @{$inheritance{$class}};
		$extends = "extends $i[0]";
		my @sInherit = &getInherit($i[0]);
		@superOrder = map { my @a=(); if ($params{$_}) { @a = @{$params{$_}} } (@a) } @sInherit;
		my $superParams = join ', ', @superOrder;
		$super1 = "\t\tsuper($superParams);";
		$implements = "implements ".join(", ",@i[1..$#i]) if @i > 1;
		if (grep /ContextEvent/, @inherit) {
			if (grep /UserEvent|ChannelEvent/, @i) {
				# Must re-implement getContext
				$extra .= <<END;
	/**
	 * Get the reply context in which this event resides
	 * \@returns The context
	 */
END
				if (grep /ChannelEvent/, @inherit) {
					$extra .= <<END;
	public String getContext() {
		return getChannel();
	}
END
				} else {
					$extra .= <<END;
	public String getContext() {
		return getNick();
	}
END
				}
			}
		}

		# Now for synthetic overrides
		@allOverrides = map { my @a=(); if ($overrides{$_}) { @a = @{$overrides{$_}} } (@a) } @inherit;
		@myOverrides = map { my @a=(); if ($overrides{$_}) { @a = @{$overrides{$_}} } (@a) } @i[1..$#i];
		@underOverrides = map { my @a=(); if ($overrides{$_}) { @a = @{$overrides{$_}} } (@a) } @sInherit;
		if (@underOverrides) {
			$super2 = "\t\tsuper(old, ".join(', ', @underOverrides).");";
		}
		if (@allOverrides) {
			$overrideProto = "$class old, ".join(', ', map { "$paramType{$_} $_" } @allOverrides);
			$cloneProto = join(', ', map { "$paramType{$_} $_" } @allOverrides);
			$cloneCall = 'this, '.join(', ', @allOverrides);
		}
	}

	if ($class eq "IRCEvent") {
		# Speshul things needed!
		push @constOrder, "synthLevel";
		$paramType{synthLevel} = "int";
		push @constOrder, "millis";
		$paramType{millis} = "long";
		push @constOrder, "random";
		$paramType{random} = "int";
		$super1 = "";
		$super2 = "";
	}

	my $members = "";
	my $memberInit1 = "";
	my $memberInit2 = "";
	my $getters = "";
	foreach my $field (grep { my$a=$_;!scalar(grep {$a eq $_} @superOrder) } @constOrder) {
		$members .= <<END;
	/**
	 * $field
	 */
	private final $paramType{$field} $field;

END
		$memberInit1 .= "\t\tthis.$field = $field;\n";

		# Is it overridden?
		if (grep { $field eq $_ } @myOverrides) {
			$memberInit2 .= "\t\tthis.$field = $field;\n";
		} else {
			$memberInit2 .= "\t\tthis.$field = old.$field;\n";
		}

		my $get = ($paramType{$field} eq 'boolean') ? 'is' : 'get';
		$getters .= <<END;
	/**
	 * Get the value of $field
	 * \@returns The value of $field
	 */
	public $paramType{$field} $get\u$field() {
		return $field;
	}

END
	}

	if ($class eq "IRCEvent") {
		$memberInit1 = <<END;
		this.methodName = methodName;
		this.synthLevel = 0;
		this.millis = System.currentTimeMillis();
		this.random = ((int)(Math.random()*127));
END
		$memberInit2 = <<END;
		this.methodName = old.methodName;
		this.synthLevel = old.synthLevel + 1;
		this.millis = System.currentTimeMillis();
		this.random = ((int)(Math.random()*127));
END
	}

	my $classdef = <<END;
/**
 *
 * \@author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public class $class $extends $implements
{
$members
	/**
	 * Construct a new $class
	 */
	public $class($constProto)
	{
$super1
$memberInit1
	}

	/**
	 * Synthesize a new $class from an old one.
	 */
	public $class($overrideProto)
	{
$super2
$memberInit2
	}

	/**
	 * Synthesize a new $class from this one.
	 * \@returns The new $class object.
	 */
	public IRCEvent cloneEvent($cloneProto) {
		return new $class($cloneCall);
	}

$getters
$extra
}
END
	open CLASS, ">org/uwcs/choob/support/events/$class.java";
	print CLASS $classdef;
	close CLASS
}

foreach my $interface (keys %params) {
	next if $interface eq 'IRCEvent';
	my $getters = "";
	foreach (@{$params{$interface}}) {
		my $paramType;

		if (s/\((\w+)\)//) {
			$paramType = $1;
		} else {
			$paramType = "String";
		}

		my $get = ($paramType eq 'boolean') ? 'is' : 'get';
		$getters .= <<END;
	/**
	 * Get the value of $_
	 * \@returns The value of $_
	 */
	public $paramType $get\u$_();

END
	}
	my $classdef = <<END;
/**
 *
 * \@author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public interface $interface
{
$getters
}
END

	open CLASS, ">org/uwcs/choob/support/events/$interface.java";
	print CLASS $classdef;
	close CLASS
}

sub getInherit {
	my $class = shift;
	my @stuff = ($class);
	if ($inheritance{$class}) {
		return ((map { (&getInherit($_)) } @{$inheritance{$class}}), $class);
	} else {
		return ($class);
	}
}



# IGNORE THESE EVENTS FOR NOW
#protected void onNickChange(String oldNick, String login, String hostname, String newNick)
# Handled internally in pircBot, overriding causes breakage, don't let it happen:
#protected void onFinger(String sourceNick, String sourceLogin, String sourceHostname, String target) { spinThread(new ChannelEvent(ChannelEvent.ce_Finger, new String[] {sourceNick, sourceLogin, sourceHostname, target })); }
#protected void onPing(String sourceNick, String sourceLogin, String sourceHostname, String target, String pingValue) { spinThread(new ChannelEvent(ChannelEvent.ce_Ping, new String[] {sourceNick, sourceLogin, sourceHostname, target, pingValue})); }
#protected void onServerPing(String response) { spinThread(new ChannelEvent(ChannelEvent.ce_ServerPing, new String[] {response  })); }
#protected void onServerResponse(int code, String response) { spinThread(new ChannelEvent(ChannelEvent.ce_ServerResponse, new String[] {Integer.toString(code), response  })); }
#protected void onTime(String sourceNick, String sourceLogin, String sourceHostname, String target) { spinThread(new ChannelEvent(ChannelEvent.ce_Time, new String[] {sourceNick, sourceLogin, sourceHostname, target })); }
#protected void onVersion(String sourceNick, String sourceLogin, String sourceHostname, String target) { spinThread(new ChannelEvent(ChannelEvent.ce_Version, new String[] {sourceNick, sourceLogin, sourceHostname, target })); }

# Protect against RFC breakage.
#protected void onNotice(String sourceNick, String sourceLogin, String sourceHostname, String target, String notice) { spinThread(new ChannelEvent(ChannelEvent.ce_Notice, new String[] {sourceNick, sourceLogin, sourceHostname, target, notice})); }

# Handled elsewhere in this file, for now:
#protected void onMessage(String channel, String sender, String login, String hostname, String message) { spinThread(new ChannelEvent(ChannelEvent.ce_Message, new String[] {channel, sender, login, hostname, message})); }
#protected void onPrivateMessage(String sender, String login, String hostname, String message) { spinThread(new ChannelEvent(ChannelEvent.ce_PrivateMessage, new String[] {sender, login, hostname, message })); }


# THESE EVENTS ARE REAL
__DATA__
Message(target, nick, login, hostname, message)[channel = target] ChannelMessage, Public message
PrivateMessage(nick, login, hostname, message)[target = null] PrivateMessage, Private message
Action(nick, login, hostname, target, message) Action, Action (public or private) !!!
ChannelInfo(channel, int userCount, topic) ChannelInfo, Channel information from LIST
DeVoice(channel, nick, login, hostname, target)[mode = "v", bool set = true] ChannelUserMode, Channel de-voice
Deop(channel, nick, login, hostname, target)[mode = "o", bool set = true] ChannelUserMode, Channel de-op
Invite(target, nick, login, hostname, channel) ChannelInvite, Channel invite
Join(channel, nick, login, hostname) ChannelJoin, Channel join
Kick(channel, nick, login, hostname, target, reason) ChannelKick, Channel kick
Mode(channel, nick, login, hostname, modes) ChannelModes, Channel mode (generic) !!!
NickChange(nick, login, hostname, newNick) NickChange, Server nickname change
Op(channel, nick, login, hostname, target)[mode = "o", bool set = true] ChannelUserMode, Channel op
Part(channel, nick, login, hostname) ChannelPart, Channel part
Quit(nick, login, hostname, message) QuitEvent, Server quit
RemoveChannelBan(channel, nick, login, hostname, param)[mode = "b", bool set = false] ChannelParamMode, Channel unban
RemoveChannelKey(channel, nick, login, hostname, param)[mode = "k", bool set = false] ChannelParamMode, Channel now unkeyed
RemoveChannelLimit(channel, nick, login, hostname)[mode = "l", bool set = false] ChannelMode, Channel now unlimited
RemoveInviteOnly(channel, nick, login, hostname)[mode = "i", bool set = false] ChannelMode, Channel no longer invite only
RemoveModerated(channel, nick, login, hostname)[mode = "m", bool set = false] ChannelMode, Channel no longer moderated
RemoveNoExternalMessages(channel, nick, login, hostname)[mode = "n", bool set = false] ChannelMode, Channel can receive external messages
RemovePrivate(channel, nick, login, hostname)[mode = "p", bool set = false] ChannelMode, Channel no longer private
RemoveSecret(channel, nick, login, hostname)[mode = "s", bool set = false] ChannelMode, Channel no longer secret
RemoveTopicProtection(channel, nick, login, hostname)[mode = "t", bool set = false] ChannelMode, Channel topic not settable by all
SetChannelBan(channel, nick, login, hostname, param)[mode = "b", bool set = true] ChannelParamMode, Channel ban
SetChannelKey(channel, nick, login, hostname, param)[mode = "k", bool set = true] ChannelParamMode, Channel now keyed
SetChannelLimit(channel, nick, login, hostname, int prm)[param = (String)prm, mode = "l", bool set = true] ChannelParamMode, Channel now limited
SetInviteOnly(channel, nick, login, hostname)[mode = "i", bool set = true] ChannelMode, Channel now invite only
SetModerated(channel, nick, login, hostname)[mode = "m", bool set = true] ChannelMode, Channel now moderated
SetNoExternalMessages(channel, nick, login, hostname)[mode = "n", bool set = true] ChannelMode, Channel no longer accepts external messages
SetPrivate(channel, nick, login, hostname)[mode = "p", bool set = true] ChannelMode, Channel now private
SetSecret(channel, nick, login, hostname)[mode = "s", bool set = true] ChannelMode, Channel now secret
SetTopicProtection(channel, nick, login, hostname)[mode = "t", bool set = true] ChannelMode, Channel topic now settable by all
Topic(channel, message, nick, long date, boolean changed) ChannelTopic, Channel topic (on join or change)
Unknown(line) UnknownEvent, Unknown event
UserMode(targetNick, nick, login, hostname, modes) UserModes, User mode changed
Voice(channel, nick, login, hostname, target)[mode = "v", bool set = true] ChannelUserMode, Channel voice

