package org.hobbit.sparql_snb;

import org.hobbit.awscontroller.AWSController;
import org.hobbit.sparql_snb.systems.neptune.NeptuneClusterManager;
import org.hobbit.sparql_snb.systems.neptune.NeptuneSystemAdapter;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import static org.hobbit.sparql_snb.Constants.SSH_KEY_NAME;

/**
 * @author Pavel Smirnov. (psmirnov@agtinternational.com / smirnp@gmail.com)
 */
public class NeptuneClusterManagerTest {

    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();
    NeptuneSystemAdapter neptuneSystemAdapter;
    NeptuneClusterManager clusterManager;

    @Before
    public void init() throws Exception {
        environmentVariables.set("AWS_REGION", "eu-west-1");

        AWSController awsController = new AWSController();
        clusterManager = new NeptuneClusterManager(awsController,"neptune", SSH_KEY_NAME);

        neptuneSystemAdapter = new NeptuneSystemAdapter();
//        neptuneSystemAdapter.initClusterManager();
    }

    @Test
    @Ignore
    public void createClusterTest() throws Exception {
        clusterManager.createCluster();
        //neptuneSystemAdapter.neptuneClusterManager.createCluster();

    }

    @Test
    @Ignore
    public void deleteClusterTest() throws Exception {
        clusterManager.deleteCluster();
        //neptuneSystemAdapter.neptuneClusterManager.createCluster();

    }



}
