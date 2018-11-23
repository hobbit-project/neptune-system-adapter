package org.hobbit.sparql_snb.systems;

import org.aksw.jena_sparql_api.core.UpdateExecutionFactory;
import org.aksw.jena_sparql_api.core.UpdateExecutionFactoryHttp;
import org.aksw.jena_sparql_api.core.utils.UpdateRequestUtils;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.apache.jena.atlas.web.auth.HttpAuthenticator;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.update.UpdateRequest;
import org.hobbit.core.Constants;
import org.hobbit.core.components.AbstractSystemAdapter;
import org.hobbit.core.rabbit.RabbitMQUtils;

import org.hobbit.sdk.JenaKeyValue;
import org.hobbit.utils.EnvVariables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hobbit.sparql_snb.Constants.*;


/**
 * @author Pavel Smirnov. (psmirnov@agtinternational.com / smirnp@gmail.com)
 */
public abstract class TripleStoreSystemAdapter extends AbstractSystemAdapter {

    protected Logger LOGGER = LoggerFactory.getLogger(TripleStoreSystemAdapter.class);
    protected boolean dataLoadingFinished = false;
    protected AtomicInteger totalReceived = new AtomicInteger(0);
    protected AtomicInteger totalSent = new AtomicInteger(0);
    protected Semaphore allDataReceivedMutex = new Semaphore(0);
    protected String datasetFolderName;
    protected QueryExecutionFactoryHttp queryExecFactory;
    protected UpdateExecutionFactory updateExecFactory;

    protected int loadingNumber=0;
    private JenaKeyValue parameters;
    protected HttpAuthenticator auth;
    protected String updateEndpoint;
    protected String queryEndpoint;


    protected String sharedFolderPath;
    protected String tripleStoreContainerId;

    @Override
    public void init() throws Exception {
        LOGGER.info("Initialization begins.");
        super.init();

        parameters = new JenaKeyValue.Builder().buildFrom(systemParamModel);
        datasetFolderName = "datasets";

        String sessionId =  EnvVariables.getString(Constants.HOBBIT_SESSION_ID_KEY, Constants.HOBBIT_SESSION_ID_FOR_PLATFORM_COMPONENTS);

        if(new File("/.dockerenv").exists())
            sharedFolderPath =  "/share";
        else
            sharedFolderPath =  "/tmp/"+sessionId;

        try {
            Files.createDirectories(Paths.get(datasetFolderName));
        } catch (IOException e) {
            LOGGER.error("Failed to create folder: {}", e.getLocalizedMessage());
        }
        //File theDir = new File(datasetFolderName);
        //theDir.mkdir();

        LOGGER.info("Executing local init");

        initStage1();

        if(queryEndpoint==null){
            throw new Exception("queryEndpoint is not initalized");
        }else
            LOGGER.info("Query endpoint: {}", queryEndpoint);

        if(updateEndpoint==null){
            throw new Exception("updateEndpoint is not initalized");
        }else
            LOGGER.info("Update endpoint: {}", updateEndpoint);

        if(auth==null){
            LOGGER.warn("HttpAuthenticator for updateFactory not inialized. Auth would be skipped");
        }


        queryExecFactory = new QueryExecutionFactoryHttp(queryEndpoint);
        // create update factory

        updateExecFactory = (auth!=null ? new UpdateExecutionFactoryHttp(updateEndpoint, auth) : new UpdateExecutionFactoryHttp(updateEndpoint));
        LOGGER.info("Initialization is over.");

        postInit();
    }

    protected abstract void initStage1() throws Exception;
    protected abstract void postInit() throws Exception;


