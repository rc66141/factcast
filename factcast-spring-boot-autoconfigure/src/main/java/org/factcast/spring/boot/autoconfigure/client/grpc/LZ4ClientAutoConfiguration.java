/*
 * Copyright © 2018 factcast (http://factcast.org)
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
package org.factcast.spring.boot.autoconfigure.client.grpc;

import org.factcast.client.grpc.*;
import org.factcast.client.grpc.codec.*;
import org.factcast.spring.boot.autoconfigure.store.inmem.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;

import net.devh.boot.grpc.client.channelfactory.*;
import net.jpountz.lz4.*;

/**
 * Configures optional LZ4 Codec
 *
 * @author uwe.schaefer@mercateo.com
 */

@Configuration
@ConditionalOnClass({ LZ4Compressor.class, Lz4GrpcClientCodec.class, GrpcFactStore.class,
        GrpcChannelFactory.class })
@AutoConfigureAfter(InMemFactStoreAutoConfiguration.class)
@AutoConfigureBefore(GrpcFactStoreAutoConfiguration.class)
public class LZ4ClientAutoConfiguration {

    @Bean
    public Lz4GrpcClientCodec snappyCodec() {
        return new Lz4GrpcClientCodec();
    }
}
