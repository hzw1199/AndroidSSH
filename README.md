## AndroidSSH - SSH from Android devices

This is a simple SSH client implementation for Android, using the JSch library.

Functionality includes:

* SSH shell-like
* Execute remote commands
* SFTP file upload / download
* SCP file upload

This project is based on [AndroidSSH](https://github.com/jonghough/AndroidSSH). I just add SCP support and fix some bugs.

![](/art/screenshot.png)

## How to use

* clone repository
* `gradle assembleDebug` (or make a release build if you want)
* install on your device
* SSH into your server (you need your SSH server's ip address or URL, port nnumber (probably 22) , and your username and password.
* input commands or click SFTP/SCP button to upload / download files
