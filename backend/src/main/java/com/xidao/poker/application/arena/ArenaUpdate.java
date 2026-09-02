package com.xidao.poker.application.arena;

import com.xidao.poker.engine.arena.ArenaSnapshot;

public record ArenaUpdate(long sequence, ArenaSnapshot snapshot, boolean duplicate) { }
