package com.aiplatform.foundation.trustedquery.application.diagnostic.port.out;

import com.aiplatform.foundation.trustedquery.application.diagnostic.dto.QueryRunTerminalCommit;

/**
 * 原子提交查询终态、最小引用与内部诊断事实的输出端口。
 */
public interface QueryRunTerminalCommitter {

    /**
     * @throws RuntimeException 终态、引用或诊断任一写入失败时整体回滚。
     */
    void commit(QueryRunTerminalCommit terminal);
}
