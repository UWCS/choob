// JavaScript plugin for RSS and Atom feeds.
// 
// Copyright 2005 - 2006, James G. Ross
// 

var BufferedReader    = Packages.java.io.BufferedReader;
var File              = Packages.java.io.File;
var FileInputStream   = Packages.java.io.FileInputStream;
var InputStreamReader = Packages.java.io.InputStreamReader;
var System            = Packages.java.lang.System;
var URL               = Packages.java.net.URL;
var ChoobPermission   = Packages.uk.co.uwcs.choob.support.ChoobPermission;
var GetContentsCached = Packages.uk.co.uwcs.choob.support.GetContentsCached;


function log(msg) {
	dumpln("FEEDS [" + (new Date()) + "] " + msg);
}

String.prototype.trim =
function _trim() {
	return this.replace(/^\s+/, "").replace(/\s+$/, "");
}

// Constructor: Feeds
function Feeds(mods, irc) {
	profile.start();
	this._mods = mods;
	this._irc = irc;
	this._debugChannel    = "#testing42";
	this._announceChannel = "#testing42";
	this._debugProfile    = false;
	this._debugInterval   = false;
	this._debugXML        = false;
	
	this._feedList = new Array();
	this._feedCheckLock = false;
	
	var feeds = mods.odb.retrieve(Feed, "");
	for (var i = 0; i < feeds.size(); i++) {
		var feed = feeds.get(i);
		
		// Allow the feed to save itself when it makes changes.
		feed.__mods = mods;
		feed.save = function _feed_save() { this.__mods.odb.update(this) };
		
		feed.init(this, "");
		this._feedList.push(feed);
	}
	
	mods.interval.callBack("feed-check", 30000, 1);
	profile.stop("init");
}


Feeds.prototype.info = [
		"Generic feed reader with notification.",
		"James Ross",
		"silver@warwickcompsoc.co.uk",
		"1.5.24"
	];


// Callback for all intervals from this plugin.
Feeds.prototype.interval = function(param, mods, irc) {
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


// Command: Add
Feeds.prototype.commandAdd = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 2);
	if (params.size() <= 2) {
		irc.sendContextReply(mes, "Syntax: Feeds.Add <feedname> <url>");
		return;
	}
	var feedName = String(params.get(1)).trim();
	var feedURL  = String(params.get(2)).trim();
	
	// Remove existing feed, if possible.
	var feed = this._getFeed(feedName);
	if (feed) {
		if (!this._canAdminFeed(feed, mes)) {
			irc.sendContextReply(mes, "You don't have permission to do that!");
		}
		this._removeFeed(feed);
	}
	
	// Load new feed.
	irc.sendContextReply(mes, "Loading feed '" + feedName + "'...");
	var feed = new Feed(this, feedName, feedURL, mes.getContext());
	mods.odb.save(feed);
	
	// Allow the feed to save itself when it makes changes.
	feed.__mods = mods;
	feed.save = function _feed_save() { this.__mods.odb.update(this) };
	
	feed.owner = this._getOwnerFrom(mes.getNick());
	this._feedList.push(feed);
	feed.addOutputTo(mes.getContext());
	
	// Check feed now.
	mods.interval.callBack("feed-check", 1000, 1);
}
Feeds.prototype.commandAdd.help = [
		"Adds a new feed.",
		"<feedname> <url>",
		"<feedname> is the name for the new feed",
		"<url> is the URL of the new feed"
	];


// Command: Remove
Feeds.prototype.commandRemove = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 1);
	if (params.size() <= 1) {
		irc.sendContextReply(mes, "Syntax: Feeds.Remove <feedname>");
		return;
	}
	var feedName = String(params.get(1)).trim();
	
	var feed = this._getFeed(feedName);
	if (!feed) {
		irc.sendContextReply(mes, "Feed '" + feedName + "' not found.");
		return;
	}
	if (!this._canAdminFeed(feed, mes)) {
		irc.sendContextReply(mes, "You don't have permission to do that!");
		return;
	}
	
	this._removeFeed(feed);
	irc.sendContextReply(mes, "Feed '" + feed.displayName + "' removed.");
}
Feeds.prototype.commandRemove.help = [
		"Removes an feed entirely.",
		"<feedname>",
		"<feedname> is the name of the feed to remove"
	];


// Command: List
Feeds.prototype.commandList = function(mes, mods, irc) {
	if (this._feedList.length == 0) {
		irc.sendContextReply(mes, "No feeds set up.");
		return;
	}
	
	function dispFeed(i, feed) {
		var dests = feed._outputTo.join(", ");
		if (feed.getError()) {
			irc.sendContextReply(mes, "Feed " + feed.name + ": '\x02" + feed.displayName +
					"\x02', ERROR: " + feed.getError() +
					", source <" + feed.url + ">." +
					(dests ? " Notifications to: " + dests + "." : ""));
		} else {
			irc.sendContextReply(mes, "Feed " + feed.name + ": '\x02" + feed.displayName +
					"\x02', owned by " + (feed.owner ? feed.owner : "<unknown>") +
					(feed.isPrivate ? " (\x02private\x02)" : "") +
					", " + feed._lastItemCount +
					" items (" + feed.getLastLoaded() + "), TTL of " + feed.ttl + "s, source <" + feed.url + ">." +
					(dests ? " Notifications to: " + dests + "." : ""));
		}
	};
	
	function getFeedString(feed) {
		return feed.name + " (" + feed._lastItemCount + (feed.isPrivate ? ", \x02private\x02" : "") + ")";
	};
	
	function getFeedErrorString(feed) {
		return feed.name + " (" + feed.getError() + ")";
	};
	
	var params = mods.util.getParams(mes, 1);
	if (params.size() > 1) {
		var findName = String(params.get(1)).trim();
		var findIO = "," + findName.toLowerCase() + ",";
		var foundIO = false;
		
		for (var i = 0; i < this._feedList.length; i++) {
			// Skip private feeds that user can't control.
			if (this._feedList[i].isPrivate && !this._canAdminFeed(this._feedList[i], mes)) {
				continue;
			}
			if (this._feedList[i].name.toLowerCase() == findName.toLowerCase()) {
				dispFeed(i, this._feedList[i]);
				return;
			} else if (("," + this._feedList[i]._outputTo.join(",") + ",").toLowerCase().indexOf(findIO) != -1) {
				dispFeed(i, this._feedList[i]);
				foundIO = true;
			} else if (findName == "*") {
				dispFeed(i, this._feedList[i]);
			}
		}
		if ((findName != "*") && !foundIO) {
			irc.sendContextReply(mes, "Feed or target '" + findName + "' not found.");
		}
		return;
	}
	
	var outputs = new Object();
	var errs = new Array();
	
	for (var i = 0; i < this._feedList.length; i++) {
		// Skip private feeds that user can't control.
		if (this._feedList[i].isPrivate && !this._canAdminFeed(this._feedList[i], mes)) {
			continue;
		}
		
		var st = this._feedList[i].getSendTo();
		
		for (var j = 0; j < st.length; j++) {
			if (!(st[j] in outputs)) {
				outputs[st[j]] = new Array();
			}
			outputs[st[j]].push(this._feedList[i]);
		}
		if (st.length == 0) {
			if (!("nowhere" in outputs)) {
				outputs["nowhere"] = new Array();
			}
			outputs["nowhere"].push(this._feedList[i]);
		}
		
		if (this._feedList[i].getError()) {
			errs.push(this._feedList[i]);
		}
	}
	
	var outputList = new Array();
	for (var o in outputs) {
		outputList.push(o);
	}
	outputList.sort();
	
	for (o = 0; o < outputList.length; o++) {
		var str = "For " + outputList[o] + ": ";
		for (var i = 0; i < outputs[outputList[o]].length; i++) {
			if (i > 0) {
				str += ", ";
			}
			str += getFeedString(outputs[outputList[o]][i]);
		}
		str += ".";
		irc.sendContextReply(mes, str);
	}
	for (var i = 0; i < errs.length; i++) {
		irc.sendContextReply(mes, "Error: " + getFeedErrorString(errs[i]));
	}
}
Feeds.prototype.commandList.help = [
		"Lists all feeds and where they are displayed, or information about a single feed.",
		"[<name>]",
		"<name> is the (optional) name of a feed or target (e.g. channel) to get details on"
	];


