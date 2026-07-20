package com.aiplatform.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.stereotype.Component;

class SpringManagedConstructorContractTest {
    private static final String CLASS_RESOURCE_PATTERN = "classpath*:com/aiplatform/**/*.class";
    private static final String COMPONENT = Component.class.getName();
    private static final String CONFIGURATION_PROPERTIES = ConfigurationProperties.class.getName();
    private static final String JAKARTA_INJECT = "jakarta.inject.Inject";
    private static final String JAKARTA_NAMED = "jakarta.inject.Named";
    private static final String JAKARTA_MANAGED_BEAN = "jakarta.annotation.ManagedBean";
    private static final Set<String> REPAIRED_MANAGED_TYPES = Set.of(
            "com.aiplatform.knowledge.application.job.KnowledgeDocumentJobLeaseHeartbeat",
            "com.aiplatform.knowledge.application.job.KnowledgeDocumentJobWorker",
            "com.aiplatform.knowledge.application.maintenance.KnowledgeDocumentMaintenanceWorker",
            "com.aiplatform.knowledge.infrastructure.storage.MinioDocumentObjectDeletionAdapter",
            "com.aiplatform.infra.springai.model.provider.ollama.OllamaChatModelFactory");
    private static final Map<String, String> RUNTIME_MODULE_PACKAGE_SENTINELS = Map.ofEntries(
            Map.entry("shared-kernel", "com.aiplatform.shared.kernel."),
            Map.entry("shared-observability", "com.aiplatform.shared.observability."),
            Map.entry("shared-web", "com.aiplatform.shared.web."),
            Map.entry("shared-security", "com.aiplatform.shared.security."),
            Map.entry("shared-audit", "com.aiplatform.shared.audit."),
            Map.entry("capability-contracts", "com.aiplatform.contracts.capability."),
            Map.entry("infra-persistence", "com.aiplatform.infra.persistence."),
            Map.entry("infra-vector", "com.aiplatform.infra.vector."),
            Map.entry("infra-redis", "com.aiplatform.infra.redis."),
            Map.entry("infra-object-storage", "com.aiplatform.infra.objectstorage."),
            Map.entry("infra-rocketmq", "com.aiplatform.infra.rocketmq."),
            Map.entry("infra-satoken", "com.aiplatform.infra.satoken."),
            Map.entry("infra-spring-ai", "com.aiplatform.infra.springai."),
            Map.entry("identity-access", "com.aiplatform.identity."),
            Map.entry("model-hub", "com.aiplatform.modelhub."),
            Map.entry("knowledge-base", "com.aiplatform.knowledge."),
            Map.entry("app-studio", "com.aiplatform.appstudio."),
            Map.entry("approval-center", "com.aiplatform.approval."),
            Map.entry("observability-audit", "com.aiplatform.audit."),
            Map.entry("summary-ops", "com.aiplatform.summary."),
            Map.entry("platform-boot", "com.aiplatform.boot."));

    @Test
    void everyProductionManagedTypeHasDeterministicConstructorSelection() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver(classLoader);
        CachingMetadataReaderFactory metadataReaders =
                new CachingMetadataReaderFactory(resolver);
        Set<String> classpathTypeNames = new TreeSet<>();
        Set<String> managedTypeNames = new TreeSet<>();

        for (Resource resource : resolver.getResources(CLASS_RESOURCE_PATTERN)) {
            if (!resource.isReadable() || isTestClassResource(resource)) {
                continue;
            }
            AnnotationMetadata metadata = metadataReaders.getMetadataReader(resource).getAnnotationMetadata();
            classpathTypeNames.add(metadata.getClassName());
            if (isManagedType(metadata)) {
                managedTypeNames.add(metadata.getClassName());
            }
        }

        List<String> violations = new ArrayList<>();
        for (String className : managedTypeNames) {
            Class<?> managedType = Class.forName(className, false, classLoader);
            constructorSelectionViolation(managedType).ifPresent(violations::add);
        }

