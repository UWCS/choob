// JavaScript plugin example for Choob.
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

// Constructor: JSExample
function JSExample(mods, irc) {
	// Set off two callbacks.
	// Parameters are:
	//   <param>  Argument passed directly to the callback. Used to identify
	//            different intervals in this plugin.
	//   <delay>  Miliseconds to wait before callback.
	//   <id>     A unique ID for the callback. Setting a callback with the same
	//            ID as an existing one will replace it. IDs are released once
	//            once the callback has been made.
	mods.interval.callBack("init", 1000, 1);
	mods.interval.callBack("trac-svn", 15000, 2);
	
	this._debugChannel = "#testing42";
	this._announceChannel = "#bots";
	
	/*var url = new URL("http://trac.warwickcompsoc.co.uk/choob/cgi-bin/trac.cgi/timeline?daysback=30&max=5&wiki=on&ticket=on&changeset=on&milestone=on&format=rss");
	this._tracRSS = new GetContentsCached(url, 30000);
	this._tracRSSLastItem = "";
	this._tracRSSChannel = this._announceChannel;*/
	
	var url2 = new URL("http://trac.warwickcompsoc.co.uk/choob/cgi-bin/trac.cgi/roadmap");
	this._tracRoadmap = new GetContentsCached(url2, 30000);
	this._tracRoadmapLastData = new Object();
	this._tracRoadmapChannel = this._announceChannel;
}


JSExample.prototype.info = [
		"Basic example JavaScript plugin w/ Trac Roadmap notifications.",
		"James Ross",
		"silver@warwickcompsoc.co.uk",
		"1.3.0"
	];


// Callback for all intervals from this plugin.
JSExample.prototype.interval = function(param, mods, irc) {
	// We use the <param> to identify the callback function to call. This has
	// some significant advantages over the basic interval/callback model
	// provided for us. The main one is, of course, that we keep the code for
	// each interval (however many we have) separate and self-contained.
	// 
	// To do this, we construct a method name from <param>, by adding a "_"
	// prefix (methods that don't start with a letter are ignored by the
	// plugin manager), then removing all "-"s, upper-casing the following
	// letter. This gives the nice mapping of a name "foo-bar-baz" to method
	// _fooBarBaz. We call the method with the same signature as this
	// function, and announce any errors from this code in the 'debug channel'.
	
	function mapCase(s, a, b) {
		return a.toUpperCase() + b.toLowerCase();
	};
	
	if (param) {
		var name = "_" + param.replace(/-(\w)(\w+)/g, mapCase) + "Interval";
		
		if (name in this) {
			this[name](param, mods, irc);
		} else {
			irc.sendMessage(this._debugChannel, "Interval code missing: " + name);
		}
		
	} else {
		irc.sendMessage(this._debugChannel, "Unnamed interval attempted!");
	}
}


// Command: Exit
JSExample.prototype.commandExit = function(mes, mods, irc) {
	if (!mods.security.hasPerm(new ChoobPermission("exit"), mes)) {
		irc.sendContextReply(mes, "You don't have permission to do that!");
		return;
	}
	var params = mods.util.getParams(mes, 1);
	if (params.size() <= 1) {
		irc.quit("Bye bye!");
	} else {
		irc.quit(params.get(1));
	}
}
JSExample.prototype.commandExit.help = [
		"Makes the bot exit, if you have permission."
	];


// Command: Restart
JSExample.prototype.commandRestart = function(mes, mods, irc) {
	if (!mods.security.hasPerm(new ChoobPermission("exit"), mes)) {
		irc.sendContextReply(mes, "You don't have permission to do that!");
		return;
	}
	var params = mods.util.getParams(mes, 1);
	if (params.size() <= 1) {
		irc.restart("Restarting...");
	} else {
		irc.restart(params.get(1));
	}
}
JSExample.prototype.commandRestart.help = [
		"Makes the bot exit, recompile, and restart itself, if you have permission."
	];


