package org.keedio.flume.source.watchdir.listener.simpletxtsource;

import org.apache.flume.ChannelException;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.event.EventBuilder;
import org.keedio.flume.source.watchdir.listener.LineReadListener;
import org.keedio.flume.source.watchdir.listener.simpletxtsource.util.ChannelAccessor;
import org.keedio.flume.source.watchdir.metrics.MetricsEvent;
import org.keedio.flume.source.watchdir.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.keedio.flume.source.watchdir.listener.simpletxtsource.FileEventSourceListener.*;
import static org.keedio.flume.source.watchdir.listener.simpletxtsource.util.ChannelAccessor.printFilesObserved;

/**
 * This worker proccess the xml file in order to extract the expeted events.
 * @author rolmo
 *
 */
public class FileEventHelper {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(FileEventHelper.class);

  protected static final String FILEHEADERNAME_FAKE = "fileHeaderNameFake";
  private static final String LINE_FEED = "\n";

  private List<Integer> listIndexToRemove;
  private Map<String, TreeMap<Integer,Event>> mapPendingEvents;
  private List<Event> listEventToProcess;
    
  private List<Event> buffer;
  private LineReadListener lineReadListener;

  private ChannelAccessor accessor;
  private boolean isMultilineActive;
  private Integer maxchars;
  protected boolean multilineAssignToPreviousLine;
  protected String multilineRegex;
  protected String multilineFirstLineRegex;
  protected boolean multilineNegateRegex;
  protected int eventsCapacity;
  protected boolean fileHeader;
  protected String fileHeaderName;
  protected boolean baseHeader;
  protected String baseHeaderName;
  protected boolean multilineFlushEntireBuffer;
  protected Pattern patternMultilineRegex;
  protected Pattern patternMultilineFirstLineRegex;
  
  public void setLineReadListener(LineReadListener lineReadListener){
    this.lineReadListener = lineReadListener;
  }

  public FileEventHelper(Context context) {
    accessor = ChannelAccessor.getInstance();
    buffer = new Vector<>();
    isMultilineActive = context.getBoolean(MULTILINE_ACTIVE) == null ? false : context.getBoolean(MULTILINE_ACTIVE);
    maxchars = context.getInteger(MAX_CHARS) == null ? 100000 : context.getInteger(MAX_CHARS);
    multilineAssignToPreviousLine = context.getBoolean(MULTILINE_ASIGN_TO_PREVIOUS_LINE) == null ? true : context.getBoolean(MULTILINE_ASIGN_TO_PREVIOUS_LINE);
    multilineRegex = context.getString(MULTILINE_REGEX);
    multilineFirstLineRegex = context.getString(MULTILINE_FIRST_LINE_REGEX);
    multilineNegateRegex = context.getBoolean(MULTILINE_NEGATE_REGEX) == null ? false : context.getBoolean(MULTILINE_NEGATE_REGEX);
    eventsCapacity = context.getInteger(EVENTS_CAPACITY) == null ? 1000 : context.getInteger(EVENTS_CAPACITY);
    fileHeader = context.getBoolean(FILE_HEADER) == null ? false : context.getBoolean(FILE_HEADER);
    fileHeaderName = context.getString(FILE_HEADER_NAME);
    multilineFlushEntireBuffer = context.getBoolean(MULTILINE_FLUSH_ENTIRE_BUFFER) == null ? false : context.getBoolean(MULTILINE_FLUSH_ENTIRE_BUFFER);

    //En caso de ser necesario compilamos los patterns de las expresiones regulares (general y de primera línea)
    if ((isMultilineActive) && (multilineRegex != null) && (!"".equals(multilineRegex))) {
        patternMultilineRegex = Pattern.compile(multilineRegex);
    }
    if ((patternMultilineRegex != null) && (multilineFirstLineRegex != null) && (!"".equals(multilineFirstLineRegex))) {
        patternMultilineFirstLineRegex = Pattern.compile(multilineFirstLineRegex);
    }
  }

  public List<Event> getBuffer() {
    return buffer;
  }

