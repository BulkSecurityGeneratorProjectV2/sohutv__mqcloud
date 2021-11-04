package com.sohu.tv.mq.cloud.task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.rocketmq.common.protocol.body.KVTable;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import com.sohu.tv.mq.cloud.bo.Broker;
import com.sohu.tv.mq.cloud.bo.CheckStatusEnum;
import com.sohu.tv.mq.cloud.bo.Cluster;
import com.sohu.tv.mq.cloud.bo.ClusterStat;
import com.sohu.tv.mq.cloud.bo.NameServer;
import com.sohu.tv.mq.cloud.bo.SlaveFallBehind;
import com.sohu.tv.mq.cloud.bo.UserWarn.WarnType;
import com.sohu.tv.mq.cloud.mq.MQAdminCallback;
import com.sohu.tv.mq.cloud.mq.MQAdminTemplate;
import com.sohu.tv.mq.cloud.service.AlertService;
import com.sohu.tv.mq.cloud.service.BrokerService;
import com.sohu.tv.mq.cloud.service.ClusterService;
import com.sohu.tv.mq.cloud.service.NameServerService;
import com.sohu.tv.mq.cloud.util.MQCloudConfigHelper;
import com.sohu.tv.mq.cloud.util.Result;

import net.javacrumbs.shedlock.core.SchedulerLock;

/**
 * 集群实例状态监控
 * 
 * @Description:
 * @author zhehongyuan
 * @date 2018年10月11日
 */
public class ClusterMonitorTask {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private MQAdminTemplate mqAdminTemplate;

    @Autowired
    private AlertService alertService;

    @Autowired
    private ClusterService clusterService;

    @Autowired
    private NameServerService nameServerService;

    @Autowired
    private BrokerService brokerService;

    @Autowired
    private MQCloudConfigHelper mqCloudConfigHelper;

    /**
     * 每6分钟监控一次
     */
    @Scheduled(cron = "45 */6 * * * *")
    @SchedulerLock(name = "nameServerMonitor", lockAtMostFor = 345000, lockAtLeastFor = 345000)
    public void nameServerMonitor() {
        if (clusterService.getAllMQCluster() == null) {
            logger.warn("nameServerMonitor mqcluster is null");
            return;
        }
        logger.info("monitor NameServer start");
        long start = System.currentTimeMillis();
        List<ClusterStat> clusterStatList = new ArrayList<>();
        for (Cluster mqCluster : clusterService.getAllMQCluster()) {
            ClusterStat clusterStat = monitorNameServer(mqCluster);
            if (clusterStat != null) {
                clusterStatList.add(clusterStat);
            }
        }
        handleAlarmMessage(clusterStatList, WarnType.NAMESERVER_ERROR);
        logger.info("monitor NameServer end! use:{}ms", System.currentTimeMillis() - start);
    }

    /**
     * 每5分钟监控一次
     */
    @Scheduled(cron = "50 */5 * * * *")
    @SchedulerLock(name = "brokerMonitor", lockAtMostFor = 240000, lockAtLeastFor = 240000)
    public void brokerMonitor() {
        if (clusterService.getAllMQCluster() == null) {
            logger.warn("brokerMonitor mqcluster is null");
            return;
        }
        logger.info("monitor broker start");
        long start = System.currentTimeMillis();
        // 缓存broker状态信息
        Map<Cluster, List<Broker>> clusterMap = new HashMap<>();
        List<ClusterStat> clusterStatList = new ArrayList<>();
        for (Cluster mqCluster : clusterService.getAllMQCluster()) {
            Result<List<Broker>> brokerListResult = brokerService.query(mqCluster.getId());
            if (brokerListResult.isEmpty()) {
                continue;
            }
            List<Broker> brokerList = brokerListResult.getResult();
            ClusterStat clusterStat = monitorBroker(mqCluster, brokerList);
            if (clusterStat != null) {
                clusterStatList.add(clusterStat);
            }
            if (brokerList != null && !brokerList.isEmpty()) {
                clusterMap.put(mqCluster, brokerList);
            }
        }
        handleAlarmMessage(clusterStatList, WarnType.BROKER_ERROR);
        // broker偏移量预警
        brokerFallBehindWarn(clusterMap);
        logger.info("monitor broker end! use:{}ms", System.currentTimeMillis() - start);
    }

