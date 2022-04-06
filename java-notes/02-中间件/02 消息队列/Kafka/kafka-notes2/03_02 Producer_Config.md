[toc]
## Producer Config
### buffer.memory & max.block.ms
生产者的主线程，产生的消息，堆积在RecordAccumulator进行缓存，该参数设置缓存大小，默认32M，缓存装满之后，程序将阻塞，阻塞时间超过max.block.ms后，抛出异常

### batch.size 
ProducerBatch缓存大小，默认16KB   
消息在传输过程以字节数组存在，为了避免反复内存创建和释放，生产者内部有一个类似ByteBuffer缓存机制，当生产出的消息评估后，大小不超过batch.size设置参数值，该消息会生成ProducerBatch对象，且被复用，超过该大小，生成的ProducerBatch不会复用

### max.in.flight.requests.per.connection 
InFlightRequests中每个连接最多缓存多少个未响应的请求，默认值5

### metadata.max.age.ms 
元数据在未进行主动同步的情况下，在一定的时间间隔进行元数据同步的时间间隔，默认300000,5分钟   
除了在该定时状态同步，需要使用元数据时，客户端不存在所需的元数据，也会主动进行更新

### acks
指定分区中必须要有多少个副本收到消息，生产者才认为消息是发送成功的   
- 1 默认值，只要分区的leader副本收到消息，服务端就产生成功响应，此设置为可靠性与吞吐量的折中   
- 0 发出后，不待响应，最大吞吐量方案，无法保证可靠性
- -1 或 all, 所有ISR中的副本都收到消息，服务端才生成成功响应，为最强可靠性，但如果只有一个leader副本，则与设置为1无差异，可以结合min.insync.replicas参数确保最小的副本数   
注：该属性类型为字符串

### request.timeout.ms
指定请求发送给服务端后，响应的超时时间，默认30s
响应之所以超时，是服务端在同步消息给leader后，还可能同等待其他follower副本的拉取，消耗时长