#flume-taildir-v2-source
Source of Flume NG for tailing files in a directory. This source is based on flume-taildirectory and XMLWinEvent version. The two sources have been packaged in a single jar extracting commons features.

Features:
* Whitelist and blacklist for including/excluding files to monitor
* Recovery mechanism in case flume dies
* Now, it is possible to read files on startup
* Process both xml and single event line files.
* It is possible to set both number of events or max time in seconds to inject events to channel

#Compilation
```
mvn clean package
```

#Dependecies
This project depends on next libs:
* jpathwatch (v 0.95)
* metrics-core (v 3.1.1)
* stax
* This source uses streams, so be sure to execute under a Java8 enviroment

#Use
Make the directory in flume installation path ```$FLUME_HOME/plugins.d/watchdir-v2/lib``` and copy the file   ```watchdir--X.Y.Z.jar```.  
Edit flume configuration file with the parameters above.

#Configuration
##Configuration for XML files
| Property Name | Default | Description |
| ------------- | :-----: | :---------- |
| Channels | - |  |
| Type | - | org.keedio.flume.source.watchdir.listener.xmlsource.WatchDirXMLWinEventSourceListener |
| dirs.1.dir | - | directory to be monitorized (only one) |
| dirs.1.tag | - | Tag identifying markup to serialize to channel (ex. \\.xml) |
| dirs.1.taglevel | - | Use in case of nested xml (ex. \\.xml) |
| dirs.1.whitelist | - | regex pattern indicating whitelist files to be monitorized (ex. \\.xml) |
| dirs.1.blacklist | - | regex pattern indicating blacklist files to be excluded (ex. \\.xml) |
| dirs.2.dir | - | second directory configuration... |
| dirs.2.tag | - | ... |
| dirs.2.taglevel | - | ... |
| dirs.2.whitelist | - | ... |
| dirs.2.blacklist | - | ... |
| whitelist | - | regex pattern indicating whitelist files to be monitorized (ex. \\.xml). If it is set it will rewrite the directory one |
| blacklist | - | regex pattern indicating blacklist files to be excluded (ex. \\.xml). If it is set it will rewrite the directory one |
|readonstartup|false|Used in order to indicate if the agent have to proccess files existing in the directory on startup|
|pathtoser|true|The .ser file used by the recovery mecanism|
|timetoser|true|Time to generate ser file used by the recovery mecanism|

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
|pathtoser|true|The .ser file used by the recovery mecanism|
|timetoser|true|Time to generate ser file used by the recovery mecanism|
|followlinks|false|Follow symbolic links to directories referenced in monitorized directories
|fileHeader|false|Include file absolute path in events header
|fileHeaderKey||Key of file absolute path header
|basenameHeader|false|Include file base name in events header
|basenameHeaderKey||Key of file base name header
|eventsCapacity|1000|Number of events until channel injection|
|autocommittime|10|Number of seconds until channel injection|
|maxcharsonmessage|100000|Max characters for messages events, if greater then message is discarded|


##Configuration for multiline events files
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
|pathtoser|true|The .ser file used by the recovery mecanism|
|timetoser|true|Time to generate ser file used by the recovery mecanism|
|followlinks|false|Follow symbolic links to directories referenced in monitorized directories
|fileHeader|false|Include file absolute path in events header
|fileHeaderKey||Key of file absolute path header
|basenameHeader|false|Include file base name in events header
|basenameHeaderKey||Key of file base name header
|eventsCapacity|1000|Number of events until channel injection|
|autocommittime|10|Number of seconds until channel injection|
|maxcharsonmessage|100000|Max characters for messages events, if greater then message is discarded|
|multilineActive|false|Enable multiline events funcionality|
|multilineRegex| - |Regex pattern indicating when a event is a multiline event|
|multilineFirstLineRegex| - |Regex pattern indicating when a event is a first line of a multiline event|
|multilineNegateRegex|false|If true, a message not matching the pattern will constitute a match of a multiline event|
|multilineAssignToPreviousLine|true|Indicates the relation to the multi-line event. If true the headers of the first event will be the headers of the multiline event. Otherwise will be the headers of the last event|
|multilineFlushEntireBuffer|false|If true the pending multiline events are sent at the end of buffer processing (The entire buffer is processed)|


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
ag1.sources.r1.timetoser=5
ag1.sources.r1.eventsCapacity=10000
ag1.sources.r1.autocommittime=10
```

## Metrics
All metrics related to number of events are based on entering events. The outgoing events (exceptions joined events) are not kept in mind when metrics are calculated


## Log changes
* Fix a bug found under high load conditions


