
all:
	protoc --java_out=../src/ WebRtcData.proto Backups.proto DeviceName.proto

clean:
	rm -f ../src/su/sres/securesms/backup/BackupProtos.java
	rm -f ../src/su/sres/securesms/devicelist/DeviceNameProtos.java
	rm -f ../src/su/sres/securesms/webrtc/WebRtcDataProtos.java
