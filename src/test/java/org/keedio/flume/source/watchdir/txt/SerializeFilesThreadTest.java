package org.keedio.flume.source.watchdir.txt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.keedio.flume.source.watchdir.InodeInfo;
import org.keedio.flume.source.watchdir.listener.simpletxtsource.FileEventSourceListener;
import org.keedio.flume.source.watchdir.listener.simpletxtsource.SerializeFilesThread;
import org.mockito.Mock;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class SerializeFilesThreadTest {

	@Mock
	FileEventSourceListener listener = mock(FileEventSourceListener.class);
	
	@Test
	public void testSerializacion() throws Exception {
		Map<String, InodeInfo> map = new HashMap<>();
		InodeInfo in = new InodeInfo(0L, "inode");
		map.put("1", in);
		
		when(listener.getFilesObserved()).thenReturn(map);
		
		SerializeFilesThread ser = new SerializeFilesThread(listener, "/tmp/test.ser", 5);
		ser.fromMapToSerFile();
		Map<String, InodeInfo> aux = ser.getMapFromSerFile();
		
		Assert.assertEquals(map.get("1").getFileName(), aux.get("1").getFileName());
		
	}
	
	 @Test
	  public void testBadSerializacion() throws Exception {
	   try {
	      Map<String, Long> map = new HashMap<>();
	      map.put("1", 0L);
	      
	      // Serailizamos un objeto incompatible
	      FileOutputStream fos = new FileOutputStream("/tmp/test.ser");
	      ObjectOutputStream oos = new ObjectOutputStream(fos);
	      oos.writeObject(map);   
	      
	      SerializeFilesThread ser = new SerializeFilesThread(listener, "/tmp/test.ser", 5);
	      Map<String, InodeInfo> aux = ser.getMapFromSerFile();
	      
	   } catch (ClassCastException e) {
	     System.out.println("Se tiene que crear el fichero de backup");
	   }
	    Assert.assertEquals(new File("/tmp/test.ser.bck").exists(), true);
	    
	  }
	
}
