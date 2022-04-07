# 一、工程构建
- maven工程，pom.xml引入依赖
```xml
<dependency>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-starter</artifactId>
	<version>2.1.2.RELEASE</version>
</dependency>
<dependency>
	<groupId>org.apache.kafka</groupId>
	<artifactId>kafka-clients</artifactId>
	<version>2.6.0</version>
</dependency>
```
- logback-test.xml   
过滤掉过多日志
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration debug="false">
    <!-- definition of appender STDOUT -->
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%-4relative [%thread] %-5level %logger{35} - %msg %n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <!-- appender referenced after it is defined -->
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```
# 二、 AdminClientAPI
## 1.用途
Kafka管理客户端API，实现topic、broker等各类对象的管理。
对应源码包：com.dce.kafka.sample.api.admin

## 2.获得AdminClient
对kafka任何对象的管理，均需要首先获取AdminClient。
```java
    /**
     * 获取AdminClient
     * @return AdminClient
     */
    public static AdminClient getAdminClient() {
        Properties properties = new Properties();
        properties.setProperty(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, host_port);
        LOGGER.info("============================getAdminClient from " + host_port + "====================================");
        return AdminClient.create(properties);
    }
```
1. AdminClient.create()接受Properties、Map作为入参，结果一致；
2. AdminClientConfig包括需要配置的各类属性常量，避免变量拼错。

## 3.Topic操作
### 创建
```java
    public static void createTopic(String topicName) {
        AdminClient adminClient = AdminClientFactory.getAdminClient();
        short partitionsNum = 1;
        short refactor = 1;
        NewTopic newTopic = new NewTopic(topicName, partitionsNum, refactor);
        CreateTopicsResult topics = adminClient.createTopics(Arrays.asList(newTopic));
        LOGGER.info("--------------   topics   -------------------- :" + topics);
    }
```
### 查看
```java
    public static void topicList() throws ExecutionException, InterruptedException {
        AdminClient adminClient = AdminClientFactory.getAdminClient();
        // 包含一个KafkaFuture，用于异步发送请求之后等待操作结果，支持链式调用以及其他异步编程模型
        ListTopicsResult listTopicsResult = adminClient.listTopics();
        LOGGER.info("--------------   topic size   -------------------- :" + listTopicsResult.names().get().size());
        listTopicsResult.names().get().stream().forEach(s -> {
            LOGGER.info("--------------   topic name   -------------------- :" + s);
        });

        ListTopicsOptions listTopicsOptions = new ListTopicsOptions();
        // 是否需要内部Topic
        listTopicsOptions.listInternal(true);
        // 包含timeoutMs这个成员变量，用来设定请求的超时时间
        listTopicsResult = adminClient.listTopics(listTopicsOptions);
        listTopicsResult.listings().get().stream().forEach(topic -> {
            LOGGER.info("--------------   topic list   -------------------- " + topic);
        });
    }
```
### 删除
```java
    public static void delTopic(String topicName) throws ExecutionException, InterruptedException {
        AdminClient adminClient = AdminClientFactory.getAdminClient();
        adminClient.deleteTopics(Arrays.asList(topicName));
        ListTopicsResult listTopicsResult = adminClient.listTopics();
        LOGGER.info("--------------   topic size   -------------------- :" + listTopicsResult.names().get().size());
        listTopicsResult.names().get().stream().forEach(s -> {
            LOGGER.info("--------------   topic name   -------------------- :" + s);
        });
    }
```