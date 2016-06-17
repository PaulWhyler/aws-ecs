package org.pmwhyle.eg.ecs;

import com.amazonaws.services.ecs.model.KeyValuePair;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CaseFormat;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A value class to represent the configuration of a task, running a docker
 * instance on an ECS setup.
 */
class RunEcsConfig {

    private String clusterName;
    private String taskName;
    private String imageName;
    private String bucketName;
    private String outputFilename;
    private int cpuPercentage;
    private int memoryUsage;
    private HashMap<String, Object> runEnvironment;

    public RunEcsConfig() {
    }

    private RunEcsConfig(String clusterName, String taskName, String imageName, String bucketName, String outputFilename, int cpuPercentage, int memoryUsage, HashMap<String, Object> runEnvironment) {
        this.clusterName = clusterName;
        this.taskName = taskName;
        this.imageName = imageName;
        this.bucketName = bucketName;
        this.outputFilename = outputFilename;
        this.cpuPercentage = cpuPercentage;
        this.memoryUsage = memoryUsage;
        this.runEnvironment = runEnvironment;
    }

    private static List<KeyValuePair> convertToKeyValuePairs(Map<String, Object> envMap) {
        return envMap.entrySet().stream()
                .map(e -> new KeyValuePair().withName(convertToKebabCase(e.getKey())).withValue(coerce(e.getValue())))
                .collect(Collectors.toList());
    }

    private static String convertToKebabCase(String key) {
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, key);
    }

    private static String coerce(Object value) {
        if (value instanceof String)
            return (String) value;
        else if (value instanceof Integer)
            return Integer.toString((Integer) value);
        else
            throw new RunContainerConfigFileException(value);
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getImageName() {
        return imageName;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getOutputFilename() {
        return outputFilename;
    }

    public int getCpuPercentage() {
        return cpuPercentage;
    }

    public int getMemoryUsage() {
        return memoryUsage;
    }

    int getCpu() {
        return getCpuPercentage() * 1024 / 100;
    }

    public HashMap<String, Object> getRunEnvironment() {
        return runEnvironment;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public void setOutputFilename(String outputFilename) {
        this.outputFilename = outputFilename;
    }

    public void setCpuPercentage(int cpuPercentage) {
        this.cpuPercentage = cpuPercentage;
    }

    public void setMemoryUsage(int memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public void setRunEnvironment(HashMap<String, Object> runEnvironment) {
        this.runEnvironment = runEnvironment;
    }

    List<KeyValuePair> getEnvironment() {
        List<KeyValuePair> keyValuePairs = convertToKeyValuePairs(runEnvironment);
        keyValuePairs.add(new KeyValuePair().withName("S3_BUCKET").withValue(getBucketName()));
        keyValuePairs.add(new KeyValuePair().withName("S3_OUTPUT_KEY").withValue(getOutputFilename()));
        return keyValuePairs;
    }


    static RunEcsConfig example() {
        return new RunEcsConfig(
                "my-eg",
                "my-task",
                "my-eg/primes",
                UUID.randomUUID().toString(),
                "primes-output.txt",
                50,
                128,
                new HashMap<String, Object>() {{
                    put("takeN", 100);
                    put("takeEveryN", 100);
                    put("ignoreFirstN", 100);
                }}
        );
    }

    static RunEcsConfig fromFile(ObjectMapper objectMapper, String arg) {
        try {
            return objectMapper.readValue(new File(arg), new TypeReference<RunEcsConfig>(){});
        } catch (IOException e) {
            throw new RunContainerConfigFileException(arg, e);
        }
    }

    private static class RunContainerConfigFileException extends RuntimeException {

        RunContainerConfigFileException(String filename, IOException e) {
            super("Couldn't parse config file " + filename, e);
        }

        RunContainerConfigFileException(Object value) {
            super("Couldn't parse config file, the value " + value + " of type " +
                    value.getClass() + " \ncouldn't be converted into a String representation of an integer.");
        }
    }
}
