#flume-taildir-v2-source
Source of Flume NG for tailing files in a directory. This source is based on flume-taildirectory version.

This version works on CPU performance improvements. Now, events are processes in batchs. 


**Important**

Features based on XML files have been removed


#Compilation
```
mvn clean package
```

#Dependecies
This project depends on next libs:
* metrics-core (v 3.1.1)

#Use
Make the directory in flume installation path ```$FLUME_HOME/plugins.d/watchdir-v2/lib``` and copy the file  jar file.  
Edit flume configuration file with the parameters above.

#Configuration

##Configuration for single line events files
| Property Name | Default | Description |
| ------------- | :-----: | :---------- |
| Channels | - |  |
| Type | - | org.keedio.flume.source.watchdir.listener.simpletxtsource.FileEventSourceListener |
| dirs.1.dir | - | directory to be monitorized (only one) |
| dirs.1.whitelist | - | regex pattern indicating whitelist files to be monitorized (ex. \\.xml) |
| dirs.1.blacklist | - | regex pattern indicating blacklist files to be excluded (ex. \\.xml) |
| dirs.2.dir | - | second directory configuration... |
| dirs.2.whitelist | - | ... |
| dirs.2.blacklist | - | ... |
| whitelist | - | regex pattern indicating whitelist files to be monitorized (ex. \\.xml). If it is set it will rewrite the directory one |
| blacklist | - | regex pattern indicating blacklist files to be excluded (ex. \\.xml). If it is set it will rewrite the directory one |
|readonstartup|false|Used in order to indicate if the agent have to proccess files existing in the directory on startup|
|pathtoser| - |The .ser file used by the recovery mecanism|
|timetoser| - |Time to generate ser file used by the recovery mecanism|
|timetoprocessevents|10|Time to procesed files, this parameter affects to CPU performance|
|followlinks|false|Follow symbolic links to directories referenced in monitorized directories
|fileHeader|false|Include file absolute path in events header
|fileHeaderKey||Key of file absolute path header
|basenameHeader|false|Include file base name in events header
|basenameHeaderKey||Key of file base name header

* Example
```
# Describe/configure the source
ag1.sources.r1.type = org.keedio.flume.source.watchdir.listener.simpletxtsource.FileEventSourceListener
ag1.sources.r1.dirs.1.dir = /tmp/dir1
ag1.sources.r1.dirs.1.blacklist =
ag1.sources.r1.dirs.1.whitelist =
ag1.sources.r1.dirs.2.dir = /tmp/dir2
ag1.sources.r1.dirs.2.blacklist =
ag1.sources.r1.dirs.2.whitelist = \\.txt
ag1.sources.r1.blacklist = \\.\\d+
ag1.sources.r1.whitelist =
ag1.sources.r1.readonstartup=true
ag1.sources.r1.pathtoser=/tmp/map.ser
ag1.sources.r1.timetoser=5```
