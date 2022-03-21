[toc]
## Consumer Config
### key.deserializer & value.deserializer
消息中key、value的反序列化类，需与producer中的序列化类对应

### bootstrap.servers
指定消费者连接的broker，可设置一个或多个，设置单个即可连接集群，但建议设置多个，避免单点宕机风险   

### group.id
consumer隶属消息组名称，默认""，设置为null会抛异常

### client.id
客户端id，如设定亦可，consumer会自动生成，如consumer-1

### enable.auto.commit
consumer客户端是否自动提交，默认为true，所谓的自动提交非每消费一条就提交一次，而是定期提交，提交周期根据另一个参数auto.commit.interval.ms所设置的时间决定，如果需要手动提交需要设置参数为false

### auto.commit.interval.ms
consumer自动提交时，提交的时间间隔，默认5秒。生效前提为enable.auto.commit设置true

### auto.offset.reset
获取不到offset时，consumer将读取auto.offset.reset参数，来指导自己offset的取值，auto.offset.reset取值
- latest（默认） ：从所分配分区的最末尾开始消费
- earliest ：从所分配分区的起始位置开始消费，起始位置为当前kafka中尚存在消息的低水位位置
- none ：配置为none，则不取起始，不取末尾，抛出异常：NoOffsetForPartitionException
- 为设置上述之一，则抛出ConfigException

### interceptor.classes
配置的拦截器类，需配置全路径

### fetch.min.bytes
- 消费者拉取消息时，单次获取最小的字节数，默认1B   
- 即当拉取消息小于参数值，就进行等待，指导消息达到或超出该水平，才完成拉取      
- 调大参数可提高数据处理的吞吐量，但也可能导致阻塞延迟，需结合实际业务设置 
- 范围为所有分区  

### fetch.max.bytes
- 消费者拉取消息时最大字节数，默认50M
- 可以理解为拉取消息返回的阈值，而非是否拉取的开关，如果单条消息已经超出最大字节数，仍然是可以拉取的，否则kafka将无法工作
- 传输过程中消息的大小限制，通过另一个参数：max.message.bytes来设置
- 范围为所有分区

### fetch.max.wait.ms
- 拉取消息等待的最长时间，单位毫秒，默认500ms
- 需要根据业务情况，与fetch.min.bytes结合配合使用
- 对时间敏感则调小参数

### max.partition.fetch.bytes
- 每个分区单次拉取消息的最大字节数，默认1M
- 单条消息大于该数值，也能拉取，同fetch.max.bytes类似

### max.poll.records
- 单次拉取消息的数量，默认500条
- 如果消息大小比较小，可调大该参数，提升消费速度

### connections.max.idle.ms
- 连接闲置多久后关闭，默认540000ms，9分钟

### exclude.internal.topics
- 是否公开内部主题，默认yes
- 内部主题包括：__consumer_offsets  __transaction_stat
- 订阅内部主题需要使用subscribe(Collection)方法

### receive.buffer.bytes
- 设置接收消息缓冲区（Socket）大小，默认64K
- consumer与kafka网络距离较远（部署在不同机房、地域），适当增大缓冲区大小
- 设置为-1，使用操作系统默认设置

### send.buffer.bytes
- 设置发送消息端缓冲区（Socket）大小，默认128K
- 设置为-1，使用操作系统默认设置

### request.timeout.ms
- Consumer等待请求响应最长时间，默认30000ms

### metadata.max.age.ms
- 元数据在一定时间内未被更新，则启动一次元数据更新操作，该参数设置时间，默认5分钟（300000ms）

### reconnect.backoff.ms
- 消费端尝试重新连接broker的等待时间，避免不停重来造成的服务端压力，默认50ms

### retry.backoff.ms
- 消费端向指定的主题、分区发送消息失败后，重试的间隔时间，默认100ms，与reconnect.backoff.ms类似，一个是连接，一个是发送消息

### isolation.level
- 消费者获取消息的隔离级别，类似数据库的事务隔离级别，类型为字符串
- read_committed :消费者获取的消息为事务已提交的消息，即消费到LSO（LastStableOffset）的消息
- read_uncommitted ：消费者获取所有消息，即消费到HW（HighWater）的消息

### heartbeat.interval.ms
消费者组内的消费者发送心跳到消费者协调器的时间，以监控消费者会话状态是否超时，有消费者在session.timeout.ms时间内未发送心跳则进行重新平衡分配   
通常情况该参数小于session.timeout.ms，不高于该值的三分之一
默认值3000ms

### session.timeout.ms & group.min.session.timeout.ms & group.max.session.timeout.ms
检查消费者是否失效，无心跳达到超时时间视为失效，默认10000ms   
session.timeout.ms需在group.min.session.timeout.ms（6s）和group.max.session.timeout.ms（300s）之间   
若消费者心跳超过参数时间未送达，协调器认为消费者死亡   

### max.poll.intervals.ms
消费组管理消费者时，消费者拉取消息的最大间隔时间，默认300000ms，若在该时间长度内未发起poll操作，视为该消费者离开消费组，需进行再均衡操作

### partition.assignment.strategy
消费者分区策略，默认RangeAssignor      
- org.apache.kafka.clients.consumer.RangeAssignor
- org.apache.kafka.clients.consumer.RoundRobinAssignor
- org.apache.kafka.clients.consumer.StickyAssignor 

### interceptor.class
消费者拦截器