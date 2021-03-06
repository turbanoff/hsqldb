#!/bin/sh
# -----------------------------------------------------
# Run with -help for usage.
# If $JAVA_HOME is set, editing this script should not be required.
# Send any questions to fchoong@user.sourceforge.net
# -----------------------------------------------------

# the value set here will override the value passed by $JAVA_HOME or the -jdkhome switch
jdkhome=""

PRG=$0

#
# resolve symlinks
#

while [ -h "$PRG" ]; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '^.*-> \(.*\)$' 2>/dev/null`
    if expr "$link" : '^/' 2> /dev/null >/dev/null; then
	PRG="$link"
    else
	PRG="`dirname $PRG`/$link"
    fi
done

progdir=`dirname $PRG`
progname=`basename $0`

# ../ will lead us to the home
dbhome="$progdir/.."

# absolutize dbhome

dbhome=`cd ${dbhome}; pwd`

#
# bring in needed functions

. ${dbhome}/lib/functions

#--------------------------------------------------------------------------------------------------------------
pre_main

#
# let's go
#

# Note: we are starting at $dbhome/build

echo cd ../
cd ../
echo rm -r -f classes
rm -r -f classes
echo mkdir classes
mkdir classes
echo cd build
cd build
echo cd ../src/org/hsqldb/util
cd ../src/org/hsqldb/util
echo "$jdkhome/bin/javac" -d ../../../../classes CodeSwitcher.java
"$jdkhome/bin/javac" -d ../../../../classes CodeSwitcher.java
echo cd ../../../../build
cd ../../../../build
echo "$jdkhome/bin/java" -classpath "$cp:../classes" org.hsqldb.util.CodeSwitcher ../src/org/hsqldb/jdbcConnection.java -JDBC3
"$jdkhome/bin/java" -classpath "$cp:../classes" org.hsqldb.util.CodeSwitcher ../src/org/hsqldb/jdbcConnection.java -JDBC3
echo "$jdkhome/bin/java" -classpath "$cp:../classes" org.hsqldb.util.CodeSwitcher ../src/org/hsqldb/jdbcDatabaseMetaData.java -JDBC3
"$jdkhome/bin/java" -classpath "$cp:../classes" org.hsqldb.util.CodeSwitcher ../src/org/hsqldb/jdbcDatabaseMetaData.java -JDBC3
echo "$jdkhome/bin/java" -classpath "$cp:../classes" org.hsqldb.util.CodeSwitcher ../src/org/hsqldb/jdbcPreparedStatement.java -JDBC3
"$jdkhome/bin/java" -classpath "$cp:../classes" org.hsqldb.util.CodeSwitcher ../src/org/hsqldb/jdbcPreparedStatement.java -JDBC3
echo "$jdkhome/bin/java" -classpath "$cp:../classes" org.hsqldb.util.CodeSwitcher ../src/org/hsqldb/jdbcResultSet.java -JDBC3
"$jdkhome/bin/java" -classpath "$cp:../classes" org.hsqldb.util.CodeSwitcher ../src/org/hsqldb/jdbcResultSet.java -JDBC3
echo "$jdkhome/bin/java" -classpath "$cp:../classes" org.hsqldb.util.CodeSwitcher ../src/org/hsqldb/jdbcStatement.java -JDBC3
"$jdkhome/bin/java" -classpath "$cp:../classes" org.hsqldb.util.CodeSwitcher ../src/org/hsqldb/jdbcStatement.java -JDBC3

# and we exit.
