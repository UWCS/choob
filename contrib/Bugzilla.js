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

// Callback: bugmail-check
Bugzilla.prototype._bugmailCheckInterval = function(param, mods, irc) {
	var pop3host     = this._mods.plugin.callAPI("Options", "GetGeneralOption", ["POP3Server",   ""]);
	var pop3port     = this._mods.plugin.callAPI("Options", "GetGeneralOption", ["POP3Port",     "110"]);
	var pop3account  = this._mods.plugin.callAPI("Options", "GetGeneralOption", ["POP3Account",  ""]);
	var pop3password = this._mods.plugin.callAPI("Options", "GetGeneralOption", ["POP3Password", ""]);
	
	var bugs = new Array();
	
	var pop3 = new POP3Server(pop3host, pop3port, pop3account, pop3password);
	var mailList = pop3.getMessageList();
	for (var i = 0; i < mailList.length; i++) {
		if (!(mailList[i] in this._seenMsgs)) {
			var bug = new BugmailParser(pop3.getMessage(mailList[i]));
			this._seenMsgs[mailList[i]] = true;
			bugs.push(bug);
		}
	}
	pop3.close();
	
	this._mods.interval.callBack("bugmail-check", 30000 /* 30s */, 1);
	
	if (bugs.length == 0) {
		return;
	}
	for (var i = 0; i < bugs.length; i++) {
		var msg = "Bug " + bugs[i].bugNumber;
		//msg += " [" + bugs[i].product + ": " + bugs[i].component + "]";
		log(msg);
		msg += ": " + bugs[i].from + " ";
		
		var things = new Array();
		if (bugs[i].changes.length > 0) {
			var msgp = "changed ";
			for (var j = 0; j < bugs[i].changes.length; j++) {
				if (j > 0) {
					msgp += ", ";
				}
				msgp += bugs[i].changes[j].name + " from '" + bugs[i].changes[j].oldValue + "' to '" + bugs[i].changes[j].newValue + "'";
			}
			things.push(msgp);
		}
		
		if (bugs[i].removed.length > 0) {
			var msgp = "removed ";
			for (var j = 0; j < bugs[i].removed.length; j++) {
				if (j > 0) {
					msgp += ", ";
				}
				msgp += bugs[i].removed[j].name + " '" + bugs[i].removed[j].value + "'";
			}
			things.push(msgp);
		}
		
		if (bugs[i].added.length > 0) {
			var msgp = "added ";
			for (var j = 0; j < bugs[i].added.length; j++) {
				if (j > 0) {
					msgp += ", ";
				}
				msgp += bugs[i].added[j].name + " '" + bugs[i].added[j].value + "'";
			}
			things.push(msgp);
		}
		msg += things.join("; ") + ".";
		
		var comp = bugs[i].product + ":" + bugs[i].component;
		for (var t in this._targetList) {
			if (this._targetList[t].hasComponent(comp)) {
				irc.sendMessage(this._targetList[t].target, msg);
			}
		}
	}
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




function POP3Server(host, port, account, password) {
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
	
	this._parse(msg);
}

BugmailParser.listFields = {
	"CC": true,
	"Group": true,
	"Keywords": true,
	"BugsThisDependsOn": true,
	"OtherBugsDependingOnThis": true
};

BugmailParser.prototype._parse = function(lines) {
	var ary;
	var changeParts = ["", "", ""];
	
	for (var i = 0; i < lines.length; i++) {
		//log("PARSE LINE: " + lines[i]);
		if ((ary = lines[i].match(/^Subject:\s*(new\s+)?\[Bug (\d+)\]\s+(.*?)\s*$/i))) {
			//log("BUG ID : " + ary[2] + " --- " + ary[3]);
			this.isNew = Boolean(ary[1]);
			this.bugNumber = Number(ary[2]);
			this.summary = ary[3];
			//if (this.isNew) {
			//	log("NEW BUG!");
			//}
			
		} else if ((ary = lines[i].match(/^X-Bugzilla-(Product|Component):\s*(.*?)\s*$/i))) {
			//log(ary[1].toUpperCase() + ": " + ary[2]);
			this[ary[1].toLowerCase()] = ary[2];
			
		} else if ((ary = lines[i].match(/^(\S+) changed:$/))) {
			//log("USER   : " + ary[1]);
			this.from = ary[1];
			
		} else if ((ary = lines[i].match(/^([^|]+)\|([^|]+)\|([^|]+)$/))) {
			var parts = ["", "", ""];
			for (var j = 0; j <= 2; j++) {
				parts[j] = ary[j + 1].trim();
			}
			if (parts[0] == "What")
				continue;
			
			if ((parts[1].length == 28) || (parts[2].length == 28)) {
				// Forced wrap occured, so continue to next line.
				for (var j = 0; j <= 2; j++) {
					changeParts[j] += parts[j];
				}
				continue;
			}
			for (var j = 0; j <= 2; j++) {
				changeParts[j] += parts[j];
			}
			
			if (changeParts[0] in BugmailParser.listFields) {
				if (changeParts[1]) {
					//log("REMOVED: " + changeParts[0] + " --- " + changeParts[1]);
					this.removed.push({ name: changeParts[0], value: changeParts[1] });
				}
				if (changeParts[2]) {
					//log("ADDED  : " + changeParts[0] + " --- " + changeParts[2]);
					this.added.push({ name: changeParts[0], value: changeParts[2] });
				}
			} else {
				//log("CHANGED: " + changeParts[0] + " --- " + changeParts[1] + " --- " + changeParts[2]);
				this.changes.push({ name: changeParts[0], oldValue: changeParts[1], newValue: changeParts[2] });
			}
			
			for (var j = 0; j <= 2; j++) {
				changeParts[j] = "";
			}
		}
	}
}
