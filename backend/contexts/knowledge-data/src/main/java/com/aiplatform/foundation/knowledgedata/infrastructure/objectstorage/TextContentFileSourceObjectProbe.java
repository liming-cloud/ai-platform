package com.aiplatform.foundation.knowledgedata.infrastructure.objectstorage;

import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.TextContentIntegrityException;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.FileSourceObjectProbe;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import java.util.Objects;

/**
 * 复用私有文本对象存储核验首个 UTF-8 文件来源。
 */
public final class TextContentFileSourceObjectProbe implements FileSourceObjectProbe {

    private final TextContentObjectStoragePort storage;

    public TextContentFileSourceObjectProbe(TextContentObjectStoragePort storage) {
        this.storage = Objects.requireNonNull(storage, "文本对象存储端口不能为空");
    }

    @Override
    public void verify(SourceRevision revision) {
        Objects.requireNonNull(revision, "来源修订不能为空");
        var file = Objects.requireNonNull(revision.fileObject(), "文件来源修订必须包含对象事实");
        var reference = new TextContentReference(Math.toIntExact(revision.revision()),
                file.objectKey(), file.contentDigest(), file.byteSize(),
                TextContentReference.UTF8_TEXT, revision.createdAt());
        var metadata = storage.metadata(reference);
        if (!file.objectKey().equals(metadata.objectKey())
                || !file.contentDigest().equals(metadata.sha256())
                || file.byteSize() != metadata.byteSize()
                || !file.mediaType().equalsIgnoreCase(metadata.mediaType())) {
            throw new TextContentIntegrityException();
        }
    }
}
