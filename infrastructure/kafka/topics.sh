#!/bin/sh
set -eu
export PATH="$PATH:/opt/kafka/bin:/opt/bitnami/kafka/bin"
for topic in security.auth.events security.network.events security.alerts security.events.dlt; do
  kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic "$topic" --partitions 3 --replication-factor 1
done
