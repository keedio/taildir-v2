package org.keedio.flume.source.watchdir;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javaxt.io.Directory;
import name.pachler.nio.file.Path;
import name.pachler.nio.file.WatchKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * This thread monitors the directory indicated in the constructor recursively. 
 * At the time believed a new file to all 
 * registered listeners are notified. 
 * <p>
 * Events deleted or modified files are not managed.
 *
 */
public class WatchDirObserver implements Runnable {

	private List<WatchDirListener> listeners;
	private static final Logger LOGGER= LoggerFactory
			.getLogger(WatchDirObserver.class);
	private final Map<WatchKey, Path> keys;
	private WatchDirFileSet set;
	private List<WatchDirEvent> eventsToProcceed;
	// El acceso a eventsToProceed tiene que ser concurrente
	public static Semaphore semaforo = new Semaphore(1);
	private int timeToProccessEvents;

	public List<WatchDirEvent> getEventsToProcceed() {
		return eventsToProcceed;
	}


	public synchronized List<WatchDirListener> getListeners() {
		return listeners;
	}
	
    public WatchDirObserver(WatchDirFileSet set, int timeToProccessEvents) {
    	this.set = set;
    	keys = new HashMap<WatchKey, Path>();
    	listeners = new ArrayList<WatchDirListener>();
		eventsToProcceed = new ArrayList<WatchDirEvent>();
		this.timeToProccessEvents = timeToProccessEvents;
    }

    /**
     * Method used to record listeners. There must be at least one.
     * @param listener	Must implement WhatchDirListerner. See listeners implementations for more information
     */
    public void addWatchDirListener(WatchDirListener listener) {
    	listeners.add(listener);
    }

	protected void update(WatchDirEvent event) throws Exception {
		// En caso de configurar el tiempo a -1 se procesan tan pronto como se reciben los eventos.
		if (timeToProccessEvents!=-1) {
			semaforo.acquire();
			eventsToProcceed.add(event);
			semaforo.release();
		} else
			updateImmediately(event);

	}


    protected void updateImmediately(WatchDirEvent event) {
    	for (WatchDirListener listener:getListeners()) {
    		try{
        		listener.process(event);
    		} catch (WatchDirException e) {
    			LOGGER.info("Error procesando el listener", e);
    		}
    	}
    }



    @Override
	public void run() {

		// Lanzamos el prceso de procesado programado de eventos
		if (timeToProccessEvents != -1) {
			EventProcessingThread ept = new EventProcessingThread(this, timeToProccessEvents);
			new Thread(ept).start();
		}

    	if (listeners.isEmpty()) {
    		LOGGER.error("No existen listeners. Finalizando");
    	} else {
    		try {
    			boolean fin = false;
    			
    			// En primer lugar procesamos todos los ficheros pre-existentes
    			if (set.isReadOnStartup()) {
        			for(String file:set.getExistingFiles()) {
        				WatchDirEvent event = new WatchDirEvent(file, null, Directory.Event.CREATE, set);
						update(event);
        				LOGGER.debug("Fichero existente anteriormente:" + file + " .Se procesa");
        			}
    			}

				javaxt.io.Directory directory = new javaxt.io.Directory(set.getPath());
				java.util.List events = directory.getEvents();


				while (true){

					javaxt.io.Directory.Event event;
					synchronized (events) {
						while (events.isEmpty()) {
							try {
								events.wait();
							}
							catch (InterruptedException e) {
							  e.printStackTrace();
							}
						}
						event = (javaxt.io.Directory.Event)events.remove(0);
					}

					if (event!=null){
						LOGGER.debug(event.toString());
						if (set.haveToProccess(event.getFile())) {
	            			update(new WatchDirEvent(event.getFile(), event.getOriginalFile(), event.getEventID(), set));
						}
					}
				}
    		} catch (Exception e) {
    			LOGGER.info(e.getMessage(), e);
    		}
    	}
	}
    
    public static boolean match(String patterns, String string) {
    	
    	String[] splitPat = patterns.split(",");
    	boolean match = false;
    	
    	for (String pattern:splitPat) {
        	Pattern pat = Pattern.compile(pattern + "$");
        	Matcher mat = pat.matcher(string);
        	
        	match = match || mat.find();
        	
        	if (match) break;
    	}
    	
    	
    	return match;
    }

}