// Command: Info
JSExample.prototype.commandInfo = function(mes, mods, irc) {
	try {
		var version = this._getSVNRevision();
		if (version) {
			irc.sendContextReply(mes, "Currently running SVN revision " + version + ".");
		} else {
			irc.sendContextReply(mes, "Couldn't find a version in the SVN file!");
		}
	} catch(ex) {
		irc.sendContextReply(mes, "Error getting SVN info: " + ex);
	}
}
JSExample.prototype.commandInfo.help = [
		"Displays some information about the bot."
	];


// Command: Roadmap
JSExample.prototype.commandRoadmap = function(mes, mods, irc) {
	var ok = false;
	
	for (var i in this._tracRoadmapLastData) {
		var itemObj = this._tracRoadmapLastData[i];
		ok = true;
		
		irc.sendContextReply(mes, "Trac: '" + itemObj.name + "' is " +
				itemObj.percent + "% complete (" +
				itemObj.ticketsOpen + " of " +
				itemObj.ticketsTotal + " tickets remain).");
	}
	if (!ok) {
		irc.sendContextReply(mes, "Either there are no Trac releases, or there was a problem getting the data.");
	}
}
JSExample.prototype.commandRoadmap.help = [
		"Shows the current Trac roadmap information."
	];


// Command: Timeline
JSExample.prototype.commandTimeline = function(mes, mods, irc) {
	var items = this._getTracRoadmapItems(5, null);
	
	if (items.length == 0) {
		irc.sendContextReply(mes, "Either there are no Trac timeline items, or there was a problem getting the data.");
		return;
	}
	for (var i = 0; i < items.length; i++) {
		irc.sendContextReply(mes, "Trac: " + items[i].title + " (" + items[i].link + ") - " + items[i].desc);
	}
}
JSExample.prototype.commandTimeline.help = [
		"Shows the newest 5 items from the Trac timeline."
	];


// Command: Eval
JSExample.prototype.commandEval = function(mes, mods, irc) {
	if (mes.getNick() != "Silver") {
		irc.sendContextReply(mes, "Error: Bad user!");
		return;
	}
	var params = mods.util.getParams(mes, 1);
	if (params.size() <= 1) {
		irc.sendContextReply(mes, "Syntax: JSExample.Eval <JS>");
		return;
	}
	var js = "" + params.get(1);
	
	try {
		var rv = "" + eval(js);
	} catch(ex) {
		if (("name" in ex) && ("message" in ex) && ("fileName" in ex)) {
			irc.sendContextReply(mes, "Exception: [" + ex.name + "] <" + ex.message + "> at <" + ex.fileName + ">.");
			var javaEx = ex.javaException;
			javaEx.printStackTrace();
		} else {
			irc.sendContextReply(mes, "Exception: " + ex);
		}
		return;
	}
	rv = rv.replace(/[\r\n]+/g, " ");
	
	irc.sendContextReply(mes, "Result: " + rv);
}
JSExample.prototype.commandTimeline.help = [
		"Shows the newest 5 items from the Trac timeline."
	];


// Interval: init
JSExample.prototype._initInterval = function(param, mods, irc) {
	irc.sendMessage(this._debugChannel, "Interval! Woo!");
}