    /**
     * ping name server
     * 
     * @param mqCluster
     */
    private ClusterStat monitorNameServer(Cluster mqCluster) {
        Result<List<NameServer>> nameServerListResult = nameServerService.query(mqCluster.getId());
        if (nameServerListResult.isEmpty()) {
            return null;
        }
        List<String> nameServerAddressList = new ArrayList<String>();
        for (NameServer ns : nameServerListResult.getResult()) {
            nameServerAddressList.add(ns.getAddr());
        }
        List<String> statList = new ArrayList<>();
        mqAdminTemplate.execute(new MQAdminCallback<Void>() {
            public Void callback(MQAdminExt mqAdmin) throws Exception {
                for (String addr : nameServerAddressList) {
                    try {
                        mqAdmin.getNameServerConfig(Arrays.asList(addr));
                        nameServerService.update(mqCluster.getId(), addr, CheckStatusEnum.OK);
                    } catch (Exception e) {
                        nameServerService.update(mqCluster.getId(), addr, CheckStatusEnum.FAIL);
                        statList.add("ns:" + addr + ";Exception: " + e.getMessage());
                    }
                }

                return null;
            }

            public Cluster mqCluster() {
                return mqCluster;
            }

            @Override
            public Void exception(Exception e) throws Exception {
                statList.add("Exception: " + e.getMessage());
                return null;
            }
        });
        if (statList.size() == 0) {
            return null;
        }
        ClusterStat clusterStat = new ClusterStat();
        clusterStat.setClusterLink(mqCloudConfigHelper.getNameServerMonitorLink(mqCluster.getId()));
        clusterStat.setClusterName(mqCluster.getName());
        clusterStat.setStats(statList);
        return clusterStat;
    }

    /**
     * ping Broker
     * 
     * @param mqCluster
     */
    private ClusterStat monitorBroker(Cluster mqCluster, List<Broker> brokerList) {
        List<String> statList = new ArrayList<>();
        mqAdminTemplate.execute(new MQAdminCallback<Void>() {
            public Void callback(MQAdminExt mqAdmin) throws Exception {
                for (Broker broker : brokerList) {
                    try {
                        KVTable kvTable = mqAdmin.fetchBrokerRuntimeStats(broker.getAddr());
                        broker.setMaxOffset(NumberUtils.toLong(kvTable.getTable().get("commitLogMaxOffset")));
                        brokerService.update(mqCluster.getId(), broker.getAddr(), CheckStatusEnum.OK);
                    } catch (Exception e) {
                        brokerService.update(mqCluster.getId(), broker.getAddr(), CheckStatusEnum.FAIL);
                        statList.add("bk:" + broker.getAddr() + ";Exception: " + e.getMessage());
                    }
                }
                return null;
            }

            public Cluster mqCluster() {
                return mqCluster;
            }

            @Override
            public Void exception(Exception e) throws Exception {
                statList.add("Exception: " + e.getMessage());
                return null;
            }
        });
        if (statList.size() == 0) {
            return null;
        }
        ClusterStat clusterStat = new ClusterStat();
        clusterStat.setClusterLink(mqCloudConfigHelper.getBrokerMonitorLink(mqCluster.getId()));
        clusterStat.setClusterName(mqCluster.getName());
        clusterStat.setStats(statList);
        return clusterStat;
    }

    /**
     * 处理报警信息
     * 
     * @param alarmList
     * @param type
     * @param alarmTitle
     */
    private void handleAlarmMessage(List<ClusterStat> clusterStatList, WarnType warnType) {
        if (clusterStatList.isEmpty()) {
            return;
        }
        // 发送并保持邮件预警
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("list", clusterStatList);
        alertService.sendWarn(null, warnType, paramMap);
    }

    /**
     * broker最大offset预警
     * 
     * @param clusterMap
     */
    private void brokerFallBehindWarn(Map<Cluster, List<Broker>> clusterMap) {
        if (clusterMap.isEmpty()) {
            return;
        }
        List<SlaveFallBehind> list = new ArrayList<>();
        for (Cluster cluster : clusterMap.keySet()) {
            List<Broker> brokerList = clusterMap.get(cluster);
            for (Broker broker : brokerList) {
                // slave跳过
                if (!broker.isMaster()) {
                    continue;
                }
                // 获取slave
                Broker slave = findSlave(broker, brokerList);
                if (slave == null) {
                    continue;
                }
                // 获取slave落后字节
                long fallBehindOffset = broker.getMaxOffset() - slave.getMaxOffset();
                if (fallBehindOffset <= mqCloudConfigHelper.getSlaveFallBehindSize()) {
                    continue;
                }
                SlaveFallBehind slaveFallBehind = new SlaveFallBehind();
                slaveFallBehind.setClusterName(cluster.getName());
                slaveFallBehind.setBrokerLink(mqCloudConfigHelper.getHrefLink(
                        mqCloudConfigHelper.getBrokerMonitorLink(cluster.getId()), broker.getBrokerName()));
                slaveFallBehind.setFallBehindOffset(fallBehindOffset);
                slaveFallBehind.setSlaveFallBehindSize(mqCloudConfigHelper.getSlaveFallBehindSize());
                list.add(slaveFallBehind);
            }
        }
        if (list.size() <= 0) {
            return;
        }
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("list", list);
        alertService.sendWarn(null, WarnType.SLAVE_FALL_BEHIND, paramMap);
    }

    private Broker findSlave(Broker master, List<Broker> brokerList) {
        for (Broker broker : brokerList) {
            if (broker.isMaster()) {
                continue;
            }
            if (master.getBrokerName().equals(broker.getBrokerName())) {
                return broker;
            }
        }
        return null;
    }
}
