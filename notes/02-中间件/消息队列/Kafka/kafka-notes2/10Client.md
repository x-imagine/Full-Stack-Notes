# Client
## 一、客户端分区分配
消费者客户端通过partition.assignment.strategy设置消费者与主题之间分区分配策略   
策略包括：   
- org.apache.kafka.clients.consumer.RangeAssignor
- org.apache.kafka.clients.consumer.RoundRobinAssignor
- org.apache.kafka.clients.consumer.StickyAssignor   
可以设置一个或多个策略
### 1.RangeAssignor
RangeAssignor策略原理是按照分区数和消费者数进行整除运算获得一个跨度，按照跨度进行平均分配。   
简单理解就是对某主题的分区尽可能平分，如：   
例1：   
主题A下的分区数：10，p0-p9   
客户端消费者数：2，c0-c1   
则分配效果是：   
c0：p0 p1 p2 p3 p4   
c1：p5 p6 p7 p8 p9   
例2：   
主题A下的分区数：7，p0-p6     
客户端消费者数：3，c0-c2    
则分配效果是：   
c0：p0 p1 p2   
c1：p3 p4    
c2：p5 p6 

缺点：由于分配顺序是按照消费者名称的字典顺序分配，如果多个主题分区都不能被整除，那么字典顺序靠前的几个消费者，负载将更高，甚至过载。   
如：
上例2中的情况，如果存在相同的主题B、C、D，分区数均与A一致，则分配结果为   
c0：A的p0 p1 p2，B的p0 p1 p2，C的p0 p1 p2，共9个分区      
c1：A的p3 p4，B的p3 p4，C的p3 p4，共6个分区  
c2：A的p5 p6，B的p5 p6，C的p5 p6，共6个分区  

### 2.RoundRobinAssignor
RoundRobinAssignor是对消费组下的所有消费者及订阅的所有主题分区按照自定拍下，然后轮训方式将分区依次分给消费者   
例1：   
主题A下的分区数：5，p0-p4     
主题B下的分区数：5，p0-p4     
客户端消费者数：2，c0、c1，且均订阅了A、B主题 
则分配效果是：   
c0：A的p0 p2 p4 B的p1 p3   
c1：A的p1 p3    B的p0 p2 p4   
如上，在消费者都订阅了相同的主题情况下，大家像抓扑克牌一下分配分区，则大体是平均分配的，只会有无法整除的情况下有的消费者会多分配1个   

缺点：如果消费组内不同消费者订阅了不同的分区，则可能变成每个抓扑克牌的人在不同的牌堆里抓牌，仍导致分配不均   
例2：   
主题A下的分区数：5，p0-p4     
主题B下的分区数：5，p0-p4     
客户端消费者数：2，c0订阅了A、B主题，c1仅订阅了A主题
则分配效果是：   
c0：A的p0 p2 p4 B的p0 p1 p2 p4 p3   
c1：A的p1 p3  
又出现了分配不均，可能导致某消费者过载
### 3.StickyAssignor
StickyAssignor是一种有粘性、有记忆的策略，其宗旨有二：   
1.分区尽可能均匀分配   
2.分区尽可能与上次分配保持一致   
两者发生冲突，目标1优先于目标2   
例：
主题A有1个分区：AP0   
主题B有2个分区：BP0 BP1   
主题C有3个分区：CP0 CP1 CP2   
消费组中有3个消费者：      
C0订阅A   
C1订阅A B   
C2订阅A B C   
如果使用RoundRobinAssignor策略，结果：   
C0:AP0   
C1:BP0   
C2:BP1 CP0 CP1 CP2   
采用StickyAssignor分配   
C0:AP0
C1:BP0 BP1
C2:CP0 CP1 CP2
如果此时C0脱离消费组，则分配调整为   
C1:BP0 BP1 AP0 (BP0 BP1保持第一次分配不变)   
C2:CP0 CP1 CP2 (全都保持第一次分配不变)   
### 4.自定义分配策略
还可以通过实现partitionAssignor接口，进行自定义分配策略的定义，并配置在partition.assignment.strategy参数即可，场景较少，不悉数用法

