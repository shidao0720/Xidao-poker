CREATE TABLE game_record (
    game_id                 VARCHAR(64) PRIMARY KEY,
    room_name               VARCHAR(128) NOT NULL,
    room_created_at         TIMESTAMPTZ NOT NULL,
    small_blind             INTEGER NOT NULL CHECK (small_blind > 0),
    big_blind               INTEGER NOT NULL CHECK (big_blind > small_blind),
    buy_in                  INTEGER NOT NULL CHECK (buy_in >= big_blind),
    max_players             INTEGER NOT NULL CHECK (max_players BETWEEN 2 AND 10),
    first_hand_started_at   TIMESTAMPTZ NOT NULL,
    last_hand_ended_at      TIMESTAMPTZ NOT NULL,
    hand_count              BIGINT NOT NULL DEFAULT 0 CHECK (hand_count >= 0)
);

CREATE TABLE poker_user (
    player_id       VARCHAR(64) PRIMARY KEY,
    display_name    VARCHAR(64) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

CREATE TABLE hand_history (
    game_id                    VARCHAR(64) NOT NULL REFERENCES game_record(game_id) ON DELETE CASCADE,
    hand_id                    BIGINT NOT NULL CHECK (hand_id > 0),
    started_at                 TIMESTAMPTZ NOT NULL,
    ended_at                   TIMESTAMPTZ NOT NULL CHECK (ended_at >= started_at),
    button_seat                SMALLINT NOT NULL CHECK (button_seat BETWEEN 0 AND 9),
    small_blind_seat           SMALLINT NOT NULL CHECK (small_blind_seat BETWEEN 0 AND 9),
    big_blind_seat             SMALLINT NOT NULL CHECK (big_blind_seat BETWEEN 0 AND 9),
    total_pot                  INTEGER NOT NULL CHECK (total_pot > 0),
    community_cards            JSONB NOT NULL,
    pots                       JSONB NOT NULL,
    awards                     JSONB NOT NULL,
    action_history_complete    BOOLEAN NOT NULL,
    PRIMARY KEY (game_id, hand_id)
);

CREATE TABLE hand_player (
    game_id             VARCHAR(64) NOT NULL,
    hand_id             BIGINT NOT NULL,
    player_id           VARCHAR(64) NOT NULL REFERENCES poker_user(player_id),
    player_name         VARCHAR(64) NOT NULL,
    seat                SMALLINT NOT NULL CHECK (seat BETWEEN 0 AND 9),
    starting_stack      INTEGER NOT NULL CHECK (starting_stack >= 0),
    ending_stack        INTEGER NOT NULL CHECK (ending_stack >= 0),
    total_contribution  INTEGER NOT NULL CHECK (total_contribution >= 0),
    winnings            INTEGER NOT NULL CHECK (winnings >= 0),
    folded              BOOLEAN NOT NULL,
    disconnected        BOOLEAN NOT NULL,
    busted              BOOLEAN NOT NULL,
    showdown            BOOLEAN NOT NULL,
    hand_key            BIGINT,
    hand_category       VARCHAR(32),
    hole_cards          JSONB NOT NULL,
    PRIMARY KEY (game_id, hand_id, player_id),
    UNIQUE (game_id, hand_id, seat),
    FOREIGN KEY (game_id, hand_id) REFERENCES hand_history(game_id, hand_id) ON DELETE CASCADE,
    CHECK ((showdown AND hand_key IS NOT NULL AND hand_category IS NOT NULL)
        OR (NOT showdown AND hand_key IS NULL AND hand_category IS NULL))
);

CREATE TABLE game_action (
    game_id            VARCHAR(64) NOT NULL,
    hand_id            BIGINT NOT NULL,
    action_index       INTEGER NOT NULL CHECK (action_index > 0),
    turn_id            BIGINT NOT NULL CHECK (turn_id > 0),
    player_id          VARCHAR(64) NOT NULL REFERENCES poker_user(player_id),
    phase              VARCHAR(16) NOT NULL,
    action_type        VARCHAR(16) NOT NULL,
    paid               INTEGER NOT NULL CHECK (paid >= 0),
    stack_after        INTEGER NOT NULL CHECK (stack_after >= 0),
    street_bet_after   INTEGER NOT NULL CHECK (street_bet_after >= 0),
    current_bet_after  INTEGER NOT NULL CHECK (current_bet_after >= 0),
    full_raise         BOOLEAN NOT NULL,
    PRIMARY KEY (game_id, hand_id, action_index),
    FOREIGN KEY (game_id, hand_id) REFERENCES hand_history(game_id, hand_id) ON DELETE CASCADE
);

CREATE TABLE player_statistic (
    player_id           VARCHAR(64) PRIMARY KEY REFERENCES poker_user(player_id),
    hands_played        BIGINT NOT NULL CHECK (hands_played >= 0),
    hands_won           BIGINT NOT NULL CHECK (hands_won >= 0),
    total_contributed   BIGINT NOT NULL CHECK (total_contributed >= 0),
    total_winnings      BIGINT NOT NULL CHECK (total_winnings >= 0),
    updated_at          TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_hand_history_ended_at ON hand_history (ended_at DESC);
CREATE INDEX idx_hand_player_player ON hand_player (player_id, game_id, hand_id);
CREATE INDEX idx_game_action_player ON game_action (player_id, game_id, hand_id);
