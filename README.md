





## JVM线上问题排查

### 前言

项目运行中，难免会遇到一些问题，有时候cpu占用特别高，有时候内存特别高，出现这样的问题，我们该如何怎么解决，现在我们

就从下面示例代码来演示，如何定位到是哪个线程造成的，造成的原因是啥。



### 示例代码

```java
public class Deadlock {

    public static void main(String[] args) throws InterruptedException {
        Object o = new Object();

        new Thread(()->{
            synchronized (o) {
                System.out.println("T1:我要开始死锁啦....");
                int i = 0;
                while (true) {
                    i++;
                }
            }
        },"T1").start();

        //让T1先获取到锁
        TimeUnit.SECONDS.sleep(500);

        new Thread(()->{
            synchronized (o) {
                System.out.println("T2:我拿到锁啦....");
            }
        },"T2").start();
    }
}
```



代码很简单，就是让T1一直占有锁对象，不断对i进行累加，这段代码运行一段时间以后，由于死锁的存在，cpu会一直飙升。具体原因的排查我们接下来开始将

### 排查原因

我们按照下面的步骤开试找原因

**1.** 首先通过`top`命令来查看资源消耗的情况，找到程序对应的线程id

- 通过jps命令找到所有的java程序，通过类名我们知道进程pid是12687

```shell
[root@izwz99ykxbjgxi2xajonmiz local]# jps
17713 Jps
3226 Application
26699 QuorumPeerMain
12687 Deadlock
```

- 通过top命令查看pid12687资源消耗情况

```shell
[root@izwz99ykxbjgxi2xajonmiz local]# top
	PID USER      PR  NI    VIRT    RES    SHR S %CPU %MEM     TIME+ COMMAND                                                                                                                                                        
12687 root      20   0 1994740  24056  11456 S 44.2  1.3   2:39.42 java                                                                                                                                                           
20976 root      20   0  961880  60284  12152 R 29.9  3.2   0:00.90 hexo                                                                                                                                                           
20965 root      20   0  910204  23840  11348 S  5.0  1.3   0:00.15 node /usr/local   
```

我们可以看到CPU被占用了44.2%，这确实有点高啦，CPU占用高居不下，一般都是代码中产生了死锁，首先我们要明确这个。

**2.** 通过`top -Hp pid` 查看这个程序中所有运行的线程

```shell
[root@izwz99ykxbjgxi2xajonmiz local]# top -Hp 12687
  PID USER      PR  NI    VIRT    RES    SHR S %CPU %MEM     TIME+ COMMAND                                                                                                                                                        
12711 root      20   0 1995768  24132  11464 R 23.7  1.3   5:25.59 java                                                                                                                                                           
12700 root      20   0 1995768  24132  11464 S  0.3  1.3   0:00.33 java                                                                                                                                                           
12687 root      20   0 1995768  24132  11464 S  0.0  1.3   0:00.00 java                                                                                                                                                           
12688 root      20   0 1995768  24132  11464 S  0.0  1.3   0:00.11 java                                                                                                                                                           
12691 root      20   0 1995768  24132  11464 S  0.0  1.3   0:00.01 java                                                                                                                                                           
12693 root      20   0 1995768  24132  11464 S  0.0  1.3   0:00.00 java                                                                                                                                                           
12694 root      20   0 1995768  24132  11464 S  0.0  1.3   0:00.00 java                                                                                                                                                           
12696 root      20   0 1995768  24132  11464 S  0.0  1.3   0:00.00 java                                                                                                                                                           
12697 root      20   0 1995768  24132  11464 S  0.0  1.3   0:00.01 java                                                                                                                                                           
12698 root      20   0 1995768  24132  11464 S  0.0  1.3   0:00.03 java                                                                                                                                                           
12699 root      20   0 1995768  24132  11464 S  0.0  1.3   0:00.00 java                                                                                                                                                           
23766 root      20   0 1995768  24132  11464 S  0.0  1.3   0:00.00 java
```

我们程序里面其实只有三个线程，一个main，一个T1，T2 其余的都是程序自己运行的,可能是GC线程，其实都是JVM内部的线程，具体的我们不管，我们找到我们自己启动的线程就好。通过这个命令我们可以看到`pid=12711`这个线程占用CPU特别高，我们需要特别去查看一下这个线程的信息啦。