// Command: AddOutput
Feeds.prototype.commandAddOutput = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 2);
	if (params.size() <= 2) {
		irc.sendContextReply(mes, "Syntax: Feeds.AddOutput <feedname> <dest>");
		return;
	}
	var feedName = String(params.get(1)).trim();
	
	var feed = this._getFeed(feedName);
	if (!feed) {
		irc.sendContextReply(mes, "Feed '" + feedName + "' not found.");
		return;
	}
	if (!this._canAdminFeed(feed, mes)) {
		irc.sendContextReply(mes, "You don't have permission to do that!");
		return;
	}
	var feedDest = String(params.get(2)).trim();
	
	if (feed.addOutputTo(feedDest)) {
		irc.sendContextReply(mes, "Feed '" + feed.displayName + "' will now output to '" + feedDest + "'.");
	} else {
		irc.sendContextReply(mes, "Feed '" + feed.displayName + "' already outputs to '" + feedDest + "'.");
	}
}
Feeds.prototype.commandAddOutput.help = [
		"Adds a new output destination for an feed.",
		"<feedname> <dest>",
		"<feedname> is the name of the feed to modify",
		"<dest> is the channel name to send notifications to"
	];


// Command: RemoveOutput
Feeds.prototype.commandRemoveOutput = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 2);
	if (params.size() <= 2) {
		irc.sendContextReply(mes, "Syntax: Feeds.RemoveOutput <feedname> <dest>");
		return;
	}
	var feedName = String(params.get(1)).trim();
	
	var feed = this._getFeed(feedName);
	if (!feed) {
		irc.sendContextReply(mes, "Feed '" + feedName + "' not found.");
		return;
	}
	if (!this._canAdminFeed(feed, mes)) {
		irc.sendContextReply(mes, "You don't have permission to do that!");
		return;
	}
	var feedDest = String(params.get(2)).trim();
	
	if (feed.removeOutputTo(feedDest)) {
		irc.sendContextReply(mes, "Feed '" + feed.displayName + "' will no longer output to '" + feedDest + "'.");
	} else {
		irc.sendContextReply(mes, "Feed '" + feed.displayName + "' doesn't output to '" + feedDest + "'.");
	}
}
Feeds.prototype.commandRemoveOutput.help = [
		"Removes an output destination for an feed.",
		"<feedname> <dest>",
		"<feedname> is the name of the feed to modify",
		"<dest> is the channel name to stop sending notifications to"
	];


// Command: Recent
Feeds.prototype.commandRecent = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 2);
	if (params.size() <= 1) {
		irc.sendContextReply(mes, "Syntax: Feeds.Recent <feedname> [[<offset>] <count>]");
		return;
	}
	var feedName = String(params.get(1)).trim();
	
	var feed = this._getFeed(feedName);
	if (!feed) {
		irc.sendContextReply(mes, "Feed '" + feedName + "' not found.");
		return;
	}
	// Allow anyone to get recent items for public feeds, and only someone
	// who can admin a feed to do it for private feeds.
	if (feed.isPrivate && !this._canAdminFeed(feed, mes)) {
		irc.sendContextReply(mes, "You don't have permission to do that!");
		return;
	}
	var offset = 0;
	var count  = 5;
	if (params.size() > 3) {
		offset = params.get(2);
		count  = params.get(3);
	} else if (params.size() > 2) {
		count  = params.get(2);
	}
	
	if ((String(offset).trim() != String(Number(offset))) || (offset < 0)) {
		irc.sendContextReply(mes, "<offset> must be numeric and non-negative");
		return;
	}
	
	if ((String(count).trim() != String(Number(count))) || (count <= 0)) {
		irc.sendContextReply(mes, "<count> must be numeric and positive");
		return;
	}
	
	if (offset + count > feed._lastItemCount) {
		count = feed._lastItemCount - offset;
	}
	
	feed.showRecent(mes.getContext(), offset, count);
}
Feeds.prototype.commandRecent.help = [
		"Displays a number of recent items from a feed.",
		"<feedname> [[<offset>] <count>]",
		"<feedname> is the name of the feed to modify",
		"<offset> is now many entires back in time to go (default is 0)",
		"<count> is the number of recent items to show (default is 5)"
	];


// Command: SetOwner
Feeds.prototype.commandSetOwner = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 2);
	if (params.size() <= 2) {
		irc.sendContextReply(mes, "Syntax: Feeds.SetOwner <feedname> <owner>");
		return;
	}
	var feedName = String(params.get(1)).trim();
	
	var feed = this._getFeed(feedName);
	if (!feed) {
		irc.sendContextReply(mes, "Feed '" + feedName + "' not found.");
		return;
	}
	if (!this._canAdminFeed(feed, mes)) {
		irc.sendContextReply(mes, "You don't have permission to do that!");
		return;
	}
	var owner = this._getOwnerFrom(String(params.get(2)).trim());
	
	feed.owner = owner;
	irc.sendContextReply(mes, "Feed '" + feed.displayName + "' now has an owner of " + owner + ".");
	mods.odb.update(feed);
}
Feeds.prototype.commandSetOwner.help = [
		"Sets the owner of the feed, who has full control over it.",
		"<feedname> <ttl>",
		"<feedname> is the name of the feed to modify",
		"<owner> is the new owner",
	];


// Command: SetPrivate
Feeds.prototype.commandSetPrivate = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 2);
	if (params.size() <= 2) {
		irc.sendContextReply(mes, "Syntax: Feeds.SetPrivate <feedname> <value>");
		return;
	}
	var feedName = String(params.get(1)).trim();
	
	var feed = this._getFeed(feedName);
	if (!feed) {
		irc.sendContextReply(mes, "Feed '" + feedName + "' not found.");
		return;
	}
	if (!this._canAdminFeed(feed, mes)) {
		irc.sendContextReply(mes, "You don't have permission to do that!");
		return;
	}
	var isPrivate = String(params.get(2)).trim();
	isPrivate = ((isPrivate == "1") || (isPrivate == "on") || (isPrivate == "true") || (isPrivate == "yes"));
	
	feed.isPrivate = isPrivate;
	if (isPrivate) {
		irc.sendContextReply(mes, "Feed '" + feed.displayName + "' is now private.");
	} else {
		irc.sendContextReply(mes, "Feed '" + feed.displayName + "' is no longer private.");
	}
	mods.odb.update(feed);
}
Feeds.prototype.commandSetOwner.help = [
		"Sets whether the feed shows up to users who can't administrate it.",
		"<feedname> <value>",
		"<feedname> is the name of the feed to modify",
		"<value> is either 'true' or 'false'",
	];


