package com.nss;

import com.nss.ddd.infrastructure.distributed.redisson.DistributedLockService;
import com.nss.ddd.infrastructure.distributed.redisson.impl.RedissonDistributedLockServiceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisConnectionException;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RedissonDistributedLockServiceImpl} với {@link RedissonClient}/{@link RLock} mock (backlog
 * 0035 Phase 4) — kiểm cả bốn nhánh: lấy được khoá, không lấy được khoá (timeout), lỗi hạ tầng (Redis
 * không tới được), và không {@code unlock()} một khoá không còn thuộc về mình.
 */
class RedissonDistributedLockServiceImplTest {

    private final RedissonClient redissonClient = mock(RedissonClient.class);

    private final DistributedLockService lockService = new RedissonDistributedLockServiceImpl(redissonClient);

    @Test
    @DisplayName("Lay duoc khoa -> chay action, unlock o finally")
    void runsActionWhenLockAcquired() throws Exception {
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("LOCK:X")).thenReturn(lock);
        when(lock.tryLock(1, 5, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isLocked()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = lockService.executeWithLock("LOCK:X", () -> "action", () -> "fallback");

        assertEquals("action", result);
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("Khong lay duoc khoa trong thoi gian cho -> chay fallback, KHONG unlock")
    void runsFallbackWhenLockNotAcquired() throws Exception {
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("LOCK:X")).thenReturn(lock);
        when(lock.tryLock(1, 5, TimeUnit.SECONDS)).thenReturn(false);

        String result = lockService.executeWithLock("LOCK:X", () -> "action", () -> "fallback");

        assertEquals("fallback", result);
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("Redis khong toi duoc (RedisConnectionException) -> chay fallback, KHONG nem exception")
    void runsFallbackWhenRedisUnreachable() {
        when(redissonClient.getLock("LOCK:X")).thenThrow(new RedisConnectionException("khong ket noi duoc"));

        String result = lockService.executeWithLock("LOCK:X", () -> "action", () -> "fallback");

        assertEquals("fallback", result);
    }

    @Test
    @DisplayName("Da lay duoc khoa nhung het lease truoc finally -> KHONG goi unlock tren khoa cua nguoi khac")
    void doesNotUnlockWhenNoLongerHeldByCurrentThread() throws Exception {
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("LOCK:X")).thenReturn(lock);
        when(lock.tryLock(1, 5, TimeUnit.SECONDS)).thenReturn(true);
        // Redisson da tu nha khoa (het lease) truoc khi chay toi finally — isHeldByCurrentThread=false.
        when(lock.isLocked()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(false);

        String result = lockService.executeWithLock("LOCK:X", () -> "action", () -> "fallback");

        assertEquals("action", result);
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("unlock ban than nem loi -> nuot, khong lam gay ket qua da co")
    void swallowsUnlockFailure() throws Exception {
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("LOCK:X")).thenReturn(lock);
        when(lock.tryLock(1, 5, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isLocked()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        doThrow(new RedisConnectionException("mat ket noi luc unlock")).when(lock).unlock();

        String result = lockService.executeWithLock("LOCK:X", () -> "action", () -> "fallback");

        assertEquals("action", result);
    }
}
