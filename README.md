# System Adapter for Amazon Neptune
HOBBIT-compatible system adapter for [AWS Neptune](https://aws.amazon.com/neptune/). 
- Created out of the official [AWS Neptune Cloud Formation stack](https://docs.aws.amazon.com/neptune/latest/userguide/quickstart.html) splitted on three ones (for speeding up)
- The code can be executed locally (without the HOBBIT platform) together with the [Data Storage Benchmark](https://github.com/hobbit-project/DataStorageBenchmark). 
- The original benchmark images or modified images with reduced amount of triples (2M) for ScaleFactor=1 can be used (see BenchmarkTest.java).

## Requrements (for local running, packaging, building docker image)
- Docker in swarm mode (tested on 17.12.1)
- [Java SDK](https://github.com/hobbit-project/java-sdk) dependency installed
- [AwsController](https://github.com/hobbit-project/aws-controller) dependency installed
- AWS_ACCESS_KEY_ID, AWS_SECRET_KEY, AWS_ROLE_ARN, AWS_REGION provided as environemnt variables (or specified in the system.ttl) file

## Running/debugging instructions
- Provide account-specific information (see requirements)
- To run the adapter as part of Benchmark: `make debug-system`
- To debug adapter as pure java: see the `checkHealth()` from BenchmarkTest.java
- To package adapter into docker image (to push into the HOBBIT platform): `make build-images`
- To run the adapter as docker image (to check it locally before pushing the HOBBIT platform): `checkHealthDockerized()` from BenchmarkTest.java

## Disclaimer
The software in this repository automates the management of the official [AWS Neptune Cloud Formation stacks](https://docs.aws.amazon.com/neptune/latest/userguide/quickstart.html) for benchmarking purposes within the HOBBIT platform. 

Use of Amazon Web Services (including benchmarking purposes) via this software is still regulated by Amazon's terms of use (since you need to provide account-specific information).

Inappropriate use benchmarking results (e.g. for publishing) may cause terms violations and further liability.
