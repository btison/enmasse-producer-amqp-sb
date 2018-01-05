package com.redhat.btison.enmasse.amqp.sb.producer.rest;

import java.util.concurrent.CompletableFuture;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.redhat.btison.enmasse.amqp.sb.producer.model.SimpleMessage;

import io.vertx.core.Vertx;

@Component
@Path("/")
public class MessageResource {

    @Autowired
    private Vertx vertx;

    @POST
    @Path("/message")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response message(SimpleMessage message) {
        String text = message.getMessage();
        CompletableFuture<Void> future = new CompletableFuture<>();
        vertx.eventBus().send("message-producer", text, ar -> {
            if (ar.succeeded()) {
                future.complete(null);
            } else {
                future.completeExceptionally(ar.cause());
            }
        });
        try {
            future.get();
            return Response.ok().build();
        } catch (Exception e) {
            return Response.status(500, e.getMessage()).build();
        }
    }

}
