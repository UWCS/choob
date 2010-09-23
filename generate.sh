# Generate the log from mysql.
nice -n 19 perl loggen.pl > log.log

# Remove any crazy Nick|links that were missed.
cat log.log | sed 's/\([a-zA-Z0-9]\)[|][A-Za-z0-9]*/\1/g' > log2.log
mv log2.log log.log

# Run pisg.
cd pisg-0.67
cat config_base | sed 's#OUTFILE#/home/choob/public_html/pisg/index.html#' > pisg.cfg
nice -n 19 perl pisg
rm pisg.cfg
cd ..
rm log.log
