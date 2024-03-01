/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package sun.security.ec;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.security.interfaces.XECPrivateKey;
import java.util.Optional;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.NamedParameterSpec;

import sun.security.pkcs.PKCS8Key;
import sun.security.x509.AlgorithmId;

public final class XDHPrivateKeyImpl extends PKCS8Key implements XECPrivateKey {

    private static final long serialVersionUID = 1L;

    private AlgorithmParameterSpec paramSpec;

    XDHPrivateKeyImpl(XECParameters params, byte[] k)
        throws InvalidKeyException {

        this.paramSpec = new NamedParameterSpec(params.getName());
        this.algid = new AlgorithmId(params.getOid());
        this.key = k.clone();

        checkLength(params);
    }

    XDHPrivateKeyImpl(byte[] encoded) throws InvalidKeyException {

        decode(encoded);
        XECParameters params = XECParameters.get(
            InvalidKeyException::new, algid);
        paramSpec = new NamedParameterSpec(params.getName());

        checkLength(params);
    }

    void checkLength(XECParameters params) throws InvalidKeyException {

        if (params.getBytes() != this.key.length) {
            throw new InvalidKeyException(
                "key length must be " + params.getBytes());
        }
    }

    public byte[] getK() {
        return key.clone();
    }

    @Override
    public String getAlgorithm() {
        return "XDH";
    }

    @Override
    public AlgorithmParameterSpec getParams() {
        return paramSpec;
    }

    @Override
    public Optional<byte[]> getScalar() {
        return Optional.of(getK());
    }

    /**
     * Restores the state of this object from the stream.
     * <p>
     * Deserialization of this object is not supported.
     *
     * @param  stream the {@code ObjectInputStream} from which data is read
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if a serialized class cannot be loaded
     */
    private void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        throw new InvalidObjectException(
                "XDHPrivateKeyImpl keys are not directly deserializable");
    }
}

