package com.sohu.tv.mq.cloud.service;

import com.google.common.collect.Lists;
import com.sohu.tv.mq.cloud.bo.Consumer;
import com.sohu.tv.mq.cloud.bo.ConsumerTraffic;
import com.sohu.tv.mq.cloud.bo.Topic;
import com.sohu.tv.mq.cloud.dao.*;
import com.sohu.tv.mq.cloud.util.DateUtil;
import com.sohu.tv.mq.cloud.util.Result;
import com.sohu.tv.mq.cloud.util.WebUtil;
import com.sohu.tv.mq.cloud.web.controller.param.ManagerParam;
import com.sohu.tv.mq.cloud.web.controller.param.PaginationParam;
import com.sohu.tv.mq.cloud.web.vo.ConsumerManagerVo;
import com.sohu.tv.mq.cloud.web.vo.ConsumerStateVo;
import com.sohu.tv.mq.cloud.web.vo.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author fengwang219475
 * @version 1.0
 * @description: TODO
 * @date 2022/2/28 15:05
 */
@Service
public class ConsumerManagerService extends ManagerBaseService{

    @Resource
    private ConsumerTrafficDao consumerTrafficDao;

    @Resource
    private ConsumerDao consumerDao;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * 列表查询
     */
    public Result<?> queryAndFilterConsumerList(ManagerParam param, PaginationParam paginationParam) throws Exception{

        try {

            List<Long> idList = new ArrayList<>();
            List<Topic> topicList = comonFilterTopic(param,paginationParam,idList,true);
            if (CollectionUtils.isEmpty(topicList)){
                logger.warn("multi condition to query Topic,the result of topicList is empty");
                return Result.getOKResult();
            }
            Map<Long, List<Topic>> topicMap = topicList.stream().collect(Collectors.groupingBy(Topic::getId));
            List<Consumer> consumers = consumerDao.selectByTidList(idList);
            if (CollectionUtils.isEmpty(consumers)){
                logger.warn("query consumer by topic id,the result of consumer is empty");
                return Result.getOKResult();
            }
            final List<Long> cids = consumers.stream().map(Consumer::getId).collect(Collectors.toList());
            // 消费量筛选 消费量为0或没有消费记录
            if (param.getNoneConsumerFlows() != null && param.getNoneConsumerFlows()){
                List<Long> noneFlowCids = consumerTrafficDao.selectNoneConsumerFlowsId(idList,new Date());
                consumers = consumers.stream().filter(node->
                        noneFlowCids.contains(node.getId())
                ).collect(Collectors.toList());
            }
            Map<Long, Long> consumerFlowSum = summaryConsumerFlowBy5Min(cids);
            paginationParam.caculatePagination(consumers.size());
            consumers = consumers.stream().skip(paginationParam.getBegin()).limit(paginationParam.getNumOfPage())
                    .collect(Collectors.toList());
            List<ConsumerManagerVo> resultVo = new ArrayList<>(consumers.size());
            int index = paginationParam.getBegin();
            for (Consumer consumer : consumers) {
                ConsumerManagerVo consumerManagerVo = new ConsumerManagerVo();
                consumerManagerVo.setConsumer(consumer);
                consumerManagerVo.setLastFiveMinusConsumerFlow(consumerFlowSum.getOrDefault(consumer.getId(),0L));
                consumerManagerVo.setTopic(Optional.ofNullable(topicMap.get(consumer.getTid())).map(node->node.get(0)).orElse(new Topic()));
                consumerManagerVo.setIndex(index ++);
                resultVo.add(consumerManagerVo);
            }
            return Result.getResult(resultVo);
        } catch (Exception e) {
            logger.error("multi condition to query consumer is err,the err message : {}",e.getMessage());
            return Result.getDBErrorResult(e);
        }
    }

    /**
     * 计算前五分钟消费流量
     */
    private Map<Long, Long> summaryConsumerFlowBy5Min(List<Long> cids){
        //计算前五分钟消费流量
        final Map<Date, List<String>> dateRange = calculationDateRange();
        Map<Long, Long> consumerFlowSum = new HashMap<>(30);
        dateRange.forEach((key, value) -> {
            //查询并合并消费流量
            List<ConsumerTraffic> consumerTrafficList = consumerTrafficDao.selectFlowByDateTimeRangeAndCids(key, value, cids);
            if (!CollectionUtils.isEmpty(consumerTrafficList)) {
                consumerTrafficList.forEach(node->{
                    Long sum = consumerFlowSum.getOrDefault(node.getConsumerId(), 0L);
                    consumerFlowSum.put(node.getConsumerId(), sum + node.getCount());
                });
            }
        });
    return consumerFlowSum;
    }


    /**
     * 属性查找
     */
    public Result<?> getConsumerAttribute(long cid) {
        try {
            Consumer consumer = consumerDao.selectById(cid);
            if (consumer == null) {
                return Result.getResult(-1);
            }
            return Result.getResult(consumer.getConsumeWay());
        } catch (Exception e) {
            logger.error("getConsumerAttribute is err,the err message : {}",e.getMessage());
            return Result.getDBErrorResult(e);
        }
    }

    /**
     * 更改消费属性
     */
    public Result<?> editConsumerType(long cid, int consumeWay, HttpServletRequest request){
        try {
            UserInfo userInfo = (UserInfo) WebUtil.getAttribute(request, UserInfo.USER_INFO);
            consumerDao.updateConsumerWay(cid, consumeWay);
            logger.warn("the consumer consume_way is update,the consumer id is {},the update consumerWay is {},the operator id is {}, " +
                    "the update time is {}",cid,consumeWay,userInfo.getUser().getEmail(), DateUtil.formatYMD(new Date()));
            return Result.getOKResult();
        } catch (Exception e) {
            logger.error("editConsumerType is err,the err message : {}",e.getMessage());
            return Result.getDBErrorResult(e);
        }
    }

    /**
     * 当前消费者状态
     */
    public Result<?> getConsumerState(long cid, long tid) {
        try {
            // 时间范围
            ZoneId zoneId = ZoneId.systemDefault();
            LocalDateTime startLocalTime = LocalDateTime.now();
            LocalDateTime endLocalTime = startLocalTime.minusDays(30);
            Date endTime = Date.from(startLocalTime.atZone(zoneId).toInstant());
            Date startTime = Date.from(endLocalTime.atZone(zoneId).toInstant());
            Long flowsCount = consumerTrafficDao.selectSummaryDataByRangeTime(Lists.newArrayList(cid), startTime, endTime);
            int cSize = consumerDao.selectByTid(tid).size();
            ConsumerStateVo consumerStateVo = new ConsumerStateVo();
            consumerStateVo.setRecentMonConMsgNum(flowsCount == null? 0L:flowsCount);
            consumerStateVo.setOnlyRelation(cSize==1?0:1);
            return Result.getResult(consumerStateVo);
        } catch (Exception e) {
            logger.error("getConsumerState is err,the err message : {}",e.getMessage());
            return Result.getDBErrorResult(e);
        }
    }
}
