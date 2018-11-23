# System Adapter for Amazon Neptune
HOBBIT-compatible system Adapter for [AWS Neptune](https://www.google.com/url?sa=t&rct=j&q=&esrc=s&source=web&cd=1&cad=rja&uact=8&ved=2ahUKEwiKk8_Nw-reAhUDDSwKHWcrC-oQFjAAegQIBRAC&url=https%3A%2F%2Faws.amazon.com%2Fneptune%2F&usg=AOvVaw38TVCJpz68Aqm-z0jtgZxN). 
- Uses the official cloud formation stack splitted on three ones (vpc, client, cluster)
- The code can be executed locally together with the [Data Storage Benchmark](https://github.com/hobbit-project/DataStorageBenchmark) (Using either the original images or modified images with reduced amount of triples (2M) for ScaleFactor=1 - see BenchmarkTest.java)

# Requrements (for running or building/docker image building)
- Docker in swarm mode (tested on 17.12.1)
- [Java SDK](https://github.com/hobbit-project/java-sdk) dependency installed
- [AwsController](https://github.com/hobbit-project/aws-controller) dependency installed
- AWS_ACCESS_KEY_ID, AWS_SECRET_KEY, AWS_ROLE_ARN, AWS_REGION provided as environemnt variables (or in via system.ttl) file

# Running/debugging instructions
- To run the adapter as part of Benchmark: `make debug-system`
- To debug adapter as pure java: see the `checkHealth()` from BenchmarkTest.java
- To package adapter into docker image (to push into the HOBBIT platform): `make build-images`
- To run the adapter as docker image (to check it locally before pushing the HOBBIT platform): `checkHealthDockerized()` from BenchmarkTest.java

# Disclaimer
The software in this repository automates the management of the official [AWS Neptune Cloud Formation stacks](https://docs.aws.amazon.com/neptune/latest/userguide/quickstart.html) for benchmarking purposes within the HOBBIT platform. Use of AWS Services (including the benchmarking purposes) via this software is still regulated by Amazon's terms of use (since you need to provide information about your account - AWS_ACCESS_KEY_ID, AWS_SECRET_KEY, AWS_ROLE_ARN to the software). Use the benchmark results (e.g. for publishing) may violate the terms and cause liability.
