/*
 *  Adopted from https://github.com/verhas/License3j
 */

package su.sres.securesms.activation;

import android.annotation.SuppressLint;

import javax.crypto.Cipher;
import java.lang.reflect.Modifier;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import su.sres.securesms.logging.Log;

import com.annimon.stream.Stream;

/**
 * A license describes the rights that a certain user has. The rights are represented by {@link Feature}s.
 * Each feature has a name, type and a value. The license is essentially the set of features.
 * <p>
 * As examples features can be license expiration date and time, number of users allowed to use the software,
 * name of rights and so on.
 */
public class License {
    private static final int MAGIC = 0x21CE_4E_5E; // LICE(N=4E)SE
    final private static String LICENSE_ID = "licenseId";
    private static final String SIGNATURE_KEY = "licenseSignature";
    private static final String DIGEST_KEY = "signatureDigest";
    final private static String EXPIRATION_DATE = "expiryDate";
    final private Map<String, Feature> features = new HashMap<>();

    static final int BYTES = 4;

    public License() {
    }

    protected License(License license) {
        features.putAll(license.features);
    }

    /**
     * Get a feature of a given name from the license or {@code null} if there is no feature for the name in the
     * license.
     *
     * @param name the name of the feature we want to retrieve
     * @return the feature object
     */
    public Feature get(String name) {
        return features.get(name);
    }

    /**
     * Checks the expiration date of the license and returns {@code true} if the license has expired.
     * <p>
     * The expiration date is stored in the license feature {@code expiryDate}. A license is expired
     * if the current date is after the specified {@code expiryDate}. At the given date (ms precision) the
     * license is still valid.
     * <p>
     * The method does not check that the license is properly signed or not. That has to be checked using a
     * separate call to the underlying license.
     *
     * @return {@code true} if the license has expired.
     */
    public boolean isExpired() {
        final Date expiryDate = get(EXPIRATION_DATE).getDate();
        final Date today = new Date();
        return today.getTime() > expiryDate.getTime();
    }