// Command: SetTTL
Feeds.prototype.commandSetTTL = function(mes, mods, irc) {
	var params = mods.util.getParams(mes, 2);
	if (params.size() <= 2) {
		irc.sendContextReply(mes, "Syntax: Feeds.SetTTL <feedname> <ttl>");
		return;
	}
	var feedName = String(params.get(1)).trim();
	
	var feed = this._getFeed(feedName);
	if (!feed) {
		irc.sendContextReply(mes, "Feed '" + feedName + "' not found.");
		return;
	}
	if (!this._canAdminFeed(feed, mes)) {
		irc.sendContextReply(mes, "You don't have permission to do that!");
		return;
	}
	var feedTTL = 1 * params.get(2);
	
	if (feedTTL < 60) {
		irc.sendContextReply(mes, "Sorry, but a TTL of less than 60 is not allowed.");
		return;
	}
	
	feed.ttl = feedTTL;
	irc.sendContextReply(mes, "Feed '" + feed.displayName + "' now has a TTL of " + feedTTL + ".");
	mods.odb.update(feed);
}
Feeds.prototype.commandSetTTL.help = [
		"Sets the TTL (time between updates) of a feed.",
		"<feedname> <ttl>",
		"<feedname> is the name of the feed to modify",
		"<ttl> is the new TTL for the feed",
	];


// Command: SetDebug
Feeds.prototype.commandSetDebug = function(mes, mods, irc) {
	if (!mods.security.hasPerm(new ChoobPermission("feeds.debug"), mes)) {
		irc.sendContextReply(mes, "You don't have permission to do that!");
		return;
	}
	
	var params = mods.util.getParams(mes, 2);
	if (params.size() <= 2) {
		irc.sendContextReply(mes, "Syntax: Feeds.SetDebug <flag> <enabled>");
		return;
	}
	var flag    = String(params.get(1)).trim();
	var enabled = String(params.get(2)).trim();
	enabled = ((enabled == "1") || (enabled == "on") || (enabled == "true") || (enabled == "yes"));
	
	if (flag == "profile") {
		if (enabled) {
			this._debugProfile = true;
			irc.sendContextReply(mes, "Debug profiling enabled.");
		} else {
			this._debugProfile = false;
			irc.sendContextReply(mes, "Debug profiling disabled.");
		}
	} else if (flag == "interval") {
		if (enabled) {
			this._debugInterval = true;
			irc.sendContextReply(mes, "Debug interval timing enabled.");
		} else {
			this._debugInterval = false;
			irc.sendContextReply(mes, "Debug interval timing disabled.");
		}
	} else if (flag == "xml") {
		if (enabled) {
			this._debugXML = true;
			irc.sendContextReply(mes, "Debug XML parser enabled.");
		} else {
			this._debugXML = false;
			irc.sendContextReply(mes, "Debug XML parser disabled.");
		}
	} else if (flag == "trace") {
		if (enabled) {
			profile.showRunningTrace = true;
			irc.sendContextReply(mes, "Debug execution trace enabled.");
		} else {
			profile.showRunningTrace = false;
			irc.sendContextReply(mes, "Debug execution trace disabled.");
		}
	} else {
		irc.sendContextReply(msg, "Unknown flag specified.");
	}
}
Feeds.prototype.commandSetDebug.help = [
		"Sets debug mode on or off.",
		"<flag> <enabled>",
		"<flag> is one of 'profile', 'interval', 'xml' or 'trace', so specify what to debug",
		"<enabled> is either 'true' or 'false' to set"
	];


// Command: Info
//Feeds.prototype.commandInfo = function(mes, mods, irc) {
//	
//	//irc.sendContextReply(mes, "Error getting SVN info: " + ex);
//}
//Feeds.prototype.commandInfo.help = [
//		"Stuff."
//	];


Feeds.prototype._getOwnerFrom = function(nick) {
	var primary = this._mods.nick.getBestPrimaryNick(nick);
	var root = this._mods.security.getRootUser(primary);
	if (root) {
		return String(root);
	}
	return String(primary);
}

Feeds.prototype._getFeed = function(name) {
	for (var i = 0; i < this._feedList.length; i++) {
		var feed = this._feedList[i];
		if (feed.name.toLowerCase() == name.toLowerCase()) {
			return feed;
		}
	}
	return null;
}

Feeds.prototype._canAdminFeed = function(feed, mes) {
	if (this._mods.security.hasPerm(new ChoobPermission("feeds.edit"), mes)) {
		return true; // plugin admin
	}
	if (feed.owner == this._getOwnerFrom(mes.getNick())) {
		return true; // feed owner
	}
	return false;
}

Feeds.prototype._removeFeed = function(feed) {
	for (var i = 0; i < this._feedList.length; i++) {
		if (this._feedList[i] == feed) {
			this._mods.odb["delete"](this._feedList[i]);
			this._feedList.splice(i, 1);
			return;
		}
	}
}

Feeds.prototype._ = function() {
}

// Interval: feed-check
Feeds.prototype._feedCheckInterval = function(param, mods, irc) {
	if (this._feedCheckLock)
		return;
	this._feedCheckLock = true;
	if (this._debugInterval) {
		log("Interval: start");
	}
	
	for (var i = 0; i < this._feedList.length; i++) {
		var feed = this._feedList[i];
		if (!feed.safeToCheck()) {
			continue;
		}
		
		if (this._debugInterval) {
			log("Interval:   checking " + feed.name + " (" + -this._feedList[i].getNextCheck() + "ms late)");
		}
		if (this._debugProfile) {
			profile.start();
		}
		feed.checkForNewItems();
		if (this._debugProfile) {
			profile.stop(feed.name);
		}
	}
	
	var nextCheck = 60 * 60 * 1000; // 1 hour
	for (var i = 0; i < this._feedList.length; i++) {
		var feedNextCheck = this._feedList[i].getNextCheck();
		if (feedNextCheck < 0) {
			feedNextCheck = 0;
		}
		if (nextCheck > feedNextCheck) {
			nextCheck = feedNextCheck;
		}
	}
	// Helps to group the calls.
	var extra = 0;
	if (nextCheck > 10000) {
		extra = 5000;
	}
	
	if (this._debugInterval) {
		log("Interval:   next check due in " + nextCheck + "ms" + (extra ? " + " + extra + "ms" : ""));
		log("Interval: end");
	}
	
	this._feedCheckLock = false;
	// Don't return in anything less than 1s.
	mods.interval.callBack("feed-check", nextCheck + extra, 1);
}


function Feed() {
	this.id = 0;
	this.name = "";
	this.displayName = "";
	this.outputTo = "";
	this.url = "";
	this.ttl = 300; // Default TTL
	this.owner = "";
	this.isPrivate = false;
	this.save = function(){};
	this._error = "";
	this._errorExpires = 0;
	if (arguments.length > 0) {
		this._ctor(arguments[0], arguments[1], arguments[2], arguments[3]);
	}
}

