<?
/*
Usage:

Either:
	http://localhost/path/to/ObjectDbDump.php?object={type of object to dump}&format={format to dump}

	ie.
	http://localhost/path/to/ObjectDbDump.php?object=plugins.Alias.AliasObject&format=alias.add
	to dump aliases in the alias.add format.

	Supported formats listed if format ommited.

Or, from the command line:

	php ObjectDbDump.php {format} {object}

	ie.

	c:\php\php ObjectDbDump.php alias.add plugins.Alias.AliasObject

Nb: Insert username and password below.

*/

header ("Content-type: text/plain");
mysql_connect("localhost", "choob","choob");

mysql_select_db("choob");

if ($_SERVER['argc']==1)
{
	$getlimit=$_GET{'limit'};
	$getformat=$_GET{"format"};
	$getobject=$_GET{'object'};
}
else
{
	if ($_SERVER['argc'] !=3)
	{
		echo "head -n 24 me for manual.";
		exit;
	}

	$getformat=$_SERVER['argv'][1];
	$getobject=$_SERVER['argv'][2];
}


if (isset($getlimit))
	$limit=$getlimit;

$r=mysql_query('select `objectid`,`fieldname`,`fieldstring` from `objectstoredata` where `objectid` in (select distinct `objectstoredata`.`objectid` from `objectstoredata` inner join `objectstore` on (`objectstoredata`.`objectid`=`objectstore`.`objectid`) WHERE `ClassName`="' . mysql_real_escape_string($getobject) . '") and 1 order by `objectid`' . (isset($limit) ? "limit $limit" : ""));
echo mysql_error();

if (!mysql_num_rows($r))
{
	echo "Not found!";
	exit;
}

$t=array();
$lastrow=-1;

while ($row=mysql_fetch_row($r))
{
	if ($row[0]!=$lastrow)
	{
		if ($lastrow!=-1)
			array_push($t, $raray);
		$raray=array();
		$lastrow=$row[0];
		$raray['id']=$row[0];
	}
	$raray[$row[1]] = $row[2];
}
array_push($t, $raray);

$format=$getformat;

switch ($format)
{
	case "xml":
	{
		foreach ($t as $el)
		{
			echo "<object type=\"$getobject\" id={$el['id']}>\n";
			foreach ($el as $n => $l)
				if ($n!='id')
					echo "\t<item name=\"$n\">$l</item>\n";
			echo "</object>\n\n";
		}
		break;
	}
	case "alias.add":
	{
		foreach ($t as $el)
			echo "alias.add {$el['name']} {$el['converted']}\n";
		break;
	}
	case "csil":
	{
		foreach ($t as $el)
		{
			$s="";
			echo "$getobject:{";
			foreach ($t[0] as $ra => $nil)
						$s.="$ra\t";
					$s=substr($s,0,strlen($s)-1);
			echo "$s}:{";
			$s="";
			foreach ($el as $n => $l)
				if ($n!='id')
					$s.= str_replace("\t", "   ", $l) . "\t";

			$s=substr($s,0,strlen($s)-1);

			echo "$s}\n";
		}
		break;
	}
	default:
	echo "Supported formats: xml, csil and alias.add.";
}