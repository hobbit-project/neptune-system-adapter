package org.hobbit.sparql_snb.systems.neptune;

import com.amazonaws.services.cloudformation.model.StackResourceSummary;
import com.amazonaws.services.ec2.model.Instance;
import org.hobbit.awscontroller.AWSController;
import org.hobbit.awscontroller.StackHandlers.AbstractStackHandler;
import org.hobbit.cloud.interfaces.ICloudClusterManager;
import org.hobbit.cloud.interfaces.Node;
import org.hobbit.cloud.interfaces.Resource;
import org.hobbit.sparql_snb.systems.neptune.handlers.NeptuneClientStackHandler;
import org.hobbit.sparql_snb.systems.neptune.handlers.NeptuneDBClusterStackHandler;
import org.hobbit.sparql_snb.systems.neptune.handlers.NeptuneStackHandlerBuilder;
import org.hobbit.sparql_snb.systems.neptune.handlers.NeptuneVpcStackHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Pavel Smirnov. (psmirnov@agtinternational.com / smirnp@gmail.com)
 */
public class NeptuneClusterManager implements ICloudClusterManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NeptuneClusterManager.class);
    public AWSController awsController;
    AbstractStackHandler vpcStackHandler;
    AbstractStackHandler clusterStackHandler;
    AbstractStackHandler clientStackHandler;
    NeptuneStackHandlerBuilder builder;
    String dbClusterEndpoint;
    List<List<AbstractStackHandler>> stackList;


    public NeptuneClusterManager(AWSController awsController, String clusterName, String sshKeyName){
        this(awsController, clusterName, sshKeyName, new HashMap());
    }

    public NeptuneClusterManager(AWSController awsController, String clusterName, String sshKeyName, Map clusterParams){

        this.awsController = awsController;
        builder = new NeptuneStackHandlerBuilder()
                .clusterName(clusterName)
                .vpcStackName(clusterName+"-vpc")
                .sshKeyName(sshKeyName);

        //Stacks created out of official Neptune stack (https://s3.amazonaws.com/aws-neptune-customer-samples/v2/cloudformation-templates/neptune-full-stack-nested-template.json)

        vpcStackHandler = new NeptuneVpcStackHandler(builder);
        clusterStackHandler = new NeptuneDBClusterStackHandler(builder).appendParameters(clusterParams);
        clientStackHandler = new NeptuneClientStackHandler(builder);

        stackList = new ArrayList<List<AbstractStackHandler>>() {{
            add(Arrays.asList(new AbstractStackHandler[]{ vpcStackHandler }));
            add(Arrays.asList(new AbstractStackHandler[]{ clusterStackHandler, clientStackHandler }));
        }};
    }


    @Override
    public Resource getVPC() throws Exception {
        return null;
    }

    @Override
    public Node getBastion() throws Exception {
        List<StackResourceSummary> summary = awsController.getStackResources(clientStackHandler.getName(), "AWS::EC2::Instance");
        String instanceId = summary.get(0).getPhysicalResourceId();
        Instance instance = awsController.getEC2InstanceByName(instanceId).get(0);
        Node ret = new Node(instance.getInstanceId()).setPublicIpAddress(instance.getPublicIpAddress());
        return ret;
    }

    @Override
    public Node getNAT() throws Exception {
        return null;
    }


    public void createCluster(String configuration) throws Exception {
        createCluster();
    }

    public String getDBClusterEndpoint() throws Exception {
        if(dbClusterEndpoint==null) {
            Map<String, String> outputsMap = awsController.getStackOutputsMap(clusterStackHandler.getName());
            dbClusterEndpoint = outputsMap.get("DBClusterEndpoint");
        }
        return dbClusterEndpoint;
    }

    public void deleteCluster(String s) throws Exception {

    }

    public String getLoaderRoleArn(){
        String roleArn = null;
        try {
            roleArn = awsController.getStackOutputsMap(clientStackHandler.getName()).get("NeptuneLoadFromS3IAMRoleArn");
        } catch (Exception e) {
            LOGGER.error("Failed to get roleArn: {}", e.getLocalizedMessage());
        }
        return roleArn;
    }

    public void createCluster() throws Exception {
        LOGGER.info("Creating cluster");

        List<String> createdStackIds = awsController.createStacks(stackList, true);

        if(createdStackIds.contains(clientStackHandler.getId()))
            addRoleARN();


    }

    public void addRoleARN() throws Exception {
        String clusterId = awsController.getStackOutputsMap(clusterStackHandler.getName()).get("DBClusterId");
        LOGGER.info("Adding bulk loading role arn to the cluster {}", clusterId);
        awsController.addRoleToDBCluster(clusterId, getLoaderRoleArn());
    }

    @Override
    public void deleteCluster() throws Exception {
        awsController.deleteStacks(stackList);
    }

    public void close() throws Exception {
        super.clone();
        LOGGER.info("Virtuoso has stopped.");
    }


}
