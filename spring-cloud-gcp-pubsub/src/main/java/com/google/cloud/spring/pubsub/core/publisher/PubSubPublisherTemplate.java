/*
 * Copyright 2017-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.spring.pubsub.core.publisher;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.spring.pubsub.core.PubSubDeliveryException;
import com.google.cloud.spring.pubsub.support.PublisherFactory;
import com.google.cloud.spring.pubsub.support.converter.PubSubMessageConverter;
import com.google.cloud.spring.pubsub.support.converter.SimplePubSubMessageConverter;
import com.google.pubsub.v1.PubsubMessage;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.Assert;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SettableListenableFuture;

/**
 * Default implementation of {@link PubSubPublisherOperations}.
 *
 * <p>The main Google Cloud Pub/Sub integration component for publishing to topics.
 *
 * @since 1.1
 */
public class PubSubPublisherTemplate implements PubSubPublisherOperations {

  private static final Log LOGGER = LogFactory.getLog(PubSubPublisherTemplate.class);

  private PubSubMessageConverter pubSubMessageConverter = new SimplePubSubMessageConverter();

  private final PublisherFactory publisherFactory;

  /**
   * Default {@link PubSubPublisherTemplate} constructor that uses {@link
   * SimplePubSubMessageConverter} to serialize and deserialize payloads.
   *
   * @param publisherFactory the {@link com.google.cloud.pubsub.v1.Publisher} factory to publish to
   *     topics.
   */
  public PubSubPublisherTemplate(PublisherFactory publisherFactory) {
    Assert.notNull(publisherFactory, "The publisherFactory can't be null.");

    this.publisherFactory = publisherFactory;
  }

  public PubSubMessageConverter getMessageConverter() {
    return this.pubSubMessageConverter;
  }

  public void setMessageConverter(PubSubMessageConverter pubSubMessageConverter) {
    Assert.notNull(pubSubMessageConverter, "The pubSubMessageConverter can't be null.");

    this.pubSubMessageConverter = pubSubMessageConverter;
  }

  /**
   * Uses the configured message converter to first convert the payload and headers to a {@code
   * PubsubMessage} and then publish it.
   */
  @Override
  public <T> ListenableFuture<String> publish(
      String topic, T payload, Map<String, String> headers) {
    return publish(topic, this.pubSubMessageConverter.toPubSubMessage(payload, headers));
  }

  @Override
  public <T> ListenableFuture<String> publish(String topic, T payload) {
    return publish(topic, payload, null);
  }

  @Override
  public ListenableFuture<String> publish(final String topic, PubsubMessage pubsubMessage) {
    Assert.hasText(topic, "The topic can't be null or empty.");
    Assert.notNull(pubsubMessage, "The pubsubMessage can't be null.");

    ApiFuture<String> publishFuture =
        this.publisherFactory.createPublisher(topic).publish(pubsubMessage);

    final SettableListenableFuture<String> settableFuture = new SettableListenableFuture<>();
    ApiFutures.addCallback(
        publishFuture,
        new ApiFutureCallback<String>() {

          @Override
          public void onFailure(Throwable throwable) {
            String errorMessage = "Publishing to " + topic + " topic failed.";
            LOGGER.warn(errorMessage, throwable);
            PubSubDeliveryException pubSubDeliveryException =
                new PubSubDeliveryException(pubsubMessage, errorMessage, throwable);
            settableFuture.setException(pubSubDeliveryException);
          }

          @Override
          public void onSuccess(String result) {
            if (LOGGER.isDebugEnabled()) {
              LOGGER.debug("Publishing to " + topic + " was successful. Message ID: " + result);
            }
            settableFuture.set(result);
          }
        },
        directExecutor());

    return settableFuture;
  }

  public PublisherFactory getPublisherFactory() {
    return this.publisherFactory;
  }
}
