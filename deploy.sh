#!/bin/bash
set -eu
mvn package -Dmaven.test.skip=true

rm ../working/choob-*.jar

# For reference, the "crazy git command" is so that we can work out what
# version of the bot is running with "ps aux | grep choob-uwcs".
# "uwcs-v001-22-gd0927c2" is a valid revision according to git, and also human readable.
# i.e. "git log uwcs-v001-22-gd0927c2.." will show what's happened since the last deploy.
# See man git-describe and man git-rev-parse (at your peril)
mv target/choob-*.jar ../working/choob-$(git describe --always --tags).jar

