This is Choob, our Java IRC bot.

License: LGPL (don't ask why)

# How to run Maven Choob

## Ensure you're using the right software
 * MySQL 5.1+
 * Java 6.  Java 7 should work but is mostly untested (we have to work around some things: [1d5ad0a91](https://github.com/UWCS/choob/commit/1d5ad0a91db78f4b7f8b483f099c486d6ac50e69)).
 * Maven **3**.  Maven 2 (2.2.1 tested) fails to resolve the dependencies that it creates.  Worst.  Maven3 isn't packaged for [Debian](http://bugs.debian.org/cgi-bin/bugreport.cgi?bug=592218) or [Ubuntu](https://bugs.launchpad.net/ubuntu/+source/maven2/+bug/672971) right now.

## # On Debian/Ubuntu, that'd be:
    sudo apt-get install mysql-server default-jdk git curl
    curl http://mirror.lividpenguin.com/pub/apache/maven/binaries/apache-maven-3.0.3-bin.tar.gz | tar zxv # http://maven.apache.org/download.html
    export PATH=$(pwd)/apache-maven-3.0.3/bin:$PATH

## # Download and compile
    git clone https://github.com/UWCS/choob.git choob
    cd choob

    # this will download literally the entire Internet, and take _over four minutes_.
    mvn package
    java -jar target/choob-1.0-SNAPSHOT.jar setup

## # Create the MySQL user
    mysql -uroot -p
    CREATE USER choob@localhost IDENTIFIED BY 'choob';
    CREATE DATABASE choob;
    GRANT ALL PRIVILEGES ON choob.* TO choob@localhost;

## # Setup database and config
 # (Windows doesn't support this \ syntax, so you need to do it all on one line, with no backslashes.)

    java -jar target/choob-1.0-SNAPSHOT.jar setup \
       dbServer=localhost dbUser=choob dbPass=choob database=choob \
       ircServer=irc.uwcs.co.uk ircChannel=#bots \
       botNick=ChoobTrac \
       rootNick=YourNick

## # Start the bot
    java -jar target/choob-1.0-SNAPSHOT.jar

## # Set-up the bot for use in Eclipse
 # if you've never used mvn eclipse before, you need to:
[mvn eclipse:configure-workspace -Dworkspace=/path/to/workspace](http://maven.apache.org/plugins/maven-eclipse-plugin/configure-workspace-mojo.html)

    mvn clean eclipse:eclipse
    mvn eclipse:eclipse -DdownloadSources=true -DdownloadJavadocs=true &

## Running in the Eclipse debugger
If you run in the (Eclipse) debugger, Choob will attempt to allow Eclipse to compile things.  This makes debugging inside **plugins** work, at the expense of the Choob security model (and intervals, and..) being crippled.

You'll see:
    Still waiting for your IDE to create the class...

You need to:

 * Turn on automatic workspace refresh.  Window -> Preferences -> search for "refresh" -> Workspace -> Refresh Automatically.
 * Add `tmp` as a source folder.  It'll show at the bottom, below all the other source folders.  Right click -> build path -> use as source folder.

If you'd rather just debug Choob itself with the classic plugin model, set the property `choobDebuggerHack=false`.

