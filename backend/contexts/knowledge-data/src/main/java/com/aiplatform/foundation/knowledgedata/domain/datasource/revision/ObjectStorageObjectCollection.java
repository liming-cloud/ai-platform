package com.aiplatform.foundation.knowledgedata.domain.datasource.revision;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 对象存储来源修订冻结的平台范围选择及实际对象集合。
 *
 * <p>定义摘要覆盖范围、选择修订和按稳定顺序记录的每个对象元数据；后续同步只能消费该集合，不能重新扫描可变前缀。</p>
 */
public record ObjectStorageObjectCollection(
        PlatformObjectRangeId rangeId,
        long selectionRevision,
        List<PlatformObjectReference> objects) implements ValueObject {

    public ObjectStorageObjectCollection {
        rangeId = Objects.requireNonNull(rangeId, "平台对象范围标识不能为空");
        if (selectionRevision < 1) {
            throw new IllegalArgumentException("对象范围选择修订必须为正数");
        }
        objects = List.copyOf(Objects.requireNonNull(objects, "对象集合不能为空"));
        if (objects.isEmpty()) {
            throw new IllegalArgumentException("对象集合不能为空");
        }
        var objectIds = new HashSet<String>();
        for (var object : objects) {
            if (!objectIds.add(Objects.requireNonNull(object, "对象集合不能包含空值").objectId())) {
                throw new IllegalArgumentException("对象集合不能包含重复的平台对象标识");
            }
        }
    }

    /**
     * 返回覆盖范围、选择修订和对象元数据的不可变定义摘要。
     */
    public String definitionDigest() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            update(digest, rangeId.value());
            update(digest, Long.toString(selectionRevision));
            for (var object : objects) {
                update(digest, object.objectId());
                update(digest, object.contentDigest());
                update(digest, Long.toString(object.byteSize()));
                update(digest, object.mediaType());
                update(digest, object.freshAt().toString());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
