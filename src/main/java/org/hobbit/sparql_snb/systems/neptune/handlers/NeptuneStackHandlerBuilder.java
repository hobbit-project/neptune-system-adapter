package org.hobbit.sparql_snb.systems.neptune.handlers;

import org.hobbit.awscontroller.StackHandlers.VpcDependentStackHandler;

/**
 * @author Pavel Smirnov. (psmirnov@agtinternational.com / smirnp@gmail.com)
 */

public class NeptuneStackHandlerBuilder <C extends VpcDependentStackHandler, B extends NeptuneStackHandlerBuilder<C,B>> extends VpcDependentStackHandler.Builder<C,B>{


    public String sshKeyName;
    public String clusterName;

    public B vpcStackName(String value){
        vpcStackName = value;
        return (B)this;
    }

    public B sshKeyName(String value){
        sshKeyName = value;
        return (B)this;
    }

    public B clusterName(String value){
        clusterName = value;
        return (B)this;
    }

    public B tags(String value) {
        tags = value;
        return (B)this;
    }


}
