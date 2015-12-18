package org.keedio.flume.source.watchdir.xml;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.apache.commons.io.FileUtils;
import org.apache.flume.Channel;
import org.apache.flume.ChannelSelector;
import org.apache.flume.Context;
import org.apache.flume.channel.ChannelProcessor;
import org.apache.flume.channel.MemoryChannel;
import org.apache.flume.channel.ReplicatingChannelSelector;
import org.apache.flume.conf.Configurables;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.keedio.flume.source.watchdir.WatchDirEvent;
import org.keedio.flume.source.watchdir.WatchDirObserver;
import org.keedio.flume.source.watchdir.listener.fake.FakeListener;
import org.keedio.flume.source.watchdir.listener.xmlsource.WatchDirXMLWinEventSourceListener;
import org.mockito.Mock;
import org.mockito.Spy;

import com.google.common.collect.Lists;
import com.google.common.io.Files;

import static org.hamcrest.core.StringContains.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class WatchDirXMLWinEventSourceListenerTest {

	
	WatchDirXMLWinEventSourceListener listener;
	Channel channel;
	File tstFolder1;
	File tstFolder2;
	File tstFolder3;

	@Mock
	FakeListener mock = mock(FakeListener.class);
	
	@Rule
    public TemporaryFolder testFolder = new TemporaryFolder(new File(System.getProperty("java.io.tmpdir")));

	@Rule
    public ExpectedException thrown= ExpectedException.none();


	public void setUp() throws Exception{
        tstFolder1 = testFolder.newFolder("/tmp1/");
        tstFolder2 = testFolder.newFolder("/tmp2/");
        tstFolder3 = testFolder.newFolder("/tmp3/");
		
		listener = new WatchDirXMLWinEventSourceListener();
		
		channel = new MemoryChannel();
		Context context = new Context();
		context.put("dirs.1.dir", tstFolder1.getAbsolutePath());
		context.put("dirs.1.tag", "event");
		context.put("dirs.1.taglevel", "2");
		context.put("dirs.2.dir", tstFolder2.getAbsolutePath());
		context.put("dirs.2.tag", "event");
		context.put("dirs.2.taglevel", "2");
		context.put("event.terminator", "|#]");
		context.put("keep-alive", "1");
		context.put("capacity", "100000");
		context.put("transactionCapacity", "100000");
		context.put("blacklist", "");
		context.put("whitelist", "");
		context.put("pathtoser", testFolder.getRoot() + "/test.ser");
		context.put("timetoser", "5");
		context.put("timetoprocessevents", "-1");

		Configurables.configure(listener, context);
		Configurables.configure(channel, context);
		
		ChannelSelector rcs = new ReplicatingChannelSelector();
		rcs.setChannels(Lists.newArrayList(channel));

		listener.setChannelProcessor(new ChannelProcessor(rcs));
		
		listener.configure(context);
		
		listener.start();;
		Thread.sleep(2000);
	}

	public void finish() throws Exception{
		channel.stop();
		listener.stop();
		Thread.sleep(2000);
	}
	
	@Test
	public void testFileModified() {
		
		try {
			setUp();

			// Registramos el FakeListener en todos los monitores
			for (WatchDirObserver observer: listener.getMonitor()) {
				observer.addWatchDirListener(mock);
			}
			

            // Creamos el fichero en el directorio 1
        	FileUtils.copyFile(new File("src/test/resources/nested.xml"), testFolder.newFile("tmp1/nested.xml"));

            Thread.sleep(10000);
			verify(mock, times(1)).process(any(WatchDirEvent.class));

			finish();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        } catch (Throwable e) {
            e.printStackTrace();
            Assert.fail();
        }
		
	}
	
	@Test
	public void testFileModifiedNotObserved() {
		
		try {
			setUp();

			// Registramos el FakeListener en todos los monitores
			for (WatchDirObserver observer: listener.getMonitor()) {
				observer.addWatchDirListener(mock);
			}
			

            // Creamos el fichero en el directorio 1
        	FileUtils.copyFile(new File("src/test/resources/test.xml"), testFolder.newFile("tmp3/test.xml"));

            Thread.sleep(10000);
			verify(mock, times(0)).process(any(WatchDirEvent.class));
			Assert.assertFalse("No se ha creado el fichero", new File("tmp3/test.xml.finished").exists());

			finish();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        } catch (Throwable e) {
            e.printStackTrace();
            Assert.fail();
        }
		
	}

	@Test
	public void testFileSerCreated() throws Exception {

			setUp();

			// Registramos el FakeListener en todos los monitores
			for (WatchDirObserver observer: listener.getMonitor()) {
				observer.addWatchDirListener(mock);
			}
			

            // Creamos el fichero en el directorio 1
        	FileUtils.copyFile(new File("src/test/resources/test.xml"), testFolder.newFile("tmp3/test.xml"));

            Thread.sleep(2000);
            
            // Los ficheros .finished han tenido que ser generados.
            thrown.expectMessage(containsString("already exists in the test folder"));
		testFolder.newFile("/test.ser");

			finish();
	}
	
	@Test
	@Ignore
	public void testExistingFiles() throws Exception {

			// Creamos el fichero en el directorio 1
    		FileUtils.copyFile(new File("src/test/resources/test.xml"), testFolder.newFile("tmp1/test.xml"));
    		FileUtils.copyFile(new File("src/test/resources/test.xml"), testFolder.newFile("tmp1/test2.xml"));
    		FileUtils.copyFile(new File("src/test/resources/test.xml"), testFolder.newFile("tmp3/test.xml"));

			// Registramos el FakeListener en todos los monitores
			for (WatchDirObserver observer: listener.getMonitor()) {
				observer.addWatchDirListener(mock);
			}
			
            Thread.sleep(2000);
            verify(mock, times(2)).process(any(WatchDirEvent.class));
            
            // Los ficheros .finished han tenido que ser generados.
            thrown.expectMessage(containsString("already exists in the test folder"));
            testFolder.newFile("tmp1/test.xml.finished").exists();
            testFolder.newFile("tmp1/test2.xml.finished").exists();


	}

	@Test
	public void testMappingConvertion() {
		
		Map<String, String> map = new HashMap<String, String>();
		map.put("1.1", "1.1");
		map.put("1.2", "1.2");
		map.put("1.3", "1.3");
		map.put("1.4", "1.4");
		map.put("2.1", "2.1");
		map.put("2.2", "2.2");
		map.put("2.3", "2.3");
		map.put("2.4", "2.4");
		
		Map test = WatchDirXMLWinEventSourceListener.getMapProperties(map);
		
		Assert.assertTrue("La tabla tiene dos elementos", test.size() == 2);
		String str = ((Map<String, String>)test.get("2")).get("3");
		Assert.assertTrue("El elemento 2, 3", "2.3".equals(str));
		
	}
	
	@Test
	public void longCopyTest() {

	}

}
