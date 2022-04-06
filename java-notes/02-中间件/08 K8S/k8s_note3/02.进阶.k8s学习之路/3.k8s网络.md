# Service

1. Pod的IP在重启或者扩容时，会发生变化，如何保证一个Pod(frontend)访问另外一组的Pod(backend)，这里有一个service的概念。

2. Service的访问，分为集群内部(Pod)和集群外部(Internet)，为了满足场景，有三张类型:
    - ClusterIP: 提供一个集群内部的虚拟IP一共Pod访问
    - NodePort: 在每个Node上打开一个端口以供外部访问
    - LoadBalancer: 通过外部的负载均衡器来访问


# K8S的几种网络通信

- 容器间通信
- Pod间通信
- Service间通信
- 跨节点通信
- 跨集群通信

1. Container-to-Container

    `kubernetes`不会直接操作容器本身，对外呈现的最小操作单位是`Pod`，这里容器之间的通信是指一个`Pod`内的容器之间如何通信。我们观察一个`Pod`里的资源时，会发现有一个自己启动的`pause`容器，这个容器的作用是为`Pod`提供网络，所有其他的容器(也就是我们通过模板启动的容器)，都是共享`pause`容器网络的。在实现上，如Docker提供了`--net=container:ID`这样的选项。
    每个`pod`对外呈现一个唯一的IP，`Pod`内的各容器共享这个IP，也就是说各容器处于同一网络空间(namespace)中，可直接使用`localhost`互相访问，需要注意的是各容器使用的端口不能冲突，可以认为`IP:Port`能够唯一确定一个容器。类比一个系统内的多个网络进程。

2. Pod-to-Pod

    `kubernetes`网络实现基于以下三个原则：

    - 所有容器和其他容器的通信不需要NAT
    - 所有`node`和容器间的通信不需要NAT
    - 容器自己看到的IP就是对外呈现的IP

    那么，这是如何做到的呢？在同一`node`时，是通过容器网桥实现的，如Docker默认使用的网桥`Docker0`；在跨`node`通信时，是通过`node`间的`overlay network`实现互联的，如`flannel`, `OpenVSwitch`提供的`VxLAN tunnel`。官方列举了更多的`overlay network`实现，[参考链接](https://kubernetes.io/docs/concepts/cluster-administration/networking/)。

3. Pod-to-Service

    作为前端的`Service`与作为后端的`Pods`之间是通过`kubernetes`的组件`kube-proxy`实现通信的。`kube-proxy`为`Service`实现了虚拟IP的形式，即`ClusterIP`。有两种代理模式，`userspace`和`iptables`。

    - `userspace`模式下，需要在本`node`上分配`proxy port`，该`port`被代理到后端的`Pods`，这样来自`ClusterIP:Port`的信息被重定向到`proxy port`，然后代理到后端的`Pods`。
    - `iptables`模式下，`kube-proxy`通过直接增删`iptables`规则实现重定向转发。它比`userspace`模式更快更可靠，缺点是当前`Pod`无响应时，它无法自动重试其他`Pod`，需要借助`readiness probes`实现。


# K8S网络基础

1. 容器间通信

    同一个Pod的容器共享同一个网络命名空间，它们之间的访问可以用localhost地址 + 容器端口就可以访问。

    ![容器间通信](https://github.com/cxdtotsj/K8S/blob/master/pic/%E5%AE%B9%E5%99%A8%E9%97%B4%E9%80%9A%E4%BF%A1.jpg)

2. 同一Node中Pod间通信

    同一Node中Pod的默认路由都是docker0的地址，由于它们关联在同一个docker0网桥上，地址网段相同，所有它们之间应当是能直接通信的。

    ![同一Node中Pod通信](https://github.com/cxdtotsj/K8S/blob/master/pic/%E5%90%8C%E4%B8%80node%E4%B8%ADPod%E9%80%9A%E4%BF%A1.jpg)

3. 不同Node中Pod间通信

    不同Node中Pod间通信要满足2个条件： Pod的IP不能冲突； 将Pod的IP和所在的Node的IP关联起来，通过这个关联让Pod可以互相访问。(Flannel，Calico)

    ![不同Node间Pod通信](https://github.com/cxdtotsj/K8S/blob/master/pic/%E4%B8%8D%E5%90%8CNode%E4%B8%ADPod%E9%80%9A%E4%BF%A1.jpg)

