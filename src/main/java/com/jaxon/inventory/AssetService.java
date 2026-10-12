package com.jaxon.inventory;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AssetService implements AssetLookup {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    AssetService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    AssetResponse create(AssetCreateRequest request, UUID actorId, UUID correlationId) {
        String name = request.name().trim();
        Integer activeType = jdbc.queryForObject(
                "SELECT count(*) FROM inventory.asset_types WHERE code = ? AND status = 'ACTIVE'",
                Integer.class,
                request.assetType());
        if (activeType == null || activeType == 0) {
            throw new AssetValidationException();
        }

        UUID assetId = UUID.randomUUID();
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        jdbc.update(
                """
                INSERT INTO inventory.assets (id, name, asset_type, status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 0)
                """,
                assetId,
                name,
                request.assetType(),
                request.status(),
                Timestamp.from(now),
                Timestamp.from(now));
        jdbc.update(
                """
                INSERT INTO inventory.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, 'ASSET_CREATED', '1.0', ?, 'INVENTORY',
                        'HUMAN_USER', ?, 'ASSET', ?, 0,
                        'SUCCESS', ?, '{}'::jsonb, ?)
                """,
                UUID.randomUUID(),
                Timestamp.from(now),
                actorId,
                assetId,
                correlationId,
                Timestamp.from(now));
        return new AssetResponse(assetId, name, request.assetType(), request.status(), now, now, 0);
    }

    @Transactional(readOnly = true)
    Optional<AssetResponse> find(UUID assetId) {
        return jdbc.query(
                        """
                        SELECT id, name, asset_type, status, created_at, updated_at, version
                        FROM inventory.assets
                        WHERE id = ?
                        """,
                        (resultSet, row) -> new AssetResponse(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getString("name"),
                                resultSet.getString("asset_type"),
                                resultSet.getString("status"),
                                resultSet.getTimestamp("created_at").toInstant(),
                                resultSet.getTimestamp("updated_at").toInstant(),
                                resultSet.getLong("version")),
                        assetId)
                .stream()
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID assetId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM inventory.assets WHERE id = ?", Integer.class, assetId);
        return count != null && count == 1;
    }
}

final class AssetValidationException extends RuntimeException {}
