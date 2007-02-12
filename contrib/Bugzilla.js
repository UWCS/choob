// JavaScript plugin for Bugzilla reporting.
// 
// Copyright 2006, James G. Ross
// 

var BufferedReader    = Packages.java.io.BufferedReader;
var InputStreamReader = Packages.java.io.InputStreamReader;
var DataOutputStream  = Packages.java.io.DataOutputStream;
var URL               = Packages.java.net.URL;
var Socket            = Packages.java.net.Socket;


function log(msg) {
	dumpln("BUGZILLA [" + (new Date()) + "] " + msg);
}

String.prototype.trim =
function _trim() {
	return this.replace(/^\s+/, "").replace(/\s+$/, "");
}


// Constructor: Bugzilla
function Bugzilla(mods, irc) {
	this._mods = mods;
	this._irc = irc;
	this._debugChannel = "#testing42";
	
	this._targetList = new Object();
	this._seenMsgs = new Object();
	this._firstTime = true;
	this._rebuilding = false;
	
	var targets = this._mods.odb.retrieve(BugmailTarget, "");
	for (var i = 0; i < targets.size(); i++) {
		var target = targets.get(i);
		
		// Allow the feed to save itself when it makes changes.
		target.__mods = this._mods;
		target.save = function _target_save() { this.__mods.odb.update(this) };
		
		target.init(this);
		this._targetList[target.target] = target;
	}
	
	this._mods.interval.callBack("bugmail-check", 10000 /* 10s */, 1);
}


Bugzilla.prototype.info = [
		"Bugzilla bug notification plugin.",
		"James Ross",
		"silver@warwickcompsoc.co.uk",
		"$Rev$$Date$"
	];


Bugzilla.prototype.optionsGeneral = ["POP3Server", "POP3Port", "POP3Account", "POP3Password"];


// Callback for all intervals from this plugin.
Bugzilla.prototype.interval = function(param, mods, irc) {
	function mapCase(s, a, b) {
		return a.toUpperCase() + b.toLowerCase();
	};
	
	if (param) {
		var name = "_" + param.replace(/-(\w)(\w+)/g, mapCase) + "Interval";
		
		if (name in this) {
			try {
				this[name](param, mods, irc);
			} catch(ex) {
				log("Exception in " + name + ": " + ex + (ex.fileName ? " at <" + ex.fileName + ":" + ex.lineNumber + ">":""));
				irc.sendMessage(this._debugChannel, "Exception in " + name + ": " + ex + (ex.fileName ? " at <" + ex.fileName + ":" + ex.lineNumber + ">":""));
			}
		} else {
			irc.sendMessage(this._debugChannel, "Interval code missing: " + name);
		}
		
	} else {
		irc.sendMessage(this._debugChannel, "Unnamed interval attempted!");
	}
}


// Interval: bugmail-check
Bugzilla.prototype._bugmailCheckInterval = function(param, mods, irc) {
	var changesList = new Array();
	
	try {
		if (this._rebuilding) {
			throw new Error("Rebuilding, can't check mail.");
		}
		var self = this;
		this._checkMail(function(pop3, messageID) {
			if (!(messageID in self._seenMsgs)) {
				if (!self._firstTime) {
					var changes = new BugmailParser(pop3.getMessage(messageID));
					changesList.push(changes);
				}
				self._seenMsgs[messageID] = true;
			}
			return true;
		});
	} catch(ex) {
		log("Error checking bugmail: " + ex);
		this._mods.interval.callBack("bugmail-check", 30000 /* 30s */, 1);
		return;
	}
	
	this._mods.interval.callBack("bugmail-check", 30000 /* 30s */, 1);
	
	if ((changesList.length == 0) || this._firstTime) {
		this._firstTime = false;
	} else {
		this._spam(changesList);
	}
	
	this._updateActivityDB(changesList);
}


// Command: AddComponent
Bugzilla.prototype.commandAddComponent = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 2);
	if (params.size() <= 2) {
		irc.sendContextReply(mes, "Syntax: Bugzilla.AddComponent <channel> <component>");
		return;
	}
	var channel = String(params.get(1)).trim();
	
	var target = this._targetList[channel];
	if (!target) {
		target = this._targetList[channel] = new BugmailTarget(this, channel);
		this._mods.odb.save(target);
		target.__mods = this._mods;
		target.save = function _target_save() { this.__mods.odb.update(this) };
	}
	
	var component = String(params.get(2)).trim();
	
	if (target.addComponent(component)) {
		irc.sendContextReply(mes, "Channel '" + channel + "' will now receive bugmail for '" + component + "'.");
	} else {
		irc.sendContextReply(mes, "Channel '" + channel + "' already receives bugmail for '" + component + "'.");
	}
}
Bugzilla.prototype.commandAddComponent.help = [
		"Adds a new component to the list for a channel.",
		"<channel> <component>",
		"<channel> is the name of the channel to adjust",
		"<component> is the component to start sending bugmail for"
	];


// Command: RemoveComponent
Bugzilla.prototype.commandRemoveComponent = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 2);
	if (params.size() <= 2) {
		irc.sendContextReply(mes, "Syntax: Bugzilla.RemoveComponent <channel> <component>");
		return;
	}
	var channel = String(params.get(1)).trim();
	
	var target = this._targetList[channel];
	if (!target) {
		target = this._targetList[channel] = new BugmailTarget(this, channel);
		this._mods.odb.save(target);
		target.__mods = this._mods;
		target.save = function _target_save() { this.__mods.odb.update(this) };
	}
	
	var component = String(params.get(2)).trim();
	
	if (target.removeComponent(component)) {
		irc.sendContextReply(mes, "Channel '" + channel + "' will no longer receive bugmail for '" + component + "'.");
	} else {
		irc.sendContextReply(mes, "Channel '" + channel + "' doesn't receive bugmail for '" + component + "'.");
	}
}
Bugzilla.prototype.commandRemoveComponent.help = [
		"Removes a component from the list for a channel.",
		"<channel> <component>",
		"<channel> is the name of the channel to adjust",
		"<component> is the component to stop sending bugmail for"
	];


// Command: Log
Bugzilla.prototype.commandLog = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 1);
	if (params.size() <= 1) {
		irc.sendContextReply(mes, "Syntax: Bugzilla.Log <bug number>");
		return;
	}
	var bugNum = Number(params.get(1));
	
	var bugs = this._mods.odb.retrieve(BugzillaActivityGroup, "WHERE bug = " + Number(bugNum));
	
	if (bugs.length == 0) {
		irc.sendContextReply(mes, "Nothing found in log for bug " + bugNum);
		return;
	}
	
	var changesList = new Array();
	for (var i = 0; i < bugs.size(); i++) {
		var change = new Object();
		change.changeGroup = bugs.get(i);
		change.changeSet = new Array();
		var changes = this._mods.odb.retrieve(BugzillaActivity, "WHERE group = " + Number(change.changeGroup.id));
		for (var j = 0; j < changes.size(); j++) {
			change.changeSet.push(changes.get(j));
		}
		changesList.push(change);
	}
	
	this._spam(changesList, mes);
}
Bugzilla.prototype.commandLog.help = [
		"Shows all changes capture for a single bug.",
		"<bug number>",
		"<bug number> is the bug number to show the log of"
	];


