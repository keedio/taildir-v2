package org.keedio.flume.source.watchdir.listener.xmlsource;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class SerializeFilesThread implements Runnable {

	private WatchDirXMLWinEventSourceListener listener;
	private String path;
	private int seconds;
	
	public SerializeFilesThread(WatchDirXMLWinEventSourceListener listener, String path, int seconds) {
		this.listener = listener;
		this.path = path;
		this.seconds = seconds;
	}
	
	@Override
	public void run() {
		try {
			while (true) {
				fromSetToSerFile();

				Thread.sleep(seconds * 1000);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public Set<String> getMapFromSerFile() throws Exception {
		Set<String> set = null;
		
		FileInputStream fis = new FileInputStream(path);
		ObjectInputStream ois = new ObjectInputStream(fis);
		set = (Set<String>) ois.readObject();
			
		return set;
			
	}

	public void fromSetToSerFile() throws Exception {
		FileOutputStream fos = new FileOutputStream(path);
		ObjectOutputStream oos = new ObjectOutputStream(fos);
		oos.writeObject(listener.getFilesObserved());			
	}

}
