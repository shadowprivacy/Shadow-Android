/*
 * Copyright 2019 Zhou Pengfei
 * SPDX-License-Identifier: Apache-2.0
 */

package su.sres.glide.common.loader;

import su.sres.glide.common.io.Reader;

import java.io.IOException;

/**
 * @Description: Loader
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-14
 */
public interface Loader {
    Reader obtain() throws IOException;
}
