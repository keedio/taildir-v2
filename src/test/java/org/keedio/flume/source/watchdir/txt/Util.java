package org.keedio.flume.source.watchdir.txt;

import org.junit.Test;
import org.keedio.flume.source.watchdir.listener.simpletxtsource.FileEventSourceListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Util {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileEventSourceListener.class);
  
  @Test
  public void test() {
    
    try {
      for (int i = 0; i < 10; i++) {
        LOGGER.info("-->" + i);
      }

      int a = 1 / 0;
    } catch (Exception e) {
      e.printStackTrace();
      LOGGER.error(stackTrace(e));
    }
  }
 
  
  private static String stackTrace(Throwable error) {
    StringBuilder trace = new StringBuilder();
    for(StackTraceElement element:error.getStackTrace()) {
        trace.append("  ").append(element).append("\n");
    }
    return trace.toString();
}
}
