/*
 * Copyright 2019 Zhou Pengfei
 * SPDX-License-Identifier: Apache-2.0
 */

package su.sres.glide.common.loader;

import su.sres.glide.common.io.ByteBufferReader;
import su.sres.glide.common.io.Reader;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @Description: ByteBufferLoader
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-15
 */
public abstract class ByteBufferLoader implements Loader {
    public abstract ByteBuffer getByteBuffer();

    @Override
    public Reader obtain() throws IOException {
        return new ByteBufferReader(getByteBuffer());
    }
}
