SPEC=$1
LOG=$(mktemp)

. $(basename $SPEC .spec).spec

trap "rm $LOG" EXIT

if [ -z "$CHANNEL" ]; then
	CHANNEL='IS NOT NULL'
fi

grep -m1 password ~/.my.cnf | \
	cut -d= -f2- | \
	nice perl loggen.pl "$CHANNEL" $TIME > $LOG

# Remove any crazy Nick|links that were missed.
sed -i 's/\([a-zA-Z0-9]\)[|][A-Za-z0-9]*/\1/g' $LOG


BASE=$HOME/public_html/pisg/$SPEC
mkdir -p $BASE
chmod a+rx $BASE

CURRENT=$BASE/current.html

if [ -n "$ARCHIVE" ]; then
	OUTPUT="$BASE/$(date +'%Y-%m-%d').html"
else
	OUTPUT=$CURRENT
fi


cd pisg-0.72

	CFG=$(mktemp)
	trap "rm $CFG" EXIT

	sed "s,OUTFILE,$OUTPUT,;s,INFILE,$LOG," < ../config_base > $CFG
	nice perl pisg --configfile=$CFG

	cp gfx/*.png $BASE
	chmod a+r $BASE/*.png

cd ..

chmod a+r $OUTPUT
if [ -n "$ARCHIVE" ]; then
	rm $BASE/current.html
	ln -s $OUTPUT $CURRENT
	chmod a+r $CURRENT
fi

