// JavaScript plugin for BugzillaBugmail bugmail reporting.
// 
// Copyright 2006, 2007, 2008, 2009, 2011, James G. Ross
// 

var BufferedReader    = Packages.java.io.BufferedReader;
var BufferedWriter    = Packages.java.io.BufferedWriter;
var InputStreamReader = Packages.java.io.InputStreamReader;
var DataOutputStream  = Packages.java.io.DataOutputStream;
var File              = Packages.java.io.File;
var FileReader        = Packages.java.io.FileReader;
var FileWriter        = Packages.java.io.FileWriter;
var URL               = Packages.java.net.URL;
var Socket            = Packages.java.net.Socket;
var GetContentsCached = Packages.uk.co.uwcs.choob.support.GetContentsCached;


function log(msg) {
	var date = String(new Date());
	date = date.substr(4, 20);
	dumpln("BugzillaBugmail [" + date + "] " + msg);
}

function sleep(ms) {
	try {
		Packages.java.lang.Thread.sleep(ms);
	} catch(ex) {
		log("Exception in sleep: " + ex);
	}
}

String.prototype.trim =
function _trim() {
	return this.replace(/^\s+/, "").replace(/\s+$/, "");
}

Array.prototype.indexOf =
function _indexof(item) {
	for (var i = 0; i < this.length; i++) {
		if (this[i] == item) return i;
	}
	return -1;
}


// Constructor: BugzillaBugmail
function BugzillaBugmail(mods, irc) {
	this._mods = mods;
	this._irc = irc;
	
	this._debugChannel     = "";
	this._debugSpew        = "";
	this._mailStore        = "C:\\Users\\James\\Documents\\Mozilla.org\\IRC Bot\\data\\bugzillabugmail\\";
	// Debug: 0 => nothing, 1 => changes, 2 => parsed info, 3 => e-mail source, 4 => raw lines.
	BugmailParser.debug    = 0;
	this._targetList       = new Object();
	this._seenFileNames    = new Object();
	this._rebuilding       = false;
	this._lineLengthLimit  = 390;
	this._updateSpeed      = 60 * 1000; // 60 seconds
	this._updateMax        = 1;
	
	var targets = this._mods.odb.retrieve(BugmailTarget, "");
	for (var i = 0; i < targets.size(); i++) {
		var target = targets.get(i);
		
		// Allow the feed to save itself when it makes changes.
		target.__mods = this._mods;
		target.save = function _target_save() { this.__mods.odb.update(this) };
		
		target.init(this);
		this._targetList[target.target] = target;
	}
	
	var bugmails = this._mods.odb.retrieve(BugzillaActivityGroup, "");
	for (var i = 0; i < bugmails.size(); i++) {
		this._seenFileNames[bugmails.get(i).fileName()] = true;
	}
	
	this._mods.interval.callBack("bugmail-check", 10000 /* 10s */, 1);
}


BugzillaBugmail.prototype.info = [
		"BugzillaBugmail bug notification plugin.",
		"James Ross",
		"silver@warwickcompsoc.co.uk",
		"$Rev$$Date$"
	];


BugzillaBugmail.prototype.optionsGeneral = ["POP3Server", "POP3Port", "POP3Account", "POP3Password"];


// Callback for all intervals from this plugin.
BugzillaBugmail.prototype.interval = function(param, mods, irc) {
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
				if (this._debugChannel) irc.sendMessage(this._debugChannel, "Exception in " + name + ": " + ex + (ex.fileName ? " at <" + ex.fileName + ":" + ex.lineNumber + ">":""));
			}
		} else {
			if (this._debugChannel) irc.sendMessage(this._debugChannel, "Interval code missing: " + name);
		}
		
	} else {
		if (this._debugChannel) irc.sendMessage(this._debugChannel, "Unnamed interval attempted!");
	}
}


// Interval: bugmail-check
BugzillaBugmail.prototype._bugmailCheckInterval = function(param, mods, irc) {
	var bugmailList = new Array();
	
	if (this._rebuilding) {
		throw new Error("Rebuilding, can't check mail.");
	}
	
	var count = 0;
	var store = new File(this._mailStore);
	var files = store.listFiles().sort();
	for (var i = 0; i < files.length; i++) {
		if (!files[i].getName().endsWith(".eml")) continue;
		if (files[i].getName() in this._seenFileNames) continue;
		
		try {
			var changes = this._processMessageFile(files[i]);
		} catch(ex) {
			log("Exception parsing bugmail: " + ex);
			break;
		}
		
		var q = "WHERE `msgid` = \"" + this._mods.odb.escapeString(changes.changeGroup.msgid) + "\"";
		var bugs = this._mods.odb.retrieve(BugzillaActivityGroup, q);
		if (bugs.size() > 0) {
			this._seenFileNames[changes.changeGroup.fileName()] = true;
			continue;
		}
		
		bugmailList.push(changes);
		this._seenFileNames[changes.changeGroup.fileName()] = true;
		count++;
		if (count >= this._updateMax) break;
	}
	if (count > 0) {
		log("Bugmail Check: " + count + " old e-mails parsed.");
	}
	
	if (count == 0) {
		var self = this;
		this._checkMail(function(pop3, messageNumber, messageId) {
			try {
				var changes = self._downloadMessage(pop3, messageNumber, messageId);
				
				var q = "WHERE `msgid` = \"" + self._mods.odb.escapeString(changes.changeGroup.msgid) + "\"";
				var bugs = self._mods.odb.retrieve(BugzillaActivityGroup, q);
				if (bugs.size() > 0) {
					self._seenFileNames[changes.changeGroup.fileName()] = true;
					return true;
				}
				
				bugmailList.push(changes);
				self._seenFileNames[changes.changeGroup.fileName()] = true;
			} catch(ex) {
				log("Exception downloading bugmail: " + ex);
				return false;
			}
			count++;
			if (count >= self._updateMax) return false;
			return true;
		});
		if (count > 0) {
			log("Bugmail Check: " + count + " new e-mails downloaded and parsed.");
		}
	}
	
	this._mods.interval.callBack("bugmail-check", this._updateSpeed, 1);
	this._spam(bugmailList);
	this._updateActivityDB(bugmailList);
}


// Command: ListComponents
BugzillaBugmail.prototype.commandListComponents = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 1);
	if (params.size() <= 1) {
		irc.sendContextReply(mes, "Syntax: BugzillaBugmail.ListComponents <channel>");
		return;
	}
	var channel = String(params.get(1)).trim();
	
	var components = new Array();
	
	var target = this._targetList[channel];
	if (target) {
		components = target.listComponents();
	}
	
	if (components.length == 0) {
		irc.sendContextReply(mes, "Channel '" + channel + "' doesn't receive any bugmail.");
	} else {
		irc.sendContextReply(mes, "Channel '" + channel + "' receives bugmail for '" + components.join("', '") + "'.");
	}
}
BugzillaBugmail.prototype.commandListComponents.help = [
		"Lists the components for a channel.",
		"<channel>",
		"<channel> is the name of the channel to list"
	];


