package cn.isuyu.jvm.tuning;

import java.util.concurrent.TimeUnit;

/**
 * @Author NieZhiLiang
 * @Email nzlsgg@163.com
 * @GitHub https://github.com/niezhiliang
 * @Date 2020/7/8 上午10:24
 */
public class Deadlock {

    public static void main(String[] args) throws InterruptedException {
        Object o = new Object();

        new Thread(()->{
            synchronized (o) {
                System.out.println("T1:我要开始死锁啦....");
                try {
                    o.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
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
