-- phpMyAdmin SQL Dump
-- version 2.7.0-pl2
-- http://www.phpmyadmin.net
--
-- Host: localhost
-- Generation Time: Jan 15, 2006 at 04:20 PM
-- Server version: 5.0.16
-- PHP Version: 5.0.4
--
-- Database: `choob`
--

-- -
-- This is needed to make sure the constraints from the existing db are
-- cleared, otherwise we get constraint errors when importing.
-- -
DROP DATABASE IF EXISTS choob;
CREATE DATABASE choob;

USE `choob`;

-- --------------------------------------------------------

--
-- Table structure for table `Plugins`
--

DROP TABLE IF EXISTS `Plugins`;
CREATE TABLE `Plugins` (
  `PluginName` varchar(64) NOT NULL default '',
  `URL` mediumtext NOT NULL,
  `CorePlugin` tinyint(1) NOT NULL default '0',
  PRIMARY KEY  (`PluginName`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Dumping data for table `Plugins`
--

INSERT INTO `Plugins` VALUES ('Alias', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/Alias.java', 1);
INSERT INTO `Plugins` VALUES ('Calc', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/Calc.java', 1);
INSERT INTO `Plugins` VALUES ('Dict', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/Dict.java', 1);
INSERT INTO `Plugins` VALUES ('Factoids', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/Factoids.java', 1);
INSERT INTO `Plugins` VALUES ('Flood', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/Flood.java', 1);
INSERT INTO `Plugins` VALUES ('Help', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/Help.java', 1);
INSERT INTO `Plugins` VALUES ('Http', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/Http.java', 0);
INSERT INTO `Plugins` VALUES ('Karma', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/Karma.java', 1);
INSERT INTO `Plugins` VALUES ('Lookup', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/Lookup.java', 1);
INSERT INTO `Plugins` VALUES ('MFJ', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/MFJ.java', 1);
INSERT INTO `Plugins` VALUES ('MiscMsg', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/MiscMsg.java', 1);
INSERT INTO `Plugins` VALUES ('NickServ', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/NickServ.java', 1);
INSERT INTO `Plugins` VALUES ('Options', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/Options.java', 1);
INSERT INTO `Plugins` VALUES ('PassThru', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/PassThru.java', 1);
INSERT INTO `Plugins` VALUES ('Plugin', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/Plugin.java', 1);
INSERT INTO `Plugins` VALUES ('Quote', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/Quote.java', 1);
INSERT INTO `Plugins` VALUES ('Security', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/Security.java', 1);
INSERT INTO `Plugins` VALUES ('Seen', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/Seen.java', 1);
INSERT INTO `Plugins` VALUES ('Shutup', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/Shutup.java', 1);
INSERT INTO `Plugins` VALUES ('Talk', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/Talk.java', 1);
INSERT INTO `Plugins` VALUES ('Tell', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/Tell.java', 1);
INSERT INTO `Plugins` VALUES ('Test', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/Test.java', 1);
INSERT INTO `Plugins` VALUES ('TimedEvents', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/TimedEvents.java', 1);
INSERT INTO `Plugins` VALUES ('Topic', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/Topic.java', 1);
INSERT INTO `Plugins` VALUES ('UserTypeCheck', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/UserTypeCheck.java', 1);
INSERT INTO `Plugins` VALUES ('Vote', 'http://svn.uwcs.co.uk/repos/choob/trunk/contrib/Vote.java', 1);

-- --------------------------------------------------------

--
-- Table structure for table `_objectdb_plugins_alias_aliasobject`
--

DROP TABLE IF EXISTS `_objectdb_plugins_alias_aliasobject`;
CREATE TABLE `_objectdb_plugins_alias_aliasobject` (
  `id` int(11) NOT NULL auto_increment,
  `name` text,
  `converted` text,
  `owner` text,
  `locked` tinyint(4) NOT NULL default '0',
  `help` text,
  `core` text,
  PRIMARY KEY  (`id`),
  KEY `name__index` (`name`(16)),
  KEY `converted__index` (`converted`(16)),
  KEY `owner__index` (`owner`(16)),
  KEY `locked__index` (`locked`),
  KEY `help__index` (`help`(16)),
  KEY `core__index` (`core`(16))
) ENGINE=InnoDB DEFAULT CHARSET=latin1 AUTO_INCREMENT=162 ;

--
-- Dumping data for table `_objectdb_plugins_alias_aliasobject`
--

INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (3, 'colour', 'MFJ.Colour', 'SpeshulChoob', 1, NULL, 'MFJ.Colour');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (4, 'color', 'Talk.Say We don''t speak American here!', 'SpeshulChoob', 1, NULL, 'MFJ.Color');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (5, 'commands', 'help.commands', 'SpeshulChoob', 1, NULL, 'Help.Commands');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (7, 'reasonup', 'karma.reasonup', 'SpeshulChoob', 1, NULL, 'Karma.ReasonUp');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (8, 'lamequote', 'quote.karmamod down $1 $.', 'bucko', 1, 'Make a quote more lame. ||| [ <QuoteID> ] ||| <QuoteID> is the (optional) ID of a quote to make more lame (default: the most recent quote in context.)', NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (9, 'shutup', 'Shutup.Add', 'SpeshulChoob', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (10, 'karma', 'Karma.Get', 'SpeshulChoob', 1, NULL, 'Karma.Get');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (11, 'wakeup', 'Shutup.Remove', 'SpeshulChoob', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (12, 'karmahiscore', 'Karma.HighScores', 'SpeshulChoob', 1, NULL, 'Karma.HighScores');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (13, 'karmaloscore', 'Karma.LowScores', 'SpeshulChoob', 1, NULL, 'Karma.LowScores');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (14, 'say', 'Talk.Say', 'SpeshulChoob', 1, NULL, 'Talk.Say');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (15, 'do', 'Talk.Me', 'SpeshulChoob', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (16, 'msg', 'Talk.Msg', 'SpeshulChoob', 1, NULL, 'Talk.Msg');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (17, 'describe', 'Talk.Describe', 'SpeshulChoob', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (18, 'at', 'TimedEvents.At', 'SpeshulChoob', 1, NULL, 'TimedEvents.At');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (19, 'in', 'TimedEvents.In', 'SpeshulChoob', 1, NULL, 'TimedEvents.In');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (20, 'wqt', 'TimedEvents.Last', 'SpeshulChoob', 1, NULL, 'TimedEvents.Last');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (21, 'tell', 'Tell.Send', 'SpeshulChoob', 1, NULL, 'Tell.Send');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (22, 'ask', 'Tell.Send', 'SpeshulChoob', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (23, 'ct', 'MiscMsg.Ct', 'SpeshulChoob', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (24, 'flipacoin', 'MiscMsg.FlipACoin', 'SpeshulChoob', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (25, '8ball', 'MiscMsg.8Ball', 'SpeshulChoob', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (26, 'time', 'MiscMsg.Time', 'SpeshulChoob', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (27, 'date', 'MiscMsg.Date', 'SpeshulChoob', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (28, 'random', 'MiscMsg.Random', 'SpeshulChoob', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (29, 'quote', 'Quote.Create', 'SpeshulChoob', 1, NULL, 'Quote.Create');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (30, 'quoteme', 'Quote.Create action:$1', 'SpeshulChoob', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (31, 'quoten', 'Quote.Create', 'SpeshulChoob', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (32, 'getquote', 'Quote.Get', 'SpeshulChoob', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (33, 'quotecount', 'Quote.Count', 'SpeshulChoob', 1, NULL, 'Quote.Count');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (34, 'quotekarma', 'Quote.Info', 'SpeshulChoob', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (35, 'alias', 'Alias.add', 'SpeshulChoob', 1, NULL, 'Alias.add');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (36, 'showalias', 'Alias.Show', 'SpeshulChoob', 1, NULL, 'Alias.Show');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (38, 'whatis', 'Factoids.WhatIs', 'SpeshulChoob', 1, NULL, 'Factoids.Whatis');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (39, 'dance', 'MFJ.Dance', 'SpeshulChoob', 1, NULL, 'MFJ.Dance');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (40, 'help', 'Help.LongHelp', 'bucko', 1, NULL, 'Help.Help');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (41, 'plugins', 'Help.Plugins', 'SpeshulChoob', 1, NULL, 'Help.Plugins');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (42, 'lookup', 'Lookup.LookupIn', 'SpeshulChoob', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (43, 'mxlookup', 'Lookup.LookupIn $1 MX', 'SpeshulChoob', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (44, 'nslookup', 'Lookup.LookupIn', 'SpeshulChoob', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (45, 'featurerequest', 'MiscMsg.featurerequest', 'SpeshulChoob', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (46, 'bugreport', 'MiscMsg.bugreport', 'SpeshulChoob', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (47, 'unquote', 'quote.remove', 'SpeshulChoob', 1, NULL, 'Quote.Remove');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (49, 'reasondown', 'karma.reasondown', 'SpeshulChoob', 1, NULL, 'Karma.ReasonDown');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (50, 'aliaslist', 'alias.list', 'SpeshulChoob', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (51, 'events', 'events.list', 'icStatic', 1, NULL, 'Events.List');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (59, 'lastquote', 'quote.last', 'icStatic', 1, NULL, 'Quote.Last');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (60, 'urbandict', 'dict.dict urbandict', 'Faux', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (62, 'seen', 'seen.seen', 'fred', 1, NULL, 'Seen.Seen');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (64, 'leetquote', 'quote.karmamod up $1 $.', 'bucko', 1, 'Make a quote more leet. ||| [ <QuoteID> ] ||| <QuoteID> is the (optional) ID of a quote to make more leet (default: the most recent quote in context.)', NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (67, 'setkarma', 'karma.set', 'bucko', 1, NULL, 'Karma.Set');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (70, 'calc', 'Calc.Calc', 'bucko', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (72, 'dict', 'dict.dict', 'Faux', 0, NULL, 'Dict.Dict');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (73, 'callvote', 'vote.call', 'bucko', 1, NULL, 'Vote.Call');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (74, 'vote', 'vote.vote', 'bucko', 1, NULL, 'Vote.Vote');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (75, 'l33tquote', 'quote.karmamod up $1$.', 'bucko', 1, 'Make a quote more l33t. ||| [ <QuoteID> ] ||| <QuoteID> is the (optional) ID of a quote to make more l33t (default: the most recent quote in context.)', NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (80, 'randomquote', 'quote.get', 'Faux', 0, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (81, 'randomq', 'quote.get', 'Faux', 1, NULL, 'Quote.Get');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (82, 'acronym', 'dict.dict acronym', 'Faux|away', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (84, '1337quote', 'quote.karmamod up $1 $.', 'Polar', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (85, 'remind', 'TimedEvents.At 18:00 tell', 'bucko', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (88, 'dns', 'lookup.lookupin $1 A', 'bucko', 0, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (89, 'blockhelp', 'Help.BlockHelp', 'bucko', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (90, 'summary', 'Help.Summary', 'bucko', 1, NULL, 'Help.Summary');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (91, 'syntax', 'Help.Syntax', 'bucko', 1, NULL, 'Help.Syntax');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (92, 'longhelp', 'Help.LongHelp', 'bucko', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (94, 'whois', 'Factoids.WhatIs', 'Faux', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (95, 'signups', 'events.signups', 'Faux', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (96, 'info', 'quote.info', 'Blood_God', 0, NULL, 'Vote.Info');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (97, 'quoteinfo', 'quote.info', 'Skumby', 0, NULL, 'Quote.Info');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (98, 'aliasinfo', 'alias.info', 'Skumby', 0, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (99, 'quotesummary', 'Quote.Summary', 'bucko', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (100, 'fliapcoin', 'miscmsg.flipacoin', 'Blood_God', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (108, 'unalias', 'alias.remove', 'tim', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (109, 'set', 'Options.Set', 'bucko', 1, NULL, 'Options.Set');
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (116, 'metaquote', 'Quote.Get /quote/', 'bucko', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (119, 'day', 'MFJ.Day', 'bucko', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (120, 'month', 'MFJ.Month', 'bucko', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (121, 'year', 'MFJ.Year', 'bucko', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (122, 'century', 'talk.reply This is the 21st century.', 'Faux', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (123, 'shout', 'talk.shout', 'Faux', 0, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (128, 'reply', 'talk.reply', 'Faux', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (130, 'multiline', 'quote.get length:>1', 'Faux', 0, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (131, 'reverse', 'lookup.lookupin $1.in-addr.arpa. PTR', 'bucko', 0, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (132, 'singleline', 'quote.get length:=1', 'Faux', 0, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (137, 'removealias', 'alias.remove', 'Skumby|molly', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (139, 'reason', 'karma.reason', 'Faux', 0, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (144, 'whatbe', 'Factoids.WhatIs', 'Faux', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (145, 'tv', 'tv.search', 'Faux', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (150, 'topicdiff', 'Topic.Diff', 'bucko', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (151, 'wikipedia', 'dict.dict wikipedia', 'Faux', 0, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (153, 'silence', 'shutup.add', 'Blood_God', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (157, 'joinquotes', 'Quote.Count length:=1', 'Faux', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (158, 'karmahighscores', 'karma.highscores', 'Faux', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (159, 'karmahiscores', 'karma.highscores', 'Faux', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (160, 'karmascores', 'karma.highscores', 'Faux', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (162, 'resolve', 'Lookup.LookupIn', 'bucko', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (165, 'lock', 'alias.lock', 'icStatic', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (166, 'unlock', 'alias.unlock', 'Blood_God', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (168, 'sp', 'dict.spelt', 'Faux', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (169, 'karmaloscores', 'Karma.LowScores', 'Blood_God', 0, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (170, 'karmalowscore', 'Karma.LowScores', 'Blood_God', 0, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (171, 'karmalowscores', 'Karma.LowScores', 'Blood_God', 0, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (172, 'karmahighscore', 'Karma.HighScores', 'Blood_God', 0, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (175, 'wp', 'dict.dict wikipedia', 'Faux', 0, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (180, 'coin', 'MiscMsg.FlipACoin', 'Blood_God', 0, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (182, 'remember', 'Factoids.Remember', 'sadiq', 0, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (186, 'spell', 'dict.spelt', 'Faux', 0, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (191, 'welcome', 'Talk.Say $?1{Welcome, $[1-]}{$[nick]: You need to specify someone to welcome!}', 'bucko', 0, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (192, 'voteinfo', 'vote.info', 'Silver', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (270, 'bug', 'MiscMsg.bugreport', 'Blood_God', 0, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (277, 'signup', 'events.signup', 'Faux', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (282, 'aliaslock', 'alias.lock', 'Blood_God', 0, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (299, 'karmasearch', 'karma.search', 'Faux', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (304, 'event', 'events.info lan', 'Faux', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (305, 'rq', 'quote.get', 'drac|work', 1, NULL, NULL);
INSERT INTO `_objectdb_plugins_alias_aliasobject` VALUES (307, 'remindme', 'TimedEvents.At 18:00 tell $[nick]', 'tim', 1, NULL, NULL);

-- --------------------------------------------------------

--
-- Table structure for table `GroupMembers`
--

DROP TABLE IF EXISTS `GroupMembers`;
CREATE TABLE `GroupMembers` (
  `GroupID` int(11) unsigned default NULL,
  `MemberID` int(11) unsigned default NULL,
  UNIQUE KEY `GroupID` (`GroupID`,`MemberID`),
  KEY `MemberID` (`MemberID`),
  KEY `GroupID_2` (`GroupID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Dumping data for table `GroupMembers`
--

INSERT INTO `GroupMembers` VALUES (1, 4);
INSERT INTO `GroupMembers` VALUES (1, 5);
INSERT INTO `GroupMembers` VALUES (1, 6);
INSERT INTO `GroupMembers` VALUES (1, 7);
INSERT INTO `GroupMembers` VALUES (1, 8);
INSERT INTO `GroupMembers` VALUES (1, 9);
INSERT INTO `GroupMembers` VALUES (1, 10);
INSERT INTO `GroupMembers` VALUES (1, 11);
INSERT INTO `GroupMembers` VALUES (1, 12);
INSERT INTO `GroupMembers` VALUES (1, 13);
INSERT INTO `GroupMembers` VALUES (1, 14);
INSERT INTO `GroupMembers` VALUES (1, 15);
INSERT INTO `GroupMembers` VALUES (1, 16);
INSERT INTO `GroupMembers` VALUES (1, 17);
INSERT INTO `GroupMembers` VALUES (1, 19);
INSERT INTO `GroupMembers` VALUES (1, 22);
INSERT INTO `GroupMembers` VALUES (1, 24);
INSERT INTO `GroupMembers` VALUES (1, 25);
INSERT INTO `GroupMembers` VALUES (1, 28);
INSERT INTO `GroupMembers` VALUES (1, 34);
INSERT INTO `GroupMembers` VALUES (1, 37);
INSERT INTO `GroupMembers` VALUES (1, 46);
INSERT INTO `GroupMembers` VALUES (1, 49);
INSERT INTO `GroupMembers` VALUES (1, 72);
INSERT INTO `GroupMembers` VALUES (4, 3);
INSERT INTO `GroupMembers` VALUES (4, 47);
INSERT INTO `GroupMembers` VALUES (28, 27);
INSERT INTO `GroupMembers` VALUES (32, 31);
INSERT INTO `GroupMembers` VALUES (36, 35);
INSERT INTO `GroupMembers` VALUES (39, 38);
INSERT INTO `GroupMembers` VALUES (39, 40);
INSERT INTO `GroupMembers` VALUES (39, 41);
INSERT INTO `GroupMembers` VALUES (43, 42);
INSERT INTO `GroupMembers` VALUES (45, 44);
INSERT INTO `GroupMembers` VALUES (49, 48);
INSERT INTO `GroupMembers` VALUES (51, 50);
INSERT INTO `GroupMembers` VALUES (57, 56);
INSERT INTO `GroupMembers` VALUES (62, 61);
INSERT INTO `GroupMembers` VALUES (65, 64);
INSERT INTO `GroupMembers` VALUES (67, 66);
INSERT INTO `GroupMembers` VALUES (69, 68);
INSERT INTO `GroupMembers` VALUES (71, 70);

-- --------------------------------------------------------

--
-- Table structure for table `History`
--

DROP TABLE IF EXISTS `History`;
CREATE TABLE `History` (
  `LineID` int(11) unsigned NOT NULL auto_increment,
  `Type` varchar(50) NOT NULL default '',
  `Nick` varchar(64) NOT NULL default '',
  `Hostmask` varchar(128) NOT NULL default '',
  `Channel` varchar(32) default NULL,
  `Text` text NOT NULL,
  `Time` bigint(20) NOT NULL default '0',
  `Random` tinyint(4) NOT NULL default '0',
  PRIMARY KEY  (`LineID`),
  KEY `Nick` (`Nick`),
  KEY `Channel` (`Channel`),
  KEY `Time` (`Time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 AUTO_INCREMENT=39 ;

--
-- Dumping data for table `History`
--

-- --------------------------------------------------------


--
-- Table structure for table `LoadedPlugins`
--

DROP TABLE IF EXISTS `LoadedPlugins`;
CREATE TABLE `LoadedPlugins` (
  `Name` varchar(255) NOT NULL default '',
  `Source` longtext NOT NULL,
  PRIMARY KEY  (`Name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Dumping data for table `LoadedPlugins`
--


-- --------------------------------------------------------

--
-- Table structure for table `UserNodePermissions`
--

DROP TABLE IF EXISTS `UserNodePermissions`;
CREATE TABLE `UserNodePermissions` (
  `NodeID` int(11) unsigned NOT NULL default '0',
  `Type` varchar(50) NOT NULL default '',
  `Permission` varchar(80) NOT NULL default '',
  `Action` varchar(30) NOT NULL default '',
  UNIQUE KEY `NodeID_2` (`NodeID`,`Type`,`Permission`,`Action`),
  KEY `NodeID` (`NodeID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='Table storing plugin permissions';

--
-- Dumping data for table `UserNodePermissions`
--

INSERT INTO `UserNodePermissions` VALUES (1, 'java.security.AllPermission', '', '');
INSERT INTO `UserNodePermissions` VALUES (5, 'choob', 'plugin.load.*', '');
INSERT INTO `UserNodePermissions` VALUES (18, 'org.uwcs.choob.support.ChoobPermission', 'state.join.*', '');
INSERT INTO `UserNodePermissions` VALUES (18, 'org.uwcs.choob.support.ChoobPermission', 'state.part.*', '');
INSERT INTO `UserNodePermissions` VALUES (18, 'uk.co.uwcs.choob.support.ChoobPermission', 'state.join.#bots', '');
INSERT INTO `UserNodePermissions` VALUES (18, 'uk.co.uwcs.choob.support.ChoobPermission', 'state.join.#choob', '');
INSERT INTO `UserNodePermissions` VALUES (18, 'uk.co.uwcs.choob.support.ChoobPermission', 'state.join.#pogochoob', '');
INSERT INTO `UserNodePermissions` VALUES (19, 'java.net.SocketPermission', 'faux.uwcs.co.uk:80', 'connect,resolve');
INSERT INTO `UserNodePermissions` VALUES (26, 'java.security.AllPermission', '', '');
INSERT INTO `UserNodePermissions` VALUES (36, 'uk.co.uwcs.choob.support.ChoobPermission', 'plugin.load.Amorya', '');
INSERT INTO `UserNodePermissions` VALUES (51, 'uk.co.uwcs.choob.support.ChoobPermission', 'plugin.load.tim', '');
INSERT INTO `UserNodePermissions` VALUES (57, 'uk.co.uwcs.choob.support.ChoobPermission', 'plugin.detach.si1entdave', '');
INSERT INTO `UserNodePermissions` VALUES (57, 'uk.co.uwcs.choob.support.ChoobPermission', 'plugin.detach.survey', '');
INSERT INTO `UserNodePermissions` VALUES (57, 'uk.co.uwcs.choob.support.ChoobPermission', 'plugin.load.si1entdave', '');
INSERT INTO `UserNodePermissions` VALUES (57, 'uk.co.uwcs.choob.support.ChoobPermission', 'plugin.load.survey', '');
INSERT INTO `UserNodePermissions` VALUES (57, 'uk.co.uwcs.choob.support.ChoobPermission', 'plugin.unload.si1entdave', '');
INSERT INTO `UserNodePermissions` VALUES (57, 'uk.co.uwcs.choob.support.ChoobPermission', 'plugin.unload.survey', '');
INSERT INTO `UserNodePermissions` VALUES (63, 'java.security.AllPermission', '', '');
INSERT INTO `UserNodePermissions` VALUES (74, 'java.security.AllPermission', '', '');
INSERT INTO `UserNodePermissions` VALUES (75, 'java.security.AllPermission', '', '');

-- --------------------------------------------------------

--
-- Table structure for table `UserNodes`
--

DROP TABLE IF EXISTS `UserNodes`;
CREATE TABLE `UserNodes` (
  `NodeID` int(11) unsigned NOT NULL auto_increment,
  `NodeName` varchar(32) NOT NULL default '',
  `NodeClass` tinyint(3) unsigned NOT NULL default '0',
  PRIMARY KEY  (`NodeID`),
  UNIQUE KEY `NodeName` (`NodeName`,`NodeClass`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 AUTO_INCREMENT=77 ;

--
-- Dumping data for table `UserNodes`
--

INSERT INTO `UserNodes` VALUES (76, 'admin', 2);
INSERT INTO `UserNodes` VALUES (15, 'Alias', 2);
INSERT INTO `UserNodes` VALUES (35, 'amorya', 0);
INSERT INTO `UserNodes` VALUES (36, 'amorya', 1);
INSERT INTO `UserNodes` VALUES (2, 'anonymous', 3);
INSERT INTO `UserNodes` VALUES (31, 'benji|tov', 0);
INSERT INTO `UserNodes` VALUES (32, 'benji|tov', 1);
INSERT INTO `UserNodes` VALUES (27, 'bucko', 0);
INSERT INTO `UserNodes` VALUES (28, 'bucko', 1);
INSERT INTO `UserNodes` VALUES (55, 'bucko', 2);
INSERT INTO `UserNodes` VALUES (60, 'calc', 2);
INSERT INTO `UserNodes` VALUES (44, 'Crazy_Piglet', 0);
INSERT INTO `UserNodes` VALUES (45, 'Crazy_Piglet', 1);
INSERT INTO `UserNodes` VALUES (42, 'db_rat', 0);
INSERT INTO `UserNodes` VALUES (43, 'db_rat', 1);
INSERT INTO `UserNodes` VALUES (26, 'dict', 2);
INSERT INTO `UserNodes` VALUES (19, 'Events', 2);
INSERT INTO `UserNodes` VALUES (20, 'Factoids', 2);
INSERT INTO `UserNodes` VALUES (3, 'Faux', 0);
INSERT INTO `UserNodes` VALUES (4, 'Faux', 1);
INSERT INTO `UserNodes` VALUES (47, 'Faux|tov', 0);
INSERT INTO `UserNodes` VALUES (59, 'flood', 2);
INSERT INTO `UserNodes` VALUES (9, 'Help', 2);
INSERT INTO `UserNodes` VALUES (25, 'http', 2);
INSERT INTO `UserNodes` VALUES (66, 'jonatan', 0);
INSERT INTO `UserNodes` VALUES (67, 'jonatan', 1);
INSERT INTO `UserNodes` VALUES (16, 'Karma', 2);
INSERT INTO `UserNodes` VALUES (21, 'Lectures', 2);
INSERT INTO `UserNodes` VALUES (54, 'llama', 2);
INSERT INTO `UserNodes` VALUES (22, 'Lookup', 2);
INSERT INTO `UserNodes` VALUES (23, 'MFJ', 2);
INSERT INTO `UserNodes` VALUES (13, 'MiscMsg', 2);
INSERT INTO `UserNodes` VALUES (41, 'murphster', 0);
INSERT INTO `UserNodes` VALUES (40, 'murphy', 0);
INSERT INTO `UserNodes` VALUES (38, 'murphybob', 0);
INSERT INTO `UserNodes` VALUES (39, 'murphybob', 1);
INSERT INTO `UserNodes` VALUES (8, 'NickServ', 2);
INSERT INTO `UserNodes` VALUES (33, 'option', 2);
INSERT INTO `UserNodes` VALUES (34, 'options', 2);
INSERT INTO `UserNodes` VALUES (37, 'passthru', 2);
INSERT INTO `UserNodes` VALUES (6, 'Plugin', 2);
INSERT INTO `UserNodes` VALUES (5, 'plugindevs', 3);
INSERT INTO `UserNodes` VALUES (74, 'pogo', 2);
INSERT INTO `UserNodes` VALUES (68, 'Polar', 0);
INSERT INTO `UserNodes` VALUES (69, 'Polar', 1);
INSERT INTO `UserNodes` VALUES (17, 'Quote', 2);
INSERT INTO `UserNodes` VALUES (1, 'root', 3);
INSERT INTO `UserNodes` VALUES (48, 'sadiq', 0);
INSERT INTO `UserNodes` VALUES (49, 'sadiq', 1);
INSERT INTO `UserNodes` VALUES (7, 'Security', 2);
INSERT INTO `UserNodes` VALUES (75, 'see', 2);
INSERT INTO `UserNodes` VALUES (10, 'Seen', 2);
INSERT INTO `UserNodes` VALUES (24, 'Shutup', 2);
INSERT INTO `UserNodes` VALUES (56, 'Si1entDave', 0);
INSERT INTO `UserNodes` VALUES (57, 'Si1entDave', 1);
INSERT INTO `UserNodes` VALUES (70, 'Sudilos', 0);
INSERT INTO `UserNodes` VALUES (71, 'Sudilos', 1);
INSERT INTO `UserNodes` VALUES (64, 'superdump', 0);
INSERT INTO `UserNodes` VALUES (65, 'superdump', 1);
INSERT INTO `UserNodes` VALUES (58, 'survey', 2);
INSERT INTO `UserNodes` VALUES (11, 'Talk', 2);
INSERT INTO `UserNodes` VALUES (14, 'Tell', 2);
INSERT INTO `UserNodes` VALUES (18, 'Test', 2);
INSERT INTO `UserNodes` VALUES (50, 'tim', 0);
INSERT INTO `UserNodes` VALUES (51, 'tim', 1);
INSERT INTO `UserNodes` VALUES (52, 'tim', 2);
INSERT INTO `UserNodes` VALUES (53, 'time', 2);
INSERT INTO `UserNodes` VALUES (12, 'TimedEvents', 2);
INSERT INTO `UserNodes` VALUES (73, 'topic', 2);
INSERT INTO `UserNodes` VALUES (63, 'tv', 2);
INSERT INTO `UserNodes` VALUES (72, 'usertypecheck', 2);
INSERT INTO `UserNodes` VALUES (46, 'vote', 2);
INSERT INTO `UserNodes` VALUES (61, 'zx64', 0);
INSERT INTO `UserNodes` VALUES (62, 'zx64', 1);

--
-- Constraints for dumped tables
--

--
-- Constraints for table `GroupMembers`
--
ALTER TABLE `GroupMembers`
  ADD CONSTRAINT `GroupMembers_ibfk_1` FOREIGN KEY (`GroupID`) REFERENCES `UserNodes` (`NodeID`),
  ADD CONSTRAINT `GroupMembers_ibfk_2` FOREIGN KEY (`MemberID`) REFERENCES `UserNodes` (`NodeID`);

--
-- Constraints for table `UserNodePermissions`
--
ALTER TABLE `UserNodePermissions`
  ADD CONSTRAINT `UserNodePermissions_ibfk_1` FOREIGN KEY (`NodeID`) REFERENCES `UserNodes` (`NodeID`);
