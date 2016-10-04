/***************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 ****************************************************************/
package org.keedio.flume.source.watchdir.listener.simpletxtsource;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.flume.Context;
import org.apache.flume.EventDrivenSource;
import org.apache.flume.conf.Configurable;
import org.apache.flume.source.AbstractSource;
import org.keedio.flume.source.watchdir.CleanRemovedEventsProcessingThread;
import org.keedio.flume.source.watchdir.InodeInfo;
import org.keedio.flume.source.watchdir.WatchDirEvent;
import org.keedio.flume.source.watchdir.WatchDirException;
import org.keedio.flume.source.watchdir.WatchDirFileSet;
import org.keedio.flume.source.watchdir.WatchDirListener;
import org.keedio.flume.source.watchdir.WatchDirObserver;
import org.keedio.flume.source.watchdir.listener.LineReadListener;
import org.keedio.flume.source.watchdir.metrics.MetricsController;
import org.keedio.flume.source.watchdir.metrics.MetricsEvent;
import org.keedio.flume.source.watchdir.util.Util;
import org.mortbay.log.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import org.keedio.flume.source.watchdir.listener.simpletxtsource.util.ChannelAccessor;


/**
 * Implementation of a source of flume that consumes XML files that follow
 * the standard architecture for monitoring events microsoft (WMI standard).
 * <p>
 * The different events of the created file (Event tag block) are extracted and
 * sent to the corresponding channel.
 */
