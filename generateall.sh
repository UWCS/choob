# Generate the log from mysql.
nice -n 19 perl loggenall.pl > log.log

# Remove any crazy Nick|links that were missed.
cat log.log | sed 's/\([a-zA-Z0-9]\)[|][A-Za-z0-9]*/\1/g' > log2.log
mv log2.log log.log
FOO="/home/choob/public_html/pisg/all/`date +'%Y-%m-%d'`.html"
# Run pisg.
cd pisg-0.67
cat config_base | sed "s#OUTFILE#$FOO#" > pisg.cfg
nice -n 19 perl pisg
rm pisg.cfg
cd ..
rm log.log
chmod a+r $FOO
rm /home/choob/public_html/pisg/all/current.html
ln -s $FOO /home/choob/public_html/pisg/all/current.html
chmod a+r /home/choob/public_html/pisg/all/current.html
