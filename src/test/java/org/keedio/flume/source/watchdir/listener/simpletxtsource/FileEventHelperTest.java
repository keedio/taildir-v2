package org.keedio.flume.source.watchdir.listener.simpletxtsource;


import org.apache.flume.Event;
import org.apache.flume.channel.ChannelProcessor;
import org.apache.flume.event.EventBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.keedio.flume.source.watchdir.listener.simpletxtsource.util.ChannelAccessor;
import org.keedio.flume.utils.TestUtils;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.times;
import static org.powermock.api.mockito.PowerMockito.*;

import org.apache.log4j.Logger;

@RunWith(PowerMockRunner.class)
@PrepareForTest({org.apache.flume.channel.ChannelProcessor.class, org.keedio.flume.source.watchdir.listener.simpletxtsource.FileEventSourceListener.class,
        org.keedio.flume.source.watchdir.listener.simpletxtsource.FileEventHelper.class})
@SuppressWarnings("all")
public class FileEventHelperTest {

    private static final Logger LOGGER = Logger.getLogger(FileEventHelperTest.class);

    private static final Object[] objectNull = null; //to prevent warning
    private static final Class<?>[] classNull = null; //to prevent warning

    private static final String MULTILINE_REGEX = "(^\\d+\\serror)|(^.+Exception: .+)|([a-zA-Z\\.]*Exception)|(^\\s+at .+)|(^\\s+... \\d+ more)|(^\\s*Caused by:.+)";
    private static final String MULTILINE_FIRST_LINE_REGEX = "(^.+Exception: .+)|([a-zA-Z\\.]*Exception)";

    private static final String MULTILINE_TIMESTAMP_REGEX = "\\d{4}-[0-1]\\d-[0-3]\\dT[0-2]\\d:[0-5]\\d:[0-5]\\d\\.\\d{3}(Z|[+-](2[0-3]|[01][0-9])[0-5][0-9]).*";

    private static final String FILE_HEADER_NAME = "fileHeaderName";
    private static final String FILE_HEADER_NAME_FAKE = "fileHeaderNameFake";
    private static final String FILE_HEADER_VALUE_FILE01 = "/path/File01.log";
    private static final String FILE_HEADER_VALUE_FILE02 = "/path/File02.log";
    private static final String FILE_HEADER_VALUE_FILE03 = "/path/File03.log";
    private static final String MESSAGE_SIMPLE_EVENT = "Mensaje evento simple";


    private FileEventSourceListener mockListener;
    private FileEventHelper spyHelper;


    /**
     * Prepara los datos del listener/helper (mock) para testar el comportamiento de los metodos
     *
     * @param multilineActive               boolean indicando si se el proceso del buffer contempla multiónea
     * @param multilineRegex                String con la expresion regular para multilínea
     * @param multilineFirstLineRegex       String con la expresión regular para la primera línea de una multilínea
     * @param multilineNegateRegex          boolean indicando si se hace un matcheo positivo o negativo
     * @param multilineAssignToPreviousLine boolean indicando si la primera linea de un conjunto multilinea es la que aporta las cabeceras
     * @param multilineFlushEntireBuffer    boolean indicando si al finalizar el procesado del buffer los eventos catalogados como pendientes son enviados
     * @param fileHeader                    boolean indicando si el evento posee cabecera con el nombre del fichero en origen
     * @param fileHeaderName                String con el nombre de la cabecera que indica el fichero al que pertenece el evento
     * @param bufferTest                    Vector con el conjunto de eventos a ser procesados
     * @throws Exception
     */
    @SuppressWarnings("all")
    private void prepareTestData(boolean multilineActive, String multilineRegex, String multilineFirstLineRegex, boolean multilineNegateRegex,
                                 boolean multilineAssignToPreviousLine, boolean multilineFlushEntireBuffer,
                                 boolean fileHeader, String fileHeaderName, Vector<Event> bufferTest) throws Exception {


        //A la hora de enviar la lista de eventos al channelProcessor no queremos hacer nada.
        ChannelProcessor mockChannelProcesor = mock(ChannelProcessor.class);
        mockListener = mock(FileEventSourceListener.class);
        when(mockListener.getChannelProcessor()).thenReturn(mockChannelProcesor);
        doNothing().when(mockChannelProcesor).processEventBatch(anyList());
        ChannelAccessor.init(mockChannelProcesor);

        //Establecemos las propiedades del listener que determinan el comportamiento del metodo
        mockListener.multilineActive = multilineActive;

        if ((multilineActive) && (multilineRegex != null) && (!"".equals(multilineRegex))) {
            mockListener.patternMultilineRegex = Pattern.compile(multilineRegex);
        }

        if ((mockListener.patternMultilineRegex != null) && (multilineFirstLineRegex != null) && (!"".equals(multilineFirstLineRegex))) {
            mockListener.patternMultilineFirstLineRegex = Pattern.compile(multilineFirstLineRegex);
        }

        mockListener.multilineNegateRegex = multilineNegateRegex;
        mockListener.multilineAssignToPreviousLine = multilineAssignToPreviousLine;
        mockListener.multilineFlushEntireBuffer = multilineFlushEntireBuffer;

        mockListener.fileHeader = fileHeader;
        if (mockListener.fileHeader) {
            mockListener.fileHeaderName = fileHeaderName;
        }

        spyHelper = spy(new FileEventHelper(mockListener));

        //Si dentro del metodo a la hora de eliminar eventos del buffer se accede mediante getBuffer() tambien podemos establecer el buffer de esta manera
        //when(spyHelper.getBuffer()).thenReturn(bufferTest);

        //Si a la hora de eliminar eventos del buffer accedemos directamente al campo buffer nos vemos obligados a setear el valor para el campo.
        TestUtils.refectSetValue(spyHelper, "buffer", bufferTest);

        //A efectos de test dejamos que no se elimine la lista de eventos a procesar
        doNothing().when(spyHelper, "clearListEventToProcess");



    }

    /**
     * Crea un evento
     *
     * @param fileHeader      boolean indicando si el evento posee cabecera con el nombre del fichero en origen
     * @param fileHeaderName  String con el nombre de la cabecera que indica el fichero al que pertenece el evento
     * @param fileHeaderValue String con el valor que toma la cabecera (nombre del fichero al que pertenece)
     * @param message         String con el mensaje a incluir en el evento
     * @return Event con el evento creado
     * @throws Exception
     */
    @SuppressWarnings("all")
    private Event createEvent(boolean fileHeader, String fileHeaderName, String fileHeaderValue, String message) throws Exception {


        Event event = EventBuilder.withBody(message.getBytes());
        Map<String, String> headers = new HashMap<String, String>();

        if (fileHeader) {
            headers.put(fileHeaderName, fileHeaderValue);
        } else {
            headers.put(FileEventHelper.FILEHEADERNAME_FAKE, fileHeaderValue);
        }

        event.setHeaders(headers);

        return event;

    }


    /**
     * Crea un evento
     *
     * @param fileHeader      boolean indicando si el evento posee cabecera con el nombre del fichero en origen
     * @param fileHeaderName  String con el nombre de la cabecera que indica el fichero al que pertenece el evento
     * @param fileHeaderValue String con el valor que toma la cabecera (nombre del fichero al que pertenece)
     * @param message         String con el mensaje a incluir en el evento
     * @return Event con el evento creado
     * @throws Exception
     */
    @SuppressWarnings("all")
    private Event createEventWithTimestamp(boolean fileHeader, String fileHeaderName, String fileHeaderValue, String message, boolean withTimestamp) throws Exception {

        Event event = null;
        if (withTimestamp) {
            StringBuilder sb = new StringBuilder();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            sb.append(sdf.format(Calendar.getInstance().getTime()));
            sb.append(" - ").append(message);
            event = EventBuilder.withBody(sb.toString().getBytes());
        } else {
            event = EventBuilder.withBody(message.getBytes());
        }

        Map<String, String> headers = new HashMap<String, String>();

        if (fileHeader) {
            headers.put(fileHeaderName, fileHeaderValue);
        } else {
            headers.put(FileEventHelper.FILEHEADERNAME_FAKE, fileHeaderValue);
        }

        event.setHeaders(headers);

        return event;

    }

    /**
     * Crea una lista de eventos a partir del stack trace de un throwable
     *
     * @param throwable       Throwable a partir de cuyo stack trace se forma el listado de evnetos
     * @param fileHeader      boolean indicando si el evento posee cabecera con el nombre del fichero en origen
     * @param fileHeaderName  String con el nombre de la cabecera que indica el fichero al que pertenece el evento
     * @param fileHeaderValue String con el valor que toma la cabecera (nombre del fichero al que pertenece)
     * @return List con los eventos creados
     * @throws Exception
     */
    @SuppressWarnings("all")
    private List<Event> createListEventsFromThrowable(Throwable throwable, boolean fileHeader, String fileHeaderName, String fileHeaderValue) throws Exception {

        List<Event> throwableEvents = new Vector<>();

        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        String stackTrace = sw.toString();

        LOGGER.debug(stackTrace);

        BufferedReader br = new BufferedReader(new StringReader(stackTrace));
        List<String> stackTraceLines = br.lines().collect(Collectors.toList());

        for (String stackTraceLine : stackTraceLines) {
            Event eventStackTrace = createEvent(fileHeader, fileHeaderName, fileHeaderValue, stackTraceLine);

            throwableEvents.add(eventStackTrace);
        }

        return throwableEvents;
    }

