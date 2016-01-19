/***************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 ****************************************************************/
package org.keedio.flume.source.watchdir.listener.simpletxtsource;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
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
import org.apache.flume.Context;
import org.apache.flume.EventDrivenSource;
import org.apache.flume.conf.Configurable;
import org.apache.flume.source.AbstractSource;
import org.keedio.flume.source.watchdir.*;
import org.keedio.flume.source.watchdir.metrics.MetricsController;
import org.keedio.flume.source.watchdir.metrics.MetricsEvent;
import org.keedio.flume.source.watchdir.util.Util;
import org.mortbay.log.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import com.google.common.base.Preconditions;


/**
 * 
 * Implementation of a source of flume that consumes XML files that follow 
 * the standard architecture for monitoring events microsoft (WMI standard). 
 * <p>
 * The different events of the created file (Event tag block) are extracted and
 * sent to the corresponding channel.
 *
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
	private static final String TIME_TO_PROCESS_EVENTS = "timetoprocessevents";
	private static final Logger LOGGER = LoggerFactory
			.getLogger(FileEventSourceListener.class);
	private ExecutorService executor;
	private Set<WatchDirObserver> monitor; 
	private MetricsController metricsController;
	private Set<WatchDirFileSet> fileSets;
	protected Map<String, Properties> dirProps;
	private boolean readOnStartUp;
	private int maxWorkers = 10;
	protected int bufferSize = 1024;
	protected String suffix;
	protected Map<String, InodeInfo> filesObserved;
	private SerializeFilesThread ser;
	private boolean followLinks;
	protected boolean fileHeader;
	protected String fileHeaderName;
	protected boolean baseHeader;
	protected String baseHeaderName;
	private int timeToProcessEvents;
	
	public synchronized MetricsController getMetricsController() {
		return metricsController;
	}

	public Set<WatchDirObserver> getMonitor() {
		return monitor;
	}

	public void setMonitor(Set<WatchDirObserver> monitor) {
		this.monitor = monitor;
	}

	@Override
	public void configure(Context context) {
		
		LOGGER.info("Source Configuring..");
		
		metricsController = new MetricsController();
		
		Map<String, String> criterias = context.getSubProperties(CONFIG_DIRS);
		Map getCriterias = getMapProperties(criterias);
		
		String globalWhiteList = context.getString(WHITELIST);
		String globalBlackList = context.getString(BLACKLIST);
		String pathToSerialize = context.getString(PATH_TO_SER);
		int timeToSer = context.getInteger(TIME_TO_SER);
		readOnStartUp = context.getBoolean(READ_ON_STARTUP)==null?false:context.getBoolean(READ_ON_STARTUP);
		followLinks = context.getBoolean(FOLLOW_LINKS)==null?false:context.getBoolean(FOLLOW_LINKS);
		fileHeader = context.getBoolean(FILE_HEADER)==null?false:context.getBoolean(FILE_HEADER);
		fileHeaderName = context.getString(FILE_HEADER_NAME);
		baseHeader = context.getBoolean(BASE_HEADER)==null?false:context.getBoolean(BASE_HEADER);
		timeToProcessEvents = context.getInteger(TIME_TO_PROCESS_EVENTS)==null?10:context.getInteger(TIME_TO_PROCESS_EVENTS);
		baseHeaderName = context.getString(BASE_HEADER_NAME);
		
		// Lanzamos el proceso de serializacion
		ser = new SerializeFilesThread(this, pathToSerialize, timeToSer);
		try {
			filesObserved = ser.getMapFromSerFile();
		} catch (Exception e) {
			filesObserved = new HashMap<String, InodeInfo>();
		}
		new Thread(ser).start();
		new Thread(new CleanRemovedEventsProcessingThread(this, timeToProcessEvents)).start();
		new Thread(new EventProcessingThread(this, timeToProcessEvents)).start();

		// Creamos los filesets
		fileSets = new HashSet<WatchDirFileSet>();
		Iterator it = getCriterias.keySet().iterator();
		while (it.hasNext()) {
			Map<String, String> aux = (Map<String, String>)getCriterias.get(it.next());
			WatchDirFileSet auxSet = new WatchDirFileSet(aux.get(DIR), globalWhiteList!=null?globalWhiteList:aux.get(WHITELIST), globalBlackList!=null?globalBlackList:aux.get(BLACKLIST), readOnStartUp, followLinks);
			
			fileSets.add(auxSet);
		}

		Preconditions.checkState(!fileSets.isEmpty(), "Bad configuration, review documentation on https://github.com/keedio/XMLWinEvent/blob/master/README.md");	

	}
	
	public static Map<String, Map<String, String>> getMapProperties(Map<String, String> all) {
		
		Map<String, Map<String, String>> map = new HashMap<String, Map<String,String>>();
		Iterator<String> it = all.keySet().iterator();
		
		while(it.hasNext()){
			String key = it.next();
			String[]aux = key.split("\\.");
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



		executor = Executors.newFixedThreadPool(maxWorkers);
		monitor = new HashSet<WatchDirObserver>();
		
		try {
			Iterator<WatchDirFileSet> it = fileSets.iterator();
			
			while(it.hasNext()) {
				WatchDirObserver aux = new WatchDirObserver(it.next(), timeToProcessEvents);
				aux.addWatchDirListener(this);

				Log.debug("Lanzamos el proceso");
				new Thread(aux).start();

				monitor.add(aux);
			}
			metricsController.start();
			
			
		} catch (Exception e) {
			LOGGER.error(e.getMessage(),e);
		}

		super.start();
	}

	@Override
	public void stop() {
		LOGGER.info("Stopping source");
		executor.shutdown();
		metricsController.stop();
		super.stop();
	}
	
	@Override
	public void  process(WatchDirEvent event) throws WatchDirException {

		Path path = null;
		Path oldPath = null;
		String inode;
		
		try {
	    inode = Util.getInodeID(event.getPath());		  
		} catch (WatchDirException e) {
		  //En caso de borrado de ficheros no podemos obtener el inode
		  LOGGER.warn("Could not get file props because it was removed");
		  return;
		}

		// Si no esta instanciado el source informamos

		switch(event.getType()) {
		
			case "ENTRY_CREATE":
				//Comprobamos si el inodo no existia, en cuyo caso se crea. Si ya existia viene de una renombrado.
				if (!getFilesObserved().containsKey(Util.getInodeID(event.getPath()))) {
					if (event.getSet().haveToProccess(event.getPath())) {
						InodeInfo info = new InodeInfo(0L, event.getPath());
						getFilesObserved().put(inode, info);
						metricsController.manage(new MetricsEvent(MetricsEvent.NEW_FILE));

						LOGGER.debug("Se ha creado el fichero de eventos: " + event.getPath());
					}
				} else {
					// Viene de rotado. Cambiamos el nombre del fichero
					InodeInfo old = getFilesObserved().get(inode);
					old.setFileName(event.getPath());
					old.setProcess(true);
					// y se marca para que no se vuelva a gestionar
					getFilesObserved().put(inode, old);
				}
				// Notificamos nuevo fichero creado
				break;
			case "ENTRY_MODIFY":
				if (!event.getSet().haveToProccess(event.getPath())) break;
				InodeInfo old = getFilesObserved().get(inode);
				if (!old.isProcess()) {
					old.setProcess(true);
					getFilesObserved().put(inode, old);
				}
				break;
			default:
				LOGGER.info("El evento " + event.getPath() + " no se trata.");
				break;
		}
	}

	public synchronized Map<String, InodeInfo> getFilesObserved() {
		return filesObserved;
	}
}	
