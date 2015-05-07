#!/bin/bash

STATE=$1
NOW=$(date +"%D %T")
KEEPALIVED="/etc/keepalived"

case $STATE in
	"MASTER") touch $KEEPALIVED/MASTER
		echo "$NOW Becomind MASTER" >> $KEEPALIVED/LOG
		/etc/init.d/haproxy start
		java -jar /home/pepper/zkhaproxy/build/libs/zkhaproxy-1.2.jar
		exit 0
		;;

	"BACKUP") rm $KEEPALIVED/MASTER
		echo "$NOW Becoming BACKUP" >> $KEEPALIVED/LOG
		/etc/init.d/haproxy stop || killall -9 haproxy
		kill -9 $(ps aux | grep zkhaproxy | awk '{print $2}')
		exit 0
		;;

	"FAULT") rm $KEEPALIVED/MASTER
		echo "$NOW Becoming FAULT" >> $KEEPALIVED/LOG
		/etc/init.d/haproxy stop || killall -9 haproxy
		kill -9 $(ps aux | grep zkhaproxy | awk '{print $2}')
		exit 0
		;;
	
	*) 	echo "$NOW Becoming UNKOWN" >> $KEEPALIVED/LOG
		exit 1
		;;

esac
