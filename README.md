## AWS ECS

Runs a docker image on an AWS ECS setup.

Takes a JSON configuration file listing a named ECS setup, for example as created
using the aws-vpc project, and a docker image in AWS ECR, for example as
created by the docker-primes project, and runs the image as a task with an
allowance for CPU and memory.

Currently only a single EC2 instance will be set up, and so if resources
aren't available to run a task, it will fail.

### Prerequesites

* JDK installed on desktop (tested with OpenJDK 1.8.0_91).

* AWS credentials in ~/.aws/credentials
  * The AWS account used needs administrator privileges.

(Only tested on Kubuntu 16.04 LTS).

### Installation

#### JDK

<http://openjdk.java.net/install/>

#### AWS Credentials

<http://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html#cli-config-files>

Put a file called `credentials` in the `.aws` directory under your
home directory. This needs to contain credentials for an account or ideally an
IAM user that has permissions to read, write to and create AWS ECR repositories.
An example file would contain something like the following:

    [default]
    aws_access_key_id=BGHAU5SU23JJFO89QJEH
    aws_secret_access_key=huds8H/hudy73djdhwsuGYTFkugyuiItq/u8yHia

#### Gradle

The gradlew wrapper script will install Gradle as part of the build.

### Usage

#### Building

A standalone jar is created by running

    ./gradlew shadowJar

from the project root.

#### Running

run-ecs takes one argument, the filename of a JSON configuration file, an example of which follows.
The filename should be relative to the current directory.

For example,

        java -jar build/libs/run-ecs.jar primes-config.json

A sample JSON configuration might be

```json
{
  "clusterName" : "my-eg",
  "taskName" : "my-task",
  "imageName" : "my-eg/primes",
  "bucketName" : "a7d50896-1616-48e1-b259-64fad883e022",
  "outputFilename" : "primes-output.txt",
  "cpuPercentage" : 50,
  "memoryUsage" : 128,
  "runEnvironment" : {
    "takeN" : 100,
    "ignoreFirstN" : 100,
    "takeEveryN" : 100
  }
}
```


The properties of this file are:

* clusterName - the identifier used when creating the VPC
* taskName - an identifier for this task. If a task definition with this name has already been created,
it will be re-used with the original configuration, useful for instance if the docker image has been updated.
* imageName - the name of the image repository, e.g. the value set using
`def awsRepositoryName = "my-eg/primes"` in the docker-primes project.
The :latest tagged version of this image will be used.
* bucketName - the name of an S3 bucket, within this AWS account, to write any output to.
If a bucket with this name doesn't exist, it will be created, with a 2 day deletion time.
* outputFilename - the name of the output, when written to S3.
* cpuPercentage - the percentage of a single instance CPU to reserve.
* memoryUsage - the memory in Mb to allocate.
* runEnvironment - environment variables to pass to the docker instance. In this example,
variables TAKE_N, IGNORE_FIRST_N and TAKE_EVERY_N would be set.


The output from running this will include a URL to the bucket output, which can be shared
 without need for any AWS credentials.



<br/><hr/>

##### License

Copyright © 2016 Paul Whyler

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
