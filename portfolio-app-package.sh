#!/bin/sh
# force build with Java 21
export JAVA_HOME=`/usr/libexec/java_home -v 21`
java -version
#export MAVEN_OPTS="-Xmx4g"
#mvn --update-snapshots -D eclipse.p2.mirrors=false --file=portfolio-app/pom.xml clean verify
# clean up old builds
rm -rf */target                                                           1 ↵
# build
mvn -f portfolio-app/pom.xml -Denforcer.skip=true clean verify
# skip tests
#mvn -f portfolio-app/pom.xml -Denforcer.skip=true -DskipTests clean verify