**3.** 通过`jstack pid`命令查询出程序中所有线程运行的具体情况，特别需要注意那些线程状态为`WAITING`的

```shell
[root@izwz99ykxbjgxi2xajonmiz local]# jstack 12687
2020-07-08 11:29:25
Full thread dump Java HotSpot(TM) 64-Bit Server VM (25.201-b09 mixed mode):

"Attach Listener" #11 daemon prio=9 os_prio=0 tid=0x00007f37d0001000 nid=0xa6d waiting on condition [0x0000000000000000]
   java.lang.Thread.State: RUNNABLE

"DestroyJavaVM" #10 prio=5 os_prio=0 tid=0x00007f37f8009000 nid=0x3190 waiting on condition [0x0000000000000000]
   java.lang.Thread.State: RUNNABLE

"T2" #9 prio=5 os_prio=0 tid=0x00007f37f80f6800 nid=0x5cd6 waiting for monitor entry [0x00007f37fd81b000]
   java.lang.Thread.State: BLOCKED (on object monitor)
	at cn.isuyu.jvm.tuning.Deadlock.lambda$main$1(Deadlock.java:31)
	- waiting to lock <0x00000000ffbe6b98> (a java.lang.Object)
	at cn.isuyu.jvm.tuning.Deadlock$$Lambda$2/531885035.run(Unknown Source)
	at java.lang.Thread.run(Thread.java:748)

"T1" #8 prio=5 os_prio=0 tid=0x00007f37f80f4800 nid=0x31a7 runnable [0x00007f37fd91c000]
   java.lang.Thread.State: RUNNABLE
	at cn.isuyu.jvm.tuning.Deadlock.lambda$main$0(Deadlock.java:21)
	- locked <0x00000000ffbe6b98> (a java.lang.Object)
	at cn.isuyu.jvm.tuning.Deadlock$$Lambda$1/834600351.run(Unknown Source)
	at java.lang.Thread.run(Thread.java:748)

"Service Thread" #7 daemon prio=9 os_prio=0 tid=0x00007f37f80b4000 nid=0x319b runnable [0x0000000000000000]
   java.lang.Thread.State: RUNNABLE

"C1 CompilerThread1" #6 daemon prio=9 os_prio=0 tid=0x00007f37f80b1000 nid=0x319a waiting on condition [0x0000000000000000]
   java.lang.Thread.State: RUNNABLE

"C2 CompilerThread0" #5 daemon prio=9 os_prio=0 tid=0x00007f37f80ae800 nid=0x3199 waiting on condition [0x0000000000000000]
   java.lang.Thread.State: RUNNABLE

"Signal Dispatcher" #4 daemon prio=9 os_prio=0 tid=0x00007f37f80ad000 nid=0x3198 runnable [0x0000000000000000]
   java.lang.Thread.State: RUNNABLE

"Finalizer" #3 daemon prio=8 os_prio=0 tid=0x00007f37f807a000 nid=0x3196 in Object.wait() [0x00007f37fdf22000]
   java.lang.Thread.State: WAITING (on object monitor)
	at java.lang.Object.wait(Native Method)
	- waiting on <0x00000000ffbe5fe0> (a java.lang.ref.ReferenceQueue$Lock)
	at java.lang.ref.ReferenceQueue.remove(ReferenceQueue.java:144)
	- locked <0x00000000ffbe5fe0> (a java.lang.ref.ReferenceQueue$Lock)
	at java.lang.ref.ReferenceQueue.remove(ReferenceQueue.java:165)
	at java.lang.ref.Finalizer$FinalizerThread.run(Finalizer.java:216)

"Reference Handler" #2 daemon prio=10 os_prio=0 tid=0x00007f37f8077800 nid=0x3195 in Object.wait() [0x00007f37fe023000]
   java.lang.Thread.State: WAITING (on object monitor)
	at java.lang.Object.wait(Native Method)
	- waiting on <0x00000000ffbe6198> (a java.lang.ref.Reference$Lock)
	at java.lang.Object.wait(Object.java:502)
	at java.lang.ref.Reference.tryHandlePending(Reference.java:191)
	- locked <0x00000000ffbe6198> (a java.lang.ref.Reference$Lock)
	at java.lang.ref.Reference$ReferenceHandler.run(Reference.java:153)

```

