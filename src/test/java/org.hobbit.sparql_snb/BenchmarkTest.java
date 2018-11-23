package org.hobbit.sparql_snb;

import org.apache.commons.io.FileUtils;
import org.apache.jena.rdf.model.*;
import org.hobbit.controller.docker.MetaDataFactory;
import org.hobbit.core.Constants;
import org.hobbit.core.components.Component;
import org.hobbit.core.rabbit.RabbitMQUtils;
import org.hobbit.sdk.EnvironmentVariablesWrapper;
import org.hobbit.sdk.JenaKeyValue;
import org.hobbit.sdk.docker.PullBasedDockerizer;
import org.hobbit.sdk.docker.RabbitMqDockerizer;
import org.hobbit.sdk.docker.builders.AbstractDockersBuilder;
import org.hobbit.sdk.docker.builders.PullBasedDockersBuilder;
import org.hobbit.sdk.docker.builders.hobbit.*;

import org.hobbit.sdk.examples.dummybenchmark.DummyEvalStorage;
import org.hobbit.sdk.utils.CommandQueueListener;
import org.hobbit.sdk.utils.ComponentsExecutor;

import org.hobbit.sdk.utils.MultiThreadedImageBuilder;
import org.hobbit.sdk.utils.commandreactions.CommandReactionsBuilder;

        import org.hobbit.sparql_snb.systems.neptune.NeptuneSystemAdapter;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Date;


import static org.hobbit.sparql_snb.Constants.*;
import static org.hobbit.sparql_snb.Constants.NEPTUNE_IMAGE_NAME;
import static org.hobbit.core.Constants.*;

/**
 * @author Pavel Smirnov
 */

public class BenchmarkTest extends EnvironmentVariablesWrapper {

    //Benchmark with 2M triples for SF=1
    public static String BENCHMARK_BASE_IMAGE_URI="git.project-hobbit.eu:4567/smirnp/data-storage-benchmark/";

    //Original benchmark containers are available (50M triples for SF=1)
    //public static String BENCHMARK_BASE_IMAGE_URI="git.project-hobbit.eu:4567/mspasic/";

    private RabbitMqDockerizer rabbitMqDockerizer;
    private ComponentsExecutor componentsExecutor;
    private CommandQueueListener commandQueueListener;

    BenchmarkDockerBuilder benchmarkBuilder;
    DataGenDockerBuilder dataGeneratorBuilder;
    TaskGenDockerBuilder taskGeneratorBuilder;
    EvalStorageDockerBuilder evalStorageBuilder;
    EvalModuleDockerBuilder evalModuleBuilder;
    SystemAdapterDockerBuilder systemAdapterBuilder;

    Component benchmarkController = new SNBBenchmarkController();
    Component dataGen = new SNBDataGenerator();
    Component taskGen = new SNBTaskGenerator();
    Component evalStorage = new DummyEvalStorage();

    Component systemAdapter;
    Component evalModule = new SNBEvaluationModule();

    @Before
    public void init(){

        //environmentVariables.set("AWS_ROLE_ARN", "");
        environmentVariables.set("AWS_REGION", "eu-west-1");
        environmentVariables.set(RABBIT_MQ_HOST_NAME_KEY, "rabbit");
        environmentVariables.set(HOBBIT_SESSION_ID_KEY, "session_"+String.valueOf(new Date().getTime()));

    }

    public void init(Boolean useCachedImage) throws Exception {

        benchmarkBuilder = new BenchmarkDockerBuilder(new PullBasedDockersBuilder(BENCHMARK_BASE_IMAGE_URI+"dsb-benchmarkcontroller"));
        dataGeneratorBuilder = new DataGenDockerBuilder(new PullBasedDockersBuilder(BENCHMARK_BASE_IMAGE_URI+"dsb-datagenerator"));
        taskGeneratorBuilder = new TaskGenDockerBuilder(new PullBasedDockersBuilder(BENCHMARK_BASE_IMAGE_URI+"dsb-taskgenerator"));

        evalStorageBuilder = new EvalStorageDockerBuilder(new PullBasedDockersBuilder("git.project-hobbit.eu:4567/defaulthobbituser/defaultevaluationstorage:1.0.5"));
        evalModuleBuilder = new EvalModuleDockerBuilder(new PullBasedDockersBuilder((BENCHMARK_BASE_IMAGE_URI+"dsb-evaluationmodule")));

        systemAdapter = new NeptuneSystemAdapter();
        systemAdapterBuilder = new SystemAdapterDockerBuilder(new CommonDockersBuilder(systemAdapter.getClass(), NEPTUNE_IMAGE_NAME).addFileOrFolder("sshkeys").addFileOrFolder("aws").useCachedImage(useCachedImage));

    }


    @Test
    public void buildImages() throws Exception {

        init(false);

        MultiThreadedImageBuilder builder = new MultiThreadedImageBuilder(5);
        builder.addTask(systemAdapterBuilder);
        builder.build();
    }

    @Test
    public void checkHealth() throws Exception {
        checkHealth(false);
    }