    /**
     * Crea una lista de eventos de los cuales el primero lleva timestamp antes del mensaje (simulando syslog)
     * @param fileHeader      boolean indicando si el evento posee cabecera con el nombre del fichero en origen
     * @param fileHeaderName  String con el nombre de la cabecera que indica el fichero al que pertenece el evento
     * @param fileHeaderValue String con el valor que toma la cabecera (nombre del fichero al que pertenece)
     * @param message String con el mensaje a incluir en el evento
     * @param numEvents int numero de eventos a crear
     * @return List con los eventos creados
     * @throws Exception
     */
    @SuppressWarnings("all")
    private List<Event> createListEventsFirstTimestamp(boolean fileHeader, String fileHeaderName, String fileHeaderValue, String message, int numEvents) throws Exception {

        List<Event> listEvents = new Vector<>();
        Event event;
        if (numEvents > 0) {

            for (int i=0; i<numEvents; i++) {
                if (i==0) {
                    event = createEventWithTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, true);
                } else {
                    event = createEventWithTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, false);
                }

                listEvents.add(event);
            }

        }

        return listEvents;
    }


    /*******************************
     **** ONE SINGLE FILE TESTS ****
     *******************************/

    @Test
    @SuppressWarnings("all")
    public void testEmptyBuffer() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, false, null, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            Assert.assertNotNull("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess);
            Assert.assertNotNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess);
            Assert.assertNotNull("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess);
            Assert.assertNotNull("The value of bufferAfterProcess is not correct", bufferAfterProcess);

            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), 0);
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 0);
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testEmptyBuffer", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testOneFileOneSingleLineEvent() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            Event event = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(event);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se tiene que eliminar 1 evento del buffer (el evento simple enviado)
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), 1);
            //Se tiene que eliminar el elemento del buffer en la primera posicion.
            Assert.assertTrue("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.get(0).equals(0));
            //No existen eventos pendientes de envio
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se ha enviado 1 evento
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 1);
            //El buffer no contiene ningun elemento por enviar
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(1)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileOneSingleLineEvent", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testOneFileOneSingleLineEventRemoveFakeHeader() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader ficticio
            boolean fileHeader = false;
            String fileHeaderName = FILE_HEADER_NAME_FAKE;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            Event event = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(event);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se tiene que eliminar 1 evento del buffer (el evento simple enviado)
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), 1);
            //Se tiene que eliminar el elemento del buffer en la primera posicion.
            Assert.assertTrue("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.get(0).equals(0));
            //No existen eventos pendientes de envio
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se ha enviado 1 evento
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 1);
            //El buffer no contiene ningun elemento por enviar
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);
            //Comprobamos que el evento a enviar no contiene la cabecera ficticia
            Event eventToProcess = listEventToProcessAfterProcess.get(0);
            Assert.assertFalse("The value of bufferAfterProcess is not correct",eventToProcess.getHeaders().containsKey(fileHeaderName));

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(1)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileOneSingleLineEventRemoveFakeHeader", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testOneFileOneMultilineEventNoFlush() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsException = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;

            try {
                int a = 1 / 0;
            } catch (Exception e) {
                listEventsException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsException);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //No se ha indicado ningún elemento para eliminar
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), 0);
            //El map contiene 1 elemento con los eventos no enviados del file01
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 1);
            Assert.assertNotNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(fileHeaderValue));
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(fileHeaderValue).size(), listEventsException.size());
            //No se ha enviado ningún evento (todos quedan pendientes)
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 0);
            //El buffer no ha sido modificado
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), originalBufferTestSize);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(1)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileOneMultilineEventNoFlush", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testOneFileOneMultilineEventFlush() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsException = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;

            try {
                int a = 1 / 0;
            } catch (Exception e) {
                listEventsException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsException);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Todos los elementos se han indicado para borrar del buffer
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El no contiene eventos pendientes
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se ha enviado 1 único evento
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 1);
            //No quedan elementos en el buffer
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(1)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileOneMultilineEventFlush", e);
            Assert.fail();
        }

    }



    @Test
    @SuppressWarnings("all")
    public void testOneFileOneMultilineEventFlushFakeHeader() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsException = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader ficticio
            boolean fileHeader = false;
            String fileHeaderName = FILE_HEADER_NAME_FAKE;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;

            try {
                int a = 1 / 0;
            } catch (Exception e) {
                listEventsException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsException);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Todos los elementos se han indicado para borrar del buffer
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El no contiene eventos pendientes
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se ha enviado 1 único evento
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 1);
            //Comprobamos que el evento a enviar no contiene la cabecera ficticia
            Event eventToProcess = listEventToProcessAfterProcess.get(0);
            Assert.assertFalse("The value of bufferAfterProcess is not correct",eventToProcess.getHeaders().containsKey(fileHeaderName));
            //No quedan elementos en el buffer
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(1)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileOneMultilineEventFlushFakeHeader", e);
            Assert.fail();
        }


    }



    @Test
    @SuppressWarnings("all")
    public void testOneFileSimplePlusMultilineEventNoFlush() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsException = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            Event event = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(event);

            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsException);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se ha indicado 1 elemento para eliminar (el evento simple)
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), 1);
            //El map contiene 1 elemento con los eventos no enviados del file01
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 1);
            Assert.assertNotNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(fileHeaderValue));
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(fileHeaderValue).size(), listEventsException.size());
            //Se ha procesado el evento simple. Los eventos de excepcion quedan pendientes
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 1);
            //Del buffer original se ha eliminado el primer elemento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), originalBufferTestSize - 1);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileSimplePlusMultilineEventNoFlush", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testOneFileSimplePlusMultilineEventFlush() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsException = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            Event event = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(event);

            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsException);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se han indicado todos los eventos para eliminar (todos se han convertido en eventos a enviar)
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map No contiene elementos (todos se han convertido en eventos a enviar)
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se han procesado 2 eventos (el evento simple y el creado a partir de los eventos de excepcion)
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 2);
            //Del buffer original se han eliminado todos los elementos (todos se procesan)
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileSimplePlusMultilineEventFlush", e);
            Assert.fail();
        }

    }

    @Test
    @SuppressWarnings("all")
    public void testOneFileSimplePlusMultilinePlusSimpleEventNoFlush() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsException = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            Event event = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(event);

            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsException);

            Event anotherEvent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(anotherEvent);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se ha indicado todos los elementos del buffer para eliminar
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene elementos pendientes de eliminar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se han procesado el evento simple, el evento compuesto de la excepcion y el segundo evento simple.
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 3);
            //Del buffer original se han eliminado todos los elementos
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(3)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileSimplePlusMultilinePlusSimpleEventNoFlush", e);
            Assert.fail();
        }

    }



    @Test
    @SuppressWarnings("all")
    public void testOneFileSimplePlusMultilinePlusSimpleEventFlush() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsException = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            Event event = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(event);

            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsException);

            Event anotherEvent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(anotherEvent);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se ha indicado todos los elementos del buffer para eliminar
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene elementos pendientes de eliminar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se han procesado el evento simple, el evento compuesto de la excepcion y el segundo evento simple.
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 3);
            //Del buffer original se han eliminado todos los elementos
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(3)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileSimplePlusMultilinePlusSimpleEventFlush", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testOneFileSimplePlusDoubleMultilineEventNoFlush() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsException = null;
            List<Event> listEventsAnotherException = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            Event event = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(event);

            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsException);


            try {
                Object i = Integer.valueOf(42);
                String s = (String)i;
            } catch (Exception e) {
                listEventsAnotherException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsAnotherException);

            int firstExceptionEventsSize = listEventsException.size();
            int secondExceptionEventsSize = listEventsAnotherException.size();
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se ha indicado como elementos a eliminar los correspondientes al evento simple y a los eventos generados por la primera excepcion
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), firstExceptionEventsSize + 1);
            //El map contiene 1 elemento pendientes de eliminar (el correspondiente a la segunda excepcion
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 1);
            Assert.assertNotNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(fileHeaderValue));
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(fileHeaderValue).size(), secondExceptionEventsSize);
            //Se han procesado 2 eventos, el evento simple y el compuesto por los eventos de la primera excepcion
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 2);
            //Del buffer original se han eliminado los eventos correspondientes al evento simple y a los eventos correspondientes a la primera excepcion
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), secondExceptionEventsSize);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(3)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileSimplePlusDoubleMultilineEventNoFlush", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testOneFileSimplePlusDoubleMultilineEventFlush() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsException = null;
            List<Event> listEventsAnotherException = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            Event event = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(event);

            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsException);


            try {
                Object i = Integer.valueOf(42);
                String s = (String)i;
            } catch (Exception e) {
                listEventsAnotherException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsAnotherException);

            int firstExceptionEventsSize = listEventsException.size();
            int secondExceptionEventsSize = listEventsAnotherException.size();
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se ha indicado como elementos a eliminar los correspondientes todos los eventos
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map No contiene elementos pendientes de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se han procesado 3 eventos, el evento simple, el evento compuesto por los eventos correspondientes a la primera excepcion y el evento compuesto por los eventos de la segunda excepcion
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 3);
            //Del buffer original se han eliminado todos los eventos
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(3)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileSimplePlusDoubleMultilineEventFlush", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testOneFileSimplePlusDoubleMultilinePlusSimpleEventNoFlush() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsException = null;
            List<Event> listEventsAnotherException = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            Event event = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(event);

            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsException);


            try {
                Object i = Integer.valueOf(42);
                String s = (String)i;
            } catch (Exception e) {
                listEventsAnotherException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsAnotherException);

            Event anotherEvent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(anotherEvent);

            int firstExceptionEventsSize = listEventsException.size();
            int secondExceptionEventsSize = listEventsAnotherException.size();
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se ha indicado como elementos a eliminar todos los elementos (los correspondientes a los 2 eventos simples y a los 2 eventos generados por las excepciones)
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map No contiene elementos pendientes de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se han procesado 4 eventos, los 2 eventos simples y los 2 eventos compuestos por los eventos de las 2 excepciones
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 4);
            //Del buffer original se han eliminado todos los eventos
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(4)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileSimplePlusDoubleMultilinePlusSimpleEventNoFlush", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testOneFileSimplePlusDoubleMultilinePlusSimpleEventFlush() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsException = null;
            List<Event> listEventsAnotherException = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            Event event = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(event);

            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsException);


            try {
                Object i = Integer.valueOf(42);
                String s = (String)i;
            } catch (Exception e) {
                listEventsAnotherException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsAnotherException);

            Event anotherEvent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(anotherEvent);

            int firstExceptionEventsSize = listEventsException.size();
            int secondExceptionEventsSize = listEventsAnotherException.size();
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se ha indicado como elementos a eliminar todos los elementos (los correspondientes a los 2 eventos simples y a los 2 eventos generados por las excepciones)
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map No contiene elementos pendientes de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se han procesado 4 eventos, los 2 eventos simples y los 2 eventos compuestos por los eventos de las 2 excepciones
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 4);
            //Del buffer original se han eliminado todos los eventos
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(4)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileSimplePlusDoubleMultilinePlusSimpleEventFlush", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testOneFileSimplePlusDoubleMultilinePlusSimpleEventNoFlushNoRegexFirstLine() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsException = null;
            List<Event> listEventsAnotherException = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            Event event = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(event);

            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsException);


            try {
                Object i = Integer.valueOf(42);
                String s = (String)i;
            } catch (Exception e) {
                listEventsAnotherException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsAnotherException);

            Event anotherEvent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(anotherEvent);

            int firstExceptionEventsSize = listEventsException.size();
            int secondExceptionEventsSize = listEventsAnotherException.size();
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, null, false, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se ha indicado como elementos a eliminar todos los elementos (los correspondientes a los 2 eventos simples y al evento generado por las 2 excepciones)
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map No contiene elementos pendientes de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se han procesado 3 eventos, los 2 eventos simples y el evento creado a partir de las 2 excepciones contíguas
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 3);
            //Del buffer original se han eliminado todos los eventos
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileSimplePlusDoubleMultilinePlusSimpleEventNoFlushNoRegexFirstLine", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testOneFileSimplePlusDoubleMultilinePlusSimpleEventFlushNoRegexFirstLine() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsException = null;
            List<Event> listEventsAnotherException = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            Event event = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(event);

            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsException);


            try {
                Object i = Integer.valueOf(42);
                String s = (String)i;
            } catch (Exception e) {
                listEventsAnotherException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsAnotherException);

            Event anotherEvent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(anotherEvent);

            int firstExceptionEventsSize = listEventsException.size();
            int secondExceptionEventsSize = listEventsAnotherException.size();
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, null, false, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se ha indicado como elementos a eliminar todos los elementos (los correspondientes a los 2 eventos simples y al evento generado por las 2 excepciones)
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map No contiene elementos pendientes de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se han procesado 3 eventos, los 2 eventos simples y el evento creado a partir de las 2 excepciones contíguas
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 3);
            //Del buffer original se han eliminado todos los eventos
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileSimplePlusDoubleMultilinePlusSimpleEventFlushNoRegexFirstLine", e);
            Assert.fail();
        }

    }


    /******************************
     **** MULTIPLE FILES TESTS ****
     ******************************/

    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesOneSingleLineEvent() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            Event event = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(event);

            //Creamos otro evento simple asignado al fichero "File02". Con fileHeader rea
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;

            Event anotherEvent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(anotherEvent);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se tiene que eliminar 2 eventos del buffer (los eventos simples envíados)
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), 2);
            //Se tiene que eliminar el elemento del buffer en la primera y segunda posiciones.
            Assert.assertTrue("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.get(0).equals(0));
            Assert.assertTrue("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.get(1).equals(1));
            //No existen eventos pendientes de envio
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se ha enviado 2 eventos
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 2);
            //El primer evento corresponde al evento del primer fichero
            Event firstEvent = listEventToProcessAfterProcess.get(0);
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", firstEvent.getHeaders().get(fileHeaderName), FILE_HEADER_VALUE_FILE01);
            //El segundo evento corresponde al evento del segundo fichero
            Event secondEvent = listEventToProcessAfterProcess.get(1);
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", secondEvent.getHeaders().get(fileHeaderName), FILE_HEADER_VALUE_FILE02);
            //El buffer no contiene ningun elemento por enviar
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesOneSingleLineEvent", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesOneMultilineEventNoFlush() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsExceptionFirstFile = null;
            List<Event> listEventsExceptionSecondFile = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;

            try {
                int a = 1 / 0;
            } catch (Exception e) {
                listEventsExceptionFirstFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsExceptionFirstFile);


            //Creamos una lista de eventos a partir del stack trace de otra excepcion. Asignamos estos eventos al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsExceptionSecondFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsExceptionSecondFile);

            int firstFileExceptionEventsSize = listEventsExceptionFirstFile.size();
            int secondFileExceptionEventsSize = listEventsExceptionSecondFile.size();
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //No se ha indicado ningún elemento para eliminar
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), 0);
            //El map contiene 2 elemento con los eventos no enviados del file01 y del file02
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 2);
            Assert.assertNotNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE01));
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE01).size(), firstFileExceptionEventsSize);
            Assert.assertNotNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE02));
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE02).size(), secondFileExceptionEventsSize);
            //No se ha enviado ningún evento (todos quedan pendientes)
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 0);
            //El buffer no ha sido modificado
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), originalBufferTestSize);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesOneMultilineEventNoFlush", e);
            Assert.fail();
        }

    }



    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesOneMultilineEventNoFlushRiffling() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsExceptionFirstFile = null;
            List<Event> listEventsExceptionSecondFile = null;
            List<Event> listEventsRiffling = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;

            try {
                int a = 1 / 0;
            } catch (Exception e) {
                listEventsExceptionFirstFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            //Creamos una lista de eventos a partir del stack trace de otra excepcion. Asignamos estos eventos al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsExceptionSecondFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            int firstFileExceptionEventsSize = listEventsExceptionFirstFile.size();
            int secondFileExceptionEventsSize = listEventsExceptionSecondFile.size();

            //Realizamos el riffling de los eventos para garantizar que van mezclados los eventos de ambos ficheros
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsExceptionFirstFile, listEventsExceptionSecondFile);
            bufferTest.addAll(listEventsRiffling);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //No se ha indicado ningún elemento para eliminar
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), 0);
            //El map contiene 2 elemento con los eventos no enviados del file01 y del file02
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 2);
            Assert.assertNotNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE01));
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE01).size(), firstFileExceptionEventsSize);
            Assert.assertNotNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE02));
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE02).size(), secondFileExceptionEventsSize);
            //No se ha enviado ningún evento (todos quedan pendientes)
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 0);
            //El buffer no ha sido modificado
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), originalBufferTestSize);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesOneMultilineEventNoFlushRiffling", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesOneMultilineEventFlush() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsExceptionFirstFile = null;
            List<Event> listEventsExceptionSecondFile = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;

            try {
                int a = 1 / 0;
            } catch (Exception e) {
                listEventsExceptionFirstFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsExceptionFirstFile);


            //Creamos una lista de eventos a partir del stack trace de otra excepcion. Asignamos estos eventos al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsExceptionSecondFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsExceptionSecondFile);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Todos los elementos se han indicado para eliminar
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene ningún elemento pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se han procesado 2 eventos (1 por cada fichero)
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 2);
            //El primer elemento de la lista corresponde al evento compuesto creado por los eventos de excepcion del primer fichero
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.get(0).getHeaders().get(fileHeaderName), FILE_HEADER_VALUE_FILE01);
            //El segundo elemento de la lista corresponde al evento compuesto creado por los eventos de excepcion del segundo fichero
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.get(1).getHeaders().get(fileHeaderName), FILE_HEADER_VALUE_FILE02);
            //Del buffer se han eliminado todos los elementos
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesOneMultilineEventFlush", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesOneMultilineEventFlushRiffling() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsExceptionFirstFile = null;
            List<Event> listEventsExceptionSecondFile = null;
            List<Event> listEventsRiffling = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;

            try {
                int a = 1 / 0;
            } catch (Exception e) {
                listEventsExceptionFirstFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            //Creamos una lista de eventos a partir del stack trace de otra excepcion. Asignamos estos eventos al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsExceptionSecondFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            //Realizamos el riffling de los eventos para garantizar que van mezclados los eventos de ambos ficheros
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsExceptionFirstFile, listEventsExceptionSecondFile);
            bufferTest.addAll(listEventsRiffling);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Todos los elementos se han indicado para eliminar
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene ningún elemento pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se han procesado 2 eventos (1 por cada fichero)
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 2);
            //Dependiendo del riffling efectuado los eventos pertenecientes a cada fichero iran antes o despues.
            for (Event eventToProcess : listEventToProcessAfterProcess) {
                String fileHeaderNameValue = eventToProcess.getHeaders().get(fileHeaderName);
                Assert.assertTrue("The value of listEventToProcessAfterProcess is not correct", fileHeaderNameValue.equals(FILE_HEADER_VALUE_FILE01) | fileHeaderNameValue.equals(FILE_HEADER_VALUE_FILE02));
            }
            //Del buffer se han eliminado todos los elementos
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesOneMultilineEventFlushRiffling", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileSimpleSecondFileExceptionNoFlush() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsExceptionSecondFile = null;

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            Event event = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(event);

            //Creamos un evento simple para el segundo fichero
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            Event anotherevent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(anotherevent);

            //Creamos una lista de eventos a partir del stack trace de una excepcion. Asignamos estos eventos al fichero "File02". Con fileHeader real
            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsExceptionSecondFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsExceptionSecondFile);

            int secondFileExceptionEventsSize = listEventsExceptionSecondFile.size();
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se han indicado 2 eventos para eliminar (los eventos simples de ambos ficheros)
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), 2);
            //El map contiene 1 elemento con los eventos no enviados del file02
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 1);
            Assert.assertNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE01));
            Assert.assertNotNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE02));
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE02).size(), secondFileExceptionEventsSize);
            //Se han enviado los eventos simples
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 2);
            //En el buffer permanecen los eventos pendientes del file02
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), secondFileExceptionEventsSize);
            for (Event bufferEvent : bufferAfterProcess) {
                String fileHeaderNameValue = bufferEvent.getHeaders().get(fileHeaderName);
                Assert.assertEquals("The value of bufferAfterProcess is not correct", fileHeaderNameValue, FILE_HEADER_VALUE_FILE02);
            }

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(3)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileSimpleSecondFileExceptionNoFlush", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileSimpleSecondFileExceptionFlush() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsExceptionSecondFile = null;

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            Event event = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(event);

            //Creamos un evento simple para el segundo fichero
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            Event anotherevent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(anotherevent);

            //Creamos una lista de eventos a partir del stack trace de una excepcion. Asignamos estos eventos al fichero "File02". Con fileHeader real
            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsExceptionSecondFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsExceptionSecondFile);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Todos los eventos seran seleccionados para eliminar (seran procesados)
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene ningún elemento pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se han enviado los 2 eventos simples y el evento compuesto por los eventos de la excepcion del segundo fichero
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 3);
            //En el buffer no permanece ningun evento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(3)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileSimpleSecondFileExceptionFlush", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileSimpleSecondFileExceptionNoFlushRiffling() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsFirstFile = new ArrayList<Event>();
            List<Event> listEventsSecondFile = new ArrayList<Event>();
            List<Event> listEventsExceptionSecondFile = null;
            List<Event> listEventsRiffling = null;

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            Event event = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            listEventsFirstFile.add(event);

            //Creamos un evento simple para el segundo fichero "File02"
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            Event anotherevent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            listEventsSecondFile.add(anotherevent);

            //Creamos una lista de eventos a partir del stack trace de una excepcion. Asignamos estos eventos al fichero "File02". Con fileHeader real
            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsExceptionSecondFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            listEventsSecondFile.addAll(listEventsExceptionSecondFile);

            //Realizamos el riffling de los eventos para garantizar que van mezclados los eventos de ambos ficheros
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsFirstFile, listEventsSecondFile);
            bufferTest.addAll(listEventsRiffling);

            int secondFileExceptionEventsSize = listEventsExceptionSecondFile.size();
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se han indicado 2 eventos para eliminar (los eventos simples de ambos ficheros)
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), 2);
            //El map contiene 1 elemento con los eventos no enviados del file02
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 1);
            Assert.assertNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE01));
            Assert.assertNotNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE02));
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE02).size(), secondFileExceptionEventsSize);
            //Se han enviado los eventos simples
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 2);
            //En el buffer permanecen los eventos pendientes del file02
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), secondFileExceptionEventsSize);
            for (Event bufferEvent : bufferAfterProcess) {
                String fileHeaderNameValue = bufferEvent.getHeaders().get(fileHeaderName);
                Assert.assertEquals("The value of bufferAfterProcess is not correct", fileHeaderNameValue, FILE_HEADER_VALUE_FILE02);
            }

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(3)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileSimpleSecondFileExceptionNoFlushRiffling", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileSimpleSecondFileExceptionFlushRiffling() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsFirstFile = new ArrayList<Event>();
            List<Event> listEventsSecondFile = new ArrayList<Event>();
            List<Event> listEventsExceptionSecondFile = null;
            List<Event> listEventsRiffling = null;

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            Event event = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            listEventsFirstFile.add(event);

            //Creamos un evento simple para el segundo fichero
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            Event anotherevent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            listEventsSecondFile.add(anotherevent);

            //Creamos una lista de eventos a partir del stack trace de una excepcion. Asignamos estos eventos al fichero "File02". Con fileHeader real
            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsExceptionSecondFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            listEventsSecondFile.addAll(listEventsExceptionSecondFile);

            //Realizamos el riffling de los eventos para garantizar que van mezclados los eventos de ambos ficheros
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsFirstFile, listEventsSecondFile);
            bufferTest.addAll(listEventsRiffling);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Todos los eventos seran seleccionados para eliminar (seran procesados)
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene ningún elemento pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se han enviado los 2 eventos simples y el evento compuesto por los eventos de la excepcion del segundo fichero
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 3);
            //En el buffer no permanece ningun evento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(3)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileSimpleSecondFileExceptionFlush", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileExceptionSecondFileExceptionNoFlush() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsExceptionFirstFile = null;
            List<Event> listEventsExceptionSecondFile = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;


            try {
                int a = 1 / 0;
            } catch (Exception e) {
                listEventsExceptionFirstFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsExceptionFirstFile);


            //Creamos una lista de eventos a partir del stack trace de una excepcion. Asignamos estos eventos al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsExceptionSecondFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsExceptionSecondFile);

            //Creamos un evento simple para el segundo fichero "File02"
            String message = MESSAGE_SIMPLE_EVENT;
            Event anotherevent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(anotherevent);

            int firstFileExceptionEventsSize = listEventsExceptionFirstFile.size();
            int secondFileExceptionEventsSize = listEventsExceptionSecondFile.size();
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se han indicado los eventos del segundo fichero para eliminar (los del evento compuesto + el evento simple)
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), secondFileExceptionEventsSize + 1);
            //El map contiene 1 elemento con los eventos no enviados del file01
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 1);
            Assert.assertNotNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE01));
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE01).size(), firstFileExceptionEventsSize);
            Assert.assertNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE02));
            //Se han enviado el evento compuesto del file02 + el evento simple del file02
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 2);
            //En el buffer permanecen los eventos pendientes del file01
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), firstFileExceptionEventsSize);
            for (Event bufferEvent : bufferAfterProcess) {
                String fileHeaderNameValue = bufferEvent.getHeaders().get(fileHeaderName);
                Assert.assertEquals("The value of bufferAfterProcess is not correct", fileHeaderNameValue, FILE_HEADER_VALUE_FILE01);
            }

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(3)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileExceptionSecondFileExceptionNoFlush", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileExceptionSecondFileExceptionFlush() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsExceptionFirstFile = null;
            List<Event> listEventsExceptionSecondFile = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;


            try {
                int a = 1 / 0;
            } catch (Exception e) {
                listEventsExceptionFirstFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsExceptionFirstFile);


            //Creamos una lista de eventos a partir del stack trace de una excepcion. Asignamos estos eventos al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsExceptionSecondFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsExceptionSecondFile);

            //Creamos un evento simple para el segundo fichero "File02"
            String message = MESSAGE_SIMPLE_EVENT;
            Event anotherevent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(anotherevent);

            int firstFileExceptionEventsSize = listEventsExceptionFirstFile.size();
            int secondFileExceptionEventsSize = listEventsExceptionSecondFile.size();
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se han indicado todos los eventos para eliminar
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene ningún elemento pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se han enviado el evento compuesto del file01 + el evento compuesto del file02 + el evento simple del file02
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 3);
            //En el buffer no permanecen nigún elemento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(3)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileExceptionSecondFileExceptionFlush", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileExceptionSecondFileExceptionNoFlushRiffling() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsFirstFile = new ArrayList<Event>();
            List<Event> listEventsSecondFile = new ArrayList<Event>();
            List<Event> listEventsExceptionFirstFile = null;
            List<Event> listEventsExceptionSecondFile = null;
            List<Event> listEventsRiffling = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;


            try {
                int a = 1 / 0;
            } catch (Exception e) {
                listEventsExceptionFirstFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            listEventsFirstFile.addAll(listEventsExceptionFirstFile);


            //Creamos una lista de eventos a partir del stack trace de una excepcion. Asignamos estos eventos al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsExceptionSecondFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            listEventsSecondFile.addAll(listEventsExceptionSecondFile);

            //Creamos un evento simple para el segundo fichero "File02"
            String message = MESSAGE_SIMPLE_EVENT;
            Event anotherevent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            listEventsSecondFile.add(anotherevent);

            int firstFileExceptionEventsSize = listEventsExceptionFirstFile.size();
            int secondFileExceptionEventsSize = listEventsExceptionSecondFile.size();

            //Realizamos el riffling de los eventos para garantizar que van mezclados los eventos de ambos ficheros
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsFirstFile, listEventsSecondFile);
            bufferTest.addAll(listEventsRiffling);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se han indicado los eventos del segundo fichero para eliminar (los del evento compuesto + el evento simple)
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), secondFileExceptionEventsSize + 1);
            //El map contiene 1 elemento con los eventos no enviados del file01
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 1);
            Assert.assertNotNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE01));
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE01).size(), firstFileExceptionEventsSize);
            Assert.assertNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE02));
            //Se han enviado el evento compuesto del file02 + el evento simple del file02
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 2);
            //En el buffer permanecen los eventos pendientes del file01
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), firstFileExceptionEventsSize);
            for (Event bufferEvent : bufferAfterProcess) {
                String fileHeaderNameValue = bufferEvent.getHeaders().get(fileHeaderName);
                Assert.assertEquals("The value of bufferAfterProcess is not correct", fileHeaderNameValue, FILE_HEADER_VALUE_FILE01);
            }

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(3)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileExceptionSecondFileExceptionNoFlushRiffling", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileExceptionSecondFileExceptionFlushRiffling() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsFirstFile = new ArrayList<Event>();
            List<Event> listEventsSecondFile = new ArrayList<Event>();
            List<Event> listEventsExceptionFirstFile = null;
            List<Event> listEventsExceptionSecondFile = null;
            List<Event> listEventsRiffling = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;


            try {
                int a = 1 / 0;
            } catch (Exception e) {
                listEventsExceptionFirstFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            listEventsFirstFile.addAll(listEventsExceptionFirstFile);


            //Creamos una lista de eventos a partir del stack trace de una excepcion. Asignamos estos eventos al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsExceptionSecondFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            listEventsSecondFile.addAll(listEventsExceptionSecondFile);

            //Creamos un evento simple para el segundo fichero "File02"
            String message = MESSAGE_SIMPLE_EVENT;
            Event anotherevent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            listEventsSecondFile.add(anotherevent);

            int firstFileExceptionEventsSize = listEventsExceptionFirstFile.size();
            int secondFileExceptionEventsSize = listEventsExceptionSecondFile.size();

            //Realizamos el riffling de los eventos para garantizar que van mezclados los eventos de ambos ficheros
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsFirstFile, listEventsSecondFile);
            bufferTest.addAll(listEventsRiffling);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se han indicado todos los eventos para eliminar
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene ningún elemento pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se han enviado el evento compuesto del file01 + el evento compuesto del file02 + el evento simple del file02
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 3);
            //En el buffer no permanecen nigún elemento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(3)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileExceptionSecondFileExceptionFlushRiffling", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileDoubleExceptionSecondFileExceptionNoFlush() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsExceptionFirstFileFirstException = null;
            List<Event> listEventsExceptionFirstFileSecondException = null;
            List<Event> listEventsExceptionSecondFile = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;


            try {
                int a = 1 / 0;
            } catch (Exception e) {
                listEventsExceptionFirstFileFirstException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsExceptionFirstFileFirstException);

            try {
                Object i = Integer.valueOf(42);
                String s = (String)i;
            } catch (Exception e) {
                listEventsExceptionFirstFileSecondException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsExceptionFirstFileSecondException);


            //Creamos una lista de eventos a partir del stack trace de una excepcion. Asignamos estos eventos al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsExceptionSecondFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsExceptionSecondFile);

            //Creamos un evento simple para el segundo fichero "File02"
            String message = MESSAGE_SIMPLE_EVENT;
            Event anotherevent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(anotherevent);

            int firstFileFirstExceptionEventsSize = listEventsExceptionFirstFileFirstException.size();
            int firstFileSecondExceptionEventsSize = listEventsExceptionFirstFileSecondException.size();
            int secondFileExceptionEventsSize = listEventsExceptionSecondFile.size();
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se han indicado para eliminar los eventos de la primera excepcion (File01) + Eventos excepcion segundo fichero (File02) + evento simple segundo fichero (File02)
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), firstFileFirstExceptionEventsSize + secondFileExceptionEventsSize + 1);
            //El map contiene 1 elemento con los eventos no enviados del file01 (los eventos de la segunda excepcion
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 1);
            Assert.assertNotNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE01));
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE01).size(), firstFileSecondExceptionEventsSize);
            Assert.assertNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE02));
            //Se han enviado el evento compuesto de la primera excepcion (File01), el evento compuesto de la excepcion (File02) + el evento simple del File02
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 3);
            //En el buffer permanecen los eventos pendientes del file01 (corresponden a la segunda excepcion
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), secondFileExceptionEventsSize);
            for (Event bufferEvent : bufferAfterProcess) {
                String fileHeaderNameValue = bufferEvent.getHeaders().get(fileHeaderName);
                Assert.assertEquals("The value of bufferAfterProcess is not correct", fileHeaderNameValue, FILE_HEADER_VALUE_FILE01);
            }

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(4)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileDoubleExceptionSecondFileExceptionNoFlush", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileDoubleExceptionSecondFileExceptionFlush() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsExceptionFirstFileFirstException = null;
            List<Event> listEventsExceptionFirstFileSecondException = null;
            List<Event> listEventsExceptionSecondFile = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;


            try {
                int a = 1 / 0;
            } catch (Exception e) {
                listEventsExceptionFirstFileFirstException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsExceptionFirstFileFirstException);

            try {
                Object i = Integer.valueOf(42);
                String s = (String)i;
            } catch (Exception e) {
                listEventsExceptionFirstFileSecondException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsExceptionFirstFileSecondException);


            //Creamos una lista de eventos a partir del stack trace de una excepcion. Asignamos estos eventos al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsExceptionSecondFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsExceptionSecondFile);

            //Creamos un evento simple para el segundo fichero "File02"
            String message = MESSAGE_SIMPLE_EVENT;
            Event anotherevent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            bufferTest.add(anotherevent);

            int firstFileFirstExceptionEventsSize = listEventsExceptionFirstFileFirstException.size();
            int firstFileSecondExceptionEventsSize = listEventsExceptionFirstFileSecondException.size();
            int secondFileExceptionEventsSize = listEventsExceptionSecondFile.size();
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se han indicado para eliminar todos los eventos (se procesan todos)
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene elementos pendientes de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se han enviado 4 eventos (el evento compuesto de la primera excepcion (File01) + el evento compuesto de la segunda excepcion (File02) + el evento compuesto de la excepcion (File02) + el evento simple del File02
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 4);
            //En el buffer no permanece ningún evento (todos han sido enviado para procesar)
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(4)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileDoubleExceptionSecondFileExceptionFlush", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileDoubleExceptionSecondFileExceptionNoFlushRiffling() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsFirstFile = new ArrayList<Event>();
            List<Event> listEventsSecondFile = new ArrayList<Event>();
            List<Event> listEventsExceptionFirstFileFirstException = null;
            List<Event> listEventsExceptionFirstFileSecondException = null;
            List<Event> listEventsExceptionSecondFile = null;
            List<Event> listEventsRiffling = null;


            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;


            try {
                int a = 1 / 0;
            } catch (Exception e) {
                listEventsExceptionFirstFileFirstException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            listEventsFirstFile.addAll(listEventsExceptionFirstFileFirstException);

            try {
                Object i = Integer.valueOf(42);
                String s = (String)i;
            } catch (Exception e) {
                listEventsExceptionFirstFileSecondException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            listEventsFirstFile.addAll(listEventsExceptionFirstFileSecondException);


            //Creamos una lista de eventos a partir del stack trace de una excepcion. Asignamos estos eventos al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsExceptionSecondFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            listEventsSecondFile.addAll(listEventsExceptionSecondFile);

            //Creamos un evento simple para el segundo fichero "File02"
            String message = MESSAGE_SIMPLE_EVENT;
            Event anotherevent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            listEventsSecondFile.add(anotherevent);

            int firstFileFirstExceptionEventsSize = listEventsExceptionFirstFileFirstException.size();
            int firstFileSecondExceptionEventsSize = listEventsExceptionFirstFileSecondException.size();
            int secondFileExceptionEventsSize = listEventsExceptionSecondFile.size();

            //Realizamos el riffling de los eventos para garantizar que van mezclados los eventos de ambos ficheros
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsFirstFile, listEventsSecondFile);
            bufferTest.addAll(listEventsRiffling);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se han indicado para eliminar los eventos de la primera excepcion (File01) + Eventos excepcion segundo fichero (File02) + evento simple segundo fichero (File02)
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), firstFileFirstExceptionEventsSize + secondFileExceptionEventsSize + 1);
            //El map contiene 1 elemento con los eventos no enviados del file01 (los eventos de la segunda excepcion
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 1);
            Assert.assertNotNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE01));
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE01).size(), firstFileSecondExceptionEventsSize);
            Assert.assertNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE02));
            //Se han enviado el evento compuesto de la primera excepcion (File01), el evento compuesto de la excepcion (File02) + el evento simple del File02
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 3);
            //En el buffer permanecen los eventos pendientes del file01 (corresponden a la segunda excepcion
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), secondFileExceptionEventsSize);
            for (Event bufferEvent : bufferAfterProcess) {
                String fileHeaderNameValue = bufferEvent.getHeaders().get(fileHeaderName);
                Assert.assertEquals("The value of bufferAfterProcess is not correct", fileHeaderNameValue, FILE_HEADER_VALUE_FILE01);
            }

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(4)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileDoubleExceptionSecondFileExceptionNoFlushRiffling", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileDoubleExceptionSecondFileExceptionFlushRiffling() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsFirstFile = new ArrayList<Event>();
            List<Event> listEventsSecondFile = new ArrayList<Event>();
            List<Event> listEventsExceptionFirstFileFirstException = null;
            List<Event> listEventsExceptionFirstFileSecondException = null;
            List<Event> listEventsExceptionSecondFile = null;
            List<Event> listEventsRiffling = null;


            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;


            try {
                int a = 1 / 0;
            } catch (Exception e) {
                listEventsExceptionFirstFileFirstException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            listEventsFirstFile.addAll(listEventsExceptionFirstFileFirstException);

            try {
                Object i = Integer.valueOf(42);
                String s = (String)i;
            } catch (Exception e) {
                listEventsExceptionFirstFileSecondException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            listEventsFirstFile.addAll(listEventsExceptionFirstFileSecondException);


            //Creamos una lista de eventos a partir del stack trace de una excepcion. Asignamos estos eventos al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsExceptionSecondFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            listEventsSecondFile.addAll(listEventsExceptionSecondFile);

            //Creamos un evento simple para el segundo fichero "File02"
            String message = MESSAGE_SIMPLE_EVENT;
            Event anotherevent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            listEventsSecondFile.add(anotherevent);

            int firstFileFirstExceptionEventsSize = listEventsExceptionFirstFileFirstException.size();
            int firstFileSecondExceptionEventsSize = listEventsExceptionFirstFileSecondException.size();
            int secondFileExceptionEventsSize = listEventsExceptionSecondFile.size();

            //Realizamos el riffling de los eventos para garantizar que van mezclados los eventos de ambos ficheros
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsFirstFile, listEventsSecondFile);
            bufferTest.addAll(listEventsRiffling);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se han indicado para eliminar todos los eventos (se procesan todos)
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene elementos pendientes de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se han enviado 4 eventos (el evento compuesto de la primera excepcion (File01) + el evento compuesto de la segunda excepcion (File02) + el evento compuesto de la excepcion (File02) + el evento simple del File02
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 4);
            //En el buffer no permanece ningún evento (todos han sido enviado para procesar)
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(4)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileDoubleExceptionSecondFileExceptionFlushRiffling", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileDoubleExceptionSecondFileExceptionSimpleThirdFileSimpleEventNoFlushRiffling() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsFirstFile = new ArrayList<Event>();
            List<Event> listEventsSecondFile = new ArrayList<Event>();
            List<Event> listEventsThirdFile = new ArrayList<Event>();
            List<Event> listEventsExceptionFirstFileFirstException = null;
            List<Event> listEventsExceptionFirstFileSecondException = null;
            List<Event> listEventsExceptionSecondFile = null;
            List<Event> listEventsRiffling = null;


            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;


            try {
                int a = 1 / 0;
            } catch (Exception e) {
                listEventsExceptionFirstFileFirstException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            listEventsFirstFile.addAll(listEventsExceptionFirstFileFirstException);

            try {
                Object i = Integer.valueOf(42);
                String s = (String)i;
            } catch (Exception e) {
                listEventsExceptionFirstFileSecondException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            listEventsFirstFile.addAll(listEventsExceptionFirstFileSecondException);


            //Creamos una lista de eventos a partir del stack trace de una excepcion. Asignamos estos eventos al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsExceptionSecondFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            listEventsSecondFile.addAll(listEventsExceptionSecondFile);

            //Creamos un evento simple para el segundo fichero "File02"
            String message = MESSAGE_SIMPLE_EVENT + "File02";
            Event anotherEvent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);
            listEventsSecondFile.add(anotherEvent);

            //Creamos un evento simple para el tercer fichero "File03"
            fileHeaderValue = FILE_HEADER_VALUE_FILE03;
            message = MESSAGE_SIMPLE_EVENT + "File03";
            Event additionalEvent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);

            listEventsThirdFile.add(additionalEvent);

            int firstFileFirstExceptionEventsSize = listEventsExceptionFirstFileFirstException.size();
            int firstFileSecondExceptionEventsSize = listEventsExceptionFirstFileSecondException.size();
            int secondFileExceptionEventsSize = listEventsExceptionSecondFile.size();
            int thirdFileEventsSize = listEventsThirdFile.size();

            //Realizamos el riffling de los eventos para garantizar que van mezclados los eventos de ambos ficheros
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsFirstFile, listEventsSecondFile);
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsRiffling, listEventsThirdFile);
            bufferTest.addAll(listEventsRiffling);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se han indicado para eliminar los eventos de la primera excepcion (File01) + Eventos excepcion segundo fichero (File02) + evento simple segundo fichero (File02) + evento simple (File03)
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), firstFileFirstExceptionEventsSize + secondFileExceptionEventsSize + 2);
            //El map contiene 1 elemento con los eventos no enviados del file01 (los eventos de la segunda excepcion
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 1);
            Assert.assertNotNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE01));
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE01).size(), firstFileSecondExceptionEventsSize);
            Assert.assertNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE02));
            Assert.assertNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.get(FILE_HEADER_VALUE_FILE03));
            //Se han enviado el evento compuesto de la primera excepcion (File01), el evento compuesto de la excepcion (File02) + el evento simple de File02 + el evento simple de File03
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 4);
            //En el buffer permanecen los eventos pendientes del file01 (corresponden a la segunda excepcion
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), secondFileExceptionEventsSize);
            for (Event bufferEvent : bufferAfterProcess) {
                String fileHeaderNameValue = bufferEvent.getHeaders().get(fileHeaderName);
                Assert.assertEquals("The value of bufferAfterProcess is not correct", fileHeaderNameValue, FILE_HEADER_VALUE_FILE01);
            }

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(5)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 2)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileDoubleExceptionSecondFileExceptionSimpleThirdFileSimpleEventNoFlushRiffling", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileDoubleExceptionSecondFileExceptionThirdFileSimpleEventFlushRiffling() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsFirstFile = new ArrayList<Event>();
            List<Event> listEventsSecondFile = new ArrayList<Event>();
            List<Event> listEventsThirdFile = new ArrayList<Event>();
            List<Event> listEventsExceptionFirstFileFirstException = null;
            List<Event> listEventsExceptionFirstFileSecondException = null;
            List<Event> listEventsExceptionSecondFile = null;
            List<Event> listEventsRiffling = null;


            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;


            try {
                int a = 1 / 0;
            } catch (Exception e) {
                listEventsExceptionFirstFileFirstException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            listEventsFirstFile.addAll(listEventsExceptionFirstFileFirstException);

            try {
                Object i = Integer.valueOf(42);
                String s = (String)i;
            } catch (Exception e) {
                listEventsExceptionFirstFileSecondException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            listEventsFirstFile.addAll(listEventsExceptionFirstFileSecondException);


            //Creamos una lista de eventos a partir del stack trace de una excepcion. Asignamos estos eventos al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            try {
                String nullString = null;
                nullString.toString();
            } catch (Exception e) {
                listEventsExceptionSecondFile = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            listEventsSecondFile.addAll(listEventsExceptionSecondFile);


            //Creamos un evento simple para el tercer fichero "File03"
            fileHeaderValue = FILE_HEADER_VALUE_FILE03;
            String message = MESSAGE_SIMPLE_EVENT + "File03";
            Event additionalEvent = createEvent(fileHeader, fileHeaderName, fileHeaderValue, message);

            listEventsThirdFile.add(additionalEvent);

            int firstFileFirstExceptionEventsSize = listEventsExceptionFirstFileFirstException.size();
            int firstFileSecondExceptionEventsSize = listEventsExceptionFirstFileSecondException.size();
            int secondFileExceptionEventsSize = listEventsExceptionSecondFile.size();
            int thirdFileEventsSize = listEventsThirdFile.size();

            //Realizamos el riffling de los eventos para garantizar que van mezclados los eventos de ambos ficheros
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsFirstFile, listEventsSecondFile);
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsRiffling, listEventsThirdFile);
            bufferTest.addAll(listEventsRiffling);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer,Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer,Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se han indicado para eliminar todos los eventos (se procesan todos)
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene elementos pendientes de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se han enviado 4 eventos (el evento compuesto de la primera excepcion (File01) + el evento compuesto de la segunda excepcion (File01)
            // + el evento compuesto de la excepcion (File02) + el evento simple de File03
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 4);
            //En el buffer no permanece ningún evento (todos han sido enviado para procesar)
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(4)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize - 1)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileDoubleExceptionSecondFileExceptionThirdFileSimpleEventFlushRiffling", e);
            Assert.fail();
        }

    }


    /********************************************
     **** ONE SINGLE FILE NEGATE REGEX TESTS ****
     ********************************************/

    @Test
    @SuppressWarnings("all")
    public void testEmptyBufferNegateRegex() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, false, false, null, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            Assert.assertNotNull("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess);
            Assert.assertNotNull("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess);
            Assert.assertNotNull("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess);
            Assert.assertNotNull("The value of bufferAfterProcess is not correct", bufferAfterProcess);

            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), 0);
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 0);
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());


        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testEmptyBufferNegateRegex", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testOneFileOneTimestampEventNegateRegexNoFlush() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            List<Event> listEvents = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1);
            bufferTest.addAll(listEvents);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //No se ha eliminado ningun evento (todos quedan pendientes de proceso
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), 0);
            //El map contiene 1 elemento pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 1);
            //No se ha enviado ningún evento (quedan pendientes de proceso)
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 0);
            //En el buffer permanecen todos los eventos
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), originalBufferTestSize);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(1)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileOneTimestampEventNegateRegexNoFlush", e);
            Assert.fail();
        }
    }



    @Test
    @SuppressWarnings("all")
    public void testOneFileOneTimestampEventNegateRegexFlush() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            List<Event> listEvents = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1);
            bufferTest.addAll(listEvents);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se han eliminado todos los eventos
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene elementos pendientes de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //SE ha enviado 1 evento
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 1);
            //En el buffer no queda ningún evento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(1)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileOneTimestampEventNegateRegexNoFlush", e);
            Assert.fail();
        }
    }



    @Test
    @SuppressWarnings("all")
    public void testOneFileOneTimestampPlusNoTimestampEventNegateRegexNoFlush() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            List<Event> listEvents = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);
            bufferTest.addAll(listEvents);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //No se ha eliminado ningun evento (todos quedan pendientes de proceso
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), 0);
            //El map contiene 1 elemento pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 1);
            //No se ha enviado ningún evento (quedan pendientes de proceso)
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 0);
            //En el buffer permanecen todos los eventos
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), originalBufferTestSize);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(1)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileOneTimestampPlusNoTimestampEventNegateRegexNoFlush", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testOneFileOneTimestampPlusNoTimestampEventNegateRegexFlush() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            List<Event> listEvents = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);
            bufferTest.addAll(listEvents);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Todos los eventos son eliminados
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene ningún elemento pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se ha envíado 1 evento compuesto por todos los eventos pendientes
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 1);
            //En el buffer no permanece ningún evento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(1)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileOneTimestampPlusNoTimestampEventNegateRegexNoFlush", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testOneFileOneTimestampPlusTimestampEventNegateRegexNoFlush() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            List<Event> listEvents = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1);
            listEvents.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1));

            bufferTest.addAll(listEvents);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se elimina el primer evento
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), 1);
            //El map contiene 1 elemento pendiente de procesar (el correspondiente al segundo evento)
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 1);
            //Se ha procesado el primer evento
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 1);
            //En el buffer permanecen el segundo evento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 1);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileOneTimestampPlusTimestampEventNegateRegexNoFlush", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testOneFileOneTimestampPlusTimestampEventNegateRegexFlush() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            List<Event> listEvents = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1);
            listEvents.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1));

            bufferTest.addAll(listEvents);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Todos los eventos son eliminados
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene ningún elemento pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se ha envíado 2 eventos
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 2);
            //En el buffer no permanece ningún evento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileOneTimestampPlusTimestampEventNegateRegexNoFlush", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testOneFileOneTimestampPlusNoTimestampPlusTimestampEventNegateRegexNoFlush() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            List<Event> listEvents = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);
            listEvents.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1));

            bufferTest.addAll(listEvents);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se eliminan todos los eventos menos el ultimo
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize - 1);
            //El map contiene 1 elemento pendiente de procesar (el correspondiente al ultimo evento)
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 1);
            //Se ha procesado 1 evento compuesto por el evento timestamp y los consecutivos no timestamp
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 1);
            //En el buffer permanecen el ultimo evento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 1);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileOneTimestampPlusNoTimestampPlusTimestampEventNegateRegexNoFlush", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testOneFileOneTimestampPlusNoTimestampPlusTimestampEventNegateRegexFlush() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            List<Event> listEvents = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);
            listEvents.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1));

            bufferTest.addAll(listEvents);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se eliminan todos los eventos
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene ningún elemento pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se ha envíado 2 eventos
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 2);
            //En el buffer no permanece ningún evento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileOneTimestampPlusNoTimestampPlusTimestampEventNegateRegexFlush", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testOneFileNoTimestampPlusOneTimestampEventNegateRegexNoFlush() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            List<Event> listEvents = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);
            //Eliminamos el evento timestamp de la lista
            listEvents.remove(0);
            //Add el evento timestamp
            listEvents.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1));

            bufferTest.addAll(listEvents);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se eliminan todos los eventos menos el ultimo
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize - 1);
            //El map contiene 1 elemento pendiente de procesar (el correspondiente al ultimo evento)
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 1);
            //Se ha procesado 1 evento compuesto por el evento timestamp y los consecutivos no timestamp
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 1);
            //En el buffer permanecen el ultimo evento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 1);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(1)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testOneFileNoTimestampPlusOneTimestampEventNegateRegexNoFlush", e);
            Assert.fail();
        }
    }




    /*******************************************
     **** MULTIPLE FILES NEGATE REGEX TESTS ****
     *******************************************/

    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesOneTimestampEventNoFlush() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            List<Event> listEvents = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1);

            //Creamos otro evento timestamp asignado al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;

            //Add el evento timestamp
            listEvents.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1));

            bufferTest.addAll(listEvents);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //No se ha eliminado ningun evento (todos quedan pendientes de proceso
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), 0);
            //El map contiene 2 elementos pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 2);
            //No se ha enviado ningún evento (quedan pendientes de proceso)
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 0);
            //En el buffer permanecen todos los eventos
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), originalBufferTestSize);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesOneTimestampEventNoFlush", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesOneTimestampEventFlush() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            List<Event> listEvents = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1);

            //Creamos otro evento timestamp asignado al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;

            //Add el evento timestamp
            listEvents.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1));

            bufferTest.addAll(listEvents);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se eliminan todos los eventos
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene ningún elemento pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se ha envíado 2 eventos
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 2);
            //En el buffer no permanece ningún evento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesOneTimestampEventFlush", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesOneTimestampPlusNoTimestampEventNoFlush() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            List<Event> listEvents = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);

            //Creamos otros eventos asignados al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;

            //Add el evento timestamp + eventos no timestamp
            listEvents.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10));

            bufferTest.addAll(listEvents);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //No se ha eliminado ningun evento (todos quedan pendientes de proceso
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), 0);
            //El map contiene 2 elementos pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 2);
            //No se ha enviado ningún evento (quedan pendientes de proceso)
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 0);
            //En el buffer permanecen todos los eventos
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), originalBufferTestSize);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesOneTimestampPlusNoTimestampEventNoFlush", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesOneTimestampPlusNoTimestampEventFlush() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT;

            //Creamos el buffer del test
            List<Event> listEvents = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);

            //Creamos otros eventos asignados al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;

            //Add el evento timestamp + eventos no timestamp
            listEvents.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10));

            bufferTest.addAll(listEvents);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se eliminan todos los eventos
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene ningún elemento pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se ha envíado 2 eventos
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 2);
            //En el buffer no permanece ningún evento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesOneTimestampPlusNoTimestampEventFlush", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesOneTimestampPlusNoTimestampEventNoFlushRiffling() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsFirstFile = new ArrayList<Event>();
            List<Event> listEventsSecondFile = new ArrayList<Event>();
            List<Event> listEventsRiffling = null;

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT + " File01";

            //Creamos el buffer del test
            listEventsFirstFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);

            //Creamos otros eventos asignados al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            message = MESSAGE_SIMPLE_EVENT + " File02";

            //Add el evento timestamp + eventos no timestamp
            listEventsSecondFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);

            //Realizamos el riffling de los eventos para garantizar que van mezclados los eventos de ambos ficheros
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsFirstFile, listEventsSecondFile);
            bufferTest.addAll(listEventsRiffling);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //No se ha eliminado ningun evento (todos quedan pendientes de proceso
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), 0);
            //El map contiene 2 elementos pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 2);
            //No se ha enviado ningún evento (quedan pendientes de proceso)
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 0);
            //En el buffer permanecen todos los eventos
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), originalBufferTestSize);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesOneTimestampPlusNoTimestampEventNoFlushRiffling", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesOneTimestampPlusNoTimestampEventFlushRiffling() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsFirstFile = new ArrayList<Event>();
            List<Event> listEventsSecondFile = new ArrayList<Event>();
            List<Event> listEventsRiffling = null;

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT + " File01";

            //Creamos el buffer del test
            listEventsFirstFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);

            //Creamos otros eventos asignados al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            message = MESSAGE_SIMPLE_EVENT + " File02";

            //Add el evento timestamp + eventos no timestamp
            listEventsSecondFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);

            //Realizamos el riffling de los eventos para garantizar que van mezclados los eventos de ambos ficheros
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsFirstFile, listEventsSecondFile);
            bufferTest.addAll(listEventsRiffling);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se eliminan todos los eventos
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene ningún elemento pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se ha envíado 2 eventos
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 2);
            //En el buffer no permanece ningún evento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesOneTimestampPlusNoTimestampEventFlushRiffling", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileOneTimestampPlusNoTimestampSecondFileOneTimestampEventNoFlush() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT + " File01";

            //Creamos el buffer del test
            List<Event> listEvents = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);

            //Creamos otros eventos asignados al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            message = MESSAGE_SIMPLE_EVENT + " File02";

            //Add el evento timestamp
            listEvents.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1));

            bufferTest.addAll(listEvents);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //No se ha eliminado ningun evento (todos quedan pendientes de proceso
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), 0);
            //El map contiene 2 elementos pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 2);
            //No se ha enviado ningún evento (quedan pendientes de proceso)
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 0);
            //En el buffer permanecen todos los eventos
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), originalBufferTestSize);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileOneTimestampPlusNoTimestampSecondFileOneTimestampEventNoFlush", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileOneTimestampPlusNoTimestampSecondFileOneTimestampEventFlush() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT + " File01";

            //Creamos el buffer del test
            List<Event> listEvents = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);

            //Creamos otros eventos asignados al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            message = MESSAGE_SIMPLE_EVENT + " File02";

            //Add el evento timestamp
            listEvents.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1));

            bufferTest.addAll(listEvents);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se eliminan todos los eventos
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene ningún elemento pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se ha envíado 2 eventos
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 2);
            //En el buffer no permanece ningún evento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileOneTimestampPlusNoTimestampSecondFileOneTimestampEventFlush", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileOneTimestampPlusNoTimestampSecondFileOneTimestampEventNoFlushRiffling() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsFirstFile = new ArrayList<Event>();
            List<Event> listEventsSecondFile = new ArrayList<Event>();
            List<Event> listEventsRiffling = null;

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT + " File01";

            //Creamos el buffer del test
            listEventsFirstFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);

            //Creamos otros eventos asignados al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            message = MESSAGE_SIMPLE_EVENT + " File02";

            //Add el evento timestamp
            listEventsSecondFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1);

            //Realizamos el riffling de los eventos para garantizar que van mezclados los eventos de ambos ficheros
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsFirstFile, listEventsSecondFile);
            bufferTest.addAll(listEventsRiffling);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //No se ha eliminado ningun evento (todos quedan pendientes de proceso
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), 0);
            //El map contiene 2 elementos pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 2);
            //No se ha enviado ningún evento (quedan pendientes de proceso)
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 0);
            //En el buffer permanecen todos los eventos
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), originalBufferTestSize);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileOneTimestampPlusNoTimestampSecondFileOneTimestampEventNoFlushRiffling", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileOneTimestampPlusNoTimestampSecondFileOneTimestampEventFlushRiffling() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsFirstFile = new ArrayList<Event>();
            List<Event> listEventsSecondFile = new ArrayList<Event>();
            List<Event> listEventsRiffling = null;

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT + " File01";

            //Creamos el buffer del test
            listEventsFirstFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);

            //Creamos otros eventos asignados al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            message = MESSAGE_SIMPLE_EVENT + " File02";

            //Add el evento timestamp
            listEventsSecondFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1);

            //Realizamos el riffling de los eventos para garantizar que van mezclados los eventos de ambos ficheros
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsFirstFile, listEventsSecondFile);
            bufferTest.addAll(listEventsRiffling);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se eliminan todos los eventos
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene ningún elemento pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se ha envíado 2 eventos
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 2);
            //En el buffer no permanece ningún evento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(2)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileOneTimestampPlusNoTimestampSecondFileOneTimestampEventFlushRiffling", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileOneTimestampPlusNoTimestampPlusTimestampSecondFileOneTimestampEventNoFlush() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT + " File01";

            //Creamos el buffer del test
            List<Event> listEvents = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);

            //Add evento timestamp
            listEvents.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1));

            //Creamos otros eventos asignados al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            message = MESSAGE_SIMPLE_EVENT + " File02";

            //Add el evento timestamp
            listEvents.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1));

            bufferTest.addAll(listEvents);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se han eliminado todos los elementos menos los 2 últimos eventos timestamp de cada uno de los ficheros
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize - 2);
            //El map contiene 2 elementos pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 2);
            //Se ha enviado 1 evento (File01) compuesto por los eventos encontrados hasta la llegada del segundo evento Timestamp
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 1);
            //En el buffer permanecen los 2 últimos eventos timestamp llegados
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 2);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(3)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileOneTimestampPlusNoTimestampPlusTimestampSecondFileOneTimestampEventNoFlush", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileOneTimestampPlusNoTimestampPlusTimestampSecondFileOneTimestampEventFlush() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT + " File01";

            //Creamos el buffer del test
            List<Event> listEvents = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);

            //Add evento timestamp
            listEvents.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1));

            //Creamos otros eventos asignados al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            message = MESSAGE_SIMPLE_EVENT + " File02";

            //Add el evento timestamp
            listEvents.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1));

            bufferTest.addAll(listEvents);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se eliminan todos los eventos
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene ningún elemento pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se ha envíado 3 eventos (el compuesto por el evento Timestamp + eventos no Timestamp de file01, el segundo evento Timestamp de File01 y el evento Timestamp de File02
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 3);
            //En el buffer no permanece ningún evento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(3)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileOneTimestampPlusNoTimestampPlusTimestampSecondFileOneTimestampEventFlush", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileOneTimestampPlusNoTimestampPlusTimestampSecondFileOneTimestampEventNoFlushRiffling() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsFirstFile = new ArrayList<Event>();
            List<Event> listEventsSecondFile = new ArrayList<Event>();
            List<Event> listEventsRiffling = null;

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT + " File01";

            //Creamos el buffer del test
            listEventsFirstFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);

            //Add evento timestamp
            listEventsFirstFile.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1));

            //Creamos otros eventos asignados al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            message = MESSAGE_SIMPLE_EVENT + " File02";

            //Add el evento timestamp
            listEventsSecondFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1);

            //Realizamos el riffling de los eventos para garantizar que van mezclados los eventos de ambos ficheros
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsFirstFile, listEventsSecondFile);
            bufferTest.addAll(listEventsRiffling);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se han eliminado todos los elementos menos los 2 últimos eventos timestamp de cada uno de los ficheros
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize - 2);
            //El map contiene 2 elementos pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 2);
            //Se ha enviado 1 evento (File01) compuesto por los eventos encontrados hasta la llegada del segundo evento Timestamp
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 1);
            //En el buffer permanecen los 2 últimos eventos timestamp llegados
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 2);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(3)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileOneTimestampPlusNoTimestampPlusTimestampSecondFileOneTimestampEventNoFlushRiffling", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileOneTimestampPlusNoTimestampPlusTimestampSecondFileOneTimestampEventFlushRiffling() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsFirstFile = new ArrayList<Event>();
            List<Event> listEventsSecondFile = new ArrayList<Event>();
            List<Event> listEventsRiffling = null;

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT + " File01";

            //Creamos el buffer del test
            listEventsFirstFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);

            //Add evento timestamp
            listEventsFirstFile.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1));

            //Creamos otros eventos asignados al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            message = MESSAGE_SIMPLE_EVENT + " File02";

            //Add el evento timestamp
            listEventsSecondFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1);

            //Realizamos el riffling de los eventos para garantizar que van mezclados los eventos de ambos ficheros
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsFirstFile, listEventsSecondFile);
            bufferTest.addAll(listEventsRiffling);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se eliminan todos los eventos
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene ningún elemento pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se ha envíado 3 eventos (el compuesto por el evento Timestamp + eventos no Timestamp de file01, el segundo evento Timestamp de File01 y el evento Timestamp de File02
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 3);
            //En el buffer no permanece ningún evento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(3)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileOneTimestampPlusNoTimestampPlusTimestampSecondFileOneTimestampEventFlushRiffling", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileOneTimestampPlusNoTimestampPlusTimestampSecondFileOneTimestampPlusNoTimestampEventNoFlush() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT + " File01";

            //Creamos el buffer del test
            List<Event> listEvents = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);

            //Add evento timestamp
            listEvents.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1));

            //Creamos otros eventos asignados al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            message = MESSAGE_SIMPLE_EVENT + " File02";

            //Add el evento timestamp
            listEvents.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10));

            bufferTest.addAll(listEvents);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se han eliminado los elementos correspondientes al primer timestamp + los sucesivos no timestamp de File01
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), 10);
            //El map contiene 2 elementos pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 2);
            //Se ha enviado 1 evento (File01) compuesto por los eventos encontrados hasta la llegada del segundo evento Timestamp
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 1);
            //En el buffer permanecen el últimos eventos timestamp de File01 + los eventos de File02
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 11);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(3)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileOneTimestampPlusNoTimestampPlusTimestampSecondFileOneTimestampPlusNoTimestampEventNoFlush", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileOneTimestampPlusNoTimestampPlusTimestampSecondFileOneTimestampPlusNoTimestampEventFlush() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT + " File01";

            //Creamos el buffer del test
            List<Event> listEvents = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);

            //Add evento timestamp
            listEvents.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1));

            //Creamos otros eventos asignados al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            message = MESSAGE_SIMPLE_EVENT + " File02";

            //Add el evento timestamp
            listEvents.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10));

            bufferTest.addAll(listEvents);

            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se eliminan todos los eventos
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene ningún elemento pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se ha envíado 3 eventos (el compuesto por el evento Timestamp + eventos no Timestamp de file01, el segundo evento Timestamp de File01
            //y el evento compuesto por el evento Timestamp + eventos no timestamp de File02
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 3);
            //En el buffer no permanece ningún evento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(3)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileOneTimestampPlusNoTimestampPlusTimestampSecondFileOneTimestampPlusNoTimestampEventFlush", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileOneTimestampPlusNoTimestampPlusTimestampSecondFileOneTimestampPlusNoTimestampEventNoFlushRiffling() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsFirstFile = new ArrayList<Event>();
            List<Event> listEventsSecondFile = new ArrayList<Event>();
            List<Event> listEventsRiffling = null;

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT + " File01";

            //Creamos el buffer del test
            listEventsFirstFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);

            //Add evento timestamp
            listEventsFirstFile.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1));

            //Creamos otros eventos asignados al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            message = MESSAGE_SIMPLE_EVENT + " File02";

            //Add el evento timestamp
            listEventsSecondFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 4);

            int firstFileEventsSize = listEventsFirstFile.size();
            int secondFileEventsSize = listEventsSecondFile.size();

            //Realizamos el riffling de los eventos para garantizar que van mezclados los eventos de ambos ficheros
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsFirstFile, listEventsSecondFile);
            bufferTest.addAll(listEventsRiffling);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se han eliminado los elementos correspondientes al primer timestamp + los sucesivos no timestamp de File01
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), firstFileEventsSize - 1);
            //El map contiene 2 elementos pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 2);
            //Se ha enviado 1 evento (File01) compuesto por los eventos encontrados hasta la llegada del segundo evento Timestamp
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 1);
            //En el buffer permanecen el últimos eventos timestamp de File01 + los eventos de File02
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), secondFileEventsSize + 1);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(3)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileOneTimestampPlusNoTimestampPlusTimestampSecondFileOneTimestampPlusNoTimestampEventNoFlushRiffling", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileOneTimestampPlusNoTimestampPlusTimestampSecondFileOneTimestampPlusNoTimestampEventFlushRiffling() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsFirstFile = new ArrayList<Event>();
            List<Event> listEventsSecondFile = new ArrayList<Event>();
            List<Event> listEventsRiffling = null;

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT + " File01";

            //Creamos el buffer del test
            listEventsFirstFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);

            //Add evento timestamp
            listEventsFirstFile.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1));

            //Creamos otros eventos asignados al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            message = MESSAGE_SIMPLE_EVENT + " File02";

            //Add el evento timestamp
            listEventsSecondFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);

            int firstFileEventsSize = listEventsFirstFile.size();
            int secondFileEventsSize = listEventsSecondFile.size();

            //Realizamos el riffling de los eventos para garantizar que van mezclados los eventos de ambos ficheros
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsFirstFile, listEventsSecondFile);
            bufferTest.addAll(listEventsRiffling);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se eliminan todos los eventos
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene ningún elemento pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se ha envíado 3 eventos (el compuesto por el evento Timestamp + eventos no Timestamp de file01, el segundo evento Timestamp de File01
            //y el evento compuesto por el evento Timestamp + eventos no timestamp de File02
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 3);
            //En el buffer no permanece ningún evento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);


            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(3)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileOneTimestampPlusNoTimestampPlusTimestampSecondFileOneTimestampPlusNoTimestampEventFlushRiffling", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileTNTSecondFileTNThirdFileTEventNoFlushRiffling() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsFirstFile = new ArrayList<Event>();
            List<Event> listEventsSecondFile = new ArrayList<Event>();
            List<Event> listEventsThirdFile = new ArrayList<Event>();
            List<Event> listEventsRiffling = null;

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT + " File01";

            //Creamos el buffer del test
            listEventsFirstFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);

            //Add evento timestamp
            listEventsFirstFile.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1));

            //Creamos otros eventos asignados al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            message = MESSAGE_SIMPLE_EVENT + " File02";

            //Add el evento timestamp + n eventos no timestamp
            listEventsSecondFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 4);

            //Creamos otros eventos asignados al fichero "File03". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE03;
            message = MESSAGE_SIMPLE_EVENT + " File03";

            //Add el evento timestamp
            listEventsThirdFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1);


            int firstFileEventsSize = listEventsFirstFile.size();
            int secondFileEventsSize = listEventsSecondFile.size();
            int thirdFileEventsSize = listEventsThirdFile.size();

            //Realizamos el riffling de los eventos para garantizar que van mezclados los eventos de ambos ficheros
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsFirstFile, listEventsSecondFile);
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsRiffling, listEventsThirdFile);
            bufferTest.addAll(listEventsRiffling);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se han eliminado los elementos correspondientes al primer timestamp + los sucesivos no timestamp de File01
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), firstFileEventsSize - 1);
            //El map contiene 3 elementos pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 3);
            //Se ha enviado 1 evento (File01) compuesto por los eventos encontrados hasta la llegada del segundo evento Timestamp
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 1);
            //En el buffer permanecen el último evento timestamp de File01 + los eventos de File02 + el evento timestamp de File03
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), secondFileEventsSize + 2);

            //El metodo privado processAllpendingEvents NO se ha ejecutado (No Flush)
            verifyPrivate(spyHelper, times(0)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(4)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileTNTSecondFileTNThirdFileTEventNoFlushRiffling", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testMultipleFilesFirstFileTNTSecondFileTNThirdFileTEventFlushRiffling() {

        try {

            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsFirstFile = new ArrayList<Event>();
            List<Event> listEventsSecondFile = new ArrayList<Event>();
            List<Event> listEventsThirdFile = new ArrayList<Event>();
            List<Event> listEventsRiffling = null;

            //Creamos un evento simple asignado al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;
            String message = MESSAGE_SIMPLE_EVENT + " File01";

            //Creamos el buffer del test
            listEventsFirstFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 10);

            //Add evento timestamp
            listEventsFirstFile.addAll(createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1));

            //Creamos otros eventos asignados al fichero "File02". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE02;
            message = MESSAGE_SIMPLE_EVENT + " File02";

            //Add el evento timestamp + n eventos no timestamp
            listEventsSecondFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 4);

            //Creamos otros eventos asignados al fichero "File03". Con fileHeader real
            fileHeaderValue = FILE_HEADER_VALUE_FILE03;
            message = MESSAGE_SIMPLE_EVENT + " File03";

            //Add el evento timestamp
            listEventsThirdFile = createListEventsFirstTimestamp(fileHeader, fileHeaderName, fileHeaderValue, message, 1);


            int firstFileEventsSize = listEventsFirstFile.size();
            int secondFileEventsSize = listEventsSecondFile.size();
            int thirdFileEventsSize = listEventsThirdFile.size();

            //Realizamos el riffling de los eventos para garantizar que van mezclados los eventos de ambos ficheros
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsFirstFile, listEventsSecondFile);
            listEventsRiffling = TestUtils.riffleShuffleLists(listEventsRiffling, listEventsThirdFile);
            bufferTest.addAll(listEventsRiffling);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_TIMESTAMP_REGEX, null, true, true, true, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            TestUtils.reflectExecuteMethod(spyHelper, "processEventBatch", objectNull, classNull);

            //Recogemos los valores de los elementos creados / modificados por el metodo
            List<Integer> listIndexToRemoveAfterProcess = (List<Integer>) TestUtils.reflectValue(spyHelper, "listIndexToRemove");
            Map<String, TreeMap<Integer, Event>> mapPendingEventsAfterProcess = (Map<String, TreeMap<Integer, Event>>) TestUtils.reflectValue(spyHelper, "mapPendingEvents");
            List<Event> listEventToProcessAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "listEventToProcess");
            List<Event> bufferAfterProcess = (List<Event>) TestUtils.reflectValue(spyHelper, "buffer");

            //Se eliminan todos los eventos
            Assert.assertEquals("The value of listIndexToRemove is not correct", listIndexToRemoveAfterProcess.size(), originalBufferTestSize);
            //El map NO contiene ningún elemento pendiente de procesar
            Assert.assertEquals("The value of mapPendingEventsAfterProcess is not correct", mapPendingEventsAfterProcess.size(), 0);
            //Se ha envíado 4 eventos (el compuesto por el evento Timestamp + eventos no Timestamp de file01, el segundo evento Timestamp de File01
            //, el evento compuesto por el evento Timestamp + eventos no timestamp de File02 + evento Timestamp de File03
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", listEventToProcessAfterProcess.size(), 4);
            //En el buffer no permanece ningún evento
            Assert.assertEquals("The value of bufferAfterProcess is not correct", bufferAfterProcess.size(), 0);


            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez (Flush)
            verifyPrivate(spyHelper, times(1)).invoke("processAllPendingEvents");
            //Comprobamos que el metodo processPendingEventsFile se ha ejecutado un número correcto de veces
            verifyPrivate(spyHelper, times(4)).invoke("processPendingEventsFile", any());
            //Comprobamos que el metodo addEventPendingProcess es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("addEventPendingProcess", any(), anyInt());
            //Comprobamos que el metodo isSimpleLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(originalBufferTestSize)).invoke("isSimpleLineEvent", any());
            //Comprobamos que el metodo isMultilineFirstLineEvent es invocado el numero correcto de veces
            verifyPrivate(spyHelper, times(0)).invoke("isMultilineFirstLineEvent", any());

        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testMultipleFilesFirstFileTNTSecondFileTNThirdFileTEventFlushRiffling", e);
            Assert.fail();
        }
    }


    @Test
    @SuppressWarnings("all")
    public void testCommitPendingsMultilineActiveComplete() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsExceptionFirstFileFirstException = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;

            try {
                int a = 1 / 0;
            } catch (Exception e) {
                listEventsExceptionFirstFileFirstException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsExceptionFirstFileFirstException);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            spyHelper.commitPendings();

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez
            verifyPrivate(spyHelper, times(1)).invoke("processEventBatch");

            //Comprobamos NO que se ha invocado el clear del buffer
            Assert.assertTrue("The value of listEventToProcessAfterProcess is not correct", spyHelper.getBuffer().size() > 0);


        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testCommitPendingsMultilineActiveComplete", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testCommitPendingsMultilineNotActiveComplete() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsExceptionFirstFileFirstException = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;

            try {
                int a = 1 / 0;
            } catch (Exception e) {
                listEventsExceptionFirstFileFirstException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }

            bufferTest.addAll(listEventsExceptionFirstFileFirstException);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(false, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, bufferTest);

            //Invocamos al metodo
            spyHelper.commitPendings();

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez
            verifyPrivate(spyHelper, times(0)).invoke("processEventBatch");

            //Comprobamos que se ha invocado el clear del buffer
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", spyHelper.getBuffer().size(), 0);


        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testCommitPendingsMultilineNotActiveComplete", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testCommitPendingsExceptionThrown() {

        try {
            Vector<Event> bufferTest = new Vector<Event>();
            List<Event> listEventsExceptionFirstFileFirstException = null;

            //Creamos una lista de eventos a partir del stack trace de una excepción. Asignamos estos eventos al fichero "File01". Con fileHeader real
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;

            try {
                int a = 1 / 0;
            } catch (Exception e) {
                listEventsExceptionFirstFileFirstException = createListEventsFromThrowable(e, fileHeader, fileHeaderName, fileHeaderValue);
            }


            bufferTest.addAll(listEventsExceptionFirstFileFirstException);
            int originalBufferTestSize = bufferTest.size();

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, bufferTest);

            //Garantizamos un error al invocar el metodo processEventBatch
            Error error = new Error("error inesperado");
            doThrow(error).when(spyHelper, "processEventBatch");

            //Invocamos al metodo
            spyHelper.commitPendings();

            //Comprobamos que se ha invocado el clear del buffer
            Assert.assertEquals("The value of listEventToProcessAfterProcess is not correct", spyHelper.getBuffer().size(), 0);


        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testCommitPendingsExceptionThrown", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testCreateEventHeadersMultilineActiveFileHeader() {

        try {
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, null);

            //Invocamos al metodo
            Class<?>[] argsCreateEventHeaders = new Class[1];
            argsCreateEventHeaders[0] = String.class;

            Map<String,String> headers = (Map<String,String>) TestUtils.reflectExecuteMethod(spyHelper, "createEventHeaders", argsCreateEventHeaders, fileHeaderValue);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez
            verifyPrivate(spyHelper, times(1)).invoke("createEventHeaders", anyString());

            //Comprobamos que la cabecera fileHeaderName ha sido creada
            Assert.assertNotNull("The value of headers is not correct", headers.get(fileHeaderName));

            //Comprobamos que la cabecera fileHeaderName tiene el valor correcto
            Assert.assertEquals("The value of headers is not correct", headers.get(fileHeaderName), fileHeaderValue);

            //Comprobamos que la cabecera fake no ha sido creada
            Assert.assertNull("The value of headers is not correct", headers.get(FILE_HEADER_NAME_FAKE));



        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testCreateEventHeadersMultilineActiveFileHeader", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testCreateEventHeadersMultilineActiveWithoutFileHeader() {

        try {
            boolean fileHeader = false;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;

            //Preparamos los datos para invocar el metodo
            prepareTestData(true, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, null, null);

            //Invocamos al metodo
            Class<?>[] argsCreateEventHeaders = new Class[1];
            argsCreateEventHeaders[0] = String.class;

            Map<String,String> headers = (Map<String,String>) TestUtils.reflectExecuteMethod(spyHelper, "createEventHeaders", argsCreateEventHeaders, fileHeaderValue);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez
            verifyPrivate(spyHelper, times(1)).invoke("createEventHeaders", anyString());

            //Comprobamos que la cabecera fake ha sido creada
            Assert.assertNotNull("The value of headers is not correct", headers.get(FILE_HEADER_NAME_FAKE));

            //Comprobamos que la cabecera fake tiene el valor correcto
            Assert.assertEquals("The value of headers is not correct", headers.get(FILE_HEADER_NAME_FAKE), fileHeaderValue);


        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testCreateEventHeadersMultilineActiveWithoutFileHeader", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testCreateEventHeadersMultilineNotActiveFileHeader() {

        try {
            boolean fileHeader = true;
            String fileHeaderName = FILE_HEADER_NAME;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;

            //Preparamos los datos para invocar el metodo
            prepareTestData(false, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, fileHeaderName, null);

            //Invocamos al metodo
            Class<?>[] argsCreateEventHeaders = new Class[1];
            argsCreateEventHeaders[0] = String.class;

            Map<String,String> headers = (Map<String,String>) TestUtils.reflectExecuteMethod(spyHelper, "createEventHeaders", argsCreateEventHeaders, fileHeaderValue);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez
            verifyPrivate(spyHelper, times(1)).invoke("createEventHeaders", anyString());

            //Comprobamos que la cabecera fileHeaderName ha sido creada
            Assert.assertNotNull("The value of headers is not correct", headers.get(fileHeaderName));

            //Comprobamos que la cabecera fileHeaderName tiene el valor correcto
            Assert.assertEquals("The value of headers is not correct", headers.get(fileHeaderName), fileHeaderValue);

            //Comprobamos que la cabecera fake no ha sido creada
            Assert.assertNull("The value of headers is not correct", headers.get(FILE_HEADER_NAME_FAKE));



        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testCreateEventHeadersMultilineNotActiveFileHeader", e);
            Assert.fail();
        }

    }


    @Test
    @SuppressWarnings("all")
    public void testCreateEventHeadersMultilineNotActiveWithoutFileHeader() {

        try {
            boolean fileHeader = false;
            String fileHeaderValue = FILE_HEADER_VALUE_FILE01;

            //Preparamos los datos para invocar el metodo
            prepareTestData(false, MULTILINE_REGEX, MULTILINE_FIRST_LINE_REGEX, false, true, false, fileHeader, null, null);

            //Invocamos al metodo
            Class<?>[] argsCreateEventHeaders = new Class[1];
            argsCreateEventHeaders[0] = String.class;

            Map<String,String> headers = (Map<String,String>) TestUtils.reflectExecuteMethod(spyHelper, "createEventHeaders", argsCreateEventHeaders, fileHeaderValue);

            //El metodo privado processAllpendingEvents se ha ejecutado 1 vez
            verifyPrivate(spyHelper, times(1)).invoke("createEventHeaders", anyString());

            //Comprobamos que la cabecera fake NO ha sido creada
            Assert.assertNull("The value of headers is not correct", headers.get(FILE_HEADER_NAME_FAKE));


        } catch (Exception e) {
            LOGGER.error("Ha ocurrido un error en el test testCreateEventHeadersMultilineNotActiveWithoutFileHeader", e);
            Assert.fail();
        }

    }


}
