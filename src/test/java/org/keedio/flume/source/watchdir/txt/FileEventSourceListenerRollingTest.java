package org.keedio.flume.source.watchdir.txt;

import com.google.common.collect.Lists;
import com.google.common.io.LineReader;
import junit.framework.Assert;
import org.apache.commons.io.FileUtils;
import org.apache.flume.Channel;
import org.apache.flume.ChannelSelector;
import org.apache.flume.Context;
import org.apache.flume.channel.ChannelProcessor;
import org.apache.flume.channel.MemoryChannel;
import org.apache.flume.channel.ReplicatingChannelSelector;
import org.apache.flume.conf.Configurables;
import org.apache.log4j.Logger;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.keedio.flume.source.watchdir.WatchDirEvent;
import org.keedio.flume.source.watchdir.WatchDirObserver;
import org.keedio.flume.source.watchdir.listener.LineReadListener;
import org.keedio.flume.source.watchdir.listener.fake.FakeListener;
import org.keedio.flume.source.watchdir.listener.simpletxtsource.FileEventSourceListener;
import org.mockito.Mock;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;

import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class FileEventSourceListenerRollingTest {
    private FileEventSourceListener listener;
    private final static String tstFolder = System.getProperty("java.io.tmpdir") + File.separatorChar + "rollingtest";
    private final static String testLogFileFolder = tstFolder + File.separatorChar + "log";
    private final static String testLogFile = testLogFileFolder + File.separatorChar + "rollingtestfile_log";

    private LineReadListener lineReadListenerMock;

    private static Thread logGeneratorThread;

    private static Runnable logGeneratorRunnable = new Runnable() {
        private Logger LOG = Logger.getLogger("rolling.test");

        @Override
        public void run() {
            long maxiter = 100000;
            
            long idx = 0;
            try {
                while (maxiter - idx > 0) {
                    LOG.info("this is message #" + (idx++));
                    Thread.sleep(10);
                    
                    File logFile = new File(testLogFile + ".1");
                    
                    if (logFile.exists() && idx < maxiter - 100){
                        idx = maxiter - 100;
                    }
                    
                }
            } catch (InterruptedException ie) {
            }
        }
    };

    @AfterClass
    public static void shutdownClass() throws IOException, InterruptedException{
        logGeneratorThread.interrupt();
        FileUtils.deleteDirectory(new File(tstFolder));
    }
    
    @BeforeClass
    public static void setUpClass() throws IOException, InterruptedException{
        logGeneratorThread = new Thread(logGeneratorRunnable);
        logGeneratorThread.setName("logGeneratorThread");
        logGeneratorThread.start();

        Thread.sleep(100);
    }

    @Before
    public void setUp() throws IOException, InterruptedException {
        listener = new FileEventSourceListener();
        lineReadListenerMock = mock(LineReadListener.class);

        Channel channel = new MemoryChannel();
        Context context = new Context();
        context.put("dirs.1.dir", testLogFileFolder);
        //context.put("event.terminator", "|#]");
        context.put("keep-alive", "1");
        context.put("capacity", "100000");
        context.put("transactionCapacity", "100000");
        context.put("blacklist", "");
        context.put("whitelist", "rollingtestfile_log");
        context.put("pathtoser", tstFolder + File.separatorChar + "rollingtest.ser");
        context.put("timetoser", "5");

        Configurables.configure(listener, context);
        Configurables.configure(channel, context);

        ChannelSelector rcs = new ReplicatingChannelSelector();
        rcs.setChannels(Lists.newArrayList(channel));

        listener.setChannelProcessor(new ChannelProcessor(rcs));
        listener.configure(context);
        listener.setLineReadListener(lineReadListenerMock);
        listener.start();

    }

    @After
    public void finish() {
        listener.stop();
    }
    @Test
    public void testFileModified() {

        try {
            while (logGeneratorThread.isAlive()){
                Thread.sleep(5000);
            }

            Thread.sleep(5000);

            File logFile = new File(testLogFile);
            File logFile1 = new File(testLogFile + ".1");

            LineNumberReader lineNumberReader  = new LineNumberReader(new BufferedReader(new FileReader(logFile)));
            lineNumberReader.skip(Long.MAX_VALUE);
            
            LineNumberReader lineNumberReader1 = new LineNumberReader(new BufferedReader(new FileReader(logFile1)));
            lineNumberReader1.skip(Long.MAX_VALUE);
            
            int expected = lineNumberReader.getLineNumber() + lineNumberReader1.getLineNumber();
            
            System.out.println("A total of " + expected + " lines have been generated");
            
            verify(lineReadListenerMock, times(expected)).lineRead(anyString());

            lineNumberReader.close();
            lineNumberReader1.close();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }
}
