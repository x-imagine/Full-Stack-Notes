# Introduction
kafka：分布式流平台，非MQ   
安装包：kafka_scala版本-kafka版本.tgz   
如：kafka_2.11-2.4.0.tgz
依赖：zookeeper + jdk（scala是jdk开发）

## 核心概念
### 1.broker
Kafka服务代理节点，如果一台主机仅部署一个kafka，一个主机为一个broker，多个broker构成了Kafka集群。

### 2.topic
topic逻辑存在的一个概念，同一个topic可存在于多个broker；   
topic可由一个或多个partition组成，每个partition以int类型进行唯一编号；   
创建topic时可指定partition数量，也可在业务所需时修改partition数量，实现水平扩展。

### 3.partition
实际存储消息的单位，一个partition只能属于一个topic；   
partition是一个有序的队列，视为不断追加消息的日志文件，每个消息分配一个唯一的offset，用于记录其位置，称为偏移量；   
partition可以随topic一起跨多个broker存在；    
partition以副本机制进行管理，一个partition可存在1-n多个副本，但同时间只能有一个leader，其余为follower。

#### 3.1 partition replica 机制
- 同一编号的 partition 在不同 broker 内，包含相同的消息，多个包含相同消息的 partition 即为副本；   
- 多个副本以一主多从结构存在，即单个 leader 、多个 follower ；   
- leader 负责外部的读写，follower则仅与leader同步；   
- 绝对的相同时点，leader与follower并不能保证完全一致，因为同步过程需要时间；   
- leader所在的broker宕机，将从follower中选举新的leader，保证高可用；   
- 相关概念缩写：   
ISR（In-sync Replicas）：保持一定同步的副本集合，ISR为AR的子集，该集合副本有资格被选举为leader     
OSR（Out-of-sync Replicas）：滞后过多的副本集合，OSR为AR的子集，但正常情况为空    
AR（Assigned Replicas）：所有副本集合，AR = ISR + OSR，leader负责跟踪和维护replica在ISR或OSR   

#### 3.2 partition 中的变量
- LSO（LogStartOffset）:分区文件起始偏移量，即第一条消息所在的位置；   
- LEO(LogEndOffset):分区文件当前终止偏移量，即如果再产生一条消息时即将写入的位置；   
- HW（HighWatermark）:ISR副本分区中，最小的LogEndOffset的值，消费者消费时，仅能消费该offset以前的消息，不包括该位置；    
- LW（Low Watermark）：低水位，代表 AR 集合中最小的 logStartOffset 值。副本的拉取请求和删除消息请求能促使LW的增长； 
- LEO、HW作为关键的存在，体现了partition同步的逻辑，即多副本之间在持续同步，而同步最慢的副本，制约着消费者可获取的消息数量。   
如：   
生产者产生了10个消息：   
leader：LEO=11   
follower1：LEO=10   
follower2：LEO=8   
上述情况，leader的HW为8，消费者只能消费offset为：0-7的消息。follower2同步慢的情况，形成了该partition木桶的短板。同时，如果消息未在所有副本中完成同步，该消息将不被确认成功提交。     
该机制通过性能换取了可靠性，保障任意broker宕机，新的follower被选举为leader后，无数据丢失，也无重复消费。

### 4.offset
offset是partition中记录每条消息的偏移量，通过offset可保证顺序、唯一的获取消息；   
offset有效范围在单个partition内，同主题下的多partition无法保证顺序性，即分区有序，主题内无法保证消费消息的顺序。

### 5.consumer
consumer以poll模式从broker获取消息，且向kafka同步消息位置（offset），宕机后通过识别上次同步的消息位置，继续拉取消息，确保消息不丢失
- 消费组：消费组是逻辑概念，每个消费者都对应一个消费组，消费组下有一个或多个消费者，可有一个或多个消费组
- 消费者：实际应用的实例，归属于消费组，不同消费者可部署在不同的 broker
- 消费方式：消费组订阅的 topic 下的多个分区，分别交由消费组下的消费者消费，同一分区只能被组内的一个消费消费（即组内不能重复消费）
- 模型优点：消费者与消费组的模型，提供了消费者横向扩展的能力，如果分区增加，可以增加消费者数，提高处理能力，但如果消费者大于分区数，则部分消费者会闲置；如果系统中只有一个消费组，则可视为消息投递模式为点对点（P2P），多消费组则可视为发布订阅模式（Pub\Sub），

