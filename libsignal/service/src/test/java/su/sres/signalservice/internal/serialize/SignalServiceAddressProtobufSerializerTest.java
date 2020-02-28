package su.sres.signalservice.internal.serialize;

import org.junit.Test;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.push.SignalServiceAddress;
import su.sres.signalservice.internal.serialize.protos.AddressProto;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public final class SignalServiceAddressProtobufSerializerTest {

    @Test
    public void serialize_and_deserialize_uuid_address() {
        SignalServiceAddress address      = new SignalServiceAddress(Optional.fromNullable(UUID.randomUUID()), Optional.<String>absent(), Optional.<String>absent());
        AddressProto         addressProto = su.sres.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer.toProtobuf(address);
        SignalServiceAddress deserialized = su.sres.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer.fromProtobuf(addressProto);

        assertEquals(address, deserialized);
    }

    @Test
    public void serialize_and_deserialize_e164_address() {
        SignalServiceAddress address      = new SignalServiceAddress(Optional.<UUID>absent(), Optional.of("+15552345678"), Optional.<String>absent());
        AddressProto         addressProto = su.sres.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer.toProtobuf(address);
        SignalServiceAddress deserialized = su.sres.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer.fromProtobuf(addressProto);

        assertEquals(address, deserialized);
    }

    @Test
    public void serialize_and_deserialize_both_address() {
        SignalServiceAddress address      = new SignalServiceAddress(Optional.fromNullable(UUID.randomUUID()), Optional.of("+15552345678"), Optional.<String>absent());
        AddressProto         addressProto = su.sres.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer.toProtobuf(address);
        SignalServiceAddress deserialized = su.sres.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer.fromProtobuf(addressProto);

        assertEquals(address, deserialized);
    }

    @Test
    public void serialize_and_deserialize_both_address_with_relay() {
        SignalServiceAddress address      = new SignalServiceAddress(Optional.fromNullable(UUID.randomUUID()), Optional.of("+15552345678"), Optional.of("relay"));
        AddressProto         addressProto = su.sres.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer.toProtobuf(address);
        SignalServiceAddress deserialized = SignalServiceAddressProtobufSerializer.fromProtobuf(addressProto);

        assertEquals(address, deserialized);
    }
}