package org.hobbit.sparql_snb;

import org.hobbit.sdk.docker.builders.DynamicDockerFileBuilder;

public class CommonDockersBuilder extends DynamicDockerFileBuilder {


    public CommonDockersBuilder(Class runningClass, String imageName) throws Exception {
        super("CommonDockersBuilder");
        imageName(imageName);
        buildDirectory(".");
        jarFilePath(System.getProperty("jarFilePath"));
        dockerWorkDir("/usr/src/sparql-snb");
        runnerClass(org.hobbit.core.run.ComponentStarter.class, runningClass);
        containerName(runningClass.getSimpleName());
        //runnerClass(value);
    }


}