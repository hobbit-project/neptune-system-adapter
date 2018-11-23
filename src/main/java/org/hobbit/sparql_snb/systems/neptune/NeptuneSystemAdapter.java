package org.hobbit.sparql_snb.systems.neptune;

import com.amazonaws.services.neptune.model.DBClusterRoleAlreadyExistsException;
import com.google.common.io.CharStreams;
import com.google.gson.JsonObject;
import com.jcraft.jsch.ChannelExec;

import java.io.*;

import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.parser.JSONParser;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ErrorManager;
import jdk.nashorn.internal.runtime.options.Options;
import jdk.nashorn.internal.scripts.JO;
import org.aksw.jena_sparql_api.core.utils.UpdateRequestUtils;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.update.UpdateProcessor;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.RDF;
import org.hobbit.awscontroller.AWSController;
import org.hobbit.awscontroller.SSH.HSession;
import org.hobbit.awscontroller.SSH.SshConnector;
import org.hobbit.awscontroller.SSH.SshTunnelsProvider;
import org.hobbit.sdk.JenaKeyValue;
import org.hobbit.sparql_snb.systems.TripleStoreSystemAdapter;
import org.hobbit.vocab.HOBBIT;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

import static org.hobbit.sparql_snb.Constants.*;
import static org.hobbit.sparql_snb.systems.neptune.Constants.*;
import static org.hobbit.sparql_snb.systems.neptune.Constants.SSH_KEY_NAME;
import static org.hobbit.sparql_snb.systems.neptune.Constants.SSH_KEY_PATH;

/**
 * @author Pavel Smirnov. (psmirnov@agtinternational.com / smirnp@gmail.com)
 */
public class NeptuneSystemAdapter extends TripleStoreSystemAdapter {
    private static final String[] paramKeys = new String[]{ "AWS_ACCESS_KEY_ID" , "AWS_SECRET_KEY", "AWS_ROLE_ARN", "AWS_REGION" };

    NeptuneClusterManager neptuneClusterManager;
    Semaphore waitingMutex = new Semaphore(0);

    JenaKeyValue parameters;

    AWSController awsController;
    SshConnector sshConnector;

    String neptuneClusterIp;
    CloseableHttpClient httpclient;
    Context context;
    private String sparqlHostAndPort;
    String instanceType;
    int attemptsLimit=12;

    //JSONParser jsonParser;

    @Override
    public void init() throws Exception {
        LOGGER = LoggerFactory.getLogger(NeptuneSystemAdapter.class);
//        parameters = new HashMap<>();
        super.init();
    }

    @Override
    protected void initStage1() throws Exception{


        parameters = new JenaKeyValue.Builder().buildFrom(systemParamModel);
        httpclient = HttpClients.createDefault();

        //Map<String, String> parameters = new HashMap<>();
        Property parameter;
        NodeIterator objIterator;
        ResIterator iterator = systemParamModel.listResourcesWithProperty(RDF.type, HOBBIT.Parameter);
        Property defaultValProperty = systemParamModel.getProperty("http://w3id.org/hobbit/vocab#defaultValue");
        while (iterator.hasNext()) {
            parameter = systemParamModel.getProperty(iterator.next().getURI());
            objIterator = systemParamModel.listObjectsOfProperty(parameter, defaultValProperty);
            while (objIterator.hasNext()) {
                String value = objIterator.next().asLiteral().getString();
                if(!parameters.containsKey(BENCHMARK_NS+"#"+parameter.getLocalName()))
                    parameters.put(BENCHMARK_NS+"#"+parameter.getLocalName(), value);
            }
        }


        List<String> missingParams = new ArrayList<>();
        for(String key : paramKeys)
            if(!parameters.containsKey(BENCHMARK_NS+"#"+key)) {
                missingParams.add(key);
            }

        if(missingParams.size()>0)
            throw new Exception("Missing params: "+ String.join(", ", missingParams.toArray(new String[0])));

        String aws_access_key_id = parameters.getStringValueFor(BENCHMARK_NS+"#AWS_ACCESS_KEY_ID");
        String aws_secret_key = parameters.getStringValueFor(BENCHMARK_NS+"#AWS_SECRET_KEY");
        String aws_role_arn = parameters.getStringValueFor(BENCHMARK_NS+"#AWS_ROLE_ARN");
        String aws_region = parameters.getStringValueFor(BENCHMARK_NS+"#AWS_REGION");

        if(parameters.containsKey(BENCHMARK_NS+"#neptuneInstanceType"))
            instanceType = parameters.getStringValueFor(BENCHMARK_NS+"#neptuneInstanceType");

        awsController = new AWSController(aws_access_key_id, aws_secret_key, aws_role_arn, aws_region);
        //awsController2 = new AWSController(aws_access_key_id, aws_secret_key, aws_role_arn, "eu-central-1");
        try {
            awsController.init();
            //awsController2.init();
        }
        catch (Exception e){
            LOGGER.error("Failed to init aws controller: {}", e.getLocalizedMessage());
        }


        initClusterManager();
        //Thread.sleep(600000);

        Options options = new Options("nashorn");
        options.set("anon.functions", true);
        options.set("parse.only", true);
        options.set("scripting", true);

        ErrorManager errors = new ErrorManager();
        context = new Context(options, errors, Thread.currentThread().getContextClassLoader());

        queryEndpoint = "http://"+ sparqlHostAndPort +"/sparql";
        updateEndpoint = queryEndpoint;

    }



