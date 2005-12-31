#!/usr/bin/perl

use warnings;
use strict;
use CGI::Carp;

$SIG{__WARN__} = sub {
	my ($msg) = @_;
	print "PERL WARNING: $msg\n";
	print Carp::longmess($msg) . "\n";
};
$SIG{__DIE__} = sub {
	my ($msg) = @_;
	print "PERL FATAL ERROR: $msg\n";
	print  Carp::longmess($msg) . "\n";
};

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
	print "$!\n";
	die "Unable to open bot.conf file! It must be in the current or parent directory to this script.";
}

use DBI;
my $dbh = DBI->connect("DBI:mysql:database=choob;host=$dbhost;port=3306", $dbuser, $dbpass, { RaiseError => 1});

my %params = ();
foreach my $item (@ARGV) {
	if ($item =~ /^--(class|sort|format|columns)=(.*)$/) {
		$params{$1} = $2;
	} elsif ($item =~ /^--(help)$/) {
		$params{$1} = 1;
	} else {
		print STDERR qq[Ignored unknown option: $item\n];
	}
}

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

if ($params{help}) {
	print <<HELP;
Displays the objects in the bot's ObjectDB store.

objects.pl [ --help | [ --class=CLASS [ --sort=SORT ] [ --columns=COLS ] ] ]

 --help     Shows this message.
 --class    Displays all the objects of a single class. Without this, a list
            of known classes is displayed instead.
 --sort     Sorts the list by something other than "id".
 --columns  Limits what columns are displays.
 CLASS      Either a full-qualified class name, or just the final component.
 SORT       Controls the sort of the objects, and follows the format:
              FIELD/CMP/DIR
            Where:
              FIELD  Specifies the object property to sort by.
              CMP    Either "s" or "n" to indicate string or numeric sorting.
              DIR    Either "a" or "d" for ascending/decending sorting.
 COLS       A comma-separated list of column names to display.
HELP
	exit;
}

