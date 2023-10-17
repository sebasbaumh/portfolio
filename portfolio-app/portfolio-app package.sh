#!/bin/sh
mvn -U -T 8 -D tycho.disableP2Mirrors=true clean package
