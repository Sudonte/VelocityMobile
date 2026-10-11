package com.example.velocitysuites.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.example.velocitysuites.network.dto.PaginatedResponse;
import com.google.gson.Gson;

import org.junit.Test;

/** A server reply with "data": null (or no data key) must read as an empty list, not crash the refresh. */
public class PaginatedResponseNullDataTest {

    @Test
    public void explicitNullDataReadsAsEmpty() {
        PaginatedResponse<String> r = new Gson().fromJson("{\"data\":null,\"total\":0}", PaginatedResponse.class);
        assertNotNull(r.items());
        assertEquals(0, r.items().size());
    }

    @Test
    public void missingDataKeyReadsAsEmpty() {
        PaginatedResponse<String> r = new Gson().fromJson("{\"total\":0}", PaginatedResponse.class);
        assertEquals(0, r.items().size());
    }

    @Test
    public void realDataIsReturned() {
        PaginatedResponse<String> r = new Gson().fromJson("{\"data\":[\"a\",\"b\"]}", PaginatedResponse.class);
        assertEquals(2, r.items().size());
    }
}