    public void initClusterManager() throws Exception {

        Map<String, String> neptuneClusterParams = new HashMap<>();
        if(instanceType!=null)
            neptuneClusterParams.put("DbInstanceType", instanceType);

        neptuneClusterManager = new NeptuneClusterManager(awsController,NEPTUNE_CLUSTER_NAME, SSH_KEY_NAME, neptuneClusterParams);
        neptuneClusterManager.createCluster();

        String endPointAddress = neptuneClusterManager.getDBClusterEndpoint();

        LOGGER.info("Resolving neptune cluster host {}", endPointAddress);

        String command = "getent hosts "+endPointAddress+" | awk '{print $1}'";

        String bastionHostIp = neptuneClusterManager.getBastion().getPublicIpAddress();
        HSession resolveSession = new HSession(SSH_USER, bastionHostIp, SSH_PORT, SSH_KEY_PATH);
        sshConnector = SshConnector.getInstance();
        sshConnector.openTunnel(resolveSession, new Function<HSession, String>(){
            @Override
            public String apply(HSession hSession) {
                try {
                    ChannelExec channel = (ChannelExec) hSession.getSession().openChannel("exec");
                    channel.setCommand(command);
                    channel.setInputStream(null);
                    InputStream output = channel.getInputStream();
                    channel.connect();
                    String result = CharStreams.toString(new InputStreamReader(output)).trim();
                    neptuneClusterIp = result;
                    waitingMutex.release();
                    channel.disconnect();
                } catch (Exception e) {
                    LOGGER.error("Failed to resolve hostname: {}", e.getMessage());
                }
                return null;
            }
        }, null);

        waitingMutex.acquire();
        LOGGER.info("Neptune cluster host was resolved to {}", neptuneClusterIp);

        HSession jumpHostSession = new HSession(SSH_USER, bastionHostIp, SSH_PORT, SSH_KEY_PATH, new String[]{ neptuneClusterIp +":"+NEPTUNE_PORT }, null);
        SshTunnelsProvider.init(jumpHostSession, new Function<HSession, String>() {
            @Override
            public String apply(HSession hSession) {
                Map<Integer, Integer> portForwadings = hSession.getForwardings();
                LOGGER.info("SSH connection to {} established. Ports forwardings: {}", hSession.getHost(), portForwadings.toString());
                sparqlHostAndPort = "localhost:"+String.valueOf(portForwadings.get(NEPTUNE_PORT));
                LOGGER.info("Sparql endpoint: {}", "http://"+sparqlHostAndPort+"/sparql");
                queryExecFactory = new QueryExecutionFactoryHttp("http://"+ sparqlHostAndPort +"/sparql");

                waitingMutex.release();
                return null;
            }
        }, new Function<HSession, String>() {
            @Override
            public String apply(HSession hSession) {
                LOGGER.info("Ssh connection lost");
                return null;
            }
        });
        SshTunnelsProvider.newSshTunnel(null, null);
        waitingMutex.acquire();
    }


    @Override
    protected void postInit() throws Exception {
        LOGGER.info("Deleting all triples before loading");
        //String res1 = new String(execQuery("SELECT (COUNT(?s) AS ?triples) WHERE { ?s ?p ?o }"));

        UpdateRequest updateRequest = UpdateRequestUtils.parse("CLEAR ALL");
        UpdateProcessor updateProcessor = updateExecFactory.createUpdateProcessor(updateRequest);
        try {
            updateProcessor.execute();
        } catch (Exception e) {
            LOGGER.error("Failed to execute update request: {}", e.getLocalizedMessage());
            //e.printStackTrace();
        }

        //String res2 = new String(execQuery("SELECT (COUNT(?s) AS ?triples) WHERE { ?s ?p ?o }"));

        LOGGER.info("Creating bucket {}", NEPTUNE_BUCKET_NAME);
        awsController.createBucket(NEPTUNE_BUCKET_NAME);


        LOGGER.info("Uploading files to bucket {}", NEPTUNE_BUCKET_NAME);

        int errorsCount=0;
        File theDir = new File(datasetFolderName);
        for (File f : theDir.listFiles()){
            try {
                awsController.putObjectToS3(NEPTUNE_BUCKET_NAME, f);
                LOGGER.info("{} loaded to s3", f.getName());
            } catch (Exception e) {
                LOGGER.error("Could not put {} to S3: {}", f.getName(), e.getLocalizedMessage());
                errorsCount++;
            }
        }
        if(errorsCount>0)
            throw new Exception("Datasets not loaded to s3. See exceptions");

        try {
            neptuneClusterManager.addRoleARN();
        }
        catch (DBClusterRoleAlreadyExistsException e){
            LOGGER.info(e.getLocalizedMessage());
        }
    }

