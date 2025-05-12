package com.alibaba.csp.sentinel.dashboard.repository.rule;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.RuleEntity;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 通用处理id为 原子类
 * @author Weiziming
 * @date 2025/5/12 14:51
 */
public abstract class InMemoryRuleRepositoryWithAtomicId<T extends RuleEntity>  extends InMemoryRuleRepositoryAdapter<T>{

    private static AtomicLong ids = new AtomicLong(0);

    @Override
    protected void setMaxId(long id) {
        ids.set(id);
    }

    @Override
    protected long nextId() {
        return ids.incrementAndGet();
    }
}
