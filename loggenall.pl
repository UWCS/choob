#!/usr/bin/perl -w

use DBI;            # Load the DBI module

my ($dbh, $sth, @row);

$dbh = DBI->connect( "dbi:mysql:choob", "choob", "THIS IS NOT THE REAL PASSWORD" , {
    PrintError => 0,   ### Don't report errors via warn(  )
    RaiseError => 1    ### Do report errors via die(  )
} );

### Prepare a SQL statement for execution
$sth = $dbh->prepare( 'SELECT CONCAT( FROM_UNIXTIME(`Time`/1000,"%H:%i:%s"), " ", case (`Type` LIKE "%ChannelAction") when 0 then concat("<",(case instr(`Nick`,"|") when 0 then `Nick` else left(`Nick`, instr(`Nick`,"|")-1) end),"> ") else concat(" * ",(case instr(`Nick`,"|") when 0 then `Nick` else left(`Nick`, instr(`Nick`,"|")-1) end)," ") end , `Text` ) as `Line` FROM `History` WHERE `Channel`="#compsoc" AND `Nick` NOT LIKE "Guest%" AND `Text` NOT LIKE "!%" ORDER BY `Time`;' );

### Execute the statement in the database
$sth->execute(  );

### Retrieve the returned rows of data
while ( @row = $sth->fetchrow_array(  ) ) {
    print "@row\n";
}

### Disconnect from the database
$dbh->disconnect(  );

exit;

