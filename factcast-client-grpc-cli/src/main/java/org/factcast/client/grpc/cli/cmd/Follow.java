/**
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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
package org.factcast.client.grpc.cli.cmd;

import java.util.UUID;

import org.factcast.client.grpc.cli.util.Command;
import org.factcast.client.grpc.cli.util.ConsoleFactObserver;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SpecBuilder;
import org.factcast.core.subscription.SubscriptionRequest;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(
        commandNames = "follow",
        commandDescription = "read all matching facts and keep connected while listening for new ones")
public class Follow implements Command {

    @Parameter(names = "-ns", description = "the namespace filtered on", required = true)
    String ns;

    @Parameter(names = "-from", description = "start reading AFTER the fact with the given id")
    UUID from;

    @Parameter(names = "-fromNowOn", help = true, description = "read only future facts")
    boolean fromNow = false;

    final ConsoleFactObserver obs = new ConsoleFactObserver();

    @Override
    public void runWith(FactCast fc) {

        SpecBuilder catchup = SubscriptionRequest.follow(FactSpec.ns(ns));

        if (fromNow)
            fc.subscribeToFacts(catchup.fromNowOn(), obs);
        else {
            if (from == null)
                fc.subscribeToFacts(catchup.fromScratch(), obs);
            else
                fc.subscribeToFacts(catchup.from(from), obs);
        }

        obs.awaitTermination();
    }

}
