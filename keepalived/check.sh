#!/bin/bash

if ls /etc/keepalived/MASTER; then 
	killall -0 haproxy 
	exit
else
	exit 0
fi