Feed.prototype._ctor = function(parent, name, url, loadContext) {
	this.name = name;
	this.displayName = name;
	this.url = url;
	this.init(parent, loadContext)
}

Feed.prototype.init = function(parent, loadContext) {
	profile.enterFn("Feed(" + this.name + ")", "init");
	this._parent = parent;
	this._loadContext = loadContext;
	this._outputTo = new Array();
	if (this.outputTo) {
		this._outputTo = this.outputTo.split(" ");
		this._outputTo.sort();
	}
	
	this._cachedContents = null;
	this._lastSeen = new Object();
	this._lastSeenPub = new Object();
	this._lastItemCount = 0;
	this._lastCheck = 0;
	this._lastLoaded = 0;
	profile.leaveFn("init");
}

Feed.prototype.addOutputTo = function(destination) {
	for (var i = 0; i < this._outputTo.length; i++) {
		if (this._outputTo[i].toLowerCase() == destination.toLowerCase()) {
			return false;
		}
	}
	this._outputTo.push(destination);
	this._outputTo.sort();
	this.outputTo = this._outputTo.join(" ");
	this.save();
	return true;
}

Feed.prototype.removeOutputTo = function(destination) {
	for (var i = 0; i < this._outputTo.length; i++) {
		if (this._outputTo[i].toLowerCase() == destination.toLowerCase()) {
			this._outputTo.splice(i, 1);
			this.outputTo = this._outputTo.join(" ");
			this.save();
			return true;
		}
	}
	return false;
}

Feed.prototype.getError = function() {
	if (this._error) {
		return this._error + " [expires " + (new Date(this._errorExpires)) + "]";
	}
	return "";
}

Feed.prototype.setError = function(msg) {
	this._error = msg;
	this._errorExpires = Number(new Date()) + 60 * 60 * 1000; // 1 hour
}

Feed.prototype.getSendTo = function() {
	var st = new Array();
	for (var i = 0; i < this._outputTo.length; i++) {
		st.push(this._outputTo[i]);
	}
	return st;
}

Feed.prototype.getLastLoaded = function() {
	if (this._lastLoaded == 0)
		return "never loaded";
	return ("loaded " + this._lastLoaded);
}

// Return boolean indicating if it is ok to reload the contents of the feed.
Feed.prototype.safeToCheck = function() {
	// If the error has expired, clear it.
	if (this._error && (this._errorExpires < Number(new Date()))) {
		this._error = "";
		this._errorExpires = 0;
	}
	if (this._error) {
		return false;
	}
	
	// <ttl> min delay. Default is 1m.
	var checkTime = Number(new Date()) - (this.ttl * 1000);
	return (Number(this._lastCheck) < checkTime);
}

// Return the number of milliseconds until the next checkpoint.
Feed.prototype.getNextCheck = function() {
	var delay = (this._lastCheck ? Number(this._lastCheck) - Number(new Date()) + (this.ttl * 1000) : 0);
	if (this._error) {
		delay = this._errorExpires - Number(new Date());
	}
	
	return delay;
}

Feed.prototype.showRecent = function(target, offset, count) {
	var items = this.getItems();
	
	if (this.getError()) {
		this._sendToAll("'" + this.displayName + "': \x02ERROR\x02: " + this.getError());
		return;
	}
	
	if (items.length == 0) {
		this._sendTo(target, "'" + this.displayName + "' has no recent items.");
		return;
	}
	
	var start = items.length - 1; // Default to the last item.
	if (start > offset + count) {
		start = offset + count - 1;
	}
	if (start > items.length - 1) {
		// Make sure not to start before the oldest item we have.
		start = items.length - 1;
	}
	for (var i = start; i >= offset; i--) {
		this._sendTo(target, "[" + items[i].date + "] \x1F" + items[i].title + "\x1F " + items[i].desc,
		             (items[i].link ? " <" + items[i].link + ">" : ""));
	}
}

Feed.prototype.checkForNewItems = function() {
	profile.enterFn("Feed(" + this.name + ")", "checkForNewItems");
	if (this.getError()) {
		profile.leaveFn("checkForNewItems");
		return;
	}
	
	var firstRun = (this._lastCheck == 0);
	var newItems = this.getNewItems();
	
	if (this.getError()) {
		if (firstRun && this._loadContext) {
			// We're trying to load the feed, and it failed. Oh the humanity.
			this._parent._irc.sendMessage(this._loadContext, "'" + this.displayName + "' failed to load, incurring the error: " + this.getError());
		}
		//this._sendToAll("'" + this.displayName + "': \x02ERROR\x02: " + this.getError());
		profile.leaveFn("checkForNewItems");
		return;
	}
	this._lastLoaded = new Date();
	
	if (firstRun && this._loadContext) {
		this._parent._irc.sendMessage(this._loadContext, "'" + this.displayName + "' loaded with " + this._lastItemCount + " items.");
	}
	
	// If there are more than 3 items, and it's more than 20% of the feed's
	// length, don't display the items. This allows feeds with more items (e.g.
	// news feeds) to flood a little bit more, but still prevents a feed from
	// showing all it's items if it just added them all.
	// Never bother with more than 10 items, whatever.
	if ((newItems.length > 10) || ((newItems.length > 3) && (newItems.length > 0.20 * this._lastItemCount))) {
		this._sendToAll("'" + this.displayName + "' has too many (" + newItems.length + ") new items to display.");
	} else {
		for (var i = newItems.length - 1; i >= 0; i--) {
			if (newItems[i].updated) {
				this._sendToAll("\x1F" + newItems[i].title + "\x1F " + newItems[i].desc,
				                (newItems[i].link ? " <" + newItems[i].link + ">" : ""));
			} else {
				this._sendToAll("\x1F\x02" + newItems[i].title + "\x02\x1F " + newItems[i].desc,
				                (newItems[i].link ? " <" + newItems[i].link + ">" : ""));
			}
		}
	}
	profile.leaveFn("checkForNewItems");
}

Feed.prototype.ensureCachedContents = function() {
	profile.enterFn("Feed(" + this.name + ")", "ensureCachedContents");
	try {
		if (!this._cachedContents) {
			var urlObj = new URL(this.url);
			profile.enterFn("Feed(" + this.name + ")", "ensureCachedContents.getContentsCached");
			this._cachedContents = new GetContentsCached(urlObj, 60000);
			profile.leaveFn("ensureCachedContents.getContentsCached");
		}
	} catch(ex) {
		// Error = no items.
		this.setError("Exception getting data: " + ex + (ex.fileName ? " at <" + ex.fileName + ":" + ex.lineNumber + ">":""));
		profile.leaveFn("ensureCachedContents");
		return false;
	}
	profile.leaveFn("ensureCachedContents");
	return true;
}

