package su.sres.signalservice.internal.serialize;

import su.sres.signalservice.api.messages.SignalServiceMetadata;
import su.sres.signalservice.internal.serialize.protos.MetadataProto;

public final class SignalServiceMetadataProtobufSerializer {

    private SignalServiceMetadataProtobufSerializer() {
    }

    public static MetadataProto toProtobuf(SignalServiceMetadata metadata) {
        return MetadataProto.newBuilder()
                .setAddress(SignalServiceAddressProtobufSerializer.toProtobuf(metadata.getSender()))
                .setSenderDevice(metadata.getSenderDevice())
                .setNeedsReceipt(metadata.isNeedsReceipt())
                .setTimestamp(metadata.getTimestamp())
                .setServerTimestamp(metadata.getServerTimestamp())
                .build();
    }

    public static SignalServiceMetadata fromProtobuf(MetadataProto metadata) {
        return new SignalServiceMetadata(SignalServiceAddressProtobufSerializer.fromProtobuf(metadata.getAddress()),
                metadata.getSenderDevice(),
                metadata.getTimestamp(),
                metadata.getServerTimestamp(),
                metadata.getNeedsReceipt());
    }
}