// Command: Search
Bugzilla.prototype.commandSearch = function(mes, mods, irc) {
	var params = mods.util.getParams(mes);
	if (params.size() <= 1) {
		irc.sendContextReply(mes, "Syntax: Bugzilla.Search <terms>");
		return;
	}
	
	var terms = new Array();
	for (var i = 1; i < params.size(); i++) {
		terms.push({ field: "summary", word: String(params.get(i)) });
	}
	
	for (var i = 0; i < terms.length; i++) {
		terms[i] = terms[i].field + " LIKE \"%" + this._mods.odb.escapeForLike(terms[i].word) + "%\"";
	}
	var qs = "WHERE " + terms.join(" OR ") + " SORT DESC time";
	
	var bugs = this._mods.odb.retrieve(BugzillaActivityGroup, qs);
	
	if (bugs.size() == 0) {
		irc.sendContextReply(mes, "No bugs matched search terms.");
		return;
	}
	
	var results = new Array();
	var resultsHash = new Object();
	for (var i = 0; i < bugs.size(); i++) {
		var bugId = bugs.get(i).bug;
		if (!(bugId in resultsHash)) {
			results.push("bug " + bugId);
			resultsHash[bugId] = true;
		}
	}
	
	var space = 380 - 16 - results.join(", ").length;
	space = Math.floor(space / results.length) - 3;
	if (space < 10) {
		space = 0;
	}
	
	results = new Array();
	resultsHash = new Object();
	for (var i = 0; i < bugs.size(); i++) {
		var bug = bugs.get(i);
		var bugId = bug.bug;
		if (!(bugId in resultsHash)) {
			var summ = "";
			if (space > 0) {
				if (bug.summary.length > space) {
					summ = " [" + bug.summary.substr(0, space - 3) + "...]";
				} else {
					summ = " [" + bug.summary + "]";
				}
			}
			results.push("bug " + bugId + summ);
			resultsHash[bugId] = true;
		}
	}
	irc.sendContextReply(mes, "Bugs matching: " + results.join(", ") + ".");
}
Bugzilla.prototype.commandSearch.help = [
		"Searches known bugs by summary.",
		"<terms>",
		"<terms> is one or more words to look for in the summary"
	];


// Command: Queue
Bugzilla.prototype.commandQueue = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 1);
	if (params.size() <= 1) {
		irc.sendContextReply(mes, "Syntax: Bugzilla.Queue <email or name>");
		return;
	}
	var email = String(params.get(1)).trim();
	var queue = new Array();
	
	if (/\s/.test(email) || (email.indexOf("@") == -1)) {
		// Real name, not e-mail.
		var uq = "WHERE name = \"" + this._mods.odb.escapeString(email) + "\"";
		var users = this._mods.odb.retrieve(BugzillaUser, uq);
		if (users.size() > 0) {
			email = users.get(0).email;
		}
	}
	
	var q = "WITH plugins.Bugzilla.BugzillaActivityGroup AS Group"
			+ " WHERE Group.id = group"
			+ " AND attachment > 0"
			+ " AND field = \"Flag\?\""
			+ " AND newValue = \"" + this._mods.odb.escapeString(email) + "\""
	;
	var flags = this._mods.odb.retrieve(BugzillaActivity, q);
	
	for (var i = 0; i < flags.size(); i++) {
		var flag = flags.get(i);
		
		q = "WITH plugins.Bugzilla.BugzillaActivityGroup AS Group"
			+ " WHERE Group.id = group"
			+ " AND attachment = " + Number(flag.attachment)
			+ " AND oldValue = \"" + this._mods.odb.escapeString(flag.oldValue) + "\""
			+ " AND ("
				+ "field = \"Flag\+\""
				+ " OR field = \"Flag\-\""
				+ " OR field = \"Flag\""
			+ ")"
		;
		var flagRVs = this._mods.odb.retrieve(BugzillaActivity, q);
		
		if (flagRVs.size() == 0) {
			queue.push(flag);
		}
	}
	
	for (i = 0; i < queue.length; i++) {
		var event = queue[i];
		var group = this._mods.odb.retrieve(BugzillaActivityGroup, "WHERE id = " + event.group);
		group = group.get(0);
		
		queue[i] = "bug " + group.bug + " (" + event.oldValue + " attachment " + event.attachment + " for " + this._fmtUser(group.user) + ")";
	}
	
	if (queue.length == 0) {
		irc.sendContextReply(mes, "Queue for " + this._fmtUser(email) + " is empty.");
	} else {
		irc.sendContextReply(mes, "Queue for " + this._fmtUser(email) + ": " + queue.join(", ") + ".");
	}
}
Bugzilla.prototype.commandQueue.help = [
		"Shows the review queue for a user, as seen by the bot.",
		"<email or name>",
		"<email or name> is the e-mail address or name of the user to show the queue of"
	];


// Command: RebuildDB
Bugzilla.prototype.commandRebuildDB = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 0);
	if (params.size() <= 0) {
		irc.sendContextReply(mes, "Syntax: Bugzilla.RebuildDB");
		return;
	}
	
	if (this._rebuilding) {
		irc.sendContextReply(mes, "Rebuilding in progress; cannot start another.");
		return;
	}
	
	this._rebuilding = true;
	irc.sendContextReply(mes, "Removing existing data...");
	
	// Delete all BugzillaActivityGroup.
	var flags = this._mods.odb.retrieve(BugzillaActivityGroup, "");
	for (var i = 0; i < flags.size(); i++)
		this._mods.odb["delete"](flags.get(i));
	
	// Delete all BugzillaActivity.
	var flags = this._mods.odb.retrieve(BugzillaActivity, "");
	for (var i = 0; i < flags.size(); i++)
		this._mods.odb["delete"](flags.get(i));
	
	var changesList = new Array();
	try {
		var self = this;
		var shown = false;
		this._checkMail(function(pop3, messageID) {
			if (!shown) {
				irc.sendContextReply(mes, "Rebuilding database from bugmail, this may take a few minutes...");
				shown = true;
			}
			var changes = new BugmailParser(pop3.getMessage(messageID));
			changesList.push(changes);
			return true;
		});
	} catch(ex) {
		irc.sendContextReply(mes, "Error checking bugmail: " + ex);
		this._rebuilding = false;
		return;
	}
	
	this._updateActivityDB(changesList);
	irc.sendContextReply(mes, "Database rebuilt from bugmail.");
	this._rebuilding = false;
}
Bugzilla.prototype.commandRebuildDB.help = [
		"Rebuilds the activity log from currently-stored bugmails.",
		""
	];


