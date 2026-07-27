package com.aiplatform.foundation.knowledgedata.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.foundation.knowledgedata.domain.common.error.KnowledgeDataErrorCode;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextDataSource;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.EmbeddingConfiguration;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.IndexVersion;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.IndexVersionId;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.IndexVersionStatus;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.TextChunk;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpace;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceStatus;
import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.util.HashSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class KnowledgeDomainTypeTest {

    private static final Pattern ERROR_CODE = Pattern.compile(
            "^KNOWLEDGE_DATA\\.(REQUEST|AUTHENTICATION|AUTHORIZATION|RESOURCE|"
                    + "CONFLICT|QUOTA|DEPENDENCY_UNAVAILABLE|DEPENDENCY_RESPONSE|SYSTEM)"
                    + "\\.\\d{4}$");

    @Test
    void 聚合与值对象应声明明确领域类型() {
        assertThat(AggregateRoot.class).isAssignableFrom(KnowledgeSpace.class);
        assertThat(AggregateRoot.class).isAssignableFrom(TextDataSource.class);
        assertThat(AggregateRoot.class).isAssignableFrom(IndexVersion.class);

        assertThat(ValueObject.class).isAssignableFrom(KnowledgeSpaceId.class);
        assertThat(ValueObject.class).isAssignableFrom(DataSourceId.class);
        assertThat(ValueObject.class).isAssignableFrom(IndexVersionId.class);
        assertThat(ValueObject.class).isAssignableFrom(CreatorId.class);
        assertThat(ValueObject.class).isAssignableFrom(TextContentReference.class);
        assertThat(ValueObject.class).isAssignableFrom(EmbeddingConfiguration.class);
        assertThat(ValueObject.class).isAssignableFrom(TextChunk.class);
        assertThat(ValueObject.class).isAssignableFrom(KnowledgeSpaceStatus.class);
        assertThat(ValueObject.class).isAssignableFrom(DataSourceStatus.class);
        assertThat(ValueObject.class).isAssignableFrom(IndexVersionStatus.class);
    }

    @Test
    void 错误编码应符合上下文格式且互不重复() {
        var codes = new HashSet<String>();
        for (var error : KnowledgeDataErrorCode.values()) {
            assertThat(error.code()).matches(ERROR_CODE);
            assertThat(error.code().split("\\.", -1)[1])
                    .isEqualTo(error.type().name());
            assertThat(codes.add(error.code())).isTrue();
            assertThat(error.defaultMessage()).isNotBlank();
        }
    }
}
