# Redis Persistence

<nav>
<a href="#一数据持久化">一、数据持久化</a><br/>
<a href="#二RDB-机制">二、RDB 机制</a><br/>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="#21-手动触发">2.1 手动触发</a><br/>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="#22-自动触发">2.2 自动触发</a><br/>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="#23-相关配置">2.3 相关配置</a><br/>
<a href="#三AOF-机制">三、AOF 机制</a><br/>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="#31-执行原理">3.1 执行原理</a><br/>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="#32-同步策略">3.2 同步策略</a><br/>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="#33-相关配置">3.3 相关配置</a><br/>
<a href="#四对比分析">四、对比分析</a><br/>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="#41-优点与缺点">4.1 优点与缺点</a><br/>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="#42-使用建议">4.2 使用建议</a><br/>
</nav>

## 一、数据持久化

默认情况下 Redis  的数据都是保存在内存中，为避免 Redis 进程意外退出而导致数据丢失的问题，Redis 提供了 RDB 和 AOF 两种方式来实现数据的持久化存储。

## 二、RDB 机制

RDB 机制是以指定的时间间隔将 Redis 中的数据生成快照并保存到硬盘中，它更适合于定时备份数据的应用场景。可以通过手动或者自动的方式来触发 RDB 机制：

### 2.1 手动触发

可以通过以下两种方式来手动触发 RDB 机制：

+ **save** ：save 命令会阻塞当前 Redis 服务，直到 RDB 备份过程完成，在这个时间内，客户端的所有查询都会被阻塞；
+ **bgsave** ：Redis 进程会 fork 出一个子进程，阻塞只会发生在 fork 阶段，之后持久化的操作则由子进程来完成。

### 2.2 自动触发

除了手动使用命令触发外，在某些场景下也会自动触发 Redis 的 RDB 机制：

+ 在 `redis.conf` 中配置了 `save m n` ，表示如果在 m 秒内存在了 n 次修改操作时，则自动触发 `bgsave`;
+ 如果从节点执行全量复制操作，则主节点自动执行 `bgsave`，并将生成的 RDB 文件发送给从节点；
+ 执行 `debug reload` 命令重新加载 Redis 时，会触发 `save` 操作；
+ 执行 `shutdown` 命令时候，如果没有启用 AOF 持久化则默认采用 `bgsave ` 进行持久化。

### 2.3 相关配置

**1. 文件目录**

RDB 文件默认保存在 Redis 的工作目录下，默认文件名为 `dump.rdb`，可以通过静态或动态方式修改：

+ 静态配置：通过修改 `redis.conf` 中的工作目录 `dir` 和数据库存储文件名 `dbfilename` 两个配置；

+ 动态修改：通过在命令行中执行以下命令：

  ```shell
  config set dir{newDir}
  config set dbfilename{newFileName}
  ```

**2. 压缩算法**

Redis 默认采用 LZF 算法对生成的 RDB 文件做压缩处理， 这样可以减少占用空间和网络传输的数据量，但是压缩过程会耗费 CPU 的计算资源， 你可以按照实际情况，选择是否启用。可以通过修改 `redis.conf` 中的 `rdbcompression` 配置或使用以下命令来进行动态修改： 

```shell
config set rdbcompression{yes|no}
```

## 三、AOF 机制

AOF 是 Redis 提供的另外一种持久化的方式，它以独立日志的方式记录每次写入操作，重启时再重新执行这些操作，从而达到恢复数据的命令。

### 3.1 执行原理

开启 AOF 机制后，所有的写入命令都会追加到 aof_buf 缓冲区中，并按照指定的策略定时将缓冲区中的数据同步到磁盘上。 AOF 除了记录每条命令外，还会在适当的时候 fork 出一个子进程对 AOF 文件进行重写，在重写过程中，Redis 会将可以合并的语句进行合并，将无效的语句进行删除，从而减小 AOF 文件的体积，以便减少文件的占用空间和方便在数据恢复时能够更快的进行加载。

### 3.2 同步策略