// Command: AddComponent
BugzillaBugmail.prototype.commandAddComponent = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 2);
	if (params.size() <= 2) {
		irc.sendContextReply(mes, "Syntax: BugzillaBugmail.AddComponent <channel> <component>");
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
BugzillaBugmail.prototype.commandAddComponent.help = [
		"Adds a new component to the list for a channel.",
		"<channel> <component>",
		"<channel> is the name of the channel to adjust",
		"<component> is the component to start sending bugmail for"
	];


// Command: RemoveComponent
BugzillaBugmail.prototype.commandRemoveComponent = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 2);
	if (params.size() <= 2) {
		irc.sendContextReply(mes, "Syntax: BugzillaBugmail.RemoveComponent <channel> <component>");
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
BugzillaBugmail.prototype.commandRemoveComponent.help = [
		"Removes a component from the list for a channel.",
		"<channel> <component>",
		"<channel> is the name of the channel to adjust",
		"<component> is the component to stop sending bugmail for"
	];


// Command: Log
BugzillaBugmail.prototype.commandLog = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 1);
	if (params.size() <= 1) {
		irc.sendContextReply(mes, "Syntax: BugzillaBugmail.Log <bug number>");
		return;
	}
	var bugNum = Number(params.get(1));
	
	var bugs = this._mods.odb.retrieve(BugzillaActivityGroup, "WHERE bug = " + Number(bugNum) + " SORT ASC time");
	
	if (bugs.size() == 0) {
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
BugzillaBugmail.prototype.commandLog.help = [
		"Shows all changes capture for a single bug.",
		"<bug number>",
		"<bug number> is the bug number to show the log of"
	];


// Command: Search
BugzillaBugmail.prototype.commandSearch = function(mes, mods, irc) {
	var syntax = "Syntax: BugzillaBugmail.Search [<channel>] [-open || -closed || -enh || -bug] [-recent[=<period>]] [<terms>]";
	var params = mods.util.getParams(mes);
	if (params.size() <= 1) {
		irc.sendContextReply(mes, syntax);
		return;
	}
	
	var channel = String(mes.getContext());
	if (channel.substr(0, 1) != "#") {
		channel = "";
	}
	
	var paramMap = {
		"open":   { field: "bug_status",   items: ["UNCONFIRMED", "NEW", "ASSIGNED", "REOPENED"] },
		"closed": { field: "bug_status",   items: ["RESOLVED", "VERIFIED", "CLOSED"] },
		"enh":    { field: "bug_severity", items: ["enhancement"] },
		"bug":    { field: "bug_severity", items: ["blocker", "critical", "major", "normal", "minor", "trivial"] },
	};
	
	var search = {
		component: [],
		bug_status: [],
		bug_severity: [],
		short_desc: []
	};
	search.short_desc.joinString = " ";
	
	var gotArgument = false;
	for (var i = 1; i < params.size(); i++) {
		var param = params.get(i);
		if (param.substr(0, 1) == "#") {
			channel = param;
		} else if (param == "-recent") {
			search.chfieldfrom = "1w";
			search.chfieldto = "Now";
			gotArgument = true;
		} else if (param.substr(0, 8) == "-recent=") {
			search.chfieldfrom = param.substr(8);
			search.chfieldto = "Now";
			gotArgument = true;
		} else if (param.substr(0, 1) == "-") {
			if (param.substr(1) in paramMap) {
				var options = paramMap[param.substr(1)];
				for (var j = 0; j < options.items.length; j++) {
					search[options.field].push(options.items[j]);
				}
			} else {
				irc.sendContextReply(mes, syntax);
				return;
			}
		} else {
			search.short_desc.push(param);
			gotArgument = true;
		}
	}
	if (!channel || !gotArgument) {
		irc.sendContextReply(mes, syntax);
		return;
	}
	if (search.bug_status.length == 0) {
		for (var i = 0; i < paramMap["open"].items.length; i++) {
			search.bug_status.push(paramMap["open"].items[i]);
		}
	}
	if (channel in this._targetList) {
		var comps = this._targetList[channel].listComponents();
		for (var i = 0; i < comps.length; i++) {
			var procomp = comps[i];
			if (procomp.indexOf(":") != -1) {
				procomp = procomp.substr(procomp.indexOf(":") + 1);
			}
			search.component.push(procomp);
		}
	}
	
	var url = "https://bugzilla.mozilla.org/buglist.cgi?query_format=advanced&short_desc_type=allwordssubstr";
	for (var field in search) {
		if (search[field] instanceof Array) {
			if (search[field].joinString) {
				url += "&" + field + "=" + escape(search[field].join(search[field].joinString));
			} else {
				for (var i = 0; i < search[field].length; i++) {
					url += "&" + field + "=" + escape(search[field][i]);
				}
			}
		} else {
			url += "&" + field + "=" + escape(search[field]);
		}
	}
	url += "&cmdtype=doit&ctype=rdf&order=Last+Changed";
	
	var urlObj = new URL(url);
	var cachedContents = new GetContentsCached(urlObj, 60000);
	var bugListXML = String(cachedContents.getContents());
	
	var XMLNS_RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
	var XMLNS_BUGZILLA = "http://www.bugzilla.org/rdf#";
	
	try {
		var xmlParser = new XMLParser(bugListXML);
	} catch(ex) {
		irc.sendContextReply(mes, "XML Parser: " + ex);
		return;
	}
	var rootElement = xmlParser.rootElement;
	var bugsArray = new Array();
	var more = false;
	try {
		if (!rootElement.is("RDF", XMLNS_RDF)) {
			throw new Error("Root is not RDF");
		}
		
		var result = rootElement.childByName("result", XMLNS_BUGZILLA);
		if (!result) {
			throw new Error("Root doesn't contain <bz:result>");
		}
		
		var bugs = result.childByName("bugs", XMLNS_BUGZILLA);
		if (!bugs) {
			throw new Error("<bz:result> doesn't contain <bz:bugs>");
		}
		
		var seq = bugs.childByName("Seq", XMLNS_RDF);
		if (!seq) {
			throw new Error("<bz:bugs> doesn't contain <rdf:Seq>");
		}
		
		var lis = seq.childrenByName("li", XMLNS_RDF);
		for (var i = 0; i < lis.length; i++) {
			var bug = lis[i].childByName("bug", XMLNS_BUGZILLA);
			if (!bug) {
				throw new Error("<rdf:li> doesn't contain <bz:bug>");
			}
			
			var id = bug.childByName("id", XMLNS_BUGZILLA);
			if (!id) {
				throw new Error("<bz:bug> doesn't contain <bz:id>");
			}
			id = id.contents();
			
			var summary = bug.childByName("short_desc", XMLNS_BUGZILLA);
			if (!summary) {
				throw new Error("<bz:bug> doesn't contain <bz:short_desc>");
			}
			summary = _decodeEntities(summary.contents());
			
			if (bugsArray.length >= 20) {
				more = true;
				break;
			}
			bugsArray.push({ bug: id, summary: summary });
		}
	} catch(ex) {
		irc.sendContextReply(mes, "Processing: " + ex);
	}
	
	if (bugsArray.length == 0) {
		irc.sendContextReply(mes, "No bugs matched.");
	} else {
		irc.sendContextReply(mes, "Bugs matching: " + this.apiFormatBugList(bugsArray, 360) + (more ? ", and more..." : "."));
	}
}
BugzillaBugmail.prototype.commandSearch.help = [
		"Searches known bugs by summary.",
		"[<channel>] [-open || -closed || -enh || -bug] [-recent[=<period>]] [<terms>]",
		"<channel> specifies which channel to act as; required for private queries",
		"-closed,-open,-enh,-bug select bug statuses/types (defaults to open enh+bugs)",
		"-recent limits the bugs to those changed recently (default of 1 week)",
		"<period> sets the recent period (e.g. '3d', '1w')",
		"<terms> is one or more words to look for in the summary"
	];


// Command: Queue
BugzillaBugmail.prototype.commandQueue = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 1);
	if (params.size() <= 1) {
		irc.sendContextReply(mes, "Syntax: BugzillaBugmail.Queue <email or name>");
		return;
	}
	var email = String(params.get(1)).trim();
	var queue = new Array();
	
	if (/\s/.test(email) || (email.indexOf("@") == -1)) {
		// Real name, not e-mail.
		var uq = "WHERE name = \"" + this._mods.odb.escapeString(email) + "\"";
		//log("commandQueue: " + uq);
		var users = this._mods.odb.retrieve(BugzillaUser, uq);
		//log("commandQueue: " + users.size());
		if (users.size() > 0) {
			email = users.get(0).email;
		}
	}
	
	var q = "WITH plugins.BugzillaBugmail.BugzillaActivityGroup AS Group"
			+ " WHERE Group.id = group"
			+ " AND attachment > 0"
			+ " AND field = \"Flag\?\""
			+ " AND newValue = \"" + this._mods.odb.escapeString(email) + "\""
	;
	//log("commandQueue: " + q);
	var flags = this._mods.odb.retrieve(BugzillaActivity, q);
	//log("commandQueue: " + flags.size());
	
	for (var i = 0; i < flags.size(); i++) {
		var flag = flags.get(i);
		
		q = "WITH plugins.BugzillaBugmail.BugzillaActivityGroup AS Group"
			+ " WHERE Group.id = group"
			+ " AND attachment = " + Number(flag.attachment)
			+ " AND oldValue = \"" + this._mods.odb.escapeString(flag.oldValue) + "\""
			+ " AND ("
				+ "field = \"Flag\+\""
				+ " OR field = \"Flag\-\""
				+ " OR field = \"Flag\""
			+ ")"
		;
		//log("commandQueue: " + q);
		var flagRVs = this._mods.odb.retrieve(BugzillaActivity, q);
		//log("commandQueue: " + flagRVs.size());
		
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
BugzillaBugmail.prototype.commandQueue.help = [
		"Shows the review queue for a user, as seen by the bot.",
		"<email or name>",
		"<email or name> is the e-mail address or name of the user to show the queue of"
	];


// Command: RebuildDB
BugzillaBugmail.prototype.commandRebuildDB = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 1);
	if (params.size() <= 0) {
		irc.sendContextReply(mes, "Syntax: BugzillaBugmail.RebuildDB");
		return;
	}
	
	if (this._rebuilding) {
		irc.sendContextReply(mes, "Rebuilding in progress; cannot start another.");
		return;
	}
	
	var cmd = "";
	if (params.size() > 1) {
		cmd = String(params.get(1)).trim();
	}
	
	this._rebuilding = true;
	
	if (!cmd || (cmd == "clear")) {
		irc.sendContextReply(mes, "Removing existing data...");
		
		// Delete all BugzillaActivityGroup.
		var flags = this._mods.odb.retrieve(BugzillaActivityGroup, "");
		for (var i = 0; i < flags.size(); i++)
			this._mods.odb["delete"](flags.get(i));
		
		// Delete all BugzillaActivity.
		var flags = this._mods.odb.retrieve(BugzillaActivity, "");
		for (var i = 0; i < flags.size(); i++)
			this._mods.odb["delete"](flags.get(i));
		
		this._seenFileNames = new Object();
		
		irc.sendContextReply(mes, "Removed existing data.");
	}
	
	if (!cmd || (cmd == "build") || (cmd == "build-once")) {
		var bugmailList = new Array();
		try {
			var currentFile = "";
			var store = new File(this._mailStore);
			var files = store.listFiles().sort();
			for (var i = 0; i < files.length; i++) {
				if (!files[i].getName().endsWith(".eml")) continue;
				if (files[i].getName() in this._seenFileNames) continue;
				currentFile = files[i].getName();
				
				var changes = this._processMessageFile(files[i]);
				
				var q = "WHERE `msgid` = \"" + this._mods.odb.escapeString(changes.changeGroup.msgid) + "\"";
				var bugs = this._mods.odb.retrieve(BugzillaActivityGroup, q);
				if (bugs.size() > 0) {
					this._seenFileNames[changes.changeGroup.fileName()] = true;
					continue;
				}
				
				bugmailList.push(changes);
				this._seenFileNames[changes.changeGroup.fileName()] = true;
				currentFile = "";
				if (cmd == "build-once") break;
			}
		} catch(ex) {
			irc.sendContextReply(mes, "Error rebuilding from bugmail: " + ex + (currentFile ? " <" + currentFile + ">" : ""));
			this._rebuilding = false;
			return;
		}
		
		this._updateActivityDB(bugmailList);
		if (cmd == "build-once") {
			irc.sendContextReply(mes, "Updated database from one bugmail." + (bugmailList.length > 0 ? " (" + bugmailList[0].changeGroup.fileName() + ")" : ""));
		} else {
			irc.sendContextReply(mes, "Rebuilt database from bugmail.");
		}
	}
	
	this._rebuilding = false;
}
BugzillaBugmail.prototype.commandRebuildDB.help = [
		"Rebuilds the activity log from currently-stored bugmails.",
		"[<command>]",
		"<command> can be 'clear' (to empty database) or 'build' (to fill database)"
	];


