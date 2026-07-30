package com.aiplatform.foundation.knowledgedata.infrastructure.datasource;

import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvProjection;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvProjectionPort;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvStoragePort;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetCsvDefinition;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetRowFilter;
import java.io.IOException;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** 严格按 RFC4180 读取平台私有 UTF-8 CSV，并仅投影冻结的可检索字段。 */
public final class Rfc4180StructuredDatasetCsvProjectionAdapter implements StructuredDatasetCsvProjectionPort {
    private final StructuredDatasetCsvStoragePort storage;
    private final long maximumBytes;
    private final long maximumRows;
    private final long maximumLineBytes;
    private final int maximumFieldCharacters;

    public Rfc4180StructuredDatasetCsvProjectionAdapter(StructuredDatasetCsvStoragePort storage, long maximumBytes, long maximumRows, long maximumLineBytes, int maximumFieldCharacters) {
        this.storage = Objects.requireNonNull(storage, "结构化数据集 CSV 存储不能为空");
        if (maximumBytes < 1 || maximumRows < 1 || maximumLineBytes < 1 || maximumFieldCharacters < 1) throw new IllegalArgumentException("CSV 资源上限必须为正数");
        this.maximumBytes = maximumBytes; this.maximumRows = maximumRows; this.maximumLineBytes = maximumLineBytes; this.maximumFieldCharacters = maximumFieldCharacters;
    }

    @Override public StructuredDatasetCsvProjection project(StructuredDatasetCsvDefinition definition) {
        var value = Objects.requireNonNull(definition, "结构化数据集定义不能为空");
        if (value.csv().byteSize() > maximumBytes) throw new IllegalArgumentException("CSV 字节数超过上限");
        try (var input = storage.read(value.csv())) {
            var rows = parse(Objects.requireNonNull(input, "CSV 内容流不能为空"));
            return project(value, rows);
        } catch (IOException exception) { throw new IllegalArgumentException("CSV 读取或编码不合法", exception); }
    }

    private List<List<String>> parse(InputStream input) throws IOException {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        var rows = new ArrayList<List<String>>(); var row = new ArrayList<String>(); var field = new StringBuilder(); boolean quoted = false; boolean atFieldStart = true;
        long lineBytes = 0;
        try (var reader = new PushbackReader(
                new InputStreamReader(new LimitedInputStream(input, maximumBytes), decoder), 1)) {
        int next;
        while ((next = reader.read()) != -1) {
            char ch = (char) next; lineBytes += String.valueOf(ch).getBytes(StandardCharsets.UTF_8).length;
            if (lineBytes > maximumLineBytes) throw new IllegalArgumentException("CSV 单行字节数超过上限");
            if (quoted) {
                if (ch == '"') { int escaped = reader.read(); if (escaped == '"') { field.append(ch); lineBytes++; } else { quoted = false; if (escaped != -1) reader.unread(escaped); } }
                else if (ch == '\r') { if (reader.read() != '\n') throw new IllegalArgumentException("CSV 换行格式不合法"); field.append('\n'); lineBytes = 0; }
                else field.append(ch);
            } else if (ch == '"' && atFieldStart) { quoted = true; atFieldStart = false; }
            else if (ch == ',') { add(row, field); field.setLength(0); atFieldStart = true; }
            else if (ch == '\r') { if (reader.read() != '\n') throw new IllegalArgumentException("CSV 换行格式不合法"); add(row, field); rows.add(row); if (rows.size() > maximumRows + 1) throw new IllegalArgumentException("CSV 行数超过上限"); row = new ArrayList<>(); field.setLength(0); atFieldStart = true; lineBytes = 0; }
            else if (ch == '\n') throw new IllegalArgumentException("CSV 换行格式不合法");
            else { field.append(ch); atFieldStart = false; }
        }
        }
        if (quoted) throw new IllegalArgumentException("CSV 引号未闭合");
        if (!row.isEmpty() || field.length() > 0 || !atFieldStart) { add(row, field); rows.add(row); }
        return rows;
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private final long maximum; private long read;
        private LimitedInputStream(InputStream input, long maximum) { super(input); this.maximum = maximum; }
        @Override public int read() throws IOException { var value = super.read(); if (value >= 0) count(1); return value; }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException { var value = super.read(bytes, offset, length); if (value > 0) count(value); return value; }
        private void count(long increment) { if ((read += increment) > maximum) throw new IllegalArgumentException("CSV 字节数超过上限"); }
    }

    private void add(List<String> row, StringBuilder field) { if (field.length() > maximumFieldCharacters) throw new IllegalArgumentException("CSV 字段字符数超过上限"); row.add(field.toString()); }
    private StructuredDatasetCsvProjection project(StructuredDatasetCsvDefinition definition, List<List<String>> rows) {
        if (rows.isEmpty()) throw new IllegalArgumentException("CSV 必须包含标题行");
        var expected = definition.fields().stream().map(value -> value.fieldName()).toList(); var header = rows.getFirst();
        if (!header.equals(expected) || header.stream().anyMatch(String::isBlank) || new HashSet<>(header).size() != header.size()) throw new IllegalArgumentException("CSV 标题与冻结字段映射不匹配");
        int contentIndex = header.indexOf(definition.contentField()), titleIndex = definition.titleField() == null ? -1 : header.indexOf(definition.titleField());
        var output = new StringBuilder(); long records = 0, skipped = 0;
        for (int r = 1; r < rows.size(); r++) { var row = rows.get(r); if (row.size() != header.size()) throw new IllegalArgumentException("CSV 数据行字段数量不匹配"); var content = row.get(contentIndex).strip(); if (content.isEmpty()) { if (definition.rowFilter() == StructuredDatasetRowFilter.ALL_ROWS) throw new IllegalArgumentException("CSV 存在空主内容行"); skipped++; continue; } var projected = titleIndex < 0 || row.get(titleIndex).strip().isEmpty() ? content : row.get(titleIndex).strip() + "\n" + content; if (records++ > 0) output.append("\n\n"); output.append(projected); }
        if (records == 0) throw new IllegalArgumentException("CSV 不包含有效数据行"); return new StructuredDatasetCsvProjection(output.toString(), records, skipped);
    }
}
