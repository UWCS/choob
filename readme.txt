Welcome to Choob.

This is currently just a basic set of instructions to get the bot running.

Minimum requirements:
 - A copy of the jdk, preferably 1.5 or above, in your path; avaliable from: http://java.sun.com/
 - A working sql server, preferably mysql 4.1 or above; a local version can be set up using files avalible from http://dev.mysql.com/
 
1. Create an account on the sql server for the bot to use.
2. Run the sql script in 'db/choob.db' on your server.
3. cd to the 'src' directory.
4. Ensure you have a bot.conf. bot.conf should look something like this:

botName=ChoobyMcNickNameahurtz
botTrigger=!
dbUser=choobdbUser
dbPass=choobdbPass1234
dbServer=localhost

5. Compile the bot by executing the 'compile' script for your platform.
6. Run the bot by executing the 'run' script for your platform.

Enjoy.
