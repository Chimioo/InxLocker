# InxLocker

[English](./README_en.md) | [中文](./README.md)

InxLocker is an Xposed module designed to intercept/forward Android system application installation and uninstallation requests, redirecting them to your specified installer app.

It is particularly suitable for devices with deeply customized systems.

## Key Features

- **Installation Redirection**: Intercepts installation requests for APK files and automatically launches your chosen installer.
- **Uninstallation Redirection**: Intercepts app uninstallation requests (via Intent) and hands them over to the specified program for processing.
- **Session Interception (Session Install)**: Supports session installation interception for modern Android systems (intercepts `CONFIRM_INSTALL`).
- **Forced Component Invocation**: Supports specifying a particular Activity component by long-pressing an installer list item (e.g., system installer), solving issues where some apps hardcode the installer.
- **Hot Reload**: Based on `libxposed api 102` features, settings take effect immediately without a system reboot by clicking the "Hot Reload" button on the home page.
- **Permission Fix**: Provides experimental fixes for installation path permission issues in some Android 14+ environments.
- **Hide Icon**: Supports hiding the launcher icon to keep the desktop tidy; settings can be accessed directly through the Xposed manager.

## Usage

### 1. Installation and Activation
1. Download and install InxLocker.
2. Activate this module in your Xposed manager (e.g., **LSPosed**).
3. **Scope Selection**:
   - Check **System Framework** to intercept system-level installation distribution.
   - Check the specific apps you want to forward installations from.
4. Restart the system (or restart the checked apps).

### 2. Configuration Redirection
1. Open the InxLocker app.
2. In **"Default Installer Settings"**, click and select your desired installer from the list.
3. **Advanced**: If the target installer cannot be launched normally after selection, you can **long-press** the installer to try enabling "Forced Component Invocation" to bypass the system's default distribution logic.

### 3. Functional Adjustments
- **Intercept Uninstallation**: When enabled, clicking to uninstall an app will jump to your set installer/uninstaller.
- **Session Install Interception**: Mainly used to intercept silent/split installation confirmation interfaces on modern Android systems.


## Notes

- This module is developed based on `libxposed`.
- After hiding the icon, if you need to find the settings interface again, please enter through the module list in the LSPosed manager.

## Open Source License
The code for this project is licensed under the [GPLv3 License](./LICENSE).
