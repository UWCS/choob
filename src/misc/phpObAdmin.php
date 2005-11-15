/*
Step 1:
Fill in username and password below.

Step 2:
Visit in web-browser.
*/

<?
mysql_connect("localhost","USERNAME","PASSWORD");
mysql_select_db("choob");

if (isset($_GET{'limit'}))
	$limit=$_GET{'limit'};

$thas=$_SERVER{'PHP_SELF'};
if ($_SERVER{'QUERY_STRING'}=="") 																// Page has been requested, gives the <frames> page.
{
	?>
	<html><head><title>phpObAdmin</title></head>
	<frameset cols="260,*" rows="*" border="1">
		<frame src="<?=$thas?>?left" />
		<frame name="main" src="<?=$thas?>?intro" />
		<noframes>
			<p>Frames pls.</p>
		</noframes>
	</frameset>

	<?
	exit;
}																								// </frames>

if ($_SERVER{'QUERY_STRING'}=="intro")															// Give the <intro /> page.
{
	echo "<--";
	exit;
}

if (isset($_GET['delete']))																		// We've been asked to <delete> an object.
{
	$id=(int)$_GET['delete'];
	mysql_query('delete from `objectstoredata` where `objectid`=' . $id . ';');
	mysql_query('delete from `objectstore` where `objectid`=' . $id . ';');
	echo mysql_error();
	echo "Done! (<a href=\"{$_SERVER['HTTP_REFERER']}\">The page that lead you here</a>).";
	exit;
}
																								// </delete>

$object=$_GET{"object"};																		// We've possibly been supplied with an <object>

$r=mysql_query('select `objectid`,`fieldname`,`fieldstring` from `objectstoredata` where `objectid` in (select distinct `objectstoredata`.`objectid` from `objectstoredata` inner join `objectstore` on (`objectstoredata`.`objectid`=`objectstore`.`objectid`) WHERE `ClassName`="' . mysql_real_escape_string($object) . '" order by `objectid`)');
echo mysql_error();

if (!mysql_num_rows($r))																			// It didn't exist, show the table <list>.
{
	$r=mysql_query('select distinct `classname` from `objectstore`');
	echo mysql_error();

	if (isset($object))
		echo "Not found, select table:";
	else
		echo "Select table:";

	echo "<ul>";

	while ($row=mysql_fetch_row($r))
		echo "<li><a href=\"?object={$row[0]}\" target=\"main\">" . substr($row[0],8) . "</a></li>";

	echo "</ul>";

	exit;																							// </list>
}

																								// <Display> the list of </object>s.

echo "<h1>$object</h1>";

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

echo "<table>\n<tr>";

foreach ($t[0] as $keyz => $nil)
	echo "<th>$keyz</th>";
unset($nil);
echo "</tr>\n";

foreach ($t as $sub)
{
	echo "<tr>";
	foreach ($sub as $key => $a)
	{
		if ($key=="id")
			echo "<td><a href=\"?delete=$a\" title=\"Delete\">x</a>&nbsp;$a</td>";
		else
		{
			if (strpos($a,"\n")===false)
				echo "<td>$a</td>\n";
			else
				echo "<td><pre>$a</pre></td>\n";
		}
	}
	echo "</tr>";
}


echo "</table>";																				// </display>