package com.xidao.poker.engine.snapshot;

import com.xidao.poker.engine.action.ActionType;

import java.util.Set;

/**
 * 服务端为当前查看者计算的精确行动边界。前端只负责展示，不自行推导下注规则。
 */
public record ActionOptions(
        Set<ActionType> legalActions,
        int toCall,
        int callAmount,
        Integer minimumBetTo,
        Integer minimumRaiseTo,
        int maximumTo
) {
    private static final ActionOptions NONE = new ActionOptions(Set.of(), 0, 0, null, null, 0);

    public ActionOptions {
        legalActions = legalActions == null ? Set.of() : Set.copyOf(legalActions);
        if (toCall < 0 || callAmount < 0 || maximumTo < 0) {
            throw new IllegalArgumentException("action amounts cannot be negative");
        }
        if (callAmount > toCall) throw new IllegalArgumentException("call amount cannot exceed to-call amount");
        if (minimumBetTo != null && minimumBetTo <= 0) {
            throw new IllegalArgumentException("minimum bet must be positive");
        }
        if (minimumRaiseTo != null && minimumRaiseTo <= 0) {
            throw new IllegalArgumentException("minimum raise must be positive");
        }
    }

    public static ActionOptions none() {
        return NONE;
    }
}
