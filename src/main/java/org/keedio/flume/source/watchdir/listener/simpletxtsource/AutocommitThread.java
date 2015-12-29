package org.keedio.flume.source.watchdir.listener.simpletxtsource;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.keedio.flume.source.watchdir.listener.xmlsource.WatchDirXMLWinEventSourceListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutocommitThread implements Runnable {

	private FileEventSourceListener listener;
	private int seconds;
	private static final Logger LOGGER = LoggerFactory
			.getLogger(AutocommitThread.class);
	
	public AutocommitThread(FileEventSourceListener listener, int seconds) {
		this.listener = listener;
		this.seconds = seconds;
	}
	
	@Override
	public void run() {
			while (true) {
		    try {
  			  listener.getHelper().commitPendings();
  			  
  			  Thread.sleep(seconds);
		    } catch (Exception e) {
		      LOGGER.debug("Error en autocommit... Esperando...."); 
		    }
			}
		
	}

}
