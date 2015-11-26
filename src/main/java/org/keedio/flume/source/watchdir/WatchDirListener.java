package org.keedio.flume.source.watchdir;

public interface WatchDirListener {

	
	/**
	 * In this method the logic is implemented to perform once notified the file creation event.
	 * @param event	Event to process
	 * @throws WatchDirException if an error occurs while processing.
	 */
	public void process(WatchDirEvent event) throws WatchDirException;

}
