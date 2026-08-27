package com.xidao.poker.web.protocol;

import com.xidao.poker.engine.snapshot.GameSnapshot;

public record SnapshotPayload(long connectionEpoch, String resumeToken, GameSnapshot state) {
}
