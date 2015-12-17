package org.keedio.flume.source.watchdir;


import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javaxt.io.Directory;

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

	private WatchService watcherSvc;
	private List<WatchDirListener> listeners;
	private static final Logger LOGGER= LoggerFactory
			.getLogger(WatchDirObserver.class);
	private final Map<WatchKey, Path> keys;
	private WatchDirFileSet set;
	private List<WatchDirEvent> eventsToProcceed;
	// El acceso a eventsToProceed tiene que ser concurrente
	//public static Semaphore semaforo = new Semaphore(1);
	private int timeToProccessEvents;

	public int getGeneratedEvents() {
		return generatedEvents;
	}

	private int generatedEvents = 0;

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

		try {
			Path directotyToWatch = Paths.get(set.getPath());
			watcherSvc = FileSystems.getDefault().newWatchService();
			registerAll(java.nio.file.Paths.get(directotyToWatch.toString()));

		} catch (IOException e){
			LOGGER.info("No se puede monitorizar el directorio: " + set.getPath(), e);
		}
    }

    /**
     * Method used to record listeners. There must be at least one.
     * @param listener	Must implement WhatchDirListerner. See listeners implementations for more information
     */
    public void addWatchDirListener(WatchDirListener listener) {
    	listeners.add(listener);
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


	/*
    @Override
	public void run() {

    	if (listeners.isEmpty()) {
    		LOGGER.error("No existen listeners. Finalizando");
    	} else {
    		try {
    			boolean fin = false;
    			
    			// En primer lugar procesamos todos los ficheros pre-existentes
    			if (set.isReadOnStartup()) {
        			for(String file:set.getExistingFiles()) {
        				WatchDirEvent event = new WatchDirEvent(file, null, Directory.Event.CREATE, set);
						updateImmediately(event);
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
						generatedEvents++;
					}

					if (event!=null){
						LOGGER.debug(event.toString());
            			updateImmediately(new WatchDirEvent(event.getFile(), event.getOriginalFile(), event.getEventID(), set));
					}
				}
    		} catch (Exception e) {
    			LOGGER.info(e.getMessage(), e);
    		}
    	}
	}
	*/

	static <T> WatchEvent<T> castEvent(WatchEvent<?> event) {
		return (WatchEvent<T>)event;
	}

	@SuppressWarnings("unchecked")
	static <T> WatchEvent<T> cast(WatchEvent<?> event) {
		return (WatchEvent<T>) event;
	}

	private void registerAll(final java.nio.file.Path start) throws IOException {

		EnumSet<FileVisitOption> opts;

		if (set.isFollowLinks())
			opts = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
		else
			opts = EnumSet.noneOf(FileVisitOption.class);

		// register directory and sub-directories
		Files.walkFileTree(start, opts, Integer.MAX_VALUE, new SimpleFileVisitor<java.nio.file.Path>() {
			@Override
			public FileVisitResult preVisitDirectory(java.nio.file.Path dir, BasicFileAttributes attrs)
					throws IOException {
				register(Paths.get(dir.toString()));
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private void register(Path dir) throws IOException {

		LOGGER.trace("WatchDir: register");

		// Solo nos preocupamos por los ficheros de nueva creacion

		WatchKey key = null;
		try {
			key = dir.register(watcherSvc, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
		} catch (UnsupportedOperationException e) {
			LOGGER.debug("Eventos no soportados. Registramos solo CREATE, DELETED, MODIFY");
			key = dir.register(watcherSvc, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
		}
		Path prev = keys.get(key);

		LOGGER.info("Previous directory: " + prev);
		if (prev == null) {
			LOGGER.info("Registering directory: " + dir);
		} else {
			if (!dir.equals(prev)) {
				LOGGER.info("Updating previous directory: " + "-> " + prev + " to " + dir);
			}
		}

		keys.put(key, dir);

	}

	@Override
	public void run() {

		if (listeners.isEmpty()) {
			LOGGER.error("No existen listeners. Finalizando");
		} else {
			try {
				boolean fin = false;

				// En primer lugar procesamos todos los ficheros pre-existentes
				if (set.isReadOnStartup()) {
					for(String file:set.getExistingFiles()) {
						WatchDirEvent event = new WatchDirEvent(file, "ENTRY_CREATE", set);
						updateImmediately(event);
						LOGGER.debug("Fichero existente anteriormente:" + file + " .Se procesa");
					}
				}

				for (;;) {
					// wait for key to be signaled
					WatchKey key;
					key = watcherSvc.take();
					Path dir = keys.get(key);

					if (dir == null) {
						LOGGER.error("WatchKey not recognized!!");
						continue;
					}

					for (WatchEvent<?> event : key.pollEvents()) {
						try{
							WatchEvent.Kind<?> kind = event.kind();

							// Context for directory entry event is the file name of
							// entry
							WatchEvent<Path> ev = cast(event);
							Path name = ev.context();
							Path path = dir.resolve(name);

							// print out event
							LOGGER.trace(event.kind().name() + ": " + path);

							if (java.nio.file.Files.isDirectory(java.nio.file.Paths.get(path.toString()), LinkOption.NOFOLLOW_LINKS))
								registerAll(java.nio.file.Paths.get(path.toString()));
							else {
								//if (set.haveToProccess(path.toString())) {
								updateImmediately(new WatchDirEvent(path.toString(), event.kind().name(), set));
								//}
							}

						} catch (IOException x) {
							LOGGER.error(x.getMessage(), x);
						}
					}
					// reset key and remove from set if directory no longer
					// accessible
					boolean valid = key.reset();
					if (!valid) {
						keys.remove(key);
						// all directories are inaccessible
						if (keys.isEmpty()) {
							break;
						}
					}
				}

/*
				while (!fin) {
					// wait for key to be signaled
					WatchKey key;
					key = watcherSvc.take();
					Path dir = keys.get(key);

					for (WatchEvent<?> event : key.pollEvents()) {
						WatchEvent<Path> ev = cast(event);
						Path name = ev.context();
						Path path = dir.resolve(name);

						// Si se crea un nuevo directorio es necesario registrarlo de nuevo
						if (java.nio.file.Files.isDirectory(java.nio.file.Paths.get(path.toString()), LinkOption.NOFOLLOW_LINKS))
							registerAll(java.nio.file.Paths.get(path.toString()));
						else {
							//if (set.haveToProccess(path.toString())) {
								updateImmediately(new WatchDirEvent(path.toString(), event.kind().name(), set));
							//}
						}

					}

					// reset key and remove from set if directory no longer
					// accessible
					key.reset();

					Thread.sleep(1000);
				}
*/
			} catch (InterruptedException e) {
				LOGGER.info(e.getMessage(), e);
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
