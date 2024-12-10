package su.sres.signalservice.internal.serialize;

import org.junit.Test;
import org.whispersystems.libsignal.util.guava.Optional;

import su.sres.signalservice.api.push.ACI;
import su.sres.signalservice.api.push.SignalServiceAddress;
import su.sres.signalservice.internal.serialize.protos.AddressProto;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public final class SignalServiceAddressProtobufSerializerTest {

  @Test
  public void serialize_and_deserialize_uuid_address() {
    SignalServiceAddress address      = new SignalServiceAddress(ACI.from(UUID.randomUUID()), Optional.absent());
    AddressProto         addressProto = su.sres.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer.toProtobuf(address);
    SignalServiceAddress deserialized = su.sres.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer.fromProtobuf(addressProto);

    assertEquals(address, deserialized);
  }

  @Test
  public void serialize_and_deserialize_both_address() {
    SignalServiceAddress address      = new SignalServiceAddress(ACI.from(UUID.randomUUID()), Optional.of("+15552345678"));
    AddressProto         addressProto = su.sres.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer.toProtobuf(address);
    SignalServiceAddress deserialized = su.sres.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer.fromProtobuf(addressProto);

    assertEquals(address, deserialized);
  }
}