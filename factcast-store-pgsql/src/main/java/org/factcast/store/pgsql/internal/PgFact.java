/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.pgsql.internal;

import java.sql.ResultSet;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.FactHeader;
import org.factcast.core.util.FactCastJson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;

import lombok.AccessLevel;
import lombok.Generated;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;

/**
 * PG Specific implementation of a Fact.
 *
 * This class is necessary in order to delay parsing of the header until
 * necessary (when accessing meta-data)
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(of = { "id" })
public class PgFact implements Fact {

    @Getter
    @NonNull
    final UUID id;

    @Getter
    @NonNull
    final String ns;

    @Getter
    final String type;

    @Getter
    final int version;

    @Getter
    final Set<UUID> aggIds;

    @Getter
    @NonNull
    final String jsonHeader;

    @Getter
    @NonNull
    final String jsonPayload;

    @JsonProperty
    Map<String, String> meta = null;

    @Override
    public String meta(String key) {
        if (meta == null) {
            meta = deser();
        }
        return meta.get(key);
    }

    private transient FactHeader header;

    @Override
    public @NonNull FactHeader header() {
        if (header == null)
            header = FactCastJson.readValue(FactHeader.class, jsonHeader);
        return header;
    }

    @SneakyThrows
    @Generated
    private Map<String, String> deser() {
        Meta deserializedMeta = FactCastJson.readValue(Meta.class, jsonHeader);
        return deserializedMeta.meta;
    }

    // just picks the MetaData from the Header (as we know the rest already
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Meta {

        @JsonProperty
        final Map<String, String> meta = new HashMap<>();
    }

    @SneakyThrows
    @Generated
    public static Fact from(ResultSet resultSet) {
        String id = resultSet.getString(PgConstants.ALIAS_ID);
        String aggId = resultSet.getString(PgConstants.ALIAS_AGGID);
        String type = resultSet.getString(PgConstants.ALIAS_TYPE);
        String ns = resultSet.getString(PgConstants.ALIAS_NS);
        String jsonHeader = resultSet.getString(PgConstants.COLUMN_HEADER);
        String jsonPayload = resultSet.getString(PgConstants.COLUMN_PAYLOAD);
        int version = resultSet.getInt(PgConstants.COLUMN_VERSION);
        return new PgFact(UUID.fromString(id), ns, type, version, toUUIDArray(aggId), jsonHeader,
                jsonPayload);
    }

    @VisibleForTesting
    static Set<UUID> toUUIDArray(String aggIdArrayAsString) {
        Set<UUID> set = new LinkedHashSet<>();
        if (aggIdArrayAsString != null && aggIdArrayAsString.trim().length() > 2) {
            UUID[] readValue = FactCastJson.readValue(UUID[].class, aggIdArrayAsString);
            if (readValue != null) {
                set.addAll(Arrays.asList(readValue));
            }
        }
        return set;
    }
}
