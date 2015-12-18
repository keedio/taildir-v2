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
package org.keedio.flume.source.watchdir.listener.xmlsource;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.flume.Context;
import org.apache.flume.EventDrivenSource;
import org.apache.flume.conf.Configurable;
import org.apache.flume.source.AbstractSource;
import org.keedio.flume.source.watchdir.WatchDirEvent;
import org.keedio.flume.source.watchdir.WatchDirException;
import org.keedio.flume.source.watchdir.WatchDirFileSet;
import org.keedio.flume.source.watchdir.WatchDirListener;
import org.keedio.flume.source.watchdir.WatchDirObserver;
import org.keedio.flume.source.watchdir.listener.xmlsource.SerializeFilesThread;
import org.keedio.flume.source.watchdir.metrics.MetricsController;
import org.keedio.flume.source.watchdir.metrics.MetricsEvent;
import org.mortbay.log.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class WatchDirXMLWinEventSourceListener extends AbstractSource implements
		Configurable, EventDrivenSource, WatchDirListener {

	private static final String CONFIG_DIRS = "dirs.";
	private static final String DIR = "dir";
	private static final String WHITELIST = "whitelist";
	private static final String BLACKLIST = "blacklist";
	private static final String TAGNAME = "tag";
	private static final String TAGLEVEL = "taglevel";
	private static final String MAX_WORKERS = "maxworkers";
	private static final String BUFFER_SIZE = "buffersize";
	private static final String READ_ON_STARTUP = "readonstartup";
	private static final String SUFFIX = "suffix";
	private static final String PATH_TO_SER = "pathtoser";	
	private static final String TIME_TO_SER = "timetoser";	
	private static final String FOLLOW_LINKS = "followlinks";
	private static final Logger LOGGER = LoggerFactory
			.getLogger(WatchDirXMLWinEventSourceListener.class);
	private ExecutorService executor;
	private Set<WatchDirObserver> monitor; 
	private MetricsController metricsController;
	private Set<WatchDirFileSet> fileSets;
	private boolean readOnStartUp;
	private int maxWorkers = 10;
	protected int bufferSize = 1024;
	protected Map<String, Properties> dirProps;
	protected Set<String> filesObserved;
	private boolean followLinks;
	
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
		maxWorkers = context.getString(MAX_WORKERS)==null?10:Integer.parseInt(context.getString(MAX_WORKERS));
		bufferSize = context.getString(BUFFER_SIZE)==null?1024:Integer.parseInt(context.getString(BUFFER_SIZE));
		readOnStartUp = context.getBoolean(READ_ON_STARTUP)==null?false:context.getBoolean(READ_ON_STARTUP);
		followLinks = context.getBoolean(FOLLOW_LINKS)==null?false:context.getBoolean(FOLLOW_LINKS);
		dirProps = new HashMap<String, Properties>();
		String pathToSerialize = context.getString(PATH_TO_SER);
		int timeToSer = context.getInteger(TIME_TO_SER);
		
		// Lanzamos el proceso de serializacion
		SerializeFilesThread ser = new SerializeFilesThread(this, pathToSerialize, timeToSer);
		try {
			filesObserved = ser.getMapFromSerFile();
		} catch (Exception e) {
			filesObserved = new HashSet<String>();
		}
		new Thread(ser).start();

		// Creamos los filesets
		fileSets = new HashSet<WatchDirFileSet>();
		Iterator it = getCriterias.keySet().iterator();
		while (it.hasNext()) {
			Properties props = new Properties();
			Map<String, String> aux = (Map<String, String>)getCriterias.get(it.next());
			WatchDirFileSet auxSet = new WatchDirFileSet(aux.get(DIR), globalWhiteList!=null?globalWhiteList:aux.get(WHITELIST), globalBlackList!=null?globalBlackList:aux.get(BLACKLIST), readOnStartUp, 
					followLinks);
			props.put("tagName", aux.get(TAGNAME));
			props.put("tagLevel", aux.get(TAGLEVEL));
			
			fileSets.add(auxSet);
			dirProps.put(aux.get(DIR), props);
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
				WatchDirObserver aux = new WatchDirObserver(it.next(), -1);
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
	public void process(WatchDirEvent event) throws WatchDirException {

		// Si no esta instanciado el source informamos
		switch(event.getType()) {
		
			case "ENTRY_CREATE":
				// Comprobamos si el fichero ya fue procesado anteriormente
				if (getFilesObserved().contains(event.getPath())) break;
				
				metricsController.manage(new MetricsEvent(MetricsEvent.NEW_FILE));
				executor.submit(new WatchDirXMLWinWorker(this, event));
				// Notificamos nuevo fichero creado
				getFilesObserved().add(event.getPath());
				break;
			default:
				LOGGER.info("El evento " + event.getPath() + " no se trata.");
				break;
		}
	}

	public Set<String> getFilesObserved() {
		return filesObserved;
	}
	
}
