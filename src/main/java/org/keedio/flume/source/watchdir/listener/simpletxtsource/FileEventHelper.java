package org.keedio.flume.source.watchdir.listener.simpletxtsource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.flume.ChannelException;
import org.apache.flume.Event;
import org.apache.flume.event.EventBuilder;
import org.keedio.flume.source.watchdir.InodeInfo;
import org.keedio.flume.source.watchdir.WatchDirEvent;
import org.keedio.flume.source.watchdir.listener.LineReadListener;
import org.keedio.flume.source.watchdir.metrics.MetricsEvent;
import org.keedio.flume.source.watchdir.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This worker proccess the xml file in order to extract the expeted events.
 * @author rolmo
 *
 */
public class FileEventHelper {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(FileEventHelper.class);

  FileEventSourceListener listener;
  private List<Event> buffer;
  private LineReadListener lineReadListener;
  
  public void setLineReadListener(LineReadListener lineReadListener){
    this.lineReadListener = lineReadListener;
  }

  public FileEventHelper(FileEventSourceListener listener) {
    this.listener = listener;
    //this.buffer = new ArrayList<>();
    this.buffer = new Vector<Event>();
  }

  public synchronized List<Event> getBuffer() {
    return buffer;
  }

  public void process(String inode) {
    String path = "";
    try {
      Date inicio = new Date();
      int procesados = 0;
      path = this.listener.getFilesObserved().get(inode).getFileName();
      LOGGER.debug("Processing inode:" + inode + ", path: " + path);
      File file = new File(path);
      
      if (file.exists()) {
        readLines(inode);

        long intervalo = new Date().getTime() - inicio.getTime();

        // Notificamos el tiempo de procesado para las metricas
        listener.getMetricsController().manage(new MetricsEvent(MetricsEvent.MEAN_FILE_PROCESS, intervalo));
        listener.getMetricsController().manage(new MetricsEvent(MetricsEvent.TOTAL_FILE_EVENTS, procesados));

      } else {
        LOGGER.warn("File '" + file + "' associated with inode '"+inode+"' does not exists and cannot be processed.");
      }
    } catch (Exception e) {
      LOGGER.error("Error procesando el fichero: " + path);
      LOGGER.error(e.getMessage());
      e.printStackTrace();
    }
  }

  public void commitPendings() {
    try {
      listener.getChannelProcessor().processEventBatch(getBuffer());
    } catch (ChannelException e) {
      LOGGER.error("No se han podido innyectar los eventos", e.getMessage());
      LOGGER.error("Mensajes perdidos: " + getBuffer().size());
      // Borramos el buffer
      LOGGER.error("ERROR AL INYECTAR LOS DATOS EN EL CANAL. PARAMOS EL AGENTE.",e);
      Util.printFilesObserved(listener.getFilesObserved());
      listener.stop();
    } catch (Exception e) {
      LOGGER.error("Excepcion general por los interceptores.",e);

      Util.printFilesObserved(listener.getFilesObserved());
    } catch (Throwable e) {
      LOGGER.error("Excepcion tipo throiwable por los interceptores.",e);

      Util.printFilesObserved(listener.getFilesObserved());
    } finally {
      getBuffer().clear();
    }
  }

  private void readLines(String inode) throws Exception {

    LOGGER.debug("ENTRAMOS EN EL HELPER......");
    //
    Long lastByte = 0L;
    processInode(this.listener.getFilesObserved().get(inode).getFileName(), inode);

  }

  private void processInode(String path, String inode) throws Exception {
    Long lastLine = listener.getFilesObserved().get(inode).getPosition();
    LOGGER.debug(String.format("Se procesa el fichero %s(%s) desde la linea %d", path, inode, lastLine));

    if (lastLine < 0) {
      LOGGER.debug(String.format("Negative lastByte: %d", lastLine));
      return;
    }

    List<String> linesToProc;

    try (BufferedReader br = getBufferedReader(path)) {
      //skip the first line and the columns length to get the data
      //columns are identified as being splittable on the delimiter
      linesToProc = br.lines().skip(lastLine).map(s -> (String)s).collect(Collectors.toList());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }		

    for (String line : linesToProc) {
      LOGGER.debug(String.format("%s(%s):Se procesa linea: %s", path, inode, line));
      if (lineReadListener != null){
        lineReadListener.lineRead(line);
      }

      if (line.length() > listener.maxchars) {
        LOGGER.debug(String.format("Se superan el tamaño máximo, descartamos el mensaje --> %s", line));
        continue;
      }

      Event ev = EventBuilder.withBody(line.getBytes());

      // Put header props
      Map<String, String> headers = new HashMap<String, String>();
      if (listener.fileHeader)
        headers.put(listener.fileHeaderName, path);
      if (listener.baseHeader)
        headers.put(listener.baseHeaderName, new File(path).getName());
      if (!headers.isEmpty())
        ev.setHeaders(headers);


      getBuffer().add(ev);

      // Notificamos un evento de nuevo mensaje
      listener.getMetricsController().manage(new MetricsEvent(MetricsEvent.NEW_EVENT));

    }

    LOGGER.debug(String.format("%s(%s):Se procesa actualiza de %d a %d", path, inode, lastLine, lastLine+linesToProc.size()));

    listener.getFilesObserved().get(inode).setPosition(lastLine+linesToProc.size());

    // Lanzamos los eventos del buffer si sobrepasamos el máximo
    if (getBuffer().size() > listener.eventsCapacity) {
      listener.getChannelProcessor().processEventBatch(getBuffer());
      getBuffer().clear();
    }

  }

  private static BufferedReader getBufferedReader(String fileName) throws FileNotFoundException {
    return new BufferedReader(new FileReader(fileName));
  }
}
