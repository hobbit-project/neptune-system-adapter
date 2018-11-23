package org.hobbit.sparql_snb.systems.neptune.handlers;

import org.hobbit.awscontroller.StackHandlers.VpcDependentStackHandler;

/**
 * @author Pavel Smirnov. (psmirnov@agtinternational.com / smirnp@gmail.com)
 */
public class NeptuneDBClusterStackHandler extends VpcDependentStackHandler {

    public NeptuneDBClusterStackHandler(NeptuneStackHandlerBuilder builder) {
        super(builder);
        name = builder.clusterName +"-cluster";
        bodyFilePath = "aws/neptune-cluster.yaml";

    }



}
