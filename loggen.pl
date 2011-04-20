#!/usr/bin/perl -w

use strict;
use warnings;
use DBI;
use POSIX qw/strftime/;

die("usage: echo password | $0 channel_filter_expression [days of data]\n" .
	"e.g. echo ponies | $0 \"IN ('#compsoc','#work')\" 14") if ($#ARGV == -1);

chomp (my $pw = <STDIN>);

my $dbh = DBI->connect('dbi:mysql:choob', 'choob', $pw, {
    PrintError => 0,   ### Don't report errors via warn(  )
    RaiseError => 1    ### Do report errors via die(  )
});

### Prepare a SQL statement for execution
my $sth = $dbh->prepare('SELECT `Time`, `Type`, `Nick`, `Text` FROM `History`
	WHERE `Channel` ' . $ARGV[0] . ' AND `Nick` NOT LIKE "Guest%" AND `Text` NOT LIKE "!%"
	' . ($#ARGV > 0 && $ARGV[1] > 0 ? 'AND ((UNIX_TIMESTAMP()-(`Time`/1000)) < (60*60*24*' . $ARGV[1] . '))' : '') . '
	ORDER BY `Time`;');

### Execute the statement in the database
$sth->execute();

### Retrieve the returned rows of data
while (my ($time, $type, $nick, $text) = $sth->fetchrow_array()) {
	$time = strftime('%H:%M:%S', gmtime($time / 1000));
	$nick = nicklink($nick);
	if ($type =~ /ChannelKick$/) {
		printf "%s -!- somebody was kicked from #compsoc by %s [%s]\n", $time, $nick, $text;
	} elsif ($type =~ /ChannelAction$/) {
		printf "%s * %s %s\n", $time, $nick, $text;
	} else {
		printf "%s <%s> %s\n", $time, $nick, $text;
	}
}

### Disconnect from the database
$dbh->disconnect();

### Format nicknames by trimming '|extra' off
sub nicklink {
	$_[0] =~ s/\|.*$//;
	return $_[0];
}
