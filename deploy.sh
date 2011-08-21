#!/bin/bash
mvn package
rm ../working/choob-*.jar
mv target/choob-*.jar ../working/choob-$(git describe --always --tags).jar

