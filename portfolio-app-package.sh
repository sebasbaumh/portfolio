#!/bin/sh
#export MAVEN_OPTS="-Xmx4g"
#mvn --update-snapshots -D eclipse.p2.mirrors=false --file=portfolio-app/pom.xml clean verify
rm -rf */target                                                           1 ↵
#rm -rf */**/target
mvn -f portfolio-app/pom.xml -Denforcer.skip=true clean verify
