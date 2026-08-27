package com.xidao.poker.persistence.history;

import com.xidao.poker.application.history.CompletedHandArchive;
import com.xidao.poker.engine.history.CompletedHandAction;
import com.xidao.poker.engine.history.CompletedHandPlayer;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface HandHistoryMapper {
    @Insert("""
            INSERT INTO game_record (
                game_id, room_name, room_created_at, small_blind, big_blind, buy_in, max_players,
                first_hand_started_at, last_hand_ended_at
            ) VALUES (
                #{archive.gameId}, #{archive.roomName}, #{archive.roomCreatedAt},
                #{archive.hand.config.smallBlind}, #{archive.hand.config.bigBlind},
                #{archive.hand.config.buyIn}, #{archive.hand.config.maxPlayers},
                #{archive.startedAt}, #{archive.endedAt}
            )
            ON CONFLICT (game_id) DO UPDATE SET
                room_name = EXCLUDED.room_name,
                first_hand_started_at = LEAST(game_record.first_hand_started_at, EXCLUDED.first_hand_started_at),
                last_hand_ended_at = GREATEST(game_record.last_hand_ended_at, EXCLUDED.last_hand_ended_at)
            """)
    int upsertGame(@Param("archive") CompletedHandArchive archive);

    @Insert("""
            INSERT INTO hand_history (
                game_id, hand_id, started_at, ended_at, button_seat, small_blind_seat, big_blind_seat,
                total_pot, community_cards, pots, awards, action_history_complete
            ) VALUES (
                #{archive.gameId}, #{archive.handId}, #{archive.startedAt}, #{archive.endedAt},
                #{archive.hand.buttonSeat}, #{archive.hand.smallBlindSeat}, #{archive.hand.bigBlindSeat},
                #{archive.hand.totalPot}, CAST(#{communityCardsJson} AS jsonb),
                CAST(#{potsJson} AS jsonb), CAST(#{awardsJson} AS jsonb),
                #{archive.hand.actionHistoryComplete}
            )
            ON CONFLICT (game_id, hand_id) DO NOTHING
            """)
    int insertHand(
            @Param("archive") CompletedHandArchive archive,
            @Param("communityCardsJson") String communityCardsJson,
            @Param("potsJson") String potsJson,
            @Param("awardsJson") String awardsJson
    );

    @Insert("""
            INSERT INTO poker_user (player_id, display_name, created_at, updated_at)
            VALUES (#{player.playerId}, #{player.playerName}, #{updatedAt}, #{updatedAt})
            ON CONFLICT (player_id) DO UPDATE SET
                display_name = EXCLUDED.display_name,
                updated_at = EXCLUDED.updated_at
            """)
    int upsertUser(@Param("player") CompletedHandPlayer player, @Param("updatedAt") java.time.Instant updatedAt);

    @Insert("""
            INSERT INTO hand_player (
                game_id, hand_id, player_id, player_name, seat, starting_stack, ending_stack,
                total_contribution, winnings, folded, disconnected, busted, showdown,
                hand_key, hand_category, hole_cards
            ) VALUES (
                #{gameId}, #{handId}, #{player.playerId}, #{player.playerName}, #{player.seat},
                #{player.startingStack}, #{player.endingStack}, #{player.totalContribution},
                #{player.winnings}, #{player.folded}, #{player.disconnected}, #{player.busted},
                #{player.showdown}, #{player.handKey}, #{player.handCategory}, CAST(#{holeCardsJson} AS jsonb)
            )
            """)
    int insertPlayer(
            @Param("gameId") String gameId,
            @Param("handId") long handId,
            @Param("player") CompletedHandPlayer player,
            @Param("holeCardsJson") String holeCardsJson
    );

    @Insert("""
            INSERT INTO game_action (
                game_id, hand_id, action_index, turn_id, player_id, phase, action_type,
                paid, stack_after, street_bet_after, current_bet_after, full_raise
            ) VALUES (
                #{gameId}, #{handId}, #{action.actionIndex}, #{action.turnId}, #{action.playerId},
                #{action.phase}, #{action.action}, #{action.paid}, #{action.stackAfter},
                #{action.streetBetAfter}, #{action.currentBetAfter}, #{action.fullRaise}
            )
            """)
    int insertAction(
            @Param("gameId") String gameId,
            @Param("handId") long handId,
            @Param("action") CompletedHandAction action
    );

    @Insert("""
            INSERT INTO player_statistic (
                player_id, hands_played, hands_won, total_contributed, total_winnings, updated_at
            ) VALUES (
                #{player.playerId}, 1, #{won}, #{player.totalContribution}, #{player.winnings}, #{updatedAt}
            )
            ON CONFLICT (player_id) DO UPDATE SET
                hands_played = player_statistic.hands_played + 1,
                hands_won = player_statistic.hands_won + EXCLUDED.hands_won,
                total_contributed = player_statistic.total_contributed + EXCLUDED.total_contributed,
                total_winnings = player_statistic.total_winnings + EXCLUDED.total_winnings,
                updated_at = EXCLUDED.updated_at
            """)
    int upsertStatistic(
            @Param("player") CompletedHandPlayer player,
            @Param("won") int won,
            @Param("updatedAt") java.time.Instant updatedAt
    );

    @Update("""
            UPDATE game_record
            SET hand_count = hand_count + 1,
                last_hand_ended_at = GREATEST(last_hand_ended_at, #{endedAt})
            WHERE game_id = #{gameId}
            """)
    int markHandSaved(@Param("gameId") String gameId, @Param("endedAt") java.time.Instant endedAt);
}
