# Persistent Android APK Signing

GitHub Actions can sign debug APKs with a stable private key when the repository secrets below are configured. The keystore and its passwords must never be committed to the repository or sent in chat.

## Create a keystore

On a computer with Java's keytool installed, run:

```sh
keytool -genkeypair -v -keystore flowy-signing.jks -alias flowy -keyalg RSA -keysize 4096 -validity 10000
```

Choose a strong keystore password and key password, and keep them in a password manager. The certificate identity fields do not affect app installation. Keep flowy-signing.jks backed up securely. If it is lost, future APKs cannot update installations signed with it.

## Add repository secrets

In the GitHub repository, open **Settings > Secrets and variables > Actions > New repository secret** and add:

| Secret name | Value |
| --- | --- |
| ANDROID_KEYSTORE_BASE64 | Base64 encoding of the complete flowy-signing.jks file |
| ANDROID_KEYSTORE_PASSWORD | The keystore password |
| ANDROID_KEY_ALIAS | flowy |
| ANDROID_KEY_PASSWORD | The key password |

For Windows PowerShell, copy the Base64 value with:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("flowy-signing.jks")) | Set-Clipboard
```

On macOS or Linux:

```sh
base64 < flowy-signing.jks | tr -d '\n'
```

Paste only the Base64 output into the ANDROID_KEYSTORE_BASE64 secret. Do not paste the passwords anywhere except their matching GitHub secret fields.

## Build and install

Run **Actions > Android Build > Run workflow** on the main branch after saving the secrets. Builds from main then use the persistent key. Pull request builds do not receive the key.

The older APKs were signed with temporary CI keys. Android will reject an update across different signing keys. Uninstall the existing Flowy Keyboard once before installing the first APK signed with the persistent key. After that transition, use only APKs built from main with these same secrets.

## Current integration limit

The connected GitHub integration can edit source code and workflows, but it cannot write repository secrets. The owner must add these four values in GitHub Settings. Once added, the workflow automatically signs later builds.
