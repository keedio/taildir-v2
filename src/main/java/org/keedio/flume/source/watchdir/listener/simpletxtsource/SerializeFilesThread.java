package org.keedio.flume.source.watchdir.listener.simpletxtsource;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import org.keedio.flume.source.watchdir.InodeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SerializeFilesThread implements Runnable {

	private FileEventSourceListener listener;
	private String path;
	private int seconds;
	private static final Logger LOGGER = LoggerFactory
			.getLogger(SerializeFilesThread.class);
	
	public SerializeFilesThread(FileEventSourceListener listener, String path, int seconds) {
		this.listener = listener;
		this.path = path;
		this.seconds = seconds;
	}
	
	@Override
	public void run() {
		try {
			while (true) {
				fromMapToSerFile();

				Thread.sleep(seconds * 1000);
			}
		} catch (Exception e) {
			LOGGER.debug("Error en la lectura del fichero, todavía no se ha generado."); 
		}
		
	}
	
	public Map<String, InodeInfo> getMapFromSerFile() throws Exception {
		Map<String, InodeInfo> map = null;
		
		FileInputStream fis = new FileInputStream(path);
		ObjectInputStream ois = new ObjectInputStream(fis);
		map = (Map<String, InodeInfo>) ois.readObject();
			
		return map;
			
	}

	public void fromMapToSerFile() throws Exception {
		FileOutputStream fos = new FileOutputStream(path);
		ObjectOutputStream oos = new ObjectOutputStream(fos);
		oos.writeObject(listener.getFilesObserved());			
	}

}
