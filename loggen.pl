#!/usr/bin/perl -w

use DBI;            # Load the DBI module
use strict;
use warnings;

die ("usage: echo password | $0 channel_filter_expression [days of data]\n" .
	"e.g. echo ponies | $0 \"IN ('#compsoc','#work')\" 14") if ($#ARGV == -1);

my ($dbh, $sth, @row, $pw);

chomp ($pw = <STDIN>);

$dbh = DBI->connect( "dbi:mysql:choob", "choob", $pw, {
    PrintError => 0,   ### Don't report errors via warn(  )
    RaiseError => 1    ### Do report errors via die(  )
} );

### Prepare a SQL statement for execution
$sth = $dbh->prepare( 
#CREATE FUNCTION nicklink(nick CHAR(64)) RETURNS CHAR(64) 
#	RETURN (
#		CASE INSTR(`Nick`, "|") 
#			WHEN 0 THEN `Nick` 
#			ELSE LEFT(`Nick`, INSTR(`Nick`,"|")-1) 
#		END
#	);

'SELECT CONCAT( 
	FROM_UNIXTIME(`Time`/1000,"%H:%i:%s"),
	" ",
	CASE (Type LIKE "%ChannelAction") 
		WHEN 0 THEN '.# It-s not an action..
			'CASE (Type LIKE "%ChannelKick")
				WHEN 0 THEN '.# It-s not a kick, either, must be a message.
					'CONCAT("<", nicklink(`Nick`),"> ", `Text`) 
				ELSE '.# It-s a kick.
					'CONCAT("-!- ", "somebody", " was kicked from #compsoc by ", nicklink(`Nick`), " [", `Text`, "]")
			END
		ELSE  # It is an action.
			CONCAT(" * ", nicklink(`Nick`), " ", `Text`) 
	END
) AS `Line` 
FROM `History` 
WHERE `Channel` ' . $ARGV[0] . '
AND `Nick` NOT LIKE "Guest%" 
AND `Text` NOT LIKE "!%" 
' . ($#ARGV > 0 && $ARGV[1] > 0 ? 'AND ((UNIX_TIMESTAMP()-(`Time`/1000)) < (60*60*24*' . $ARGV[1] . '))' : '') . '
ORDER BY `Time`;' );

### Execute the statement in the database
$sth->execute(  );

### Retrieve the returned rows of data
while ( @row = $sth->fetchrow_array(  ) ) {
    print "@row\n";
}

### Disconnect from the database
$dbh->disconnect(  );

exit;

