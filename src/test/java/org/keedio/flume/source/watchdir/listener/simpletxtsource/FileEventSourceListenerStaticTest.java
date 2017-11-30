package org.keedio.flume.source.watchdir.listener.simpletxtsource;

import com.google.common.collect.ImmutableMap;
import org.apache.flume.Context;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.keedio.flume.source.watchdir.InodeInfo;
import org.keedio.flume.source.watchdir.WatchDirEvent;
import org.keedio.flume.source.watchdir.WatchDirException;
import org.keedio.flume.source.watchdir.WatchDirFileSet;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.times;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.*;

/**
 * Created by luca on 19/08/16.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest(org.keedio.flume.source.watchdir.util.Util.class)
public class FileEventSourceListenerStaticTest {
    final String errorFile = "/var/log/httpd/kosmos-error_log";
    final String accessFileName = "kosmos-access_log";
    final String accessFile = "/var/log/httpd/"+accessFileName;
    final String accessFileRenamed = "/var/log/httpd/"+accessFileName + "-20160810";

    @Test
    public void testInitWithInconsistentObservedFileMap() throws Exception {
        mockStatic(org.keedio.flume.source.watchdir.util.Util.class);

        Path tmpFile0 = Files.createTempFile("testInitWithInconsistentObservedFileMap", "");
        Path tmpFile1 = Files.createTempFile("testInitWithInconsistentObservedFileMap", "");


        given(org.keedio.flume.source.watchdir.util.Util.getInodeID(tmpFile0.toFile().getAbsolutePath())).willReturn("1269");
        given(org.keedio.flume.source.watchdir.util.Util.getInodeID(tmpFile1.toFile().getAbsolutePath())).willReturn("1274");
        
        Map<String, InodeInfo> mockFilesObserved = new HashMap<>();

        
        SerializeFilesThread mockSerializer = mock(SerializeFilesThread.class);
        when(mockSerializer.getMapFromSerFile()).thenReturn(mockFilesObserved);

        InodeInfo errorFileInodeInfo = spy(new InodeInfo(0L, tmpFile0.toFile().getAbsolutePath()));
        InodeInfo accessFileInodeInfo = spy(new InodeInfo(321123L, tmpFile1.toFile().getAbsolutePath()));

        InodeInfo notExistentFileInodeInfo = spy(new InodeInfo(321123L, accessFile));

        mockFilesObserved.put("269", errorFileInodeInfo);
        mockFilesObserved.put("273", accessFileInodeInfo);
        mockFilesObserved.put("293", notExistentFileInodeInfo);

        FileEventSourceListener listener = new FileEventSourceListener();
        listener.filesObserved =  mockFilesObserved;
        listener.ser = mockSerializer;

        HashSet<WatchDirFileSet> mockHashSet = spy(new HashSet<WatchDirFileSet>());
        when(mockHashSet.isEmpty()).thenReturn(false);
        
        listener.fileSets = mockHashSet;

        Context ctx = mock(Context.class);
        when(ctx.getString(anyString())).thenReturn("");
        when(ctx.getInteger(anyString())).thenReturn(1);
        when(ctx.getBoolean(anyString())).thenReturn(false);
        when(ctx.getSubProperties(anyString())).thenReturn(ImmutableMap.of());

        listener.configure(ctx);
        
        assertFalse(listener.getFilesObserved().containsKey("269"));
        assertFalse(listener.getFilesObserved().containsKey("273"));
        assertFalse(listener.getFilesObserved().containsKey("293"));

        assertFalse(listener.getFilesObserved().containsKey("1269"));
        assertFalse(listener.getFilesObserved().containsKey("1273"));

        listener.stop();
    }

    @Test
    public void testRenameEventDifferentInode() throws WatchDirException {
        mockStatic(org.keedio.flume.source.watchdir.util.Util.class);
        
        given(org.keedio.flume.source.watchdir.util.Util.getInodeID(errorFile)).willReturn("269");
        given(org.keedio.flume.source.watchdir.util.Util.getInodeID(accessFile)).willReturn("274");
        given(org.keedio.flume.source.watchdir.util.Util.getInodeID(accessFileRenamed)).willReturn("273");


        WatchDirFileSet watchDirFileSet = mock(WatchDirFileSet.class);
        given(watchDirFileSet.haveToProccess(errorFile)).willReturn(false);
        given(watchDirFileSet.haveToProccess(accessFile)).willReturn(true);

        WatchDirEvent event = mock(WatchDirEvent.class);
        given(event.getPath()).willReturn(accessFileRenamed);
        given(event.getType()).willReturn("ENTRY_CREATE");
        given(event.getSet()).willReturn(watchDirFileSet);

        FileEventSourceListener listener = new FileEventSourceListener();

        Map<String, InodeInfo> mockFilesObserved = new HashMap<>();

        InodeInfo errorFileInodeInfo = spy(new InodeInfo(0L, errorFile));
        InodeInfo accessFileInodeInfo = spy(new InodeInfo(321123L, accessFile));

        mockFilesObserved.put("269", errorFileInodeInfo);
        mockFilesObserved.put("273", accessFileInodeInfo);
        listener.filesObserved =  mockFilesObserved;


        FileEventHelper mockHelper = mock(FileEventHelper.class);
        listener.helper = mockHelper;
        
        listener.process(event);
        
        verify(watchDirFileSet, times(1)).haveToProccess(accessFile);
        verify(mockHelper, times(1)).process("273");
        verify(accessFileInodeInfo, times(1)).setFileName(accessFileRenamed);

        assertFalse(mockFilesObserved.containsKey("273"));
        assertFalse(mockFilesObserved.containsKey("274"));

        /* simulates new event after rotation */
        WatchDirEvent eventAfterRotation = mock(WatchDirEvent.class);
        given(eventAfterRotation.getPath()).willReturn(accessFile);
        given(eventAfterRotation.getType()).willReturn("ENTRY_MODIFY");
        given(eventAfterRotation.getSet()).willReturn(watchDirFileSet);

        listener.process(eventAfterRotation);

        Mockito.verify(mockHelper, times(1)).process("274");
        assertTrue(mockFilesObserved.containsKey("274"));
        
    }
    
    /**
     * Tests when there are two files. One of them in the whitelist and the other one not
     * being processed.
     * 
     * This tests check the listener behaviour when both files are rotated and the one in the whiteleist 
     * gets the same inode of the file that was not previously processed.
     * 
     * @throws WatchDirException
     */
    @Test
    public void testRenameEventSameInode() throws WatchDirException {
        mockStatic(org.keedio.flume.source.watchdir.util.Util.class);
        given(org.keedio.flume.source.watchdir.util.Util.getInodeID(anyString())).willReturn("269");

        WatchDirFileSet watchDirFileSet = mock(WatchDirFileSet.class);
        given(watchDirFileSet.haveToProccess(errorFile)).willReturn(false);
        given(watchDirFileSet.haveToProccess(accessFile)).willReturn(true);
        
        WatchDirEvent event = mock(WatchDirEvent.class);
        given(event.getPath()).willReturn(accessFile);
        given(event.getType()).willReturn("ENTRY_CREATE");
        given(event.getSet()).willReturn(watchDirFileSet);
        
        FileEventSourceListener listener = new FileEventSourceListener();

        Map<String, InodeInfo> mockFilesObserved = new HashMap<>();
        
        InodeInfo errorFileInodeInfo = spy(new InodeInfo(0L, errorFile));
        InodeInfo accessFileInodeInfo = spy(new InodeInfo(4322L, accessFile));
        
        mockFilesObserved.put("269", errorFileInodeInfo);
        mockFilesObserved.put("273", accessFileInodeInfo);
        listener.filesObserved =  mockFilesObserved;

        listener.process(event);

        assertFalse(mockFilesObserved.containsKey("269"));

        /* simulates new event after rotation */
        WatchDirEvent eventAfterRotation = mock(WatchDirEvent.class);
        given(eventAfterRotation.getPath()).willReturn(accessFile);
        given(eventAfterRotation.getType()).willReturn("ENTRY_MODIFY");
        given(eventAfterRotation.getSet()).willReturn(watchDirFileSet);
        
        FileEventHelper mockHelper = mock(FileEventHelper.class);
        listener.helper = mockHelper;

        listener.process(eventAfterRotation);

        Mockito.verify(mockHelper, times(1)).process("269");
        assertTrue(mockFilesObserved.containsKey("269"));
    }
}
