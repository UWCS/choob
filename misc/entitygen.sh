# Heh, it's kind of ironic that most of the scraper module was written by screenscraping.

links -dump http://www.w3.org/TR/REC-html40/sgml/entities.html | grep ENTITY | grep CDATA | sed -e "s/<.ENTITY//; s/CDATA//;s/ -- .*$//;s/\"//g;s/ \{1,\}/ / g;s/^ /EntityMap.put(\"/;s/ /\", new Character((char)/;s/\\&#\([0-9]*\);/\1));\\nEntityMap.put(\"\\#\1\", new Character((char)\1));/"