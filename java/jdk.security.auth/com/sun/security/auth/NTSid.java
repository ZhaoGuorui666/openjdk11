/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.security.auth;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.security.Principal;

/**
 * This class implements the {@code Principal} interface
 * and represents information about a Windows NT user, group or realm.
 *
 * <p> Windows NT chooses to represent users, groups and realms (or domains)
 * with not only common names, but also relatively unique numbers.  These
 * numbers are called Security IDentifiers, or SIDs.  Windows NT
 * also provides services that render these SIDs into string forms.
 * This class represents these string forms.
 *
 * <p> Principals such as this {@code NTSid}
 * may be associated with a particular {@code Subject}
 * to augment that {@code Subject} with an additional
 * identity.  Refer to the {@code Subject} class for more information
 * on how to achieve this.  Authorization decisions can then be based upon
 * the Principals associated with a {@code Subject}.
 *
 * @see java.security.Principal
 * @see javax.security.auth.Subject
 */
public class NTSid implements Principal, java.io.Serializable {

    private static final long serialVersionUID = 4412290580770249885L;

    /**
     * @serial
     */
    private String sid;

    /**
     * Create an {@code NTSid} with a Windows NT SID.
     *
     * @param stringSid the Windows NT SID.
     *
     * @exception NullPointerException if the {@code String}
     *                  is {@code null}.
     *
     * @exception IllegalArgumentException if the {@code String}
     *                  has zero length.
     */
    public NTSid (String stringSid) {
        if (stringSid == null) {
            java.text.MessageFormat form = new java.text.MessageFormat
                (sun.security.util.ResourcesMgr.getAuthResourceString
                        ("invalid.null.input.value"));
            Object[] source = {"stringSid"};
            throw new NullPointerException(form.format(source));
        }
        if (stringSid.length() == 0) {
            throw new IllegalArgumentException
                (sun.security.util.ResourcesMgr.getAuthResourceString
                        ("Invalid.NTSid.value"));
        }
        sid = stringSid;
    }

    /**
     * Return a string version of this {@code NTSid}.
     *
     * @return a string version of this {@code NTSid}
     */
    public String getName() {
        return sid;
    }

    /**
     * Return a string representation of this {@code NTSid}.
     *
     * @return a string representation of this {@code NTSid}.
     */
    public String toString() {
        java.text.MessageFormat form = new java.text.MessageFormat
                (sun.security.util.ResourcesMgr.getAuthResourceString
                        ("NTSid.name"));
        Object[] source = {sid};
        return form.format(source);
    }

    /**
     * Compares the specified Object with this {@code NTSid}
     * for equality.  Returns true if the given object is also a
     * {@code NTSid} and the two NTSids have the same String
     * representation.
     *
     * @param o Object to be compared for equality with this
     *          {@code NTSid}.
     *
     * @return true if the specified Object is equal to this
     *          {@code NTSid}.
     */
    public boolean equals(Object o) {
        if (o == null)
            return false;

        if (this == o)
            return true;

        if (!(o instanceof NTSid))
            return false;
        NTSid that = (NTSid)o;

        return sid.equals(that.sid);
    }

    /**
     * Return a hash code for this {@code NTSid}.
     *
     * @return a hash code for this {@code NTSid}.
     */
    public int hashCode() {
        return sid.hashCode();
    }

    /**
     * Restores the state of this object from the stream.
     *
     * @param  stream the {@code ObjectInputStream} from which data is read
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if a serialized class cannot be loaded
     */
    private void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        if (sid == null) {
            java.text.MessageFormat form = new java.text.MessageFormat
                    (sun.security.util.ResourcesMgr.getAuthResourceString
                            ("invalid.null.input.value"));
            Object[] source = {"stringSid"};
            throw new InvalidObjectException(form.format(source));
        }
        if (sid.length() == 0) {
            throw new InvalidObjectException
                    (sun.security.util.ResourcesMgr.getAuthResourceString
                            ("Invalid.NTSid.value"));
        }
    }
}
