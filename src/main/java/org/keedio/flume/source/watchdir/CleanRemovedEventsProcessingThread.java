package org.keedio.flume.source.watchdir;

import org.keedio.flume.source.watchdir.listener.simpletxtsource.FileEventSourceListener;
import org.keedio.flume.source.watchdir.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

public class CleanRemovedEventsProcessingThread implements Runnable {

    private FileEventSourceListener listener;
    private int seconds;
    private static final Logger LOGGER = LoggerFactory
            .getLogger(CleanRemovedEventsProcessingThread.class);

    public int getProcessedEvents() {
        return processedEvents;
    }

    private int processedEvents = 0;

    public CleanRemovedEventsProcessingThread(FileEventSourceListener listener, int seconds) {
        this.listener = listener;
        this.seconds = seconds;
        
        cleanObservedFilesDifferentInode(listener.getFilesObserved());
    }

    @Override
    public void run() {

        while (true) {
            try {
                Map<String, InodeInfo> inodes = listener.getFilesObserved();
                
                synchronized (inodes){
                    process(inodes);
                }
                
                Thread.sleep(seconds * 1000);
            } catch (InterruptedException e) {
                LOGGER.debug("CleanRemovedEventsProcessingThread interrupted, exiting",e);
                break;
            } catch (Exception e) {
                LOGGER.debug("Error en la lectura del fichero, todavía no se ha generado.",e);
            }
        }
    }

    private void process(Map<String, InodeInfo> inodes) throws Exception {
        LOGGER.debug("Processing ");
        for (String inodeKey : inodes.keySet()) {

            try {
                InodeInfo inode = inodes.get(inodeKey);
                File file = new File(inode.getFileName());
                if (!file.exists()) {
                    LOGGER.info("Removing inodekey '" + inodeKey + "' associated with file '" + file.getAbsolutePath() + "'");
                    inodes.remove(inodeKey);
                }
                
            } catch (Exception e) {
                LOGGER.info("Error procesando el listener", e);
            }
        }
    }
    
    public static void cleanObservedFilesDifferentInode(Map<String, InodeInfo> inodes){
        synchronized (inodes){
            for (Iterator<Map.Entry<String, InodeInfo>> iter = inodes.entrySet().iterator(); iter.hasNext();){
                String inodeKey = iter.next().getKey();
                
                try {
                    InodeInfo inode = inodes.get(inodeKey);

                    File file = new File(inode.getFileName());
                    if (!file.exists()) {
                        LOGGER.info("Removing inodekey '" + inodeKey + "' associated with file '" + file.getAbsolutePath() + "'");
                        iter.remove();
                    }

                    String inodeNumber = Util.getInodeID(inode.getFileName());

                    if (inodeNumber != null && !inodeNumber.equals(inodeKey)){
                        LOGGER.info("Removing inodekey '"+inodeKey+"', inodeNumber '" + inodeNumber+ "' does not match inode number for observed file '" + file.getAbsolutePath() + "'");
                        iter.remove();
                    }

                } catch (Exception e) {
                    LOGGER.info("Error procesando el listener", e);
                }
            }
        }
    }

}