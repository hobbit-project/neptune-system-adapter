# System Adapter for Amazon Neptune

[![Build Status](https://travis-ci.org/hobbit-project/neptune-system-adapter.svg?branch=master)](https://travis-ci.org/hobbit-project/neptune-system-adapter)

HOBBIT-compatible system adapter for [AWS Neptune](https://aws.amazon.com/neptune/). 
- Created out of the official [AWS Neptune Cloud Formation stack](https://docs.aws.amazon.com/neptune/latest/userguide/quickstart.html) splitted on three ones (for speeding up)
- The code can be executed locally (without the HOBBIT platform) together with the [Data Storage Benchmark](https://github.com/hobbit-project/DataStorageBenchmark). 
- The original benchmark images or modified images with reduced amount of triples (2M) for ScaleFactor=1 can be used (see BenchmarkTest.java).
- Please note that at the moment the system adapter does not delete created cloud resources (stacks). Don't forget to delete them manually.

## Requrements (for packaging, local running, building docker image)
- Docker in swarm mode (tested on 17.12.1)
- `127.0.0.1 rabbit` string in `/etc/hosts` file
- AWS_ACCESS_KEY_ID, AWS_SECRET_KEY, AWS_ROLE_ARN, AWS_REGION specified as environment variables (or specified via the system.ttl file)

## Running/debugging instructions
- Provide account-specific information (see requirements) + ssh key (see Constants.java)
- To run the adapter (together with the benchmark): `make test-system`
- To debug adapter as pure java: see the `checkHealth()` from BenchmarkTest.java
- To package adapter into docker image (to upload and run in from the HOBBIT platform): `make package` and `make build-images`. SshKeys folder would addded into the docker image (configured in BenchmarkTest.java).
- To run the adapter as docker image (to check it locally before pushing the HOBBIT platform): `checkHealthDockerized()` from BenchmarkTest.java

## Additional info
- The adapter uses the [AwsController](https://github.com/hobbit-project/aws-controller) for executing stacks and the [Java SDK](https://github.com/hobbit-project/java-sdk) to run itself locally. Find the dependencies are in the POM file.
- RabbitMQ and containers ot the benchmark are expected to be pulled and started attached to networks `hobbit` and `hobbit-core` (this should be automatically managed by SDK).
- Containers are trying to find rabbitMQ in a given 30 seconds interval (returning the `Host not found` exception every 5 seconds) and then terminate. Sometimes even 30s is not enougth for some components, then just restart it again.
- Don't forget to  manually delete created AWS resources after benchmark finished (can be done NeptuneClusterManagerTest.java) or implement it as part of system adapter.
- Feel free to create [issues](https://github.com/hobbit-project/neptune-system-adapter/issues) in case of any troubles with the code.


## Disclaimer
The software in this repository automates the management of the official [AWS Neptune Cloud Formation stacks](https://docs.aws.amazon.com/neptune/latest/userguide/quickstart.html) for benchmarking purposes within the HOBBIT platform. 

Use of Amazon Web Services (including benchmarking purposes) via this software is still regulated by Amazon's terms of use (since you need to provide account-specific information).

Inappropriate use benchmarking results (e.g. for publishing) may cause terms violations and further liability.
