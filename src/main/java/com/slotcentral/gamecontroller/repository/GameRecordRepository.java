package com.slotcentral.gamecontroller.repository;

import com.slotcentral.gamecontroller.entity.GameRecord;
import com.slotcentral.gamecontroller.entity.SpinStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GameRecordRepository extends JpaRepository<GameRecord, Long> {

    Optional<GameRecord> findBySpinId(String spinId);

    List<GameRecord> findByStatusIn(List<SpinStatus> statuses);

    @Query("SELECT g FROM GameRecord g WHERE " +
           "(:playerUid IS NULL OR g.playerUid = :playerUid) AND " +
           "(:gameId IS NULL OR g.gameId = :gameId) AND " +
           "(:status IS NULL OR g.status = :status) AND " +
           "(:from IS NULL OR g.createdAt >= :from) AND " +
           "(:to IS NULL OR g.createdAt <= :to)")
    Page<GameRecord> findWithFilters(
        @Param("playerUid") String playerUid,
        @Param("gameId") String gameId,
        @Param("status") SpinStatus status,
        @Param("from") OffsetDateTime from,
        @Param("to") OffsetDateTime to,
        Pageable pageable);
}