    @Override
    protected void loadDataset(String graphURI) throws Exception {
        int errorsCount=0;

        //LOGGER.warn("Skipping data load to S3 {}", NEPTUNE_BUCKET_NAME);
        File theDir = new File(datasetFolderName);

        String roleArn = neptuneClusterManager.getLoaderRoleArn();
        for (File f : theDir.listFiles()) {
            String url = "http://" + sparqlHostAndPort + "/loader";
            String sourceUrl = "s3://" + NEPTUNE_BUCKET_NAME + "/" + f.getName();
            HttpPost httppost = new HttpPost(url);
            httppost.setHeader("Accept", "application/json");
            httppost.setHeader("Content-Type", "application/json");

            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("source", sourceUrl);
            jsonObject.addProperty("format", "turtle");
            jsonObject.addProperty("iamRoleArn", roleArn);
            //jsonObject.addProperty("region", awsController2.getRegion());
            //jsonObject.addProperty("region", awsController.getRegion());
            jsonObject.addProperty("region", awsController.getRegion());

            String inputString = jsonObject.toString();
            httppost.setEntity(new StringEntity(inputString, ContentType.APPLICATION_JSON));


            int attempts = 0;
            boolean succceded=false;
            while (!succceded){

                HttpEntity entity = null;
                try {
                    LOGGER.info("Sending request to {}", url);
                    HttpResponse response = httpclient.execute(httppost);
                    entity = response.getEntity();
                } catch (Exception e) {
                    LOGGER.error("Failed to send loader request for {}: {}", sourceUrl, e.getMessage());
                    errorsCount++;
                }

                String result = null;
                if (entity != null) {
                    try {
                        InputStream instream = entity.getContent();
                        Scanner s = new Scanner(instream).useDelimiter("\\A");
                        result = (s.hasNext() ? s.next() : "");
                        instream.close();
                    } catch (Exception e) {
                        LOGGER.error("Failed to read loader response for {}: {}", sourceUrl, e.getMessage());
                        errorsCount++;
                    }
                }

                if (result != null && result.contains("200 OK")){
                    JSONParser parser = new JSONParser(result, new Global(context), false);
                    JO parsed = (JO) parser.parse();
                    String loadId = ((JO) parsed.get("payload")).get("loadId").toString();

                    JO status = getLoaderStatus(loadId);
                    if (String.valueOf(status.get("status")).equals("LOAD_IN_PROGRESS")) {
                        LOGGER.info("Waiting for LOAD_COMPLETED for {}: {}", sourceUrl, "http://" + sparqlHostAndPort + "/loader?loadId=" + loadId);
                    }

                    while (status.get("status").equals("LOAD_IN_PROGRESS")) {
                        status = getLoaderStatus(loadId);
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            LOGGER.debug("Thread sleep exception: {}", e.getLocalizedMessage());
                            errorsCount++;
                        }
                        LOGGER.debug("{} records loaded", status.get("totalRecords"));
                    }
                    if (status.get("status").equals("LOAD_FAILED")) {
                        LOGGER.error("Load failed for {}", sourceUrl);
                        errorsCount++;
                    } else {
                        LOGGER.info("{} for {}. Loaded {} records in {} seconds", status.get("status"), sourceUrl, status.get("totalRecords"), status.get("totalTimeSpent"));
                        succceded=true;
                    }
                } else {
                    LOGGER.warn("File not loaded: {}", result);
                    attempts++;
                    if(attempts<attemptsLimit) {
                        LOGGER.info("Trying another attempt ({}/{}) in 10s", attempts, attemptsLimit);
                        Thread.sleep(10000);
                    }else{
                        break;
                    }
                }
            }
            if(!succceded){
                LOGGER.error("File not loaded after {} attempts. See exceptions above", attemptsLimit);
                errorsCount++;
            }
        }

        if(errorsCount>0)
            throw new Exception("Not all datasets were loaded. See exceptions");

        LOGGER.info("Datasets are to loaded");
    }

    public JO getLoaderStatus(String loadId){
        String resultStr=null;
        try {
            HttpGet get = new HttpGet("http://" + sparqlHostAndPort + "/loader?loadId="+loadId);
            HttpResponse response = httpclient.execute(get);
            BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

            StringBuffer result = new StringBuffer();
            String line = "";
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            resultStr = result.toString();

        } catch (Exception e) {
            LOGGER.error("Failed to get loader status: {}", e.getLocalizedMessage());
        }

        try {
            JSONParser jsonParser = new JSONParser(resultStr, new Global(context), false);

            JO parsed = (JO) jsonParser.parse();
            JO payload = (JO) parsed.get("payload");

            JO overallStatus = (JO)payload.get("overallStatus");
            return overallStatus;
        }
        catch (Exception e){
            LOGGER.error("Failed to parse loader status: {}", e.getLocalizedMessage());
        }
        return null;
    }

    @Override
    public void close() throws IOException{
       LOGGER.warn("Skipping cluster deletion");
        //neptuneClusterManager.deleteCluster();
       LOGGER.info("Closing ssh sessions");
       sshConnector.closeSessions();
        LOGGER.info("Sessions have been closed");
       super.close();

    }
}
