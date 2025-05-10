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
package com.alibaba.csp.sentinel.dashboard.config;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.*;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author Eric Zhao
 * @since 1.4.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NacosProperties.class)
public class NacosConfig implements BeanFactoryAware {


    private static final Map<String, Class<? extends RuleEntity>> RULE_TYPE_MAP = new LinkedHashMap<>();

    /**
     * 流控规则
     * 熔断规则
     * 热点规则 (注意：这个在dashboard和在client用的不是一个实体，需要我们自行转换)
     * 系统规则
     * 授权规则
     */
    static {
        RULE_TYPE_MAP.put("flow", FlowRuleEntity.class);
        RULE_TYPE_MAP.put("degrade", DegradeRuleEntity.class);
        RULE_TYPE_MAP.put("paramFlow", ParamFlowRuleClientEntity.class);
        RULE_TYPE_MAP.put("system", SystemRuleEntity.class);
        RULE_TYPE_MAP.put("authority", AuthorityRuleClientEntity.class);
    }

    @Bean
    public ConfigService nacosConfigService(NacosProperties nacosProperties) throws Exception {
        Properties properties = new Properties();
        PropertyMapper map = PropertyMapper.get();
        map.from(nacosProperties::getServerAddr).toCall(() -> properties.put("serverAddr", nacosProperties.getServerAddr()));
        map.from(nacosProperties::getNamespace).toCall(() -> properties.put("namespace", nacosProperties.getNamespace()));
        map.from(nacosProperties::getUsername).toCall(() -> properties.put("username", nacosProperties.getUsername()));
        map.from(nacosProperties::getPassword).toCall(() -> properties.put("password", nacosProperties.getPassword()));

        return ConfigFactory.createConfigService(properties);
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        DefaultListableBeanFactory listableBeanFactory = (DefaultListableBeanFactory) beanFactory;

        RULE_TYPE_MAP.forEach((ruleKey, ruleClass) -> {
            // 循环注册Converter
            registerConverterBeans(listableBeanFactory, ruleKey, ruleClass);
        });
    }

    /**
     * 注册Converter
     * @param beanFactory
     * @param ruleKey
     * @param ruleClass
     * @param <T>
     * @author weiziming
     * @date 2025/5/10 13:44
     */
    private <T extends RuleEntity> void registerConverterBeans(
            DefaultListableBeanFactory beanFactory,
            String ruleKey,
            Class<T> ruleClass) {

        // Encoder注册（带泛型类型）
        String encoderName = ruleKey + "RuleEncoder";
        Converter<List<T>, String> encoder = ConverterFactory.createEncoder();
        beanFactory.registerSingleton(encoderName, encoder);

        // Decoder注册（带泛型类型）
        String decoderName = ruleKey + "RuleDecoder";
        Converter<String, List<T>> decoder = ConverterFactory.createDecoder(ruleClass);
        beanFactory.registerSingleton(decoderName, decoder);
    }

    /**
     * Converter 工厂类
     * @author weiziming
     * @date 2025/5/10 13:44
     */
    static class ConverterFactory {
        public static <T extends RuleEntity> Converter<List<T>, String> createEncoder() {
            return JSON::toJSONString;
        }

         public static <T extends RuleEntity> Converter<String, List<T>> createDecoder(Class<T> clazz) {
            return source -> JSON.parseArray(source, clazz);
        }
    }


}
