[toc]
## Topic Configs
topic参数在broker端都有对应参数，不显示设置，则采用broker参数默认

### max.message.bytes
消息允许的最大字节数，int，默认1048588

### cleanup.policy
通过topic的cleanup.policy，可以对主题级别进行日志清理设置，delete或compact