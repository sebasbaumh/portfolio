#!/bin/sh
mvn -U -T 8 -D eclipse.p2.mirrors=false clean package