// Command: AddUser
Bugzilla.prototype.commandAddUser = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 2);
	if (params.size() <= 2) {
		irc.sendContextReply(mes, "Syntax: Bugzilla.AddUser <email> <name>");
		return;
	}
	var email = String(params.get(1)).trim();
	
	var users = this._mods.odb.retrieve(BugzillaUser, "WHERE email = \"" + this._mods.odb.escapeString(email) + "\"");
	for (var i = 0; i < users.size(); i++) {
		this._mods.odb["delete"](users.get(i));
	}
	
	var name = String(params.get(2)).trim();
	
	var user = new BugzillaUser();
	user.email = email;
	user.name = name;
	this._mods.odb.save(user);
	
	irc.sendContextReply(mes, "User '" + email + "' (" + name + ") added.");
}
Bugzilla.prototype.commandAddUser.help = [
		"Adds a new e-mail to user mapping.",
		"<email> <name>",
		"<email> is the e-mail address to map",
		"<name> is the real name"
	];


// Command: RemoveUser
Bugzilla.prototype.commandRemoveUser = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 1);
	if (params.size() <= 1) {
		irc.sendContextReply(mes, "Syntax: Bugzilla.RemoveUser <email>");
		return;
	}
	var email = String(params.get(1)).trim();
	
	var users = this._mods.odb.retrieve(BugzillaUser, "WHERE email = \"" + this._mods.odb.escapeString(email) + "\"");
	for (var i = 0; i < users.size(); i++) {
		this._mods.odb["delete"](users.get(i));
	}
	
	if (users.size() > 0) {
		irc.sendContextReply(mes, "User '" + email + "' removed.");
	} else {
		irc.sendContextReply(mes, "User '" + email + "' didn't exist.");
	}
}
Bugzilla.prototype.commandRemoveUser.help = [
		"Removes an e-mail to user mapping.",
		"<email>",
		"<email> is the e-mail address to map"
	];


// Internal: calls back for each message.
Bugzilla.prototype._checkMail = function(callbackFn) {
	var pop3host     = this._mods.plugin.callAPI("Options", "GetGeneralOption", ["POP3Server",   ""]);
	var pop3port     = this._mods.plugin.callAPI("Options", "GetGeneralOption", ["POP3Port",     "110"]);
	var pop3account  = this._mods.plugin.callAPI("Options", "GetGeneralOption", ["POP3Account",  ""]);
	var pop3password = this._mods.plugin.callAPI("Options", "GetGeneralOption", ["POP3Password", ""]);
	
	var pop3 = new POP3Server(pop3host, pop3port, pop3account, pop3password);
	var messageIDList = pop3.getMessageList();
	for (var i = 0; i < messageIDList.length; i++) {
		if (!callbackFn(pop3, messageIDList[i])) {
			break;
		}
	}
	pop3.close();
}


// Internal: spam list of bugs at their appropriate channels.
Bugzilla.prototype._spam = function(changesList, mes) {
	var self = this;
	var odb  = this._mods.odb;
	
	for (var i = 0; i < changesList.length; i++) {
		var g = changesList[i].changeGroup;
		var s = changesList[i].changeSet;
		var things = new Array();
		var isNew = false;
		
		for (var j = 0; j < s.length; j++) {
			if (s[j].field == "NEW") {
				if (s[j].attachment == 0) {
					things.push("filed by " + this._fmtUser(g.user));
					isNew = true;
				} else {
					var msgp = "added attachment " + s[j].attachment;
					if (s[j].newValue) {
						msgp += " (" + s[j].newValue + ")";
					}
					things.push(msgp);
				}
				s[j].done = true;
			}
		}
		
		var list;
		
		function fmtField(change) {
			var str = change.field;
			if (change.attachment != 0) {
				str += " for attachment " + change.attachment;
			}
			return str;
		};
		
		function fmtFlag(flag) {
			var str = flag.oldValue;
			if (flag.field == "Flag?") {
				if (flag.newValue) {
					str += " from " + self._fmtUser(flag.newValue);
				}
			} else if ((flag.field == "Flag+") || (flag.field == "Flag-")) {
				var q = "WITH plugins.Bugzilla.BugzillaActivity AS BugzillaActivity"
						+ " WHERE BugzillaActivity.group = id"
						+ " AND bug = " + Number(g.bug)
						+ " AND BugzillaActivity.field = \"" + odb.escapeString("Flag?") + "\""
						+ " AND BugzillaActivity.attachment = " + Number(flag.attachment)
						+ " AND BugzillaActivity.oldValue = \"" + odb.escapeString(flag.oldValue) + "\""
				;
				
				var flags = odb.retrieve(BugzillaActivityGroup, q);
				log(q);
				log("CHECKING FOR FLAG: bug " + g.bug + ", " + flag.field + ", attachment " + flag.attachment + " = " + flags.size());
				
				if (flags.size() == 1) {
					str += " to " + self._fmtUser(flags.get(0).user);
				}
			}
			if (flag.attachment != 0) {
				str += " for attachment " + flag.attachment;
			}
			return str;
		};
		
		// Check for open/closed changes.
		var resolved = "";
		var reopened = "";
		var fieldMap = new Object();
		for (var j = 0; j < s.length; j++) {
			if ((s[j].field.toLowerCase() == "status") || (s[j].field.toLowerCase() == "resolution")) {
				fieldMap[s[j].field.toLowerCase()] = j;
			}
		}
		
		if (("status" in fieldMap) && ("resolution" in fieldMap)) {
			var fs = s[fieldMap["status"]];
			var fr = s[fieldMap["resolution"]];
			
			if ((fs.newValue == "RESOLVED") && fr.newValue) {
				// RESOLVED the bug, so ignore status/resolution changes.
				resolved = fr.newValue;
				s[fieldMap["status"]].done = true;
				s[fieldMap["resolution"]].done = true;
				
			} else if ((fs.oldValue == "RESOLVED") && (fs.newValue == "REOPENED") && fr.oldValue) {
				// REOPENED the bug, so ignore status/resolution changes.
				reopened = true;
				s[fieldMap["status"]].done = true;
				s[fieldMap["resolution"]].done = true;
			}
		}
		if (resolved) {
			things.push("resolved " + resolved);
		} else if (reopened) {
			things.push("reopened");
		}
		
		// Changed
		list = new Array();
		for (var j = 0; j < s.length; j++) {
			if (s[j].done) continue;
			if (/^Flag[-+?]?$/.test(s[j].field)) continue;
			if (!s[j].oldValue || !s[j].newValue) continue;
			list.push(fmtField(s[j]) + (s[j].oldValue ? " from '" + this._fmtUser(s[j].oldValue) + "'" : "") + " to '" + this._fmtUser(s[j].newValue) + "'");
			s[j].done = true;
		}
		if (list.length > 0) {
			things.push("changed " + list.join(", "));
		}
		
		// Removed
		list = new Array();
		for (var j = 0; j < s.length; j++) {
			if (s[j].done) continue;
			if (/^Flag[-+?]?$/.test(s[j].field)) continue;
			if (!s[j].oldValue || s[j].newValue) continue;
			list.push(fmtField(s[j]) + " '" + this._fmtUser(s[j].oldValue) + "'");
			s[j].done = true;
		}
		if (list.length > 0) {
			things.push("removed " + list.join(", "));
		}
		
		// Added
		list = new Array();
		for (var j = 0; j < s.length; j++) {
			if (s[j].done) continue;
			if (/^Flag[-+?]?$/.test(s[j].field)) continue;
			if (s[j].oldValue || !s[j].newValue) continue;
			list.push(fmtField(s[j]) + " '" + this._fmtUser(s[j].newValue) + "'");
			s[j].done = true;
		}
		if (list.length > 0) {
			things.push("added " + list.join(", "));
		}
		
		// Flags: cleared
		list = new Array();
		for (var j = 0; j < s.length; j++) {
			if (s[j].done) continue;
			if (s[j].field != "Flag") continue;
			list.push(fmtFlag(s[j]));
		}
		if (list.length > 0) {
			things.push("cleared " + list.join(", "));
		}
		
		// Flags: requested
		list = new Array();
		for (var j = 0; j < s.length; j++) {
			if (s[j].done) continue;
			if (s[j].field != "Flag?") continue;
			list.push(fmtFlag(s[j]));
		}
		if (list.length > 0) {
			things.push("requested " + list.join(", "));
		}
		
		// Flags: granted
		list = new Array();
		for (var j = 0; j < s.length; j++) {
			if (s[j].done) continue;
			if (s[j].field != "Flag+") continue;
			list.push(fmtFlag(s[j]));
		}
		if (list.length > 0) {
			things.push("granted " + list.join(", "));
		}
		
		// Flags: denied
		list = new Array();
		for (var j = 0; j < s.length; j++) {
			if (s[j].done) continue;
			if (s[j].field != "Flag-") continue;
			list.push(fmtFlag(s[j]));
		}
		if (list.length > 0) {
			things.push("denied " + list.join(", "));
		}
		
		if (things.length == 0) {
			continue;
		}
		
		var msg = things.join("; ") + ".";
		
		var prefix = "Bug " + g.bug;
		//prefix += " [" + g.product + ": " + g.component + "]";
		var spaceLeft = 400 - msg.length;
		
		if (isNew) {
			spaceLeft -= 4;
			if (spaceLeft < 20) spaceLeft = 20;
			prefix += " [" + g.summary.substr(0, spaceLeft) + "] ";
		} else {
			spaceLeft -= 6 + g.user.length;
			if (spaceLeft < 20) spaceLeft = 20;
			prefix += " [" + g.summary.substr(0, spaceLeft) + "]: " + this._fmtUser(g.user) + " ";
		}
		
		msg = prefix + msg;
		log(msg);
		
		if (mes) {
			this._irc.sendContextReply(mes, msg);
		} else {
			var comp = g.product + ":" + g.component;
			for (var t in this._targetList) {
				if (this._targetList[t].hasComponent(comp)) {
					this._irc.sendMessage(this._targetList[t].target, msg);
				}
			}
		}
	}
}