    @Override
    public void receiveGeneratedData(byte[] arg0) {

        if (dataLoadingFinished == false) {
            ByteBuffer dataBuffer = ByteBuffer.wrap(arg0);
            String fileName = RabbitMQUtils.readString(dataBuffer);

            LOGGER.info("Receiving file: " + fileName);

            //graphUris.add(fileName);

            byte [] content = new byte[dataBuffer.remaining()];
            dataBuffer.get(content, 0, dataBuffer.remaining());
            String destFileName = datasetFolderName + File.separator + fileName;
            if (content.length != 0) {

                FileOutputStream fos;
                try {
                    if (fileName.contains("/"))
                        fileName = fileName.replaceAll("[^/]*[/]", "");

                    fos = new FileOutputStream(destFileName);
                    fos.write(content);
                    fos.close();


                } catch (FileNotFoundException e) {
                    // TODO Auto-generated catch block
                    LOGGER.error("File not found: {}", e.getLocalizedMessage());
                    //e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    LOGGER.error("IO error: {}", e.getLocalizedMessage());
                    //e.printStackTrace();
                }
            }

            if(totalReceived.incrementAndGet() == totalSent.get()) {
                allDataReceivedMutex.release();
            }
        }
        else {
            ByteBuffer buffer = ByteBuffer.wrap(arg0);
            String insertQuery = RabbitMQUtils.readString(buffer);

            UpdateRequest updateRequest = UpdateRequestUtils.parse(insertQuery);
            try {
                updateExecFactory.createUpdateProcessor(updateRequest).execute();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void receiveGeneratedTask(String taskId, byte[] data) {

        ByteBuffer buffer = ByteBuffer.wrap(data);
        String queryString = RabbitMQUtils.readString(buffer);
        long timestamp1 = System.currentTimeMillis();
        //LOGGER.info(taskId);
        if (queryString.contains("INSERT DATA")) {
            //TODO: Virtuoso hack
            queryString = queryString.replaceFirst("INSERT DATA", "INSERT");
            queryString += "WHERE { }\n";


            //updateExecFactory = new UpdateExecutionFactoryHttp("http://"+ sparqlHostAndPort +"/sparql");
            //updateExecFactory = new UpdateExecutionFactoryHttp("http://"+ sparqlHostAndPort +"/sparql-auth", auth);
            UpdateRequest updateRequest = UpdateRequestUtils.parse(queryString);
            try {
                updateExecFactory.createUpdateProcessor(updateRequest).execute();
            } catch (Exception e) {
                LOGGER.error("Failed to execute update request: {}", e.getLocalizedMessage());
                e.printStackTrace();
            }

            try {
                this.sendResultToEvalStorage(taskId, RabbitMQUtils.writeString(""));
            } catch (IOException e) {
                LOGGER.error("Got an exception while sending results.", e);
            }
        }
        else {
            byte[] res = execQuery(queryString);

            try {
                this.sendResultToEvalStorage(taskId, res);
//				LOGGER.info(new String(outputStream.toByteArray()));
//				LOGGER.info("--------------------");
            } catch (IOException e) {
                LOGGER.error("Got an exception while sending results.", e);
            }
        }
        long timestamp2 = System.currentTimeMillis();
        //LOGGER.info("Task " + taskId + ": " + (timestamp2-timestamp1));
    }

    protected byte[] execQuery(String queryString){
        QueryExecution qe = queryExecFactory.createQueryExecution(queryString);
        ResultSet results = null;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            results = qe.execSelect();
            ResultSetFormatter.outputAsJSON(outputStream, results);
        } catch (Exception e) {
            LOGGER.error("Problem query execution : " + queryString+": "+e.getLocalizedMessage());
            //TODO: fix this hacking
            try {
                outputStream.write("{\"head\":{\"vars\":[\"xxx\"]},\"results\":{\"bindings\":[{\"xxx\":{\"type\":\"literal\",\"value\":\"XXX\"}}]}}".getBytes());
            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
            e.printStackTrace();
        } finally {
            qe.close();
        }
        return outputStream.toByteArray();
    }

    @Override
    public void receiveCommand(byte command, byte[] data){

        if (BULK_LOAD_DATA_GEN_FINISHED == command) {

            ByteBuffer buffer = ByteBuffer.wrap(data);
            int numberOfMessages = buffer.getInt();
            boolean lastBulkLoad = buffer.get() != 0;

            LOGGER.info("Bulk loading phase (" + loadingNumber + ") begins");

            // if all data have been received before BULK_LOAD_DATA_GEN_FINISHED command received
            // release before acquire, so it can immediately proceed to bulk loading
            if(totalReceived.get() == totalSent.addAndGet(numberOfMessages)) {
                allDataReceivedMutex.release();
            }

            LOGGER.info("Wait for receiving all data for bulk load " + loadingNumber + ".");
            try {
                allDataReceivedMutex.acquire();
            } catch (InterruptedException e) {
                LOGGER.error("Exception while waitting for all data for bulk load " + loadingNumber + " to be recieved.", e);
            }
            LOGGER.info("All data for bulk load " + loadingNumber + " received. Proceed to the loading...");
//
//			for (String uri : this.graphUris) {
//				String create = "CREATE GRAPH " + "<" + uri + ">";
//				LOGGER.info(create);
//				UpdateRequest updateRequest = UpdateRequestUtils.parse(create);
//				updateExecFactory.createUpdateProcessor(updateRequest).execute();
//			}
//			this.graphUris.clear();



            new Thread(new Runnable() {
                @Override
                public void run() {
                    String graphUri = "http://graph.version." + loadingNumber;
                    LOGGER.info("Bulk loading dataset for {}", graphUri);
                    try {
                        loadDataset(graphUri);
                        sendToCmdQueue(BULK_LOADING_DATA_FINISHED);
                    }
                    catch (Exception e){
                        LOGGER.error("Datasets were not loaded");
                        e.printStackTrace();
                        System.exit(1);
                    }

                    LOGGER.info("Bulk loading phase (" + loadingNumber + ") is over.");

                    loadingNumber++;

//            File theDir = new File(datasetFolderName);
//            for (File f : theDir.listFiles())
//                f.delete();

                    if (lastBulkLoad) {
                        dataLoadingFinished = true;
                        LOGGER.info("All bulk loading phases are over.");
                    }
                }
            }).start();
        }
        super.receiveCommand(command, data);

    }

    protected abstract void loadDataset(String graphURI) throws Exception;

    protected void copyDatasetsToSharedFolder(){

        Path datasetsAtSharedFolderPath = Paths.get(sharedFolderPath, "datasets");
        try {

            if(!Files.exists(Paths.get(sharedFolderPath)))
                Files.createDirectory(Paths.get(sharedFolderPath));

            if(!Files.exists(datasetsAtSharedFolderPath))
                Files.createDirectory(datasetsAtSharedFolderPath);

        } catch (IOException e) {
            LOGGER.error("Failed create datasets folder in a shared mount: {}", e.getLocalizedMessage());
            System.exit(1);

        }

        copyFiles(datasetFolderName, datasetsAtSharedFolderPath.toString());

    }

    protected void copyFiles(String sourceFolderPath, String destFolderPath){
        File theDir = new File(sourceFolderPath);
        for (File f : theDir.listFiles()){
            try {
                Files.copy(Paths.get(f.getAbsolutePath()), Paths.get(destFolderPath, f.getName()), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.error("Failed to copy {} to shared folder: {}", f.getName(), e.getLocalizedMessage());
                System.exit(1);
            }
        }
    }


    @Override
    public void close() throws IOException {
        try {
            queryExecFactory.close();
            updateExecFactory.close();
            super.close();
        } catch (Exception e) {
            LOGGER.error(e.getLocalizedMessage());
        }
//		try {
//			TimeUnit.SECONDS.sleep(10);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}

    }
}
