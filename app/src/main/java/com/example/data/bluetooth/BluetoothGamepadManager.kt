package com.example.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.example.core.model.GamepadButton
import com.example.core.model.SpadInputState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

sealed class BluetoothControllerState {
    object Disabled : BluetoothControllerState()
    data class Unsupported(val reason: String) : BluetoothControllerState()
    data class ReadyToPair(val localName: String) : BluetoothControllerState()
    data class Connected(val deviceName: String, val address: String) : BluetoothControllerState()
    data class Error(val message: String) : BluetoothControllerState()
}

class BluetoothGamepadManager(private val context: Context) {

    private val _state = MutableStateFlow<BluetoothControllerState>(BluetoothControllerState.Disabled)
    val state: StateFlow<BluetoothControllerState> = _state.asStateFlow()

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        private const val REPORT_ID_GAMEPAD = 1.toByte()

        // Standard HID Gamepad Descriptor (compatible with Samsung Tizen, Windows, Linux, Android)
        val HID_DESCRIPTOR = byteArrayOf(
            0x05.toByte(), 0x01.toByte(), // USAGE_PAGE (Generic Desktop)
            0x09.toByte(), 0x05.toByte(), // USAGE (Gamepad)
            0xa1.toByte(), 0x01.toByte(), // COLLECTION (Application)
            0x85.toByte(), REPORT_ID_GAMEPAD, //   REPORT_ID (1)
            // 16 Gamepad Buttons
            0x05.toByte(), 0x09.toByte(), //   USAGE_PAGE (Button)
            0x19.toByte(), 0x01.toByte(), //   USAGE_MINIMUM (1)
            0x29.toByte(), 0x10.toByte(), //   USAGE_MAXIMUM (16)
            0x15.toByte(), 0x00.toByte(), //   LOGICAL_MINIMUM (0)
            0x25.toByte(), 0x01.toByte(), //   LOGICAL_MAXIMUM (1)
            0x75.toByte(), 0x01.toByte(), //   REPORT_SIZE (1)
            0x95.toByte(), 0x10.toByte(), //   REPORT_COUNT (16)
            0x81.toByte(), 0x02.toByte(), //   INPUT (Data,Var,Abs)
            // Hat switch (D-Pad: 4 bits, 0=neutral, 1-8=directions)
            0x05.toByte(), 0x01.toByte(), //   USAGE_PAGE (Generic Desktop)
            0x09.toByte(), 0x39.toByte(), //   USAGE (Hat switch)
            0x15.toByte(), 0x01.toByte(), //   LOGICAL_MINIMUM (1)
            0x25.toByte(), 0x08.toByte(), //   LOGICAL_MAXIMUM (8)
            0x35.toByte(), 0x00.toByte(), //   PHYSICAL_MINIMUM (0)
            0x46.toByte(), 0x3b.toByte(), 0x01.toByte(), // PHYSICAL_MAXIMUM (315)
            0x65.toByte(), 0x14.toByte(), //   UNIT (Eng Rot:Angular Pos)
            0x75.toByte(), 0x04.toByte(), //   REPORT_SIZE (4)
            0x95.toByte(), 0x01.toByte(), //   REPORT_COUNT (1)
            0x81.toByte(), 0x42.toByte(), //   INPUT (Data,Var,Abs,Null)
            0x75.toByte(), 0x04.toByte(), //   REPORT_SIZE (4) padding
            0x95.toByte(), 0x01.toByte(), //   REPORT_COUNT (1)
            0x81.toByte(), 0x03.toByte(), //   INPUT (Const,Var,Abs)
            // Left Stick (X, Y: -127 to 127)
            0x05.toByte(), 0x01.toByte(), //   USAGE_PAGE (Generic Desktop)
            0x09.toByte(), 0x30.toByte(), //   USAGE (X)
            0x09.toByte(), 0x31.toByte(), //   USAGE (Y)
            0x15.toByte(), 0x81.toByte(), //   LOGICAL_MINIMUM (-127)
            0x25.toByte(), 0x7f.toByte(), //   LOGICAL_MAXIMUM (127)
            0x75.toByte(), 0x08.toByte(), //   REPORT_SIZE (8)
            0x95.toByte(), 0x02.toByte(), //   REPORT_COUNT (2)
            0x81.toByte(), 0x02.toByte(), //   INPUT (Data,Var,Abs)
            // Right Stick (Z, Rz: -127 to 127)
            0x09.toByte(), 0x32.toByte(), //   USAGE (Z)
            0x09.toByte(), 0x35.toByte(), //   USAGE (Rz)
            0x15.toByte(), 0x81.toByte(), //   LOGICAL_MINIMUM (-127)
            0x25.toByte(), 0x7f.toByte(), //   LOGICAL_MAXIMUM (127)
            0x75.toByte(), 0x08.toByte(), //   REPORT_SIZE (8)
            0x95.toByte(), 0x02.toByte(), //   REPORT_COUNT (2)
            0x81.toByte(), 0x02.toByte(), //   INPUT (Data,Var,Abs)
            // Triggers (Brake, Accelerator: 0 to 255)
            0x05.toByte(), 0x02.toByte(), //   USAGE_PAGE (Simulation Controls)
            0x09.toByte(), 0xc5.toByte(), //   USAGE (Brake / LT)
            0x09.toByte(), 0xc4.toByte(), //   USAGE (Accelerator / RT)
            0x15.toByte(), 0x00.toByte(), //   LOGICAL_MINIMUM (0)
            0x26.toByte(), 0xff.toByte(), 0x00.toByte(), // LOGICAL_MAXIMUM (255)
            0x75.toByte(), 0x08.toByte(), //   REPORT_SIZE (8)
            0x95.toByte(), 0x02.toByte(), //   REPORT_COUNT (2)
            0x81.toByte(), 0x02.toByte(), //   INPUT (Data,Var,Abs)
            0xc0.toByte()                // END_COLLECTION
        )
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        @SuppressLint("MissingPermission")
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE && proxy is BluetoothHidDevice) {
                hidDevice = proxy
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                _state.value = BluetoothControllerState.Disabled
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        @SuppressLint("MissingPermission")
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (registered) {
                val localName = try { bluetoothAdapter?.name ?: "S!pad Gamepad" } catch (_: Exception) { "S!pad Gamepad" }
                if (pluggedDevice != null) {
                    connectedDevice = pluggedDevice
                    val name = try { pluggedDevice.name ?: pluggedDevice.address } catch (_: Exception) { pluggedDevice.address }
                    _state.value = BluetoothControllerState.Connected(name, pluggedDevice.address)
                } else {
                    _state.value = BluetoothControllerState.ReadyToPair(localName)
                }
            } else {
                _state.value = BluetoothControllerState.Disabled
            }
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    val devName = try { device?.name ?: device?.address ?: "Samsung TV" } catch (_: Exception) { "Samsung TV" }
                    val addr = device?.address ?: ""
                    _state.value = BluetoothControllerState.Connected(devName, addr)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevice = null
                    val localName = try { bluetoothAdapter?.name ?: "S!pad Gamepad" } catch (_: Exception) { "S!pad Gamepad" }
                    _state.value = BluetoothControllerState.ReadyToPair(localName)
                }
            }
        }
    }

    fun startBluetoothController() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _state.value = BluetoothControllerState.Unsupported("Bluetooth is disabled on this device. Please turn Bluetooth ON in device settings.")
            return
        }

        try {
            val success = bluetoothAdapter.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
            if (!success) {
                _state.value = BluetoothControllerState.Unsupported("This phone's chipset does not expose Bluetooth HID Device peripheral mode.")
            }
        } catch (e: Exception) {
            _state.value = BluetoothControllerState.Error(e.message ?: "Failed to initialize Bluetooth HID Device")
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "S!pad Gamepad",
            "Virtual Gamepad for Samsung TV",
            "S!pad",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HID_DESCRIPTOR
        )
        val qos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800,
            9,
            0,
            11250,
            BluetoothHidDeviceAppQosSettings.MAX
        )

        hidDevice?.registerApp(sdp, null, qos, executor, hidCallback)
    }

    @SuppressLint("MissingPermission")
    fun sendInputState(state: SpadInputState) {
        val dev = connectedDevice ?: return
        val hid = hidDevice ?: return

        // 16 button bits
        var buttonsMask = 0
        if (state.pressedButtons.contains(GamepadButton.A)) buttonsMask = buttonsMask or (1 shl 0)
        if (state.pressedButtons.contains(GamepadButton.B)) buttonsMask = buttonsMask or (1 shl 1)
        if (state.pressedButtons.contains(GamepadButton.X)) buttonsMask = buttonsMask or (1 shl 2)
        if (state.pressedButtons.contains(GamepadButton.Y)) buttonsMask = buttonsMask or (1 shl 3)
        if (state.pressedButtons.contains(GamepadButton.L1)) buttonsMask = buttonsMask or (1 shl 4)
        if (state.pressedButtons.contains(GamepadButton.R1)) buttonsMask = buttonsMask or (1 shl 5)
        if (state.pressedButtons.contains(GamepadButton.SELECT)) buttonsMask = buttonsMask or (1 shl 6)
        if (state.pressedButtons.contains(GamepadButton.START)) buttonsMask = buttonsMask or (1 shl 7)
        if (state.pressedButtons.contains(GamepadButton.GUIDE)) buttonsMask = buttonsMask or (1 shl 8)
        if (state.pressedButtons.contains(GamepadButton.L3)) buttonsMask = buttonsMask or (1 shl 9)
        if (state.pressedButtons.contains(GamepadButton.R3)) buttonsMask = buttonsMask or (1 shl 10)

        // D-Pad Hat Switch (1=Up, 2=Up-Right, 3=Right, 4=Down-Right, 5=Down, 6=Down-Left, 7=Left, 8=Up-Left, 0=None)
        val up = state.pressedButtons.contains(GamepadButton.DPAD_UP)
        val down = state.pressedButtons.contains(GamepadButton.DPAD_DOWN)
        val left = state.pressedButtons.contains(GamepadButton.DPAD_LEFT)
        val right = state.pressedButtons.contains(GamepadButton.DPAD_RIGHT)

        val hat: Byte = when {
            up && right -> 2
            down && right -> 4
            down && left -> 6
            up && left -> 8
            up -> 1
            right -> 3
            down -> 5
            left -> 7
            else -> 0
        }.toByte()

        // Left Stick (-127 to 127)
        val lx = (state.leftStick.x * 127f).coerceIn(-127f, 127f).toInt().toByte()
        val ly = (state.leftStick.y * -127f).coerceIn(-127f, 127f).toInt().toByte()

        // Right Stick (-127 to 127)
        val rx = (state.rightStick.x * 127f).coerceIn(-127f, 127f).toInt().toByte()
        val ry = (state.rightStick.y * -127f).coerceIn(-127f, 127f).toInt().toByte()

        // Triggers (0 to 255)
        val lt = (state.leftTrigger * 255f).coerceIn(0f, 255f).toInt().toByte()
        val rt = (state.rightTrigger * 255f).coerceIn(0f, 255f).toInt().toByte()

        val report = byteArrayOf(
            (buttonsMask and 0xFF).toByte(),
            ((buttonsMask shr 8) and 0xFF).toByte(),
            hat,
            lx,
            ly,
            rx,
            ry,
            lt,
            rt
        )

        try {
            hid.sendReport(dev, REPORT_ID_GAMEPAD.toInt(), report)
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try {
            hidDevice?.unregisterApp()
            if (bluetoothAdapter != null && hidDevice != null) {
                bluetoothAdapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
            }
        } catch (_: Exception) {}
        hidDevice = null
        connectedDevice = null
        _state.value = BluetoothControllerState.Disabled
    }
}
