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
	if ($item =~ /^-?-(class|sort|format|columns)=(.*)$/) {
		$params{$1} = $2;
	} elsif ($item =~ /^-?-(help|m)$/) {
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
	if ($sql =~ /^\s*(?:SHOW|DESCRIBE|SELECT)\s+/i) {
		$data = $sth->fetchall_arrayref();
	}
	return $data;
}

sub getClassList() {
	my @rv;
	
	if ($params{m}) {
		my $tables = &getData("SHOW TABLES", []);
		foreach my $table (@{$tables}) {
			next unless ($table->[0] =~ /^_objectdb_(.*)/);
			my $name = $1;
			$name =~ s/_/./g;
			my $className = $name;
			if ($className =~ /\.([^.]+)$/) {
				$className = $1;
			}
			push @rv, { name => $name, short => $className };
		}
	} else {
		my $objectClasses = &getData("SELECT DISTINCT ClassName FROM ObjectStore", []);
		foreach my $class (@{$objectClasses}) {
			my $className = $class->[0];
			if ($className =~ /\.([^.]+)$/) {
				$className = $1;
			}
			push @rv, { name => $class->[0], short => $className };
		}
	}
	
	return @rv;
}

if ($params{help}) {
	print <<HELP;
Displays the objects in the bot's ObjectDB store.

objects.pl [--help | [[-m] --class=CLASS [--sort=SORT] [--columns=COLS]]]

 --help     Shows this message.
 -m         Selects "multi-table" mode over the default, "single-table".
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
	print qq[Classes:\n];
	
	my @classes = &getClassList();
	foreach my $class (@classes) {
		print qq[  $class->{name} ($class->{short})\n];
	}
	
} else {
	my $className = $params{class};
	my $format = $params{format} || "text";
	
	if ($className !~ /\./) {
		my $re = quotemeta $className;
		$re = qr/\.$re$/;
		
		my @classes = &getClassList();
		foreach my $class (@classes) {
			if ($class->{name} =~ $re) {
				$className = $class->{name};
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
	
	if ($params{m}) {
		my $table = $className;
		$table =~ s/\./_/g;
		$table = "_objectdb_$table";
		my $colData = &getData("DESCRIBE ?", [$table]);
		
		foreach my $col (@{$colData}) {
			my $colName = $col->[0];
			# Skip if it is already defined, or we're not supposed to list it.
			next if (exists $columns{$colName});
			if ($params{columns} && (",$params{columns}," !~ /,$colName,/)) {
				next;
			}
			
			my $colType = "unknown";
			if ($col->[1] =~ /^test$/) {
				$colType = "string";
			} elsif ($col->[1] =~ /^(?:tiny|big)?int\(\d+\)$/) {
				$colType = "int";
			} else {
				print STDERR qq[WARNING: Unknown property type: $col->[1].\n];
			}
			
			$columns{$colName} = $colType;
			$columnLens{$colName} = length($colName);
			push @columns, $colName;
		}
		
		my $objectData = &getData("SELECT id, `" . join("`, `", @columns) . " FROM ?", [$table]);
		
		foreach my $objectItem (@{$objectData}) {
			unless (exists $objects{$objectItem->[0]}) {
				$objects{$objectItem->[0]} = { id => $objectItem->[0] };
			}
			
			for (my $i = 0; $i < @columns; $i++) {
				# Update column widths:
				my $len = length($objectItem->[$i + 1]);
				my $collen = 0;
				if (exists $columnLens{$columns[$i]}) {
					$collen = $columnLens{$columns[$i]};
				}
				if ($len > $collen) {
					$collen = $len;
				}
				$columnLens{$columns[$i]} = $collen;
				
				# Save object value:
				$objects{$objectItem->[0]}{$columns[$i]} = $objectItem->[$i + 1];
			}
		}
		
	} else {
		my $objectData = &getData("SELECT ObjectID, FieldName, FieldBigInt, FieldDouble, FieldString FROM ObjectStoreData WHERE ObjectID IN (SELECT ObjectID FROM ObjectStore WHERE ClassName = ?)", [$className]);
		
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
