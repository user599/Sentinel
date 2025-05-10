/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.dashboard.controller.rule.v2;

import com.alibaba.csp.sentinel.dashboard.auth.AuthAction;
import com.alibaba.csp.sentinel.dashboard.auth.AuthService.PrivilegeType;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.SystemRuleEntity;
import com.alibaba.csp.sentinel.dashboard.domain.Result;
import com.alibaba.csp.sentinel.dashboard.repository.rule.RuleRepository;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRuleProvider;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRulePublisher;
import com.alibaba.csp.sentinel.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

/**
 * @author leyou(lihao)
 */
@RestController
@RequestMapping("/v2/system")
public class SystemControllerV2 {

    private final Logger logger = LoggerFactory.getLogger(SystemControllerV2.class);

    @Autowired
    private RuleRepository<SystemRuleEntity, Long> repository;

    @Autowired
    @Qualifier("systemRuleNacosProvider")
    private DynamicRuleProvider<List<SystemRuleEntity>> ruleProvider;

    @Autowired
    @Qualifier("systemRuleNacosPublisher")
    private DynamicRulePublisher<List<SystemRuleEntity>> rulePublisher;


    @GetMapping("/rules")
    @AuthAction(PrivilegeType.READ_RULE)
    public Result<List<SystemRuleEntity>> apiQueryMachineRules(String app) {
        if (StringUtil.isEmpty(app)) {
            return Result.ofFail(-1, "app can't be null or empty");
        }

        try {
            List<SystemRuleEntity> rules = ruleProvider.getRules(app);
            rules = repository.saveAll(rules);
            return Result.ofSuccess(rules);
        } catch (Throwable throwable) {
            logger.error("Query machine system rules error:" + throwable.getMessage(), throwable);
            return Result.ofThrowable(-1, throwable);
        }
    }


    @PostMapping("/rule")
    @AuthAction(PrivilegeType.WRITE_RULE)
    public Result<SystemRuleEntity> apiAdd(@RequestBody SystemRuleEntity entity) {

        if (StringUtil.isEmpty(entity.getApp())) {
            return Result.ofFail(-1, "app can't be null or empty");
        }
        Double highestCpuUsage = entity.getHighestCpuUsage();
        Double highestSystemLoad = entity.getHighestSystemLoad();
        Long avgRt = entity.getAvgRt();
        Long maxThread = entity.getMaxThread();
        Double qps = entity.getQps();
        int notNullCount = countNotNullAndNotNegative(highestSystemLoad, avgRt, maxThread, qps, highestCpuUsage);
        if (notNullCount != 1) {
            return Result.ofFail(-1, "only one of [highestSystemLoad, avgRt, maxThread, qps,highestCpuUsage] "
                    + "value must be set > 0, but " + notNullCount + " values get");
        }

        // padding default value : -1
        if (null != highestCpuUsage) {
            if (highestCpuUsage > 1) {
                return Result.ofFail(-1, "highestCpuUsage must between [0.0, 1.0]");
            }
        } else {
            entity.setHighestCpuUsage(-1D);
        }
        if (null == highestSystemLoad) {
            entity.setHighestSystemLoad(-1D);
        }
        if (null == avgRt) {
            entity.setAvgRt(-1L);
        }
        if (null == maxThread) {
            entity.setMaxThread(-1L);
        }
        if (null == qps) {
            entity.setQps(-1D);
        }

        Date date = new Date();
        entity.setGmtCreate(date);
        entity.setGmtModified(date);
        try {
            entity = repository.save(entity);
        } catch (Throwable throwable) {
            logger.error("Add SystemRule error : " + throwable.getMessage(), throwable);
            return Result.ofThrowable(-1, throwable);
        }

        try {
            publishRules(entity.getApp());
        } catch (Exception e) {
            logger.error("Publish SystemRule error: " + e.getMessage(), e);
            return Result.ofThrowable(-1, e);
        }

        return Result.ofSuccess(entity);
    }

