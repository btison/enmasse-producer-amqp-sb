package com.redhat.btison.enmasse.amqp.sb.producer.vertx;

import java.io.UnsupportedEncodingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.redhat.btison.enmasse.amqp.sb.producer.AmqpConfigurationProperties;

import io.vertx.amqpbridge.AmqpBridge;
import io.vertx.amqpbridge.AmqpBridgeOptions;
import io.vertx.amqpbridge.AmqpConstants;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageProducer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;

@Component("MessageProducerVerticle")
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MessageProducerVerticle extends AbstractVerticle {

    @Autowired
    private AmqpConfigurationProperties properties;

    private AmqpBridge bridge;

    private MessageProducer<JsonObject> producer;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        AmqpBridgeOptions bridgeOptions = new AmqpBridgeOptions();
        bridgeOptions.setSsl(properties.getSsl().isEnabled());
        bridgeOptions.setTrustAll(properties.getSsl().isTrustAll());
        bridgeOptions.setHostnameVerificationAlgorithm(properties.getSsl().isVerifyHost() == false ? "" : "HTTPS");
        if (!bridgeOptions.isTrustAll()) {
            JksOptions jksOptions = new JksOptions()
                    .setPath(properties.getTruststore().getPath())
                    .setPassword(properties.getTruststore().getPassword());
            bridgeOptions.setTrustStoreOptions(jksOptions);
        }
        bridge = AmqpBridge.create(vertx, bridgeOptions);
        String host = properties.getHost();
        int port = properties.getPort();
        String username = properties.getUser();
        String password = properties.getPassword();
        bridge.start(host, port, username, password, ar -> {
            if (ar.failed()) {
                startFuture.fail(ar.cause());
            } else {
                bridgeStarted();
                startFuture.complete();
            }
        });
    }

    private void bridgeStarted() {
        producer = bridge.<JsonObject>createProducer(properties.getAddress())
                .exceptionHandler(t -> t.printStackTrace());
        vertx.eventBus().consumer("message-producer", (Message<String> msg) -> {
            JsonObject payload = new JsonObject();
            payload.put(AmqpConstants.BODY_TYPE, AmqpConstants.BODY_TYPE_DATA);
            try {
                payload.put(AmqpConstants.BODY, msg.body().getBytes("UTF-8"));
            } catch (UnsupportedEncodingException e1) {
                payload.put(AmqpConstants.BODY, new byte[] {});
            }
            try {
                producer.send(payload);
                msg.reply(null);
            } catch (Exception e) {
                e.printStackTrace();
                msg.fail(-1, e.getMessage());
            }
        });
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        if (producer != null) {
            producer.close();
        }
        if (bridge != null) {
            bridge.close(ar -> {});
        }
    }
}