我们找到我们需要关注的线程，其他的先不管，我们可以看到T1处于运行状态，T2被阻塞了，我们可以看到他们都监控的是一个对象

`0x00007f37fd81b000`, 从这里我们可以知道T2一直在等T1释放锁，我们过一段时间再看，执行一下`jstack`命令，会发现T2还是阻塞状态。我们就可以确定一定是T1线程这里发生了死锁，具体我们就需要去T1线程里面找原因啦。

```shell
"T2" #9 prio=5 os_prio=0 tid=0x00007f37f80f6800 nid=0x5cd6 waiting for monitor entry [0x00007f37fd81b000]
   java.lang.Thread.State: BLOCKED (on object monitor)
	at cn.isuyu.jvm.tuning.Deadlock.lambda$main$1(Deadlock.java:31)
	- waiting to lock <0x00000000ffbe6b98> (a java.lang.Object)
	at cn.isuyu.jvm.tuning.Deadlock$$Lambda$2/531885035.run(Unknown Source)
	at java.lang.Thread.run(Thread.java:748)

"T1" #8 prio=5 os_prio=0 tid=0x00007f37f80f4800 nid=0x31a7 runnable [0x00007f37fd91c000]
   java.lang.Thread.State: RUNNABLE
	at cn.isuyu.jvm.tuning.Deadlock.lambda$main$0(Deadlock.java:21)
	- locked <0x00000000ffbe6b98> (a java.lang.Object)
	at cn.isuyu.jvm.tuning.Deadlock$$Lambda$1/834600351.run(Unknown Source)
	at java.lang.Thread.run(Thread.java:748)
```



通过上面信息线程运行在哪个类的第几行我们都可以知道，`at cn.isuyu.jvm.tuning.Deadlock.lambda$main$0(Deadlock.java:21)`我们这里这是一个超级简单的死锁例子，让我们去代码中找问题，那太容易，但实际的开发中，业务不知道要比这个难多少啦，

我们还需要进行下面这些流程继续找问题

**4.** 使用`jmap -histo pid`查询程序中，内存占用比较多的对象

```shell
[root@izwz99ykxbjgxi2xajonmiz ~]# jmap -histo 12687 | head -20

 num     #instances         #bytes  class name
----------------------------------------------
   1:          1940         155528  [C  //char数组
   2:           269          82496  [I //int数组
   3:           654          74752  java.lang.Class
   4:            88          45472  [B //byte数组
   5:          1800          43200  java.lang.String
   6:           833          37024  [Ljava.lang.Object;
   7:           147          12936  java.lang.reflect.Method
   8:           227          12712  jdk.internal.org.objectweb.asm.Item
   9:           221          12376  java.lang.invoke.MemberName
  10:           359          10792  [Ljava.lang.Class;
  11:           212           8480  java.lang.invoke.MethodType
  12:           259           8288  java.util.concurrent.ConcurrentHashMap$Node
  13:           111           7992  java.lang.reflect.Field
  14:           225           7200  java.lang.invoke.LambdaForm$Name
  15:           178           7120  java.lang.ref.SoftReference
  16:           212           6784  java.lang.invoke.MethodType$ConcurrentWeakInternSet$WeakEntry
  17:           108           6480  [Ljava.lang.ref.SoftReference;
```

有些时候内存占用特别高的时候，我们用这个jmap命令就能一下找到哪个对象占用内存最多，具体去这个类找原因。



//也可以使用图形界面来看，不过要先开启协议,这个会拉低程序的效率，我们生产环境肯定不能使用这种方式来做，都需要通过

在线命令排查或使用阿里的`arthas`,图形界面只会在项目上线测试时会用到图形界面。



### 总结

- 首先通过`top`命令找到当前程序的进程id
- 通过`top -Hp pid`找到该进程下的所有线程（这一步有时候可以不需要）
- 通过``jstack pid`找到这个进程中所有的线程执行情况，特别注意那些线程状态为`WAITING、BLOCKED`的线程
- 通过`jmap -histo pid | head -20` 找出占用内存比较多的对象
- 其实还有一步骤是堆转储放到图形界面里面去分析