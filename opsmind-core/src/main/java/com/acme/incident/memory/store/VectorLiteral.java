package com.acme.incident.memory.store;

/**
 * pgvector wire format. Avoids adding a Hibernate user type just to write
 * one column: the vector only ever crosses the boundary as a bind parameter
 * cast with ::vector.
 */
public final class VectorLiteral {

    private VectorLiteral() { }

    public static String of(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 12 + 2);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