// Interval: Trac RSS
JSExample.prototype._tracSvnInterval = function(param, mods, irc) {
	try {
		/*var newItems = this._getTracRoadmapItems(5, this._tracRSSLastItem);
		
		if (this._tracRSSLastItem) {
			for (var i = 0; i < newItems.length; i++) {
				irc.sendMessage(this._tracRSSChannel, "Trac: " + newItems[i].title + " (" + newItems[i].link + ") - " + newItems[i].desc);
			}
		}
		if (newItems.length > 0) {
			this._tracRSSLastItem = newItems[newItems.length - 1].date;
		}*/
		
		// Roadmap changes.
		var roadmapData = "" + this._tracRoadmap.getContents();
		roadmapData = roadmapData.replace(/[\r\n]+/g, "");
		
		var firstRun = true;
		var roadmapChan = this._debugChannel;
		for (var i in this._tracRoadmapLastData) {
			firstRun = false;
			roadmapChan = this._tracRoadmapChannel;
			break;
		}
		
		var roadmapRegExp = new RegExp("class=\"milestone\".*?class=\"description\"", "gi");
		
		var item;
		while ((item = roadmapRegExp.exec(roadmapData))) {
			var itemObj = new Object();
			itemObj.name = /<em>(.*?)<\/em>/i.exec(item)[1];
			itemObj.percent = 1 * /class="percent">(.*?)\%</i.exec(item)[1];
			itemObj.ticketsOpen = 1 * /\?status=new[^"]+">(.*?)</i.exec(item)[1];
			itemObj.ticketsClosed = 1 * /\?status=closed[^"]+">(.*?)</i.exec(item)[1];
			itemObj.ticketsTotal = itemObj.ticketsOpen + itemObj.ticketsClosed;
			itemObj.all = [
					itemObj.name, itemObj.percent, itemObj.ticketsOpen, itemObj.ticketsClosed
				].join(",");
			
			var lastObj = null;
			if (this._tracRoadmapLastData && (itemObj.name in this._tracRoadmapLastData)) {
				lastObj = this._tracRoadmapLastData[itemObj.name];
			}
			
			//if (firstRun && !lastObj) {
			//	lastObj = {
			//			name: itemObj.name, percent: 100,
			//			ticketsOpen: 0, ticketsClosed: 0, ticketsTotal: 0,
			//			all: itemObj.name + ",0,0,0"
			//		};
			//}
			
			if (lastObj) {
				if ((lastObj.ticketsOpen > 0) && (itemObj.ticketsOpen == 0) && (itemObj.ticketsTotal > 0)) {
					irc.sendMessage(roadmapChan, "Trac: '" + itemObj.name + "' is now complete (all " +
							itemObj.ticketsTotal + " tickets closed)! :-D");
					
				} else if (itemObj.ticketsOpen < lastObj.ticketsOpen) {
					var count = (lastObj.ticketsOpen - itemObj.ticketsOpen);
					
					irc.sendMessage(roadmapChan, "Trac: '" + itemObj.name + "' has gone from " +
							lastObj.percent + "% complete to " +
							itemObj.percent + "% complete :-) (" +
							count + (count == 1 ? " ticket" : " tickets") + " closed, now " +
							itemObj.ticketsOpen + " of " +
							itemObj.ticketsTotal + (itemObj.ticketsTotal == 1 ? " ticket" : " tickets") + " remain).");
					
				} else if (itemObj.ticketsOpen > lastObj.ticketsOpen) {
					var count = (itemObj.ticketsOpen - lastObj.ticketsOpen);
					
					irc.sendMessage(roadmapChan, "Trac: '" + itemObj.name + "' has gone from " +
							lastObj.percent + "% complete to " +
							itemObj.percent + "% complete :-( (" +
							count + (count == 1 ? " ticket" : " tickets") + " opened, now " +
							itemObj.ticketsOpen + " of " +
							itemObj.ticketsTotal + (itemObj.ticketsTotal == 1 ? " ticket" : " tickets") + " remain).");
				}
				
			} else if (!firstRun) {
				irc.sendMessage(roadmapChan, "Trac: '" + itemObj.name + "' is " +
						itemObj.percent + "% complete (" +
						itemObj.ticketsOpen + " of " +
						itemObj.ticketsTotal + " tickets remain).");
			}
			this._tracRoadmapLastData[itemObj.name] = itemObj;
		}
	} catch (ex) {
		irc.sendMessage(this._debugChannel, "_tracSvnInterval exception: line " + ex.lineNumber + ", " + ex);
	}
	mods.interval.callBack(param, 30000, 2);
}


// Command: JS
JSExample.prototype.commandJS = function(mes, mods, irc) {
	irc.sendContextReply(mes, "JS Rocks!");
}
// Note: This maps internally to the generic helpCommandJS.
//       It is purely convenience (and nice OO).
JSExample.prototype.commandJS.help = ["Says something about JS."];


// Command: Test
JSExample.prototype.commandTest = function(mes, mods, irc) {
	irc.sendContextReply(mes, "Our name: " + mods.security.getPluginName(0));
}
JSExample.prototype.commandTest.help = ["Runs some tests, and displays our plugin name."];


// Filter: /js sucks/
JSExample.prototype.filterFoo = function(mes, mods, irc) {
	irc.sendContextReply(mes, "No, JS Rocks!");
}
JSExample.prototype.filterFoo.regexp = /js sucks/i;


// Event: join
JSExample.prototype._onJoin = function(event, mods, irc) {
	// FIXME //
	if (event.getNick() != "SilverBOT") {
		irc.sendContextMessage(event, "Welcome to " + event.getChannel() + ", " + event.getNick() + "!");
	}
	if ((event.getChannel() == this._debugChannel) ||
		((event.getNick() == "SilverBOT") && (event.getChannel() == this._announceChannel)))
	{
		var version = this._getSVNRevision();
		if (version) {
			irc.sendContextMessage(event, "Currently running SVN revision " + version + ".");
		}
	}
}


// Event: part
JSExample.prototype._onPart = function(event, mods, irc) {
	irc.sendContextMessage(event, "Cya on the flip-side, " + event.getNick() + "!");
}


// Internal stuff.
JSExample.prototype._getSVNRevision = function() {
	var svnDataFile = new File("svn.data");
	if (!svnDataFile.exists()) {
		return null;
	}
	
	var reader = new BufferedReader(new InputStreamReader(new FileInputStream(svnDataFile)));
	var line;
	while((line = reader.readLine()) != null) {
		var ary = line.match(/\/repos\/choob\/!svn\/ver\/(\d+)\//);
		if (ary) {
			return ary[1];
		}
	}
	return null;
}

JSExample.prototype._getTracRoadmapItems = function(count, cutoff) {
	var rssData = "" + this._tracRSS.getContents();
	rssData = rssData.replace(/[\r\n]+/g, "");
	
	var re = new RegExp("<item>.*?<\\/item>", "gi");
	var rvItems = new Array();
	
	var item;
	while ((item = re.exec(rssData))) {
		var pubDate = /<pubDate>(.*?)<\/pubDate>/i.exec(item)[1];
		
		if (pubDate == cutoff) {
			break;
		}
		
		var title = /<title>(.*?)<\/title>/i.exec(item)[1];
		var link  = /<link>(.*?)<\/link>/i.exec(item)[1];
		var desc  = /<description>(.*?)<\/description>/i.exec(item)[1];
		// Decode XML into HTML...
		desc = desc.replace(/&lt;/g, "<");
		desc = desc.replace(/&gt;/g, ">");
		desc = desc.replace(/&#34;/g, '"'); // Why not &quot;? Who knows.
		desc = desc.replace(/&amp;/g, '&');
		// Remove questionmarks at end of link text (WikiLink stuff).
		desc = desc.replace(/\[<a[^>]*href="([^\"]+trac.cgi\/changeset\/\d+)"[^>]*>(.*?)<\/a>\]/gi, "[$2] ($1)");
		desc = desc.replace(/<a[^>]*href="([^\"]+trac.cgi\/ticket\/\d+)"[^>]*>(.*?)<\/a>/gi, "$2 ($1)");
		desc = desc.replace(/\?<\/a>/gi, "");
		// Remove all tags.
		desc = desc.replace(/<[^>]+>/g, "");
		// Decode HTML into text...
		desc = desc.replace(/&lt;/g, "<");
		desc = desc.replace(/&gt;/g, ">");
		desc = desc.replace(/&#34;/g, '"'); // Why not &quot;? Who knows.
		desc = desc.replace(/&amp;/g, '&');
		// Remove all entities.
		desc = desc.replace(/&[^;]+;/g, "");
		
		var ary;
		if ((ary = title.match(/^(Ticket #\d+ resolved)/i))) {
			title = ary[1];
		}
		link = link.replace(/trac\.warwickcompsoc\.co\.uk/gi, "trac.uwcs.co.uk");
		desc = desc.replace(/trac\.warwickcompsoc\.co\.uk/gi, "trac.uwcs.co.uk");
		
		rvItems.unshift({ date: pubDate, title: title, link: link, desc: desc });
	}
	return rvItems;
}