    @Test
    public void checkHealthDockerized() throws Exception {
        checkHealth(true);
    }

    private void checkHealth(Boolean dockerized) throws Exception {

        Boolean useCachedImages = true;
        init(useCachedImages);

        rabbitMqDockerizer = RabbitMqDockerizer.builder().build();

        benchmarkController = benchmarkBuilder.build();
        dataGen = dataGeneratorBuilder.build();
        taskGen = taskGeneratorBuilder.build();
        //evalStorage = evalStorageBuilder.build();
        evalModule = evalModuleBuilder.build();

        if(dockerized) {
            systemAdapter = systemAdapterBuilder.build();
        }

        commandQueueListener = new CommandQueueListener();
        componentsExecutor = new ComponentsExecutor();

        rabbitMqDockerizer.run();

        CommandReactionsBuilder commandReactionsBuilder = new CommandReactionsBuilder(componentsExecutor, commandQueueListener)
                .benchmarkController(benchmarkController).benchmarkControllerImageName(benchmarkBuilder.getImageName())
                .dataGenerator(dataGen).dataGeneratorImageName(dataGeneratorBuilder.getImageName())
                .taskGenerator(taskGen).taskGeneratorImageName(taskGeneratorBuilder.getImageName())
                .evalStorage(evalStorage).evalStorageImageName("git.project-hobbit.eu:4567/defaulthobbituser/defaultevaluationstorage:1.0.5")
                .systemAdapter(systemAdapter).systemAdapterImageName(systemAdapterBuilder.getImageName())
                .evalModule(evalModule).evalModuleImageName(evalModuleBuilder.getImageName());


        commandQueueListener.setCommandReactions(
                commandReactionsBuilder.containerCommandsReaction(),
                commandReactionsBuilder.benchmarkSignalsReaction()
        );


        componentsExecutor.submit(commandQueueListener);
        commandQueueListener.waitForInitialisation();

        String benchmarkContainerId = "benchmark";
        String systemContainerId = "system";


       benchmarkContainerId = commandQueueListener.createContainer(benchmarkBuilder.getImageName(), "benchmark", new String[]{ HOBBIT_EXPERIMENT_URI_KEY+"="+NEW_EXPERIMENT_URI,  BENCHMARK_PARAMETERS_MODEL_KEY+"="+ RabbitMQUtils.writeModel2String(createBenchmarkParameters().toModel()) });
       systemContainerId = commandQueueListener.createContainer(systemAdapterBuilder.getImageName(), "system" ,new String[]{ SYSTEM_PARAMETERS_MODEL_KEY+"="+  RabbitMQUtils.writeModel2String(createSystemParameters()) });

        environmentVariables.set("BENCHMARK_CONTAINER_ID", benchmarkContainerId);
        environmentVariables.set("SYSTEM_CONTAINER_ID", systemContainerId);

        commandQueueListener.waitForTermination();

        rabbitMqDockerizer.stop();

        Assert.assertFalse(componentsExecutor.anyExceptions());
    }


    public static JenaKeyValue createBenchmarkParameters() {

        JenaKeyValue kv = new JenaKeyValue(NEW_EXPERIMENT_URI);
        kv.put(BENCHMARK_NS+"#hasSF", 1);
        kv.put(BENCHMARK_NS+"#numberOfOperations", 50);
        return kv;
    }

    public static Model createSystemParameters() throws IOException {
        byte[] bytes = FileUtils.readFileToByteArray(new File("system.ttl"));
        Model model =  MetaDataFactory.byteArrayToModel(bytes, "TTL");

        String benchmarkInstanceId = Constants.NEW_EXPERIMENT_URI;
        Resource benchmarkInstanceResource = model.createResource(benchmarkInstanceId);
        //model.add(benchmarkInstanceResource, model.createProperty(BENCHMARK_NS+"#neptuneInstanceType"), model.createTypedLiteral("db.r4.2xlarge", "xsd:string"));
        model.add(benchmarkInstanceResource, model.createProperty(BENCHMARK_NS+"#neptuneInstanceType"), model.createTypedLiteral("db.r4.large", "xsd:string"));

        model.add(benchmarkInstanceResource, model.createProperty(BENCHMARK_NS+"#AWS_ACCESS_KEY_ID"), model.createTypedLiteral(System.getenv("AWS_ACCESS_KEY_ID"), "xsd:string"));
        model.add(benchmarkInstanceResource, model.createProperty(BENCHMARK_NS+"#AWS_SECRET_KEY"), model.createTypedLiteral(System.getenv("AWS_SECRET_KEY"), "xsd:string"));
        model.add(benchmarkInstanceResource, model.createProperty(BENCHMARK_NS+"#AWS_ROLE_ARN"), model.createTypedLiteral(System.getenv("AWS_ROLE_ARN"), "xsd:string"));
        model.add(benchmarkInstanceResource, model.createProperty(BENCHMARK_NS+"#AWS_REGION"), model.createTypedLiteral(System.getenv("AWS_REGION"), "xsd:string"));

        return model;

    }


}
