#!/usr/bin/perl

use warnings;
use strict;
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

my %params = ();
if ($web) {
	foreach my $item (split(";", $ENV{QUERY_STRING})) {
		if ($item =~ /^(class|sort|format|columns)=(.*)$/) {
			$params{$1} = $2;
		} elsif ($item =~ /^(help)$/) {
			$params{$1} = 1;
		} else {
			die "Unknown option: $item";
		}
	}
} else {
	foreach my $item (@ARGV) {
		if ($item =~ /^-?-(class|sort|format|columns)=(.*)$/) {
			$params{$1} = $2;
		} elsif ($item =~ /^-?-(help)$/) {
			$params{$1} = 1;
		} else {
			print STDERR qq[Unknown option: $item\n];
			exit 1;
		}
	}
}

my $format = uc($params{format}) || ($web ? "HTML" : "TEXT");

if ($web) {
	if ($format eq 'HTML') {
		print "Content-Type: text/html\015\012";
	} elsif ($format eq 'XML') {
		print "Content-Type: application/xml\015\012";
	}
	print "\015\012";
}

my ($dbhost, $dbname, $dbuser, $dbpass) = ("", "choob", "", "");
if (open(CFG, "bot.conf") || open(CFG, "../bot.conf")) {
	my @conf = <CFG>;
	close(CFG);
	
	foreach my $conf (@conf) {
		if ($conf =~ /^dbServer=(.*?)[\r\n]+$/) {
			$dbhost = $1;
		} elsif ($conf =~ /^database=(.*?)[\r\n]+$/) {
			$dbname = $1;
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
my $dbh = DBI->connect("DBI:mysql:database=$dbname;host=$dbhost;port=3306", $dbuser, $dbpass, { RaiseError => 1});

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

sub isMultiTable() {
	my $tables = &getData("SHOW TABLES", []);
	if (grep { $_->[0] =~ /^_objectdb_/ } @{$tables}) {
		return 1;
	}
	return 0;
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

sub showHelp() {
	print qq[<PRE>\n] if ($web);
	print <<HELP;
Displays the objects in the bot's ObjectDB store.

objects.pl [--help | [--class=CLASS [--sort=SORT] [--columns=COLS] [--format=FMT]]]

 --help     Shows this message.
 --class    Displays all the objects of a single class. Without this, a list
            of known classes is displayed instead.
 --sort     Sorts the list by something other than "id".
 --columns  Limits what columns are displays.
 --format   Overrides the default selection of output format.
 CLASS      Either a full-qualified class name, or just the final component.
 SORT       Controls the sort of the objects, and follows the format:
              FIELD/CMP/DIR
            Where:
              FIELD  Specifies the object property to sort by.
              CMP    Either "s" or "n" to indicate string or numeric sorting.
              DIR    Either "a" or "d" for ascending/decending sorting.
 COLS       A comma-separated list of column names to display.
 FMT        An output format, from the list of: TEXT, HTML, XML.
HELP
	print qq[</PRE>\n] if ($web);
}

sub showClassList() {
	if ($format eq 'TEXT') {
		print qq[Classes:\n];
	}
	
	my @classes = &getClassList();
	
	if ($format eq 'HTML') {
		print qq[	<UL>\n];
	}
	foreach my $class (@classes) {
		if ($format eq 'TEXT') {
			print qq[  $class->{name} ($class->{short})\n];
			
		} elsif ($format eq 'HTML') {
			print qq[		<LI><A HREF="?class=$class->{short}" TITLE="$class->{name}">$class->{name}</A></LI>\n];
		}
	}
	if ($format eq 'HTML') {
		print qq[	</UL>\n];
	}
}

if ($format eq 'HTML') {
	print qq[<HTML>\n];
	print qq[<HEAD>\n];
	print qq[	<TITLE>Choob ObjectDB Store</TITLE>\n];
	print qq[	<STYLE>\n];
	print qq[		body {\n];
	print qq[			color: black;\n];
	print qq[			background: white;\n];
	print qq[		}\n];
	print qq[		table {\n];
	print qq[			border-collapse: collapse;\n];
	print qq[		}\n];
	print qq[		tr.even {\n];
	print qq[		}\n];
	print qq[		tr.odd {\n];
	print qq[			background: #CCC;\n];
	print qq[		}\n];
	print qq[	</STYLE>\n];
	print qq[</HEAD>\n];
	print qq[<BODY>\n];
	
} elsif ($format eq 'XML') {
	print qq[<?xml version="1.0" standalone="yes"?>\n];
}

if ($params{help}) {
	&showHelp();
	
} else {
	# Compute multi-table setting.
	$params{m} = &isMultiTable();

	unless ($params{class}) {
		&showClassList();
		
	} else {
		my $className = $params{class};
		
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
		
		if ($format eq 'TEXT') {
			print qq[Class: $className\n];
			
		} elsif ($format eq 'HTML') {
			print qq[<H3>Class $className</H3>\n];
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
			my $colData = &getData("DESCRIBE $table", []);
			
			foreach my $col (@{$colData}) {
				my $colName = $col->[0];
				# Skip if it is already defined, or we're not supposed to list it.
				next if (exists $columns{$colName});
				if ($params{columns} && (",$params{columns}," !~ /,$colName,/)) {
					next;
				}
				
				my $colType = "unknown";
				if ($col->[1] =~ /^text$/) {
					$colType = "string";
				} elsif ($col->[1] =~ /^(?:tiny|big)?int\(\d+\)$/) {
					$colType = "int";
				} elsif ($col->[1] =~ /^double$/) {
					$colType = "double";
				} else {
					warn qq[Unknown property type: $col->[1].];
				}
				
				$columns{$colName} = $colType;
				$columnLens{$colName} = length($colName);
				push @columns, $colName;
			}
			
			my $objectData = &getData("SELECT id, `" . join("`, `", @columns) . "` FROM $table", []);
			
			foreach my $objectItem (@{$objectData}) {
				unless (exists $objects{$objectItem->[0]}) {
					$objects{$objectItem->[0]} = { id => $objectItem->[0] };
				}
				
				for (my $i = 0; $i < @columns; $i++) {
					# Update column widths:
					my $len = length(defined $objectItem->[$i + 1] ? $objectItem->[$i + 1] : $itemMissing);
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
				my $len = length(defined $objectItem->[4] ? $objectItem->[4] : $itemMissing);
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
		
		my $baseURL = "";
		if ($web) {
			$baseURL = "?" . $ENV{QUERY_STRING};
			$baseURL =~ s/;sort=[^;]+//;
		}
		
		if ($format eq 'TEXT') {
			print qq[Sort : $sortCol, ] . ($sortDir eq 'a' ? "ascending" : "descending") . qq[.\n];
			print qq[\n];
			
		} elsif ($format eq 'HTML') {
			print qq[	<P>Sorted by $sortCol, ] . ($sortDir eq 'a' ? "ascending" : "descending") . qq[. ];
			print qq[<A HREF="$baseURL;format=xml;sort=$sortCol/$sortCmp/$sortDir">Also available in XML</A></P>\n];
			
			print qq[	<TABLE BORDER="1">\n];
			print qq[		<THEAD><TR>\n];
		}
		
		my $rowSep = "";
		if ($format eq 'TEXT') {
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
			
		} elsif ($format eq 'HTML') {
			print qq[			<TH>&nbsp;</TH>\n];
			foreach my $col (@columns) {
				my $cmp = ($columns{$col} ne "string" ? "n" : "s");
				if (($col eq $sortCol) && ($sortDir eq 'a')) {
					print qq[			<TH><A HREF="$baseURL;sort=$col/$cmp/d" TITLE="Sort by $col descending">$col</A></TH>\n];
				} else {
					print qq[			<TH><A HREF="$baseURL;sort=$col/$cmp/a" TITLE="Sort by $col ascending">$col</A></TH>\n];
				}
			}
			print qq[		</TR><TR>\n];
			print qq[			<TD>&nbsp;</TD>\n];
			foreach my $col (@columns) {
				print qq[			<TH>] . $columns{$col} . qq[</TH>\n];
			}
			print qq[		</TR></THEAD>\n];
			print qq[		<TBODY>];
			
		} elsif ($format eq 'XML') {
			shift @columns;
			print qq[<objects class="$className" sortKey="$sortCol" sortCompare="] . ($sortCmp eq 'n' ? "numeric" : "text") . qq[" sortDir="] . ($sortDir eq 'a' ? "ascending" : "descending") . qq[">\n];
		}
		
		my $even = 0;
		foreach my $key (@objKeys) {
			if ($format eq 'TEXT') {
				foreach my $col (@columns) {
					my $val = $itemMissing;
					if (defined $objects{$key}{$col}) {
						$val = $objects{$key}{$col};
					}
					print qq[| $val] . (" " x ($columnLens{$col} - length($val))) . qq[ ];
				}
				print qq[|\n];
				print $rowSep;
			} elsif ($format eq 'HTML') {
				if ($even) {
					print qq[<TR CLASS="even">\n];
				} else {
					print qq[<TR CLASS="odd">\n];
				}
				print qq[			<TD><A HREF="?class=$className;id=$key">X</A></TD>\n];
				foreach my $col (@columns) {
					if (defined $objects{$key}{$col}) {
						print qq[			<TD>] . $objects{$key}{$col} . qq[</TD>\n];
					} else {
						print qq[			<TD STYLE="font-weight: bold; color: red;">MISSING</TD>\n];
					}
				}
				print qq[		</TR>];
			} elsif ($format eq 'XML') {
				print qq[	<object id="$key">\n];
				foreach my $col (@columns) {
					if (($columns{$col} eq 'unknown') || !(defined $objects{$key}{$col})) {
						print qq[		<field name="$col" type="$columns{$col}"/>\n];
					} else {
						print qq[		<field name="$col" type="$columns{$col}">] . &escapeXML($objects{$key}{$col}) . qq[</field>\n];
					}
				}
				print qq[	</object>\n];
			}
			$even = ($even + 1) % 2;
		}
		if ($format eq 'HTML') {
			print qq[</TBODY>\n];
			print qq[	</TABLE>\n];
			
		} elsif ($format eq 'XML') {
			print qq[</objects>\n];
		}
		
	}
}

if ($format eq 'HTML') {
	print qq[</BODY>\n];
	print qq[</HTML>\n];
}

$dbh->disconnect();

sub unescape() {
	my $item = shift;
	$item =~ s/\+/ /g;
	$item =~ s/%([0-9A-F][0-9A-F])/sprintf('%c', hex($1))/gei;
	return $item;
}

sub escapeXML() {
	my $item = shift;
	$item =~ s/&/&amp;/g;
	$item =~ s/</&lt;/g;
	$item =~ s/>/&gt;/g;
	$item =~ s/"/&quot;/g;
	return $item;
}
