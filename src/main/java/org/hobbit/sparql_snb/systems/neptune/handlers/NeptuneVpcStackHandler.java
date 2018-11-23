package org.hobbit.sparql_snb.systems.neptune.handlers;

import org.hobbit.awscontroller.StackHandlers.AbstractStackHandler;

/**
 * @author Pavel Smirnov. (psmirnov@agtinternational.com / smirnp@gmail.com)
 */
public class NeptuneVpcStackHandler extends AbstractStackHandler {


    public NeptuneVpcStackHandler(NeptuneStackHandlerBuilder builder) {
        super(builder);
        name = builder.getVpcStackName();
        bodyFilePath = "aws/neptune-vpc.yaml";
    }

}
