# Privacy Policy

**Highmark SSH**
Rabbit Hole Solutions Inc.
Last Updated: September 22, 2026

## Overview

Highmark SSH is an SSH terminal emulator for Meta Quest headsets. Your privacy is important to us. This policy explains what data the app handles and how it is used.

## Data Collection

Highmark SSH does **not** collect, transmit, or share any personal data. The app:

- Does not collect analytics or telemetry
- Does not track usage or behavior
- Does not connect to any servers other than the SSH hosts you specify
- Does not require an account or registration
- Does not use advertising or third-party SDKs that collect data

## Data Stored on Your Device

Highmark SSH stores the following data **locally on your device only**:

- **Connection history** — Hostnames, ports, and usernames of servers you connect to, saved for convenience
- **Saved passwords** — Encrypted using AES-256-GCM with keys stored in the Android Keystore (hardware-backed on Quest). Passwords are only stored if you explicitly choose "Remember password"
- **SSH keys** — If you use key authentication, the app generates an Ed25519 key pair on your device. The private key is encrypted the same way as saved passwords and never leaves your device. The public key is stored alongside it
- **Host key fingerprints** — SSH server public key fingerprints for Trust-on-First-Use (TOFU) verification

This data never leaves your device and is not accessible to Rabbit Hole Solutions Inc. or any third party, with one exception you control: when you tap **Copy** or **Share** on an SSH public key, that public key is placed on the clipboard or handed to the app you pick from the system share sheet, so you can install it on your server. A public key is not secret and cannot be used to access your servers. Your private key is never copied or shared.

## Clipboard

Highmark SSH writes to the clipboard only when you copy terminal text or a public key, and reads from it only when you choose **Paste**.

## Network Activity

The only network connections made by Highmark SSH are the SSH connections you initiate to your own servers. No data is sent to Rabbit Hole Solutions Inc. or any other party.

## Data Deletion

All data stored by Highmark SSH resides locally on your device. You can delete it at any time by:

- **Clearing app data** — Go to Settings > Apps > Highmark SSH > Clear Data
- **Uninstalling the app** — Removes all stored data completely
- **Within the app** — Delete individual saved connections using the delete button in the connection list

No data is stored remotely, so there is nothing to request deletion of from our servers.

## Children's Privacy

Highmark SSH does not knowingly collect any data from children under the age of 13.

## Changes to This Policy

If this privacy policy is updated, the revised version will be posted at this URL with an updated date. As the app does not collect contact information, users are encouraged to check this page periodically.

## Contact

If you have questions about this privacy policy, please contact us at:

- GitHub: [github.com/rabbitHoleSolutions/highmark-ssh](https://github.com/rabbitHoleSolutions/highmark-ssh)
- Issues: [github.com/rabbitHoleSolutions/highmark-ssh/issues](https://github.com/rabbitHoleSolutions/highmark-ssh/issues)
