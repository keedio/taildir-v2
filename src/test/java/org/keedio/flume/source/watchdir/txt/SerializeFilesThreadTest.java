package org.keedio.flume.source.watchdir.txt;

import java.util.HashMap;
import java.util.Map;

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
	
}