function _dummy2() {
	for (var i = 0; i < bugs.length; i++) {
		var things = new Array();
		
		if (bugs[i].changes.length > 0) {
			var nameMapping = new Object();
			var ignoredChanges = new Object();
			var resolved = "";
			var reopened = "";
			
			for (var j = 0; j < bugs[i].changes.length; j++) {
				if (bugs[i].changes[j].name == "Status") {
					nameMapping["status"] = j;
				}
				if (bugs[i].changes[j].name == "Resolution") {
					nameMapping["resolution"] = j;
				}
			}
			
			if (("status" in nameMapping) && ("resolution" in nameMapping)) {
				var s = bugs[i].changes[nameMapping["status"]];
				var r = bugs[i].changes[nameMapping["resolution"]];
				
				if ((s.newValue == "RESOLVED") && r.newValue) {
					// RESOLVED the bug, so ignore status/resolution changes.
					resolved = r.newValue;
					ignoredChanges[nameMapping["status"]] = true;
					ignoredChanges[nameMapping["resolution"]] = true;
					
				} else if ((s.oldValue == "RESOLVED") && (s.newValue == "REOPENED") && r.oldValue) {
					// REOPENED the bug, so ignore status/resolution changes.
					reopened = true;
					ignoredChanges[nameMapping["status"]] = true;
					ignoredChanges[nameMapping["resolution"]] = true;
				}
			}
			
			if (resolved) {
				things.push("resolved " + resolved);
			} else if (reopened) {
				things.push("reopened");
			}
			
			var list = new Array();
			for (var j = 0; j < bugs[i].changes.length; j++) {
				if ((j in ignoredChanges) && ignoredChanges[j]) {
					continue;
				}
				var oldV = bugs[i].changes[j].oldValue;
				var newV = bugs[i].changes[j].newValue;
				if (/^(Assigned To|QA Contact)$/.test(bugs[i].changes[j].name)) {
					oldV = this._fmtUser(oldV);
					newV = this._fmtUser(newV);
				}
				list.push(bugs[i].changes[j].name + (oldV ? " from '" + oldV + "'" : "") + " to '" + newV + "'");
			}
			if (list.length > 0) {
				things.push("changed " + list.join(", "));
			}
		}
		
		if (bugs[i].removed.length > 0) {
			var list = new Array();
			for (var j = 0; j < bugs[i].removed.length; j++) {
				list.push(bugs[i].removed[j].name + " '" + bugs[i].removed[j].value + "'");
			}
			things.push("removed " + list.join(", "));
		}
		
		if (bugs[i].added.length > 0) {
			var list = new Array();
			for (var j = 0; j < bugs[i].added.length; j++) {
				list.push(bugs[i].added[j].name + " '" + bugs[i].added[j].value + "'");
			}
			things.push("added " + list.join(", "));
		}
		
		var self = this;
		var odb = this._mods.odb;
		
		// Shows just the name and (possibly) what attribute it was for.
		function makeFlag(bug, flag) {
			return flag.name + makeFlagAttributeSuffix(bug, flag);
		};
		
		// Shows the same as makeFlag, but additionally includes who requested the flag - if possible.
		function makeFlagDone(bug, flag) {
			var q = "WHERE `bug` = " + Number(bug.bugNumber) + " AND `name` = \"" + odb.escapeString(flag.name) + "\"";
			//log("MAKEFLAGDONE: " + q);
			var flags = odb.retrieve(BugzillaSavedFlagRequest, q);
			//log("MAKEFLAGDONE: = " + flags.size());
			if (flags.size() == 1) {
				return flag.name + " to " + self._fmtUser(flags.get(0).by) + makeFlagAttributeSuffix(bug, flag);
			}
			return makeFlag(bug, flag);
		};
		
		// Gives the suffix for a flag to identify which attachment it is for.
		function makeFlagAttributeSuffix(bug, flag) {
			if (typeof flag.attachment == "undefined") {
				return "";
			}
			return " for attachment " + flag.attachment;
		};
		
		if (bugs[i].flagsCleared.length > 0) {
			var list = new Array();
			for (var j = 0; j < bugs[i].flagsCleared.length; j++) {
				list.push(makeFlag(bugs[i], bugs[i].flagsCleared[j]));
			}
			things.push("cleared " + list.join(", "));
		}
		
		if (bugs[i].flagsRequested.length > 0) {
			var list = new Array();
			for (var j = 0; j < bugs[i].flagsRequested.length; j++) {
				list.push(bugs[i].flagsRequested[j].name
						+ (bugs[i].flagsRequested[j].user ? " from " + bugs[i].flagsRequested[j].user : "")
						+ makeFlagAttributeSuffix(bugs[i], bugs[i].flagsRequested[j]));
			}
			things.push("requested " + list.join(", "));
		}
		
		if (bugs[i].flagsGranted.length > 0) {
			var list = new Array();
			for (var j = 0; j < bugs[i].flagsGranted.length; j++) {
				list.push(makeFlagDone(bugs[i], bugs[i].flagsGranted[j]));
			}
			things.push("granted " + list.join(", "));
		}
		
		if (bugs[i].flagsDenied.length > 0) {
			var list = new Array();
			for (var j = 0; j < bugs[i].flagsDenied.length; j++) {
				list.push(makeFlagDone(bugs[i], bugs[i].flagsDenied[j]));
			}
			things.push("denied " + list.join(", "));
		}
		
		if (things.length == 0) {
			continue;
		}
		
		var msg = things.join("; ") + ".";
		
		var prefix = "Bug " + bugs[i].bugNumber;
		//prefix += " [" + bugs[i].product + ": " + bugs[i].component + "]";
		var spaceLeft = 400 - msg.length;
		
		if (bugs[i].isNew) {
			spaceLeft -= 4;
			if (spaceLeft < 20) spaceLeft = 20;
			prefix += " [" + bugs[i].summary.substr(0, spaceLeft) + "] ";
		} else {
			spaceLeft -= 6 + bugs[i].user.length;
			if (spaceLeft < 20) spaceLeft = 20;
			prefix += " [" + bugs[i].summary.substr(0, spaceLeft) + "]: " + this._fmtUser(bugs[i].user) + " ";
		}
		
		msg = prefix + msg;
		log(msg);
		
		if (mes) {
			this._irc.sendContextReply(mes, msg);
		} else {
			var comp = bugs[i].product + ":" + bugs[i].component;
			for (var t in this._targetList) {
				if (this._targetList[t].hasComponent(comp)) {
					this._irc.sendMessage(this._targetList[t].target, msg);
				}
			}
		}
	}
}