  public void process(String inode) {
    String path = "";
    try {
      Date inicio = new Date();
      int procesados = 0;
      path = accessor.getFileObserved(inode).getFileName();
      LOGGER.debug("Processing inode:" + inode + ", path: " + path);
      File file = new File(path);
      
      if (file.exists()) {
        readLines(inode);

        long intervalo = new Date().getTime() - inicio.getTime();

        // Notificamos el tiempo de procesado para las metricas
        //TODO: Metricas
        //listener.getMetricsController().manage(new MetricsEvent(MetricsEvent.MEAN_FILE_PROCESS, intervalo));
        //listener.getMetricsController().manage(new MetricsEvent(MetricsEvent.TOTAL_FILE_EVENTS, procesados));

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

    boolean isComplete = false;
    try {

      //Si se encuentra activo el tratamiento multilínea realizamos el procesamiento del buffer
      if (isMultilineActive) {
          processEventBatch();
      } else {
          accessor.sendEventsToChannel(getBuffer());
      }
      isComplete = true;
    } catch (ChannelException e) {
      LOGGER.error("No se han podido innyectar los eventos", e.getMessage());
      LOGGER.error("Mensajes perdidos: " + getBuffer().size());
      // Borramos el buffer
      LOGGER.error("ERROR AL INYECTAR LOS DATOS EN EL CANAL. PARAMOS EL AGENTE.",e);
      printFilesObserved();
        //TODO: Do stop listener
      //listener.stop();
    } catch (Exception e) {
      LOGGER.error("Excepcion general por los interceptores.",e);

      printFilesObserved();
    } catch (Throwable e) {
      LOGGER.error("Excepcion tipo throiwable por los interceptores.",e);

      printFilesObserved();
    } finally {

      if (isComplete) {
          if (!isMultilineActive) {
              //En caso de no haber tratamiento multilínea vaciamos el buffer
              getBuffer().clear();
          }
      } else {
          //Si ha habido algun problema vaciaremos el buffer independientemente si hay tratamiento multilinea o no
          getBuffer().clear();
      }

    }

  }

  private void readLines(String inode) throws Exception {

    LOGGER.debug("ENTRAMOS EN EL HELPER......");
    //
    Long lastByte = 0L;
    processInode(accessor.getFileObserved(inode).getFileName(), inode);

  }

  private void processInode(String path, String inode) throws Exception {

    Long lastLine = accessor.getFileObserved(inode).getPosition();
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

      if (line.length() > maxchars) {
        LOGGER.debug(String.format("Se superan el tamaño máximo, descartamos el mensaje --> %s", line));
        continue;
      }

      Event ev = EventBuilder.withBody(line.getBytes());

      //Obtenemos los headers para el evento
      Map<String, String> headers = createEventHeaders(path);

      if (!headers.isEmpty()) {
          ev.setHeaders(headers);
      }

      getBuffer().add(ev);

      // Notificamos un evento de nuevo mensaje
      //TODO: Metricas
      //  listener.getMetricsController().manage(new MetricsEvent(MetricsEvent.NEW_EVENT));

    }

    LOGGER.debug(String.format("%s(%s):Se procesa actualiza de %d a %d", path, inode, lastLine, lastLine+linesToProc.size()));

    accessor.getFileObserved(inode).setPosition(lastLine+linesToProc.size());

    // Lanzamos los eventos del buffer si sobrepasamos el máximo
    if (getBuffer().size() > eventsCapacity) {

        if (isMultilineActive) {
            processEventBatch();
        } else {
            accessor.sendEventsToChannel(getBuffer());
            getBuffer().clear();
        }

    }

  }

  private static BufferedReader getBufferedReader(String fileName) throws FileNotFoundException {
    return new BufferedReader(new FileReader(fileName));
  }


    /**
     * Procesa los eventos que hubiera en el buffer concatenando aquellos que pertenezcan al mismo fichero y que sean eventos multilinea (excepciones, etc)
     * en un solo evento a enviar al canal de Flume
     */
    private synchronized void processEventBatch() {

        List<Event> listEventsBuffer = getBuffer();

        listIndexToRemove = new ArrayList<>();
        mapPendingEvents = new HashMap<String, TreeMap<Integer,Event>>();
        listEventToProcess = new Vector<Event>();

        //Obtenemos el tamanyo en este momento del buffer. Dicho tamanyo determinará hasta que elementos del mismo son procesados
        //independientemente de que otros procesos anyadan más eventos al mismo
        int bufferSize = listEventsBuffer.size();


        //Recorremos los eventos que posea el buffer
        for (int index = 0; index < bufferSize; index ++) {
            Event eventBuffer = listEventsBuffer.get(index);

            //En funcion del parametro negate la forma de procesar el buffer de eventos sera diferente
            if (!multilineNegateRegex) {
              //Se consideran que aquellos eventos que satisfacen la expresion regular son eventos multilínea.

              if (isSimpleLineEvent(eventBuffer)) {
                  //Se trata de un evento de linea simple

                  //Procesamos los eventos pendientes que pudieran existir para ese mismo fichero
                  Event joinedEvent = processPendingEventsFile(eventBuffer);
                  if (joinedEvent != null) {

                      //Si la cabecera fuera ficticia la removemos
                      if (!fileHeader) {
                          joinedEvent.getHeaders().remove(FILEHEADERNAME_FAKE);
                      }

                      listEventToProcess.add(joinedEvent);


                  }


                  //El evento actual tiene que ser procesado en Flume. Si la cabecera fuera ficticia la removemos
                  if (!fileHeader) {
                      eventBuffer.getHeaders().remove(FILEHEADERNAME_FAKE);
                  }

                  listEventToProcess.add(eventBuffer);

                  //El indice del elemento se indica para su posterior borrado del buffer
                  listIndexToRemove.add(index);

              } else {

                  if (isMultilineFirstLineEvent(eventBuffer)) {
                    //Se trata de la primera línea de un evento multilínea

                    //Procesamos los eventos pendientes que pudieran existir para ese mismo fichero
                    Event joinedEvent = processPendingEventsFile(eventBuffer);

                    if (joinedEvent != null) {

                        //Si la cabecera fuera ficticia la removemos
                        if (!fileHeader) {
                            joinedEvent.getHeaders().remove(FILEHEADERNAME_FAKE);
                        }

                        listEventToProcess.add(joinedEvent);
                    }

                  }

                  //Add el evento como pendiente para proceso.
                  addEventPendingProcess(eventBuffer, index);

              }

            } else {
              //Se consideran que los eventos que no satisfacen la expresion son multilinea. Un evento que la satisface puede tener más lineas asociadas o no

              if (isSimpleLineEvent(eventBuffer)) {
                  //Se trata de un evento que no es la continuacion de linea de otro

                  //Procesamos los eventos pendientes que pudieran existir para ese mismo fichero
                  Event joinedEvent = processPendingEventsFile(eventBuffer);

                  if (joinedEvent != null) {

                      //Si la cabecera fuera ficticia la removemos
                      if (!fileHeader) {
                          joinedEvent.getHeaders().remove(FILEHEADERNAME_FAKE);
                      }

                      listEventToProcess.add(joinedEvent);
                  }

              }

              //Add el evento como pendiente para proceso.
              addEventPendingProcess(eventBuffer, index);

            }

        }


        //Si por configuracion indicamos que se procese el buffer entero, todos los eventos que queden como pendientes tienen que ser enviados a flume
        // (1 evento por fichero).
        if (multilineFlushEntireBuffer) {
            //Generamos eventos para toda la informacion pendiente existente
            List<Event> listPendingEvents = processAllPendingEvents();

            if ((listPendingEvents != null) && (listPendingEvents.size() > 0)) {

                //Si los eventos han sido creados con la cabecera fake, se elimina
                if (!fileHeader) {

                    for (Event pendingEvent : listPendingEvents) {
                        pendingEvent.getHeaders().remove(FILEHEADERNAME_FAKE);
                    }
                }

                listEventToProcess.addAll(listPendingEvents);

                //Eliminamos los datos que ya han sido añadidos como eventos a enviar
                clearMapPendingEvents();

            }

        }


        //Procesamos la lista de eventos a evniar a Flume
        if (listEventToProcess.size() > 0) {
            accessor.sendEventsToChannel(listEventToProcess);
            clearListEventToProcess();
        }

        //Ordenamos los indices antes de su eliminación del buffer para garantizar un orden de borrado correcto
        Collections.sort(listIndexToRemove);

        //Eliminamos del buffer los elementos seleccionados para su borrado. El borrado lo efectuado en orden inverso
        ListIterator<Integer> listIndexesRemoveIterator = listIndexToRemove.listIterator(listIndexToRemove.size());
        while (listIndexesRemoveIterator.hasPrevious()) {
          int indexToRemove = listIndexesRemoveIterator.previous();

          buffer.remove(indexToRemove);
        }


    }


    /**
     * Detecta si un evento se trata de un evento simple (de una sola línea) o se trataría de un evento
     * susceptible de poder tener más de una línea
     * @param eventBuffer Event para el que se determina si es un evento simple de una sola línea
     * @return boolean indicando que el evento es de una sola línea (true) o se trata de un evento susceptible de tener
     * más de una línea (eventos de excepción, etc)
     */
    private synchronized boolean isSimpleLineEvent(Event eventBuffer) {

        boolean isSimpleLineEvent = false;

        //Obtenemos el String correspondiente al mensaje a partir dal array de bytes
        String message = new String(eventBuffer.getBody());



        Matcher matcher = patternMultilineRegex.matcher(message);
        isSimpleLineEvent = !matcher.matches();

        if (LOGGER.isDebugEnabled()) {
            String fileHeaderName = getFileHeaderNameFromHeaders(eventBuffer);

            StringBuilder sb = new StringBuilder();
            sb.append("El mensaje del evento [").append(message).append("] procedente del fichero [").append(fileHeaderName).append("] match multilineRegex [").append(patternMultilineRegex).append("] --> ").append(!isSimpleLineEvent);
            LOGGER.debug(sb.toString());
        }


        //En el caso de que negate sea true, consideramos eventos de linea simple aquellos que matchean contra la expresión regular (aunque luego existan más líneas
        //pertenecientes al evento
        if (multilineNegateRegex) {
            isSimpleLineEvent = !isSimpleLineEvent;
        }

        return isSimpleLineEvent;

    }

    /**
     * Detecta si se trata de la primera linea de un evento multilinea. En el caso de excepciones la primera línea tiene un matcheo diferente y permitiría
     * detectar si 2 excepciones seguidas fueran enviadas crear 2 eventos diferentes
     * @param eventBuffer Event del cual se obtiene la informacion
     * @return boolean indicando si el evento se trata de la primera línea de una excepción
     */
    private synchronized boolean isMultilineFirstLineEvent(Event eventBuffer) {

        boolean isMultilineFirstLineEvent = false;

        //Solo tendremos en cuenta la detección de la primera linea de excepción si se define en las propiedades.
        if (patternMultilineFirstLineRegex != null) {

            //Obtenemos el String correspondiente al mensaje a partir dal array de bytes
            String message = new String(eventBuffer.getBody());

            Matcher matcher = patternMultilineFirstLineRegex.matcher(message);

            isMultilineFirstLineEvent = matcher.matches();

            if (LOGGER.isDebugEnabled()) {
                String fileHeaderName = getFileHeaderNameFromHeaders(eventBuffer);

                StringBuilder sb = new StringBuilder();
                sb.append("El mensaje del evento [").append(message).append("] procedente del fichero [").append(fileHeaderName).append("] match multilineFirstLineRegex [").append(patternMultilineFirstLineRegex).append("] --> ").append(isMultilineFirstLineEvent);
                LOGGER.debug(sb.toString());
            }


        }

        return isMultilineFirstLineEvent;

    }


    /**
     * Metodo que crea un evento a partir de todos los eventos pendientes que existan para dicho fichero
     * @param eventBuffer Event del que se obtiene cual es el fichero para el cual obtener los eventos pendientes
     * @return Event con el contenido concatenado en varías líneas de todos los eventos pendientes que existan
     */
    private synchronized Event processPendingEventsFile(Event eventBuffer) {

        Event joinedEvent = null;
        StringBuilder sb = new StringBuilder();

        if (eventBuffer != null) {

            //Obtenemos el fichero al que pertenece el evento a partir de la header (sea ficticia o no)
            String fileHeaderName = getFileHeaderNameFromHeaders(eventBuffer);

            if ((fileHeaderName != null) && (!"".equals(fileHeaderName))) {

                //Obtenemos el Map de los eventos pendientes del fichero al que pertenece el evento
                if (mapPendingEvents.containsKey(fileHeaderName)) {

                    TreeMap<Integer, Event> treeMapPendingEventsFile = mapPendingEvents.get(fileHeaderName);

                    //En funcion de a que evento se asignara el contenido total de los eventos nos quedaremos con las cabeceras del evento adecuado
                    //(bien las del primer evento de la cadena, bien las del último)

                    boolean getHeaderEvent = true;
                    Map<String, String> headersJoinedEvent = null;

                    //Recorremos el map generando el contenido del evento a devolver

                    for (Integer indexKey : treeMapPendingEventsFile.keySet()) {

                        Event partialEvent = treeMapPendingEventsFile.get(indexKey);

                        //Obtenemos el String correspondiente al mensaje a partir dal array de bytes
                        String partialMessage = new String(partialEvent.getBody());

                        sb.append(partialMessage).append(LINE_FEED);

                        //Obtenemos la cabecera del evento si procede
                        if (getHeaderEvent) {
                            headersJoinedEvent = partialEvent.getHeaders();

                            if (multilineAssignToPreviousLine) {
                                //Garantizamos que las cabeceras que se asignan son las del primer evento
                                getHeaderEvent = false;
                            }
                        }

                        //Introducimos el indice que el evento tuviera en el buffer como indice a ser borrado
                        listIndexToRemove.add(indexKey);
                    }

                    //Eliminamos el ultimo salto de linea
                    if (sb.length() > 0) {
                        sb.setLength(sb.length() - 1);
                    }
                    //Tenemos las cabeceras del primer (o del ultimo evento) y el StringBuilder con el contenido de todos los eventos pendientes
                    joinedEvent = EventBuilder.withBody(sb.toString().getBytes());
                    joinedEvent.setHeaders(headersJoinedEvent);

                    //Eliminamos los eventos pendientes
                    mapPendingEvents.remove(fileHeaderName);

                }

            } else {
                LOGGER.error("Evento sin cabecera fileHeaderName getEventListToProcess()");
            }

        }

        //Devolvemos el evento creado
        return joinedEvent;
    }




    /**
     * Crea una lista de eventos con los eventos pendientes de tratar, creando un evento por cada fichero que tuviera eventos pendientes
     * @return List con los eventos creados
     */
    private synchronized List<Event> processAllPendingEvents() {

        List<Event> listEvents = new Vector<Event>();
        StringBuilder sb = new StringBuilder();

        //Obtenemos el conjunto de ficheros para los que existen eventos pendientes
        for (String fileHeaderName : mapPendingEvents.keySet()) {

            Event joinedEvent = null;
            sb.setLength(0);

            //Obtenemos el arbol de eventos correspondientes al fichero
            TreeMap<Integer, Event> treeMapPendingEventsFile = mapPendingEvents.get(fileHeaderName);

            //En funcion de a que evento se asignara el contenido total de los eventos nos quedaremos con las cabeceras del evento adecuado
            //(bien las del primer evento de la cadena, bien las del último)

            boolean getHeaderEvent = true;
            Map<String, String> headersJoinedEvent = null;

            //Recorremos el map generando el contenido del evento a devolver

            for (Integer indexKey : treeMapPendingEventsFile.keySet()) {

                Event partialEvent = treeMapPendingEventsFile.get(indexKey);

                //Obtenemos el String correspondiente al mensaje a partir dal array de bytes
                String partialMessage = new String(partialEvent.getBody());

                sb.append(partialMessage).append(LINE_FEED);

                //Obtenemos la cabecera del evento si procede
                if (getHeaderEvent) {
                    headersJoinedEvent = partialEvent.getHeaders();

                    if (multilineAssignToPreviousLine) {
                        //Garantizamos que las cabeceras que se asignan son las del primer evento
                        getHeaderEvent = false;
                    }
                }

                //Introducimos el indice que el evento tuviera en el buffer como indice a ser borrado
                listIndexToRemove.add(indexKey);
            }

            //Eliminamos el ultimo salto de linea
            if (sb.length() > 0) {
                sb.setLength(sb.length() - 1);
            }
            //Tenemos las cabeceras del primer (o del ultimo evento) y el StringBuilder con el contenido de todos los eventos pendientes
            joinedEvent = EventBuilder.withBody(sb.toString().getBytes());
            joinedEvent.setHeaders(headersJoinedEvent);

            //Anyadimos el evento a la lista de eventos a procesar
            listEvents.add(joinedEvent);

        }

        return listEvents;


    }

    /**
     * Añade un evento a la lista de eventos pendientes de procesar
     * @param eventBuffer Event pendiente de procesar
     * @param index int con el indice que el evento posee dentro del buffer (para su posterior eliminacion)
     */
    private synchronized void addEventPendingProcess(Event eventBuffer, int index) {

        if (eventBuffer != null) {


            //Obtenemos el fichero al que pertenece el evento a partir de la header (sea ficticia o no)
            String fileHeaderName = getFileHeaderNameFromHeaders(eventBuffer);

            //Obtenemos el Map de los eventos pendientes del fichero al que pertenece el evento
            if (mapPendingEvents.containsKey(fileHeaderName)) {

                TreeMap<Integer, Event> treeMapPendingEventsFile = mapPendingEvents.get(fileHeaderName);

                //Add el evento como evento pendiente
                treeMapPendingEventsFile.put(index, eventBuffer);

            } else {
                //No existen eventos pendientes para dicho fichero.Creamos el map con los eventos pendientes y le anyadimos el evento
                TreeMap<Integer, Event> treeMapPendingEventsFile = new TreeMap<>();
                treeMapPendingEventsFile.put(index, eventBuffer);

                mapPendingEvents.put(fileHeaderName, treeMapPendingEventsFile);
            }

        }
    }

    /**
     * Elimina los datos de la lista de eventos para procesar
     */
    private synchronized  void clearListEventToProcess() {
        listEventToProcess.clear();
    }

    /**
     * Elimina los datos del map de eventos pendientes de enviar
     */
    protected synchronized  void clearMapPendingEvents() {
        mapPendingEvents.clear();
    }


    /**
     * Devuelve el nombre del fichero al que pertenece el evento a partir de los datos de las cabeceras del mismo
     * @param eventBuffer Event del que se obtiene los datos
     * @return String con el nombre del fichero al que pertenece el evento
     */
    private synchronized String getFileHeaderNameFromHeaders(Event eventBuffer) {

        String fileHeaderName = null;

        if (eventBuffer != null) {

            //Obtenemos el fichero al que pertenece el evento a partir de la header (sea ficticia o no)
            Map<String, String> headersEventBuffer = eventBuffer.getHeaders();

            if ((headersEventBuffer != null) && (!headersEventBuffer.isEmpty())) {

                if (fileHeader) {
                    fileHeaderName = headersEventBuffer.get(fileHeaderName);
                } else {
                    fileHeaderName = headersEventBuffer.get(FILEHEADERNAME_FAKE);
                }

            }
        }

        return fileHeaderName;
    }

    /**
     * Crea los headers del evento a partir del path en función de las variables de configuracion existentes
     * @param path String con el path
     * @return Map con los headers creados para el evento
     */
    private Map<String, String> createEventHeaders(String path) {

        Map<String, String> headers = new HashMap<String, String>();

        if (isMultilineActive) {

            //2016-08-25 - Con motivo del tratamiento de la excepcion multilinea siempre introducimos cabeceras (sirven para saber a que fichero pertenece la linea)
            //Serían eliminados en el tratamiento posterior

            if (fileHeader) {
                headers.put(fileHeaderName, path);
            } else {
                headers.put(FILEHEADERNAME_FAKE, path);
            }
            if (baseHeader) {
                headers.put(baseHeaderName, new File(path).getName());
            }


        } else {

            // Put header props
            if (fileHeader) {
                headers.put(fileHeaderName, path);
            }
            if (baseHeader) {
                headers.put(baseHeaderName, new File(path).getName());
            }
        }

        return headers;
    }


}
