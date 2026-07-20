package com.aiplatform.infra.springai.document.reader.resource;

import org.springframework.core.io.ByteArrayResource;

/**
 * 带文件名的内存资源，供 Spring AI TextReader 保留来源名称。
 */
public final class NamedByteArrayResource extends ByteArrayResource {
    private final String filename;

    public NamedByteArrayResource(byte[] byteArray, String filename) {
        super(byteArray);
        this.filename = filename;
    }

    @Override
    public String getFilename() {
        return filename;
    }
}
