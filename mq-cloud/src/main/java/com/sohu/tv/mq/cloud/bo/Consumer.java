package com.sohu.tv.mq.cloud.bo;

import com.sohu.tv.mq.util.MQProtocol;

import java.util.Date;

/**
 * Consumer对象
 * 
 * @Description:
 * @author yongfeigao
 * @date 2018年6月12日
 */
public class Consumer {
    // 广播方式消费
    public static int CLUSTERING = 0;
    // 集群方式消费
    public static int BROADCAST = 1;

    // id
    private long id;
    // topic id
    private long tid;
    // consumer name
    private String name;
    // 消费方式 0:集群消费,1:广播消费
    private int consumeWay;
    // 创建日期
    private Date createDate;
    // 更新日期
    private Date updateTime;
    
    // 额外字段，用于存储topic名字
    private String topicName;

    // 额外字段，consumer流量
    private Traffic consumerTraffic;
    
    private int traceEnabled;
    
    // 用途
    private String info;

    // 通信协议
    private int protocol;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getTid() {
        return tid;
    }

    public void setTid(long tid) {
        this.tid = tid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getConsumeWay() {
        return consumeWay;
    }

    public void setConsumeWay(int consumeWay) {
        this.consumeWay = consumeWay;
    }

    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public boolean isClustering() {
        return CLUSTERING == getConsumeWay();
    }
    
    public boolean isBroadcast() {
        return BROADCAST == getConsumeWay();
    }
    
    public Traffic getConsumerTraffic() {
        return consumerTraffic;
    }

    public void setConsumerTraffic(Traffic consumerTraffic) {
        this.consumerTraffic = consumerTraffic;
    }

    public int getTraceEnabled() {
        return traceEnabled;
    }

    public void setTraceEnabled(int traceEnabled) {
        this.traceEnabled = traceEnabled;
    }

    public boolean traceEnabled() {
        return traceEnabled == 1;
    }
    
    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public int getProtocol() {
        return protocol;
    }

    public void setProtocol(int protocol) {
        this.protocol = protocol;
    }

    public boolean isHttpProtocol() {
        return MQProtocol.isHttp(protocol);
    }

    public boolean isProxyRemoting() {
        return MQProtocol.isProxyRemoting(protocol);
    }

    @Override
    public String toString() {
        return "Consumer{" +
                "id=" + id +
                ", tid=" + tid +
                ", name='" + name + '\'' +
                ", consumeWay=" + consumeWay +
                ", createDate=" + createDate +
                ", updateTime=" + updateTime +
                ", topicName='" + topicName + '\'' +
                ", consumerTraffic=" + consumerTraffic +
                ", traceEnabled=" + traceEnabled +
                ", info='" + info + '\'' +
                ", protocol=" + protocol +
                '}';
    }
}