// Command: AddUser
BugzillaBugmail.prototype.commandAddUser = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 2);
	if (params.size() <= 2) {
		irc.sendContextReply(mes, "Syntax: BugzillaBugmail.AddUser <email> <name>");
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
BugzillaBugmail.prototype.commandAddUser.help = [
		"Adds a new e-mail to user mapping.",
		"<email> <name>",
		"<email> is the e-mail address to map",
		"<name> is the real name"
	];


// Command: RemoveUser
BugzillaBugmail.prototype.commandRemoveUser = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 1);
	if (params.size() <= 1) {
		irc.sendContextReply(mes, "Syntax: BugzillaBugmail.RemoveUser <email>");
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
BugzillaBugmail.prototype.commandRemoveUser.help = [
		"Removes an e-mail to user mapping.",
		"<email>",
		"<email> is the e-mail address to map"
	];


// API: GetBugSummary
BugzillaBugmail.prototype.apiGetBugSummary = function(bugNumber) {
	var qs = "WHERE bug = " + Number(bugNumber) + " SORT DESC time";
	var bugs = this._mods.odb.retrieve(BugzillaActivityGroup, qs);
	if (bugs.size() > 0) {
		return bugs.get(0).summary;
	}
	return "";
}


// API: FormatBugList
BugzillaBugmail.prototype.apiFormatBugList = function(bugs, space) {
	var results = new Array();
	var resultsHash = new Object();
	for (var i = 0; i < bugs.length; i++) {
		var bugId = bugs[i].bug;
		if (!(bugId in resultsHash)) {
			results.push("bug " + bugId);
			resultsHash[bugId] = true;
		}
	}
	
	space -= results.join(", ").length;
	space = Math.floor(space / results.length) - 13;
	if (space < 10) {
		space = 0;
	}
	
	results = new Array();
	resultsHash = new Object();
	for (var i = 0; i < bugs.length; i++) {
		var bug = bugs[i];
		var bugId = bug.bug;
		if (!(bugId in resultsHash)) {
			var summ = "";
			if (space > 0) {
				if (bug.summary.length > space) {
					summ = " [" + bug.summary.substr(0, space - 2) + "»]";
				} else {
					summ = " [" + bug.summary + "]";
				}
			}
			results.push("bug " + bugId + summ);
			resultsHash[bugId] = true;
		}
	}
	return results.join(", ");
}


// Internal: calls back for each message.
BugzillaBugmail.prototype._checkMail = function(callbackFn) {
	var pop3host     = this._mods.plugin.callAPI("Options", "GetGeneralOption", ["POP3Server",   ""]);
	var pop3port     = this._mods.plugin.callAPI("Options", "GetGeneralOption", ["POP3Port",     "110"]);
	var pop3account  = this._mods.plugin.callAPI("Options", "GetGeneralOption", ["POP3Account",  ""]);
	var pop3password = this._mods.plugin.callAPI("Options", "GetGeneralOption", ["POP3Password", ""]);
	
	try {
		var pop3 = new POP3Server(pop3host, pop3port, pop3account, pop3password);
		var messageList = pop3.getMessageIdList();
		for (var i = 0; i < messageList.length; i++) {
			if (!callbackFn(pop3, messageList[i].number, messageList[i].id)) {
				break;
			}
		}
		pop3.close();
	} catch(ex) {}
}


// Internal: downloads and parses a single message.
BugzillaBugmail.prototype._downloadMessage = function(pop3, messageNumber, messageId) {
	// Get the message.
	var lines = pop3.getMessage(messageNumber);
	
	// Parse the message into changes.
	var changes = new BugmailParser(lines.concat());
	
	// Write out the message to a file.
	var messageFile = new File(this._mailStore + changes.changeGroup.fileName());
	var messageFileOutput = new BufferedWriter(new FileWriter(messageFile));
	for (var i = 0; i < lines.length; i++) {
		messageFileOutput.write(lines[i] + "\n");
	}
	messageFileOutput.close();
	
	log("DWNLD " + messageId + " --> " + changes.changeGroup.fileName());
	log("PARSE " + changes.changeGroup.fileName() + " --> [" + new Date(changes.changeGroup.time) + "] Bug " + changes.changeGroup.bug + " [" + changes.changeGroup.product + ": " + changes.changeGroup.component + "] (format " + changes.bugmailFormat + ")");
	
	pop3.deleteMessage(messageNumber);
	
	return changes;
}


// Internal: process a single file as a message.
BugzillaBugmail.prototype._processMessageFile = function(messageFile) {
	// Write out the message to a file.
	var line;
	var lines = new Array();
	var messageFileOutput = new BufferedReader(new FileReader(messageFile));
	while ((line = messageFileOutput.readLine()) != null) {
		lines.push(String(line));
	}
	messageFileOutput.close();
	
	// Parse the message into changes.
	var changes = new BugmailParser(lines);
	
	log("PARSE " + changes.changeGroup.fileName() + " --> [" + new Date(changes.changeGroup.time) + "] Bug " + changes.changeGroup.bug + " [" + changes.changeGroup.product + ": " + changes.changeGroup.component + "] (format " + changes.bugmailFormat + ")");
	
	return changes;
}


// Internal: spam list of bugs at their appropriate channels.
BugzillaBugmail.prototype._spam = function(changesList, mes) {
	var self = this;
	var odb  = this._mods.odb;
	
	for (var i = 0; i < changesList.length; i++) {
		var g = changesList[i].changeGroup;
		var s = changesList[i].changeSet;
		var things = new Array();
		var isNew = false;
		var comment = "";
		var productComponent = g.product + ":" + g.component;
		
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
				
			} else if (s[j].field == "COMMENT") {
				comment = s[j].newValue;
				s[j].done = true;
				
			}
		}
		
		// Some fields are ignored for spamming of new bugs, to keep the line shorter.
		if (isNew) {
			var ignoreOnNew = ["Summary", "Assigned To", "QA Contact", "Priority"];
			for (var j = 0; j < s.length; j++) {
				for (var k = 0; k < ignoreOnNew.length; k++) {
					if (s[j].field == ignoreOnNew[k]) {
						s[j].done = true;
						break;
					}
				}
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
				var q = "WITH plugins.BugzillaBugmail.BugzillaActivity AS BugzillaActivity"
						+ " WHERE BugzillaActivity.group = id"
						+ " AND bug = " + Number(g.bug)
						+ " AND BugzillaActivity.field = \"" + odb.escapeString("Flag?") + "\""
						+ " AND BugzillaActivity.attachment = " + Number(flag.attachment)
						+ " AND BugzillaActivity.oldValue = \"" + odb.escapeString(flag.oldValue) + "\""
				;
				
				try {
					//log("fmtFlag: " + q);
					var flags = odb.retrieve(BugzillaActivityGroup, q);
					//log("fmtFlag: " + flags.size());
					//log("CHECKING FOR FLAG: bug " + g.bug + ", " + flag.field + ", attachment " + flag.attachment + " = " + flags.size());
					
					if (flags.size() == 1) {
						str += " to " + self._fmtUser(flags.get(0).user);
					}
				} catch(ex) {}
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
				
			} else if ((fs.oldValue == "RESOLVED") && ((fs.newValue == "UNCONFIRMED") || (fs.newValue == "REOPENED")) && fr.oldValue) {
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
			if (s[j].done || /^Flag[-+?]?$/.test(s[j].field) || !s[j].oldValue || !s[j].newValue) continue;
			list.push(fmtField(s[j]) + (s[j].oldValue ? " from '" + this._fmtUser(s[j].oldValue) + "'" : "") + " to '" + this._fmtUser(s[j].newValue) + "'");
			s[j].done = true;
		}
		if (list.length > 0) {
			things.push("changed " + list.join(", "));
		}
		
		// Removed
		list = new Array();
		for (var j = 0; j < s.length; j++) {
			if (s[j].done || /^Flag[-+?]?$/.test(s[j].field) || !s[j].oldValue || s[j].newValue) continue;
			list.push(fmtField(s[j]) + " '" + this._fmtUser(s[j].oldValue) + "'");
			s[j].done = true;
		}
		if (list.length > 0) {
			things.push("removed " + list.join(", "));
		}
		
		// Added
		list = new Array();
		for (var j = 0; j < s.length; j++) {
			if (s[j].done || /^Flag[-+?]?$/.test(s[j].field) || s[j].oldValue || !s[j].newValue) continue;
			list.push(fmtField(s[j]) + " '" + this._fmtUser(s[j].newValue) + "'");
			s[j].done = true;
		}
		if (list.length > 0) {
			if (isNew) {
				things.push(list.join(", "));
			} else {
				things.push("added " + list.join(", "));
			}
		}
		
		// Flags: cleared
		list = new Array();
		for (var j = 0; j < s.length; j++) {
			if (s[j].done || (s[j].field != "Flag")) continue;
			list.push(fmtFlag(s[j]));
		}
		if (list.length > 0) {
			things.push("cleared " + list.join(", "));
		}
		
		// Flags: requested
		list = new Array();
		for (var j = 0; j < s.length; j++) {
			if (s[j].done || (s[j].field != "Flag?")) continue;
			list.push(fmtFlag(s[j]));
		}
		if (list.length > 0) {
			things.push("requested " + list.join(", "));
		}
		
		// Flags: granted
		list = new Array();
		for (var j = 0; j < s.length; j++) {
			if (s[j].done || (s[j].field != "Flag+")) continue;
			list.push(fmtFlag(s[j]));
		}
		if (list.length > 0) {
			things.push("granted " + list.join(", "));
		}
		
		// Flags: denied
		list = new Array();
		for (var j = 0; j < s.length; j++) {
			if (s[j].done || (s[j].field != "Flag-")) continue;
			list.push(fmtFlag(s[j]));
		}
		if (list.length > 0) {
			things.push("denied " + list.join(", "));
		}
		
		if (comment) {
			things.push("commented: \"" + comment + "\"");
		}
		
		if (things.length == 0) {
			continue;
		}
		
		var msg = things.join("; ") + ".";
		
		var prefix = "Bug " + g.bug;
		//prefix += " [" + g.product + ": " + g.component + "]";
		var spaceLeft = this._lineLengthLimit - prefix.length - msg.length;
		
		if (isNew) {
			spaceLeft -= 4;
			if (spaceLeft < 20) spaceLeft = 20;
			prefix += " [" + g.summary.substr(0, spaceLeft) + "] ";
		} else {
			spaceLeft -= 6 + this._fmtUser(g.user).length;
			if (spaceLeft < 20) spaceLeft = 20;
			prefix += " [" + g.summary.substr(0, spaceLeft) + "]: " + this._fmtUser(g.user) + " ";
		}
		
		msg = prefix + msg;
		log("SPAM  " + msg);
		
		if (msg.length > this._lineLengthLimit) {
			msg = msg.substr(0, this._lineLengthLimit - 5) + (comment ? "»\"." : "»");
		}
		
		if (mes) {
			this._irc.sendContextReply(mes, msg);
		} else if (this._debugSpew) {
			if (this._debugSpew != "-") {
				this._irc.sendMessage(this._debugSpew, msg);
			}
		} else {
			for (var t in this._targetList) {
				if (this._targetList[t].hasComponent(productComponent)) {
					this._irc.sendMessage(this._targetList[t].target, msg);
				}
			}
		}
	}
}