        violations.sort(Comparator.naturalOrder());
        assertThat(managedTypeNames)
                .as("production Spring stereotype discovery must not silently scan an empty classpath")
                .isNotEmpty();
        assertThat(managedTypeNames)
                .as("every constructor repaired after the real Spring 7 startup failure must remain discoverable")
                .containsAll(REPAIRED_MANAGED_TYPES);
        for (Map.Entry<String, String> sentinel : RUNTIME_MODULE_PACKAGE_SENTINELS.entrySet()) {
            assertThat(classpathTypeNames)
                    .as("platform-boot runtime classpath must include module %s", sentinel.getKey())
                    .anyMatch(className -> className.startsWith(sentinel.getValue()));
        }
        assertThat(violations)
                .as("Spring 7 managed types must expose one required injection constructor or one default constructor")
                .isEmpty();
    }

    @Test
    void rejectsMultipleUnannotatedConstructorsWithoutDefaultConstructor() {
        assertThat(constructorSelectionViolation(AmbiguousConstructorFixture.class))
                .hasValueSatisfying(violation -> assertThat(violation)
                        .contains(AmbiguousConstructorFixture.class.getName())
                        .contains("java.lang.String")
                        .contains("java.lang.Integer"));
    }

    @Test
    void acceptsOneExplicitConfigurationPropertiesBindingConstructor() {
        assertThat(constructorSelectionViolation(ExplicitlyBoundConfigurationFixture.class)).isEmpty();
    }

    private static boolean isManagedType(AnnotationMetadata metadata) {
        return metadata.isIndependent()
                && (hasAnnotationOrMetaAnnotation(metadata, COMPONENT)
                        || hasAnnotationOrMetaAnnotation(metadata, CONFIGURATION_PROPERTIES)
                        || hasAnnotationOrMetaAnnotation(metadata, JAKARTA_NAMED)
                        || hasAnnotationOrMetaAnnotation(metadata, JAKARTA_MANAGED_BEAN));
    }

    private static boolean hasAnnotationOrMetaAnnotation(
            AnnotationMetadata metadata, String annotationName) {
        return metadata.hasAnnotation(annotationName) || metadata.hasMetaAnnotation(annotationName);
    }

    private static java.util.Optional<String> constructorSelectionViolation(Class<?> managedType) {
        if (managedType.isInterface()
                || Modifier.isAbstract(managedType.getModifiers())
                || managedType.isAnnotation()
                || managedType.isEnum()) {
            return java.util.Optional.empty();
        }
        List<Constructor<?>> constructors = Arrays.stream(managedType.getDeclaredConstructors())
                .filter(constructor -> !constructor.isSynthetic())
                .toList();
        if (constructors.size() <= 1) {
            return java.util.Optional.empty();
        }

        long requiredInjectionConstructors = constructors.stream()
                .filter(SpringManagedConstructorContractTest::isRequiredInjectionConstructor)
                .count();
        long anyInjectionConstructors = constructors.stream()
                .filter(SpringManagedConstructorContractTest::isAnyInjectionConstructor)
                .count();
        long defaultConstructors =
                constructors.stream().filter(constructor -> constructor.getParameterCount() == 0).count();
        boolean configurationProperties =
                MergedAnnotations.from(managedType).isPresent(ConfigurationProperties.class);
        long bindingConstructors = constructors.stream()
                .filter(constructor -> MergedAnnotations.from(constructor)
                        .isPresent(ConstructorBinding.class))
                .count();
        boolean unambiguous = (requiredInjectionConstructors == 1 && anyInjectionConstructors == 1)
                || (anyInjectionConstructors == 0 && defaultConstructors == 1)
                || (configurationProperties
                        && anyInjectionConstructors == 0
                        && bindingConstructors == 1);
        if (unambiguous) {
            return java.util.Optional.empty();
        }

        String signatures = constructors.stream()
                .map(Constructor::toGenericString)
                .sorted()
                .reduce((left, right) -> left + "; " + right)
                .orElse("<none>");
        return java.util.Optional.of(managedType.getName()
                + " requiredInjectionConstructors=" + requiredInjectionConstructors
                + ", anyInjectionConstructors=" + anyInjectionConstructors
                + ", defaultConstructors=" + defaultConstructors
                + ", bindingConstructors=" + bindingConstructors
                + ", configurationProperties=" + configurationProperties
                + ", constructors=[" + signatures + "]");
    }

    private static boolean isRequiredInjectionConstructor(Constructor<?> constructor) {
        MergedAnnotations annotations = MergedAnnotations.from(constructor);
        MergedAnnotation<Autowired> autowired = annotations.get(Autowired.class);
        if (autowired.isPresent()) {
            return autowired.getBoolean("required");
        }
        return annotations.isPresent(JAKARTA_INJECT);
    }

    private static boolean isAnyInjectionConstructor(Constructor<?> constructor) {
        MergedAnnotations annotations = MergedAnnotations.from(constructor);
        return annotations.isPresent(Autowired.class) || annotations.isPresent(JAKARTA_INJECT);
    }

    private static boolean isTestClassResource(Resource resource) {
        String description = resource.getDescription();
        return description.contains("/test-classes/") || description.contains("\\test-classes\\");
    }

    private static final class AmbiguousConstructorFixture {
        private AmbiguousConstructorFixture(String value) {}

        private AmbiguousConstructorFixture(Integer value) {}
    }

    @ConfigurationProperties("aip.constructor-contract-fixture")
    private static final class ExplicitlyBoundConfigurationFixture {
        @ConstructorBinding
        private ExplicitlyBoundConfigurationFixture(String value) {}

        private ExplicitlyBoundConfigurationFixture(Integer value) {}
    }
}