Redis 提供了三种同步策略，用于控制 AOF 缓冲区同步数据到磁盘上的行为，由参数 `appendfsync` 控制：

| 可选配置 | 说明                                                         |
| -------- | ------------------------------------------------------------ |
| **always**   | 命令写入 aof_buf 后就调系统 fsync 操作同步到 AOF 文件        |
| **everysec** | 命令写入 aof_buf 后就调用系统的 write 操作，但 fsync 同步文件的操作则由专门线程每秒调用一次 |
| **no**       | 命令写入 aof_buf 后就调用系统的 write 操作，不对 AOF 文件做 fsync 同步，同步操作由操作系统负责，通常同步周期最长为30秒 |

write 和 fsync 操作说明：

- write 操作会触发延迟写机制，Linux 在内核提供页缓冲区用来提高硬盘的 IO 性能，write 操作在写入系统缓冲区后直接返回。同步操作依赖于系统调度机制，例如缓冲区页空间写满或达到特定时间周期。 同步文件之前，如果此时系统故障宕机，缓冲区内数据将丢失。 
- fsync 针对单个文件操作，做强制硬盘同步，fsync 操作将阻塞直到写入硬盘完成后返回，它保证了数据持久化的安全。 

Redis 默认的同步机制为 `everysec`，此时能够兼顾性能和保证数据安全，在发生意外宕机的时，最多会丢失一秒的数据。

### 3.3 相关配置

想要使用 AOF 功能，需要配置 `appendonly ` 的值为 `yes`，默认值为 `no`。默认 AOF 的文件名为 `appendonly.aof`, 可以通过修改`appendfilename` 的值进行修改，和 RDB 文件的保存位置一样，默认保存在 Redis 的工作目录下。

## 四、对比分析

### 4.1 优点与缺点

#### RDB 的优点

- RDB 使用一次性生成内存快照的方式， 产生的文件紧凑压缩比更高， 适用于备份和全量复制等场景。
- RDB 文件通常比同一数据集的等效 AOF 文件小，所以使用 RDB 恢复数据远远快于 AOF 方式。
- RDB 最大限度地提高了 Redis 的性能，因为 Redis 父进程只需要 fork 出一个子进程，它本生并不会执行磁盘 I/O 等操作。

#### RDB 的缺点

- RDB 方式没办法做到数据的实时持久化，假设每次持久化的时间间隔是 5 分钟，当在上一次持久化后 3 分钟后发生了服务宕机，则这三分钟内的数据会全部丢失。
- fork 操作是一个重量级的操作，如果数据集很大，Fork 操作可能会非常耗时。

#### AOF 的优点

+ AOF 能够实现实时或秒级的持久化操作，能够保证数据的最少丢失。
+ 如果突然宕机，日志以半写命令结束，可以使用 redis-check-aof 工具进行修复，从而保证数据最少丢失。

#### AOF 的缺点

+ AOF 文件通常比同一数据集等效的 RDB 文件大。
+ 根据选择的同步策略的不同，AOF 可能比 RDB 还慢。

### 4.2 使用建议

按照 Redis 官方的推荐，为保证的数据安全性，可以同时使用这两种持久化机制，在 Redis 官方的长期计划里面，未来可能会将 AOF 和 RDB 统一为单一持久化模型。需要注意的是，在这种情况下，当 Redis 重新启动时，Redis 将使用 AOF 文件重建数据集，因为它可以保证数据的最少丢失。

混合持久化    
使用 RDB 做冷备，会丢失大量数据；    
使用 AOF，则启动要花很长时间。     
混合持久化，同时使用 RDB 和 AOF，先加载 RDB 的内容，剩余丢失部分在用 AOF 文件加载。AOF 一般每隔一秒同步磁盘一次，最多丢失一秒的数据。想要不丢失，则使用消息队列。



## 参考资料

1. 付磊，张益军 . 《Redis 开发与运维》. 机械工业出版社 .  2017-3-1
2. 官方文档：[Redis Persistence](https://redis.io/topics/persistence)





