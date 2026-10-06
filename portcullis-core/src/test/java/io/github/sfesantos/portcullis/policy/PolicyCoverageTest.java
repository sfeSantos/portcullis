package io.github.sfesantos.portcullis.policy;

import io.github.sfesantos.portcullis.PolicyDefinitionException;
import io.github.sfesantos.portcullis.policy.coverage.DocumentService;
import io.github.sfesantos.portcullis.policy.coverage.LockedApi;
import io.github.sfesantos.portcullis.policy.coverage.MixedApi;
import io.github.sfesantos.portcullis.policy.coverage.OrderView;
import io.github.sfesantos.portcullis.policy.coverage.nested.NestedApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyCoverageTest {

    private static final String FIXTURES = "io.github.sfesantos.portcullis.policy.coverage";

    @Test
    void reportsPublicMethodsWithNoCheckThatNeedsAUser() {
        assertThat(names(PolicyCoverage.of(MixedApi.class).uncovered()))
                .containsExactly("MixedApi#forgotten", "MixedApi#onlyLimited");
    }

    @Test
    void typeLevelCheckCoversEveryPublicMethod() {
        assertThat(PolicyCoverage.of(LockedApi.class).uncovered()).isEmpty();
        assertThatCode(() -> PolicyCoverage.of(LockedApi.class).requireCovered()).doesNotThrowAnyException();
    }

    @Test
    void annotationOnTheInterfaceDoesNotCoverTheImplementation() {
        assertThat(names(PolicyCoverage.of(DocumentService.class).uncovered()))
                .containsExactly("DocumentService#read");
    }

    @Test
    void recordsAreSkipped() {
        assertThat(PolicyCoverage.of(OrderView.class).uncovered()).isEmpty();
    }

    @Test
    void packageScanIncludesSubpackages() {
        assertThat(names(PolicyCoverage.of(FIXTURES).uncovered())).containsExactly(
                "DocumentService#read",
                "MixedApi#forgotten",
                "MixedApi#onlyLimited",
                "NestedApi#list");
    }

    @Test
    void packagesAndClassesCombine() {
        var coverage = PolicyCoverage.of(FIXTURES + ".nested").and(LockedApi.class, MixedApi.class);

        assertThat(names(coverage.uncovered())).containsExactly("NestedApi#list", "MixedApi#forgotten", "MixedApi#onlyLimited");
    }

    @Test
    void requireCoveredNamesEveryUncoveredMethod() {
        assertThatThrownBy(() -> PolicyCoverage.of(MixedApi.class, NestedApi.class).requireCovered())
                .isInstanceOf(PolicyDefinitionException.class)
                .hasMessageContaining(MixedApi.class.getName() + "#forgotten()")
                .hasMessageContaining(MixedApi.class.getName() + "#onlyLimited()")
                .hasMessageContaining(NestedApi.class.getName() + "#list()");
    }

    @Test
    void unknownPackageFailsInsteadOfPassing() {
        assertThatThrownBy(() -> PolicyCoverage.of("io.github.sfesantos.portcullis.nothing.here"))
                .isInstanceOf(PolicyDefinitionException.class)
                .hasMessageContaining("io.github.sfesantos.portcullis.nothing.here");
    }

    @Test
    void scansPackagesInsideJars() {
        assertThat(ClassPathScanner.classesIn("org.junit.jupiter.api.io")).contains(TempDir.class);
    }

    private static List<String> names(List<Method> methods) {
        return methods.stream()
                .map(m -> m.getDeclaringClass().getSimpleName() + "#" + m.getName())
                .toList();
    }
}