unless ($params{class}) {
	my $objectClasses = &getData("SELECT DISTINCT ClassName FROM ObjectStore", []);
	
	print qq[Classes:\n];
	foreach my $class (@{$objectClasses}) {
		my $className = $class->[0];
		if ($className =~ /\.([^.]+)$/) {
			$className = $1;
		}
		print qq[  $class->[0] ($className)\n];
	}
	
} else {
	my $className = $params{class};
	my $format = $params{format} || "text";
	
	if ($className !~ /\./) {
		my $objectClasses = &getData("SELECT DISTINCT ClassName FROM ObjectStore", []);
		
		my $re = quotemeta $className;
		$re = qr/\.$re$/;
		foreach my $class (@{$objectClasses}) {
			if ($class->[0] =~ $re) {
				$className = $class->[0];
				last;
			}
		}
	}
	
	if ($format eq 'text') {
		print qq[Class: $className\n];
	} elsif ($format eq 'xml') {
		print <<XML;
<?xml version="1.0" standalone="yes"?>
XML
	}
	my $objectData = &getData("SELECT ObjectID, FieldName, FieldBigInt, FieldDouble, FieldString FROM ObjectStoreData WHERE ObjectID IN (SELECT ObjectID FROM ObjectStore WHERE ClassName = ?)", [$className]);
	
	my %objects = ();
	my @columns = ("id");
	my %columns = ( id => "int" );
	my %columnLens = ( id => 0 );
	my $itemMissing = "MISSING";
	
	if ($params{columns} && (",$params{columns}," !~ /,id,/)) {
		pop @columns;
		delete $columns{id};
		delete $columnLens{id};
	}
	
	foreach my $objectItem (@{$objectData}) {
		unless (exists $objects{$objectItem->[0]}) {
			$objects{$objectItem->[0]} = { id => $objectItem->[0] };
			if ((exists $columnLens{id}) && (length($objectItem->[0]) > $columnLens{id})) {
				$columnLens{id} = length($objectItem->[0]);
			}
		}
		unless (exists $columns{$objectItem->[1]}) {
			my $type = "unknown";
			if ((defined $objectItem->[2]) && (defined $objectItem->[3]) &&  (defined $objectItem->[4])) {
				if (($objectItem->[2] != 0) || ($objectItem->[4] eq '0')) {
					$type = "int";
				} elsif ($objectItem->[3] != 0) {
					$type = "double";
				} elsif ($objectItem->[4]) {
					$type = "string";
				}
			}
			$columns{$objectItem->[1]} = $type;
			if ($params{columns} && (",$params{columns}," !~ /,$objectItem->[1],/)) {
				next;
			}
			push @columns, $objectItem->[1];
		}
		
		# Update column widths:
		my $len = (defined $objectItem->[4] ? length($objectItem->[4]) : length($itemMissing));
		my $collen = 0;
		if (exists $columnLens{$objectItem->[1]}) {
			$collen = $columnLens{$objectItem->[1]};
		}
		if ($len > $collen) {
			$collen = $len;
		}
		$columnLens{$objectItem->[1]} = $collen;
		
		# Save object value:
		$objects{$objectItem->[0]}{$objectItem->[1]} = $objectItem->[4];
	}
	
	my $sortCol = "id";
	my $sortCmp = "n"; # n = numeric, s = string
	my $sortDir = "a"; # a = ascending, d = descending
	
	if ((exists $params{"sort"}) && ($params{"sort"} =~ /^(.*)\/([ns])\/([ad])$/)) {
		$sortCol = $1;
		$sortCmp = $2;
		$sortDir = $3;
	}
	
	my @objKeys;
	if ($sortCol) {
		if ($sortCmp eq 'n') {
			if ($sortDir eq 'a') {
				@objKeys = sort { $objects{$a}{$sortCol} <=> $objects{$b}{$sortCol} } keys %objects;
			} else {
				@objKeys = sort { $objects{$b}{$sortCol} <=> $objects{$a}{$sortCol} } keys %objects;
			}
		} else {
			if ($sortDir eq 'a') {
				@objKeys = sort { lc($objects{$a}{$sortCol}) cmp lc($objects{$b}{$sortCol}) } keys %objects;
			} else {
				@objKeys = sort { lc($objects{$b}{$sortCol}) cmp lc($objects{$a}{$sortCol}) } keys %objects;
			}
		}
	} else {
		@objKeys = sort { $a <=> $b } keys %objects;
	}
	
	if ($format eq 'text') {
		print qq[Sort : $sortCol, ] . ($sortDir eq 'a' ? "ascending" : "descending") . qq[.\n];
		print qq[\n];
		
	} elsif ($format eq 'xml') {
		shift @columns;
		print <<XML;
<objects class="$className">
XML
	}
	my $rowSep = "";
	if ($format eq 'text') {
		foreach my $col (@columns) {
			if (length($col) > $columnLens{$col}) {
				$columnLens{$col} = length($col);
			}
		}
		foreach my $col (@columns) {
			$rowSep .= qq[+] . ("-" x ($columnLens{$col} + 2));
		}
		$rowSep .= qq[+\n];
		
		print $rowSep;
		foreach my $col (@columns) {
			print qq[| $col] . (" " x ($columnLens{$col} - length($col))) . qq[ ];
		}
		print qq[|\n];
		print $rowSep;
	}
	foreach my $key (@objKeys) {
		if ($format eq 'text') {
			foreach my $col (@columns) {
				my $val = $itemMissing;
				if (defined $objects{$key}{$col}) {
					$val = $objects{$key}{$col};
				}
				print qq[| $val] . (" " x ($columnLens{$col} - length($val))) . qq[ ];
			}
			print qq[|\n];
			print $rowSep;
		} elsif ($format eq 'xml') {
			print qq[	<object id="$key">\n];
			foreach my $col (@columns) {
				if ($columns{$col} eq 'unknown') {
					print qq[		<field name="$col" type="$columns{$col}"/>\n];
				} else {
					print qq[		<field name="$col" type="$columns{$col}">] . $objects{$key}{$col} . qq[</field>\n];
				}
			}
			print qq[	</object>\n];
		}
	}
	if ($format eq 'xml') {
		print <<XML;
</objects>
XML
		exit;
	}
	
}

$dbh->disconnect();

sub unescape() {
	my $item = shift;
	$item =~ s/\+/ /g;
	$item =~ s/%([0-9A-F][0-9A-F])/sprintf('%c', hex($1))/gei;
	return $item;
}