Feed.prototype.getItems = function() {
	profile.enterFn("Feed(" + this.name + ")", "getItems");
	
	if (!this.ensureCachedContents()) {
		profile.leaveFn("getItems");
		return [];
	}
	
	var feedData = "";
	profile.enterFn("Feed(" + this.name + ")", "getItems.getCachedContents");
	try {
		feedData = String(this._cachedContents.getContents());
	} catch(ex) {
		profile.leaveFn("getItems.getCachedContents");
		// Error = no items.
		this.setError("Exception getting data: " + ex + (ex.fileName ? " at <" + ex.fileName + ":" + ex.lineNumber + ">":""));
		profile.leaveFn("getItems");
		return [];
	}
	profile.leaveFn("getItems.getCachedContents");
	
	if (feedData == "") {
		this.setError("Unable to fetch data");
		profile.leaveFn("getItems");
		return [];
	}
	
	try {
		profile.enterFn("FeedParser(" + this.name + ")", "new");
		var feedParser = new FeedParser(this._parent, feedData);
		profile.leaveFn("new");
		profile.leaveFn("getItems");
		return feedParser.items;
	} catch(ex) {
		this.setError("Exception in parser: " + ex + (ex.fileName ? " at <" + ex.fileName + ":" + ex.lineNumber + ">":""));
		profile.leaveFn("getItems");
		return [];
	}
	profile.leaveFn("getItems");
}

Feed.prototype.getNewItems = function() {
	profile.enterFn("Feed(" + this.name + ")", "getNewItems");
	
	if (!this.ensureCachedContents()) {
		profile.leaveFn("getNewItems");
		return [];
	}
	
	var feedData = "";
	profile.enterFn("Feed(" + this.name + ")", "getNewItems.getCachedContents");
	try {
		feedData = String(this._cachedContents.getContents());
	} catch(ex) {
		profile.leaveFn("getNewItems.getCachedContents");
		// Error = no items.
		this.setError("Exception getting data: " + ex + (ex.fileName ? " at <" + ex.fileName + ":" + ex.lineNumber + ">":""));
		profile.leaveFn("getNewItems");
		return [];
	}
	profile.leaveFn("getNewItems.getCachedContents");
	
	if (feedData == "") {
		this.setError("Unable to fetch data");
		profile.leaveFn("getNewItems");
		return [];
	}
	
	try {
		profile.enterFn("FeedParser(" + this.name + ")", "new");
		var feedParser = new FeedParser(this._parent, feedData);
		profile.leaveFn("new");
	} catch(ex) {
		this.setError("Exception in parser: " + ex + (ex.fileName ? " at <" + ex.fileName + ":" + ex.lineNumber + ">":""));
		profile.leaveFn("getNewItems");
		return [];
	}
	
	var firstTime = true;
	var curItems = new Object();
	for (var d in this._lastSeen) {
		firstTime = false;
		this._lastSeen[d] = false;
	}
	for (var d in this._lastSeenPub) {
		this._lastSeenPub[d] = false;
	}
	
	// Force TTL to be >= that in the feed itself.
	if (feedParser.ttl && (this.ttl < feedParser.ttl)) {
		this.ttl = feedParser.ttl;
		this.save();
	}
	
	// Update title if it's different.
	if (feedParser.title) {
		var feedTitle = feedParser.title.replace(/^\s+/, "").replace(/\s+$/, "");
		if (this.displayName != feedTitle) {
			this.displayName = feedTitle;
			this.save();
		}
	}
	
	var newItems = new Array();
	
	// Only keep new or updated items in the list.
	for (var i = 0; i < feedParser.items.length; i++) {
		var item = feedParser.items[i];
		
		// Prefer, in order: GUID, link, date, title.
		var unique = (item.guid ? item.guid : (item.link ? item.link : (item.date != "?" ? item.date : item.title)));
		var date = unique + ":" + item.date;
		if (unique in this._lastSeen) {
			// Seen this item before. Has it changed?
			if (date in this._lastSeenPub) {
				// No change.
				this._lastSeen[unique] = true;
				this._lastSeenPub[date] = true;
				continue;
			}
			// Items changed.
			item.updated = true;
		}
		// New item.
		this._lastSeen[unique] = true;
		this._lastSeenPub[date] = true;
		newItems.push(item);
		dumpln("New item: [" + unique + "]:[" + date + "]:" + (item.updated ? "updated" : "new"));
	}
	
	for (var d in this._lastSeen) {
		if (!this._lastSeen[d]) {
			delete this._lastSeen[d];
			dumpln("Lost item: [" + d + "]:_lastSeen");
		}
	}
	for (var d in this._lastSeenPub) {
		if (!this._lastSeenPub[d]) {
			delete this._lastSeenPub[d];
			dumpln("Lost item: [" + d + "]:_lastSeenPub");
		}
	}
	
	var count = 0;
	for (var d in this._lastSeenPub) {
		count++;
	}
	this._lastItemCount = count;
	
	if (firstTime) {
		newItems = new Array();
	}
	this._lastCheck = new Date();
	
	profile.leaveFn("getNewItems");
	return newItems;
}

Feed.prototype._sendToAll = function(message, suffix) {
	for (var i = 0; i < this._outputTo.length; i++) {
		this._sendTo(this._outputTo[i], message, suffix);
	}
}

Feed.prototype._sendTo = function(target, message, suffix) {
	if (typeof suffix != "string") {
		suffix = "";
	}
	if (message.length + suffix.length > 390) {
		message = message.substr(0, 390 - suffix.length) + "...";
	}
	this._parent._irc.sendMessage(target, message + suffix);
}

