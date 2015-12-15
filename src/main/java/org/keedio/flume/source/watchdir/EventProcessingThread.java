package org.keedio.flume.source.watchdir;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public class EventProcessingThread implements Runnable {

	private WatchDirObserver observer;
	private int seconds;
	private static final Logger LOGGER = LoggerFactory
			.getLogger(EventProcessingThread.class);

	public EventProcessingThread(WatchDirObserver observer, int seconds) {
		this.observer = observer;
		this.seconds = seconds;
	}
	
	@Override
	public void run() {
		try {
			while (true) {
				process(observer.getEventsToProcceed());

				Thread.sleep(seconds*1000);
			}
		} catch (Exception e) {
			LOGGER.debug("Error en la lectura del fichero, todavía no se ha generado."); 
		}
		
	}

	private void process(List<WatchDirEvent> eventsToProcceed) throws Exception {
		LOGGER.debug("Processing " + eventsToProcceed.size() + " lines");
		observer.semaforo.acquire();
    	for (WatchDirListener listener:observer.getListeners()) {
    		try{
				for (WatchDirEvent event:eventsToProcceed) {
					listener.process(event);
				}

				eventsToProcceed.clear();
    		} catch (WatchDirException e) {
    			LOGGER.info("Error procesando el listener", e);
    		} finally {
				observer.semaforo.release();
			}
		}
	}

}
