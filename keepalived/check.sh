#!/bin/bash 
# If we are not the master 0 is always return. Otherwise only return 0 if 
# haproxy & zkhaproxy are perfectly working. short-circuit evaluations are used.

if ls /etc/keepalived/MASTER; then
	killall -0 haproxy && kill -0 $(ps aux | grep zkhaproxy | grep -v 'grep' | awk '{print $2}') && exit 0
	exit 1
else
	exit 0
fi
