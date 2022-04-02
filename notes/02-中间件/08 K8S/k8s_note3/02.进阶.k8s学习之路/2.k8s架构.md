# kubernetes笔记

## 思维导图

![原理图](https://github.com/cxdtotsj/K8S/blob/master/pic/%E6%80%9D%E7%BB%B4%E5%AF%BC%E5%9B%BE.png)

![总体组织结构](https://github.com/cxdtotsj/K8S/blob/master/pic/K8S%E6%80%BB%E4%BD%93%E7%BB%84%E7%BB%87%E7%BB%93%E6%9E%84.png)

## 概述

![架构图](https://github.com/cxdtotsj/K8S/blob/master/pic/k8s%E6%9E%B6%E6%9E%84%E5%9B%BE.jpg)

1. 五部分核心组件(API Server、Controller Manager、Scheduler、Kubelet、etcd)：
    - Pod: 最小单元，支持多容器网络共享，文件系统共享
    - Service: 访问Pod的代理抽象服务，用于集群的服务发现和负载均衡
    - Replication Controller: 用于伸缩Pod副本数量的组件
    - Scheduler: 集群中资源对象的调度控制器(控制Pod分配到哪个Node上)
    - Controller Manager: 集群资源对象管理同步的组件
    - etcd: 分布式键值对(k,v)存储服务，存储整个集群的状态信息
    - Kubelet: 负责维护Pod容器的生命周期(Node的管理控制器)
    - Label: 用于service及Replication Controller与Pod关联的标签

2. K8S环境中假设包含一个Master节点，若干个Node节点，一个简单的Pod工作流：
    1. 提交请求: 用户通常提交一个Yaml文件，向API Server发起请求创建一个Pod
    2. 资源状态同步: Replication组件监控数据库中的数据变化，对已投的Pod进行数量上的同步
    3. 资源分配: Scheduler会检查Etcd数据库中记录的没有被分配的Pod，将Pod分配至有运行能的Node节点中，并更新Etcd中Pod的分配情况
    4. 新建容器: kubernetes集群节点中的kubelet对Etcd数据库中Pod部署状态进行同步，目标节点上的kubelet将Pod相关Yaml文件中的spec数据递给后面的容器运行时引擎(如Docker等)，后者负责Pod容器的运行停止和更新；kubelet会通过容器运行时引擎获取Pod的状态并将信息通过API Server更新至Etcd数据亏
    5. 节点通讯: Kube-proxy负责各节点中的Pod的网络通讯，包括服务发现和负载均衡

## 各组件具体实现

![Pod工作流](https://github.com/cxdtotsj/K8S/blob/master/pic/Pod%E5%B7%A5%E4%BD%9C%E6%B5%81.jpg)

1. API Server
    1. 为整个Pod工作流提供了资源对象(Pod，Deployment，Service等)的增删改查以及用于集群管理的Rest API接口，集群管理主要包括: 认证授权，集群状态管理，数据校验等
    2. 提供集群中各组件的通信及交互的功能
    3. 提供资源配额控制入口功能（每个POD占用的CPU、内存等）
    4. 安全的访问控制机制

    ![API Server工作原理](https://github.com/cxdtotsj/K8S/blob/master/pic/API%20Server%E5%8E%9F%E7%90%86%E5%9B%BE.jpg)

    在kubernetes集群中，API Server运行在Master节点上，默认开放两个端口，分别为本地端口8080 (非认证或授权的http请求通过该端口访问API Server)和安全端口6443 (该端口用于接收https请求并且用于token文件或者客户端证书及HTTP Basic的认证，用于基于策略的授权)，kubernetes默认为不启动https安全访问控制。


    API Server负责各组件间的通信，Scheduler,Controller Manager,Kubelet 通过API Server将资源对象信息存入etcd中，当各组件需要这些数据时，又通过API Server的Rest接口去获取，以下分别说明:

    - Kubelet与API Server
        
        ![kubelet监听原理](https://github.com/cxdtotsj/K8S/blob/master/pic/kubelet%E7%9B%91%E5%90%AC%E5%8E%9F%E7%90%86.png)

    - kube-controller-manager与API Server

        Controller Manager包含许多控制器，例如Endpoint Controller、Replication Controller、Service Account Controller等, 具体会在后面Controller Manager部分说明，这些控制器通过API Server提供的接口去实时监控当前Kubernetes集群中每个资源对象的状态变化并将最新的信息保存在etcd中，当集群中发生各种故障导致系统发生变化时，各个控制器会从etcd中获取资源对象信息并尝试将系统状态修复至理想状态。
    
    - kube-Scheduler与API Server

        Scheduler通过API Server的Watch接口监听Master节点新建的Pod副本信息并检索所有符合该Pod要求的Node列表同时执行调度逻辑，成功后将Pod绑定在目标节点处
    
    由于在集群中各组件频繁对API Server进行访问，各组件采用了缓存机制来缓解请求量，各组件定时从API Server获取资源对象信息并将其保存在本地缓存中，所以组件大部分时间是通过访问缓存数据来获得信息的。

2. Controller Manager

    Controller Manager在Pod工作流中起着管理和控制整个集群的作用，主要对资源对象进行管理，当Node节点中运行的Pod对象或是Node自身发生意外或故障时，Controller Manager会及时发现并处理，以确保整个集群处于理想工作状态。

    ![manager工作原理](https://github.com/cxdtotsj/K8S/blob/master/pic/manager%E5%B7%A5%E4%BD%9C%E5%8E%9F%E7%90%86.jpg)

    - Replication Controller

        Replication Controller称为副本控制器，在Pod工作流中主要用于保证集群中Replication Controller所关联的Pod副本数始终保持在预期值，比如若发生节点故障的情况导致Pod被意外杀死，Replication Controller会重新调度保证集群仍然运行指定副本数，另外还可通过调整Replication Controller中spec.replicas属性值来实现扩容或缩容。
    
    - Endpoint Controller

        Endpoint用来表示kubernetes集群中Service对应的后端Pod副本的访问地址，Endpoint Controller则是用来生成和维护Endpoints对象的控制器，其主要负责监听Service和对应Pod副本变化。如果监测到Service被删除，则删除和该Service同名的Endpoints对象；如果监测到新的Service被创建或是被修改，则根据该Service信息获得相关的Pod列表，然后创建或更新对应的Endpoints对象；如果监测到Pod的事件，则更新它对应的Service的Endpoints对象。

3. Scheduler

    Scheduler在整个Pod工作流中负责调度Pod到具体的Node节点，Scheduler通过API Server监听Pod状态，如果有待调度的Pod，则根据Scheduler Controller中的预选策略和优选策略给各个预备Node节点打分排序，最后将Pod调度到分数最高的Node上，然后由Node中的Kubelet组件负责Pod的启停。当然如果部署的Pod指定了NodeName属性，Scheduler会根据NodeName属性值调度Pod到指定Node节点。
    整个调度流程分为两步:
    ![scheduler](https://github.com/cxdtotsj/K8S/blob/master/pic/scheduler.jpg)

    - 第一步预选策略（Predicates）

        预选策略的主要工作机制是遍历所有当前Kubernetes集群中的Node，按照具体的预选策略选出符合要求的Node列表；如果没有符合的Node节点，则该Pod会被暂时挂起，直到有Node节点能满足条件。通用的预选策略筛选规则有：PodFitsResources、PodFitsHostPorts、HostName、MatchNodeSelector。
    
    - 第二步优选策略（priorities）

        有了第一步预选策略的筛选后，再根据优选策略给待选Node节点打分，最终选择一个分值最高的节点去部署Pod。Kubernetes用一组优先级函数处理每一个待选的主机。每一个优先级函数会返回一个0-10的分数，分数越高表示节点越适应需求，同时每一个函数也会对应一个表示权重的值。最终主机的得分用以下公式计算得出：
        FinalScoreNode =（weight1 priorityFunc1）+（weight2 priorityFunc2）+ … +（weightn * priorityFuncn）

4. Kubelet

    简而言之，Kubelet在Pod工作流中是为保证它所在的节点Pod能够正常工作，核心为监听API Server，当发现节点的Pod配置发生变化则根据最新的配置执行相应的动作，保证Pod在理想的预期状态，其中对Pod进行启停更新操作用到的是容器运行时（Docker、Rocket、LXD）。另外Kubelet也负责Volume（CVI）和网络（CNI）的管理。