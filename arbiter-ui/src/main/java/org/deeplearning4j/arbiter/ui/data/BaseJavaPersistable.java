package org.deeplearning4j.arbiter.ui.data;

import lombok.AllArgsConstructor;
import org.apache.commons.compress.utils.IOUtils;
import org.deeplearning4j.api.storage.Persistable;
import org.deeplearning4j.arbiter.ui.module.ArbiterModule;
import org.deeplearning4j.ui.stats.impl.java.JavaStatsInitializationReport;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/**
 * Common implementation
 *
 * @author Alex Black
 */
@AllArgsConstructor
public abstract class BaseJavaPersistable implements Persistable {

    private String sessionId;
    private long timestamp;

    public BaseJavaPersistable(Builder builder){
        this.sessionId = builder.sessionId;
        this.timestamp = builder.timestamp;
    }

    @Override
    public String getTypeID() {
        return ArbiterModule.ARBITER_UI_TYPE_ID;
    }

    @Override
    public long getTimeStamp() {
        return timestamp;
    }

    @Override
    public String getSessionID() {
        return sessionId;
    }

    @Override
    public int encodingLengthBytes() {
        //TODO - presumably a more efficient way to do this
        byte[] encoded = encode();
        return encoded.length;
    }

    @Override
    public byte[] encode() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(this);
        } catch (IOException e) {
            throw new RuntimeException(e); //Should never happen
        }
        return baos.toByteArray();
    }

    @Override
    public void encode(ByteBuffer buffer) {
        buffer.put(encode());
    }

    @Override
    public void encode(OutputStream outputStream) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(outputStream)) {
            oos.writeObject(this);
        }
    }

    @Override
    public void decode(byte[] decode) {
        JavaStatsInitializationReport r;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(decode))) {
            r = (JavaStatsInitializationReport) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e); //Should never happen
        }

        Field[] fields = this.getClass().getDeclaredFields();
        for (Field f : fields) {
            f.setAccessible(true);
            try {
                f.set(this, f.get(r));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e); //Should never happen
            }
        }
    }

    @Override
    public void decode(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        decode(bytes);
    }

    @Override
    public void decode(InputStream inputStream) throws IOException {
        decode(IOUtils.toByteArray(inputStream));
    }

    public static abstract class Builder<T extends Builder<T>> {
        protected String sessionId;
        protected long timestamp;

        public T sessionId(String sessionId){
            this.sessionId = sessionId;
            return (T) this;
        }

        public T timestamp(long timestamp){
            this.timestamp = timestamp;
            return (T) this;
        }

    }
}
