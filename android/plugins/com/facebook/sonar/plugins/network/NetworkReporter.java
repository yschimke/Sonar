/*
 *  Copyright (c) 2018-present, Facebook, Inc.
 *
 *  This source code is licensed under the MIT license found in the LICENSE
 *  file in the root directory of this source tree.
 *
 */

package com.facebook.sonar.plugins.network;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public interface NetworkReporter {
  void reportRequest(RequestInfo requestInfo);

  void reportResponse(ResponseInfo responseInfo);

  class Header {
    public final String name;
    public final String value;

    public Header(final String name, final String value) {
      this.name = name;
      this.value = value;
    }

    @Override
    public String toString() {
      return "Header{" + name + ": " + value + "}";
    }
  }

  class RequestInfo {
    public String requestId;
    public long timeStamp;
    public List<Header> headers = new ArrayList<>();
    public String method;
    public String uri;
    public @Nullable byte[] body;
    public int bodyLength = -1;
    public @Nullable String message = null;
    public @Nullable Integer responseCode = null;

    public Header getFirstHeader(final String name) {
      for (Header header : headers) {
        if (name.equalsIgnoreCase(header.name)) {
          return header;
        }
      }
      return null;
    }
  }

  class ResponseInfo {
    public String requestId;
    public long timeStamp;
    public int statusCode;
    public String statusReason;
    public List<Header> headers = new ArrayList<>();
    public byte[] body;

    public Header getFirstHeader(final String name) {
      for (Header header : headers) {
        if (name.equalsIgnoreCase(header.name)) {
          return header;
        }
      }
      return null;
    }
  }
}
