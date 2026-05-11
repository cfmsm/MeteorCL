package com.github.cfmsm.meteorcl.highlevel;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class Result {

    protected final ByteBuffer buffer;

    public Result(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public ByteBuffer asByteBuffer() {
        return buffer.asReadOnlyBuffer();
    }

    public byte[] asByteArray() {
        byte[] out = new byte[buffer.remaining()];
        buffer.asReadOnlyBuffer().get(out);
        return out;
    }

    public FloatBuffer asFloatBuffer() {
        return buffer.asReadOnlyBuffer().asFloatBuffer();
    }

    public float[] asFloatArray() {
        FloatBuffer fb = asFloatBuffer();
        float[] out = new float[fb.remaining()];
        fb.get(out);
        return out;
    }

    public IntBuffer asIntBuffer() {
        return buffer.asReadOnlyBuffer().asIntBuffer();
    }

    public int[] asIntArray() {
        IntBuffer ib = asIntBuffer();
        int[] out = new int[ib.remaining()];
        ib.get(out);
        return out;
    }
}