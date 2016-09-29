package org.keedio.flume.source.watchdir.listener.simpletxtsource.util;

import org.apache.flume.Event;
import org.apache.flume.channel.ChannelProcessor;
import org.keedio.flume.source.watchdir.InodeInfo;
import org.keedio.flume.source.watchdir.listener.simpletxtsource.exception.DuplicateInitializationException;
import org.keedio.flume.source.watchdir.listener.simpletxtsource.exception.NotInitializedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by luca on 29/09/16.
 */
public class ChannelAccessor {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(ChannelAccessor.class);
    
    private ChannelProcessor channelProcessor;
    protected Map<String, InodeInfo> filesObserved;
    
    private static ChannelAccessor instance;
    
    public static void init(ChannelProcessor channelProcessor, Map<String, InodeInfo> filesObserved){
        if (instance == null){
            instance = new ChannelAccessor(channelProcessor, filesObserved);
        } else {
            throw new DuplicateInitializationException();
        }
    }
    
    public static ChannelAccessor getInstance(){
        if (instance == null){
            throw new NotInitializedException();
        } else {
            return instance;
        }
    }
    
    private ChannelAccessor(ChannelProcessor channelProcessor, Map<String, InodeInfo> filesObserved){
        this.channelProcessor = channelProcessor;
        
        if (filesObserved == null){
            this.filesObserved = new HashMap<>();
        } else {
            this.filesObserved = filesObserved;
        }
    }
    
    public boolean hasElement(String inodeName){
        synchronized (filesObserved){
            return filesObserved.containsKey(filesObserved);
        }
    }
    
    public InodeInfo addFileObserved(String inodeName, InodeInfo info){
        synchronized (filesObserved){
            return filesObserved.put(inodeName, info);
        }
    }

    public InodeInfo getFileObserved(String inodeName){
        synchronized (filesObserved){
            return filesObserved.get(inodeName);
        }
    }

    public InodeInfo removeFileObserved(String inodeName){
        synchronized (filesObserved){
            return filesObserved.remove(inodeName);
        }
    }

    public void sendEventToChannel(Event event){
        synchronized (channelProcessor){
            channelProcessor.processEvent(event);
        }
    }
    
    public void sendEventsToChannel(List<Event> events){
        synchronized (channelProcessor){
            channelProcessor.processEventBatch(events);
        }
    }

    public static void printFilesObserved(){
        if (instance.filesObserved == null || instance.filesObserved.size() == 0){
            LOGGER.warn("No files monitored");
        } else {
            LOGGER.warn("Files monitored:");
            for (Map.Entry<String, InodeInfo> entry : instance.filesObserved.entrySet()) {
                LOGGER.info(entry.getKey() + " -> " + entry.getValue());
            }
        }
    }
}
