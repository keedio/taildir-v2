package org.keedio.flume.source.watchdir;

import org.keedio.flume.source.watchdir.listener.simpletxtsource.FileEventHelper;
import org.keedio.flume.source.watchdir.listener.simpletxtsource.FileEventSourceListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

public class EventProcessingThread implements Runnable {

	private FileEventSourceListener listener;
	private int seconds;
	private static final Logger LOGGER = LoggerFactory
			.getLogger(EventProcessingThread.class);

	public int getProcessedEvents() {
		return processedEvents;
	}

	private int processedEvents = 0;

	public EventProcessingThread(FileEventSourceListener listener, int seconds) {
		this.listener = listener;
		this.seconds = seconds;
	}
	
	@Override
	public void run() {
		try {
			while (true) {
				synchronized (listener.getFilesObserved()) {
					process(listener.getFilesObserved());
				}
				Thread.sleep(seconds * 1000);
			}
		} catch (Exception e) {
			LOGGER.debug("Error en la lectura del fichero, todavía no se ha generado."); 
		}
		
	}

	private void process(Map<String, InodeInfo> inodes) throws Exception {
		LOGGER.debug("Processing ");
    	for (String inodeKey:inodes.keySet()) {

    		try{
				InodeInfo inode = inodes.get(inodeKey);
				if (inode.isProcess()) {
					FileEventHelper helper = new FileEventHelper(listener, inode.getFileName(), inode.getPosition());
					Long offset = helper.launchEvents();

					// Acualizamos la posicion
					inode.setPosition(offset);
					inode.setProcess(false);
					listener.getFilesObserved().put(inodeKey, inode);
				}
    		} catch (Exception e) {
    			LOGGER.info("Error procesando el listener", e);
    		}
		}
	}

}