## 二、再均衡
和生产队种地差不多，一共12陇地   
开始2个人，一人6陇（或其他算法）      
又加入2人，一人3陇   
病倒1个，一人4陇   
离职一个，又一人6陇   
### 场景
某些场景会触发再均衡   
- 有新消费者加入消费组
- 既有消费者退出消费组：下线、网络延时超过既定时间
- 既有消费者取消订阅主题
- 消费组对应的消费者协调器节点变化
- 消费组内消费者订阅的主题数量或主题内的分区数量发生变化     
其中：消费者协调器ConsumerCoordinator在消费者客户端，其负责与服务端的组协调器GroupCoordinator进行交互工作，两者主要职责就是再平衡的处理   
客户端将全部消费组分成多个子集，每个消费组的子集在服务端对应一个组协调器GroupCoordinator
### 流程
当客户端新增消费者时，如下处理流程
- 一阶段：连接服务端协调器阶段   
消费者先找到其所在组对应的GroupCoordinator所在的broker，与其建立连接（寻找方式是根据消费组groupId的哈希值，逐步寻找消费组对应的分区leader的broker节点）
- 二阶段：加入服务端协调器并选leader阶段，选分区策略   
加入该消费组对应的组协调器GroupCoordinator，选举该消费组的leader（随机选），leader负责干活，干啥活？要选举一个分配策略出来   
策略选举办法：   
1.收集所有消费者支持的策略（即partition.assignment.strategy配置的1个或多个策略），形成候选人   
2.每个消费者为自己的候选人投票，支持就+1   
3.比较所有候选人票数，获票最多的胜出   
异常：当选出的策略某个消费者不支持该策略，则报IllegalArgumentException：Member does not support protocol
- 三阶段：同步策略结果，启动心跳任务阶段   
leader将二阶段选举出的分配策略，通过GroupCoordinator同步给各个组内成员，完成信息同步后，开启心跳任务，消费者定时向GroupCoordinator发送心跳确定存活
- 四阶段：心跳监控阶段   
心跳作为独立线程，在轮询消息空挡发送心跳，如果心跳在足够时间内未发出，视为消费者死亡，触发一次再均衡   
心跳间隔参数：heartbeat.interval.ms，默认3000ms，其值不得超过session.timeout.ms的1/3   
session.timeout.ms需在group.min.session.timeout.ms（6s）和group.max.session.timeout.ms（300s）之间，超过session.timeout.ms则触发再均衡   
max.poll.interval.ms指定消费者组调用poll方法拉取消息的最大延迟，超过此时间，消费者被认为失败，触发再均衡   

## 三、消息传输机制
消息传输在保障机制上一般有3个层级
### 1.at most once
最多一次，即便消息丢失或使用途中宕机等，均不再重复传输     
### 2.at least once
至少一次，可能因未及时收到响应，而传输重复消息
### 3.exactly once
有且仅有一次，能保证每条消息成功送达一次

当生产者发送消息后，如果网络中断，则生产者无法感知是否已传输成功，故进行重试确保消息正常送达，但可能会重复写入   
当消费者处理消息时，处理消息与他提交offset的先后顺序，很大程度决定消费者提供哪种保障机制：   
1.如果拉取后，先处理消息，再提交offset，那么在提交offset时宕机了，重新启动后，会再次拉取已处理过的消息，造成重复消费，对应at least once   
2.如果拉取后，先提交offset，再处理消息，那么在处理消息时宕机了，重启后，不会再次拉取已同步offset的消息，造成消息遗漏，对应at most once

通过幂等性和事务，可实现exactly once   

