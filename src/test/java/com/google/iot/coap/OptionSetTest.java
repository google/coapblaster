/*
 * Copyright (C) 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.iot.coap;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.net.URI;

@SuppressWarnings("ConstantConditions")
class OptionSetTest {

    @Test
    void addIfMatch() {
        OptionSet set = new OptionSet();
        Etag etag = Etag.createFromByteArray(new byte[] {0x2F});

        set.addIfMatch(etag);

        assertTrue(set.getIfMatches().contains(etag));

        assertTrue(set.hasOption(Option.IF_MATCH));

        assertEquals(1, set.size());
    }

    @Test
    void addEtag() {
        OptionSet set = new OptionSet();
        Etag etag = Etag.createFromByteArray(new byte[] {0x2F});

        set.addEtag(etag);

        assertTrue(set.getEtags().contains(etag));

        assertTrue(set.hasOption(Option.ETAG));

        assertEquals(1, set.size());
    }

    @Test
    void setIfNoneMatch() {
        OptionSet set = new OptionSet();

        set.setIfNoneMatch(true);

        assertTrue(set.getIfNoneMatch());

        assertTrue(set.hasOption(Option.IF_NONE_MATCH));

        assertEquals(1, set.size());

        set.setIfNoneMatch(false);

        assertFalse(set.getIfNoneMatch());

        assertFalse(set.hasOption(Option.IF_NONE_MATCH));

        assertEquals(0, set.size());
    }

    @Test
    void setUriHost() {
        OptionSet set = new OptionSet();

        set.setUriHost("localhost");

        assertEquals("localhost", set.getUriHost());

        assertTrue(set.hasOption(Option.URI_HOST));

        assertEquals(1, set.size());
    }

    @Test
    void setUriPort() {
        OptionSet set = new OptionSet();

        set.setUriPort(1234);

        assertEquals((Integer) 1234, set.getUriPort());

        assertTrue(set.hasOption(Option.URI_PORT));

        assertEquals(1, set.size());
    }

    @Test
    void addLocationPath() {
        OptionSet set = new OptionSet();

        set.addLocationPath("path1");

        assertTrue(set.getLocationPaths().contains("path1"));
        assertFalse(set.getLocationPaths().contains("path2"));

        assertEquals(1, set.size());

        set.addLocationPath("path2");

        assertTrue(set.getLocationPaths().contains("path1"));
        assertTrue(set.getLocationPaths().contains("path2"));

        assertEquals(2, set.size());
        assertTrue(set.hasOption(Option.LOCATION_PATH));
    }

    @Test
    void addUriPath() {
        OptionSet set = new OptionSet();

        set.addUriPath("path1");

        assertTrue(set.getUriPaths().contains("path1"));
        assertFalse(set.getUriPaths().contains("path2"));

        set.addUriPath("path2");

        assertTrue(set.getUriPaths().contains("path1"));
        assertTrue(set.getUriPaths().contains("path2"));

        assertTrue(set.hasOption(Option.URI_PATH));
    }

    @Test
    void setContentFormat() {
        OptionSet set = new OptionSet();

        set.setContentFormat(1234);

        assertEquals((Integer) 1234, set.getContentFormat());

        assertTrue(set.hasOption(Option.CONTENT_FORMAT));

        assertEquals(1, set.size());
    }

    @Test
    void setMaxAge() {
        OptionSet set = new OptionSet();

        set.setMaxAge(1234);

        assertEquals((Integer) 1234, set.getMaxAge());

        assertTrue(set.hasOption(Option.MAX_AGE));

        assertEquals(1, set.size());
    }

    @Test
    void addLocationQuery() {
        OptionSet set = new OptionSet();

        set.addLocationQuery("query1=1");

        assertTrue(set.getLocationQueries().contains("query1=1"));
        assertFalse(set.getLocationQueries().contains("query2=2"));

        assertEquals(1, set.size());

        set.addLocationQuery("query2=2");

        assertTrue(set.getLocationQueries().contains("query1=1"));
        assertTrue(set.getLocationQueries().contains("query2=2"));

        assertEquals(2, set.size());
        assertTrue(set.hasOption(Option.LOCATION_QUERY));
    }

    @Test
    void addUriQuery() {
        OptionSet set = new OptionSet();

        set.addUriQuery("query1=1");

        assertTrue(set.getUriQueries().contains("query1=1"));
        assertFalse(set.getUriQueries().contains("query2=2"));

        assertEquals(1, set.size());

        set.addUriQuery("query2=2");

        assertTrue(set.getUriQueries().contains("query1=1"));
        assertTrue(set.getUriQueries().contains("query2=2"));

        assertEquals(2, set.size());
        assertTrue(set.hasOption(Option.URI_QUERY));
    }

    @Test
    void getUri() {
        OptionSet set = new OptionSet();
        URI uri = URI.create("coap://192.168.33.20/5/s/levl/v?inc&d=2.0");

        assertEquals(uri.getQuery(), "inc&d=2.0");
        assertEquals(uri.getRawQuery(), "inc&d=2.0");

        set.setUri(uri);

        assertTrue(set.getUriQueries().contains("d=2.0"));
        assertTrue(set.getUriQueries().contains("inc"));

        assertEquals(uri, set.getUri());
        assertEquals(uri.toString(), set.getUri().toString());
        assertEquals(uri.toASCIIString(), set.getUri().toASCIIString());
    }

    @Test
    void hasOption() {
        OptionSet set = new OptionSet();
        Etag etag = Etag.createFromByteArray(new byte[] {0x2F});

        set.addIfMatch(etag);

        assertTrue(set.hasOption(Option.IF_MATCH));
    }

    @Test
    void removeOption() {
        OptionSet set = new OptionSet();
        Etag etag = Etag.createFromInteger(0x2F);

        set.addIfMatch(etag);

        assertTrue(set.hasOption(Option.IF_MATCH));

        set.removeOption(Option.IF_MATCH);

        assertFalse(set.hasOption(Option.IF_MATCH));
    }

    //    @Test
    //    void clear() {}
    //
    //    @Test
    //    void setUri() {}
    //
    //    @Test
    //    void getUri() {}
    //
    //    @Test
    //    void getProxyUri() {}
    //
    //    @Test
    //    void setAccept() {}
    //
    //    @Test
    //    void setProxyUri() {}
    //
    //    @Test
    //    void setProxyUriFromString() {}
    //
    //    @Test
    //    void setProxyScheme() {}
    //
    //    @Test
    //    void setObserve() {}
    //
    //    @Test
    //    void setSize1() {}
    //
    //    @Test
    //    void setSize2() {}
    //
    //    @Test
    //    void setBlock1() {}
    //
    //    @Test
    //    void setBlock2() {}
    //
    //    @Test
    //    void addOption() {}
    //
    //    @Test
    //    void asSortedList() {}

}