function _decodeEntities(data) {
	profile.enterFn("", "_decodeEntities");
	
	// Decode XML into HTML...
	data = data.replace(     /&lt;/g, "<");
	data = data.replace(     /&gt;/g, ">");
	data = data.replace(   /&quot;/g, '"');
	data = data.replace(  /&#0*34;/g, '"');
	data = data.replace(/&#0*8220;/g, '"');
	data = data.replace(/&#0*8221;/g, '"');
	data = data.replace(  /&#0*39;/g, "'");
	data = data.replace(/&#0*8217;/g, "'");
	data = data.replace( /&#0*160;/g, " ");
	data = data.replace(  /&mdash;/g, "-");
	data = data.replace(/&#0*8212;/g, "-");
	data = data.replace(/&#0*8230;/g, "...");
	data = data.replace(    /&amp;/g, "&");
	
	profile.leaveFn("_decodeEntities");
	return data;
}

function _decodeRSSHTML(data) {
	profile.enterFn("", "_decodeRSSHTML");
	
	// Decode XML into HTML...
	data = _decodeEntities(data);
	// Remove all tags.
	data = data.replace(/<[^>]+>/g, " ");
	// Decode HTML into text...
	data = _decodeEntities(data);
	// Remove all entities.
	//data = data.replace(/&[^;]+;/g, "");
	data = data.replace(/\s+/g, " ");
	
	profile.leaveFn("_decodeRSSHTML");
	return data;
}

function _decodeAtomText(element) {
	if (!element)
		return "";
	
	var content = element.contents();
	var type = element.attribute("type");
	
	if (type && (type.value == "html")) {
		return _decodeRSSHTML(content);
	}
	
	return content;
}

function _decodeAtomDate(element) {
	var ary = element.contents().match(/^(\d+)-(\d+)-(\d+)T(\d+):(\d+):(\d+)(?:.(\d+))?(Z|([+-])(\d+):(\d+))$/);
	if (ary) {
		var d = new Date(ary[1], ary[2], ary[3], ary[4], ary[5], ary[6]);
		// 8 = Z/zone
		// 9 = +/-
		// 10/11 = zone offset
		if (d.getTimezoneOffset() != 0) {
			d = new Date(Number(d) - d.getTimezoneOffset() * 60 * 1000);
		}
		if (ary[9] == "+") {
			d = new Date(Number(d) + ((ary[10] * 60) + ary[11]) * 60 * 1000);
		}
		if (ary[9] == "-") {
			d = new Date(Number(d) - ((ary[10] * 60) + ary[11]) * 60 * 1000);
		}
		return d;
	}
	return 0;
}



// Generic feed parser.
function FeedParser(feedsOwner, data) {
	profile.enterFn("FeedParser", "init.replace");
	data = data.replace(/[\r\n\s]+/, " ");
	profile.leaveFn("init.replace");
	profile.enterFn("XMLParser", "new");
	try {
		this._xmlData = new XMLParser(data);
	} finally {
		profile.leaveFn("new");
	}
	
	this._parse(feedsOwner);
}

FeedParser.prototype.toString = function() {
	return "FeedParser<" + this.title + ">";
}

FeedParser.prototype._parse = function(feedsOwner) {
	profile.enterFn("FeedParser", "_parse");
	this.title = "";
	this.link = "";
	this.description = "";
	this.language = "";
	this.ttl = 0;
	this.items = new Array();
	this.error = "";
	
	var ATOM_1_0_NS = "http://www.w3.org/2005/Atom";
	
	function getChildContents(elt, name, namespace) {
		profile.enterFn("FeedParser", "getChildContents");
		var child = elt.childByName(name, namespace);
		if (child) {
			profile.leaveFn("getChildContents");
			return child.contents();
		}
		profile.leaveFn("getChildContents");
		return "";
	};
	
	// Check what kind of feed we have!
	if (this._xmlData.rootElement.localName == "rss") {
		var rssVersion = this._xmlData.rootElement.attribute("version");
		
		if (rssVersion && ((rssVersion.value == 0.91) || (rssVersion.value == 2.0))) {
			// RSS 0.91 or 2.0 code.
			var channel = this._xmlData.rootElement.childByName("channel");
			
			this.title       = getChildContents(channel, "title");
			this.link        = getChildContents(channel, "link");
			this.description = getChildContents(channel, "description");
			this.language    = getChildContents(channel, "language");
			this.ttl         = getChildContents(channel, "ttl");
			
			var items = channel.childrenByName("item");
			
			for (var i = 0; i < items.length; i++) {
				var item = items[i];
				
				var pubDate = item.childByName("pubDate");
				if (pubDate) {
					pubDate = pubDate.contents();
				} else {
					pubDate = "?";
				}
				
				var guid  = item.childByName("guid") || "";
				if (guid) {
					guid = guid.contents();
				}
				
				var title = item.childByName("title") || "";
				if (title) {
					title = title.contents();
				}
				
				var link  = item.childByName("link") || "";
				if (link) {
					link = link.contents();
				}
				
				var desc = item.childByName("description") || "";
				if (desc) {
					desc = desc.contents();
				}
				
				this.items.push({
						date:    pubDate,
						guid:    guid,
						title:   _decodeRSSHTML(title),
						link:    _decodeEntities(link),
						desc:    _decodeRSSHTML(desc),
						updated: false
					});
			}
		} else {
			if (rssVersion) {
				profile.leaveFn("_parse");
				throw new Error("Unsuported RSS version: " + rssVersion.value);
			}
			profile.leaveFn("_parse");
			throw new Error("Unsuported RSS version: <unknown>");
		}
	} else if (this._xmlData.rootElement.localName == "RDF") {
		// RSS 1.0 probably.
		if (this._xmlData.rootElement.namespace == "http://www.w3.org/1999/02/22-rdf-syntax-ns#") {
			
			var channel = this._xmlData.rootElement.childByName("channel", "http://purl.org/rss/1.0/");
			
			this.title       = getChildContents(channel, "title",       "http://purl.org/rss/1.0/");
			this.link        = getChildContents(channel, "link",        "http://purl.org/rss/1.0/");
			this.description = getChildContents(channel, "description", "http://purl.org/rss/1.0/");
			this.language    = getChildContents(channel, "language",    "http://purl.org/rss/1.0/");
			this.ttl         = getChildContents(channel, "ttl",         "http://purl.org/rss/1.0/");
			
			var items = this._xmlData.rootElement.childrenByName("item", "http://purl.org/rss/1.0/");
			
			for (var i = 0; i < items.length; i++) {
				var item = items[i];
				
				var pubDate = item.childByName("pubDate", "http://purl.org/rss/1.0/");
				if (pubDate) {
					pubDate = pubDate.contents();
				} else {
					pubDate = "?";
				}
				
				var title = item.childByName("title", "http://purl.org/rss/1.0/") || "";
				if (title) {
					title = title.contents();
				}
				
				var link  = item.childByName("link", "http://purl.org/rss/1.0/") || "";
				if (link) {
					link = link.contents();
				}
				
				var desc = item.childByName("description", "http://purl.org/rss/1.0/") || "";
				if (desc) {
					desc = desc.contents();
				}
				
				this.items.push({
						date:    pubDate,
						title:   _decodeRSSHTML(title),
						link:    _decodeEntities(link),
						desc:    _decodeRSSHTML(desc),
						updated: false
					});
			}
			
		} else {
			profile.leaveFn("_parse");
			throw new Error("Unsuported namespace: " + this._xmlData.rootElement.namespace);
		}
		
	} else if (this._xmlData.rootElement.is("feed", ATOM_1_0_NS)) {
		// Atom 1.0.
		
		// Text decoder: _decodeAtomText(element)
		// Date decoder: _decodeAtomDate(element);
		
		var feed = this._xmlData.rootElement;
		this.title = _decodeAtomText(feed.childByName("title", ATOM_1_0_NS));
		
		var items = feed.childrenByName("entry", ATOM_1_0_NS);
		
		for (var i = 0; i < items.length; i++) {
			var item = items[i];
			
			var date  = _decodeAtomDate(item.childByName("updated", ATOM_1_0_NS));
			
			var title = _decodeAtomText(item.childByName("title", ATOM_1_0_NS));
			
			var link  = item.childByName("link", ATOM_1_0_NS);
			if (link) {
				link = link.attribute("href");
				if (link) {
					link = link.value;
				}
			}
			
			var desc  = _decodeAtomText(item.childByName("content", ATOM_1_0_NS));
			
			this.items.push({
					date:    date,
					title:   title,
					link:    link,
					desc:    desc,
					updated: false
				});
		}
		
	} else {
		profile.leaveFn("_parse");
		throw new Error("Unsupported feed type: " + this._xmlData.rootElement);
	}
	if (feedsOwner && feedsOwner._debugXML) {
		var limit = { value: 25 };
		log("# URL        : " + this.link);
		log("# TITLE      : " + this.title);
		log("# DESCRIPTION: " + this.description);
		this._xmlData._dump(this._xmlData.root, "", limit);
	}
	profile.leaveFn("_parse");
}



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
	profile.enterFn("XMLParser", "_dumpElement");
	for (var i = 0; i < list.length; i++) {
		this._dumpElement(list[i], indent, limit);
	}
	profile.leaveFn("_dumpElement");
}

XMLParser.prototype._dumpElement = function(elt, indent, limit) {
	profile.enterFn("XMLParser", "_dumpElement");
	if (elt._content) {
		this._dumpln(indent + elt + elt._content + "</" + elt.name + ">", limit);
	} else if (elt._children && (elt._children.length > 0)) {
		this._dumpln(indent + elt, limit);
		this._dump(elt._children, indent + "  ", limit);
		this._dumpln(indent + "</" + elt.name + ">", limit);
	} else {
		this._dumpln(indent + elt, limit);
	}
	profile.leaveFn("_dumpElement");
}

XMLParser.prototype._parse = function() {
	profile.enterFn("XMLParser", "_parse");
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
				profile.leaveFn("_parse");
				throw new Error("Expected <?xml?>, found <?" + e.name + "?>");
			}
			this.xmlPI = e;
			this.root.push(e);
			break;
			
		} else {
			break;
			//profile.leaveFn("_parse");
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
				profile.leaveFn("_parse");
				throw new Error("Expected start element, found end element");
			}
			this.rootElement = e;
			this.root.push(e);
			this._state.unshift(e);
			break;
			
		} else {
			profile.leaveFn("_parse");
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
					profile.leaveFn("_parse");
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
	
	this._eatWhitespace();
	if (this.data.length > 0) {
		profile.leaveFn("_parse");
		throw new Error("Expected EOF, found " + this.data.substr(0, 10) + "...");
	}
	profile.leaveFn("_parse");
}

XMLParser.prototype._processEntities = function() {}
XMLParser.prototype._processEntities_TODO = function(string) {
	profile.enterFn("XMLParser", "_processEntities");
	
	var i = 0;
	while (i < string.length) {
		// Find next &...
		i = string.indexOf("&", i);
		
		//if (string.substr(i, 4) == "&lt;") {
		//	this.data = string.substr(0, i - 1) + "<" + 
		
		// Make sure we skip over the character we just inserted.
		i++;
	}
	
	profile.leaveFn("_processEntities");
	return string;
}

XMLParser.prototype._eatWhitespace = function() {
	profile.enterFn("XMLParser", "_eatWhitespace");
	var len = this._countWhitespace();
	if (len > 0) {
		this.data = this.data.substr(len);
	}
	profile.leaveFn("_eatWhitespace");
}

XMLParser.prototype._countWhitespace = function() {
	profile.enterFn("XMLParser", "_countWhitespace");
	
	// Optimise by checking only first character first.
	var ws = this.data[0].match(/^\s+/);
	if (ws) {
		// Now check first 256 characters.
		ws = this.data.substr(0, 256).match(/^\s+/);
		
		if (ws[0].length == 256) {
			// Ok, check it all.
			ws = this.data.match(/^\s+/);
			profile.leaveFn("_countWhitespace");
			return ws[0].length;
		}
		profile.leaveFn("_countWhitespace");
		return ws[0].length;
	}
	profile.leaveFn("_countWhitespace");
	return 0;
}

XMLParser.prototype._eatComment = function() {
	profile.enterFn("XMLParser", "_eatComment");
	if (this.data.substr(0, 4) != "<!--") {
		profile.leaveFn("_eatComment");
		throw new Error("Expected <!--, found " + this.data.substr(0, 10) + "...");
	}
	var i = 4;
	while (i < this.data.length) {
		if (this.data.substr(i, 3) == "-->") {
			// Done.
			var c = new XMLComment(this.data.substr(4, i - 4));
			this.data = this.data.substr(i + 3);
			profile.leaveFn("_eatComment");
			return c;
		}
		i++;
	}
	profile.leaveFn("_eatComment");
	throw new Error("Expected -->, found EOF.");
}

XMLParser.prototype._eatSGMLElement = function() {
	profile.enterFn("XMLParser", "_eatSGMLElement");
	if (this.data.substr(0, 2) != "<!") {
		profile.leaveFn("_eatSGMLElement");
		throw new Error("Expected <!, found " + this.data.substr(0, 10) + "...");
	}
	
	// CDATA chunk?
	if (this.data.substr(0, 9) == "<![CDATA[") {
		profile.leaveFn("_eatSGMLElement");
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
			profile.leaveFn("_eatSGMLElement");
			return c;
		}
		i++;
	}
	profile.leaveFn("_eatSGMLElement");
	throw new Error("Expected >, found EOF.");
}

XMLParser.prototype._eatCDATAElement = function() {
	profile.enterFn("XMLParser", "_eatCDATAElement");
	if (this.data.substr(0, 9) != "<![CDATA[") {
		profile.leaveFn("_eatCDATAElement");
		throw new Error("Expected <![CDATA[, found " + this.data.substr(0, 20) + "...");
	}
	
	var i = 9;
	while (i < this.data.length) {
		if ((this.data[i] == "]") && (this.data.substr(i, 3) == "]]>")) {
			// Done.
			var e = new XMLCData(this.data.substr(9, i - 9));
			this.data = this.data.substr(i + 3);
			profile.leaveFn("_eatCDATAElement");
			return e;
		}
		i++;
	}
	profile.leaveFn("_eatCDATAElement");
	throw new Error("Expected ]]>, found EOF.");
}

XMLParser.prototype._eatElement = function(parent) {
	profile.enterFn("XMLParser", "_eatElement");
	if (this.data[0] != "<") {
		profile.leaveFn("_eatElement");
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
			profile.leaveFn("_eatElement");
			return e;
			
		} else if (start && (this.data.substr(i, 2) == "/>")) {
			e = new XMLElement(parent, name, start, pi, true);
			this.data = this.data.substr(i + 2);
			e.resolveNamespaces();
			profile.leaveFn("_eatElement");
			return e;
			
		} else if (pi && (this.data.substr(i, 2) == "?>")) {
			e = new XMLElement(parent, name, start, pi, false);
			this.data = this.data.substr(i + 2);
			e.resolveNamespaces();
			profile.leaveFn("_eatElement");
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
			profile.leaveFn("_eatElement");
			return e;
			
		} else if (!pi && !inName && !inEQ && !inVal && (this.data.substr(i, 2) == "/>")) {
			if (!e.start) {
				profile.leaveFn("_eatElement");
				throw new Error("Invalid end tag, found " + this.data.substr(0, i + 10) + "...");
			}
			e.empty = true;
			this.data = this.data.substr(i + 2);
			e.resolveNamespaces();
			profile.leaveFn("_eatElement");
			return e;
			
		} else if (pi && !inName && !inEQ && !inVal && (this.data.substr(i, 2) == "?>")) {
			this.data = this.data.substr(i + 2);
			e.resolveNamespaces();
			profile.leaveFn("_eatElement");
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
	profile.leaveFn("_eatElement");
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
	profile.enterFn("XMLElement", "toString");
	
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
	
	profile.leaveFn("toString");
	return str;
}

XMLElement.prototype.resolveNamespaces = function() {
	profile.enterFn("XMLElement", "resolveNamespaces");
	
	function getNameSpaceFromPrefix(base, pfx) {
		profile.enterFn("XMLElement", "resolveNamespaces.getNameSpaceFromPrefix");
		var attrName = "xmlns";
		if (pfx) {
			attrName = "xmlns:" + pfx;
		}
		
		var element = base;
		while (element) {
			var attr = element.attribute(attrName);
			if (attr) {
				profile.leaveFn("resolveNamespaces.getNameSpaceFromPrefix");
				return attr.value;
			}
			element = element.parent;
		}
		profile.leaveFn("resolveNamespaces.getNameSpaceFromPrefix");
		return "";
	};
	
	this.namespace = getNameSpaceFromPrefix(this, this.prefix);
	
	for (var i = 0; i < this._attributes.length; i++) {
		if (/^xmlns(?:$|:)/.test(this._attributes[i].name)) {
			continue;
		}
		this._attributes[i].namespace = getNameSpaceFromPrefix(this, this._attributes[i].prefix);
	}
	profile.leaveFn("resolveNamespaces");
}

XMLElement.prototype.is = function(localName, namespace) {
	return (this.localName == localName) && (this.namespace == namespace);
}

XMLElement.prototype.contents = function() {
	profile.enterFn("XMLElement", "contents");
	var str = this._content;
	if ((this._content == "") && (this._children.length > 0)) {
		str = "";
		for (var i = 0; i < this._children.length; i++) {
			str += this._children[i].contents();
		}
	}
	profile.leaveFn("contents");
	return str;
}

XMLElement.prototype.attribute = function(name, namespace) {
	profile.enterFn("XMLElement", "attribute");
	
	for (var i = 0; i < this._attributes.length; i++) {
		if ((typeof namespace != "undefined") && (this._attributes[i].namespace != namespace)) {
			continue;
		}
		if (this._attributes[i].name == name) {
			profile.leaveFn("attribute");
			return this._attributes[i];
		}
	}
	profile.leaveFn("attribute");
	return null;
}

XMLElement.prototype.childrenByName = function(name, namespace) {
	profile.enterFn("XMLElement", "childrenByName");
	
	var rv = [];
	for (var i = 0; i < this._children.length; i++) {
		if ((typeof namespace != "undefined") && (this._children[i].namespace != namespace)) {
			continue;
		}
		if (this._children[i].name == name) {
			rv.push(this._children[i]);
		}
	}
	profile.leaveFn("childrenByName");
	return rv;
}

XMLElement.prototype.childByName = function(name, namespace) {
	profile.enterFn("XMLElement", "childByName");
	
	var l = this.childrenByName(name);
	if (l.length != 1) {
		profile.leaveFn("childByName");
		return null;
	}
	profile.leaveFn("childByName");
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
	profile.enterFn("XMLAttribute", "toString");
	
	var str = "";
	if (this.prefix != null) {
		str += this.prefix + ":";
	}
	str += this.localName;
	if (this.namespace) {
		str += "[[" + this.namespace + "]]";
	}
	str += "='" + this.value + "'";
	
	profile.leaveFn("toString");
	return str;
}



function XMLCData(value) {
	this.type = "XMLCData";
	this.value = value;
}

XMLCData.prototype.toString = function() {
	profile.enterFn("XMLCData", "toString");
	var str = "<![CDATA[" + this.value + "]]>";
	profile.leaveFn("toString");
	return str;
}

XMLCData.prototype.contents = function() {
	return this.value;
}



function XMLComment(value) {
	this.type = "XMLComment";
	this.value = value;
}

XMLComment.prototype.toString = function() {
	profile.enterFn("XMLComment", "toString");
	var str = "<!" + this.value + ">";
	profile.leaveFn("toString");
	return str;
}

XMLComment.prototype.contents = function() {
	return this.toString();
}



function JSProfiler() {
	this.running = false;
	this.showRunningTrace = false;
	this._calls = 0;
}

JSProfiler.prototype.start = function() {
	if (this.running) {
		throw new Error("Can't start profiler when it is already running.");
	}
	this.running = true;
	this._calls = 0;
	this._functions = new Object();
	this._stack = new Array();
	this._lastJumpTime = Number(new Date());
	
	if (this.showRunningTrace) {
		log("PROFILER: START");
	}
}

JSProfiler.prototype.stop = function(title) {
	if (!this.running) {
		throw new Error("Can't stop profiler when it is not running.");
	}
	if (this.showRunningTrace) {
		log("PROFILER: STOP");
	}
	
	this.running = false;
	if (this._calls == 0) {
		//log("No JSPRofiler profiled functions.");
		return;
	}
	
	function makeCol(val, width) {
		val = String(val);
		while (val.length < width) {
			val = " " + val;
		}
		return val;
	};
	
	var keys = new Array();
	for (var key in this._functions) {
		keys.push(key);
	}
	
	var self = this;
	keys.sort(function(a, b) {
			if (self._functions[a].totalTime < self._functions[b].totalTime)
				return  1;
			if (self._functions[a].totalTime > self._functions[b].totalTime)
				return -1;
			if (self._functions[a].callCount < self._functions[b].callCount)
				return  1;
			if (self._functions[a].callCount > self._functions[b].callCount)
				return -1;
			return 0;
		});
	
	if (keys.length == 0) {
		return;
	}
	
	var shownHeaders = false;
	for (var i = 0; i < keys.length; i++) {
		var fn = this._functions[keys[i]];
		// Always print if runTime >= 1000 or in top 3, but drop out for < 100ms anyway.
		//if (((fn.totalTime < 1000) && (i >= 3)) || (fn.totalTime < 100)) {
		if (fn.totalTime < 100) {
			break;
		}
		if (!shownHeaders) {
			log("JSProfiler Dump" + (title ? " for " + title : "") + ":");
			log("  Calls   Actual (ms)  Nested (ms)  Class/Name");
			shownHeaders = true;
		}
		log("  " + makeCol(fn.callCount, 6) + makeCol(fn.runTime, 13) + makeCol(fn.totalTime, 13) + "  " + keys[i]);
	}
}

JSProfiler.prototype.enterFn = function(cls, name) {
	var key = (cls ? cls + "." : "") + name;
	if (!(key in this._functions)) {
		this._functions[key] = { cls: cls, name: name, callCount: 0, totalTime: 0, runTime: 0 };
	}
	if (this.showRunningTrace) {
		var nest = "";
		for (var i = 0; i < this._stack.length; i++) {
			nest += "  ";
		}
		log("PROFILER: " + nest + (cls ? "<" + cls + ">" : "") + name + " {");
	}
	
	var now = Number(new Date());
	
	if (this._stack.length > 0) {
		this._functions[this._stack[this._stack.length - 1].key].runTime += now - this._lastJumpTime;
	}
	
	this._calls++;
	this._functions[key].callCount++;
	this._stack.push({ key: key, name: name, start: now });
	this._lastJumpTime = now;
}

JSProfiler.prototype.leaveFn = function(name) {
	if (this.showRunningTrace) {
		var nest = "";
		for (var i = 1; i < this._stack.length; i++) {
			nest += "  ";
		}
		log("PROFILER: " + nest + "} // " + name);
	}
	
	var now = Number(new Date());
	var items = new Array();
	
	for (var i = this._stack.length - 1; i >= 0; i--) {
		if (this._stack[i].name == name) {
			this._functions[this._stack[i].key].runTime += now - this._lastJumpTime;
			this._functions[this._stack[i].key].totalTime += now - this._stack[i].start;
			if (i != this._stack.length - 1) {
				log("WARNING: leaving function '" + name + "' skipping " + (this._stack.length - 1 - i) + " stack items (" + items.join(", ") + ")!");
			}
			this._stack.splice(i);
			this._lastJumpTime = now;
			return;
		}
		items.push(this._stack[i].key);
	}
	log("WARNING: leaving function '" + name + "' we never entered!");
	this._lastJumpTime = now;
}

var profile = new JSProfiler();
