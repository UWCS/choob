#!php
<?php
function genfoo()
{
	ini_set('memory_limit', '64M');
	$f = file('compsoc.log');

	$foo = array();

	$n = count($f)/(136/7);
	foreach ($f as $ln => $line)
	{
		preg_match('/<(.*?)>/', $line, $r); 
		$foo[strtolower($r[1])][(int)($ln/$n)]++;
	}

	foreach($foo as $name => $timearr)
		if (array_sum($timearr) < 5000)
			unset($foo[$name]);
	
	return $foo;
}

$g = $QUERY_STRING;

if (preg_match_all('/([a-zA-Z0-9_-]+/)', $g, $reg))
{
	// We have a plausible get string, we're probably serving an image. (explode(',') is for wimps).
}
else
{
?>
<html>
<head>
	<title>Zomg BORAX!</title>
	<style type="text/css">
		img { width: 500px; height: 300px }
		ul { list-style: none }
	</style>
	</head>
<body>
	<img id="nub" />
	<p>
		<ul>
<?
				foreach (array_keys(genfoo()) as $name)
					echo "\t\t\t<li><input type=\"checkbox\" name=\"$name\" value=\"$name\" /></li>\n";
		?>
		</ul>
	</p>
</body>
</html>
<?
}
