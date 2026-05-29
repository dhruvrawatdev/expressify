package io.github.dhruvrawatdev.expressify.router.middleware;

import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;
import io.github.dhruvrawatdev.expressify.router.handler.NextFunction;
import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;

import java.util.List;

/**
 * Executes a list of RouteHandlers in order, linking each handler to the next
 * via a NextFunction. Used by UndertowAdapter to run per-entry handler chains.
 *
 * <p>When the last handler calls next.run(), the provided {@code whenDone} callback
 * is invoked so execution can continue in the outer routing loop.
 */
public class MiddlewareChain {

    private final List<RouteHandler> handlers;
    private final Request req;
    private final Response res;
    private final NextFunction whenDone;

    public MiddlewareChain(List<RouteHandler> handlers, Request req, Response res, NextFunction whenDone) {
        this.handlers = handlers;
        this.req = req;
        this.res = res;
        this.whenDone = whenDone;
    }

    /** Start executing the chain from the first handler. */
    public void execute() throws Exception {
        run(0);
    }

    private void run(int index) throws Exception {
        if (index >= handlers.size()) {
            whenDone.run();
            return;
        }

        RouteHandler handler = handlers.get(index);
        boolean[] nextInvoked = {false};

        NextFunction next = new NextFunction() {
            @Override
            public void run() throws Exception {
                nextInvoked[0] = true;
                MiddlewareChain.this.run(index + 1);
            }

            @Override
            public void error(Throwable err) throws Exception {
                nextInvoked[0] = true;
                whenDone.error(err);
            }
        };

        try {
            handler.handle(req, res, next);
        } catch (Throwable e) {
            if (!nextInvoked[0]) {
                whenDone.error(e);
            } else {
                if (e instanceof Exception ex) throw ex;
                throw new RuntimeException(e);
            }
        }
    }
}
