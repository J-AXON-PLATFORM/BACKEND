package com.jaxon.inventory;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AssetService implements AssetLookup, AssetCreator {

    private static final Pattern ASSET_TYPE = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private final JdbcTemplate jdbc;
    private final Clock clock;

    AssetService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    AssetResponse create(AssetCreateRequest request, UUID actorId, UUID correlationId) {
        return create(request.name(), request.assetType(), request.status(), actorId, correlationId);
    }

    @Override
    @Transactional
    public UUID createActive(String name, String assetType, UUID actorId, UUID correlationId) {
        return create(name, assetType, "ACTIVE", actorId, correlationId).assetId();
    }

    private AssetResponse create(
            String rawName, String assetType, String status, UUID actorId, UUID correlationId) {
        if (rawName == null || rawName.isBlank() || rawName.length() > 256
                || assetType == null || assetType.length() > 64 || !ASSET_TYPE.matcher(assetType).matches()) {
            throw new AssetValidationException();
        }
        String name = rawName.trim();
        Integer activeType = jdbc.queryForObject(
                "SELECT count(*) FROM inventory.asset_types WHERE code = ? AND status = 'ACTIVE'",
                Integer.class,
                assetType);
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
                assetType,
                status,
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
        return new AssetResponse(assetId, name, assetType, status, now, now, 0);
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

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(UUID assetId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM inventory.assets WHERE id = ? AND status = 'ACTIVE'",
                Integer.class,
                assetId);
        return count != null && count == 1;
    }

    @Override
    @Transactional
    public ReferenceStatus lockForReference(UUID assetId) {
        return jdbc.query(
                        "SELECT status FROM inventory.assets WHERE id = ? FOR SHARE",
                        (resultSet, row) -> "ACTIVE".equals(resultSet.getString("status"))
                                ? ReferenceStatus.ACTIVE : ReferenceStatus.INACTIVE,
                        assetId)
                .stream()
                .findFirst()
                .orElse(ReferenceStatus.NOT_FOUND);
    }
}
