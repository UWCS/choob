rem Windows version of icky compile script but it'll work. For now.

javac -classpath beanshell/bsh-2.0b4.jar;database/mysql-connector-java-3.1.8-bin.jar;pircbot/pircbot.jar org/uwcs/choob/*.java org/uwcs/choob/support/*.java org/uwcs/choob/modules/*.java org/uwcs/choob/plugins/*.java org/uwcs/choob/support/events/*.java 