public class FileEventSourceListener extends AbstractSource implements
        Configurable, EventDrivenSource, WatchDirListener {

    private static final String CONFIG_DIRS = "dirs.";
    private static final String DIR = "dir";
    private static final String WHITELIST = "whitelist";
    private static final String BLACKLIST = "blacklist";
    private static final String TAGNAME = "tag";
    private static final String TAGLEVEL = "taglevel";
    private static final String READ_ON_STARTUP = "readonstartup";
    private static final String PATH_TO_SER = "pathtoser";
    private static final String TIME_TO_SER = "timetoser";
    private static final String FOLLOW_LINKS = "followlinks";
    private static final String FILE_HEADER = "fileHeader";
    private static final String FILE_HEADER_NAME = "fileHeaderKey";
    private static final String BASE_HEADER = "basenameHeader";
    private static final String BASE_HEADER_NAME = "basenameHeaderKey";
    private static final String EVENTS_CAPACITY = "eventsCapacity";
    private static final String AUTOCOMMIT_TIME = "autocommittime";
    private static final String MAX_CHARS = "maxcharsonmessage";
    private static final String MULTILINE_ACTIVE = "multilineActive";
    private static final String MULTILINE_REGEX = "multilineRegex";
    private static final String MULTILINE_FIRST_LINE_REGEX = "multilineFirstLineRegex";
    private static final String MULTILINE_NEGATE_REGEX = "multilineNegateRegex";
    private static final String MULTILINE_ASIGN_TO_PREVIOUS_LINE = "multilineAssignToPreviousLine";
    private static final String MULTILINE_FLUSH_ENTIRE_BUFFER = "multilineFlushEntireBuffer";
    private static final Logger LOGGER = LoggerFactory

            .getLogger(FileEventSourceListener.class);
    private Set<WatchDirObserver> monitor;
    private MetricsController metricsController;
    protected Set<WatchDirFileSet> fileSets;
    protected Map<String, Properties> dirProps;
    private boolean readOnStartUp;
    private int maxWorkers = 10;
    protected int bufferSize = 1024;
    protected String suffix;
    protected Map<String, InodeInfo> filesObserved;
    protected SerializeFilesThread ser;
    private boolean followLinks;
    protected boolean fileHeader;
    protected String fileHeaderName;
    protected boolean baseHeader;
    protected String baseHeaderName;
    protected int eventsCapacity;
    protected int autocommittime;
    protected int maxchars;
    protected FileEventHelper helper;
    private Map<String, Lock> locks;
    
    private Thread autoCommitThread = null;
    private Thread cleanRemovedEventsProcessingThread = null;
    private Thread serializeFilesThread = null;

    //Multiline
    protected boolean multilineActive;
    protected String multilineRegex;
    protected String multilineFirstLineRegex;
    protected boolean multilineNegateRegex;
    protected boolean multilineAssignToPreviousLine;
    protected boolean multilineFlushEntireBuffer;
    protected Pattern patternMultilineRegex;
    protected Pattern patternMultilineFirstLineRegex;






    public void setLineReadListener(LineReadListener lineReadListener) {
        helper.setLineReadListener(lineReadListener);
    }

    public synchronized MetricsController getMetricsController() {
        return metricsController;
    }

    public Set<WatchDirObserver> getMonitor() {
        return monitor;
    }

    public void setMonitor(Set<WatchDirObserver> monitor) {
        this.monitor = monitor;
    }

    public FileEventHelper getHelper() {
        return helper;
    }

    @Override
    public void configure(Context context) {
        LOGGER.info("Source Configuring..");
        printVersionNumber();
        
        metricsController = new MetricsController();

        Map<String, String> criterias = context.getSubProperties(CONFIG_DIRS);
        Map getCriterias = getMapProperties(criterias);

        String globalWhiteList = context.getString(WHITELIST);
        String globalBlackList = context.getString(BLACKLIST);
        String pathToSerialize = context.getString(PATH_TO_SER);
        int timeToSer = context.getInteger(TIME_TO_SER);
        readOnStartUp = context.getBoolean(READ_ON_STARTUP) == null ? false : context.getBoolean(READ_ON_STARTUP);
        followLinks = context.getBoolean(FOLLOW_LINKS) == null ? false : context.getBoolean(FOLLOW_LINKS);
        fileHeader = context.getBoolean(FILE_HEADER) == null ? false : context.getBoolean(FILE_HEADER);
        fileHeaderName = context.getString(FILE_HEADER_NAME);
        baseHeader = context.getBoolean(BASE_HEADER) == null ? false : context.getBoolean(BASE_HEADER);
        baseHeaderName = context.getString(BASE_HEADER_NAME);
        eventsCapacity = context.getInteger(EVENTS_CAPACITY) == null ? 1000 : context.getInteger(EVENTS_CAPACITY);
        autocommittime = context.getInteger(AUTOCOMMIT_TIME) == null ? 10000 : context.getInteger(AUTOCOMMIT_TIME) * 1000;
        maxchars = context.getInteger(MAX_CHARS) == null ? 100000 : context.getInteger(MAX_CHARS);

        //Multiline
        multilineActive = context.getBoolean(MULTILINE_ACTIVE) == null ? false : context.getBoolean(MULTILINE_ACTIVE);
        multilineRegex = context.getString(MULTILINE_REGEX);
        multilineFirstLineRegex = context.getString(MULTILINE_FIRST_LINE_REGEX);
        multilineNegateRegex = context.getBoolean(MULTILINE_NEGATE_REGEX) == null ? false : context.getBoolean(MULTILINE_NEGATE_REGEX);
        multilineAssignToPreviousLine = context.getBoolean(MULTILINE_ASIGN_TO_PREVIOUS_LINE) == null ? true : context.getBoolean(MULTILINE_ASIGN_TO_PREVIOUS_LINE);
        multilineFlushEntireBuffer = context.getBoolean(MULTILINE_FLUSH_ENTIRE_BUFFER) == null ? false : context.getBoolean(MULTILINE_FLUSH_ENTIRE_BUFFER);

        //En caso de ser necesario compilamos los patterns de las expresiones regulares (general y de primera línea)
        if ((multilineActive) && (multilineRegex != null) && (!"".equals(multilineRegex))) {
            patternMultilineRegex = Pattern.compile(multilineRegex);
        }
        if ((patternMultilineRegex != null) && (multilineFirstLineRegex != null) && (!"".equals(multilineFirstLineRegex))) {
            patternMultilineFirstLineRegex = Pattern.compile(multilineFirstLineRegex);
        }



        // Lanzamos el proceso de serializacion
        if (ser == null)
            ser = new SerializeFilesThread(this, pathToSerialize, timeToSer);
        
        try {
            filesObserved = ser.getMapFromSerFile();
        } catch (Exception e) {
            LOGGER.info("No se pudo deserializar el fichero.");
            filesObserved = new HashMap<String, InodeInfo>();
        }

        // Creamos los filesets
        
        if (fileSets == null)
            fileSets = new HashSet<WatchDirFileSet>();
        
        locks = new HashMap<String, Lock>();
        Iterator it = getCriterias.keySet().iterator();
        while (it.hasNext()) {
            Map<String, String> aux = (Map<String, String>) getCriterias.get(it.next());
            WatchDirFileSet auxSet = new WatchDirFileSet(aux.get(DIR), globalWhiteList != null ? globalWhiteList : aux.get(WHITELIST), globalBlackList != null ? globalBlackList : aux.get(BLACKLIST), readOnStartUp, followLinks);

            fileSets.add(auxSet);
        }

        //helper = new FileEventHelper(this);
        Preconditions.checkState(!fileSets.isEmpty(), "Bad configuration, review documentation on https://github.com/keedio/XMLWinEvent/blob/master/README.md");

        serializeFilesThread = new Thread(ser,"SerializeFilesThread");
        serializeFilesThread.start();
        autoCommitThread =  new Thread(new AutocommitThread(this, autocommittime),"AutocommitThread");
        
        cleanRemovedEventsProcessingThread = new Thread(new CleanRemovedEventsProcessingThread(this, autocommittime),"CleanRemovedEventsProcessingThread");
        cleanRemovedEventsProcessingThread.start();
    }

    public static Map<String, Map<String, String>> getMapProperties(Map<String, String> all) {

        Map<String, Map<String, String>> map = new HashMap<String, Map<String, String>>();
        Iterator<String> it = all.keySet().iterator();

        while (it.hasNext()) {
            String key = it.next();
            String[] aux = key.split("\\.");
            String mapKey = aux[0];
            String auxKey = aux[1];
            String auxValue = all.get(key);

            if (!map.containsKey(mapKey)) {
                Map<String, String> auxMap = new HashMap<String, String>();
                auxMap.put(auxKey, auxValue);

                map.put(mapKey, auxMap);
            } else {
                map.get(mapKey).put(auxKey, auxValue);
            }

        }

        return map;
    }

    @Override
    public void start() {
        LOGGER.info("Source Starting..");
        monitor = new HashSet<WatchDirObserver>();

        try {
            Iterator<WatchDirFileSet> it = fileSets.iterator();

            while (it.hasNext()) {
                WatchDirObserver aux = new WatchDirObserver(it.next());
                aux.addWatchDirListener(this);

                Log.debug("Lanzamos el proceso");
                new Thread(aux).start();

                monitor.add(aux);
            }
            metricsController.start();


        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        super.start();
        
        if (helper == null) {//during tests a mock helper instance is previosly configured
            ChannelAccessor.init(getChannelProcessor());
            helper = new FileEventHelper(this);
        }

        autoCommitThread.start();
    }

    @Override
    public void stop() {
        LOGGER.info("Stopping source");
        try {
            ser.fromMapToSerFile();
        } catch (Exception e) {
            LOGGER.error("Error al serializar el mapa");
        }
        metricsController.stop();
        
        if (serializeFilesThread != null && serializeFilesThread.isAlive()){
            serializeFilesThread.interrupt();
        }
        if (autoCommitThread != null && autoCommitThread.isAlive()){ 
            autoCommitThread.interrupt();
        }
        if (cleanRemovedEventsProcessingThread != null && cleanRemovedEventsProcessingThread.isAlive()){
            cleanRemovedEventsProcessingThread.interrupt();
        }
        
        super.stop();
    }

    @Override
    public synchronized void process(WatchDirEvent event) throws WatchDirException {

        String inode;
        InodeInfo info;
        Map<String, InodeInfo> inodes = getFilesObserved();
        
        try {
            // Si no esta instanciado el source informamos
            switch (event.getType()) {

                case "ENTRY_CREATE":
                    inode = Util.getInodeID(event.getPath());
                    info = inodes.get(inode);

                    //Comprobamos si el inodo no existia, en cuyo caso se crea. Si ya existia viene de una renombrado.
                    if (!inodes.containsKey(Util.getInodeID(event.getPath()))) {
                        if (event.getSet().haveToProccess(event.getPath())) {
                            InodeInfo inf = new InodeInfo(0L, event.getPath());
                            
                            synchronized (inodes) {
                                inodes.put(inode, inf);
                            }
                            metricsController.manage(new MetricsEvent(MetricsEvent.NEW_FILE));
                            if (event.getSet().haveToProccess(event.getPath())) 
                                helper.process(inode);
                            LOGGER.debug("EVENTO NEW: " + event.getPath() + " inodo: " + inode);
                        } else {
                            LOGGER.debug("File '"+event.getPath()+"' will not be added to the list of observed files");
                        }
                        
                    } else {
                        // Viene de rotado. 

                        // Cambiamos el nombre del fichero
                        String oldPth = info.getFileName();

                        LOGGER.debug("EVENTO RENAME: " + oldPth + " a " + event.getPath() + " inodo: " + inode);

                        if (event.getPath().equals(oldPth)) {
                            break;
                        }

                        // Procesamo los eventos pendientes en el fichero rotado.
                        if (event.getSet().haveToProccess(oldPth)) {
                            LOGGER.debug("Processing pending lines for inode:" + inode);
                            info.setFileName(event.getPath());
                            helper.process(inode);
                        }
                        
                        /* 
                        se marca el INODE para que NO se vuelva a gestionar.
                        
                        ¿Porqué esto lo hemos movido fuera del IF?
                        
                        Se ha verificado la siguiente casuistica:
                        - Hay una whitelist que especifica el patrón "kosmos-access_log".
                        - En el directorio monitorizado existen dos ficheros:
                            - kosmos-access_log, inode 273
                            - kosmos-error_log, inode 269 (este segundo fichero nunca se procesa)
                        - Los dos ficheros rotan contemporaneamente a:
                            - kosmos-access_log.2016-08-14
                            - kosmos-error_log.2016-08-14
                            
                        - Se generan dos nuevos ficheros:
                            - kosmos-access_log, inode 269!!
                            - kosmos-error_log, inode 284
                            
                        Se genera un evento de tipo rename kosmos-error_log a kosmos-acces_log 
                            (el nuevo fichero ereda el mismo inode ID!!)
                        La condición del IF no se virifica pq el "oldPath" es relativo a un fichero que 
                            no se tenía que procesar y el listado de filesObserved se queda en un estado inconsistente.
                            
                        Por lo tanto, siempre es necesario actualizar el listado de ficheros a procesar.
                        
                        Se ha añadido la clase de test:
                        org.keedio.flume.source.watchdir.listener.simpletxtsource.FileEventSourceListenerStaticTest
                        para comprobar de forma automatica esta condición.
                        */
                        synchronized (inodes) {
                            inodes.remove(inode);
                        }
                    }
                    // Notificamos nuevo fichero creado
                    break;
                case "ENTRY_MODIFY":
                    inode = Util.getInodeID(event.getPath());
                    info = inodes.get(inode);

                    if (info == null) {
                        LOGGER.debug("Se inserta en fichero no monitorizado. Continuamos " + event.getPath() + ", inodo: " + inode);

                        if (event.getSet().haveToProccess(event.getPath())) {
                            LOGGER.debug("Fichero no catalogado, se añade a la lista de ficheros monitorizados: " + event.getPath());
                            InodeInfo ii = new InodeInfo(0L, event.getPath());
                            synchronized (inodes) {
                                inodes.put(inode, ii);
                            }
                            helper.process(inode);
                        }
                        break;
                    } else if (event.getSet().haveToProccess(event.getPath())) {
                        LOGGER.debug("Processing modified file " + event.getPath() + " with inode: " + inode);
                        helper.process(inode);
                    }
                    LOGGER.debug("EVENTO MODIFY: " + event.getPath() + " inodo: " + inode);
                    break;
                case "ENTRY_DELETE":
                    // No podemos obtener el inodo, el fichero ya no existe.
                    LOGGER.debug("EVENTO DELETE: " + event.getPath());
                    break;
                default:
                    LOGGER.info("EVENTO UNKNOWN" + event.getPath() + " no se trata.");
                    break;
            }

        } catch (WatchDirException e) {
            return;
        }
    }

    public synchronized Map<String, InodeInfo> getFilesObserved() {
        return filesObserved;
    }
    
    private void printVersionNumber(){
        try {
            final Properties properties = new Properties();
            properties.load(this.getClass().getClassLoader().getResourceAsStream("taildir-v2.properties"));
            
            String groupId = properties.getProperty("groupId");
            String artifactId = properties.getProperty("artifactId");
            String version = properties.getProperty("version");
            String mvnCoords = groupId + ":" + artifactId + ":" + version;
            LOGGER.info("Starting taildir agent '" + mvnCoords + "'");
        } catch (Exception ex){
            LOGGER.error("Cannot retrieve taildir agent version number");
        }
    }
}	
