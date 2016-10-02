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

    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelAccessor.class);


    private ChannelProcessor channelProcessor;

    private static ChannelAccessor instance;

    public static void init(ChannelProcessor channelProcessor){
        if (instance == null){
            instance = new ChannelAccessor(channelProcessor);
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

    private ChannelAccessor(ChannelProcessor channelProcessor){
        this.channelProcessor = channelProcessor;
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

}
