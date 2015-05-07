#flume-taildir-v2-source
Source of Flume NG for tailing files in a directory. This source is based on flume-taildirectory version.
Next feature and fixes where added to the previous one:
* The java native watch service was replaced by jpathwatch. Now events on file renames are triggered
* Whitelist and blacklist for including/excluding files to monitor
* Recovery mechanism in case flume dies
* Now, it is possible to read files on startup

#Compilation
```
mvn clean package
```

#Dependecies
This project depends on next libs:
* jpathwatch (v 0.95)
* metrics-core (v 3.1.1)

Use
===
Make the directory in flume installation path ```$FLUME_HOME/plugins.d/taildir-v2/lib``` and copy the file   ```flume-taild-v2-X.Y.Z.jar``` and  ```jpathwatch-0.95.jar, metrics-core-3.1.1.jar``` in libext.  
Edit flume configuration file with the parameters above.

Configuration
=============
| Property Name | Default | Description |
| ------------- | :-----: | :---------- |
| Channels | - |  |
| Type | - | org.keedio.watchdir.listener.WatchDirXMLWinEventSourceListener |
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


* Example
```
# Describe/configure the source
ag1.sources.r1.type = org.keedio.watchdir.listener.FileEventSourceListener
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
