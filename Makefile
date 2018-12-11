test-system:
	mvn -Dtest=BenchmarkTest#checkHealth test

package:
	mvn -DskipTests package

build-images:
	mvn -Dtest=BenchmarkTest#buildImages test

test-dockerized-system:
	make build-images
	mvn -Dtest=BenchmarkTest#checkHealthDockerized test


push-system-adapters:
	sudo docker push git.project-hobbit.eu:4567/smirnp/data-storage-benchmark/neptune-system-adapter