// Internal: updates the database of changes to bugs.
Bugzilla.prototype._updateActivityDB = function(changesList) {
	var odb = this._mods.odb;
	
	function changeGroup(time, user, bug, summary, product, component) {
		var ary;
		log("ACTIVITY LOG (GROUP): '" + [time, user, bug, summary, product, component].join("', '") + "'");
		
		var q = "WHERE `time` = " + Number(time)
				+ " AND `user` = \"" + odb.escapeString(user) + "\""
				+ " AND `bug` = " + Number(bug)
			;
		
		var activityList = odb.retrieve(BugzillaActivityGroup, q);
		var activity;
		if (activityList.size() > 0) {
			activity = activityList.get(0);
			activity.summary = summary;
			activity.product = product;
			activity.component = component;
			odb.update(activity);
		} else {
			activity = new BugzillaActivityGroup(time, user, bug, summary, product, component);
			odb.save(activity);
		}
		return activity.id;
	};
	
	function change(group, field, attachment, oldValue, newValue) {
		log("ACTIVITY LOG (SET  ): '" + [group, field, attachment, oldValue, newValue].join("', '") + "'");
		
		var q = "WHERE `group` = " + Number(group)
				+ " AND `field` = \"" + odb.escapeString(field) + "\""
				+ " AND `attachment` = " + Number(attachment)
			;
		
		var activityList = odb.retrieve(BugzillaActivity, q);
		var activity;
		if (activityList.size() > 0) {
			activity = activityList.get(0);
			activity.oldValue = oldValue;
			activity.newValue = newValue;
			odb.update(activity);
		} else {
			activity = new BugzillaActivity(group, field, attachment, oldValue, newValue);
			odb.save(activity);
		}
	};
	
	for (var i = 0; i < changesList.length; i++) {
		var g = changesList[i].changeGroup;
		if (!g.user || !g.bug) continue;
		var gid = changeGroup(g.time, g.user, g.bug, g.summary, g.product, g.component);
		
		for (var j = 0; j < changesList[i].changeSet.length; j++) {
			var s = changesList[i].changeSet[j];
			change(gid, s.field, s.attachment, s.oldValue, s.newValue);
		}
	}
}

