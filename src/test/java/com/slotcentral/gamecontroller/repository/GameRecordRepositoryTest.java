package com.slotcentral.gamecontroller.repository;

import com.slotcentral.gamecontroller.entity.GameRecord;
import com.slotcentral.gamecontroller.entity.SpinStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class GameRecordRepositoryTest {

    @Autowired
    GameRecordRepository repository;

    @Test
    void findWithFilters_byPlayerUid() {
        repository.save(createRecord("player-A", "game-1", SpinStatus.COMPLETED));
        repository.save(createRecord("player-B", "game-1", SpinStatus.COMPLETED));

        Page<GameRecord> result = repository.findWithFilters(
            "player-A", null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getPlayerUid()).isEqualTo("player-A");
    }

    @Test
    void findWithFilters_byStatus() {
        repository.save(createRecord("player-A", "game-1", SpinStatus.COMPLETED));
        repository.save(createRecord("player-A", "game-1", SpinStatus.ENGINE_FAILURE));

        Page<GameRecord> result = repository.findWithFilters(
            null, null, SpinStatus.ENGINE_FAILURE, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(SpinStatus.ENGINE_FAILURE);
    }

    @Test
    void findWithFilters_byDateRange() {
        OffsetDateTime now = OffsetDateTime.now();
        GameRecord record = createRecord("player-A", "game-1", SpinStatus.COMPLETED);
        repository.save(record);

        Page<GameRecord> result = repository.findWithFilters(
            null, null, null,
            now.minusMinutes(1), now.plusMinutes(1),
            PageRequest.of(0, 10));

        assertThat(result.getContent()).isNotEmpty();
    }

    @Test
    void findWithFilters_byGameId() {
        repository.save(createRecord("player-A", "game-1", SpinStatus.COMPLETED));
        repository.save(createRecord("player-A", "game-2", SpinStatus.COMPLETED));

        Page<GameRecord> result = repository.findWithFilters(
            null, "game-2", null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getGameId()).isEqualTo("game-2");
    }

    @Test
    void findByStatusIn() {
        repository.save(createRecord("player-A", "game-1", SpinStatus.SETTLEMENT_FAILED));
        repository.save(createRecord("player-A", "game-1", SpinStatus.COMPLETED));
        repository.save(createRecord("player-A", "game-1", SpinStatus.PENDING));

        List<GameRecord> result = repository.findByStatusIn(
            List.of(SpinStatus.SETTLEMENT_FAILED, SpinStatus.PENDING));

        assertThat(result).hasSize(2);
    }

    @Test
    void pagination_works() {
        for (int i = 0; i < 5; i++) {
            repository.save(createRecord("player-A", "game-1", SpinStatus.COMPLETED));
        }

        Page<GameRecord> page0 = repository.findWithFilters(
            "player-A", null, null, null, null, PageRequest.of(0, 2));
        Page<GameRecord> page1 = repository.findWithFilters(
            "player-A", null, null, null, null, PageRequest.of(1, 2));

        assertThat(page0.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page1.getContent()).hasSize(2);
    }

    private GameRecord createRecord(String playerUid, String gameId, SpinStatus status) {
        GameRecord record = new GameRecord();
        record.setPlayerUid(playerUid);
        record.setGameId(gameId);
        record.setBetAmount(new BigDecimal("1.00"));
        record.setStatus(status);
        return record;
    }
}
