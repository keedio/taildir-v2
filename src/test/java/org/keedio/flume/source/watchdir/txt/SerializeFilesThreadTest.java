package org.keedio.flume.source.watchdir.txt;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
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
		Map<String, Long> map = new HashMap<>();
		map.put("1", 0L);
		map.put("2", 0L);
		map.put("3", 0L);
		map.put("4", 0L);
		
		when(listener.getFilesObserved()).thenReturn(map);
		
		SerializeFilesThread ser = new SerializeFilesThread(listener, "/tmp/test.ser", 5);
		ser.fromMapToSerFile();
		Map<String, Long> aux = ser.getMapFromSerFile();
		
		Assert.assertEquals(map, aux);
		
	}
	
}