function _dummy(bugs) {
	function change(time, user, bug, summary, attachment, field, oldValue, newValue) {
		var ary;
		if (!attachment && (ary = field.match(/^Attachment #(\d+) (.*)$/))) {
			attachment = ary[1];
			field = ary[2];
		}
		log("ACTIVITY LOG: " + [time, user, bug, summary, attachment, field, oldValue, newValue].join(", "));
		
		var q = "WHERE `time` = " + Number(time)
				+ " AND `user` = \"" + odb.escapeString(user) + "\""
				+ " AND `bug` = " + Number(bug)
				+ " AND `attachment` = " + Number(attachment)
				+ " AND `field` = \"" + odb.escapeString(field) + "\""
			;
		
		var activityList = odb.retrieve(BugzillaActivity, q);
		if (activityList.size() > 0) {
			var activity = activityList.get(0);
			activity.oldValue = oldValue;
			activity.newValue = newValue;
			odb.update(activity);
		} else {
			var activity = new BugzillaActivity(time, user, bug, summary, attachment, field, oldValue, newValue);
			odb.save(activity);
		}
	};
	
	for (var i = 0; i < bugs.length; i++) {
		if (bugs[i].isNew) {
			change(bugs[i].time, bugs[i].user, bugs[i].bugNumber, bugs[i].summary, 0, "NEW", "", "");
		}
		if (bugs[i].newAttachment) {
			change(bugs[i].time, bugs[i].user, bugs[i].bugNumber, bugs[i].summary, bugs[i].newAttachment.number, "NEW", "", bugs[i].newAttachment.label || "");
		}
		for (var j = 0; j < bugs[i].changes.length; j++) {
			change(bugs[i].time, bugs[i].user, bugs[i].bugNumber, bugs[i].summary, 0, bugs[i].changes[j].field, bugs[i].changes[j].oldValue, bugs[i].changes[j].newValue);
		}
		for (var j = 0; j < bugs[i].removed.length; j++) {
			change(bugs[i].time, bugs[i].user, bugs[i].bugNumber, bugs[i].summary, 0, bugs[i].removed[j].field, bugs[i].removed[j].value, "");
		}
		for (var j = 0; j < bugs[i].added.length; j++) {
			change(bugs[i].time, bugs[i].user, bugs[i].bugNumber, bugs[i].summary, 0, bugs[i].added[j].field,   "", bugs[i].added[j].value  );
		}
		for (var j = 0; j < bugs[i].flagsCleared.length; j++) {
			change(bugs[i].time, bugs[i].user, bugs[i].bugNumber, bugs[i].summary, bugs[i].flagsCleared[j].attachment || 0,   "Flag",  bugs[i].flagsCleared[j].name,   bugs[i].flagsCleared[j].user   || "");
		}
		for (var j = 0; j < bugs[i].flagsRequested.length; j++) {
			change(bugs[i].time, bugs[i].user, bugs[i].bugNumber, bugs[i].summary, bugs[i].flagsRequested[j].attachment || 0, "Flag?", bugs[i].flagsRequested[j].name, bugs[i].flagsRequested[j].user || "");
		}
		for (var j = 0; j < bugs[i].flagsGranted.length; j++) {
			change(bugs[i].time, bugs[i].user, bugs[i].bugNumber, bugs[i].summary, bugs[i].flagsGranted[j].attachment || 0,   "Flag+", bugs[i].flagsGranted[j].name,   bugs[i].flagsGranted[j].user   || "");
		}
		for (var j = 0; j < bugs[i].flagsDenied.length; j++) {
			change(bugs[i].time, bugs[i].user, bugs[i].bugNumber, bugs[i].summary, bugs[i].flagsDenied[j].attachment || 0,    "Flag-", bugs[i].flagsDenied[j].name,    bugs[i].flagsDenied[j].user    || "");
		}
	}
}

// Internal: updates the database of currently requested flags.
Bugzilla.prototype._fmtUser = function(user) {
	var users = this._mods.odb.retrieve(BugzillaUser, "WHERE email = \"" + this._mods.odb.escapeString(user) + "\"");
	if (users.size() == 1) {
		return users.get(0).name;
	}
	return user;
}



function BugzillaUser() {
	this.id = 0;
	this.email = "";
	this.name = "";
}



function BugmailTarget() {
	this._parent = null;
	this._components = [];
	this.id = 0;
	this.componentList = "";
	this.target = "";
	
	this.save = function(){ log("ERROR: save no-op called!") };
	
	if (arguments.length > 0) {
		this._ctor(arguments[0], arguments[1]);
	}
}

BugmailTarget.prototype._ctor = function(parent, target) {
	this.target = target;
	this.init(parent);
}

BugmailTarget.prototype.init = function(parent) {
	this._parent = parent;
	if (this.componentList) {
		this._components = this.componentList.split("|");
	} else {
		this._components = [];
	}
}

BugmailTarget.prototype.addComponent = function(component) {
	for (var i = 0; i < this._components.length; i++) {
		if (this._components[i].toLowerCase() == component.toLowerCase()) {
			return false;
		}
	}
	this._components.push(component);
	this._components.sort();
	this.componentList = this._components.join("|");
	this.save();
	return true;
}

BugmailTarget.prototype.removeComponent = function(component) {
	for (var i = 0; i < this._components.length; i++) {
		if (this._components[i].toLowerCase() == component.toLowerCase()) {
			this._components.splice(i, 1);
			this.componentList = this._components.join("|");
			this.save();
			return true;
		}
	}
	return false;
}

BugmailTarget.prototype.hasComponent = function(component) {
	for (var i = 0; i < this._components.length; i++) {
		if (this._components[i].toLowerCase() == component.toLowerCase()) {
			return true;
		}
	}
	return false;
}



function BugzillaActivityGroup() {
	this.id         = 0;
	this.time       = 0;
	this.user       = "";
	this.bug        = 0;
	this.summary    = "";
	this.product    = "";
	this.component  = "";
	
	if (arguments.length > 0) {
		this._ctor(arguments[0], arguments[1], arguments[2], arguments[3], arguments[4], arguments[5]);
	}
}

BugzillaActivityGroup.prototype._ctor = function(time, user, bug, summary, product, component) {
	this.time       = time;
	this.user       = user;
	this.bug        = bug;
	this.summary    = summary;
	this.product    = product;
	this.component  = component;
	this.init();
}

BugzillaActivityGroup.prototype.init = function() {
}



function BugzillaActivity() {
	this.id         = 0;
	this.group      = 0;
	this.field      = "";
	this.attachment = 0;
	this.oldValue   = "";
	this.newValue   = "";
	
	if (arguments.length > 0) {
		this._ctor(arguments[0], arguments[1], arguments[2], arguments[3], arguments[4]);
	}
}

BugzillaActivity.prototype._ctor = function(group, field, attachment, oldValue, newValue) {
	this.group      = group;
	this.field      = field;
	this.attachment = attachment;
	this.oldValue   = oldValue;
	this.newValue   = newValue;
	this.init();
}

BugzillaActivity.prototype.init = function() {
}



function POP3Server(host, port, account, password) {
	this._host = host;
	this._port = port;
	this._socket = new Socket(host, port);
	this._outgoing = new DataOutputStream(this._socket.getOutputStream());
	this._incoming = new BufferedReader(new InputStreamReader(this._socket.getInputStream()));
	
	this.login(account, password);
}

POP3Server.prototype._getLine = function() {
	return String(this._incoming.readLine());
}

POP3Server.prototype._getLineOK = function() {
	var line = this._getLine();
	if (line.substr(0, 3) == "+OK") {
		return line;
	}
	if (line.substr(0, 4) == "-ERR") {
		throw new Error("Error returned from server: " + line.substr(4));
	}
	throw new Error("Unknown response from server: " + line);
}

POP3Server.prototype._sendLine = function(line) {
	this._outgoing.writeBytes(line + "\r\n");
}

POP3Server.prototype.login = function(account, password) {
	// Login.
	this._getLineOK(); // Welcome message.
	this._sendLine("USER " + account);
	this._getLineOK();
	this._sendLine("PASS " + password);
	this._getLineOK();
}

POP3Server.prototype.getMessageList = function() {
	this._sendLine("LIST");
	this._getLineOK();
	
	var list = new Array();
	var line = "";
	while ((line = this._getLine()) != ".") {
		list.push(line.split(/\s+/)[0]);
	}
	return list;
}

POP3Server.prototype.getMessage = function(id) {
	this._sendLine("RETR " + id);
	this._getLineOK();
	
	var msg = new Array();
	while ((line = this._getLine()) != ".") {
		msg.push(line);
	}
	return msg;
}

POP3Server.prototype.close = function() {
	this._sendLine("QUIT");
	this._socket.close();
	
	this._socket   = null;
	this._outgoing = null;
	this._incoming = null;
}



// Converts an array of lines from a single bugmail into an array of changes.
function BugmailParser(lines) {
	this.changeGroup = { time: 0, user: "", bug: 0, summary: "", product: "", component: "" };
	this.changeSet = [];
	this._parse(lines);
}

BugmailParser.fields = [
	{ name: "AssignedTo",               rename: "Assigned To" },
	{ name: "QAContact",                rename: "QA Contact" },
	{ name: "CC",                       list: true, ignore: true },
	{ name: "Group",                    list: true },
	{ name: "Keywords",                 list: true },
	{ name: "BugsThisDependsOn",        list: true, rename: "dependent bug" },
	{ name: "OtherBugsDependingOnThis", list: true, rename: "blocked bug" },
	{ name: "Flag",                     flag: true },
	{ name: /Attachment #\d+ Flag/,     flag: true },
	{ name: "Ever Confirmed",           ignore: true }
];

BugmailParser.prototype._parse = function(lines) {
	var debug = 0;
	var bodyLine = 0;
	var ary;
	
	var changes = new Array();
	var linePartLengths = [19, 28, 28];
	var changeTable = new Array();
	var haveUser = false;
	
	// Process headers.
	for (var i = 0; i < lines.length; i++) {
		if (debug > 1) log("PARSE HEAD: " + lines[i]);
		var line = lines[i].trim();
		
		if (line == "") {
			bodyLine = i + 1;
			break;
		}
		
		while ((i < lines.length - 1) && (lines[i + 1].substr(0, 1) == " ")) {
			i++;
			if (debug > 1) log("PARSE HEAD: " + lines[i]);
			line += " " + lines[i].trim();
		}
		
		if ((line.substr(0, 8) == "Subject:") && (ary = line.match(/^Subject:\s*\[Bug (\d+)\]\s+(new:\s+)?(.*?)\s*$/i))) {
			if (debug > 0) log("BUG ID    : " + ary[1] + " --- " + ary[3]);
			this.changeGroup.bug = Number(ary[1]);
			this.changeGroup.summary = ary[3];
			if (Boolean(ary[2])) {
				this.changeSet.push({ field: "NEW", attachment: 0, oldValue: "", newValue: "" });
				if (debug > 0) log("NEW BUG!");
			}
			
		} else if ((line.substr(0, 5) == "Date:") && (ary = line.match(/^Date:\s*(.*?)\s*$/i))) {
			if (debug > 0) log("DATE      : " + ary[1]);
			this.changeGroup.time = Number(new Date(ary[1]));
			
		} else if ((line.substr(0, 11) == "X-Bugzilla-") && (ary = line.match(/^X-Bugzilla-(Product|Component):\s*(.*?)\s*$/i))) {
			if (debug > 0) log((ary[1].toUpperCase() + "          ").substr(0, 10) + ": " + ary[2]);
			this.changeGroup[ary[1].toLowerCase()] = ary[2];
			
		} else if (!haveUser && (line.substr(0, 15) == "X-Bugzilla-Who:") && (ary = line.match(/^X-Bugzilla-Who:\s*(.*?)\s*$/i))) {
			if (debug > 0) log("USER      : " + ary[1]);
			this.changeGroup.user = ary[1];
			haveUser = true;
			
		}
	}
	
	// Process body.
	for (var i = bodyLine; i < lines.length; i++) {
		if (debug > 1) log("PARSE BODY: " + lines[i]);
		var line = lines[i].trim();
		
		if (!haveUser && (line.substr(0, 11) == "ReportedBy:") && (ary = line.match(/^ReportedBy:\s+(\S+)$/))) {
			if (debug > 0) log("USER      : " + ary[1]);
			this.changeGroup.user = ary[1];
			haveUser = true;
			
		} else if (!haveUser && (ary = line.match(/^(\S+) changed:$/))) {
			if (debug > 0) log("USER      : " + ary[1]);
			this.changeGroup.user = ary[1];
			haveUser = true;
			
		} else if ((ary = line.match(/Bug \d+ depends on bug \d+, which changed state./))) {
			// Crap, this bug is about changes in a dependant bug!
			break;
			
		} else if ((ary = lines[i].match(/^([^|]+)\|([^|]+)\|([^|]*)$/))) {
			var lineParts = ["", "", ""];
			if ((ary[1].length != linePartLengths[0]) || (ary[2].length != linePartLengths[1])) {
				continue;
			}
			for (var j = 0; j <= 2; j++) {
				lineParts[j] = ary[j + 1].trim();
			}
			if (lineParts[0] == "What")
				continue;
			
			changeTable.push([lineParts[0], lineParts[1], lineParts[2]]);
			
		} else if (!haveUser && (ary = lines[i].match(/^-+\s+Comment #\d+ from (\S+)\s+/))) {
			this.changeGroup.user = ary[1];
			haveUser = true;
			
			// Once we're into the comment, that's it - don't risk matching
			// anything in the comment itself.
			break;
			
		} else if ((ary = lines[i].match(/^Created an attachment \(id=(\d+)\)$/))) {
			if (debug > 0) log("ATTACHMENT: " + ary[1]);
			
			if ((i < lines.length - 2) && lines[i + 1].match(/^ --> \(/)) {
				this.changeSet.push({ field: "NEW", attachment: Number(ary[1]), oldValue: "", newValue: lines[i + 2] });
			} else {
				this.changeSet.push({ field: "NEW", attachment: Number(ary[1]), oldValue: "", newValue: "" });
			}
			
		}
	}
	
	var lineContParts = ["", "", ""];
	for (var i = 0; i < changeTable.length; i++) {
		var lineParts = changeTable[i];
		var forcedWrap = [false, false, false];
		var nonForcedWrap = false;
		
		// If any item is the entire width of that column, it is continued.
		for (var j = 0; j <= 2; j++) {
			forcedWrap[j] = (lineParts[j].length == linePartLengths[j]) || (lineParts[j].substr(-1) == "-") || (lineParts[j].substr(-1) == ",");
		}
		
		// First part of next line is empty ==> continuation.
		if ((i < changeTable.length - 1) && (changeTable[i + 1][0].length == 0)) {
			forcedWrap[0] = true;
		}
		
		// Fields with this as the first line are always 2+ lines.
		if (lineParts[0].match(/^Attachment #\d+$/)) {
			nonForcedWrap = true;
		}
		
		// If any items looks wrapped, and we're not at the end of the table, do a continuation.
		if ((nonForcedWrap || forcedWrap[0] || forcedWrap[1] || forcedWrap[2]) && (i < changeTable.length - 1)) {
			// Wrapped. Keep contents, and continue.
			for (var j = 0; j <= 2; j++) {
				lineContParts[j] += lineParts[j] + (forcedWrap[j] ? "" : " ");
			}
			continue;
		}
		
		// We're the only, or last, line of this change.
		for (var j = 0; j <= 2; j++) {
			lineContParts[j] += lineParts[j];
		}
		for (var j = 0; j <= 2; j++) {
			lineContParts[j] = lineContParts[j].trim();
		}
		
		if (debug > 0) log("CHANGE    : " + lineContParts[0] + "|" + lineContParts[1] + "|" + lineContParts[2]);
		changes.push({ name: lineContParts[0], oldValue: lineContParts[1], newValue: lineContParts[2] });
		lineContParts = ["", "", ""];
	}
	
	for (var i = 0; i < changes.length; i++) {
		var skip = false;
		var flag = false;
		var list = false;
		
		changes[i].field = changes[i].name;
		changes[i].attachment = 0;
		
		var ary;
		if ((ary = changes[i].name.match(/^Attachment #(\d+) (.*)$/))) {
			changes[i].attachment = Number(ary[1]);
			changes[i].name = ary[2];
		}
		
		for (var j = 0; j < BugmailParser.fields.length; j++) {
			var f = BugmailParser.fields[j];
			if (((typeof f.name == "string") && (changes[i].name == f.name))
					|| ((typeof f.name == "function") && (f.name instanceof RegExp) && f.name.test(changes[i].name))) {
				if (f.ignore)
					skip = true;
				if (f.list)
					list = true;
				if (f.flag)
					flag = true;
				if (f.rename)
					changes[i].name = f.rename;
				break;
			}
		}
		if (skip) {
			continue;
		}
		
		if (flag) {
			var oldFlags = (changes[i].oldValue ? changes[i].oldValue.split(/\s*,\s*/) : []);
			var newFlags = (changes[i].newValue ? changes[i].newValue.split(/\s*,\s*/) : []);
			
			for (var j = 0; j < oldFlags.length; j++) {
				var val = oldFlags[j];
				if ((ary = val.match(/^([^(]+)\((.*)\)$/))) {
					oldFlags[j] = { name: ary[1].substr(0, ary[1].length - 1), state: ary[1].substr(-1), user: ary[2] };
				} else {
					oldFlags[j] = { name:    val.substr(0,    val.length - 1), state:    val.substr(-1), user: ""     };
				}
			}
			
			for (var j = 0; j < newFlags.length; j++) {
				var val = newFlags[j];
				if ((ary = val.match(/^([^(]+)\((.*)\)$/))) {
					newFlags[j] = { name: ary[1].substr(0, ary[1].length - 1), state: ary[1].substr(-1), user: ary[2] };
				} else {
					newFlags[j] = { name:    val.substr(0,    val.length - 1), state:    val.substr(-1), user: ""     };
				}
			}
			
			// Make sure flags are linked to the attachment ID.
			for (var j = 0; j < oldFlags.length; j++) {
				oldFlags[j].attachment = changes[i].attachment;
			}
			for (var j = 0; j < newFlags.length; j++) {
				newFlags[j].attachment = changes[i].attachment;
			}
			
			// Remove old flags that are also in new flags.
			for (var j = 0; j < newFlags.length; j++) {
				for (var k = 0; k < oldFlags.length; k++) {
					if (newFlags[j].name == oldFlags[k].name) {
						oldFlags.splice(k, 1);
						break;
					}
				}
			}
			
			for (var j = 0; j < oldFlags.length; j++) {
				if (debug > 0) log("FLAG CLEARED  : " + oldFlags[j].name);
				this.changeSet.push({ field: "Flag", attachment: oldFlags[j].attachment || 0, oldValue: oldFlags[j].name, newValue: oldFlags[j].user || "" });
			}
			for (var j = 0; j < newFlags.length; j++) {
				if (newFlags[j].state == "?") {
					if (debug > 0) log("FLAG REQUESTED: " + newFlags[j].name + " --- " + newFlags[j].user);
					this.changeSet.push({ field: "Flag?", attachment: newFlags[j].attachment || 0, oldValue: newFlags[j].name, newValue: newFlags[j].user || "" });
				} else if (newFlags[j].state == "+") {
					if (debug > 0) log("FLAG GRANTED  : " + newFlags[j].name);
					this.changeSet.push({ field: "Flag+", attachment: newFlags[j].attachment || 0, oldValue: newFlags[j].name, newValue: newFlags[j].user || "" });
				} else if (newFlags[j].state == "-") {
					if (debug > 0) log("FLAG DENIED   : " + newFlags[j].name);
					this.changeSet.push({ field: "Flag-", attachment: newFlags[j].attachment || 0, oldValue: newFlags[j].name, newValue: newFlags[j].user || "" });
				}
			}
			
		} else if (list) {
			if (changes[i].oldValue) {
				if (debug > 0) log("REMOVED   : " + changes[i].name + " --- " + changes[i].oldValue);
				this.changeSet.push({ field: changes[i].name, attachment: changes[i].attachment, oldValue: changes[i].oldValue, newValue: "" });
			}
			if (changes[i].newValue) {
				if (debug > 0) log("ADDED     : " + changes[i].name + " --- " + changes[i].newValue);
				this.changeSet.push({ field: changes[i].name, attachment: changes[i].attachment, oldValue: "", newValue: changes[i].newValue });
			}
		} else {
			if (debug > 0) log("CHANGED   : " + changes[i].name + " --- " + changes[i].oldValue + " --- " + changes[i].newValue);
			this.changeSet.push({ field: changes[i].name, attachment: changes[i].attachment, oldValue: changes[i].oldValue, newValue: changes[i].newValue });
		}
	}
	
	log("PARSED bugmail for bug " + this.changeGroup.bug + " [" + this.changeGroup.product + ": " + this.changeGroup.component + "]");
}



// Converts an array of changes into nice information for display, for one change only.
function BugmailChanges(changes, timestamp) {
	this.isNew = false;
	this.time      = timestamp;
	this.user      = "unknown";
	this.bugNumber = 0;
	this.summary   = "";
	this.product   = "unknown";
	this.component = "unknown";
	this.changes   = new Array();
	this.removed   = new Array();
	this.added     = new Array();
	this.flagsCleared   = new Array();
	this.flagsRequested = new Array();
	this.flagsGranted   = new Array();
	this.flagsDenied    = new Array();
	this.newAttachment = null;
	
	this._parse(changes);
}

BugmailChanges.prototype._parse = function(changes) {
	for (var i = 0; i < changes.length; i++) {
		if (changes[i].time != this.time)
			continue;
		
		// Each change has:
		//   time, user, bug, summary, attachment, field, oldValue, newValue
		
		this.user = changes[i].user;
		this.bugNumber = changes[i].bug;
		this.summary = changes[i].summary;
		
		if (changes[i].field == "NEW") {
			if (changes[i].attachment == 0) {
				this.isNew = true;
			} else {
				this.newAttachment = { number: changes[i].attachment, label: changes[i].newValue };
			}
		} else {
		}
	}
}
