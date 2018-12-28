/*
 * Copyright 2018 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.a7zip;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.Closeable;
import java.nio.charset.Charset;
import okio.BufferedStore;
import okio.Okio;
import okio.Store;

public class InArchive implements Closeable {

  private long nativePtr;

  private InArchive(long nativePtr) {
    this.nativePtr = nativePtr;
  }

  private void checkClosed() {
    if (nativePtr == 0) {
      throw new IllegalStateException("This InArchive is closed.");
    }
  }

  // Sometimes p7zip returns string in the original charset, sometimes in utf-16.
  // If it in unknown charset, each byte stores in each char,
  // so every char in the string is smaller than 0xFF.
  // But if every char in the string is smaller than 0xFF,
  // it's hard to tell the string is in utf-16 or the original charset.
  // TODO Let p7zip tell whether it have encoded the string.
  private String applyCharsetToString(String str, Charset charset) {
    if (str == null || charset == null) {
      return str;
    }

    int length = str.length();
    for (int i = 0; i < length; i++) {
      char c = str.charAt(i);
      if (c > 0xFF) {
        // It's not a pure byte list, can't apply charset
        return str;
      }
    }

    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) {
      bytes[i] = (byte) str.charAt(i);
    }

    return new String(bytes, charset);
  }

  /**
   * Returns the format name of this archive.
   * Empty string if can't get the name.
   */
  @NonNull
  public String getFormatName() {
    checkClosed();
    return nativeGetFormatName(nativePtr);
  }

  /**
   * Returns the number of entries in this archive.
   * {@code -1} if get error.
   */
  public int getNumberOfEntries() {
    checkClosed();
    return nativeGetNumberOfEntries(nativePtr);
  }

  /**
   * Returns the type of the property for the archive.
   *
   * @param propID the id of the property
   * @return one of {@link PropType}
   */
  public PropType getArchivePropertyType(PropID propID) {
    int type = nativeGetArchivePropertyType(nativePtr, propID.ordinal());
    if (type >= 0 || type < PropType.values().length) {
      return PropType.values()[type];
    } else {
      return PropType.UNKNOWN;
    }
  }

  /**
   * Returns string property for the archive.
   *
   * @param propID the id of the property
   * @return the string property, empty string if get error
   */
  @NonNull
  public String getArchiveStringProperty(PropID propID) {
    return getArchiveStringProperty(propID, null);
  }

  /**
   * Returns string property for the archive.
   *
   * @param propID the id of the property
   * @param charset the charset of the string, {@code null} to let p7zip handle it
   * @return the string property, empty string if get error
   */
  @NonNull
  public String getArchiveStringProperty(PropID propID, @Nullable Charset charset) {
    checkClosed();
    String str = nativeGetArchiveStringProperty(nativePtr, propID.ordinal());
    str = applyCharsetToString(str, charset);
    return str != null ? str : "";
  }

  /**
   * Returns the type of the property for the archive.
   *
   * @param propID the id of the property
   * @return one of {@link PropType}
   */
  public PropType getEntryPropertyType(int index, PropID propID) {
    int type = nativeGetEntryPropertyType(nativePtr, index, propID.ordinal());
    if (type >= 0 || type < PropType.values().length) {
      return PropType.values()[type];
    } else {
      return PropType.UNKNOWN;
    }
  }

  /**
   * Returns string property for the entry.
   *
   * @param index the index of the entry
   * @param propID the id of the property
   * @return the string property, empty string if get error
   */
  @NonNull
  public String getEntryStringProperty(int index, PropID propID) {
    return getEntryStringProperty(index, propID, null);
  }

  /**
   * Returns string property for the entry.
   *
   * @param index the index of the entry
   * @param propID the id of the property
   * @param charset the charset of the string, {@code null} to let p7zip handle it
   * @return the string property, empty string if get error
   */
  @NonNull
  public String getEntryStringProperty(int index, PropID propID, @Nullable Charset charset) {
    checkClosed();
    String str = nativeGetEntryStringProperty(nativePtr, index, propID.ordinal());
    str = applyCharsetToString(str, charset);
    return str != null ? str : "";
  }

  /**
   * Returns the path of the entry.
   *
   * @param index the index of the entry
   * @return the path, empty string if get error
   */
  @NonNull
  public String getEntryPath(int index) {
    return getEntryPath(index, null);
  }

  /**
   * Returns the path of the entry.
   *
   * @param index the index of the entry
   * @param charset the charset of the string, {@code null} to let p7zip handle it
   * @return the path, empty string if get error
   */
  @NonNull
  public String getEntryPath(int index, Charset charset) {
    return getEntryStringProperty(index, PropID.PATH, charset);
  }

  @Override
  public void close() {
    if (nativePtr != 0) {
      nativeClose(nativePtr);
      nativePtr = 0;
    }
  }

  public static InArchive create(Store store) throws ArchiveException {
    if (store instanceof BufferedStore) {
      return create((BufferedStore) store);
    } else {
      return create(Okio.buffer(store));
    }
  }

  public static InArchive create(BufferedStore store) throws ArchiveException {
    long nativePtr = nativeCreate(store);

    if (nativePtr == 0) {
      // It should not be 0
      throw new ArchiveException("a7zip is buggy");
    }

    return new InArchive(nativePtr);
  }

  private static native long nativeCreate(BufferedStore store) throws ArchiveException;

  private static native String nativeGetFormatName(long nativePtr);

  private static native int nativeGetNumberOfEntries(long nativePtr);

  private static native int nativeGetArchivePropertyType(long nativePtr, int propID);

  @Nullable
  private static native String nativeGetArchiveStringProperty(long nativePtr, int propID);

  private static native int nativeGetEntryPropertyType(long nativePtr, int index, int propID);

  @Nullable
  private static native String nativeGetEntryStringProperty(long nativePtr, int index, int propID);

  private static native void nativeClose(long nativePtr);
}