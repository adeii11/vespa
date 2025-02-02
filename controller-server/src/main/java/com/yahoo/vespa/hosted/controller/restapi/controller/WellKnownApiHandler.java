// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.controller;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.StringResponse;
import com.yahoo.vespa.hosted.controller.config.WellKnownFolderConfig;
import com.yahoo.yolean.Exceptions;


/**
 * Responsible for serving contents from the RFC 8615 well-known directory
 * @author olaa
 */
public class WellKnownApiHandler extends ThreadedHttpRequestHandler {

    private final String securityTxt;

    public WellKnownApiHandler(Context context, WellKnownFolderConfig wellKnownFolderConfig) {
        super(context);
        this.securityTxt = wellKnownFolderConfig.securityTxt();
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        return switch (request.getMethod()) {
            case GET -> get(request);
            default -> ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
        };
    }

    private HttpResponse get(HttpRequest request) {
        try {
            Path path = new Path(request.getUri());
            if (path.matches("/.well-known/security.txt")) return securityTxt();
            return ErrorResponse.notFoundError("Nothing at " + path);
        }
        catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse securityTxt() {
        return new StringResponse(securityTxt);
    }

}