    @PutMapping("/rule/{id}")
    @AuthAction(PrivilegeType.WRITE_RULE)
    public Result<SystemRuleEntity> apiUpdateIfNotNull(@PathVariable("id") Long id, @RequestBody SystemRuleEntity entity) {
        if (id == null) {
            return Result.ofFail(-1, "id can't be null");
        }
        SystemRuleEntity oldEntity = repository.findById(id);
        if (oldEntity == null) {
            return Result.ofFail(-1, "id " + id + " dose not exist");
        }

        Double highestSystemLoad = entity.getHighestSystemLoad();
        Double highestCpuUsage = entity.getHighestCpuUsage();
        Long avgRt = entity.getAvgRt();
        Long maxThread = entity.getMaxThread();
        Double qps = entity.getQps();

        // padding default value : -1
        if (highestSystemLoad != null) {
            if (highestSystemLoad < 0) {
                return Result.ofFail(-1, "highestSystemLoad must >= 0");
            }
        } else {
            entity.setHighestSystemLoad(-1D);
        }
        if (highestCpuUsage != null) {
            if (highestCpuUsage < 0) {
                return Result.ofFail(-1, "highestCpuUsage must >= 0");
            } else if (highestCpuUsage > 1) {
                return Result.ofFail(-1, "highestCpuUsage must <= 1");
            }
        } else {
            entity.setHighestCpuUsage(-1D);
        }
        if (avgRt != null ) {
            if (avgRt < 0) {
                return Result.ofFail(-1, "avgRt must >= 0");
            }
        } else {
            entity.setAvgRt(-1L);
        }
        if (maxThread != null ) {
            if (maxThread < 0) {
                return Result.ofFail(-1, "maxThread must >= 0");
            }
        } else {
            entity.setMaxThread(-1L);
        }
        if (qps != null ) {
            if (qps < 0) {
                return Result.ofFail(-1, "qps must >= 0");
            }
        } else {
            entity.setQps(-1D);
        }

        entity.setApp(oldEntity.getApp());
        entity.setIp(oldEntity.getIp());
        entity.setPort(oldEntity.getPort());

        Date date = new Date();
        entity.setGmtModified(oldEntity.getGmtCreate());
        entity.setGmtModified(date);
        try {
            entity = repository.save(entity);
        } catch (Throwable throwable) {
            logger.error("Update SystemRule error:" + throwable.getMessage(), throwable);
            return Result.ofThrowable(-1, throwable);
        }
        try {
            publishRules(oldEntity.getApp());
        } catch (Exception e) {
            logger.error("Publish Update SystemRule error: " + e.getMessage(), e);
            return Result.ofThrowable(-1, e);
        }

        return Result.ofSuccess(entity);
    }

    @DeleteMapping("/rule/{id}")
    @AuthAction(PrivilegeType.DELETE_RULE)
    public Result<?> delete(@PathVariable("id") Long id) {
        if (id == null) {
            return Result.ofFail(-1, "id can't be null");
        }
        SystemRuleEntity oldEntity = repository.findById(id);
        if (oldEntity == null) {
            return Result.ofSuccess(null);
        }
        try {
            repository.delete(id);
        } catch (Throwable throwable) {
            logger.error("Delete SystemRule error:" + throwable.getMessage(), throwable);
            return Result.ofThrowable(-1, throwable);
        }
        try {
            publishRules(oldEntity.getApp());
        } catch (Exception e) {
            logger.error("Publish Delete SystemRule error: " + e.getMessage(), e);
            return Result.ofThrowable(-1, e);
        }
        return Result.ofSuccess(id);
    }

    private int countNotNullAndNotNegative(Number... values) {
        int notNullCount = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null && values[i].doubleValue() >= 0) {
                notNullCount++;
            }
        }
        return notNullCount;
    }

    private void publishRules(String app) throws Exception {
        List<SystemRuleEntity> rules = repository.findAllByApp(app);
        rulePublisher.publish(app, rules);
    }
}
