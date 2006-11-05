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
	var bugs = new Array();
	
	try {
		var self = this;
		this._checkMail(function(pop3, messageID) {
			if (!(messageID in self._seenMsgs)) {
				if (!this._firstTime) {
					var bug = new BugmailParser(pop3.getMessage(messageID));
					bugs.push(bug);
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
	
	if ((bugs.length == 0) || this._firstTime) {
		this._firstTime = false;
	} else {
		this._spam(bugs);
	}
	
	this._updateFlagDB(bugs);
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
	
	var bugs = new Array();
	try {
		var self = this;
		this._checkMail(function(pop3, messageID) {
			var bug = new BugmailParser(pop3.getMessage(messageID));
			if ((bug.bugNumber == bugNum) || (bugNum == 0)) {
				bugs.push(bug);
			}
			return true;
		});
	} catch(ex) {
		irc.sendContextReply(mes, "Error checking bugmail: " + ex);
		return;
	}
	
	if (bugs.length == 0) {
		irc.sendContextReply(mes, "Nothing found in log for bug " + bugNum);
	} else {
		this._spam(bugs, mes);
	}
}
Bugzilla.prototype.commandLog.help = [
		"Shows all changes capture for a single bug.",
		"<bug number>",
		"<bug number> is the bug number to show the log of"
	];


// Command: Queue
Bugzilla.prototype.commandQueue = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 1);
	if (params.size() <= 1) {
		irc.sendContextReply(mes, "Syntax: Bugzilla.Queue <email>");
		return;
	}
	var email = String(params.get(1)).trim();
	
	var flags = this._mods.odb.retrieve(BugzillaSavedFlagRequest, "WHERE `from` = \"" + this._mods.odb.escapeString(email) + "\"");
	var list = new Array();
	
	for (var i = 0; i < flags.size(); i++) {
		var f = flags.get(i);
		list.push("bug " + f.bug + " (" + f.name + ")");
	}
	
	if (list.length == 0) {
		irc.sendContextReply(mes, "Queue is empty.");
	} else {
		irc.sendContextReply(mes, "Queue: " + list.join(", ") + ".");
	}
}
Bugzilla.prototype.commandQueue.help = [
		"Shows the review queue for a user, as seen by the bot.",
		"<email>",
		"<email> is the user to show the queue of"
	];


// Command: RebuildDB
Bugzilla.prototype.commandRebuildDB = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 0);
	if (params.size() <= 0) {
		irc.sendContextReply(mes, "Syntax: Bugzilla.RebuildDB");
		return;
	}
	
	var bugs = new Array();
	try {
		var self = this;
		this._checkMail(function(pop3, messageID) {
			var bug = new BugmailParser(pop3.getMessage(messageID));
			bugs.push(bug);
			return true;
		});
	} catch(ex) {
		irc.sendContextReply(mes, "Error checking bugmail: " + ex);
		return;
	}
	
	this._updateFlagDB(bugs);
	irc.sendContextReply(mes, "Database rebuilt from bugmail.");
}
Bugzilla.prototype.commandRebuildDB.help = [
		"Rebuilds the list of requested flags.",
		""
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
Bugzilla.prototype._spam = function(bugs, mes) {
	for (var i = 0; i < bugs.length; i++) {
		var msg = "Bug " + bugs[i].bugNumber;
		//msg += " [" + bugs[i].product + ": " + bugs[i].component + "]";
		msg += " [" + bugs[i].summary.substr(0, 20) + "]";
		msg += ": " + bugs[i].from + " ";
		
		var things = new Array();
		
		if (bugs[i].newAttachment) {
			var msgp = "added attachment " + bugs[i].newAttachment.number;
			if (bugs[i].newAttachment.label) {
				msgp += " (" + bugs[i].newAttachment.label + ")";
			}
			things.push(msgp);
		}
		
		if (bugs[i].changes.length > 0) {
			var list = new Array();
			for (var j = 0; j < bugs[i].changes.length; j++) {
				list.push(bugs[i].changes[j].name + " from '" + bugs[i].changes[j].oldValue + "' to '" + bugs[i].changes[j].newValue + "'");
			}
			things.push("changed " + list.join(", "));
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
				return flag.name + " from " + flags.get(0).by + makeFlagAttributeSuffix(bug, flag);
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
		msg += things.join("; ") + ".";
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

// Internal: updates the database of currently requested flags.
Bugzilla.prototype._updateFlagDB = function(bugs) {
	// Update internal data.
	var flags = this._mods.odb.retrieve(BugzillaSavedFlagRequest, "");
	function getFlag(bug, flag) {
		for (var i = 0; i < flags.size(); i++) {
			var f = flags.get(i);
			if ((f.bug == bug.bugNumber) && (f.name == flag.name)) {
				return f;
			}
		}
		return null;
	};
	
	for (var i = 0; i < bugs.length; i++) {
		for (var j = 0; j < bugs[i].flagsRequested.length; j++) {
			var f = getFlag(bugs[i], bugs[i].flagsRequested[j]);
			if (f) {
				f.from = bugs[i].flagsRequested[j].user;
				f.by   = bugs[i].from;
				this._mods.odb.update(f);
				log("FLAG: Bug " + f.bug + ": " + f.name + "?" + (f.from ? "(" + f.from + ")":"") + " set by " + f.by);
			} else {
				f = new BugzillaSavedFlagRequest(bugs[i].bugNumber, bugs[i].flagsRequested[j].name, bugs[i].flagsRequested[j].user, bugs[i].from);
				this._mods.odb.save(f);
				log("FLAG: Bug " + f.bug + ": " + f.name + "?" + (f.from ? "(" + f.from + ")":"") + " set by " + f.by);
			}
		}
		for (var j = 0; j < bugs[i].flagsCleared.length; j++) {
			var f = getFlag(bugs[i], bugs[i].flagsCleared[j]);
			if (f) {
				this._mods.odb["delete"](f);
				log("FLAG: Bug " + f.bug + ": " + f.name + " cleared by " + bugs[i].from);
			}
		}
		for (var j = 0; j < bugs[i].flagsGranted.length; j++) {
			var f = getFlag(bugs[i], bugs[i].flagsGranted[j]);
			if (f) {
				this._mods.odb["delete"](f);
				log("FLAG: Bug " + f.bug + ": " + f.name + " granted by " + bugs[i].from);
			}
		}
		for (var j = 0; j < bugs[i].flagsDenied.length; j++) {
			var f = getFlag(bugs[i], bugs[i].flagsDenied[j]);
			if (f) {
				this._mods.odb["delete"](f);
				log("FLAG: Bug " + f.bug + ": " + f.name + " denied by " + bugs[i].from);
			}
		}
	}
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



function BugzillaSavedFlagRequest() {
	this.id   = 0;
	this.bug  = 0;
	this.name = "";
	this.from = "";
	this.by   = "";
	
	if (arguments.length > 0) {
		this._ctor(arguments[0], arguments[1], arguments[2], arguments[3]);
	}
}

BugzillaSavedFlagRequest.prototype._ctor = function(bug, name, from, by) {
	this.bug  = bug;
	this.name = name;
	this.from = from;
	this.by   = by;
	this.init();
}

BugzillaSavedFlagRequest.prototype.init = function() {
}




function POP3Server(host, port, account, password) {
	//log("POP3 " + host + ":" + port + " BEGIN");
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
	//log("POP3 " + this._host + ":" + this._port + " END");
}



function BugmailParser(msg) {
	this.isNew = false;
	this.bugNumber = 0;
	this.summary   = "";
	this.from      = "unknown";
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
	
	this._parse(msg);
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
	var ary;
	
	var changes = new Array();
	var lineContParts = ["", "", ""];
	var linePartLengths = [19, 28, 28];
	
	for (var i = 0; i < lines.length; i++) {
		if (debug > 1) log("PARSE LINE: " + lines[i]);
		if ((ary = lines[i].match(/^Subject:\s*\[Bug (\d+)\]\s+(new:\s+)?(.*?)\s*$/i))) {
			if (debug > 0) log("BUG ID : " + ary[1] + " --- " + ary[3]);
			this.isNew = Boolean(ary[2]);
			this.bugNumber = Number(ary[1]);
			this.summary = ary[3];
			if (this.isNew) {
				if (debug > 0) log("NEW BUG!");
			}
			
		} else if ((ary = lines[i].match(/^X-Bugzilla-(Product|Component):\s*(.*?)\s*$/i))) {
			if (debug > 0) log(ary[1].toUpperCase() + ": " + ary[2]);
			this[ary[1].toLowerCase()] = ary[2];
			
		} else if ((ary = lines[i].match(/^(\S+) changed:$/))) {
			if (debug > 0) log("USER   : " + ary[1]);
			this.from = ary[1];
			
		} else if ((ary = lines[i].match(/^([^|]+)\|([^|]+)\|([^|]*)$/))) {
			var lineParts = ["", "", ""];
			for (var j = 0; j <= 2; j++) {
				lineParts[j] = ary[j + 1].trim();
			}
			if (lineParts[0] == "What")
				continue;
			
			
			var forcedWrap = [false, false, false];
			for (var j = 0; j <= 2; j++) {
				forcedWrap[j] = (lineParts[j].length == linePartLengths[j]) || (lineParts[j].substr(-1) == "-") || (lineParts[j].substr(-1) == ",");
			}
			if (forcedWrap[0] || forcedWrap[1] || forcedWrap[2] || (lineParts[0].match(/^Attachment #\d+$/))) {
				// Wrapped. Keep contents, and continue.
				for (var j = 0; j <= 2; j++) {
					lineContParts[j] += lineParts[j] + (forcedWrap[j] ? "" : " ");
				}
				continue;
			}
			for (var j = 0; j <= 2; j++) {
				lineContParts[j] += lineParts[j];
			}
			for (var j = 0; j <= 2; j++) {
				lineContParts[j] = lineContParts[j].trim();
			}
			changes.push({ name: lineContParts[0], oldValue: lineContParts[1], newValue: lineContParts[2] });
			lineContParts = ["", "", ""];
			
		} else if ((ary = lines[i].match(/^-+\s+Comment #\d+ from (\S+)\s+/))) {
			this.from = ary[1];
			
		} else if ((ary = lines[i].match(/^Created an attachment \(id=(\d+)\)$/))) {
			if (debug > 0) log("ATTACHMENT: " + ary[1]);
			this.newAttachment = { number: Number(ary[1]), label: "" };
			
			if ((i < lines.length - 2) && lines[i + 1].match(/^ --> \(/)) {
				this.newAttachment.label = lines[i + 2];
			}
			
		}
	}
	
	for (var i = 0; i < changes.length; i++) {
		var skip = false;
		var flag = false;
		var list = false;
		
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
			var oldFlags = (changes[i].oldValue ? changes[i].oldValue.split(",") : []);
			var newFlags = (changes[i].newValue ? changes[i].newValue.split(",") : []);
			
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
			if ((ary = changes[i].name.match(/Attachment #(\d+)/))) {
				for (var j = 0; j < oldFlags.length; j++) {
					oldFlags[j].attachment = Number(ary[1]);
				}
				for (var j = 0; j < newFlags.length; j++) {
					newFlags[j].attachment = Number(ary[1]);
				}
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
				this.flagsCleared.push(oldFlags[j]);
			}
			for (var j = 0; j < newFlags.length; j++) {
				if (newFlags[j].state == "?") {
					this.flagsRequested.push(newFlags[j]);
				} else if (newFlags[j].state == "+") {
					this.flagsGranted.push(newFlags[j]);
				} else if (newFlags[j].state == "-") {
					this.flagsDenied.push(newFlags[j]);
				}
			}
			
		} else if (list) {
			if (changes[i].oldValue) {
				if (debug > 0) log("REMOVED: " + changes[i].name + " --- " + changes[i].oldValue);
				this.removed.push({ name: changes[i].name, value: changes[i].oldValue });
			}
			if (changes[i].newValue) {
				if (debug > 0) log("ADDED  : " + changes[i].name + " --- " + changes[i].newValue);
				this.added.push({ name: changes[i].name, value: changes[i].newValue });
			}
		} else {
			if (debug > 0) log("CHANGED: " + changes[i].name + " --- " + changes[i].oldValue + " --- " + changes[i].newValue);
			this.changes.push({ name: changes[i].name, oldValue: changes[i].oldValue, newValue: changes[i].newValue });
		}
	}
	
	log("PARSED bugmail for bug " + this.bugNumber + " [" + this.product + ": " + this.component + "]");
}