var entityMap = {
	"lt":      "<", "#60":     "<",
	"gt":      ">", "#62":     ">",
	"quot":    '"', "#34":     '"',
	"ldquo":   '"', "#8220":   '"',
	"rdquo":   '"', "#8221":   '"',
	"apos":    "'", "#39":     "'",
	"lsquo":   "'", "#8216":   "'",
	"rsquo":   "'", "#8217":   "'",
	"nbsp":    " ", "#160":    " ",
	"ndash":   "-", "#8211":   "-",
	"mdash":   "-", "#8212":   "-",
	"lsaquo": "<<", "#8249":  "<<",
	"rsaquo": ">>", "#8250":  ">>",
	"times":   "x",
	"#163":    "£",
	"#8230":   "...",
	"dummy":   ""
};

function _decodeEntities(data) {
	// Decode XML into HTML...
	data = data.replace(/&(?:(\w+)|#(\d+)|#x([0-9a-f]{2}));/gi,
	function _decodeEntity(match, name, decnum, hexnum) {
		if (name && (name in entityMap)) {
			return entityMap[name];
		}
		if (decnum && (String("#" + parseInt(decnum, 10)) in entityMap)) {
			return entityMap[String("#" + parseInt(decnum, 10))];
		}
		if (hexnum && (String("#" + parseInt(hexnum, 16)) in entityMap)) {
			return entityMap[String("#" + parseInt(hexnum, 16))];
		}
		return match; //"[unknown entity '" + (name || decnum || hexnum) + "']";
	});
	
	// Done as a special-case, last, so that it doesn't bugger up
	// doubly-escaped things.
	data = data.replace(/&(amp|#0*38|#x0*26);/g, "&");
	
	return data;
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
			//log("makeFlagDone: " + q);
			var flags = odb.retrieve(BugzillaSavedFlagRequest, q);
			//log("makeFlagDone: " + flags.size());
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
		var spaceLeft = this._lineLengthLimit - msg.length;
		
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
BugzillaBugmail.prototype._updateActivityDB = function(changesList) {
	var odb = this._mods.odb;
	
	function changeGroup(activity) {
		log("ACTIVITY LOG (GROUP): '" + [activity.msgid, activity.time, activity.user, activity.bug, activity.summary, activity.product, activity.component].join("', '") + "'");
		odb.save(activity);
		return activity.id;
	};
	
	function change(activity) {
		log("ACTIVITY LOG (SET  ): '" + [activity.group, activity.field, activity.attachment, activity.oldValue, activity.newValue].join("', '") + "'");
		odb.save(activity);
	};
	
	for (var i = 0; i < changesList.length; i++) {
		if (!changesList[i].changeGroup.user || !changesList[i].changeGroup.bug) continue;
		var gid = changeGroup(changesList[i].changeGroup);
		
		for (var j = 0; j < changesList[i].changeSet.length; j++) {
			changesList[i].changeSet[j].group = gid;
			change(changesList[i].changeSet[j]);
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
BugzillaBugmail.prototype._fmtUser = function(user) {
	if (String(user).indexOf("@") == -1) {
		return user;
	}
	var q = "WHERE email = \"" + this._mods.odb.escapeString(user) + "\"";
	//log("_fmtUser: " + q);
	var users = this._mods.odb.retrieve(BugzillaUser, q);
	//log("_fmtUser: " + users.size());
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

BugmailTarget.prototype.listComponents = function() {
	return this._components;
}



function BugzillaActivityGroup() {
	this.id         = 0;
	this.msgid      = "";
	this.time       = 0;
	this.user       = "";
	this.bug        = 0;
	this.summary    = "";
	this.product    = "";
	this.component  = "";
	
	if (arguments.length > 0) {
		this._ctor(arguments[0], arguments[1], arguments[2], arguments[3], arguments[4], arguments[5], arguments[6]);
	}
}

BugzillaActivityGroup.prototype._ctor = function(msgid, time, user, bug, summary, product, component) {
	this.msgid      = String(msgid);
	this.time       = Number(time);
	this.user       = String(user);
	this.bug        = Number(bug);
	this.summary    = String(summary);
	this.product    = String(product);
	this.component  = String(component);
	this.init();
}

BugzillaActivityGroup.prototype.init = function() {
}

BugzillaActivityGroup.prototype.fileName = function() {
	return this.time + "-" + this.msgid.replace(/[^0-9a-z\.@]/gi, "_") + ".eml";
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
	this.group      = Number(group);
	this.field      = String(field);
	this.attachment = Number(attachment);
	this.oldValue   = String(oldValue);
	this.newValue   = String(newValue);
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

POP3Server.prototype.getMessageIdList = function() {
	this._sendLine("UIDL");
	this._getLineOK();
	
	var list = new Array();
	var line = "";
	while ((line = this._getLine()) != ".") {
		list.push({ number: line.split(/\s+/)[0], id: line.split(/\s+/)[1] });
	}
	return list;
}

POP3Server.prototype.getMessageHeaders = function(id) {
	this._sendLine("TOP " + id + " 0");
	this._getLineOK();
	
	var msg = new Array();
	while ((line = this._getLine()) != ".") {
		msg.push(line.replace(/^\./, ""));
	}
	return msg;
}

POP3Server.prototype.getMessage = function(id) {
	this._sendLine("RETR " + id);
	this._getLineOK();
	
	var msg = new Array();
	while ((line = this._getLine()) != ".") {
		msg.push(line.replace(/^\./, ""));
	}
	return msg;
}

POP3Server.prototype.deleteMessage = function(id) {
	this._sendLine("DELE " + id);
	this._getLineOK();
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
	this.bugmailFormat = -1;
	this.changeGroup = new BugzillaActivityGroup();
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
	{ name: "Depends on",               list: true, rename: "dependent bug" },
	{ name: "OtherBugsDependingOnThis", list: true, rename: "blocked bug" },
	{ name: "Blocks",                   list: true, rename: "blocked bug" },
	{ name: "Flag",                     flag: true },
	{ name: "Flags",                    flag: true },
	{ name: "ReportedBy",               ignore: true },
	{ name: "Ever Confirmed",           ignore: true }
];

BugmailParser.wrappedFieldNames = [
	{ name: /Attachment #\d+/ }
];

BugmailParser.debug = 0;

BugmailParser.prototype._parse = function(lines) {
	var debug = BugmailParser.debug;
	var ary;
	
	function _quote_printable_escape(match, code) {
		return String.fromCharCode(eval("0x" + code));
	}
	
	function _header_charset_encoding(match, charset, type, text) {
		log("_charset_encoding: '" + [charset, type, text].join("', '") + "'.");
		if (type == "Q") {
			text = text.replace(/=([0-9A-F][0-9A-F])/g, _quote_printable_escape);
		}
		if (charset.toLowerCase() == "utf-8") {
			// Output is actually a UTF-8 stream anyway, so don't convert to Unicode.
		}
		return text;
	};
	
	function _tidy_up_comment(comment) {
		comment = comment.replace(/ +/g, " ").trim();
		comment = comment.replace(/^\(From update of attachment (\d+)\)\s*/, " ");
		comment = comment.replace(/\(In reply to comment #(\d+)\)\s*/, " ");
		comment = comment.replace(/ +/g, " ").trim();
		return comment;
	};
	
	if (debug > 0) log("");
	if (debug > 3) {
		log("RAW MESSAGE:");
		for (var i = 0; i < lines.length; i++) {
			log("    " + lines[i]);
		}
		log("");
	}
	
	var headers = new Array();
	for (var i = 0; i < lines.length; i++) {
		if (lines[i] == "") {
			lines.splice(0, i + 1);
			break;
		}
		if ((lines[i].substr(0, 1) == " ") || (lines[i].substr(0, 1) == "\t")) {
			if (lines[i].trim().substr(0, 2) == "=?") {
				headers[headers.length - 1] += lines[i].trim();
			} else {
				headers[headers.length - 1] += " " + lines[i].trim();
			}
		} else {
			headers.push(lines[i].trim());
		}
	}
	
	for (var i = 0; i < headers.length; i++) {
		headers[i] = headers[i].replace(/\u201c|u201d/g, '"');
		headers[i] = headers[i].replace(/=\?([-a-zA-Z0-9]+)\?([BQ])\?(.*?)\?=/g, _header_charset_encoding);
	}
	
	var haveUser = false;
	var useQuotePrintable = false;
	var linePartLengths = [19, 28, 28];
	var changes = new Array();
	var changeTable = new Array();
	var newBug = false;
	var hasFields = false;
	
	if (debug > 2) {
		log("MESSAGE HEADERS:");
		for (var i = 0; i < headers.length; i++) {
			log("    " + headers[i]);
		}
		log("");
	}
	
	var bugmailFormats = [
		"date from message-id received return-path subject to x-bugzilla-component x-bugzilla-product x-bugzilla-reason",
		"content-type date from message-id received return-path subject to x-bugzilla-assigned-to x-bugzilla-changed-fields x-bugzilla-component x-bugzilla-keywords x-bugzilla-priority x-bugzilla-product x-bugzilla-reason x-bugzilla-severity x-bugzilla-status x-bugzilla-target-milestone x-bugzilla-type x-bugzilla-watch-reason x-bugzilla-who",
		"content-type date from message-id mime-version received return-path subject to x-bugzilla-assigned-to x-bugzilla-changed-fields x-bugzilla-component x-bugzilla-keywords x-bugzilla-priority x-bugzilla-product x-bugzilla-reason x-bugzilla-severity x-bugzilla-status x-bugzilla-target-milestone x-bugzilla-type x-bugzilla-watch-reason x-bugzilla-who",
		"content-type date from message-id mime-version received return-path subject to x-authentication-warning x-bugzilla-assigned-to x-bugzilla-changed-fields x-bugzilla-component x-bugzilla-keywords x-bugzilla-priority x-bugzilla-product x-bugzilla-reason x-bugzilla-severity x-bugzilla-status x-bugzilla-target-milestone x-bugzilla-type x-bugzilla-watch-reason x-bugzilla-who",
		"content-type date from message-id mime-version received return-path subject to x-bugzilla-assigned-to x-bugzilla-changed-fields x-bugzilla-component x-bugzilla-keywords x-bugzilla-priority x-bugzilla-product x-bugzilla-reason x-bugzilla-severity x-bugzilla-status x-bugzilla-target-milestone x-bugzilla-type x-bugzilla-watch-reason x-bugzilla-who",
		"content-type date from message-id mime-version received return-path subject to x-bugzilla-assigned-to x-bugzilla-changed-fields x-bugzilla-classification x-bugzilla-component x-bugzilla-keywords x-bugzilla-priority x-bugzilla-product x-bugzilla-reason x-bugzilla-severity x-bugzilla-status x-bugzilla-target-milestone x-bugzilla-type x-bugzilla-watch-reason x-bugzilla-who",
		"auto-submitted content-type date from message-id mime-version received return-path subject to x-bugzilla-assigned-to x-bugzilla-changed-fields x-bugzilla-classification x-bugzilla-component x-bugzilla-keywords x-bugzilla-priority x-bugzilla-product x-bugzilla-reason x-bugzilla-severity x-bugzilla-status x-bugzilla-target-milestone x-bugzilla-type x-bugzilla-watch-reason x-bugzilla-who",
		"auto-submitted content-type date from message-id mime-version received return-path subject to x-bugzilla-assigned-to x-bugzilla-changed-fields x-bugzilla-classification x-bugzilla-component x-bugzilla-keywords x-bugzilla-priority x-bugzilla-product x-bugzilla-reason x-bugzilla-severity x-bugzilla-status x-bugzilla-target-milestone x-bugzilla-type x-bugzilla-url x-bugzilla-watch-reason x-bugzilla-who",
	];
	var bugmailNewWrapFormat = 5;
	
	var headerHash = new Object();
	for (var i = 0; i < headers.length; i++) {
		ary = headers[i].split(":");
		if (ary[0].toLowerCase() == "content-transfer-encoding") continue;
		if (ary[0].toLowerCase() == "delivered-to") continue;
		if (ary[0].toLowerCase() == "delivery-date") continue;
		if (ary[0].toLowerCase() == "envelope-to") continue;
		if (ary[0].toLowerCase() == "in-reply-to") continue;
		if (ary[0].toLowerCase() == "references") continue;
		if (ary[0].toLowerCase() == "x-originalarrivaltime") continue;
		if (ary[0].toLowerCase() == "x-virus-scanned") continue;
		headerHash[ary[0].toLowerCase()] = true;
	}
	var headerList = new Array();
	for (var p in headerHash) {
		headerList.push(p);
	}
	headerList = headerList.sort().join(" ");
	if (debug > 1) {
		log("HEADERS   : " + headerList);
	}
	this.bugmailFormat = bugmailFormats.indexOf(headerList);
	if (this.bugmailFormat == -1) {
		log("HEADERS   : " + headerList);
		throw new Error("Bugmail format is unknown!");
	}
	if (this.bugmailFormat >= bugmailNewWrapFormat) {
		linePartLengths = [19, 27, 27];
	}
	
	for (var i = 0; i < headers.length; i++) {
		var header = headers[i]
		var headerLC = header.toLowerCase();
		
		if ((headerLC.substr(0, 11) == "message-id:") && (ary = header.match(/^Message-Id:\s*<(.*?)>\s*$/i))) {
			if (debug > 1) log("MSGID     : " + ary[1]);
			this.changeGroup.msgid = ary[1];
			
		} else if ((headerLC.substr(0, 8) == "subject:") && (ary = header.match(/^Subject:\s*\[Bug (\d+)\]\s+(new:\s+)?(.*?)\s*$/i))) {
			if (debug > 1) log("BUG       : " + ary[1] + " --- " + ary[3]);
			this.changeGroup.bug = Number(ary[1]);
			this.changeGroup.summary = ary[3];
			if (Boolean(ary[2])) {
				this.changeSet.push(new BugzillaActivity(0, "NEW", 0, "", ""));
				newBug = true;
				if (debug > 1) log("NEW BUG!");
			}
			
		} else if ((headerLC.substr(0, 5) == "date:") && (ary = header.match(/^Date:\s*(.*?)\s*$/i))) {
			if (debug > 1) log("DATE      : " + ary[1]);
			this.changeGroup.time = Number(new Date(ary[1]));
			
		} else if ((headerLC.substr(0, 11) == "x-bugzilla-") && (ary = header.match(/^X-Bugzilla-(Product|Component):\s*(.*?)\s*$/i))) {
			if (debug > 1) log((ary[1].toUpperCase() + "          ").substr(0, 10) + ": " + ary[2]);
			this.changeGroup[ary[1].toLowerCase()] = ary[2];
			
		} else if (!haveUser && (headerLC.substr(0, 15) == "x-bugzilla-who:") && (ary = header.match(/^X-Bugzilla-Who:\s*(\S+)\s*$/i))) {
			this.changeGroup.user = ary[1];
			if (debug > 1) log("USER      : " + this.changeGroup.user);
			haveUser = true;
			
		} else if ((headerLC.substr(0, 26) == "content-transfer-encoding:") && (ary = header.match(/^Content-Transfer-Encoding:\s+quoted-printable\s*$/i))) {
			if (debug > 1) log("CTE       : quoted-printable");
			useQuotePrintable = true;
			
		}
	}
	
	if (!this.changeGroup.msgid) {
		throw new Error("Message doesn't have a Message-Id!");
	}
	
	if (useQuotePrintable) {
		if (debug > 1) log("QUOTE PRINTABLE");
		
		var bufferLines = lines;
		lines = new Array();
		
		var lineBuffer = "";
		for (var i = 0; i < bufferLines.length; i++) {
			var softWrap = (bufferLines[i][bufferLines[i].length - 1] == "=");
			
			lineBuffer += bufferLines[i].replace(/=([0-9A-F][0-9A-F])/g, _quote_printable_escape);
			if (softWrap) {
				lineBuffer = lineBuffer.substr(0, lineBuffer.length - 1);
			} else {
				lines.push(lineBuffer);
				lineBuffer = "";
			}
		}
		if (lineBuffer) {
			lines.push(lineBuffer);
			lineBuffer = "";
		}
		bufferLines = null;
	}
	
	if (debug > 2) {
		log("");
		log("MESSAGE BODY:");
		for (var i = 0; i < lines.length; i++) {
			log("    " + lines[i]);
		}
		log("");
	}
	
	for (var i = 0; i < lines.length; i++) {
		var line = lines[i].replace(/\u201c|u201d/g, '"');
		
		if (!haveUser && (line.substr(0, 11) == "ReportedBy:") && (ary = line.match(/^ReportedBy:\s+(\S+)$/))) {
			this.changeGroup.user = ary[1];
			if (debug > 1) log("USER      : " + this.changeGroup.user);
			haveUser = true;
			
		} else if (!haveUser && (ary = line.match(/(?:^(\S+)|\s<(\S+)>) changed:$/))) {
			this.changeGroup.user = ary[1] || ary[2];
			if (debug > 1) log("USER      : " + this.changeGroup.user);
			haveUser = true;
			
		} else if ((ary = line.match(/Bug \d+ depends on bug \d+, which changed state./))) {
			// Crap, this bug is about changes in a dependant bug!
			if (!haveUser) {
				this.changeGroup.user = "fake-user-bugzillabugmail@example.com";
				if (debug > 1) log("USER      : " + this.changeGroup.user);
			}
			break;
			
		} else if ((ary = lines[i].match(/^([^|]+)\|([^|]+)\|([^|]*)$/))) {
			var lineParts = ["", "", ""];
			if (this.bugmailFormat >= bugmailNewWrapFormat) {
				ary[2] = ary[2].replace(/ $/, "");
			}
			if ((ary[1].length != linePartLengths[0]) || (ary[2].length != linePartLengths[1])) {
				continue;
			}
			for (var j = 0; j <= 2; j++) {
				lineParts[j] = ary[j + 1].trim();
			}
			if (lineParts[0] == "What") {
				continue;
			}
			
			changeTable.push([lineParts[0], lineParts[1], lineParts[2]]);
			
		} else if ((lines[i].substr(0, 4) == "    ") && (lines[i].substr(18, 2) == ": ") && (ary = lines[i].match(/^\s*(\S+):\s*(.*)\s*$/))) {
			var value = ary[2];
			while ((i < lines.length - 1) && (lines[i + 1].substr(0, 20) == "                    ")) {
				i++;
				if (!value.match(/[-.,]$/)) value += " ";
				value += lines[i].trim();
			}
			changes.push({ name: ary[1].trim(), oldValue: "", newValue: value });
			
			if (!haveUser && (ary[1].trim() == "ReportedBy")) {
				this.changeGroup.user = ary[2];
				if (debug > 1) log("USER      : " + this.changeGroup.user);
				haveUser = true;
			}
			if (newBug && !hasFields && (i < lines.length - 1) && (lines[i + 1] == "")) {
				hasFields = true;
			}
			
		} else if (ary = lines[i].match(/^-+\s+Comment #\d+ from (\S+)\s+/)) {
			if (!haveUser && (ary[1].indexOf("@") != -1)) {
				this.changeGroup.user = ary[1];
				if (debug > 1) log("USER      : " + this.changeGroup.user);
				haveUser = true;
			}
			
			var comment = "";
			for (var j = i + 1; j < lines.length; j++) {
				if (lines[j] == "-- ") break;
				if (lines[j].match(/^(Created an attachment \(id=\d+\)|Created attachment \d+|Comment on attachment \d+)$/)) {
					j += 2;
					continue;
				}
				
				if (lines[j][0] == ">") continue;
				comment += " " + lines[j].trim();
			}
			comment = _tidy_up_comment(comment);
			
			this.changeSet.push(new BugzillaActivity(0, "COMMENT", 0, "", comment));
			if (debug > 1) log("COMMENT   : " + comment);
			
		} else if ((ary = lines[i].match(/^Created an attachment \(id=(\d+)\)$/)) || (ary = lines[i].match(/^Created attachment (\d+)$/))) {
			if (debug > 1) log("ATTACHMENT: " + ary[1]);
			
			if ((i < lines.length - 2) && lines[i + 1].match(/^ --> \(/)) {
				this.changeSet.push(new BugzillaActivity(0, "NEW", Number(ary[1]), "", lines[i + 2]));
			} else {
				this.changeSet.push(new BugzillaActivity(0, "NEW", Number(ary[1]), "", ""));
			}
			
			i += 2;
			
			if (haveUser && newBug) {
				var comment = "";
				for (var j = i + 1; j < lines.length; j++) {
					if (lines[j] == "-- ") break;
					
					if (lines[j][0] == ">") continue;
					comment += " " + lines[j].trim();
				}
				comment = _tidy_up_comment(comment);
				
				this.changeSet.push(new BugzillaActivity(0, "COMMENT", 0, "", comment));
				if (debug > 1) log("COMMENT   : " + comment);
				break;
			}
			
		} else if (haveUser && newBug && hasFields && lines[i].match(/^[^\s]/)) {
			var comment = "";
			for (var j = i; j < lines.length; j++) {
				if (lines[j] == "-- ") break;
				
				if (lines[j][0] == ">") continue;
				comment += " " + lines[j].trim();
			}
			comment = _tidy_up_comment(comment);
			
			this.changeSet.push(new BugzillaActivity(0, "COMMENT", 0, "", comment));
			if (debug > 1) log("COMMENT   : " + comment);
			break;
		}
	}
	
	var lineTableParts = ["", "", ""];
	var lineTableNames = new Array();
	var blank = "                                                                                ";
	for (var i = 0; i < changeTable.length; i++) {
		for (var j = 0; j < 3; j++) {
			lineTableParts[j] += changeTable[i][j];
			if ((changeTable[i][j].length > 0) && (changeTable[i][j].length < linePartLengths[j]) && !changeTable[i][j].match(/[-.,]$/)) {
				lineTableParts[j] += " ";
			}
		}
		
		// If we've got a repeated name, ignore it.
		if (changeTable[i][0] && (changeTable[i][0].indexOf("Attachment #") == -1)) {
			lineTableNames.push(changeTable[i][0]);
		}
		if (i < changeTable.length - 1) {
			for (var j = 0; j < lineTableNames.length; j++) {
				if (changeTable[i + 1][0] == lineTableNames[j]) {
					changeTable[i + 1][0] = "";
				}
			}
		}
		
		var wrapped = (changeTable[i][0].length == linePartLengths[0]);
		for (var j = 0; j < BugmailParser.wrappedFieldNames.length; j++) {
			if ((BugmailParser.wrappedFieldNames[j].name == changeTable[i][0]) || changeTable[i][0].match(BugmailParser.wrappedFieldNames[j].name)) {
				wrapped = true;
			}
		}
		
		if (((i < changeTable.length - 1) && (changeTable[i + 1][0] != "") && !wrapped) || (i == changeTable.length - 1)) {
			for (var j = 0; j < 3; j++) {
				lineTableParts[j] = lineTableParts[j].trim();
			}
			if (debug > 1) log("CHANGE    : "
					+ (lineTableParts[0] + blank).substr(0, 50) + "  "
					+ (lineTableParts[1] + blank).substr(0, 50) + "  "
					+ (lineTableParts[2] + blank).substr(0, 50));
			changes.push({ name: lineTableParts[0], oldValue: lineTableParts[1], newValue: lineTableParts[2] });
			lineTableParts = ["", "", ""];
			lineTableNames = new Array();
		}
	}
	if (debug > 1) log("");
	
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
				this.changeSet.push(new BugzillaActivity(0, "Flag", oldFlags[j].attachment || 0, oldFlags[j].name, oldFlags[j].user || ""));
			}
			for (var j = 0; j < newFlags.length; j++) {
				if (newFlags[j].state == "?") {
					if (debug > 0) log("FLAG REQUESTED: " + newFlags[j].name + " --- " + newFlags[j].user);
					this.changeSet.push(new BugzillaActivity(0, "Flag?", newFlags[j].attachment || 0, newFlags[j].name, newFlags[j].user || ""));
				} else if (newFlags[j].state == "+") {
					if (debug > 0) log("FLAG GRANTED  : " + newFlags[j].name);
					this.changeSet.push(new BugzillaActivity(0, "Flag+", newFlags[j].attachment || 0, newFlags[j].name, newFlags[j].user || ""));
				} else if (newFlags[j].state == "-") {
					if (debug > 0) log("FLAG DENIED   : " + newFlags[j].name);
					this.changeSet.push(new BugzillaActivity(0, "Flag-", newFlags[j].attachment || 0, newFlags[j].name, newFlags[j].user || ""));
				}
			}
			
		} else if (list) {
			if (changes[i].oldValue) {
				if (debug > 0) log("REMOVED       : " + changes[i].name + " --- " + changes[i].oldValue);
				this.changeSet.push(new BugzillaActivity(0, changes[i].name, changes[i].attachment, changes[i].oldValue, ""));
			}
			if (changes[i].newValue) {
				if (debug > 0) log("ADDED         : " + changes[i].name + " --- " + changes[i].newValue);
				this.changeSet.push(new BugzillaActivity(0, changes[i].name, changes[i].attachment, "", changes[i].newValue));
			}
		} else {
			if (debug > 0) log("CHANGED       : " + changes[i].name + " --- " + changes[i].oldValue + " --- " + changes[i].newValue);
			this.changeSet.push(new BugzillaActivity(0, changes[i].name, changes[i].attachment, changes[i].oldValue, changes[i].newValue));
		}
	}
}



// Converts an array of changes into nice information for display, for one change only.
function BugmailChanges(changes, timestamp) {
	this.isNew = false;
	this.time      = Number(timestamp);
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



// #include JavaScriptXML.jsi
// General XML parser, win!
function XMLParser(data) {
	this.data = data;
	this.root = [];
	this._state = [];
	this._parse();
}

XMLParser.prototype._dumpln = function(line, limit) {
	limit.value--;
	if (limit.value == 0) {
		dumpln("*** TRUNCATED ***");
	}
	if (limit.value <= 0) {
		return;
	}
	dumpln(line);
}

XMLParser.prototype._dump = function(list, indent, limit) {
	for (var i = 0; i < list.length; i++) {
		this._dumpElement(list[i], indent, limit);
	}
}

XMLParser.prototype._dumpElement = function(elt, indent, limit) {
	if (elt._content) {
		this._dumpln(indent + elt + elt._content + "</" + elt.name + ">", limit);
	} else if (elt._children && (elt._children.length > 0)) {
		this._dumpln(indent + elt, limit);
		this._dump(elt._children, indent + "  ", limit);
		this._dumpln(indent + "</" + elt.name + ">", limit);
	} else {
		this._dumpln(indent + elt, limit);
	}
}

XMLParser.prototype._parse = function() {
	// Hack off the Unicode DOM if it exists.
	if (this.data.substr(0, 3) == "\xEF\xBB\xBF") {
		this.data = this.data.substr(3);
	}
	
	// Process all entities here.
	this._processEntities();
	
	// Head off for the <?xml PI.
	while (this.data.length > 0) {
		this._eatWhitespace();
		
		if (this.data.substr(0, 4) == "<!--") {
			// Comment.
			this.root.push(this._eatComment());
			
		} else if (this.data.substr(0, 2) == "<!") {
			// SGML element.
			this.root.push(this._eatSGMLElement());
			
		} else if (this.data.substr(0, 2) == "<?") {
			var e = this._eatElement(null);
			if (e.name != "xml") {
				throw new Error("Expected <?xml?>, found <?" + e.name + "?>");
			}
			this.xmlPI = e;
			this.root.push(e);
			break;
			
		} else {
			break;
			//throw new Error("Expected <?xml?>, found " + this.data.substr(0, 10) + "...");
		}
	}
	
	// OK, onto the root element...
	while (this.data.length > 0) {
		this._eatWhitespace();
		
		if (this.data.substr(0, 4) == "<!--") {
			// Comment.
			this.root.push(this._eatComment());
			
		} else if (this.data.substr(0, 2) == "<!") {
			// SGML element.
			this.root.push(this._eatSGMLElement());
			
		} else if (this.data.substr(0, 2) == "<?") {
			var e = this._eatElement(null);
			this.root.push(e);
			
		} else if (this.data.substr(0, 1) == "<") {
			var e = this._eatElement(null);
			if (e.start == false) {
				throw new Error("Expected start element, found end element");
			}
			this.rootElement = e;
			this.root.push(e);
			this._state.unshift(e);
			break;
			
		} else {
			throw new Error("Expected root element, found " + this.data.substr(0, 10) + "...");
		}
	}
	
	// Now the contents.
	while (this.data.length > 0) {
		this._eatWhitespace();
		
		if (this.data.substr(0, 4) == "<!--") {
			// Comment.
			this._state[0]._children.push(this._eatComment());
			
		} else if (this.data.substr(0, 2) == "<!") {
			// SGML element.
			this._state[0]._children.push(this._eatSGMLElement());
			
		} else if (this.data[0] == "<") {
			var e = this._eatElement(this._state[0]);
			if (e.empty) {
				this._state[0]._children.push(e);
			} else if (e.start) {
				this._state[0]._children.push(e);
				this._state.unshift(e);
			} else {
				if (e.name != this._state[0].name) {
					throw new Error("Expected </" + this._state[0].name + ">, found </" + e.name + ">");
				}
				this._state.shift();
				if (this._state.length == 0) {
					// We've ended the root element, that's it folks!
					break;
				}
			}
			
		} else {
			var pos = this.data.indexOf("<");
			if (pos < 0) {
				this._state[0]._content = this.data;
				this.data = "";
			} else {
				this._state[0]._content = this.data.substr(0, pos);
				this.data = this.data.substr(pos);
			}
		}
	}
	
	// Eat any trailing spaces and comments.
	while (this.data.length > 0) {
		this._eatWhitespace();
		
		if (this.data.substr(0, 4) == "<!--") {
			// Comment.
			this.root.push(this._eatComment());
			
		} else if (this.data.length > 0) {
			throw new Error("Expected EOF or comment, found " + this.data.substr(0, 10) + "...");
		}
	}
	
	if (this._state.length > 0) {
		throw new Error("Expected </" + this._state[0].name + ">, found EOF.");
	}
	if (this.data.length > 0) {
		throw new Error("Expected EOF, found " + this.data.substr(0, 10) + "...");
	}
}

XMLParser.prototype._processEntities = function() {}
XMLParser.prototype._processEntities_TODO = function(string) {
	var i = 0;
	while (i < string.length) {
		// Find next &...
		i = string.indexOf("&", i);
		
		//if (string.substr(i, 4) == "&lt;") {
		//	this.data = string.substr(0, i - 1) + "<" + 
		
		// Make sure we skip over the character we just inserted.
		i++;
	}
	
	return string;
}

XMLParser.prototype._eatWhitespace = function() {
	var len = this._countWhitespace();
	if (len > 0) {
		this.data = this.data.substr(len);
	}
}

XMLParser.prototype._countWhitespace = function() {
	// Optimise by checking only first character first.
	if (this.data.length <= 0) {
		return 0;
	}
	var ws = this.data[0].match(/^\s+/);
	if (ws) {
		// Now check first 256 characters.
		ws = this.data.substr(0, 256).match(/^\s+/);
		
		if (ws[0].length == 256) {
			// Ok, check it all.
			ws = this.data.match(/^\s+/);
			return ws[0].length;
		}
		return ws[0].length;
	}
	return 0;
}

XMLParser.prototype._eatComment = function() {
	if (this.data.substr(0, 4) != "<!--") {
		throw new Error("Expected <!--, found " + this.data.substr(0, 10) + "...");
	}
	var i = 4;
	while (i < this.data.length) {
		if (this.data.substr(i, 3) == "-->") {
			// Done.
			var c = new XMLComment(this.data.substr(4, i - 4));
			this.data = this.data.substr(i + 3);
			return c;
		}
		i++;
	}
	throw new Error("Expected -->, found EOF.");
}

XMLParser.prototype._eatSGMLElement = function() {
	if (this.data.substr(0, 2) != "<!") {
		throw new Error("Expected <!, found " + this.data.substr(0, 10) + "...");
	}
	
	// CDATA chunk?
	if (this.data.substr(0, 9) == "<![CDATA[") {
		return this._eatCDATAElement();
	}
	
	var i = 2;
	var inQuote = "";
	while (i < this.data.length) {
		if (inQuote == this.data[i]) {
			inQuote = "";
			
		} else if ((this.data[i] == "'") || (this.data[i] == '"')) {
			inQuote = this.data[i];
			
		} else if (this.data[i] == ">") {
			// Done.
			var c = new XMLComment(this.data.substr(2, i - 1));
			this.data = this.data.substr(i + 1);
			return c;
		}
		i++;
	}
	throw new Error("Expected >, found EOF.");
}

XMLParser.prototype._eatCDATAElement = function() {
	if (this.data.substr(0, 9) != "<![CDATA[") {
		throw new Error("Expected <![CDATA[, found " + this.data.substr(0, 20) + "...");
	}
	
	var i = 9;
	while (i < this.data.length) {
		if ((this.data[i] == "]") && (this.data.substr(i, 3) == "]]>")) {
			// Done.
			var e = new XMLCData(this.data.substr(9, i - 9));
			this.data = this.data.substr(i + 3);
			return e;
		}
		i++;
	}
	throw new Error("Expected ]]>, found EOF.");
}

XMLParser.prototype._eatElement = function(parent) {
	if (this.data[0] != "<") {
		throw new Error("Expected <, found " + this.data.substr(0, 10) + "...");
	}
	
	var whitespace = /\s/i;
	var e;
	var name = "";
	var start = true;
	var pi = false;
	var i = 1;
	if (this.data[i] == "?") {
		pi = true;
		i++;
	}
	if (!pi && (this.data[i] == "/")) {
		start = false;
		i++;
	}
	
	while (i < this.data.length) {
		if (!pi && (this.data[i] == ">")) {
			e = new XMLElement(parent, name, start, pi, false);
			this.data = this.data.substr(i + 1);
			e.resolveNamespaces();
			return e;
			
		} else if (start && (this.data.substr(i, 2) == "/>")) {
			e = new XMLElement(parent, name, start, pi, true);
			this.data = this.data.substr(i + 2);
			e.resolveNamespaces();
			return e;
			
		} else if (pi && (this.data.substr(i, 2) == "?>")) {
			e = new XMLElement(parent, name, start, pi, false);
			this.data = this.data.substr(i + 2);
			e.resolveNamespaces();
			return e;
			
		} else if (whitespace.test(this.data[i])) {
			// End of name.
			e = new XMLElement(parent, name, start, pi, false);
			i++;
			break;
			
		} else {
			name += this.data[i];
		}
		i++;
	}
	
	// On to attributes.
	name = "";
	var a = "";
	var inName = false;
	var inEQ = false;
	var inVal = false;
	var inQuote = "";
	while (i < this.data.length) {
		if (!pi && !inName && !inEQ && !inVal && (this.data[i] == ">")) {
			this.data = this.data.substr(i + 1);
			e.resolveNamespaces();
			return e;
			
		} else if (!pi && !inName && !inEQ && !inVal && (this.data.substr(i, 2) == "/>")) {
			if (!e.start) {
				throw new Error("Invalid end tag, found " + this.data.substr(0, i + 10) + "...");
			}
			e.empty = true;
			this.data = this.data.substr(i + 2);
			e.resolveNamespaces();
			return e;
			
		} else if (pi && !inName && !inEQ && !inVal && (this.data.substr(i, 2) == "?>")) {
			this.data = this.data.substr(i + 2);
			e.resolveNamespaces();
			return e;
			
		} else if (inName && (this.data[i] == "=")) {
			inName = false;
			inEQ = true;
			
		} else if (inEQ && ((this.data[i] == '"') || (this.data[i] == "'"))) {
			inEQ = false;
			inVal = true;
			inQuote = this.data[i];
			
		} else if (inQuote && ((this.data[i] == '"') || (this.data[i] == "'"))) {
			if (inQuote == this.data[i]) {
				inQuote = "";
				inVal = false;
				e._attributes.push(new XMLAttribute(e, name, a));
				name = "";
				a = "";
			}
			
		} else if (whitespace.test(this.data[i])) {
			if (inVal && !inQuote) {
				inVal = false;
				e._attributes.push(new XMLAttribute(e, name, a));
				name = "";
				a = "";
			}
			
		} else if (inEQ || inVal) {
			if (inEQ) {
				inEQ = false;
				inVal = true;
				a = "";
			}
			a += this.data[i];
			
		} else {
			if (!inName) {
				inName = true;
			}
			name += this.data[i];
		}
		i++;
	}
	
	//this.data = this.data.substr(i);
	
	//e.resolveNamespaces();
	//return e;
	throw new Error("Expected >, found EOF.");
}



function XMLElement(parent, name, start, pi, empty) {
	this.type = "XMLElement";
	this.parent = parent;
	this.name = name;
	this.start = start;
	this.pi = pi;
	this.empty = empty;
	this.namespace = "";
	
	var ary = this.name.match(/^(.*?):(.*)$/);
	if (ary) {
		this.prefix = ary[1];
		this.localName = ary[2];
	} else {
		this.prefix = null;
		this.localName = this.name;
	}
	
	this._attributes = [];
	this._content = "";
	this._children = [];
}

XMLElement.prototype.toString = function() {
	var str = "<";
	if (this.pi) {
		str += "?";
	} else if (!this.start) {
		str += "/";
	}
	if (this.prefix != null) {
		str += this.prefix + ":";
	}
	str += this.localName;
	if (this.namespace) {
		str += "[[" + this.namespace + "]]";
	}
	for (var a in this._attributes) {
		str += " " + this._attributes[a];
	}
	if (this.pi) {
		str += "?";
	}
	if (this.empty || ((this._content == "") && (this._children.length == 0))) {
		str += "/";
	}
	str += ">";
	
	return str;
}

XMLElement.prototype.resolveNamespaces = function() {
	function getNameSpaceFromPrefix(base, pfx) {
		var attrName = "xmlns";
		if (pfx) {
			attrName = "xmlns:" + pfx;
		}
		
		var element = base;
		while (element) {
			var attr = element.attribute(attrName);
			if (attr) {
				return attr.value;
			}
			element = element.parent;
		}
		return "";
	};
	
	this.namespace = getNameSpaceFromPrefix(this, this.prefix);
	
	for (var i = 0; i < this._attributes.length; i++) {
		if (/^xmlns(?:$|:)/.test(this._attributes[i].name)) {
			continue;
		}
		this._attributes[i].namespace = getNameSpaceFromPrefix(this, this._attributes[i].prefix);
	}
}

XMLElement.prototype.is = function(localName, namespace) {
	return (this.localName == localName) && (this.namespace == namespace);
}

XMLElement.prototype.contents = function() {
	var str = this._content;
	if ((this._content == "") && (this._children.length > 0)) {
		str = "";
		for (var i = 0; i < this._children.length; i++) {
			str += this._children[i].contents();
		}
	}
	return str;
}

XMLElement.prototype.attribute = function(name, namespace) {
	for (var i = 0; i < this._attributes.length; i++) {
		if ((typeof namespace != "undefined") && (this._attributes[i].namespace != namespace)) {
			continue;
		}
		if (this._attributes[i].name == name) {
			return this._attributes[i];
		}
	}
	return null;
}

XMLElement.prototype.childrenByName = function(localName, namespace) {
	var rv = [];
	for (var i = 0; i < this._children.length; i++) {
		if ((typeof namespace != "undefined") && (this._children[i].namespace != namespace)) {
			continue;
		}
		if (this._children[i].localName == localName) {
			rv.push(this._children[i]);
		}
	}
	return rv;
}

XMLElement.prototype.childByName = function(localName, namespace) {
	var l = this.childrenByName(localName, namespace);
	if (l.length != 1) {
		return null;
	}
	return l[0];
}



function XMLAttribute(parent, name, value) {
	this.type = "XMLAttribute";
	this.parent = parent;
	this.name = name;
	this.value = value;
	this.namespace = "";
	
	var ary = this.name.match(/^(.*?):(.*)$/);
	if (ary) {
		this.prefix = ary[1];
		this.localName = ary[2];
	} else {
		this.prefix = null;
		this.localName = this.name;
	}
}

XMLAttribute.prototype.toString = function() {
	var str = "";
	if (this.prefix != null) {
		str += this.prefix + ":";
	}
	str += this.localName;
	if (this.namespace) {
		str += "[[" + this.namespace + "]]";
	}
	str += "='" + this.value + "'";
	return str;
}



function XMLCData(value) {
	this.type = "XMLCData";
	this.value = value;
}

XMLCData.prototype.toString = function() {
	return "<![CDATA[" + this.value + "]]>";
}

XMLCData.prototype.contents = function() {
	return this.value;
}



function XMLComment(value) {
	this.type = "XMLComment";
	this.value = value;
}

XMLComment.prototype.toString = function() {
	return "<!--" + this.value + "-->";
}

XMLComment.prototype.contents = function() {
	return this.value;
}
// #includeend
