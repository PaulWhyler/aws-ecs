package org.pmwhyle.eg.ecs;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.ecr.AmazonECRClient;
import com.amazonaws.services.ecr.model.DescribeRepositoriesRequest;
import com.amazonaws.services.ecr.model.Repository;
import com.amazonaws.services.ecr.model.RepositoryNotFoundException;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.*;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.BucketLifecycleConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.List;

/**
 */
public class RunContainer {

    private static RunEcsConfig exampleConfig = RunEcsConfig.example();


    private final AmazonECSClient ecsClient;
    private final AmazonECRClient ecrClient;
    private final AmazonS3Client s3Client;
    private final RunEcsConfig config;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private RunContainer(RunEcsConfig config) {
        this.config = config;
        ecsClient = new AmazonECSClient();
        ecrClient = new AmazonECRClient();
        s3Client = new AmazonS3Client();
    }

    public static void main(String[] args) throws JsonProcessingException {
        if (args.length != 1) {
            usage();
            System.exit(22);
        }

        RunEcsConfig runEcsConfig = RunEcsConfig.fromFile(objectMapper, args[0]);

        RunContainer runContainer = new RunContainer(runEcsConfig);
        runContainer.execute();

        System.exit(0);
    }

    private void execute() {

        String repositoryUri = getRepositoryUri(ecrClient, config.getImageName());

        Cluster cluster = getCluster(ecsClient, config.getClusterName());

        TaskDefinition definition = getTaskDefinition(repositoryUri);

        createS3Bucket(s3Client, config.getBucketName());

        runTask(ecsClient, cluster, definition);

        System.out.println("Find results in " +
                s3Client.generatePresignedUrl(config.getBucketName(), config.getOutputFilename(), new DateTime().plusHours(24).toDate()).toString() +
                "\n\nThese results will be deleted at " + new DateTime().plusHours(24).toString());

    }

    private static void usage() throws JsonProcessingException {
        StringBuilder sb = new StringBuilder();
        sb.append("\nrun-ecs takes one argument, the filename of a JSON configuration file, an example of which follows.");
        sb.append("\nThe filename should be relative to the current directory.");
        sb.append("\n\nFor example,");
        sb.append("\n\n\tjava -jar build/libs/run-ecs.jar primes-config.json");
        sb.append("\n\nA sample JSON configuration might be");
        sb.append("\n\n").append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exampleConfig));
        sb.append("\n\nThe properties of this file are:");
        sb.append("\n\n\t* clusterName - the identifier used when creating the VPC");
        sb.append("\n\t* taskName - an identifier for this task. If a task definition with this name has already been created,");
        sb.append("\n\t\tit will be re-used with the original configuration, useful for instance if the docker image has been updated.");
        sb.append("\n\t* imageName - the name of the image repository, e.g. the value set as");
        sb.append("\n\t\tdef awsRepositoryName = \"my-eg/primes\" in the docker-primes project.");
        sb.append("\n\t\tThe :latest tagged version of this image will always be used.");
        sb.append("\n\t* bucketName - the name of an S3 bucket, within this AWS account, to write any output to.");
        sb.append("\n\t\tIf a bucket with this name doesn't exist, it will be created, with a 2 day deletion time.");
        sb.append("\n\t* outputFilename - the name of the output, when written to S3.");
        sb.append("\n\t* cpuPercentage - the percentage of a single instance CPU to reserve.");
        sb.append("\n\t* memoryUsage - the memory in Mb to allocate.");
        sb.append("\n\t* runEnvironment - environment variables to pass to the docker instance. In this example, ");
        sb.append("\n\t\tvariables TAKE_N, IGNORE_FIRST_N and TAKE_EVERY_N would be set.");
        sb.append("\n\n\nThe output from running this will include a URL to the bucket output, which can be shared");
        sb.append("\n without need for any AWS credentials.");
        System.out.println(sb);
    }

    private static String createS3Bucket(AmazonS3Client s3Client, String name) {
        if (s3Client.doesBucketExist(name))
            return name;

        s3Client.createBucket(name);
        s3Client.setBucketLifecycleConfiguration(
                name,
                new BucketLifecycleConfiguration()
                        .withRules(
                                new BucketLifecycleConfiguration.Rule()
                                        .withId("deletion")
                                        .withStatus(BucketLifecycleConfiguration.ENABLED)
                                        .withExpirationDate(
                                                new DateTime(DateTimeZone.UTC).plusDays(2).withTimeAtStartOfDay().toDate())));
        return name;
    }

    private static void runTask(AmazonECSClient client, Cluster cluster, TaskDefinition definition) {
        RunTaskResult tasks =
                client.runTask(
                        new RunTaskRequest()
                                .withTaskDefinition(definition.getTaskDefinitionArn())
                                .withCluster(cluster.getClusterArn())
                                .withCount(1));

        if (tasks.getTasks().size() != 1)
            throw new TaskNotStartedException("Tasks returned for request " + definition + " didn't start?" + tasks);

    }

    private TaskDefinition getTaskDefinition(String repositoryUri) {

        List<KeyValuePair> env = config.getEnvironment();

        TaskDefinition definition;
        try {
            definition = ecsClient.describeTaskDefinition(new DescribeTaskDefinitionRequest().withTaskDefinition(config.getTaskName())).getTaskDefinition();
        } catch (AmazonClientException ace) {
            definition = ecsClient.registerTaskDefinition(
                    new RegisterTaskDefinitionRequest()
                            .withFamily(config.getTaskName())
                            .withContainerDefinitions(
                                    new ContainerDefinition()
                                            .withCpu(config.getCpu())
                                            .withEnvironment(env)
                                            .withMemory(config.getMemoryUsage())
                                            .withName(config.getTaskName())
                                            .withImage(repositoryUri))).getTaskDefinition();
        }
        return definition;
    }

    private static Cluster getCluster(AmazonECSClient client, String clusterName) {
        List<Cluster> clusters = client.describeClusters(
                new DescribeClustersRequest()
                        .withClusters(clusterName)).getClusters();

        if (clusters.size() != 1)
            throw new NoClusterFoundException("Couldn't find cluster '" + clusterName + "'");

        return clusters.get(0);
    }

    private String getRepositoryUri(AmazonECRClient ecrClient, String repositoryName) {
        List<Repository> repositories;
        try {
            repositories = ecrClient.describeRepositories(
                    new DescribeRepositoriesRequest()
                            .withRepositoryNames(repositoryName))
                    .getRepositories();
        } catch (AmazonClientException ace) {
            throw new RepositoryNotFoundException("Couldn't find repository " + repositoryName);
        }

        if (repositories.size() != 1)
            throw new RepositoryNotFoundException("Couldn't find repository " + repositoryName);

        return repositories.get(0).getRepositoryUri();
    }

    private static class NoClusterFoundException extends RuntimeException {
        NoClusterFoundException(String msg) {
            super(msg);
        }
    }

    private static class TaskNotStartedException extends RuntimeException {
        TaskNotStartedException(String msg) {
            super(msg);
        }
    }

}
