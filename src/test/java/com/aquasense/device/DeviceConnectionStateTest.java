package com.aquasense.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.aquasense.common.BusinessException;
import com.aquasense.device.connection.ConnectionSession;
import com.aquasense.device.connection.DeviceConnection;
import com.aquasense.device.connection.DeviceListener;
import com.aquasense.device.connection.DeviceSnapshot;
import com.aquasense.device.connection.DeviceState;
import com.aquasense.device.hardware.AquaSenseFirmwareAdapter;
import com.aquasense.device.hardware.DeviceException;
import com.aquasense.device.hardware.PhReading;
import com.aquasense.device.hardware.SimulatedPhMeter;
import com.aquasense.support.FakeSerialLink;
import com.aquasense.support.TestData;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DeviceConnectionStateTest {

    private final DeviceConnection connection = new DeviceConnection(TestData.properties(null, 5));
    private final ConnectionSession session = new ConnectionSession(1L, 10L, "Simulador");

    @AfterEach
    void tearDown() {
        connection.shutdown();
    }

    @Test
    void disconnectedStateRejectsMeasuring() {
        assertThat(connection.currentState()).isEqualTo(DeviceState.DISCONNECTED);
        assertThatThrownBy(() -> connection.measure(3)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("Desconectado");
    }

    @Test
    void fullLifecycleTransitionsThroughStates() {
        connection.connect(new SimulatedPhMeter(7.2, 1), session);
        assertThat(connection.currentState()).isEqualTo(DeviceState.CONNECTED);
        assertThat(connection.measure(3).ph()).isBetween(6.5, 8.0);
        connection.startLive();
        assertThat(connection.currentState()).isEqualTo(DeviceState.LIVE);
        assertThatThrownBy(() -> connection.connect(new SimulatedPhMeter(7, 2), session)).isInstanceOf(BusinessException.class);
        connection.stopLive();
        assertThat(connection.currentState()).isEqualTo(DeviceState.CONNECTED);
        connection.disconnect();
        assertThat(connection.currentState()).isEqualTo(DeviceState.DISCONNECTED);
    }

    @Test
    void liveModeNotifiesObservers() {
        List<PhReading> readings = new CopyOnWriteArrayList<>();
        connection.addListener(new DeviceListener() {
            @Override
            public void onReading(ConnectionSession s, PhReading reading, boolean simulated) {
                readings.add(reading);
            }

            @Override
            public void onStateChanged(DeviceSnapshot snapshot) {
            }
        });
        connection.connect(new SimulatedPhMeter(7.2, 3), session);
        connection.startLive();
        await().atMost(Duration.ofSeconds(3)).until(() -> readings.size() >= 3);
    }

    @Test
    void communicationFailureMovesToErrorStateAndAllowsReconnect() {
        FakeSerialLink link = new FakeSerialLink().respond("PONG");
        connection.connect(new AquaSenseFirmwareAdapter(link), session);
        assertThatThrownBy(() -> connection.measure(1)).isInstanceOf(DeviceException.class);
        assertThat(connection.currentState()).isEqualTo(DeviceState.ERROR);
        assertThat(connection.snapshot().lastError()).contains("no respondió");
        connection.connect(new SimulatedPhMeter(7.2, 4), session);
        assertThat(connection.currentState()).isEqualTo(DeviceState.CONNECTED);
    }
}
