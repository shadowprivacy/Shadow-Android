package su.sres.signalservice.internal.serialize;

import com.google.protobuf.ByteString;

import org.whispersystems.libsignal.util.guava.Optional;

import su.sres.signalservice.api.push.SignalServiceAddress;
import su.sres.signalservice.api.util.UuidUtil;
import su.sres.signalservice.internal.serialize.protos.AddressProto;

import java.util.UUID;

public final class SignalServiceAddressProtobufSerializer {

  private SignalServiceAddressProtobufSerializer() {
  }

  public static AddressProto toProtobuf(SignalServiceAddress signalServiceAddress) {
    AddressProto.Builder builder = AddressProto.newBuilder();

    builder.setUuid(ByteString.copyFrom(UuidUtil.toByteArray(signalServiceAddress.getUuid())));

    if (signalServiceAddress.getNumber().isPresent()) {
      builder.setE164(signalServiceAddress.getNumber().get());
    }

    return builder.build();
  }

  public static SignalServiceAddress fromProtobuf(AddressProto addressProto) {
    UUID             uuid   = UuidUtil.parseOrThrow(addressProto.getUuid().toByteArray());
    Optional<String> number = addressProto.hasE164() ? Optional.of(addressProto.getE164()) : Optional.absent();

    return new SignalServiceAddress(uuid, number);
  }
}