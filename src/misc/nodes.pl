#!/usr/bin/perl

use strict;
use warnings;
use CGI::Carp;

my $web = (exists $ENV{GATEWAY_INTERFACE}) && ($ENV{GATEWAY_INTERFACE} =~ /^CGI/);

$SIG{__WARN__} = sub {
	my ($msg) = @_;
	if ($web) {
		print "<DIV CLASS='warning'><B>Perl Warning</B> $msg</DIV>\n";
		print "<!--\nPERL WARNING:\n" . Carp::longmess($msg) . " -->\n";
	} else {
		print "PERL WARNING: $msg\n";
		print Carp::longmess($msg) . "\n";
	}
};
$SIG{__DIE__} = sub {
	my ($msg) = @_;
	if ($web) {
		print "<DIV CLASS='error'><B>Perl Fatal Error</B> $msg</DIV>\n";
		print "<!--\nPERL ERROR:\n" . Carp::longmess($msg) . " -->\n";
	} else {
		print "PERL FATAL ERROR: $msg\n";
		print  Carp::longmess($msg) . "\n";
	}
};

if ($web) {
	print "Content-Type: text/html\015\012";
	print "\015\012";
}

my ($dbhost, $dbuser, $dbpass) = ("", "", "");
if (open(CFG, "bot.conf") || open(CFG, "../bot.conf")) {
	my @conf = <CFG>;
	close(CFG);
	
	foreach my $conf (@conf) {
		if ($conf =~ /^dbHost=(.*?)[\r\n]+$/) {
			$dbhost = $1;
		} elsif ($conf =~ /^dbUser=(.*?)[\r\n]+$/) {
			$dbuser = $1;
		} elsif ($conf =~ /^dbPass=(.*?)[\r\n]+$/) {
			$dbpass = $1;
		}
	}
} else {
	die "Unable to open bot.conf file! It must be in the current or parent directory to this script. Error: '$!'";
}

use DBI;
my $dbh = DBI->connect("DBI:mysql:database=choob;host=$dbhost;port=3306", $dbuser, $dbpass, { RaiseError => 1});

sub getData() {
	my ($sql, $params) = @_;
	
	my $sth = $dbh->prepare($sql);
	$sth->execute(@{$params});
	my $data;
	if ($sql =~ /^\s*SELECT\s+/i) {
		$data = $sth->fetchall_arrayref();
	}
	return $data;
}

my $nodes = &getData("SELECT NodeID, NodeName, NodeClass FROM UserNodes ORDER BY NodeID", []);
my $perms = &getData("SELECT NodeID, Type, Permission, Action FROM UserNodePermissions ORDER BY NodeID", []);
my $groups = &getData("SELECT GroupID, MemberID FROM GroupMembers ORDER BY GroupID, MemberID", []);

my @nodeClass = ("User", "User group", "Plugin group", "System group");
my @specialClass = ("", "Top of heirachy", "Permission");
my %permMap = (
		"java.security.AllPermission"            => "All",
		"uk.co.uwcs.choob.support.ChoobPermission" => "Choob",
		"java.lang.RuntimePermission"            => "Runtime",
		"java.security.SecurityPermission"       => "Security",
		"java.lang.reflect.ReflectPermission"    => "Reflect",
		"java.io.FilePermission"                 => "File",
		"java.net.SocketPermission"              => "Socket",
		"java.util.PropertyPermission"           => "Property"
	);

$dbh->disconnect();

sub buildTreeFrom() {
	my ($treenode) = @_;
	if ($treenode->{id} < 0) {
		return;
	}
	
	$treenode->{name} = "";
	$treenode->{class} = 0;
	$treenode->{contents} = [];
	
	if ($treenode->{id} == 0) {
		my %ids = ();
		$treenode->{name} = "<top>";
		$treenode->{class} = -1;
		
		foreach my $node (@{$nodes}) {
			$ids{$node->[0]} = 1;
		}
		foreach my $node (@{$groups}) {
			if (exists $ids{$node->[1]}) {
				delete $ids{$node->[1]};
			}
		}
		
		$treenode->{contents} = [map { { id => $_ } } sort keys %ids];
		
	} else {
		foreach my $node (@{$nodes}) {
			if ($node->[0] == $treenode->{id}) {
				$treenode->{name} = $node->[1];
				$treenode->{class} = $node->[2];
				last;
			}
		}
		
		foreach my $node (@{$perms}) {
			if ($node->[0] == $treenode->{id}) {
				my $name = $node->[1];
				if (exists $permMap{$name}) {
					$name = $permMap{$name};
				}
				push @{$treenode->{contents}}, { id => -1, name => $name, class => -2, contents => [], perm => $node->[2], action => $node->[3] };
			}
		}
		
		foreach my $node (@{$groups}) {
			if ($node->[0] == $treenode->{id}) {
				push @{$treenode->{contents}}, { id => $node->[1] };
			}
		}
	}
	
	foreach my $child (@{$treenode->{contents}}) {
		&buildTreeFrom($child);
	}
}

sub showTree() {
	my ($treenode, $indent, $indent2) = @_;
	
	if ($web) {
		&showTreeWeb($treenode);
	} else {
		&showTreeText($treenode, $indent, $indent2);
	}
}

sub showTreeWeb() {
	my ($treenode) = @_;
	
	print "<LI>";
	print $treenode->{name};
	if ($treenode->{id} > 0) {
		print " [" . $treenode->{id} . "]";
	}
	if ($treenode->{class} == -2) {
		if ($treenode->{perm}) {
			print " " . $treenode->{perm};
			if ($treenode->{action}) {
				print " " . $treenode->{action};
			}
		}
	}
	print " <SMALL><I> - ";
	if ($treenode->{class} < 0) {
		print $specialClass[-$treenode->{class}];
	} else {
		print $nodeClass[$treenode->{class}];
	}
	print "</I></SMALL>";
	
	print "<UL>\n";
	foreach my $child (@{$treenode->{contents}}) {
		&showTreeWeb($child);
	}
	print "</UL>\n";
	
	print "</LI>\n";
}

sub showTreeText() {
	my ($treenode, $indent, $indent2) = @_;
	
	print $indent;
	print " +- ";
	print $treenode->{name};
	if ($treenode->{id} > 0) {
		print " [" . $treenode->{id} . "]";
	}
	if ($treenode->{class} == -2) {
		if ($treenode->{perm}) {
			print " " . $treenode->{perm};
			if ($treenode->{action}) {
				print " " . $treenode->{action};
			}
		}
	}
	print " - ";
	if ($treenode->{class} < 0) {
		print $specialClass[-$treenode->{class}];
	} else {
		print $nodeClass[$treenode->{class}];
	}
	print "\n";
	
	$indent .= $indent2;
	for (my $i = 0; $i < @{$treenode->{contents}}; $i++) {
		my $child = ${$treenode->{contents}}[$i];
		if ($i < @{$treenode->{contents}}-1) {
			&showTreeText($child, $indent, " |  ");
		} else {
			&showTreeText($child, $indent, "    ");
		}
	}
}

if ($web) {
	print <<HTML;
<HTML>
<HEAD>
	<TITLE>Choob Nodes</TITLE>
</HEAD>
<BODY>
	<H1>Choob Nodes</H1>
HTML
}

my $tree = { id => 0 };

&buildTreeFrom($tree);

if ($web) {
	print "<UL>\n";
}
&showTree($tree, "", "    ");
if ($web) {
	print "</UL>\n";
}