## 四、幂等
幂等就是保障对接口多次重复调用产生的结果和一次调用产生的结果一致   
如生产者retry时的重复消息写入，可通过幂等性功能避免；但如果是相同的消息对象，客户端多次通过API发送，则为多条消息，与幂等性无关   
kafka的幂等性粒度对应到分区   
### 1.开启
打开生产者客户端参数enable.idempotence
```
    properties.put("enable.idempotence", true);
```
注：开启幂等性时，最好不显示的配置retries、acks、max.in.flight.requests.per.connection，原因是在开启幂等后：   
1.retries必须大于0，否则报ConfigException: Must set retries to non-zero when using the idempotent producer，该参数默认Integer.MAX_VALUE   
2.max.in.flight.requests.per.connection必须不大于5，否则报ConfigException，该参数默认为5   
3.acks必须配置为-1，即所有副本都获得消息，否则报ConfigException，该参数默认为-1   
所以，在不能开启幂等性的同时，上述参数能够正确配置的话，那么就直接不配置，用默认配置保证无异常   

### 2.原理
kafka通过producer id 和 sequence实现幂等
- 每个生产者在创建时会分配一个PID——producer id，此行为用户无感知，也不影响业务
- 每个PID消息发送到不同的分区，都会分配一个序列，类似oracle的sequence的递增序列
- 生产者每发送消息，会以PID+分区作为“主键”，把其对应的sequence加1，把所有信息提交至broker
- broker端会记录PID+分区已更新到的序列号，且称之为curSequenceNo，并在接收消息后检查当次提交的newSequenceNo   
1.当本次发送来的newSequenceNo比curSequenceNo大1，则接受   
2.当本次发送来的newSequenceNo比curSequenceNo小，则该消息已被处理，视为重复写入，不处理   
3.当本次发送来的newSequenceNo - curSequenceNo > 1，则表示接到的消息不是连续的，中间可能有消息丢失，生产者将抛出OutOfOrderSequenceException

## 五、事务
幂等性无法跨分区控制，事务可跨分区控制，与数据库的操作多表的事务一样，kafka事务可以保证多分区操作行为的原子性   
kafka的事务可以使程序对于多分区的消费消息、生产消息、提交offset的一系列操作作为一个原子性处理   
### 1.开启
在客户端设置transactionalId，对于一个客户端，其id为唯一值，transactionalId与PID一一对应   
```
 properties.put("transactional.id", "some_id");
```
同时，开启幂等性，若不开启幂等性则会抛出ConfigException   
```
    properties.put("enable.idempotence", true);
```
### 2.原理
对生产者而言：   
- 事务保证跨生产者会话的消息幂等发送   
当拥有相同transactionalId的实例被创建时，旧的实例将不再工作，如两个生产者使用同一个transactionalId，则先启动的生产者报ProducerFencedException   
- 跨生产者会话的事务恢复   
当某个生产者宕机，新的生产者可以保证未完成的旧事务被提交或回滚，之后，新的生产者从一个正常的状态开始继续

对消费者而言：   
由于消费者的特点，导致事务特性作用在消费者上的保证性较弱，如以下情形无法保证日志中的消息如数被消费者消费   
- 日志压缩：同key的消息部分会被清理
- 日志清除：老日志分段被删除
- seek访问：客户端通过seek()方法跳跃访问
- 消费者未分配事务内的分区，无法消费事务中该分区的消息

### 3.使用
与jdbc类似，kafka事务也提供了以下几个方法处理事务
```
    void initTransactions();
    void beginTransaction();
    void sendOffsetsToTransaction(Map<TopicaPartition, OffsetAndMataData> offsets, String consumerGroupId);
    void commitTransaction();
    void abortTransaction();
```
- void initTransactions()：初始化事务，若未配置transactionalId，报IllegalStateException
- void beginTransaction()：开启事务
- void sendOffsetsToTransaction()：负责事务内的offset提交
- void commitTransaction()：提交事务
- void abortTransaction()：终止（回滚）事务

### 4.隔离级别
与数据库隔离级别对应，kafka也有自己的隔离级别   
isolation.level 默认值为 "read_uncommitted"，另可修改"read_committed"   
1.read_uncommitted：生产者事务未提交时，亦可消费   
2.read_committed：生产者事务未提交时，消息只缓存在consumer客户端，直到生产者执行commitTransaction()方可消费

另外，在日志消息中，有一类控制消息ControlBatch，其包含commit、abort两类消息，KafkaConsumer通过该消息判断对应的事务是否被提交或回滚