## 6.API
- AdminClientAPI：管理检测topic、broker等
- Producer API：发布消息到topic
- Consumer API：订阅topic、处理消息
- Stream API：输入流转输出流
- Connector API：系统或应用程序中拉取数据到kafka

## 7.Producer
- 写入机制：顺序写磁盘（高吞吐量），非随机写内存
- 机制：不是接到一条发一条，而是单位时间内批量发送，降低IO
- 分区原则：   
计算批次：不断创建批次，并向批次append记录，批次有大小限制，超出阀值，则进行发送，并创建新的批次   
计算分区：指定了partition，则直接写入，未指定，通过计算，决定消息进入哪一个partition，计算分区逻辑可自定义   
- 发送原理：直接发送leader节点；发送到哪个partition默认是伪随机，也可指定负载均衡策略；发送是异步发送，减少io，提升吞吐量

## 8.Consumer
- 单个分区的消息，只能由ConsumerGroup中的某个Consumer消费
- Consumer 从 partition 中消费的顺序，默认从头开始消费，也可以从最新消费，或自定义位置消费
- 非指定情况下，单个ConsumerGroup会消费所有partition中的消息，也可以指定仅消费某分区的消息，亦可以在消费途中暂停、恢复某分区的消费
- 比较高效的处理是partition与同组内consumer一一对应（多partition单个consumer，其本质仍为线性处理）
- 业务开发中，consumer端处理消息方式：   
一种以consumer为主体的多线程处理，一个线程一个consumer，处理一个或多个partition，consumer线程内部为阻塞式的消息处理；对partiton的管控比较好，可单独提交partiton的offset；   
另一种，以消息为主题的多线程处理，一个consumer获取数据后，数据的处理为线程池，将数据分发给线程池的不同线程进行处理，消息处理非阻塞；相对易于快速处理数据，但对partition管控能力差
没有固定的好坏之分，需要根据业务分析，选择或组合使用

## 9.消息传递保障机制
- 至少一次：只收到1次，可能多次收到
- 正好一次：收到1-n次，速度最慢，需要比对去重
- 最多一次：收到0-1次，只发不收，速度最快

--------------------------------------------------待整理----------------------------------------------------------------------
## consumer group对partition的rebalance机制
当存在多个partition，consumer组内的不同成员，需要平均分配消费任务，由协调器负责协调。   
1. 新进成员向协调器发心跳请求（心跳请求长期存在，除非退出或崩溃）
2. 协调员接到请求，对现有成员发送新成员入场消息，通知需rebalance，成员提交offset
3. 协调员根据partition和组内成员情况，重新分配负载，再次执行消费
4. 期间当有成员离开或崩溃（无心跳），以同样方式重新rebalance
和生产队种地差不多，一共12陇地，开始2个人，一人6陇（或其他算法），又加入2人，一人3陇，病倒1个，一人4陇，离职一个，又一人6陇。   
问题：当协调员通知重新分配时，各路consumer成员同步offset，但如有同步失败的，再重新分配后可能产生重复消费问题。

## Stream


## springboot的集成与启动
1. 集成：https://www.cnblogs.com/toutou/archive/2019/10/07/11354330.html   
2. 启动：KafkaListenerEndpointRegistry在SpringIOC中已经被注册为Bean，通过该类完成启动
- 随容器启动：自定义ApplicationRunner接口，注入KafkaListenerEndpointRegistry实例，在run方法start监听
```
    @Resource
    private KafkaListenerEndpointRegistry registry;
    
    public void run(ApplicationArguments args) throws Exception {
        registry.getListenerContainerIds().forEach(id -> {
            if(registry.getListenerContainer(id).isRunning()) {
                registry.getListenerContainer(id).start();
            }
        });
    }
    
```
- 定时启动：创建一个@Component类，同时@EnableScheduling
```
    //定时器，每天凌晨0点开启监听
    @Scheduled(cron = "0 0 0 * * ?")
    public void startListener() {
        //判断监听容器是否启动，未启动则将其启动
        if (!registry.getListenerContainer("id").isRunning()) {
            registry.getListenerContainer("id").start();
        }
        registry.getListenerContainer("id").resume();
    }

    //定时器，每天早上10点关闭监听
    @Scheduled(cron = "0 0 10 * * ?")
    public void shutDownListener() {
        registry.getListenerContainer("id").pause();
    }
```
