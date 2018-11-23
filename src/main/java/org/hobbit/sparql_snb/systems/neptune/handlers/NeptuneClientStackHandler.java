package org.hobbit.sparql_snb.systems.neptune.handlers;

import org.hobbit.awscontroller.StackHandlers.VpcDependentStackHandler;

/**
 * @author Pavel Smirnov. (psmirnov@agtinternational.com / smirnp@gmail.com)
 */
public class NeptuneClientStackHandler extends VpcDependentStackHandler {

    public NeptuneClientStackHandler(NeptuneStackHandlerBuilder builder) {
        super(builder);
        name = builder.clusterName +"-client";
        bodyFilePath = "aws/neptune_ec2_client.yaml";
        parameters.put("EC2SSHKeyPairName", builder.sshKeyName);

    }


    public class Builder <C extends VpcDependentStackHandler, B extends Builder<C,B>> extends VpcDependentStackHandler.Builder<C,B>{


        public String vpcStackName;
        public String sshKeyName;

        public B vpcStackName(String value){
            vpcStackName = value;
            return (B)this;
        }

        public B sshKeyName(String value){
            sshKeyName = value;
            return (B)this;
        }

        public B name(String value){
            name = value;
            return (B)this;
        }

        public B tags(String value) {
            tags = value;
            return (B)this;
        }


    }
}
