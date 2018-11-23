package org.hobbit.sparql_snb;

/**
 * @author Pavel Smirnov. (psmirnov@agtinternational.com / smirnp@gmail.com)
 */
public class Constants {
    // TODO: Add image names of containers

    public static final String GIT_REPO_PATH = "git.project-hobbit.eu:4567/smirnp/";
    public static final String PROJECT_NAME = "data-storage-benchmark/";


    public static final String NEPTUNE_IMAGE_NAME = GIT_REPO_PATH+PROJECT_NAME +"neptune-system-adapter";

    public static final String SSH_KEY_NAME = "hobbit_2";
    public static final String SSH_KEY_PATH = "sshkeys/hobbit_2.pem";


    public static final String BENCHMARK_NS = "http://w3id.org/bench";
    public static final String BENCHMARK_URI = BENCHMARK_NS+"#DSBBenchmark";


    public static final String NEPTUNE_SYSTEM_URI = BENCHMARK_NS+"#neptuneSystemAdapter";

    public static final byte  BULK_LOAD_DATA_GEN_FINISHED = (byte) 151;
    public static final byte  BULK_LOADING_DATA_FINISHED = (byte) 150;

}
