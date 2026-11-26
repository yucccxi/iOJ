package me.iynotuo.judge.judge.service;

import me.iynotuo.judge.problem.entity.ProblemTestcaseBundle;
import me.iynotuo.judge.problem.mapper.ProblemTestcaseBundleMapper;
import me.iynotuo.judge.protocol.JudgeTestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestcaseBundleHashServiceTest {

    private static final List<JudgeTestCase> CASES = List.of(new JudgeTestCase(1, "1 2\n", "3\n"));

    @Mock
    private ProblemTestcaseBundleMapper bundleMapper;

    @Mock
    private JudgeTestcaseBundleWriter bundleWriter;

    @InjectMocks
    private TestcaseBundleHashService service;

    @Test
    void returnsCachedHashWithoutComputing() throws Exception {
        when(bundleMapper.findByProblemId(1L)).thenReturn(bundle("cached-hash", 3L));

        assertThat(service.getOrCompute(1L, CASES, 3L)).isEqualTo("cached-hash");
        verifyNoInteractions(bundleWriter);
    }

    @Test
    void computesAndCachesHashWhenMissing() throws Exception {
        when(bundleMapper.findByProblemId(1L)).thenReturn(bundle(null, 2L));
        when(bundleWriter.sha256(CASES)).thenReturn("new-hash");
        when(bundleMapper.updateHashIfVersion(eq(1L), eq("new-hash"), eq(2L), any(LocalDateTime.class)))
                .thenReturn(1);

        assertThat(service.getOrCompute(1L, CASES, 2L)).isEqualTo("new-hash");

        verify(bundleMapper).ensureRow(eq(1L), eq(2L), any(LocalDateTime.class));
        verify(bundleMapper).updateHashIfVersion(eq(1L), eq("new-hash"), eq(2L), any(LocalDateTime.class));
    }

    @Test
    void doesNotCacheWhenTestcasesChangedDuringComputation() throws Exception {
        when(bundleMapper.findByProblemId(1L)).thenReturn(bundle(null, 3L));
        when(bundleWriter.sha256(CASES)).thenReturn("new-hash");

        assertThat(service.getOrCompute(1L, CASES, 2L)).isEqualTo("new-hash");

        verify(bundleMapper, never()).ensureRow(anyLong(), anyLong(), any(LocalDateTime.class));
        verify(bundleMapper, never()).updateHashIfVersion(
                anyLong(), anyString(), anyLong(), any(LocalDateTime.class));
    }

    @Test
    void hashesOnceWhenTheSameProblemIsClaimedConcurrently() throws Exception {
        CountDownLatch hashingStarted = new CountDownLatch(1);
        CountDownLatch releaseHashing = new CountDownLatch(1);
        AtomicReference<ProblemTestcaseBundle> row = new AtomicReference<>(bundle(null, 0L));

        when(bundleMapper.findByProblemId(1L)).thenAnswer(invocation -> row.get());
        when(bundleWriter.sha256(CASES)).thenAnswer(invocation -> {
            hashingStarted.countDown();
            releaseHashing.await(5, TimeUnit.SECONDS);
            return "new-hash";
        });
        when(bundleMapper.updateHashIfVersion(eq(1L), eq("new-hash"), anyLong(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    ProblemTestcaseBundle current = row.get();
                    if (current.getBundleSha256() == null
                            && Objects.equals(current.getTestcaseVersion(), invocation.getArgument(2))) {
                        row.set(bundle("new-hash", current.getTestcaseVersion()));
                        return 1;
                    }
                    return 0;
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> service.getOrCompute(1L, CASES, 0L));
            assertThat(hashingStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<String> second = executor.submit(() -> service.getOrCompute(1L, CASES, 0L));
            releaseHashing.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("new-hash");
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("new-hash");
        } finally {
            executor.shutdownNow();
        }

        verify(bundleWriter, times(1)).sha256(CASES);
    }

    @Test
    void invalidatesTheProblemCache() {
        service.invalidate(1L);

        verify(bundleMapper).invalidate(eq(1L), any(LocalDateTime.class));
    }

    private ProblemTestcaseBundle bundle(String hash, long version) {
        ProblemTestcaseBundle bundle = new ProblemTestcaseBundle();
        bundle.setProblemId(1L);
        bundle.setBundleSha256(hash);
        bundle.setTestcaseVersion(version);
        bundle.setUpdatedAt(LocalDateTime.now());
        return bundle;
    }
}