    /**
     * See {@link #isOK(PublicKey)}.
     *
     * @param key serialized encryption key to check the authenticity of the license signature
     * @return see {@link #isOK(PublicKey)}
     */
    public boolean isOK(byte[] key) {
        try {
            return isOK(LicenseKeyPair.Create.from(key, Modifier.PUBLIC).getPair().getPublic());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns true if the license is signed and the authenticity of the signature can be checked successfully
     * using the key.
     *
     * @param key encryption key to check the authenticity of the license signature
     * @return {@code true} if the license was properly signed and is intact. In any other cases it returns
     * {@code false}.
     */
    public boolean isOK(PublicKey key) {
        try {
            final MessageDigest digester = MessageDigest.getInstance(get(DIGEST_KEY).getString());
            final byte[] ser = unsigned();
            final byte[] digestValue = digester.digest(ser);
            final Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            final byte[] sigDigest = cipher.doFinal(getSignature());

            return Arrays.equals(digestValue, sigDigest);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get all the features in an array except the excluded ones in sorted order. The sorting is done on the name. This
     * is a private method and the actual sorting order is not guaranteed by the public API.
     *
     * @param excluded the set of the names of the features that are not included to the result array
     * @return the array of the features sorted.
     */
    private Feature[] featuresSorted(Set<String> excluded) {
        return this.features.values().stream().filter(f -> !excluded.contains(f.name()))
                .sorted(Comparator.comparing(Feature::name)).toArray(Feature[]::new);
    }

/*    @SuppressLint("LogTagInlined")
    private Feature[] featuresSorted(Set<String> excluded) {

        Collection<Feature> coll = this.features.values();

        boolean arrayTest = Arrays.equals(coll.stream().filter(f -> !excluded.contains(f.name())).sorted(Comparator.comparing(Feature::name)).toArray(Feature[]::new),
                                         Stream.of(coll).filter(f -> !excluded.contains(f.name())).sorted(Comparator.comparing(Feature::name)).toArray(Feature[]::new));

        Log.i("License", "Arrays are equal = " + arrayTest);

        return Stream.of(coll).filter(f -> !excluded.contains(f.name())).sorted(Comparator.comparing(Feature::name)).toArray(Feature[]::new);

    } */

    /**
     * Add a feature to the license. Note that adding a feature to a license renders the license signature invalid.
     * Adding the feature does not remove the signature feature though if there was any added previously. You can
     * calculate the signature of the license and add it to the license after you have added all features. Adding the
     * signature to the license as a feature does not ruin the signature because the signature is eventually ignored
     * when calculating the signature. The signature has to be a BINARY feature.
     *
     * <p>
     * The method throws exception in case the feature is the license signature, and the type is not {@code BINARY}.
     *
     * @param feature is added to the license
     * @return the previous feature of the same name but presumably different type and value or {@code null} in case
     * there was no previous feature in the license of the same name.
     */
    public Feature add(Feature feature) {
        if (feature.name().equals(SIGNATURE_KEY) && !feature.isBinary()) {
            throw new IllegalArgumentException("Signature of a license has to be binary.");
        }
        return features.put(feature.name(), feature);
    }

    /**
     * <p>Returns the features of the license in a map. The keys of the
     * map are the names of the features. The values are the feature
     * objects (which also contain the name).</p>
     *
     * <p>Note that the invocation of this method has its cost as it
     * recreates the returned map every time. It does not keep track if
     * the set of features has changed since the last time the method
     * was called.</p>
     *
     * @return the collected map
     */
    public Map<String, Feature> getFeatures() {
        return Collections.unmodifiableMap(new TreeMap<>(features));
    }

    /**
     * Get the license identifier. The identifier of the license is a random UUID (128 bit random value) that can
     * optionally be set and can be used to identify the license. This Id can be used as a reference to the license
     * in databases, URLs. An example use it to upload a simple file to a publicly reachable server with the name
     * of the license UUID and that it can be retrieved via a URL containing the UUID. The licensed program downloads
     * the file (presumably zero length) and in case the response code is 200 OK then it assumes that the license is OK.
     * If the server is not reachable or for some other reason it cannot reach the file it may assume that this is a
     * technical glitch and go on working for a while, however if the response is a definitive 404 it means that the file
     * was removes that means the license was revoked.
     *
     * @return the UUID former identifier of the license or null in case the license does not contain an ID.
     */
    public UUID getLicenseId() {
        try {
            return get(LICENSE_ID).getUUID();
        } catch (final Exception e) {
            return null;
        }
    }

    /**
     * Get the license serialized (not standard Java serialized!!!) as a byte array. This method is used to save the
     * license in {@code BINARY} format but not to sign the license. The returned byte array contains all the features
     * including the signature of the license.
     *
     * @return the license in binary format as a byte array
     */
    public byte[] serialized() {
        return serialized(Collections.emptySet());
    }

    /**
     * Get the license as a byte[] without the signature key. This byte array is used to create the signature of the
     * license. Obviously, the signature itself cannot be part of the signed part of the license.
     *
     * @return the byte array containing the license without the signature. Note that the message digest algorithm used
     * during the signature creation of the license and stored as a feature in the license is also signed.
     */
    public byte[] unsigned() {
        return serialized(new HashSet<>(Collections.singletonList(SIGNATURE_KEY)));
    }

    /**
     * Get the signature of the license. The signature of a license is stored in the license as a {@code BINARY}
     * feature. This method retrieves the feature and then it retrieves the value of the feature and returns the raw
     * value.
     *
     * @return the electronic signature attached to the license
     */
    public byte[] getSignature() {
        return get(SIGNATURE_KEY).getBinary();
    }

    /**
     * Create a byte array representation of the license. Include all the features except those whose name is specified
     * in the {@code excluded} set.
     *
     * @param excluded set of the feature names that are not to be present in the byte array representation of the
     *                 license.
     * @return the byte array containing the license information except the excluded features. The byte array
     * creation is deterministic in the sense that the same license will always result the same byte array. The features
     * converted into binary and concatenated, and their order is determined by primitive sorting.
     */
    private byte[] serialized(Set<String> excluded) {
        Feature[] includedFeatures = featuresSorted(excluded);
        final int featureNr = includedFeatures.length;
        byte[][] featuresSerialized = new byte[featureNr][];
        int i = 0;
        int size = 0;
        for (final Feature feature : includedFeatures) {
            featuresSerialized[i] = feature.serialized();
            size += featuresSerialized[i].length;
            i++;
        }

        final ByteBuffer buffer = ByteBuffer.allocate(size + BYTES * (featureNr + 1));
        buffer.putInt(MAGIC);
        for (final byte[] featureSerialized : featuresSerialized) {
            buffer.putInt(featureSerialized.length);
            buffer.put(featureSerialized);
        }
        return buffer.array();
    }

    /**
     * Inner class containing factory methods to create a license object from various sources.
     */
    public static class Create {
        /**
         * Create a license from the binary byte array representation.
         *
         * @param array the binary byte array representation of the license
         * @return the license object
         */
        public static License from(final byte[] array) {
            if (array.length < Integer.BYTES) {
                throw new IllegalArgumentException("serialized license is too short");
            }
            final License license = new License();
            final ByteBuffer buffer = ByteBuffer.wrap(array);
            final int magic = buffer.getInt();
            if (magic != MAGIC) {
                throw new IllegalArgumentException("serialized license is corrupt");
            }
            while (buffer.hasRemaining()) {
                try {
                    final int featureLength = buffer.getInt();
                    final byte[] featureSerialized = new byte[featureLength];
                    buffer.get(featureSerialized);
                    final Feature feature = Feature.Create.from(featureSerialized);
                    license.add(feature);
                } catch (BufferUnderflowException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            return license;
        }
    }
}
