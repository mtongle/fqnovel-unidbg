package com.anjia.unidbgserver.service;

import com.anjia.unidbgserver.dto.DeviceInfo;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FQNovelServiceRetryPolicyTest {

    @Test
    void resolveMaxAttemptsUsesPoolSizeWhenNoRequestedDevice() throws Exception {
        FQNovelService service = new FQNovelService(mock(org.springframework.web.client.RestTemplate.class), mock(com.fasterxml.jackson.databind.ObjectMapper.class));
        DevicePoolService devicePoolService = mock(DevicePoolService.class);
        when(devicePoolService.getTargetPoolSize()).thenReturn(5);
        ReflectionTestUtils.setField(service, "devicePoolService", devicePoolService);

        Method method = FQNovelService.class.getDeclaredMethod("resolveMaxAttempts", DeviceInfo.class);
        method.setAccessible(true);
        int maxAttempts = (int) method.invoke(service, (DeviceInfo) null);

        assertEquals(6, maxAttempts);
        verify(devicePoolService, times(1)).getTargetPoolSize();
    }

    @Test
    void resolveRequestDeviceReturnsNullWhenRequestDeviceIdMissing() throws Exception {
        FQNovelService service = new FQNovelService(mock(org.springframework.web.client.RestTemplate.class), mock(com.fasterxml.jackson.databind.ObjectMapper.class));
        DevicePoolService devicePoolService = mock(DevicePoolService.class);
        ReflectionTestUtils.setField(service, "devicePoolService", devicePoolService);

        Method method = FQNovelService.class.getDeclaredMethod("resolveRequestDevice", String.class, String.class);
        method.setAccessible(true);
        DeviceInfo result = (DeviceInfo) method.invoke(service, null, "testScene");

        assertNull(result);
        verify(devicePoolService, never()).nextDevice();
        verify(devicePoolService, never()).findDeviceById(anyString());
    }

    @Test
    void resolveRequestDeviceReturnsNullWhenSpecifiedDeviceNotFound() throws Exception {
        FQNovelService service = new FQNovelService(mock(org.springframework.web.client.RestTemplate.class), mock(com.fasterxml.jackson.databind.ObjectMapper.class));
        DevicePoolService devicePoolService = mock(DevicePoolService.class);
        when(devicePoolService.findDeviceById("not-found")).thenReturn(null);
        ReflectionTestUtils.setField(service, "devicePoolService", devicePoolService);

        Method method = FQNovelService.class.getDeclaredMethod("resolveRequestDevice", String.class, String.class);
        method.setAccessible(true);
        DeviceInfo result = (DeviceInfo) method.invoke(service, "not-found", "testScene");

        assertNull(result);
        verify(devicePoolService, times(1)).findDeviceById("not-found");
        verify(devicePoolService, never()).nextDevice();
    }
    @Test
    void illegalAccessRecoveryTriggersTripleActionsWhenThresholdReached() throws Exception {
        FQNovelService service = new FQNovelService(mock(org.springframework.web.client.RestTemplate.class), mock(com.fasterxml.jackson.databind.ObjectMapper.class));
        FQRegisterKeyService registerKeyService = mock(FQRegisterKeyService.class);
        DevicePoolService devicePoolService = mock(DevicePoolService.class);
        FQEncryptServiceWorker encryptServiceWorker = mock(FQEncryptServiceWorker.class);

        ReflectionTestUtils.setField(service, "registerKeyService", registerKeyService);
        ReflectionTestUtils.setField(service, "devicePoolService", devicePoolService);
        ReflectionTestUtils.setField(service, "fqEncryptServiceWorker", encryptServiceWorker);

        DeviceInfo deviceInfo = DeviceInfo.builder().deviceId("device-1").build();
        when(registerKeyService.getKeyRegisterTs(deviceInfo)).thenReturn(0L);

        Method method = FQNovelService.class.getDeclaredMethod(
            "onIllegalAccessDetected",
            DeviceInfo.class,
            DeviceInfo.class,
            int.class,
            int.class,
            int.class
        );
        method.setAccessible(true);

        int firstHit = (int) method.invoke(service, deviceInfo, null, 1, 3, 0);
        assertEquals(1, firstHit);
        verify(registerKeyService, never()).clearCache();
        verify(devicePoolService, never()).rebuildPool();
        verify(encryptServiceWorker, never()).reset();

        int secondHit = (int) method.invoke(service, deviceInfo, null, 2, 3, firstHit);
        assertEquals(0, secondHit);
        verify(registerKeyService, times(1)).clearCache();
        verify(devicePoolService, times(1)).rebuildPool();
        verify(encryptServiceWorker, times(1)).reset();
    }

    @Test
    void nonIllegalAccessResponseDoesNotTriggerRecovery() throws Exception {
        FQNovelService service = new FQNovelService(mock(org.springframework.web.client.RestTemplate.class), mock(com.fasterxml.jackson.databind.ObjectMapper.class));
        FQRegisterKeyService registerKeyService = mock(FQRegisterKeyService.class);
        DevicePoolService devicePoolService = mock(DevicePoolService.class);
        FQEncryptServiceWorker encryptServiceWorker = mock(FQEncryptServiceWorker.class);

        ReflectionTestUtils.setField(service, "registerKeyService", registerKeyService);
        ReflectionTestUtils.setField(service, "devicePoolService", devicePoolService);
        ReflectionTestUtils.setField(service, "fqEncryptServiceWorker", encryptServiceWorker);

        Method method = FQNovelService.class.getDeclaredMethod(
            "handleIllegalAccessRecoveryIfNeeded",
            String.class,
            DeviceInfo.class,
            DeviceInfo.class,
            int.class,
            int.class,
            int.class
        );
        method.setAccessible(true);

        int state = (int) method.invoke(service, "{\"code\":0,\"message\":\"success\"}", null, null, 1, 3, 0);
        assertEquals(-1, state);

        verify(registerKeyService, never()).clearCache();
        verify(devicePoolService, never()).rebuildPool();
        verify(encryptServiceWorker, never()).reset();
    }
}
