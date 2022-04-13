package com.sohu.tv.mq.cloud.mq;

import com.sohu.tv.mq.cloud.common.model.BrokerMomentStatsData;
import com.sohu.tv.mq.cloud.common.model.BrokerRateLimitData;
import com.sohu.tv.mq.cloud.common.model.BrokerStoreStat;
import com.sohu.tv.mq.cloud.common.model.UpdateSendMsgRateLimitRequestHeader;
import org.apache.rocketmq.common.protocol.RequestCode;
import org.apache.rocketmq.common.protocol.header.ConsumerSendMsgBackRequestHeader;
import org.apache.rocketmq.common.protocol.header.SendMessageRequestHeader;
import org.apache.rocketmq.remoting.exception.RemotingConnectException;
import org.apache.rocketmq.remoting.exception.RemotingSendRequestException;
import org.apache.rocketmq.remoting.exception.RemotingTimeoutException;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @author: yongfeigao
 * @date: 2022/4/6 11:08
 */
public class DefaultSohuMQAdminTest {
    private static DefaultSohuMQAdmin sohuMQAdmin;

    @Test
    public void testGetBrokerStoreStats() throws Exception {
        BrokerStoreStat bokerStoreStat = sohuMQAdmin.getBrokerStoreStats("127.0.0.1:8888");
        Assert.assertNotNull(bokerStoreStat);
    }

    @Test
    public void testGetMomentStatsInBroker() throws Exception {
        BrokerMomentStatsData brokerMomentStatsData = sohuMQAdmin.getMomentStatsInBroker("127.0.0.1:8888", "GROUP_GET_FALL_SIZE", 1);
        Assert.assertNotNull(brokerMomentStatsData);
    }

    @Test
    public void testRate() throws Exception {
        sendMsgStart();
        BrokerRateLimitData brokerRateLimitData = sohuMQAdmin.fetchSendMessageRateLimitInBroker("127.0.0.1:8888");
        Assert.assertNotNull(brokerRateLimitData);
    }

    @Test
    public void testChangeRate() throws Exception {
        UpdateSendMsgRateLimitRequestHeader updateSendMsgRateLimitRequestHeader = new UpdateSendMsgRateLimitRequestHeader();
        updateSendMsgRateLimitRequestHeader.setDefaultLimitQps(500);
        sohuMQAdmin.updateSendMessageRateLimit("127.0.0.1:8888", updateSendMsgRateLimitRequestHeader);
    }

    @Test
    public void testChangeRateTopicRate() throws Exception {
        UpdateSendMsgRateLimitRequestHeader updateSendMsgRateLimitRequestHeader = new UpdateSendMsgRateLimitRequestHeader();
        updateSendMsgRateLimitRequestHeader.setTopic("SELF_TEST_TOPIC");
        updateSendMsgRateLimitRequestHeader.setTopicLimitQps(1000);
        sohuMQAdmin.updateSendMessageRateLimit("127.0.0.1:8888", updateSendMsgRateLimitRequestHeader);
    }

    @Test
    public void testChangeRetryRate() throws Exception {
        UpdateSendMsgRateLimitRequestHeader updateSendMsgRateLimitRequestHeader = new UpdateSendMsgRateLimitRequestHeader();
        updateSendMsgRateLimitRequestHeader.setSendMsgBackLimitQps(10);
        sohuMQAdmin.updateSendMessageRateLimit("127.0.0.1:8888", updateSendMsgRateLimitRequestHeader);
    }

    @Test
    public void testDisable() throws Exception {
        UpdateSendMsgRateLimitRequestHeader updateSendMsgRateLimitRequestHeader = new UpdateSendMsgRateLimitRequestHeader();
        updateSendMsgRateLimitRequestHeader.setDisabled(true);
        sohuMQAdmin.updateSendMessageRateLimit("127.0.0.1:8888", updateSendMsgRateLimitRequestHeader);
    }

    public static void sendMessageMock() throws RemotingConnectException, RemotingSendRequestException, RemotingTimeoutException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, InterruptedException {
        SendMessageRequestHeader requestHeader = new SendMessageRequestHeader();
        String topic = "SELF_TEST_TOPIC";
        requestHeader.setProducerGroup("testGroup");
        requestHeader.setTopic(topic);
        requestHeader.setDefaultTopicQueueNums(1);
        requestHeader.setQueueId(0);
        requestHeader.setBornTimestamp(System.currentTimeMillis());
        requestHeader.setReconsumeTimes(0);
        requestHeader.setDefaultTopic("TBW102");
        requestHeader.setSysFlag(0);

        ConsumerSendMsgBackRequestHeader requestHeader2 = new ConsumerSendMsgBackRequestHeader();
        String group = "testGroup";
        requestHeader2.setGroup(group);
        requestHeader2.setOriginTopic("SELF_TEST_TOPIC");
        requestHeader2.setOffset(1L);
        requestHeader2.setDelayLevel(2);
        requestHeader2.setOriginMsgId("123abc");
        requestHeader2.setMaxReconsumeTimes(14);

        for (int i = 0; i < Integer.MAX_VALUE; ++i) {
            RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.SEND_MESSAGE, requestHeader);
            RemotingCommand response = sohuMQAdmin.getMQClientInstance().getMQClientAPIImpl().getRemotingClient().invokeSync("localhost:8888", request, 1000 * 60);
            assertTrue(response != null);
            if (response.getCode() == 2) {
                assertTrue(response.getRemark().contains(topic));
            } else {
                System.out.println(i + "=" + response.getCode() + "=" + response.getRemark());
            }

            RemotingCommand request2 = RemotingCommand.createRequestCommand(RequestCode.CONSUMER_SEND_MSG_BACK,
                    requestHeader2);
            RemotingCommand response2 = sohuMQAdmin.getMQClientInstance().getMQClientAPIImpl().getRemotingClient().invokeSync("localhost:8888", request2, 1000 * 60);
            assertTrue(response2 != null);
            if (response2.getCode() == 2) {
                assertTrue(response2.getRemark().contains(group));
            } else {
                System.out.println(i + "=" + response.getCode() + "=" + response.getRemark());
            }
        }
    }

    @BeforeClass
    public static void setup() throws Exception {
        sohuMQAdmin = new DefaultSohuMQAdmin();
        sohuMQAdmin.start();
    }

    private void sendMsgStart() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sendMessageMock();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @AfterClass
    public static void destroy() {
        sohuMQAdmin.shutdown();
    }

}