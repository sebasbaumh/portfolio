#!/bin/bash

EXECUTABLE="$1"
if [ -z $EXECUTABLE ]; then
  echo "No EXECUTABLE given. Must point to PortfolioPerformance.exe."
  exit 1
fi

PROVIDER_ARG="$2"
if [ -z "$PROVIDER_ARG" ]; then
  echo "No PROVIDER_ARG given."
  exit 1
fi

STOREPASS="$3"
if [ -z "$STOREPASS" ]; then
  echo "No STOREPASS given."
  exit 1
fi

ALIAS="$4"
if [ -z "$ALIAS" ]; then
  echo "No ALIAS given."
  exit 1
fi

# remove existing signature
osslsigncode remove-signature "$EXECUTABLE" "$EXECUTABLE.clean"

mv "$EXECUTABLE.clean" $EXECUTABLE

jsign \
  --keystore "$PROVIDER_ARG" \
  --storetype PKCS11 \
  --storepass "$STOREPASS" \
  --alias "$ALIAS" \
  --tsaurl http://time.certum.pl/ \
  --name "Portfolio Performance" \
  -url https://www.portfolio-performance.info \
  $EXECUTABLE
